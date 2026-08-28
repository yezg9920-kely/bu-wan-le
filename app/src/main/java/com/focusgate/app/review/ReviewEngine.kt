package com.focusgate.app.review

import com.focusgate.app.ai.TemplateTextEngine
import com.focusgate.app.data.AppSession
import com.focusgate.app.data.IntentType
import com.focusgate.app.data.MoodType

/**
 * 复盘统计引擎：计算用户可见的各项指标
 */
object ReviewEngine {

    data class ReviewStats(
        val totalOpens: Int,                 // 总打开次数
        val totalMinutes: Long,              // 总时长（分钟）
        val avgSessionMinutes: Long,         // 平均单次时长（分钟）
        val nightUsagePercent: Int,          // 夜间使用占比（%）
        val unconsciousPercent: Int,         // 无意图打开占比（%）
        val overtimeCount: Int,              // 超时会话次数
        val mostUsedApp: Pair<String, Int>?, // 使用最多的 App（名称，次数）
        val moodBreakdown: Map<MoodType, Int>,// 状态分布
        val intentBreakdown: Map<IntentType, Int>,// 意图分布
        val peakHour: Int,                   // 高峰时段（小时）
        val conclusion: String,              // 一句自动复盘结论
        val dailyReviewLine: String,
        val patternSummary: String
    )

    data class PeriodStats(
        val totalOpens: Int,
        val totalMinutes: Long,
        val avgSessionMinutes: Long,
        val nightUsagePercent: Int,
        val unconsciousPercent: Int,
        val overtimeCount: Int,
        val mostUsedApp: Pair<String, Int>?,
        val moodBreakdown: Map<MoodType, Float>,
        val intentBreakdown: Map<IntentType, Float>,
        val peakHour: Int,
        val conclusion: String,
        // 环比字段（与上一周期对比）
        val opensChangePct: Int,   // 打开次数变化百分比
        val minutesChangePct: Int, // 时长变化百分比
        val unconsciousChangePct: Int // 无意识占比变化（百分点）
    )

    data class DailyPoint(
        val dateLabel: String,   // "04-19"
        val opens: Int,
        val minutes: Long
    )

    fun calculate(sessions: List<AppSession>): ReviewStats {
        if (sessions.isEmpty()) {
            return emptyStats()
        }

        val closedSessions = sessions.filter { it.endTime != null }
        val totalOpens = sessions.size

        // 总时长（分钟）
        val totalMs = closedSessions.sumOf { it.durationMs }
        val totalMinutes = totalMs / 1000 / 60

        // 平均单次时长
        val avgSessionMinutes = if (closedSessions.isNotEmpty()) {
            totalMinutes / closedSessions.size
        } else 0

        // 夜间使用占比
        val nightMs = closedSessions.filter { it.isNightTime }.sumOf { it.durationMs }
        val nightUsagePercent = if (totalMs > 0) {
            ((nightMs * 100) / totalMs).toInt()
        } else 0

        // 无意图打开占比
        val unconsciousCount = sessions.count { it.intentType == IntentType.UNCONSCIOUS }
        val unconsciousPercent = (unconsciousCount * 100) / totalOpens

        // 超时会话（仅限已结束）
        val overtimeCount = closedSessions.count { it.isOvertime }

        // 使用最多的 App
        val appCounts = sessions.groupingBy { targetId(it) }.eachCount()
        val mostUsedAppEntry = appCounts.maxByOrNull { it.value }
        val mostUsedApp = mostUsedAppEntry?.let {
            Pair(getAppDisplayName(it.key), it.value)
        }

        // 状态分布
        val moodBreakdown = sessions.mapNotNull { it.mood }
            .groupingBy { it }.eachCount()

        // 意图分布
        val intentBreakdown = sessions
            .groupingBy { it.intentType }.eachCount()

        // 高峰时段
        val peakHour = sessions.groupBy {
            java.util.Calendar.getInstance().apply { timeInMillis = it.startTime }
                .get(java.util.Calendar.HOUR_OF_DAY)
        }.mapValues { it.value.size }
            .maxByOrNull { it.value }?.key ?: -1

        // 自动生成一句复盘结论
        val conclusion = generateConclusion(
            unconsciousPercent, nightUsagePercent, overtimeCount,
            totalOpens, avgSessionMinutes, mostUsedApp?.first
        )
        val dailyReviewLine = TemplateTextEngine.generateDailyReviewLine(
            totalMinutes, unconsciousPercent, nightUsagePercent, overtimeCount
        )
        val patternSummary = TemplateTextEngine.generatePatternSummary(
            hotspot = if (peakHour >= 0) String.format("%02d:00", peakHour) else "暂无高峰",
            appName = mostUsedApp?.first ?: "短视频",
            unconsciousPercent = unconsciousPercent
        )

        return ReviewStats(
            totalOpens = totalOpens,
            totalMinutes = totalMinutes,
            avgSessionMinutes = avgSessionMinutes,
            nightUsagePercent = nightUsagePercent,
            unconsciousPercent = unconsciousPercent,
            overtimeCount = overtimeCount,
            mostUsedApp = mostUsedApp,
            moodBreakdown = moodBreakdown,
            intentBreakdown = intentBreakdown,
            peakHour = peakHour,
            conclusion = conclusion,
            dailyReviewLine = dailyReviewLine,
            patternSummary = patternSummary
        )
    }

