package com.focusgate.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRecoveryPolicyTest {
    @Test
    fun sameForegroundTargetRestoresWithoutThirtySecondHeartbeatLimit() {
        val start = 1_000_000L
        assertTrue(
            SessionRecoveryPolicy.isRestorable(
                savedTargetId = "com.xingin.xhs",
                currentTargetId = "com.xingin.xhs",
                startTimeMs = start,
                effectiveMinutes = 10,
                nowMs = start + 4 * 60_000L,
                bufferMinutes = 5
            )
        )
    }

    @Test
    fun differentOrExpiredTargetIsNotRestored() {
        val start = 1_000_000L
        assertFalse(
            SessionRecoveryPolicy.isRestorable(
                "com.xingin.xhs", "com.ss.android.ugc.aweme", start, 10,
                start + 60_000L, 5
            )
        )
        assertFalse(
            SessionRecoveryPolicy.isRestorable(
                "com.xingin.xhs", "com.xingin.xhs", start, 10,
                start + 15 * 60_000L, 5
            )
        )
    }

    @Test
    fun orphanRepairNeverCountsPastSelectedDuration() {
        val start = 1_000_000L
        assertEquals(
            start + 5 * 60_000L,
            SessionRecoveryPolicy.repairedEndTime(start, 5, start + 90 * 60_000L)
        )
        assertEquals(
            start + 2 * 60_000L,
            SessionRecoveryPolicy.repairedEndTime(start, 5, start + 2 * 60_000L)
        )
    }
}
