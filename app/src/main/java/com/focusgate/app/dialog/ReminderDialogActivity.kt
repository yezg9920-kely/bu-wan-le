package com.focusgate.app.dialog

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.focusgate.app.databinding.ActivityReminderDialogBinding

/**
 * 半屏提醒 DialogActivity（中等干预档）
 * 用于中途温和打断，比通知更强，比全屏中断更轻。
 */
class ReminderDialogActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_ACTION = "extra_action"
    }

    private lateinit var binding: ActivityReminderDialogBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReminderDialogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 让 Activity 看起来像对话框（半屏、背景暗化）
        window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            android.view.WindowManager.LayoutParams.WRAP_CONTENT
        )
        window?.setDimAmount(0.6f)

        val title = intent.getStringExtra(EXTRA_TITLE) ?: "提醒"
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: "时间到了"
        val action = intent.getStringExtra(EXTRA_ACTION)
        val pkg = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""
        val appName = getAppDisplayName(pkg)

        if (action == "TIMEUP") {
            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = Unit
            })
        }

        binding.tvDialogTitle.text = title
        binding.tvDialogMessage.text = message
        binding.tvDialogAppName.text = appName

        if (action == "TIMEUP") {
            binding.btnContinue.text = "再刷 5 分钟"
            binding.btnContinue.setOnClickListener {
                sendBroadcast(Intent("com.focusgate.app.EXTEND_SESSION").apply {
                    setPackage(packageName)
                    putExtra("extend_minutes", 5)
                })
                finish()
            }
        } else {
            binding.btnContinue.text = "继续看"
            binding.btnContinue.setOnClickListener {
                finish()
            }
        }

        binding.btnExit.setOnClickListener {
            val home = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(home)
            finish()
        }
    }

    private fun getAppDisplayName(pkg: String): String = when (pkg) {
        "com.ss.android.ugc.aweme" -> "抖音"
        "com.ss.android.ugc.aweme.lite" -> "抖音极速版"
        "tv.danmaku.bili" -> "B站"
        "tv.danmaku.bilibilihd" -> "B站HD"
        "com.xingin.xhs" -> "小红书"
        "com.smile.gifmaker" -> "快手"
        "com.kuaishou.nebula" -> "快手极速版"
        else -> pkg
    }
}
