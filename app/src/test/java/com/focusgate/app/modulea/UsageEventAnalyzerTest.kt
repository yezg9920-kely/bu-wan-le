package com.focusgate.app.modulea

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsageEventAnalyzerTest {

    @Test
    fun latestForeground_returnsNewestForegroundEvent() {
        val events = listOf(
            UsageEventRecord("com.example.first", 1_000L, UsageEventRecord.Kind.OTHER),
            UsageEventRecord("com.example.first", 2_000L, UsageEventRecord.Kind.FOREGROUND),
            UsageEventRecord("com.example.second", 3_000L, UsageEventRecord.Kind.RESUMED)
        )

        val latest = UsageEventAnalyzer.latestForeground(events)

        assertEquals("com.example.second", latest?.packageName)
        assertEquals(3_000L, latest?.timestamp)
    }

    @Test
    fun latestForeground_returnsNullWhenNoForegroundSignals() {
        val events = listOf(
            UsageEventRecord("com.example.first", 1_000L, UsageEventRecord.Kind.OTHER),
            UsageEventRecord("com.example.second", 2_000L, UsageEventRecord.Kind.OTHER)
        )

        val latest = UsageEventAnalyzer.latestForeground(events)

        assertNull(latest)
    }

    @Test
    fun countLaunches_deduplicatesFastForegroundPairs() {
        val events = listOf(
            UsageEventRecord("com.target.app", 1_000L, UsageEventRecord.Kind.FOREGROUND),
            UsageEventRecord("com.target.app", 1_300L, UsageEventRecord.Kind.RESUMED),
            UsageEventRecord("com.target.app", 4_000L, UsageEventRecord.Kind.RESUMED),
            UsageEventRecord("com.other.app", 5_000L, UsageEventRecord.Kind.FOREGROUND)
        )

        val opens = UsageEventAnalyzer.countLaunches(
            events = events,
            packageName = "com.target.app",
            dedupeWindowMs = 1_500L
        )

        assertEquals(2, opens)
    }
}
