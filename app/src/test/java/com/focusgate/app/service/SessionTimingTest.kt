package com.focusgate.app.service

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionTimingTest {
    @Test
    fun selectedDurationCreatesExactlyOneReminderAtSelectedTime() {
        assertEquals(listOf(5 * 60_000L), SessionTiming.reminderOffsetsMs(5))
        assertEquals(listOf(10 * 60_000L), SessionTiming.reminderOffsetsMs(10))
        assertEquals(listOf(15 * 60_000L), SessionTiming.reminderOffsetsMs(15))
    }

    @Test
    fun restoredTimerOnlyWaitsForRemainingDuration() {
        val start = 1_000_000L
        val now = start + 8 * 60_000L
        assertEquals(2 * 60_000L, SessionTiming.remainingMs(start, 10, now))
    }

    @Test
    fun extendingAtOriginalDeadlineAddsOnlyRequestedMinutes() {
        val start = 1_000_000L
        val now = start + 10 * 60_000L
        assertEquals(5 * 60_000L, SessionTiming.remainingMs(start, 15, now))
    }

    @Test
    fun gateBlockIsAnchoredToConfirmedStart() {
        val start = 1_000_000L
        assertEquals(start + 15 * 60_000L, SessionTiming.gateBlockedUntilMs(start, 10, 5))
    }
}
