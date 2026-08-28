package com.focusgate.app.rule

import com.focusgate.app.data.AppSession
import com.focusgate.app.data.IntentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleEngineTest {

    @Test
    fun `night mode keeps selected duration and only changes intensity`() {
        val context = RuleContext(
            packageName = "com.ss.android.ugc.aweme",
            currentIntent = IntentType.RELAX,
            userSelectedDuration = 15,
            currentTimeMillis = getTimeAtHour(23)
        )
        val result = RuleEngine.defaultRules.first { it is NightModeRule }.evaluate(context)
        assertEquals(null, result.maxDurationMinutes)
        assertEquals(ReminderIntensity.STRICT, result.reminderIntensity)
    }

    @Test
    fun `unconscious streak blocks after 5 consecutive`() {
        val sessions = List(5) {
            AppSession("com.ss.android.ugc.aweme", System.currentTimeMillis(), intentType = IntentType.UNCONSCIOUS)
        }
        val context = RuleContext(
            packageName = "com.ss.android.ugc.aweme",
            currentIntent = IntentType.UNCONSCIOUS,
            allSessions = sessions
        )
        val result = RuleEngine.defaultRules.first { it is UnconsciousStreakRule }.evaluate(context)
        assertTrue(result.blockEntirely)
    }

    @Test
    fun `unconscious streak requires double confirm after 3`() {
        val sessions = List(2) {
            AppSession("com.ss.android.ugc.aweme", System.currentTimeMillis(), intentType = IntentType.UNCONSCIOUS)
        }
        val context = RuleContext(
            packageName = "com.ss.android.ugc.aweme",
            currentIntent = IntentType.UNCONSCIOUS,
            allSessions = sessions
        )
        val result = RuleEngine.defaultRules.first { it is UnconsciousStreakRule }.evaluate(context)
        assertTrue(result.requireDoubleConfirm)
        assertTrue(!result.blockEntirely)
    }

    @Test
    fun `deliberate intent resets unconscious enforcement`() {
        val sessions = List(5) {
            AppSession("com.ss.android.ugc.aweme", System.currentTimeMillis(), intentType = IntentType.UNCONSCIOUS)
        }
        val context = RuleContext(
            packageName = "com.ss.android.ugc.aweme",
            currentIntent = IntentType.INFO,
            allSessions = sessions
        )

        val result = RuleEngine.defaultRules.first { it is UnconsciousStreakRule }.evaluate(context)

        assertTrue(!result.blockEntirely)
        assertTrue(!result.requireDoubleConfirm)
    }

    @Test
    fun `engine merges rules correctly`() {
        val sessions = List(3) {
            AppSession("com.ss.android.ugc.aweme", System.currentTimeMillis(), intentType = IntentType.UNCONSCIOUS)
        }
        val context = RuleContext(
            packageName = "com.ss.android.ugc.aweme",
            currentIntent = IntentType.UNCONSCIOUS,
            userSelectedDuration = 15,
            currentTimeMillis = getTimeAtHour(23),
            allSessions = sessions
        )
        val engine = RuleEngine()
        val result = engine.evaluate(context)
        // UnconsciousStreak (priority 10) + NightMode (priority 20) should both apply
        assertTrue(result.requireDoubleConfirm)
        assertEquals(15, result.maxDurationMinutes)
        assertEquals(ReminderIntensity.STRICT, result.reminderIntensity)
    }

    @Test
    fun `wechat finder subtarget receives normal rules`() {
        val context = RuleContext(
            packageName = "com.tencent.mm#finder",
            currentIntent = IntentType.UNCONSCIOUS,
            userSelectedDuration = 15,
            currentTimeMillis = getTimeAtHour(23)
        )

        val result = RuleEngine().evaluate(context)

        assertEquals(15, result.maxDurationMinutes)
        assertEquals(ReminderIntensity.STRICT, result.reminderIntensity)
    }

    private fun getTimeAtHour(hour: Int): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, hour)
        cal.set(java.util.Calendar.MINUTE, 0)
        return cal.timeInMillis
    }
}
