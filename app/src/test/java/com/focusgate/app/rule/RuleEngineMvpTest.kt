package com.focusgate.app.rule

import com.focusgate.app.Constants
import com.focusgate.app.data.IntentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class RuleEngineMvpTest {

    @Test
    fun nightTargetApp_keepsSelectedMinutesAndUsesStrictIntensity() {
        val engine = RuleEngine()
        val nightTime = calendarAt(hourOfDay = 23)
        val context = RuleContext(
            packageName = "com.ss.android.ugc.aweme",
            currentIntent = IntentType.INFO,
            userSelectedDuration = 15,
            currentTimeMillis = nightTime,
            targetPackages = Constants.TARGET_PACKAGES
        )

        val result = engine.evaluate(context)

        assertEquals(15, result.maxDurationMinutes)
        assertEquals(ReminderIntensity.STRICT, result.reminderIntensity)
        assertTrue(result.triggeredRules.contains("NightMode"))
    }

    @Test
    fun relaxIntent_setsMediumReminder() {
        val engine = RuleEngine()
        val daytime = calendarAt(hourOfDay = 15)
        val context = RuleContext(
            packageName = "com.ss.android.ugc.aweme",
            currentIntent = IntentType.RELAX,
            userSelectedDuration = 10,
            currentTimeMillis = daytime
        )

        val result = engine.evaluate(context)

        assertEquals(ReminderIntensity.NORMAL, result.reminderIntensity)
        assertTrue(result.triggeredRules.contains("IntentStrength"))
    }

    @Test
    fun unconsciousThreeStreak_requiresDoubleConfirm() {
        val engine = RuleEngine()
        val sessions = listOf(
            fakeSession(IntentType.UNCONSCIOUS, 1_000L),
            fakeSession(IntentType.UNCONSCIOUS, 2_000L)
        )
        val context = RuleContext(
            packageName = "com.ss.android.ugc.aweme",
            currentIntent = IntentType.UNCONSCIOUS,
            userSelectedDuration = 10,
            allSessions = sessions,
            todaySessions = sessions
        )

        val result = engine.evaluate(context)

        assertTrue(result.requireDoubleConfirm)
        assertTrue(result.triggeredRules.contains("UnconsciousStreak"))
    }

    private fun calendarAt(hourOfDay: Int): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hourOfDay)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun fakeSession(intentType: IntentType, start: Long) =
        com.focusgate.app.data.AppSession(
            packageName = "com.ss.android.ugc.aweme",
            startTime = start,
            endTime = start + 1_000L,
            intentType = intentType,
            plannedDurationMinutes = 10
        )
}
