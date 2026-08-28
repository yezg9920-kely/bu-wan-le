package com.focusgate.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.focusgate.app.databinding.ActivityMainBinding
import com.focusgate.app.intervention.InterventionManager
import com.focusgate.app.service.AccessibilityMonitorService
import com.focusgate.app.util.FocusGateLogger
import com.focusgate.app.util.HyperOSHelper
import com.focusgate.app.util.SessionStorage

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var sessionStorage: SessionStorage
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, "未开启通知权限，中途提醒将无法显示", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionStorage = SessionStorage(this)
        requestNotificationPermissionIfNeeded()
        updateUI()
        setupListeners()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun setupListeners() {
        binding.btnOpenAccessibility.setOnClickListener {
            AccessibilityMonitorService.openAccessibilitySettings(this)
        }

        binding.btnRequestOverlay.setOnClickListener {
            InterventionManager.openOverlaySettings(this)
        }

        binding.btnHyperOSPopup.setOnClickListener {
            HyperOSHelper.openBackgroundPopupSettings(this)
        }

        binding.btnHyperOSAutoStart.setOnClickListener {
            HyperOSHelper.openAutoStartSettings(this)
        }

        binding.btnHyperOSBattery.setOnClickListener {
            HyperOSHelper.openBatteryOptimizationSettings(this)
        }

        binding.btnReview.setOnClickListener {
            startActivity(Intent(this, ReviewActivity::class.java))
        }

        binding.btnHistoryReview.setOnClickListener {
            startActivity(Intent(this, HistoryReviewActivity::class.java))
        }

        binding.btnClearData.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("确认清空历史？")
                .setMessage("所有使用记录和复盘数据都会被删除，此操作无法撤销。")
                .setPositiveButton("清空") { _, _ ->
                    sessionStorage.clear()
                    Toast.makeText(this, "历史数据已清空", Toast.LENGTH_SHORT).show()
                    updateUI()
                }
                .setNegativeButton("取消", null)
                .show()
        }

        binding.btnCopyLogs.setOnClickListener {
            showLogPreviewDialog()
        }
    }

    private fun showLogPreviewDialog() {
        val allLogs = FocusGateLogger.getLogs()
        val lines = allLogs.lines()
        val preview = lines.takeLast(50).joinToString("\n")
        val title = "运行日志 (最近 ${lines.size.coerceAtMost(50)} / ${lines.size} 行)"

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(preview.ifEmpty { "暂无日志" })
            .setPositiveButton("复制") { _, _ ->
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("FocusGate Logs", allLogs))
                Toast.makeText(this, "日志已复制到剪贴板 (${lines.size} 行)", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("关闭", null)
            .setNeutralButton("清空") { _, _ ->
                FocusGateLogger.clear()
                Toast.makeText(this, "日志已清空", Toast.LENGTH_SHORT).show()
                updateUI()
            }
            .show()
    }

    private fun updateUI() {
        val isAccessibilityEnabled = AccessibilityMonitorService.isEnabled(this)
        val isAccessibilityRunning = AccessibilityMonitorService.isServiceRunning()
        val hasOverlay = InterventionManager.canDrawOverlays(this)

        binding.tvAccessibilityStatus.text = when {
            isAccessibilityEnabled && isAccessibilityRunning -> "✅ 已开启并连接"
            isAccessibilityEnabled -> "⚠️ 已授权，但服务未连接"
            else -> "❌ 未开启（必须）"
        }
        binding.tvOverlayStatus.text = if (hasOverlay) "✅ 已授权" else "❌ 未授权（可选）"

        if (isAccessibilityRunning) {
            val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
            val lastEventAt = prefs.getLong(Constants.KEY_LAST_MONITORED_EVENT_AT, 0L)
            val lastTarget = prefs.getString(Constants.KEY_LAST_MONITORED_TARGET, "").orEmpty()
            val eventHint = if (lastEventAt > 0L) {
                val ago = android.text.format.DateUtils.getRelativeTimeSpanString(
                    lastEventAt,
                    System.currentTimeMillis(),
                    android.text.format.DateUtils.SECOND_IN_MILLIS
                )
                "\n最近事件：${lastTarget.ifBlank { "非受控应用" }}（$ago）"
            } else {
                "\n尚未收到前台窗口事件"
            }
            binding.tvAccessibilityHint.text =
                "服务已连接，打开抖音/B站/小红书/快手即可看到弹窗$eventHint"
            binding.tvAccessibilityHint.setTextColor(getColor(android.R.color.holo_green_dark))
        } else if (isAccessibilityEnabled) {
            binding.tvAccessibilityHint.text =
                "系统设置显示已授权，但检测服务没有连接。请关闭后重新开启无障碍权限，并检查 HyperOS 自启动和省电限制。"
            binding.tvAccessibilityHint.setTextColor(getColor(android.R.color.holo_orange_dark))
        } else {
            binding.tvAccessibilityHint.text = "点击上方按钮，在系统设置中找到 FocusGate 并开启"
            binding.tvAccessibilityHint.setTextColor(getColor(android.R.color.holo_red_dark))
        }

        // HyperOS 保活卡片显示控制
        if (HyperOSHelper.isXiaomiDevice()) {
            binding.cardHyperOS.visibility = android.view.View.VISIBLE
            val ignoringBattery = HyperOSHelper.isIgnoringBatteryOptimizations(this)
            binding.tvHyperOSStatus.text = if (ignoringBattery) {
                "电池优化已忽略 ✅，请确认其他三项也已设置"
            } else {
                "需要设置（杀后台后服务会断开）"
            }
        } else {
            binding.cardHyperOS.visibility = android.view.View.GONE
        }

        val todaySessions = sessionStorage.getTodaySessions()
        binding.tvTodayOpens.text = "今日打开次数: ${todaySessions.size}"

        val appCounts = todaySessions.groupingBy {
            if (it.subTarget != null) "${it.packageName}#${it.subTarget}" else it.packageName
        }.eachCount()
        val appText = appCounts.entries.joinToString("\n") { (targetId, count) ->
            val name = getAppDisplayName(targetId)
            "  $name: $count 次"
        }
        binding.tvAppBreakdown.text = if (appText.isEmpty()) "暂无数据" else appText

        val totalMs = todaySessions.sumOf { it.completedDurationMs }
        val totalMin = totalMs / 1000 / 60
        binding.tvTotalTime.text = "今日总时长: ${totalMin} 分钟"

        val unconsciousCount = todaySessions.count { it.intentType == com.focusgate.app.data.IntentType.UNCONSCIOUS }
        val blockedCount = todaySessions.count { it.blockedByRule != null }
        val doubleConfirmCount = todaySessions.count { it.doubleConfirmed }

        val ruleText = buildString {
            appendLine("无意图打开: ${unconsciousCount} 次")
            appendLine("二次确认: ${doubleConfirmCount} 次")
            appendLine("被阻断: ${blockedCount} 次")

            val allSessions = sessionStorage.getAllSessions()
            var streak = 0
            for (i in allSessions.indices.reversed()) {
                if (allSessions[i].intentType == com.focusgate.app.data.IntentType.UNCONSCIOUS) {
                    streak++
                } else {
                    break
                }
            }
            appendLine("当前无意识 streak: ${streak}")

            val nightCount = todaySessions.count { it.isNightTime }
            appendLine("夜间打开: ${nightCount} 次")
        }
        binding.tvRuleStats.text = ruleText
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
}