    private fun emptyStats(): ReviewStats = ReviewStats(
        totalOpens = 0,
        totalMinutes = 0,
        avgSessionMinutes = 0,
        nightUsagePercent = 0,
        unconsciousPercent = 0,
        overtimeCount = 0,
        mostUsedApp = null,
        moodBreakdown = emptyMap(),
        intentBreakdown = emptyMap(),
        peakHour = -1,
        conclusion = "暂无数据，去刷几条再来吧",
        dailyReviewLine = "今天还没有使用记录。",
        patternSummary = "暂无可识别的使用模式。"
    )

    private fun generateConclusion(
        unconsciousPct: Int,
        nightPct: Int,
        overtime: Int,
        totalOpens: Int,
        avgMin: Long,
        mostUsedApp: String?
    ): String {
        return when {
            totalOpens == 0 -> "暂无数据"
            unconsciousPct >= 50 -> "⚠️ 超过一半是无意识打开，${mostUsedApp ?: "短视频"} 正在驯化你的手指"
            nightPct >= 40 -> "🌙 夜间使用占比高，睡前刷机可能影响睡眠质量"
            overtime >= 3 -> "⏰ 有 ${overtime} 次超时，计划时长对你来说可能太紧了"
            avgMin <= 5 -> "👍 控制得不错，平均每次只刷 ${avgMin} 分钟"
            mostUsedApp != null -> "📱 ${mostUsedApp} 是你今天的主要阵地，平均每次停留 ${avgMin} 分钟"
            else -> "数据已生成，继续保持觉察"
        }
    }

    private fun getAppDisplayName(pkg: String): String = when (pkg) {
        "com.tencent.mm#finder" -> "微信视频号"
        "com.ss.android.ugc.aweme" -> "抖音"
        "com.ss.android.ugc.aweme.lite" -> "抖音极速版"
        "tv.danmaku.bili" -> "B站"
        "tv.danmaku.bilibilihd" -> "B站HD"
        "com.xingin.xhs" -> "小红书"
        "com.smile.gifmaker" -> "快手"
        "com.kuaishou.nebula" -> "快手极速版"
        else -> pkg
    }

    // ========== 历史复盘扩展方法 ==========

    fun calculateForPeriod(
        sessions: List<AppSession>,
        previousSessions: List<AppSession> = emptyList()
    ): PeriodStats {
        if (sessions.isEmpty()) {
            return emptyPeriodStats()
        }
        val closed = sessions.filter { it.endTime != null }
        val totalMs = closed.sumOf { it.durationMs }
        val totalMin = totalMs / 1000 / 60
        val avgMin = if (closed.isNotEmpty()) totalMin / closed.size else 0
        val nightMs = closed.filter { it.isNightTime }.sumOf { it.durationMs }
        val nightPct = if (totalMs > 0) ((nightMs * 100) / totalMs).toInt() else 0
        val uncoCount = sessions.count { it.intentType == IntentType.UNCONSCIOUS }
        val uncoPct = (uncoCount * 100) / sessions.size
        val otCount = closed.count { it.isOvertime }
        val appEntry = sessions.groupingBy { getAppDisplayName(targetId(it)) }
            .eachCount().maxByOrNull { it.value }
        val mostUsed = appEntry?.let { Pair(it.key, it.value) }
        val peak = sessions.groupBy {
            java.util.Calendar.getInstance().apply { timeInMillis = it.startTime }
                .get(java.util.Calendar.HOUR_OF_DAY)
        }.mapValues { it.value.size }.maxByOrNull { it.value }?.key ?: -1

        val moodMap = sessions.mapNotNull { it.mood }.groupingBy { it }.eachCount()
        val moodTotal = moodMap.values.sum().coerceAtLeast(1)
        val moodPct = moodMap.mapValues { (it.value * 100f) / moodTotal }

        val intentMap = sessions.groupingBy { it.intentType }.eachCount()
        val intentTotal = intentMap.values.sum().coerceAtLeast(1)
        val intentPct = intentMap.mapValues { (it.value * 100f) / intentTotal }

        // 环比计算
        val prevClosed = previousSessions.filter { it.endTime != null }
        val prevTotalMin = prevClosed.sumOf { it.durationMs } / 1000 / 60
        val opensChange = if (previousSessions.isNotEmpty()) {
            ((sessions.size - previousSessions.size) * 100) / previousSessions.size
        } else 0
        val minsChange = if (prevTotalMin > 0) {
            ((totalMin - prevTotalMin) * 100 / prevTotalMin).toInt()
        } else 0
        val prevUnco = if (previousSessions.isNotEmpty()) {
            (previousSessions.count { it.intentType == IntentType.UNCONSCIOUS } * 100) / previousSessions.size
        } else 0
        val uncoChange = uncoPct - prevUnco

        val conclusion = generatePeriodConclusion(
            totalMin, sessions.size, uncoPct, nightPct, otCount,
            mostUsed?.first, opensChange, minsChange, uncoChange
        )

        return PeriodStats(
            totalOpens = sessions.size,
            totalMinutes = totalMin,
            avgSessionMinutes = avgMin,
            nightUsagePercent = nightPct,
            unconsciousPercent = uncoPct,
            overtimeCount = otCount,
            mostUsedApp = mostUsed,
            moodBreakdown = moodPct,
            intentBreakdown = intentPct,
            peakHour = peak,
            conclusion = conclusion,
            opensChangePct = opensChange,
            minutesChangePct = minsChange.toInt(),
            unconsciousChangePct = uncoChange
        )
    }

