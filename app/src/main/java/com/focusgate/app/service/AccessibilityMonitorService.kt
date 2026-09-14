package com.focusgate.app.service

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.focusgate.app.Constants
import com.focusgate.app.GateActivity
import com.focusgate.app.InterruptActivity
import com.focusgate.app.MainActivity
import com.focusgate.app.R
import com.focusgate.app.data.AppSession
import com.focusgate.app.data.IntentType
import com.focusgate.app.intervention.GateOverlayController
import com.focusgate.app.intervention.InterventionManager
import com.focusgate.app.rule.ReminderIntensity
import com.focusgate.app.util.DeepQuestionBank
import com.focusgate.app.util.BuwanleLogger
import com.focusgate.app.util.SessionStorage
import kotlinx.coroutines.*
import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicReference
import java.util.UUID
import org.json.JSONObject

/**
 * 核心检测服务：AccessibilityService
 * 通过监听窗口状态、窗口集合和窗口内容事件实时检测目标 App 切换。
 * 比 UsageStatsManager 轮询更可靠，尤其在 HyperOS/Android 14 上。
 */
class AccessibilityMonitorService : AccessibilityService() {

    companion object {
        private const val TAG = "AccessibilitySvc"
        private const val COOLDOWN_MS = 10_000L  // 10 秒冷却，防止确认后重开应用反复触发
        private const val GATE_BLOCK_BUFFER_MINUTES = 5  // 封锁期缓冲（计划时长之外多封锁5分钟）
        private const val GATE_RESULT_POLL_MS = 200L
        private const val GATE_RESULT_TIMEOUT_MS = 5 * 60_000L
        private const val GATE_SURFACE_VERIFY_MS = 2_500L
        private const val PENDING_GATE_RETRY_MS = 2_500L
        private const val EVENT_HEARTBEAT_PERSIST_MS = 2_000L
        private const val GUARD_NOTIFICATION_CHANNEL_ID = "focusgate_guard"
        private const val GUARD_NOTIFICATION_ID = 1201

        @Volatile
        private var isRunning = false

        fun isEnabled(context: android.content.Context): Boolean {
            val am = context.getSystemService(ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
            val serviceList = am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC)
            return serviceList?.any {
                it.resolveInfo.serviceInfo.packageName == context.packageName &&
                        it.resolveInfo.serviceInfo.name == AccessibilityMonitorService::class.java.name
            } ?: false
        }

        fun openAccessibilitySettings(context: android.content.Context) {
            val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }

        fun isServiceRunning(): Boolean = isRunning
    }

    private var serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var sessionStorage: SessionStorage
    private lateinit var interventionManager: InterventionManager
    private lateinit var gateOverlayController: GateOverlayController
    @Volatile
    private var currentSession: AppSession? = null
    /** 当前正在等待 GateActivity 返回结果的请求。非空时不能把 HOME / 本应用误判成离开。 */
    @Volatile
    private var pendingGateId: String? = null
    private var pendingGateStartedAt: Long = 0L
    private var lastPendingGateRetryAt: Long = 0L
    /** 记录上一个受控目标（含子目标信息），用于离开检测 */
    @Volatile
    private var lastForegroundTarget: ResolvedTarget? = null
    private var lastGateTime: Long = 0
    private var lastGateTargetId: String? = null
    private var lastHeartbeatPersistAt: Long = 0L
    private var lastHeartbeatTarget: String? = null

    /** 离开检测防抖：临时弹窗（悬浮通知等）不应立即结束会话 */
    private val leaveHandler = Handler(Looper.getMainLooper())
    private var pendingLeaveRunnable: Runnable? = null
    private val LEAVE_DEBOUNCE_MS = 15_000L

    /** reminderJob 原子引用，防止并发竞态 */
    private val reminderJobRef = AtomicReference<Job?>(null)

    /** 封装检测目标：独立应用或容器应用内的子页面 */
    private data class ResolvedTarget(val packageName: String, val subTarget: String? = null) {
        val targetId: String get() = if (subTarget != null) "$packageName#$subTarget" else packageName
    }

    private fun resolveTarget(packageName: String, className: String?): ResolvedTarget? {
        // 独立受控应用
        if (Constants.TARGET_PACKAGES.contains(packageName)) {
            return ResolvedTarget(packageName)
        }
        // 容器应用内的子页面
        Constants.resolveSubTarget(packageName, className)?.let { subTarget ->
            return ResolvedTarget(packageName, subTarget)
        }
        return null
    }

    private val extendReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            if (intent?.action == "com.focusgate.app.EXTEND_SESSION") {
                val extendMinutes = intent.getIntExtra("extend_minutes", 5)
                extendCurrentSession(extendMinutes)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "onServiceConnected")
        isRunning = true
        if (!serviceScope.isActive) {
            serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        }
        sessionStorage = SessionStorage(this)
        sessionStorage.repairStaleOpenSessions()
        interventionManager = InterventionManager(this)
        gateOverlayController = GateOverlayController(this, sessionStorage)
        startForegroundGuard()
        ContextCompat.registerReceiver(
            this,
            extendReceiver,
            android.content.IntentFilter("com.focusgate.app.EXTEND_SESSION"),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        restorePendingGateState()
        // 不盲目恢复旧会话；收到前台事件后，以目标和原始计时截止点共同验证。
        getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE).edit()
            .putLong(Constants.KEY_SERVICE_LAST_CONNECTED_AT, System.currentTimeMillis())
            .apply()
        BuwanleLogger.log("SERVICE", "无障碍服务已连接，等待前台事件验证会话")

