package com.focusgate.app.review

import com.focusgate.app.data.AppSession
import com.focusgate.app.data.IntentType
import com.focusgate.app.data.MoodType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewEngineTest {

    @Test
    fun `empty sessions returns empty stats`() {
        val stats = ReviewEngine.calculate(emptyList())
        assertEquals(0, stats.totalOpens)
        assertEquals(0, stats.totalMinutes)
        assertEquals("暂无数据，去刷几条再来吧", stats.conclusion)
    }

    @Test
    fun `calculates total opens and minutes`() {
        val sessions = listOf(
            AppSession("com.ss.android.ugc.aweme", 0, endTime = 600_000, intentType = IntentType.RELAX),
            AppSession("tv.danmaku.bili", 0, endTime = 300_000, intentType = IntentType.INFO)
        )
        val stats = ReviewEngine.calculate(sessions)
        assertEquals(2, stats.totalOpens)
        assertEquals(15, stats.totalMinutes) // 10 + 5
        assertEquals(7, stats.avgSessionMinutes) // 15 / 2
    }

    @Test
    fun `detects unconscious majority`() {
        val sessions = List(5) {
            AppSession("com.ss.android.ugc.aweme", 0, endTime = 300_000, intentType = IntentType.UNCONSCIOUS)
        }
        val stats = ReviewEngine.calculate(sessions)
        assertEquals(100, stats.unconsciousPercent)
        assertTrue(stats.conclusion.contains("无意识"))
    }

    @Test
    fun `mood breakdown is correct`() {
        val sessions = listOf(
            AppSession("a", 0, endTime = 100, mood = MoodType.TIRED),
            AppSession("a", 0, endTime = 100, mood = MoodType.TIRED),
            AppSession("a", 0, endTime = 100, mood = MoodType.ITCHY)
        )
        val stats = ReviewEngine.calculate(sessions)
        assertEquals(2, stats.moodBreakdown[MoodType.TIRED])
        assertEquals(1, stats.moodBreakdown[MoodType.ITCHY])
    }
}
