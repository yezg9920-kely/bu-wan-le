package com.focusgate.app.intervention

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.view.ContextThemeWrapper
import com.focusgate.app.Constants
import com.focusgate.app.R
import com.focusgate.app.data.AppSession
import com.focusgate.app.data.IntentType
import com.focusgate.app.data.MoodType
import com.focusgate.app.databinding.ActivityGateBinding
import com.focusgate.app.rule.ReminderIntensity
import com.focusgate.app.rule.RuleContext
import com.focusgate.app.rule.RuleEngine
import com.focusgate.app.rule.RuleResult
import com.focusgate.app.util.DeepQuestionBank
import com.focusgate.app.util.FocusGateLogger
import com.focusgate.app.util.SessionStorage

/**
 * 首次打开受控应用时使用的最高优先级闸门。
 *
 * 该窗口由 AccessibilityService 直接创建为 TYPE_ACCESSIBILITY_OVERLAY，避免 Android/HyperOS
 * 对后台启动 Activity 的限制。窗口覆盖整个应用内容、接收所有触摸，并保留 GateActivity
 * 作为极端情况下的回退路径。
 */
class GateOverlayController(
    private val service: AccessibilityService,
    private val sessionStorage: SessionStorage
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val ruleEngine = RuleEngine()

    private var binding: ActivityGateBinding? = null
    private var currentRequestId: String? = null
    @Volatile
    private var attachedRequestId: String? = null
    private var targetId: String? = null
    private var selectedMood: MoodType? = null
    private var selectedIntent: IntentType? = null
    private var selectedMinutes = 10
    private var pendingRuleResult: RuleResult? = null
    private var countdownRunnable: Runnable? = null

    fun isShowing(requestId: String? = null): Boolean {
        val attachedId = attachedRequestId ?: return false
        return requestId == null || requestId == attachedId
    }

    /** 必须从主线程调用。返回 false 时由 Service 回退到 GateActivity。 */
    fun show(newTargetId: String, requestId: String): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            FocusGateLogger.log("GATE_OVERLAY", "拒绝在非主线程创建无障碍覆盖层")
            return false
        }

        dismissInternal()
        targetId = newTargetId
        currentRequestId = requestId
        selectedMood = null
        selectedIntent = null
        selectedMinutes = 10
        pendingRuleResult = null

        val themedContext = ContextThemeWrapper(service, R.style.Theme_FocusGate_Gate)
        val newBinding = ActivityGateBinding.inflate(LayoutInflater.from(themedContext))
        configureUi(newBinding)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.OPAQUE
        ).apply {
            setTitle("不玩了·全屏闸门")
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setFitInsetsTypes(0)
            }
        }

        return try {
            newBinding.root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) = Unit

                override fun onViewDetachedFromWindow(view: View) {
                    if (attachedRequestId == requestId) {
                        attachedRequestId = null
                        FocusGateLogger.log(
                            "GATE_OVERLAY",
                            "覆盖层被系统移除 target=$newTargetId request=$requestId"
                        )
                    }
                }
            })
            windowManager.addView(newBinding.root, params)
            binding = newBinding
            attachedRequestId = requestId
            newBinding.root.isFocusableInTouchMode = true
            newBinding.root.requestFocus()
            newBinding.root.bringToFront()
            startCountdown(newBinding)
            FocusGateLogger.log(
                "GATE_OVERLAY",
                "TYPE_ACCESSIBILITY_OVERLAY 已覆盖 target=$newTargetId request=$requestId"
            )
            true
        } catch (e: Exception) {
            countdownRunnable?.let(mainHandler::removeCallbacks)
            countdownRunnable = null
            binding = null
            currentRequestId = null
            attachedRequestId = null
            targetId = null
            FocusGateLogger.log(
                "GATE_OVERLAY",
                "无障碍覆盖层创建失败，准备回退 Activity: ${e.javaClass.simpleName}: ${e.message}"
            )
            false
        }
    }

    fun dismiss(expectedRequestId: String? = null) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            dismissInternal(expectedRequestId)
        } else {
            mainHandler.post { dismissInternal(expectedRequestId) }
        }
    }

    /**
     * 问题库在部分 HyperOS/ART 版本上首次校验可能耗时很久。
     * 覆盖层必须先显示，因此这里只接收后台准备好的文本并安全更新当前闸门。
     */
    fun updateDeepQuestion(requestId: String, question: String) {
        mainHandler.post {
            if (currentRequestId == requestId && attachedRequestId == requestId) {
                binding?.tvDeepQuestion?.text = "「$question」"
            }
        }
    }

    private fun configureUi(gateBinding: ActivityGateBinding) {
        val appName = displayName(targetId.orEmpty())
        gateBinding.tvAppName.text = "你确定要继续刷 $appName 吗？"
        // 不在 addView 之前触碰大型问题库，保证第一优先级覆盖层可以立即出现。
        gateBinding.tvDeepQuestion.text = "「先停一下：这是你现在真正想做的事吗？」"
        showTodayStats(gateBinding)

        // 可聚焦全屏窗口消费返回键，避免按返回键直接露出受控内容。
        gateBinding.root.setOnKeyListener { _, keyCode, _ ->
            keyCode == KeyEvent.KEYCODE_BACK
        }

        gateBinding.chipGroupMood.setOnCheckedStateChangeListener { _, checkedIds ->
            selectedMood = when (checkedIds.firstOrNull()) {
                R.id.chip_tired -> MoodType.TIRED
                R.id.chip_annoyed -> MoodType.ANNOYED
                R.id.chip_escape -> MoodType.ESCAPE
                R.id.chip_research -> MoodType.RESEARCH
                R.id.chip_itchy -> MoodType.ITCHY
                else -> null
            }
        }
        gateBinding.chipGroupIntent.setOnCheckedStateChangeListener { _, checkedIds ->
            selectedIntent = when (checkedIds.firstOrNull()) {
                R.id.chip_relax -> IntentType.RELAX
                R.id.chip_info -> IntentType.INFO
                R.id.chip_inspiration -> IntentType.INSPIRATION
                R.id.chip_unconscious -> IntentType.UNCONSCIOUS
                else -> null
            }
        }
        gateBinding.chipGroupDuration.setOnCheckedStateChangeListener { _, checkedIds ->
            selectedMinutes = when (checkedIds.firstOrNull()) {
                R.id.chip_5min -> 5
                R.id.chip_10min -> 10
                R.id.chip_15min -> 15
                else -> 10
            }
        }
        gateBinding.chip10min.isChecked = true

        gateBinding.btnConfirm.setOnClickListener {
            if (selectedMood == null) {
                Toast.makeText(service, "请先选择此刻更应该做什么", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (selectedIntent == null) {
                Toast.makeText(service, "请先回想上一次刷完后的感觉", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            evaluateRules(gateBinding)
        }

        gateBinding.btnCancelGate.setOnClickListener {
            val requestId = currentRequestId ?: return@setOnClickListener
            if (publishSimpleOutcome(requestId, Constants.GATE_OUTCOME_CANCELLED)) {
                dismiss(requestId)
                goHome()
            }
        }

        gateBinding.btnDoubleConfirm.setOnClickListener {
            val result = pendingRuleResult
            saveAndDismiss(
                doubleConfirmed = true,
                enforcedDuration = result?.maxDurationMinutes,
                intensity = result?.reminderIntensity ?: ReminderIntensity.NORMAL,
                ruleMessage = result?.message
            )
        }

        gateBinding.btnBlockOk.setOnClickListener {
            val requestId = currentRequestId
            dismiss(requestId)
            goHome()
        }
    }

    private fun evaluateRules(gateBinding: ActivityGateBinding) {
        val intentType = selectedIntent ?: IntentType.UNKNOWN
        val result = ruleEngine.evaluate(
            RuleContext(
                packageName = targetId.orEmpty(),
                currentIntent = intentType,
                userSelectedDuration = selectedMinutes,
                todaySessions = sessionStorage.getTodaySessions(),
                allSessions = sessionStorage.getAllSessions()
            )
        )

        when {
            result.blockEntirely -> showBlockPage(gateBinding, result.message ?: "触发阻断规则")
            result.requireDoubleConfirm -> {
                pendingRuleResult = result
                gateBinding.layoutNormal.visibility = View.GONE
                gateBinding.layoutDoubleConfirm.visibility = View.VISIBLE
                gateBinding.tvDoubleConfirmMessage.text = buildString {
                    append(DeepQuestionBank.randomDoubleConfirm())
                    result.message?.takeIf { it.isNotBlank() }?.let { append("\n\n").append(it) }
                }
            }
            else -> saveAndDismiss(
                doubleConfirmed = false,
                enforcedDuration = result.maxDurationMinutes,
                intensity = result.reminderIntensity,
                ruleMessage = result.message
            )
        }
    }

    private fun showBlockPage(gateBinding: ActivityGateBinding, message: String) {
        gateBinding.layoutNormal.visibility = View.GONE
        gateBinding.layoutBlock.visibility = View.VISIBLE
        gateBinding.tvBlockMessage.text = message

        val now = System.currentTimeMillis()
        val fullTargetId = targetId.orEmpty()
        val realPackage = fullTargetId.substringBefore("#")
        val subTarget = fullTargetId.substringAfter("#", "").takeIf { it.isNotBlank() }
        sessionStorage.saveSession(
            AppSession(
                packageName = realPackage,
                startTime = now,
                subTarget = subTarget,
                endTime = now,
                intentType = selectedIntent ?: IntentType.UNKNOWN,
                plannedDurationMinutes = selectedMinutes,
                blockedByRule = "UnconsciousStreak",
                enforcedDurationMinutes = 0,
                mood = selectedMood
            )
        )
        currentRequestId?.let { publishSimpleOutcome(it, Constants.GATE_OUTCOME_BLOCKED) }
    }

    private fun saveAndDismiss(
        doubleConfirmed: Boolean,
        enforcedDuration: Int?,
        intensity: ReminderIntensity,
        ruleMessage: String?
    ) {
        val requestId = currentRequestId ?: return
        val currentTargetId = targetId ?: return
        val intentType = selectedIntent ?: IntentType.UNKNOWN

        val saved = service.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(Constants.KEY_PENDING_GATE_ID, requestId)
            .putString(Constants.KEY_PENDING_GATE_OUTCOME, Constants.GATE_OUTCOME_CONFIRMED)
            .putString(Constants.KEY_PENDING_INTENT, intentType.name)
            .putInt(Constants.KEY_PENDING_MINUTES, selectedMinutes)
            .putBoolean(Constants.KEY_PENDING_DOUBLE_CONFIRMED, doubleConfirmed)
            .putInt(Constants.KEY_PENDING_ENFORCED_DURATION, enforcedDuration ?: -1)
            .putString(Constants.KEY_PENDING_INTENSITY, intensity.name)
            .putString(Constants.KEY_PENDING_RULE_MESSAGE, ruleMessage)
            .putString(Constants.KEY_PENDING_MOOD, selectedMood?.name ?: "")
            .putLong(Constants.KEY_LAST_GATE_TIME, System.currentTimeMillis())
            .putString(Constants.KEY_LAST_GATE_TARGET, currentTargetId)
            .putString(Constants.KEY_LAST_FOREGROUND_APP, currentTargetId)
            .commit()

        if (!saved) {
            Toast.makeText(service, "保存选择失败，请重试", Toast.LENGTH_SHORT).show()
            return
        }

        // 目标应用从未被送回后台；移除覆盖层后即可继续，不需要再次启动目标 Activity。
        dismiss(requestId)
        FocusGateLogger.log("GATE_OVERLAY", "用户确认，移除覆盖层 target=$currentTargetId")
    }

    private fun publishSimpleOutcome(requestId: String, outcome: String): Boolean {
        val saved = service.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(Constants.KEY_PENDING_GATE_ID, requestId)
            .putString(Constants.KEY_PENDING_GATE_OUTCOME, outcome)
            .commit()
        if (!saved) {
            Toast.makeText(service, "保存闸门状态失败，请重试", Toast.LENGTH_SHORT).show()
        }
        return saved
    }

    private fun showTodayStats(gateBinding: ActivityGateBinding) {
        val todaySessions = sessionStorage.getTodaySessions()
        val opens = todaySessions.size
        val totalMinutes = todaySessions.sumOf { it.completedDurationMs } / 1000 / 60
        val currentTargetId = targetId.orEmpty()
        val targetOpens = todaySessions.count {
            val savedTargetId = if (it.subTarget != null) {
                "${it.packageName}#${it.subTarget}"
            } else {
                it.packageName
            }
            savedTargetId == currentTargetId
        }
        gateBinding.tvTodayStats.text = if (opens > 0) {
            "今天你已经打开了 $opens 次，总时长 ${totalMinutes} 分钟（${displayName(currentTargetId)} $targetOpens 次）"
        } else {
            "今天第一次打开 — 确定要破功吗？"
        }
    }

    private fun startCountdown(gateBinding: ActivityGateBinding) {
        val totalMs = 15_000L
        val startedAt = System.currentTimeMillis()
        gateBinding.btnConfirm.isEnabled = false
        gateBinding.progressCountdown.max = totalMs.toInt()
        gateBinding.progressCountdown.progress = 0

        val runnable = object : Runnable {
            override fun run() {
                if (binding !== gateBinding) return
                val elapsed = System.currentTimeMillis() - startedAt
                val remaining = totalMs - elapsed
                gateBinding.progressCountdown.progress = elapsed.coerceAtMost(totalMs).toInt()
                if (remaining <= 0) {
                    gateBinding.tvCountdown.text = "时间到了，诚实地面对自己的选择"
                    gateBinding.btnConfirm.text = "确认，继续打开"
                    gateBinding.btnConfirm.isEnabled = true
                } else {
                    val seconds = (remaining / 1000) + 1
                    gateBinding.tvCountdown.text = "请停留 ${seconds} 秒，再想想..."
                    gateBinding.btnConfirm.text = "${seconds}秒后再想想..."
                    mainHandler.postDelayed(this, 100)
                }
            }
        }
        countdownRunnable = runnable
        mainHandler.postDelayed(runnable, 100)
    }

    private fun dismissInternal(expectedRequestId: String? = null) {
        if (expectedRequestId != null && expectedRequestId != currentRequestId) return
        countdownRunnable?.let(mainHandler::removeCallbacks)
        countdownRunnable = null
        binding?.root?.let { view ->
            try {
                windowManager.removeViewImmediate(view)
            } catch (_: Exception) {
                // Service 解绑或系统已经移除窗口时无需再次处理。
            }
        }
        binding = null
        currentRequestId = null
        attachedRequestId = null
        targetId = null
        selectedMood = null
        selectedIntent = null
        pendingRuleResult = null
    }

    private fun goHome() {
        val handled = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        if (!handled) {
            val home = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            service.startActivity(home)
        }
    }

    private fun displayName(targetId: String): String = when (targetId) {
        "com.tencent.mm#finder" -> "微信视频号"
        "com.ss.android.ugc.aweme" -> "抖音"
        "com.ss.android.ugc.aweme.lite" -> "抖音极速版"
        "tv.danmaku.bili" -> "B站"
        "tv.danmaku.bilibilihd" -> "B站HD"
        "com.xingin.xhs" -> "小红书"
        "com.smile.gifmaker" -> "快手"
        "com.kuaishou.nebula" -> "快手极速版"
        else -> targetId
    }
}
