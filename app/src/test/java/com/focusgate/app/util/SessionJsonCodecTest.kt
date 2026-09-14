package com.focusgate.app.util

import com.focusgate.app.data.AppSession
import com.focusgate.app.data.IntentType
import com.focusgate.app.data.MoodType
import com.focusgate.app.rule.ReminderIntensity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionJsonCodecTest {
    @Test
    fun `round trip preserves completed session`() {
        val session = AppSession(
            packageName = "com.example.video",
            startTime = 100,
            endTime = 200,
            subTarget = "feed",
            intentType = IntentType.INFO,
            plannedDurationMinutes = 12,
            enforcedDurationMinutes = 10,
            reminderIntensity = ReminderIntensity.STRICT,
            doubleConfirmed = true,
            blockedByRule = "daily-limit",
            mood = MoodType.TIRED
        )

        assertEquals(listOf(session), SessionJsonCodec.decode(SessionJsonCodec.encode(listOf(session))))
    }

    @Test
    fun `damaged item is skipped while valid item remains`() {
        val raw = """[{"missing":"fields"},{"pkg":"a","start":1,"end":2}]"""

        val decoded = SessionJsonCodec.decode(raw)

        assertEquals(1, decoded.size)
        assertEquals("a", decoded.single().packageName)
        assertNull(decoded.single().subTarget)
    }
}
