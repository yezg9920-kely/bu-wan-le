package com.focusgate.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.focusgate.app.databinding.ActivityInterruptBinding
import com.focusgate.app.util.DeepQuestionBank

/**
 * 超时中断页：到达本次计划时长后弹出，每次显示不同的深刻问题
 */
class InterruptActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_DURATION = "extra_duration"
        const val EXTRA_QUESTION = "extra_question"
    }

    private lateinit var binding: ActivityInterruptBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInterruptBinding.inflate(layoutInflater)
        setContentView(binding.root)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })

        val pkg = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""
        val duration = intent.getIntExtra(EXTRA_DURATION, 0)
        val appName = getAppDisplayName(pkg)

        // 从问题库随机选一个深刻问题作为主标题
        val question = intent.getStringExtra(EXTRA_QUESTION) ?: DeepQuestionBank.randomTimeout()
        binding.tvQuestion.text = question
        binding.tvSubtitle.text = "—— $appName · 原定 $duration 分钟 · 时间已到 ——"

        binding.btnExtend5.setOnClickListener {
            // 延长 5 分钟：发广播给服务
            sendBroadcast(Intent("com.focusgate.app.EXTEND_SESSION").apply {
                setPackage(packageName)
                putExtra("extend_minutes", 5)
            })
            finish()
        }

        binding.btnGoHome.setOnClickListener {
            // 返回桌面
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