        Toast.makeText(this, "不玩了 检测服务已启动", Toast.LENGTH_SHORT).show()
    }

    /**
     * 无障碍服务可能被 HyperOS 临时解绑后重连。覆盖层会随旧 Service 消失，
     * 但必须恢复同一个 requestId，不能把它误判成一次新的打开并再次计数。
     */
    private fun restorePendingGateState() {
        val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        val requestId = prefs.getString(Constants.KEY_PENDING_GATE_ID, null)
        val targetId = prefs.getString(Constants.KEY_LAST_GATE_TARGET, null)
        if (requestId.isNullOrBlank() || targetId.isNullOrBlank()) return

        val now = System.currentTimeMillis()
        val storedStartedAt = prefs.getLong(Constants.KEY_PENDING_GATE_STARTED_AT, 0L)
        if (storedStartedAt > 0L && now - storedStartedAt >= GATE_RESULT_TIMEOUT_MS) {
            clearPendingGateResult()
            BuwanleLogger.log("GATE_RESTORE", "丢弃已超时的待确认闸门 request=$requestId")
            return
        }
        val startedAt = storedStartedAt.takeIf { it > 0L } ?: now
        pendingGateId = requestId
        pendingGateStartedAt = startedAt
        lastPendingGateRetryAt = 0L
        lastGateTargetId = targetId

        if (currentSession == null) {
            val realPackage = targetId.substringBefore("#")
            val subTarget = targetId.substringAfter("#", "").takeIf { it.isNotBlank() }
            currentSession = AppSession(
                packageName = realPackage,
                startTime = startedAt,
                subTarget = subTarget
            )
        }

        val outcome = prefs.getString(Constants.KEY_PENDING_GATE_OUTCOME, null)
        BuwanleLogger.log(
            "GATE_RESTORE",
            "服务重连恢复待确认闸门 target=$targetId request=$requestId outcome=$outcome"
        )
        if (outcome != null) {
            serviceScope.launch { readAndResolvePendingGate(requestId) }
        } else {
            resumePendingGateResultMonitor(requestId, startedAt)
        }
    }

    private fun resumePendingGateResultMonitor(requestId: String, startedAt: Long) {
        serviceScope.launch {
            var waited = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
            while (waited < GATE_RESULT_TIMEOUT_MS && pendingGateId == requestId) {
                delay(GATE_RESULT_POLL_MS)
                waited += GATE_RESULT_POLL_MS
                if (readAndResolvePendingGate(requestId)) return@launch
            }
            if (waited >= GATE_RESULT_TIMEOUT_MS && pendingGateId == requestId) {
                gateOverlayController.dismiss(requestId)
                refreshGateCooldown(System.currentTimeMillis())
                currentSession = null
                pendingGateId = null
                pendingGateStartedAt = 0L
                clearActiveSessionPrefs()
                clearPendingGateResult()
                leaveHandler.post { performGlobalAction(GLOBAL_ACTION_HOME) }
                BuwanleLogger.log("GATE_RESTORE", "恢复后的闸门等待超时，已关闭")
            }
        }
    }

    private fun startForegroundGuard() {
        try {
            val notificationManager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                GUARD_NOTIFICATION_CHANNEL_ID,
                "不玩了·后台守护",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持受控应用检测服务响应，防止系统冻结"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
            notificationManager.createNotificationChannel(channel)

            val contentIntent = PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(this, GUARD_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("不玩了正在守护")
                .setContentText("正在监测抖音、小红书等受控应用")
                .setContentIntent(contentIntent)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build()

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    GUARD_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(GUARD_NOTIFICATION_ID, notification)
            }
            BuwanleLogger.log("SERVICE", "已进入 specialUse 前台守护模式")
        } catch (e: Exception) {
            BuwanleLogger.log(
                "SERVICE",
                "前台守护启动失败，继续使用无障碍服务: ${e.javaClass.simpleName}: ${e.message}"
            )
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return

        val packageName = event.packageName?.toString() ?: return
        if (packageName.isBlank()) return
        val className = event.className?.toString()
        val now = System.currentTimeMillis()
        val isContentChange = event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED

        // 防御性修复：系统 UI 弹窗（通知栏、快捷设置等）不应干扰离开检测
        if (GateEventPolicy.shouldIgnoreSystemUiEvent(packageName, className)) {
            if (!isContentChange) {
                BuwanleLogger.log("EVENT", "忽略系统UI事件 pkg=$packageName last=${lastForegroundTarget?.targetId} session=${currentSession?.let { it.packageName + (it.subTarget?.let { s -> "#$s" } ?: "") }}")
            }
            return
        }

        // Gate / 提醒 / 复盘页面属于干预流程的一部分，不能被当成“离开目标应用”。
        if (packageName == applicationContext.packageName) {
            if (!isContentChange) {
                BuwanleLogger.log("EVENT", "忽略本应用窗口事件 class=$className")
            }
            return
        }

        val resolvedTarget = resolveTarget(packageName, className)
            ?: lastForegroundTarget?.takeIf {
                event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
                    it.subTarget != null && it.packageName == packageName
            }
        val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        val heartbeatTarget = resolvedTarget?.targetId ?: ""
        if (!isContentChange ||
            heartbeatTarget != lastHeartbeatTarget ||
            now - lastHeartbeatPersistAt >= EVENT_HEARTBEAT_PERSIST_MS
        ) {
            prefs.edit()
                .putLong(Constants.KEY_LAST_MONITORED_EVENT_AT, now)
                .putString(Constants.KEY_LAST_MONITORED_TARGET, heartbeatTarget)
                .apply()
            lastHeartbeatPersistAt = now
            lastHeartbeatTarget = heartbeatTarget
        }
        if (!isContentChange) {
            BuwanleLogger.log("EVENT", "pkg=$packageName class=$className resolved=${resolvedTarget?.targetId} last=${lastForegroundTarget?.targetId} session=${currentSession?.let { it.packageName + (it.subTarget?.let { s -> "#$s" } ?: "") }}")
        }

        // 调试：记录所有微信窗口事件，帮助确认视频号 Activity 类名
        if (packageName == "com.tencent.mm" && className != null) {
            BuwanleLogger.log("WECHAT", "窗口变化: $className")
        }

        // 读取冷却时间（支持跨组件同步，防止 Gate 确认后重开反复触发）
        val syncedLastGateTime = prefs.getLong(Constants.KEY_LAST_GATE_TIME, lastGateTime)
        lastGateTime = maxOf(lastGateTime, syncedLastGateTime)
        lastGateTargetId = prefs.getString(Constants.KEY_LAST_GATE_TARGET, lastGateTargetId)

        // 同步 lastForegroundApp（GateActivity 启动应用后会写入）
        val syncedLastForeground = prefs.getString(Constants.KEY_LAST_FOREGROUND_APP, null)
        if (syncedLastForeground != null) {
            val realPkg = if (syncedLastForeground.contains("#")) syncedLastForeground.substringBefore("#") else syncedLastForeground
            val sub = if (syncedLastForeground.contains("#")) syncedLastForeground.substringAfter("#") else null
            lastForegroundTarget = ResolvedTarget(realPkg, sub)
            prefs.edit().remove(Constants.KEY_LAST_FOREGROUND_APP).apply()
            BuwanleLogger.log("SYNC", "lastForegroundTarget 同步为 ${lastForegroundTarget?.targetId}")
        }

        // Gate 期间会先回桌面再显示拦截页；这些窗口变化都不能结束待确认会话。
        val activePendingGateId = pendingGateId
        if (activePendingGateId != null) {
            // 用户点击确认后覆盖层会先移除。确认结果与下一次窗口事件之间存在几十毫秒竞态，
            // 必须先消费结果，不能因为 surface 暂时消失而把同一个闸门重新创建一次。
            val pendingOutcomeReady = GateEventPolicy.hasReadyOutcome(
                activeRequestId = activePendingGateId,
                storedRequestId = prefs.getString(Constants.KEY_PENDING_GATE_ID, null),
                outcome = prefs.getString(Constants.KEY_PENDING_GATE_OUTCOME, null)
            )
            if (pendingOutcomeReady && readAndResolvePendingGate(activePendingGateId)) {
                if (resolvedTarget != null) lastForegroundTarget = resolvedTarget
                return
            }

            val surfaceVisible = isGateSurfaceVisible(activePendingGateId)
            if (!surfaceVisible && resolvedTarget != null &&
                now - lastPendingGateRetryAt >= PENDING_GATE_RETRY_MS
            ) {
                lastPendingGateRetryAt = now
                val recovered = gateOverlayController.show(resolvedTarget.targetId, activePendingGateId)
                BuwanleLogger.log(
                    "GATE_RECOVER",
                    "待确认闸门表面丢失，事件触发重建=$recovered target=${resolvedTarget.targetId}"
                )
                if (!recovered && now - pendingGateStartedAt >= GATE_SURFACE_VERIFY_MS * 2) {
                    resetPendingGateForRetry(activePendingGateId, "事件重建覆盖层失败")
                }
            } else {
                if (!isContentChange) {
                    BuwanleLogger.log(
                        "GATE",
                        "闸门等待结果中，surfaceVisible=$surfaceVisible，忽略前台变化: $packageName"
                    )
                }
            }
            return
        }

        // 如果上一个目标是受控目标且我们现在真的离开了它，结束会话
        // 关键修复：增加防抖，防止悬浮通知/临时弹窗误判为离开
        val lastTarget = lastForegroundTarget
        val sessionTargetId = currentSession?.let {
            if (it.subTarget != null) "${it.packageName}#${it.subTarget}" else it.packageName
        }

        // 如果用户回到了当前会话的目标应用，取消任何待处理的延迟离开
        if (resolvedTarget != null && sessionTargetId != null && resolvedTarget.targetId == sessionTargetId && pendingLeaveRunnable != null) {
            pendingLeaveRunnable?.let {
                leaveHandler.removeCallbacks(it)
                pendingLeaveRunnable = null
                BuwanleLogger.log("LEAVE", "取消延迟离开: 回到了 $sessionTargetId")
            }
        }

        // 直接从一个受控目标切到另一个时，立即结束旧会话，随后为新目标触发闸门。
        if (currentSession != null && resolvedTarget != null && sessionTargetId != null &&
            resolvedTarget.targetId != sessionTargetId
        ) {
            finishCurrentSession(now, "切换到另一个受控目标 ${resolvedTarget.targetId}")
        }

        // 离开到普通应用时延迟确认，过滤通知、小窗和系统跳转造成的瞬时事件。
        if (currentSession != null && resolvedTarget == null) {
            if (pendingLeaveRunnable == null) {
                val previousTargetId = lastTarget?.targetId ?: sessionTargetId
                BuwanleLogger.log("LEAVE", "可能离开，延迟${LEAVE_DEBOUNCE_MS}ms确认: last=$previousTargetId -> now=$packageName")
                val expectedTargetId = sessionTargetId
                val runnable = Runnable {
                    if (currentSessionTargetId() == expectedTargetId) {
                        val now2 = System.currentTimeMillis()
                        finishCurrentSession(now2, "延迟确认离开 $previousTargetId")
                    }
                    pendingLeaveRunnable = null
                }
                pendingLeaveRunnable = runnable
                leaveHandler.postDelayed(runnable, LEAVE_DEBOUNCE_MS)
            }
        }

        // 如果当前已有该目标的活跃会话（用户正在刷），不重复触发
        if (currentSession != null && resolvedTarget != null) {
            val activeTargetId = currentSession!!.let {
                if (it.subTarget != null) "${it.packageName}#${it.subTarget}" else it.packageName
            }
            if (resolvedTarget.targetId == activeTargetId) {
                if (!isContentChange) {
                    BuwanleLogger.log("SKIP", "已有活跃会话: $activeTargetId, 跳过")
                }
                lastForegroundTarget = resolvedTarget
                return
            }
        }

        // ===== 检查持久化会话，防止 HyperOS 杀服务后重复闸门或丢失计时 =====
        if (currentSession == null) {
            val sessionJson = prefs.getString(Constants.KEY_ACTIVE_SESSION, null)
            if (sessionJson != null) {
                try {
                    val json = JSONObject(sessionJson)
                    val legacyPkg = json.optString("pkg", "")
                    val savedPkg = json.optString("target_id", "").ifBlank {
                        val sub = json.optString("sub_target", "")
                        if (legacyPkg.contains("#") || sub.isBlank()) legacyPkg else "$legacyPkg#$sub"
                    }
                    val savedStart = json.optLong("start_time", 0)
                    val savedDuration = json.optInt("effective_minutes", 0)
                    val currentTargetId = resolvedTarget?.targetId
                    val canRestore = currentTargetId != null && SessionRecoveryPolicy.isRestorable(
                        savedTargetId = savedPkg,
                        currentTargetId = currentTargetId,
                        startTimeMs = savedStart,
                        effectiveMinutes = savedDuration,
                        nowMs = now,
                        bufferMinutes = GATE_BLOCK_BUFFER_MINUTES
                    )
                    if (canRestore && resolvedTarget != null) {
                        val restored = AppSession(
                            packageName = resolvedTarget.packageName,
                            startTime = savedStart,
                            subTarget = resolvedTarget.subTarget,
                            intentType = try { IntentType.valueOf(json.optString("intent", "")) } catch (_: Exception) { IntentType.UNKNOWN },
                            plannedDurationMinutes = json.optInt("planned_minutes", 10),
                            enforcedDurationMinutes = json.optInt("effective_minutes", -1).takeIf { it > 0 },
                            reminderIntensity = try { ReminderIntensity.valueOf(json.optString("intensity", "NORMAL")) } catch (_: Exception) { ReminderIntensity.NORMAL },
                            doubleConfirmed = json.optBoolean("double_confirmed", false)
                        )
                        currentSession = restored
                        lastForegroundTarget = resolvedTarget
                        sessionStorage.repairStaleOpenSessions(now)
                        BuwanleLogger.log("RECOVER", "按原截止时间恢复会话，跳过闸门: $currentTargetId")
                        if (reminderJobRef.get() == null || reminderJobRef.get()?.isActive != true) {
                            startReminder(restored)
                        }
                        return
                    } else if (resolvedTarget != null || !isContentChange) {
                        clearActiveSessionPrefs()
                        clearGateBlock()
                        sessionStorage.repairStaleOpenSessions(now)
                        BuwanleLogger.log(
                            "RECOVER",
                            "结束不可恢复会话: saved=$savedPkg current=${currentTargetId ?: packageName} " +
                                "elapsed=${now - savedStart}ms selected=${savedDuration}min"
                        )
                    }
                } catch (e: Exception) {
                    clearActiveSessionPrefs()
                    clearGateBlock()
                    sessionStorage.repairStaleOpenSessions(now)
                    BuwanleLogger.log("RECOVER", "持久化会话无效，已清理: ${e.message}")
                }
            }
        }

        // 活跃会话已在上方处理。没有活跃会话时，旧封锁标记不能继续吞掉新闸门；
        // 这类残留通常来自 HyperOS 杀进程或服务重连。
        if (resolvedTarget != null) {
            val gateBlockedUntil = prefs.getLong(Constants.KEY_GATE_BLOCKED_UNTIL, 0)
            val gateBlockedTarget = prefs.getString(Constants.KEY_GATE_BLOCKED_TARGET, null)
            if (now < gateBlockedUntil && gateBlockedTarget == resolvedTarget.targetId) {
                BuwanleLogger.log("GATE_BLOCK", "清除无活跃会话对应的旧封锁: ${resolvedTarget.targetId}")
                clearGateBlock()
            }
        }

        // 如果新目标是受控目标，触发闸门
        if (resolvedTarget != null) {
            // 内容变化事件只用于弥补系统漏发首次前台事件；目标已在前台时不能用它
            // 在冷却结束瞬间突然补弹闸门，也不能连续刷屏日志/Toast。
            if (GateEventPolicy.shouldIgnoreSteadyContentEvent(
                    isContentChange = isContentChange,
                    foregroundTargetId = lastForegroundTarget?.targetId,
                    resolvedTargetId = resolvedTarget.targetId
                )
            ) {
                return
            }
            val elapsedSinceLastGate = now - lastGateTime
            val cooldown = GateEventPolicy.isCoolingDown(
                lastGateTargetId = lastGateTargetId,
                newTargetId = resolvedTarget.targetId,
                elapsedMs = elapsedSinceLastGate,
                cooldownMs = COOLDOWN_MS
            )
            BuwanleLogger.log("GATE_CHECK", "target=${resolvedTarget.targetId} cooldown=${cooldown}(${elapsedSinceLastGate}ms/${COOLDOWN_MS}ms)")
            if (!cooldown) {
                BuwanleLogger.log("GATE", "触发闸门 target=${resolvedTarget.targetId}")
                triggerGate(resolvedTarget, now)
            } else {
                BuwanleLogger.log("GATE", "冷却中跳过 target=${resolvedTarget.targetId}")
            }
        }

        lastForegroundTarget = resolvedTarget
    }

    override fun onInterrupt() {
        Log.w(TAG, "onInterrupt - service was interrupted by system")
        isRunning = false
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "onUnbind")
        isRunning = false
        serviceScope.cancel()
        reminderJobRef.getAndSet(null)?.cancel()
        interventionManager.dismissOverlay()
        if (::gateOverlayController.isInitialized) {
            gateOverlayController.dismiss()
        }
        pendingLeaveRunnable?.let {
            leaveHandler.removeCallbacks(it)
            pendingLeaveRunnable = null
        }
        try {
            unregisterReceiver(extendReceiver)
        } catch (_: Exception) {}
        stopForeground(STOP_FOREGROUND_REMOVE)
        return super.onUnbind(intent)
    }

    private fun triggerGate(target: ResolvedTarget, now: Long) {
        // 清理旧会话（如果有），避免状态残留
        if (currentSession != null) {
            finishCurrentSession(now, "触发新闸门前清理旧会话")
        }
        // 取消任何待处理的延迟离开
        pendingLeaveRunnable?.let {
            leaveHandler.removeCallbacks(it)
            pendingLeaveRunnable = null
        }

        lastGateTime = now
        lastGateTargetId = target.targetId
        getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE).edit()
            .putLong(Constants.KEY_LAST_GATE_TIME, now)
            .putString(Constants.KEY_LAST_GATE_TARGET, target.targetId)
            .apply()

        // 每次闸门使用唯一 ID，并先清掉上次可能残留的结果，防止旧选择被误读。
        clearPendingGateResult()
        val requestId = UUID.randomUUID().toString()
        pendingGateId = requestId
        pendingGateStartedAt = now
        lastPendingGateRetryAt = now
        getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE).edit()
            .putString(Constants.KEY_PENDING_GATE_ID, requestId)
            .putLong(Constants.KEY_PENDING_GATE_STARTED_AT, now)
            .commit()

        currentSession = AppSession(
            packageName = target.packageName,
            startTime = now,
            subTarget = target.subTarget
        )

        BuwanleLogger.log("TRIGGER", "triggerGate target=${target.targetId} sessionStart=${currentSession!!.startTime}")

        // 首选无障碍专用全屏覆盖层。它直接盖在目标应用之上，不依赖后台启动 Activity，
        // 因而不会被 Android/HyperOS 的后台弹出限制拦截。
        val overlayShown = gateOverlayController.show(target.targetId, requestId)
        if (overlayShown) {
            BuwanleLogger.log("TRIGGER", "首次闸门已使用 TYPE_ACCESSIBILITY_OVERLAY 显示")

            // 覆盖层先显示，再在后台加载大型问题库。部分 HyperOS/ART 设备首次
            // 校验 DeepQuestionBank 会卡住几十秒，绝不能让它阻塞无障碍主线程。
            serviceScope.launch {
                try {
                    DeepQuestionBank.resetTracking()
                    val question = DeepQuestionBank.randomAny()
                    gateOverlayController.updateDeepQuestion(requestId, question)
                } catch (e: Exception) {
                    BuwanleLogger.log(
                        "QUESTION_BANK",
                        "后台准备问题失败，保留即时占位问题: ${e.javaClass.simpleName}: ${e.message}"
                    )
                }
            }
        } else {
            // 极少数定制系统若拒绝无障碍覆盖层，再使用原来的 HOME + Activity 回退路径。
            performGlobalAction(GLOBAL_ACTION_HOME)
        }

        serviceScope.launch {
            if (!overlayShown) {
                delay(400)
                val intent = Intent(this@AccessibilityMonitorService, GateActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                        Intent.FLAG_ACTIVITY_NO_HISTORY or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra(GateActivity.EXTRA_PACKAGE_NAME, target.targetId)
                    putExtra(GateActivity.EXTRA_GATE_REQUEST_ID, requestId)
                }
                try {
                    startActivity(intent)
                    BuwanleLogger.log("TRIGGER", "回退 GateActivity startActivity 成功")
                } catch (e: Exception) {
                    BuwanleLogger.log("TRIGGER", "回退 GateActivity 启动失败: ${e.message}")
                    Toast.makeText(
                        this@AccessibilityMonitorService,
                        "拦截层启动失败，请重新开启无障碍服务",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            // startActivity() 在部分 HyperOS 版本上会被静默拦截而不抛异常。
            // 短时验证真正可见的闸门表面；若不存在，重试专用无障碍覆盖层，
            // 仍失败则解除 pending，允许下一次前台事件立即再触发。
            delay(GATE_SURFACE_VERIFY_MS)
            if (pendingGateId == requestId && !isGateSurfaceVisible(requestId)) {
                val recovered: Boolean? = withContext(Dispatchers.Main.immediate) {
                    val currentPrefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
                    val outcomeReady = GateEventPolicy.hasReadyOutcome(
                        activeRequestId = requestId,
                        storedRequestId = currentPrefs.getString(Constants.KEY_PENDING_GATE_ID, null),
                        outcome = currentPrefs.getString(Constants.KEY_PENDING_GATE_OUTCOME, null)
                    )
                    // 确认按钮移除覆盖层与后台可见性校验可能同时发生。结果已经提交
                    // 或 request 已消费时，排队中的恢复任务绝不能把旧闸门重新加回来。
                    if (pendingGateId != requestId || outcomeReady) {
                        null
                    } else {
                        gateOverlayController.show(target.targetId, requestId)
                    }
                }
                if (recovered == null) {
                    BuwanleLogger.log("GATE_RECOVER", "结果已提交，取消排队中的覆盖层重建 request=$requestId")
                } else {
                    BuwanleLogger.log(
                        "GATE_RECOVER",
                        "闸门可见性校验失败，覆盖层重试=$recovered target=${target.targetId}"
                    )
                }
                if (recovered == false && pendingGateId == requestId) {
                    resetPendingGateForRetry(requestId, "覆盖层与回退 Activity 均未显示")
                    return@launch
                }
            }

            // 覆盖层和回退 Activity 使用同一个原子结果通道。
            var waited = 0L
            while (waited < GATE_RESULT_TIMEOUT_MS) {
                delay(GATE_RESULT_POLL_MS)
                waited += GATE_RESULT_POLL_MS
                if (readAndResolvePendingGate(requestId)) break
            }
            if (waited >= GATE_RESULT_TIMEOUT_MS && pendingGateId == requestId) {
                gateOverlayController.dismiss(requestId)
                refreshGateCooldown(System.currentTimeMillis())
                clearGateBlock()
                clearActiveSessionPrefs()
                currentSession = null
                pendingGateId = null
                pendingGateStartedAt = 0L
                clearPendingGateResult()
                leaveHandler.post { performGlobalAction(GLOBAL_ACTION_HOME) }
                BuwanleLogger.log("TRIGGER", "Gate 5分钟未确认，关闭覆盖层并回到桌面")
            }
        }
    }

    @Synchronized
    private fun readAndResolvePendingGate(requestId: String): Boolean {
        val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        if (prefs.getString(Constants.KEY_PENDING_GATE_ID, null) != requestId) return false

        when (prefs.getString(Constants.KEY_PENDING_GATE_OUTCOME, null)) {
            Constants.GATE_OUTCOME_BLOCKED,
            Constants.GATE_OUTCOME_CANCELLED -> {
                val outcome = prefs.getString(Constants.KEY_PENDING_GATE_OUTCOME, null)
                BuwanleLogger.log("CONFIRM", "闸门未继续 outcome=$outcome request=$requestId")
                // 冷却必须从用户作出选择时开始，而不是从 15 秒倒计时开始。
                // 否则取消后目标 App 的残留窗口事件会立刻再次弹出同一闸门。
                refreshGateCooldown(System.currentTimeMillis())
                currentSession = null
                pendingGateId = null
                pendingGateStartedAt = 0L
                clearGateBlock()
                clearActiveSessionPrefs()
                clearPendingGateResult()
                return true
            }
            Constants.GATE_OUTCOME_CONFIRMED -> Unit
            else -> return false
        }

        val intentName = prefs.getString(Constants.KEY_PENDING_INTENT, null)
        val minutes = prefs.getInt(Constants.KEY_PENDING_MINUTES, -1)

        if (intentName != null && minutes >= 0 && currentSession != null) {
            val intentType = try {
                IntentType.valueOf(intentName)
            } catch (_: IllegalArgumentException) {
                IntentType.UNKNOWN
            }
            val doubleConfirmed = prefs.getBoolean(Constants.KEY_PENDING_DOUBLE_CONFIRMED, false)
            val intensityName = prefs.getString(Constants.KEY_PENDING_INTENSITY, ReminderIntensity.NORMAL.name)
            val intensity = try {
                ReminderIntensity.valueOf(intensityName!!)
            } catch (_: Exception) {
                ReminderIntensity.NORMAL
            }
            val moodStr = prefs.getString(Constants.KEY_PENDING_MOOD, "")
            val mood = if (moodStr.isNullOrEmpty()) null else try {
                com.focusgate.app.data.MoodType.valueOf(moodStr)
            } catch (_: Exception) { null }

            // 关键修复：把开始时间更新为用户确认时间，确保计时从用户真正开始刷时算起
            val confirmTime = System.currentTimeMillis()
            val session = currentSession!!
            val gateWaitMs = confirmTime - session.startTime
            session.startTime = confirmTime
            session.intentType = intentType
            session.plannedDurationMinutes = minutes
            session.doubleConfirmed = doubleConfirmed
            // 用户选几分钟就严格按几分钟计时。规则只能改变确认强度，不能静默缩短时长。
            session.enforcedDurationMinutes = minutes
            session.reminderIntensity = intensity
            session.mood = mood

            // 必须在重置开始时间之后保存，否则历史统计会把 15 秒闸门等待算入使用时长。
            sessionStorage.saveSession(session)
            BuwanleLogger.log("CONFIRM", "用户确认: pkg=${session.packageName} planned=$minutes enforced=${session.enforcedDurationMinutes} intensity=$intensity")
            BuwanleLogger.log("TIMER", "计时基准重置: confirmTime=$confirmTime (Gate等待了 ${gateWaitMs}ms)")

            pendingGateId = null
            pendingGateStartedAt = 0L
            clearPendingGateResult()

            // 持久化会话状态，防止 HyperOS 杀服务后丢失
            saveActiveSessionToPrefs(session)
            // 保存闸门封锁期：有效时长内 + 5分钟缓冲，绝不重复弹窗
            saveGateBlock(session)
            startReminder(session)
            return true
        }
        return false
    }

    @Synchronized
    private fun startReminder(session: AppSession) {
        cancelReminder()
        val effectiveMinutes = session.effectiveDurationMinutes
        val pkg = session.packageName

        // 所有重建、服务恢复和“再刷 5 分钟”都必须锚定用户确认时刻。
        // 若从当前时刻重新计整段时长，服务重启或延长会话都会让计时严重偏长。
        val timerStartMs = session.startTime
        val reminderOffsetMs = SessionTiming.reminderOffsetsMs(effectiveMinutes).single()
        BuwanleLogger.log("REMINDER", "启动单次计时器: pkg=$pkg selected=${effectiveMinutes}min timerStartMs=$timerStartMs")

        val newJob = serviceScope.launch {
            delayUntil(timerStartMs, reminderOffsetMs)
            // 到点恰好遇到通知、小窗或系统跳转时，lastForegroundTarget 会短暂为空。
            // 单次计时器不能因此永久漏掉提醒；等待离开防抖得出最终结论后再决定。
            val currentAndForeground = awaitCurrentSessionForeground(session)
            BuwanleLogger.log(
                "REMINDER",
                "所选时间到: selected=${effectiveMinutes}min isActive=$isActive currentAndForeground=$currentAndForeground"
            )
            if (isActive && currentAndForeground) {
                showFullScreenInterrupt(session, effectiveMinutes)
            }
        }
        reminderJobRef.set(newJob)
    }

    private suspend fun awaitCurrentSessionForeground(session: AppSession): Boolean {
        val waitDeadline = android.os.SystemClock.elapsedRealtime() + LEAVE_DEBOUNCE_MS + 2_000L
        while (currentCoroutineContext().isActive && isCurrentSession(session)) {
            if (isCurrentSessionForeground(session)) return true
            if (android.os.SystemClock.elapsedRealtime() >= waitDeadline) return false
            delay(250L)
        }
        return false
    }

    private suspend fun delayUntil(sessionStartMs: Long, targetMs: Long) {
        val now = System.currentTimeMillis()
        val elapsed = now - sessionStartMs
        val remaining = SessionTiming.checkpointRemainingMs(sessionStartMs, targetMs, now)
        BuwanleLogger.log("DELAY", "delayUntil: now=$now start=$sessionStartMs elapsed=${elapsed}ms target=${targetMs}ms remaining=${remaining}ms")
        if (remaining > 0) {
            delay(remaining)
        } else {
            BuwanleLogger.log("DELAY", "delayUntil: remaining<=0, 立即返回")
        }
    }

    private fun isCurrentSession(session: AppSession): Boolean {
        return currentSession?.let {
            it.packageName == session.packageName && it.subTarget == session.subTarget && it.startTime == session.startTime
        } ?: false
    }

    private fun isCurrentSessionForeground(session: AppSession): Boolean {
        val sessionTargetId = if (session.subTarget != null) {
            "${session.packageName}#${session.subTarget}"
        } else {
            session.packageName
        }
        return isCurrentSession(session) && lastForegroundTarget?.targetId == sessionTargetId
    }

    @Synchronized
    private fun cancelReminder() {
        reminderJobRef.getAndSet(null)?.cancel()
    }

    private suspend fun showFullScreenInterrupt(session: AppSession, duration: Int) {
        val targetId = if (session.subTarget != null) "${session.packageName}#${session.subTarget}" else session.packageName
        val question = try {
            DeepQuestionBank.randomTimeout()
        } catch (e: Exception) {
            BuwanleLogger.log("REMINDER", "超时问题生成失败，使用备用文案: ${e.message}")
            "现在继续刷，真的比你原本要做的事更重要吗？"
        }
        val overlayShown = withContext(Dispatchers.Main.immediate) {
            interventionManager.showOverlay(
                packageName = targetId,
                message = "${duration} 分钟到了。\n\n$question"
            )
        }
        if (overlayShown) {
            Log.d(TAG, "Accessibility full-screen interrupt shown for $targetId")
        } else {
            withContext(Dispatchers.Main.immediate) {
                val intent = Intent(this@AccessibilityMonitorService, InterruptActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(InterruptActivity.EXTRA_PACKAGE_NAME, targetId)
                    putExtra(InterruptActivity.EXTRA_DURATION, duration)
                    putExtra(InterruptActivity.EXTRA_QUESTION, question)
                }
                try {
                    startActivity(intent)
                    BuwanleLogger.log("REMINDER", "覆盖层失败，已启动全屏提醒备用页面")
                } catch (e: Exception) {
                    BuwanleLogger.log(
                        "REMINDER",
                        "覆盖层和备用页面均启动失败: ${e.javaClass.simpleName}: ${e.message}"
                    )
                    interventionManager.notify(
                        title = "不玩了 · 时间到了",
                        message = "${duration} 分钟到了，请先退出短视频",
                        packageName = targetId
                    )
                }
            }
            Log.d(TAG, "Fallback full-screen interrupt Activity shown for $targetId")
        }
    }

    private fun extendCurrentSession(extendMinutes: Int) {
        currentSession?.let { session ->
            val newDuration = session.effectiveDurationMinutes + extendMinutes
            session.enforcedDurationMinutes = newDuration
            interventionManager.dismissOverlay()
            sessionStorage.updateLastOpenSession(session)
            saveActiveSessionToPrefs(session)
            // 延长时仍以原始确认时间为基准，只增加用户选择的分钟数。
            saveGateBlock(session)
            Log.d(TAG, "Session extended to $newDuration minutes")
            startReminder(session)
        }
    }

    private fun getAppDisplayName(targetId: String): String = when {
        targetId == "com.tencent.mm#finder" -> "微信视频号"
        targetId == "com.ss.android.ugc.aweme" -> "抖音"
        targetId == "com.ss.android.ugc.aweme.lite" -> "抖音极速版"
        targetId == "tv.danmaku.bili" -> "B站"
        targetId == "tv.danmaku.bilibilihd" -> "B站HD"
        targetId == "com.xingin.xhs" -> "小红书"
        targetId == "com.smile.gifmaker" -> "快手"
        targetId == "com.kuaishou.nebula" -> "快手极速版"
        else -> targetId
    }

    // ======================== 会话持久化：防止 HyperOS 杀服务丢状态 ========================

    private fun saveActiveSessionToPrefs(session: AppSession) {
        try {
            val targetId = if (session.subTarget != null) {
                "${session.packageName}#${session.subTarget}"
            } else {
                session.packageName
            }
            val json = JSONObject().apply {
                // pkg 保留真实包名；target_id 保存带子目标的完整标识。
                put("pkg", session.packageName)
                put("package_name", session.packageName)
                put("target_id", targetId)
                put("sub_target", session.subTarget ?: "")
                put("start_time", session.startTime)
                put("planned_minutes", session.plannedDurationMinutes)
                put("effective_minutes", session.effectiveDurationMinutes)
                put("intent", session.intentType.name)
                put("intensity", session.reminderIntensity.name)
                put("double_confirmed", session.doubleConfirmed)
            }
            getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE).edit()
                .putString(Constants.KEY_ACTIVE_SESSION, json.toString())
                .apply()
        } catch (_: Exception) {}
    }

    private fun clearActiveSessionPrefs() {
        getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE).edit()
            .remove(Constants.KEY_ACTIVE_SESSION)
            .apply()
    }

    /**
     * 保存闸门封锁期：从确认时间到计划时长+5分钟缓冲。
     * 在这个时间段内，无论服务是否重启、会话是否丢失，闸门都不会再次触发。
     */
    private fun saveGateBlock(session: AppSession) {
        val blockedUntil = SessionTiming.gateBlockedUntilMs(
            session.startTime,
            session.effectiveDurationMinutes,
            GATE_BLOCK_BUFFER_MINUTES
        )
        val targetId = if (session.subTarget != null) {
            "${session.packageName}#${session.subTarget}"
        } else {
            session.packageName
        }
        getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE).edit()
            .putLong(Constants.KEY_GATE_BLOCKED_UNTIL, blockedUntil)
            .putString(Constants.KEY_GATE_BLOCKED_TARGET, targetId)
            .apply()
        BuwanleLogger.log("GATE_BLOCK", "闸门封锁至: $blockedUntil target=$targetId (时长${session.effectiveDurationMinutes}min+缓冲${GATE_BLOCK_BUFFER_MINUTES}min)")
    }

    /** 清除闸门封锁期（会话正常结束时调用） */
    private fun clearGateBlock() {
        getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE).edit()
            .remove(Constants.KEY_GATE_BLOCKED_UNTIL)
            .remove(Constants.KEY_GATE_BLOCKED_TARGET)
            .apply()
    }

    /** 把防重复冷却锚定到闸门关闭时刻。 */
    private fun refreshGateCooldown(now: Long) {
        val targetId = currentSessionTargetId() ?: lastGateTargetId ?: return
        lastGateTime = now
        lastGateTargetId = targetId
        getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE).edit()
            .putLong(Constants.KEY_LAST_GATE_TIME, now)
            .putString(Constants.KEY_LAST_GATE_TARGET, targetId)
            .apply()
    }

    private fun currentSessionTargetId(): String? = currentSession?.let {
        if (it.subTarget != null) "${it.packageName}#${it.subTarget}" else it.packageName
    }

    private fun finishCurrentSession(endTime: Long, reason: String) {
        val session = currentSession ?: return
        sessionStorage.closeLastSession(session.packageName, session.subTarget, endTime)
        clearGateBlock()
        clearActiveSessionPrefs()
        currentSession = null
        lastForegroundTarget = null
        cancelReminder()
        interventionManager.dismissOverlay()
        interventionManager.dismissNotifications()
        pendingLeaveRunnable?.let { leaveHandler.removeCallbacks(it) }
        pendingLeaveRunnable = null
        BuwanleLogger.log("LEAVE", "$reason: ${session.packageName}#${session.subTarget}")
    }

    private fun isGateSurfaceVisible(requestId: String): Boolean {
        if (::gateOverlayController.isInitialized && gateOverlayController.isShowing(requestId)) {
            return true
        }
        return getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
            .getString(Constants.KEY_VISIBLE_GATE_REQUEST_ID, null) == requestId
    }

    private fun resetPendingGateForRetry(requestId: String, reason: String) {
        if (pendingGateId != requestId) return
        if (::gateOverlayController.isInitialized) {
            gateOverlayController.dismiss(requestId)
        }
        currentSession = null
        pendingGateId = null
        pendingGateStartedAt = 0L
        lastPendingGateRetryAt = 0L
        lastGateTime = 0L
        clearGateBlock()
        clearActiveSessionPrefs()
        clearPendingGateResult()
        getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE).edit()
            .remove(Constants.KEY_LAST_GATE_TIME)
            .remove(Constants.KEY_LAST_GATE_TARGET)
            .apply()
        lastGateTargetId = null
        BuwanleLogger.log("GATE_RECOVER", "$reason；已解除 pending，允许下一次事件重试")
        leaveHandler.post {
            Toast.makeText(this, "拦截层未显示，已自动恢复；请重新打开目标应用", Toast.LENGTH_LONG).show()
        }
    }

    private fun clearPendingGateResult() {
        getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE).edit()
            .remove(Constants.KEY_PENDING_GATE_ID)
            .remove(Constants.KEY_PENDING_GATE_STARTED_AT)
            .remove(Constants.KEY_PENDING_GATE_OUTCOME)
            .remove(Constants.KEY_PENDING_INTENT)
            .remove(Constants.KEY_PENDING_MINUTES)
            .remove(Constants.KEY_PENDING_DOUBLE_CONFIRMED)
            .remove(Constants.KEY_PENDING_ENFORCED_DURATION)
            .remove(Constants.KEY_PENDING_INTENSITY)
            .remove(Constants.KEY_PENDING_RULE_MESSAGE)
            .remove(Constants.KEY_PENDING_MOOD)
            .remove(Constants.KEY_VISIBLE_GATE_REQUEST_ID)
            .apply()
    }
}
