package com.focusgate.app

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.focusgate.app.data.IntentType
import com.focusgate.app.data.MoodType
import com.focusgate.app.databinding.ActivityGateBinding
import com.focusgate.app.rule.RuleContext
import com.focusgate.app.rule.RuleEngine
import com.focusgate.app.rule.RuleResult
import com.focusgate.app.util.DeepQuestionBank
import com.focusgate.app.util.SessionStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GateActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_GATE_REQUEST_ID = "extra_gate_request_id"
        const val ACTION_CONFIRM_SESSION = "com.focusgate.app.CONFIRM_SESSION"
    }

    private lateinit var binding: ActivityGateBinding
    private var selectedMood: MoodType? = null
    private var selectedIntent: IntentType? = null
    private var selectedMinutes: Int = 10
    private var packageName: String? = null
    private var gateRequestId: String? = null
    private var pendingRuleResult: RuleResult? = null
    private lateinit var sessionStorage: SessionStorage
    private val ruleEngine = RuleEngine()

    private val countdownTotal = 15_000L // 15秒强制等待
    private val handler = Handler(Looper.getMainLooper())
    private var countdownRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 确保 Activity 能显示在锁屏上，并在最前面
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        binding = ActivityGateBinding.inflate(layoutInflater)
        setContentView(binding.root)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.layoutBlock.visibility != View.VISIBLE) {
                    Toast.makeText(this@GateActivity, "请做出选择", Toast.LENGTH_SHORT).show()
                }
            }
        })

        sessionStorage = SessionStorage(this)
        packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
        gateRequestId = intent.getStringExtra(EXTRA_GATE_REQUEST_ID)
        val appName = packageName?.let { getAppDisplayName(it) } ?: "未知应用"
        binding.tvAppName.text = "你确定要继续刷 $appName 吗？"

        // 显示今日统计，增加心理冲击
        showTodayStats()

        // Activity 只是无障碍覆盖层失败时的兜底，也必须先完成首帧，再异步加载问题库。
        binding.tvDeepQuestion.text = "「先停一下：这是你现在真正想做的事吗？」"
        lifecycleScope.launch(Dispatchers.Default) {
            try {
                DeepQuestionBank.resetTracking()
                val question = DeepQuestionBank.randomAny()
                withContext(Dispatchers.Main) {
                    binding.tvDeepQuestion.text = "「$question」"
                }
            } catch (e: Exception) {
                Log.w("GateActivity", "Failed to prepare deep question", e)
            }
        }

        setupMoodSelection()
        setupIntentSelection()
        setupDurationSelection()

        // 强制15秒倒计时
        startCountdown()

        binding.btnConfirm.setOnClickListener {
            if (selectedMood == null) {
                Toast.makeText(this, "请先选择此刻更应该做什么", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (selectedIntent == null) {
                Toast.makeText(this, "请先回想上一次刷完后的感觉", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            evaluateRules()
        }

        binding.btnCancelGate.setOnClickListener {
            publishGateOutcome(Constants.GATE_OUTCOME_CANCELLED)
            val home = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(home)
            finish()
        }

        binding.btnDoubleConfirm.setOnClickListener {
            val result = pendingRuleResult
            saveAndFinish(
                doubleConfirmed = true,
                enforcedDuration = result?.maxDurationMinutes,
                intensity = result?.reminderIntensity
                    ?: com.focusgate.app.rule.ReminderIntensity.NORMAL,
                ruleMessage = result?.message
            )
        }

        binding.btnBlockOk.setOnClickListener {
            val home = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(home)
            finish()
        }

        Toast.makeText(this, "FocusGate 拦截界面已弹出", Toast.LENGTH_SHORT).show()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        val requestId = gateRequestId ?: return
        val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        if (hasFocus) {
            if (prefs.getString(Constants.KEY_PENDING_GATE_ID, null) != requestId) {
                Log.w("GateActivity", "Ignoring stale gate activity: $requestId")
                finish()
                return
            }
            prefs.edit()
                .putString(Constants.KEY_VISIBLE_GATE_REQUEST_ID, requestId)
                .commit()
        } else if (prefs.getString(Constants.KEY_VISIBLE_GATE_REQUEST_ID, null) == requestId) {
            prefs.edit().remove(Constants.KEY_VISIBLE_GATE_REQUEST_ID).commit()
        }
    }

    private fun setupMoodSelection() {
        binding.chipGroupMood.setOnCheckedStateChangeListener { _, checkedIds ->
            selectedMood = when (checkedIds.firstOrNull()) {
                R.id.chip_tired -> MoodType.TIRED
                R.id.chip_annoyed -> MoodType.ANNOYED
                R.id.chip_escape -> MoodType.ESCAPE
                R.id.chip_research -> MoodType.RESEARCH
                R.id.chip_itchy -> MoodType.ITCHY
                else -> null
            }
        }
    }

    private fun setupIntentSelection() {
        binding.chipGroupIntent.setOnCheckedStateChangeListener { _, checkedIds ->
            selectedIntent = when (checkedIds.firstOrNull()) {
                R.id.chip_relax -> IntentType.RELAX
                R.id.chip_info -> IntentType.INFO
                R.id.chip_inspiration -> IntentType.INSPIRATION
                R.id.chip_unconscious -> IntentType.UNCONSCIOUS
                else -> null
            }
        }
    }

    private fun setupDurationSelection() {
        binding.chipGroupDuration.setOnCheckedStateChangeListener { _, checkedIds ->
            selectedMinutes = when (checkedIds.firstOrNull()) {
                R.id.chip_5min -> 5
                R.id.chip_10min -> 10
                R.id.chip_15min -> 15
                else -> 10
            }
        }
        binding.chip10min.isChecked = true
    }

    /** 运行规则引擎，根据结果决定下一步 */
    private fun evaluateRules() {
        val intentType = selectedIntent ?: IntentType.UNKNOWN
        val todaySessions = sessionStorage.getTodaySessions()
        val allSessions = sessionStorage.getAllSessions()

        val context = RuleContext(
            packageName = packageName ?: "",
            currentIntent = intentType,
            userSelectedDuration = selectedMinutes,
            todaySessions = todaySessions,
            allSessions = allSessions
        )

        val result = ruleEngine.evaluate(context)

        when {
            result.blockEntirely -> {
                showBlockPage(result.message ?: "触发阻断规则")
            }
            result.requireDoubleConfirm -> {
                showDoubleConfirm(result)
            }
            else -> {
                saveAndFinish(
                    doubleConfirmed = false,
                    enforcedDuration = result.maxDurationMinutes,
                    intensity = result.reminderIntensity,
                    ruleMessage = result.message
                )
            }
        }
    }

    private fun showDoubleConfirm(result: RuleResult) {
        pendingRuleResult = result
        binding.layoutNormal.visibility = View.GONE
        binding.layoutDoubleConfirm.visibility = View.VISIBLE
        binding.tvDoubleConfirmMessage.text = buildString {
            append(DeepQuestionBank.randomDoubleConfirm())
            result.message?.takeIf { it.isNotBlank() }?.let { append("\n\n").append(it) }
        }
    }

    private fun showBlockPage(message: String) {
        binding.layoutNormal.visibility = View.GONE
        binding.layoutBlock.visibility = View.VISIBLE
        binding.tvBlockMessage.text = message

        // 保存一条被阻断的记录（duration=0）
        val now = System.currentTimeMillis()
        val targetId = packageName ?: ""
        val realPkg = if (targetId.contains("#")) targetId.substringBefore("#") else targetId
        val subTarget = if (targetId.contains("#")) targetId.substringAfter("#") else null
        val blockedSession = com.focusgate.app.data.AppSession(
            packageName = realPkg,
            startTime = now,
            subTarget = subTarget,
            endTime = now,
            intentType = selectedIntent ?: com.focusgate.app.data.IntentType.UNKNOWN,
            plannedDurationMinutes = selectedMinutes,
            blockedByRule = "UnconsciousStreak",
            enforcedDurationMinutes = 0,
            mood = selectedMood
        )
        sessionStorage.saveSession(blockedSession)
        publishGateOutcome(Constants.GATE_OUTCOME_BLOCKED)
    }

    private fun saveAndFinish(
        doubleConfirmed: Boolean,
        enforcedDuration: Int? = null,
        intensity: com.focusgate.app.rule.ReminderIntensity = com.focusgate.app.rule.ReminderIntensity.NORMAL,
        ruleMessage: String? = null
    ) {
        val intentType = selectedIntent ?: IntentType.UNKNOWN
        val pkg = packageName
        val requestId = gateRequestId
        if (requestId.isNullOrBlank() || pkg.isNullOrBlank()) {
            Toast.makeText(this, "闸门状态已失效，请重新打开目标应用", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 使用同一次同步提交发布完整结果，避免 Service 读到一半写入的数据。
        val saved = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE).edit()
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
            .putString(Constants.KEY_LAST_GATE_TARGET, pkg)
            .putString(Constants.KEY_LAST_FOREGROUND_APP, pkg)
            .commit()
        if (!saved) {
            Toast.makeText(this, "保存选择失败，请重试", Toast.LENGTH_SHORT).show()
            return
        }

        // 用户确认继续，帮用户重新打开目标应用（因为之前被 GLOBAL_ACTION_HOME 切回桌面了）
        if (!pkg.isNullOrEmpty()) {
            val realPkg = if (pkg.contains("#")) pkg.substringBefore("#") else pkg
            val launchIntent = packageManager.getLaunchIntentForPackage(realPkg)
            if (launchIntent != null) {
                launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(launchIntent)
                Log.d("GateActivity", "Relaunching target app: $realPkg (target=$pkg)")
            } else {
                Toast.makeText(this, "无法自动打开应用，请手动点击", Toast.LENGTH_SHORT).show()
            }
        }

        finish()
    }

    private fun publishGateOutcome(outcome: String) {
        val requestId = gateRequestId ?: return
        getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE).edit()
            .putString(Constants.KEY_PENDING_GATE_ID, requestId)
            .putString(Constants.KEY_PENDING_GATE_OUTCOME, outcome)
            .commit()
    }

    private fun showTodayStats() {
        val todaySessions = sessionStorage.getTodaySessions()
        val opens = todaySessions.size
        val totalMin = todaySessions.sumOf { it.completedDurationMs } / 1000 / 60
        val appName = packageName?.let { getAppDisplayName(it) } ?: "短视频"
        val appOpens = todaySessions.count {
            val targetId = if (it.subTarget != null) "${it.packageName}#${it.subTarget}" else it.packageName
            targetId == packageName
        }
        binding.tvTodayStats.text = if (opens > 0) {
            "今天你已经打开了 $opens 次，总时长 ${totalMin} 分钟（$appName $appOpens 次）"
        } else {
            "今天第一次打开 — 确定要破功吗？"
        }
    }

    private fun startCountdown() {
        val startTime = System.currentTimeMillis()
        binding.btnConfirm.isEnabled = false
        binding.progressCountdown.max = countdownTotal.toInt()
        binding.progressCountdown.progress = 0

        val runnable = object : Runnable {
            override fun run() {
                val elapsed = System.currentTimeMillis() - startTime
                val remaining = countdownTotal - elapsed
                binding.progressCountdown.progress = elapsed.toInt()

                if (remaining <= 0) {
                    binding.tvCountdown.text = "时间到了，诚实地面对自己的选择"
                    binding.btnConfirm.text = "确认，继续打开"
                    binding.btnConfirm.isEnabled = true
                } else {
                    val sec = (remaining / 1000) + 1
                    binding.tvCountdown.text = "请停留 ${sec} 秒，再想想..."
                    binding.btnConfirm.text = "${sec}秒后再想想..."
                    handler.postDelayed(this, 100)
                }
            }
        }
        countdownRunnable = runnable
        handler.postDelayed(runnable, 100)
    }

    override fun onDestroy() {
        val requestId = gateRequestId
        if (requestId != null) {
            val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
            if (prefs.getString(Constants.KEY_VISIBLE_GATE_REQUEST_ID, null) == requestId) {
                prefs.edit().remove(Constants.KEY_VISIBLE_GATE_REQUEST_ID).commit()
            }
        }
        super.onDestroy()
        countdownRunnable?.let { handler.removeCallbacks(it) }
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