    fun calculateDailyTrend(sessionsByDay: Map<String, List<AppSession>>): List<DailyPoint> {
        return sessionsByDay.toSortedMap().map { (date, list) ->
            val closed = list.filter { it.endTime != null }
            val mins = closed.sumOf { it.durationMs } / 1000 / 60
            DailyPoint(
                dateLabel = date.substring(5), // "04-19"
                opens = list.size,
                minutes = mins
            )
        }
    }

    fun calculateAppBreakdown(sessions: List<AppSession>): Map<String, Float> {
        if (sessions.isEmpty()) return emptyMap()
        val closed = sessions.filter { it.endTime != null }
        val totalMs = closed.sumOf { it.durationMs }.coerceAtLeast(1)
        return closed.groupBy { getAppDisplayName(targetId(it)) }
            .mapValues { (_, list) ->
                val ms = list.sumOf { it.durationMs }
                (ms * 100f) / totalMs
            }
            .toList().sortedByDescending { it.second }.toMap()
    }

    fun calculateIntentBreakdown(sessions: List<AppSession>): Map<IntentType, Float> {
        if (sessions.isEmpty()) return emptyMap()
        val total = sessions.size
        return sessions.groupingBy { it.intentType }.eachCount()
            .mapValues { (it.value * 100f) / total }
    }

    fun calculateMoodBreakdown(sessions: List<AppSession>): Map<MoodType, Float> {
        val moods = sessions.mapNotNull { it.mood }
        if (moods.isEmpty()) return emptyMap()
        val total = moods.size
        return moods.groupingBy { it }.eachCount()
            .mapValues { (it.value * 100f) / total }
    }

    private fun emptyPeriodStats(): PeriodStats = PeriodStats(
        totalOpens = 0, totalMinutes = 0, avgSessionMinutes = 0,
        nightUsagePercent = 0, unconsciousPercent = 0, overtimeCount = 0,
        mostUsedApp = null, moodBreakdown = emptyMap(),
        intentBreakdown = emptyMap(), peakHour = -1,
        conclusion = "暂无数据，去刷几条再来吧",
        opensChangePct = 0, minutesChangePct = 0, unconsciousChangePct = 0
    )

    private fun generatePeriodConclusion(
        totalMin: Long, opens: Int, uncoPct: Int, nightPct: Int,
        overtime: Int, mostUsedApp: String?,
        opensChange: Int, minsChange: Int, uncoChange: Int
    ): String {
        val parts = mutableListOf<String>()
        if (opens == 0) return "暂无数据"

        // 环比趋势
        when {
            opensChange < -20 -> parts.add("📵 打开次数比上周期下降 ${-opensChange}%")
            opensChange > 30 -> parts.add("🔁 打开次数比上周期增加 ${opensChange}%")
        }
        when {
            minsChange < -20 -> parts.add("📉 总时长比上周期下降 ${-minsChange}%，控制得不错")
            minsChange > 30 -> parts.add("📈 总时长比上周期增加 ${minsChange}%，注意觉察")
        }
        when {
            uncoChange < -10 -> parts.add("👍 无意识打开占比下降 ${-uncoChange} 个百分点，手指在恢复控制")
            uncoChange > 10 -> parts.add("⚠️ 无意识打开占比上升 ${uncoChange} 个百分点")
        }

        // 绝对指标
        if (uncoPct >= 50) parts.add("🤖 超过一半是无意识打开，${mostUsedApp ?: "短视频"} 正在驯化你的手指")
        if (nightPct >= 35) parts.add("🌙 夜间使用占比高，睡前刷机可能影响睡眠质量")
        if (overtime >= 3) parts.add("⏰ 有 ${overtime} 次超时，计划时长可能偏紧")

        if (parts.isEmpty()) {
            return if (totalMin <= 30) "👍 控制得不错，平均每次只刷 ${if (opens > 0) totalMin / opens else 0} 分钟"
            else "📱 ${mostUsedApp ?: "短视频"} 是主要阵地，总计 ${totalMin} 分钟"
        }
        return parts.joinToString("\n")
    }

    private fun targetId(session: AppSession): String {
        return if (session.subTarget != null) {
            "${session.packageName}#${session.subTarget}"
        } else {
            session.packageName
        }
    }
}
