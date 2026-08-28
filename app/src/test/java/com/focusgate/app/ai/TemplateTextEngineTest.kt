package com.focusgate.app.ai

import com.focusgate.app.data.IntentType
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateTextEngineTest {

    @Test
    fun generateDailyReviewLine_returnsNonEmptySentence() {
        val line = TemplateTextEngine.generateDailyReviewLine(
            totalMinutes = 48,
            unconsciousPercent = 30,
            nightUsagePercent = 40,
            overtimeCount = 2
        )

        assertTrue(line.isNotBlank())
    }

    @Test
    fun generatePatternSummary_mentionsHotspotWhenAvailable() {
        val summary = TemplateTextEngine.generatePatternSummary(
            hotspot = "23:30",
            appName = "Douyin",
            unconsciousPercent = 55
        )

        assertTrue(summary.contains("23:30"))
        assertTrue(summary.contains("Douyin"))
    }

    @Test
    fun generateReminderCopy_returnsContextualMessage() {
        val copy = TemplateTextEngine.generateReminderCopy(
            stage = ReminderStage.TIME_UP,
            appName = "Douyin",
            intentType = IntentType.UNCONSCIOUS,
            isNight = true
        )

        assertTrue(copy.isNotBlank())
        assertTrue(copy.contains("Douyin"))
    }
}
