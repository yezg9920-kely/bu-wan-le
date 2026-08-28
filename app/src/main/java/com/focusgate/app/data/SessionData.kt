package com.focusgate.app.data

import com.focusgate.app.rule.ReminderIntensity

data class AppSession(
    val packageName: String,
    /** 开始时间：检测到目标应用时设置，用户确认后更新为确认时间（确保计时准确） */
    var startTime: Long,
    var endTime: Long? = null,
    /** 子目标标识（如微信视频号="finder"），独立应用为 null */
    var subTarget: String? = null,
    var intentType: IntentType = IntentType.UNKNOWN,
    var plannedDurationMinutes: Int = 10,
    /** 规则引擎计算后的实际允许时长 */
    var enforcedDurationMinutes: Int? = null,
    /** 本次提醒强度 */
    var reminderIntensity: ReminderIntensity = ReminderIntensity.NORMAL,
    /** 是否经过了二次确认 */
    var doubleConfirmed: Boolean = false,
    /** 是否被规则阻断 */
    var blockedByRule: String? = null,
    /** 用户打开前的状态（模块 E） */
    var mood: MoodType? = null
) {
    val durationMs: Long
        get() = ((endTime ?: System.currentTimeMillis()) - startTime).coerceAtLeast(0L)

    /** 统计只计算已经结束的会话，避免崩溃遗留的开放记录随时间无限增长。 */
    val completedDurationMs: Long
        get() = endTime?.let { (it - startTime).coerceAtLeast(0L) } ?: 0L

    val isNightTime: Boolean
        get() {
            val hour = java.util.Calendar.getInstance().apply { timeInMillis = startTime }
                .get(java.util.Calendar.HOUR_OF_DAY)
            return hour >= 23 || hour < 7
        }

    /** 实际使用的时长上限 */
    val effectiveDurationMinutes: Int
        get() = enforcedDurationMinutes ?: plannedDurationMinutes

    /** 是否超时（需要已结束） */
    val isOvertime: Boolean
        get() {
            val end = endTime ?: return false
            return (end - startTime) > effectiveDurationMinutes * 60_000L
        }
}

enum class IntentType {
    RELAX,      // 放松
    INFO,       // 找信息
    INSPIRATION,// 找灵感
    UNCONSCIOUS,// 下意识点开
    UNKNOWN
}

data class DailySummary(
    val date: String,
    val totalOpens: Int,
    val totalSessionMinutes: Long,
    val appBreakdown: Map<String, Int>,
    val intentBreakdown: Map<IntentType, Int>,
    val peakHour: Int,
    val mostUncontrolledApp: String?
)
