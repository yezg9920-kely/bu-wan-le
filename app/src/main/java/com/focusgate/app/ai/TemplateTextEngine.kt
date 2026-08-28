package com.focusgate.app.ai

import com.focusgate.app.data.IntentType

enum class ReminderStage {
    MIDWAY,
    TIME_UP
}

/** 本地模板文案，不联网、不上传行为数据。 */
object TemplateTextEngine {
    fun generateDailyReviewLine(
        totalMinutes: Long,
        unconsciousPercent: Int,
        nightUsagePercent: Int,
        overtimeCount: Int
    ): String = when {
        unconsciousPercent >= 50 -> "今天有 $unconsciousPercent% 的打开是下意识行为，先把最常见的触发点记下来。"
        nightUsagePercent >= 40 -> "今天夜间使用占 $nightUsagePercent%，睡前留一段无屏幕时间会更舒服。"
        overtimeCount > 0 -> "今天共使用 $totalMinutes 分钟，其中 $overtimeCount 次超过计划时长。"
        else -> "今天共使用 $totalMinutes 分钟，你正在把注意力重新拿回来。"
    }

    fun generatePatternSummary(hotspot: String, appName: String, unconsciousPercent: Int): String {
        return "$hotspot 是你最容易打开 $appName 的时段，无意识打开占 $unconsciousPercent%。"
    }

    fun generateReminderCopy(
        stage: ReminderStage,
        appName: String,
        intentType: IntentType,
        isNight: Boolean
    ): String {
        val stageText = if (stage == ReminderStage.TIME_UP) "计划时间到了" else "先停一下"
        val intentText = if (intentType == IntentType.UNCONSCIOUS) "这次打开看起来更像下意识动作" else "还记得刚才打开它的目的吗"
        val nightText = if (isNight) "，现在已是夜间" else ""
        return "$appName：$stageText$nightText。$intentText。"
    }
}
