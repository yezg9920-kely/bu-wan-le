package com.focusgate.app

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.focusgate.app.data.IntentType
import com.focusgate.app.data.MoodType
import com.focusgate.app.databinding.ActivityHistoryReviewBinding
import com.focusgate.app.review.ReviewEngine
import com.focusgate.app.util.SessionStorage
import com.focusgate.app.view.HorizontalBarChartView
import com.google.android.material.tabs.TabLayout
import java.text.SimpleDateFormat
import java.util.*

class HistoryReviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryReviewBinding
    private lateinit var sessionStorage: SessionStorage
    private val dateFormat = SimpleDateFormat("MM-dd", Locale.getDefault())
    private val fullDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryReviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionStorage = SessionStorage(this)

        binding.btnBack.setOnClickListener { finish() }

        binding.tabPeriod.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> refreshDayView()
                    1 -> refreshWeekView()
                    2 -> refreshMonthView()
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        // Default to day view
        refreshDayView()
    }

    // ==================== 日视图 ====================
    private fun refreshDayView() {
        val cal = Calendar.getInstance()
        val todayStart = startOfDay(cal)
        val todayEnd = System.currentTimeMillis()
        val todaySessions = sessionStorage.getSessionsBetween(todayStart, todayEnd)

        // 昨天环比
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterdayStart = startOfDay(cal)
        val yesterdayEnd = todayStart
        val yesterdaySessions = sessionStorage.getSessionsBetween(yesterdayStart, yesterdayEnd)

        val stats = ReviewEngine.calculateForPeriod(todaySessions, yesterdaySessions)
        renderStats(stats)

        // 24小时柱状图
        binding.tvTrendTitle.text = "今日24小时分布（分钟）"
        binding.barChart.visibility = android.view.View.VISIBLE
        binding.lineChart.visibility = android.view.View.GONE
        val hourly = sessionStorage.getHourlyDistributionForDate(todayStart)
        val values = hourly.map { it.toFloat() }
        val labels = (0..23).map { "${it}h" }
        binding.barChart.setData(values, labels, ContextCompat.getColor(this, R.color.chart_purple))
        binding.barChart.startAnimation()

        renderBreakdowns(todaySessions)
    }

    // ==================== 周视图 ====================
    private fun refreshWeekView() {
        val cal = Calendar.getInstance()
        val endToday = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, -6)
        val weekStart = startOfDay(cal)
        val weekSessions = sessionStorage.getSessionsBetween(weekStart, endToday)

        // 上周环比
        cal.add(Calendar.DAY_OF_YEAR, -7)
        val prevWeekStart = startOfDay(cal)
        val prevWeekEnd = weekStart
        val prevWeekSessions = sessionStorage.getSessionsBetween(prevWeekStart, prevWeekEnd)

        val stats = ReviewEngine.calculateForPeriod(weekSessions, prevWeekSessions)
        renderStats(stats)

        // 7天折线图
        binding.tvTrendTitle.text = "近7天使用趋势（分钟）"
        binding.barChart.visibility = android.view.View.GONE
        binding.lineChart.visibility = android.view.View.VISIBLE

        val weekData = sessionStorage.getLastNDaysSessions(7)
        val trend = ReviewEngine.calculateDailyTrend(weekData)
        val values = trend.map { it.minutes.toFloat() }
        val labels = trend.map { it.dateLabel }
        binding.lineChart.setData(values, labels, ContextCompat.getColor(this, R.color.chart_teal))
        binding.lineChart.startAnimation()

        renderBreakdowns(weekSessions)
    }

    // ==================== 月视图 ====================
    private fun refreshMonthView() {
        val cal = Calendar.getInstance()
        val endToday = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, -29)
        val monthStart = startOfDay(cal)
        val monthSessions = sessionStorage.getSessionsBetween(monthStart, endToday)

        // 上月环比（简化：再往前30天）
        cal.add(Calendar.DAY_OF_YEAR, -30)
        val prevMonthStart = startOfDay(cal)
        val prevMonthEnd = monthStart
        val prevMonthSessions = sessionStorage.getSessionsBetween(prevMonthStart, prevMonthEnd)

        val stats = ReviewEngine.calculateForPeriod(monthSessions, prevMonthSessions)
        renderStats(stats)

        // 30天柱状图
        binding.tvTrendTitle.text = "近30天使用趋势（分钟）"
        binding.barChart.visibility = android.view.View.VISIBLE
        binding.lineChart.visibility = android.view.View.GONE

        val monthData = sessionStorage.getLastNDaysSessions(30)
        val trend = ReviewEngine.calculateDailyTrend(monthData)
        val values = trend.map { it.minutes.toFloat() }
        val labels = trend.map { it.dateLabel }
        binding.barChart.setData(values, labels, ContextCompat.getColor(this, R.color.chart_orange))
        binding.barChart.startAnimation()

        renderBreakdowns(monthSessions)
    }

    // ==================== 通用渲染 ====================
    private fun renderStats(stats: ReviewEngine.PeriodStats) {
        // Opens
        binding.animOpens.setTargetNumber(stats.totalOpens.toLong(), "")
        binding.animOpens.setTextSize(28f)
        binding.animOpens.setTextColor(ContextCompat.getColor(this, R.color.black))
        binding.animOpens.startAnimation()
        renderChangeText(binding.tvOpensChange, stats.opensChangePct, "次")

        // Minutes
        binding.animMinutes.setTargetNumber(stats.totalMinutes, "'")
        binding.animMinutes.setTextSize(28f)
        binding.animMinutes.setTextColor(ContextCompat.getColor(this, R.color.black))
        binding.animMinutes.startAnimation()
        renderChangeText(binding.tvMinutesChange, stats.minutesChangePct, "分钟")

        // Avg
        binding.animAvg.setTargetNumber(stats.avgSessionMinutes, "'")
        binding.animAvg.setTextSize(28f)
        binding.animAvg.setTextColor(ContextCompat.getColor(this, R.color.black))
        binding.animAvg.startAnimation()

        // Overtime
        binding.animOvertime.setTargetNumber(stats.overtimeCount.toLong(), "")
        binding.animOvertime.setTextSize(28f)
        binding.animOvertime.setTextColor(ContextCompat.getColor(this, R.color.black))
        binding.animOvertime.startAnimation()

        // Conclusion
        binding.tvConclusion.text = stats.conclusion
    }

    private fun renderChangeText(tv: TextView, changePct: Int, unit: String) {
        when {
            changePct < 0 -> {
                tv.text = "↓ ${-changePct}% $unit"
                tv.setTextColor(ContextCompat.getColor(this, R.color.positive_green))
            }
            changePct > 0 -> {
                tv.text = "↑ ${changePct}% $unit"
                tv.setTextColor(ContextCompat.getColor(this, R.color.negative_red))
            }
            else -> {
                tv.text = "— 持平"
                tv.setTextColor(ContextCompat.getColor(this, R.color.chart_gray))
            }
        }
    }

    private fun renderBreakdowns(sessions: List<com.focusgate.app.data.AppSession>) {
        // App Donut
        val appBreakdown = ReviewEngine.calculateAppBreakdown(sessions)
        binding.donutChart.setData(appBreakdown)
        binding.donutChart.startAnimation()

        // Intent Horizontal Bar
        val intentBreakdown = ReviewEngine.calculateIntentBreakdown(sessions)
        val intentItems = intentBreakdown.map { (intent, pct) ->
            val label = when (intent) {
                IntentType.RELAX -> "🛋️ 放松"
                IntentType.INFO -> "📰 找信息"
                IntentType.INSPIRATION -> "💡 找灵感"
                IntentType.UNCONSCIOUS -> "🤖 无意识"
                IntentType.UNKNOWN -> "❓ 未知"
            }
            val color = when (intent) {
                IntentType.RELAX -> ContextCompat.getColor(this, R.color.chart_purple)
                IntentType.INFO -> ContextCompat.getColor(this, R.color.chart_teal)
                IntentType.INSPIRATION -> ContextCompat.getColor(this, R.color.chart_orange)
                IntentType.UNCONSCIOUS -> ContextCompat.getColor(this, R.color.chart_pink)
                IntentType.UNKNOWN -> ContextCompat.getColor(this, R.color.chart_gray)
            }
            HorizontalBarChartView.BarItem(label, pct, color)
        }.sortedByDescending { it.percent }
        binding.intentBarChart.setData(intentItems)
        binding.intentBarChart.startAnimation()

        // Mood Horizontal Bar
        val moodBreakdown = ReviewEngine.calculateMoodBreakdown(sessions)
        val moodItems = moodBreakdown.map { (mood, pct) ->
            val label = when (mood) {
                MoodType.TIRED -> "😫 累"
                MoodType.ANNOYED -> "😤 烦"
                MoodType.ESCAPE -> "😶‍🌫️ 想放空"
                MoodType.RESEARCH -> "🔍 找资料"
                MoodType.ITCHY -> "👆 手痒"
            }
            val color = when (mood) {
                MoodType.TIRED -> ContextCompat.getColor(this, R.color.chart_blue)
                MoodType.ANNOYED -> ContextCompat.getColor(this, R.color.chart_pink)
                MoodType.ESCAPE -> ContextCompat.getColor(this, R.color.chart_purple)
                MoodType.RESEARCH -> ContextCompat.getColor(this, R.color.chart_teal)
                MoodType.ITCHY -> ContextCompat.getColor(this, R.color.chart_orange)
            }
            HorizontalBarChartView.BarItem(label, pct, color)
        }.sortedByDescending { it.percent }
        binding.moodBarChart.setData(moodItems)
        binding.moodBarChart.startAnimation()
    }

    private fun startOfDay(cal: Calendar): Long {
        return cal.clone().let {
            (it as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }
    }
}
