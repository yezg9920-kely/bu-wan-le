package com.focusgate.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.focusgate.app.data.MoodType
import com.focusgate.app.databinding.ActivityReviewBinding
import com.focusgate.app.review.ReviewEngine
import com.focusgate.app.util.SessionStorage

class ReviewActivity : AppCompatActivity() {
    private lateinit var binding: ActivityReviewBinding
    private lateinit var sessionStorage: SessionStorage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionStorage = SessionStorage(this)

        binding.btnBack.setOnClickListener { finish() }

        binding.btnHistory.setOnClickListener {
            startActivity(Intent(this, HistoryReviewActivity::class.java))
        }

        renderTodayReview()
    }

    private fun renderTodayReview() {
        val sessions = sessionStorage.getTodaySessions()
        val stats = ReviewEngine.calculate(sessions)

        binding.tvConclusion.text = stats.conclusion

        binding.tvTotalOpens.text = "${stats.totalOpens} 次"
        binding.tvTotalMinutes.text = "${stats.totalMinutes} 分钟"
        binding.tvAvgMinutes.text = "${stats.avgSessionMinutes} 分钟"
        binding.tvNightPercent.text = "${stats.nightUsagePercent}%"
        binding.tvUnconsciousPercent.text = "${stats.unconsciousPercent}%"
        binding.tvOvertimeCount.text = "${stats.overtimeCount} 次"

        val peakText = if (stats.peakHour in 0..23) {
            "${stats.peakHour}:00 - ${stats.peakHour + 1}:00"
        } else "暂无"
        binding.tvPeakHour.text = peakText

        val appText = stats.mostUsedApp?.let {
            "${it.first} (${it.second} 次)"
        } ?: "暂无"
        binding.tvMostUsedApp.text = appText

        binding.tvMoodBreakdown.text = formatMoodBreakdown(stats.moodBreakdown)
        binding.tvIntentBreakdown.text = formatIntentBreakdown(stats.intentBreakdown)
    }

    private fun formatMoodBreakdown(map: Map<MoodType, Int>): String {
        if (map.isEmpty()) return "暂无数据"
        return map.entries.joinToString("\n") { (mood, count) ->
            val label = when (mood) {
                MoodType.TIRED -> "😫 累"
                MoodType.ANNOYED -> "😤 烦"
                MoodType.ESCAPE -> "😶‍🌫️ 想放空"
                MoodType.RESEARCH -> "🔍 找资料"
                MoodType.ITCHY -> "👆 手痒"
            }
            "  $label: ${count} 次"
        }
    }

    private fun formatIntentBreakdown(map: Map<com.focusgate.app.data.IntentType, Int>): String {
        if (map.isEmpty()) return "暂无数据"
        return map.entries.joinToString("\n") { (intent, count) ->
            val label = when (intent) {
                com.focusgate.app.data.IntentType.RELAX -> "🛋️ 放松"
                com.focusgate.app.data.IntentType.INFO -> "📰 找信息"
                com.focusgate.app.data.IntentType.INSPIRATION -> "💡 找灵感"
                com.focusgate.app.data.IntentType.UNCONSCIOUS -> "🤖 无意识"
                com.focusgate.app.data.IntentType.UNKNOWN -> "❓ 未知"
            }
            "  $label: ${count} 次"
        }
    }
}
