package com.focusgate.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.focusgate.app.backup.DataArchiveManager
import com.focusgate.app.databinding.ActivityMainBinding
import com.focusgate.app.intervention.InterventionManager
import com.focusgate.app.legal.ConsentManager
import com.focusgate.app.service.AccessibilityMonitorService
import com.focusgate.app.util.BuwanleLogger
import com.focusgate.app.util.HyperOSHelper
import com.focusgate.app.util.SessionStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var sessionStorage: SessionStorage
    private lateinit var consentManager: ConsentManager
    private lateinit var archiveManager: DataArchiveManager
    private var pendingExportPassphrase: CharArray? = null
    private var privacyDialogVisible = false

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val passphrase = pendingExportPassphrase
        pendingExportPassphrase = null
        if (uri == null || passphrase == null) {
            passphrase?.fill('\u0000')
            return@registerForActivityResult
        }
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { archiveManager.exportTo(uri, passphrase) }
            result.onSuccess { count ->
                Toast.makeText(this@MainActivity, "已加密导出 $count 条记录", Toast.LENGTH_LONG).show()
            }.onFailure { error ->
                Toast.makeText(this@MainActivity, "导出失败：${error.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) showImportPasswordDialog(uri)
    }
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
        consentManager = ConsentManager(this)
        archiveManager = DataArchiveManager(this)
        updateUI()
        setupListeners()
        if (consentManager.hasAcceptedPrivacy()) {
            requestNotificationPermissionIfNeeded()
        } else {
            showFirstRunPrivacyConsent()
        }
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
        if (::consentManager.isInitialized &&
            !consentManager.hasAcceptedPrivacy() &&
            !privacyDialogVisible
        ) {
            showFirstRunPrivacyConsent()
        }
    }

    private fun setupListeners() {
        binding.btnOpenAccessibility.setOnClickListener {
            showAccessibilityDisclosureIfNeeded()
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

        binding.btnExportData.setOnClickListener { showExportPasswordDialog() }
        binding.btnImportData.setOnClickListener {
            importLauncher.launch(arrayOf("application/octet-stream", "application/zip", "*/*"))
        }
        binding.btnPrivacy.setOnClickListener { openLegalDocument(LegalActivity.DOCUMENT_PRIVACY) }
        binding.btnTerms.setOnClickListener { openLegalDocument(LegalActivity.DOCUMENT_TERMS) }

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
        val allLogs = BuwanleLogger.getLogs()
        val lines = allLogs.lines()
        val preview = lines.takeLast(50).joinToString("\n")
        val title = "运行日志 (最近 ${lines.size.coerceAtMost(50)} / ${lines.size} 行)"

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(preview.ifEmpty { "暂无日志" })
            .setPositiveButton("复制") { _, _ ->
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("不玩了运行日志", allLogs))
                Toast.makeText(this, "日志已复制到剪贴板 (${lines.size} 行)", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("关闭", null)
            .setNeutralButton("清空") { _, _ ->
                BuwanleLogger.clear()
                Toast.makeText(this, "日志已清空", Toast.LENGTH_SHORT).show()
                updateUI()
            }
            .show()
    }

    private fun updateUI() {
        binding.tvVersion.text = "短视频自控与专注 · ${BuildConfig.VERSION_NAME} · ${BuildConfig.EDITION}"
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
            binding.tvAccessibilityHint.text = "点击上方按钮，阅读用途说明后在系统设置中找到“不玩了”并开启"
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

    private fun showFirstRunPrivacyConsent() {
        privacyDialogVisible = true
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("欢迎使用不玩了")
            .setMessage(
                "社区版所有使用记录只保存在本机，不联网、无广告。应用需要在你授权后通过无障碍服务检测选定应用的切换，但不会读取文字、输入内容或截图。\n\n请先阅读并同意隐私政策与用户协议。"
            )
            .setCancelable(false)
            .setPositiveButton("同意并继续") { _, _ ->
                consentManager.acceptPrivacy()
                requestNotificationPermissionIfNeeded()
            }
            .setNegativeButton("暂不同意") { _, _ -> finish() }
            .setNeutralButton("查看隐私政策") { _, _ ->
                openLegalDocument(LegalActivity.DOCUMENT_PRIVACY)
            }
            .create()
            .also { dialog ->
                dialog.setOnDismissListener { privacyDialogVisible = false }
                dialog.show()
            }
    }

    private fun showAccessibilityDisclosureIfNeeded() {
        if (consentManager.hasAcceptedAccessibilityDisclosure()) {
            AccessibilityMonitorService.openAccessibilitySettings(this)
            return
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("开启前请确认无障碍用途")
            .setMessage(
                "为了在你打开选定的短视频应用时及时显示停顿和到时提醒，不玩了需要检测前台应用包名及窗口切换。\n\n不会读取聊天、密码、页面文字或输入内容，不会截图，也不会执行点击手势。拒绝后将无法自动拦截，但仍可查看本地历史。"
            )
            .setPositiveButton("我已了解，去开启") { _, _ ->
                consentManager.acceptAccessibilityDisclosure()
                AccessibilityMonitorService.openAccessibilitySettings(this)
            }
            .setNegativeButton("暂不开启", null)
            .show()
    }

    private fun openLegalDocument(document: String) {
        startActivity(Intent(this, LegalActivity::class.java).putExtra(LegalActivity.EXTRA_DOCUMENT, document))
    }

    private fun showExportPasswordDialog() {
        val password = passwordField("设置至少8位备份口令")
        val confirm = passwordField("再次输入口令")
        val container = passwordContainer(password, confirm)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("加密导出")
            .setMessage("口令不会上传；忘记后无法恢复备份。")
            .setView(container)
            .setPositiveButton("选择保存位置", null)
            .setNegativeButton("取消", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val first = password.text.toString()
                if (first.length < 8) {
                    password.error = "至少8个字符"
                } else if (first != confirm.text.toString()) {
                    confirm.error = "两次口令不一致"
                } else {
                    pendingExportPassphrase = first.toCharArray()
                    dialog.dismiss()
                    val timestamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                    exportLauncher.launch("不玩了-备份-$timestamp.bwl")
                }
            }
        }
        dialog.show()
    }

    private fun showImportPasswordDialog(uri: Uri) {
        val password = passwordField("输入备份口令")
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("导入加密备份")
            .setView(password)
            .setPositiveButton("导入", null)
            .setNegativeButton("取消", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (password.text.length < 8) {
                    password.error = "至少8个字符"
                    return@setOnClickListener
                }
                val passphrase = password.text.toString().toCharArray()
                dialog.dismiss()
                lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) {
                        archiveManager.importFrom(uri, passphrase)
                    }
                    result.onSuccess { imported ->
                        Toast.makeText(
                            this@MainActivity,
                            "导入完成：新增 ${imported.importedSessions} 条记录，恢复 ${imported.importedManagedApps} 个应用设置",
                            Toast.LENGTH_LONG
                        ).show()
                        updateUI()
                    }.onFailure { error ->
                        Toast.makeText(this@MainActivity, "导入失败：${error.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun passwordField(hint: String): EditText = EditText(this).apply {
        this.hint = hint
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        setPadding(16, 12, 16, 12)
    }

    private fun passwordContainer(vararg fields: EditText): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val horizontalPadding = (20 * resources.displayMetrics.density).toInt()
        setPadding(horizontalPadding, 8, horizontalPadding, 0)
        fields.forEach(::addView)
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
