package com.focusgate.app.review

import com.focusgate.app.data.AppSession
import com.focusgate.app.data.IntentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewEngineMetricsTest {

    @Test
    fun calculate_returnsRequestedCoreMetrics() {
        val sessions = listOf(
            session(
                packageName = "com.ss.android.ugc.aweme",
                start = at(hour = 22, minute = 0),
                end = at(hour = 22, minute = 10),
                intent = IntentType.RELAX,
                planned = 5
            ),
            session(
                packageName = "com.ss.android.ugc.aweme",
                start = at(hour = 23, minute = 30),
                end = at(hour = 23, minute = 45),
                intent = IntentType.UNCONSCIOUS,
                planned = 10
            )
        )

        val stats = ReviewEngine.calculate(sessions)

        assertEquals(2, stats.totalOpens)
        assertEquals(25, stats.totalMinutes)
        assertEquals(12, stats.avgSessionMinutes)
        assertEquals(60, stats.nightUsagePercent)
        assertEquals(50, stats.unconsciousPercent)
        assertEquals(2, stats.overtimeCount)
        assertTrue(stats.dailyReviewLine.isNotBlank())
        assertTrue(stats.patternSummary.isNotBlank())
    }

    private fun session(
        packageName: String,
        start: Long,
        end: Long,
        intent: IntentType,
        planned: Int
    ): AppSession {
        return AppSession(
            packageName = packageName,
            startTime = start,
            endTime = end,
            intentType = intent,
            plannedDurationMinutes = planned
        )
    }

    private fun at(hour: Int, minute: Int): Long {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
        }
        return cal.timeInMillis
    }
}
