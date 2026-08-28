package com.focusgate.app.state

import com.focusgate.app.data.MoodType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LightweightStateInputTest {

    @Test
    fun options_containsExactlyFiveLowCostStates() {
        assertEquals(5, LightweightStateInput.options.size)
        assertTrue(LightweightStateInput.options.any { it.mood == MoodType.TIRED })
        assertTrue(LightweightStateInput.options.any { it.mood == MoodType.ANNOYED })
        assertTrue(LightweightStateInput.options.any { it.mood == MoodType.ESCAPE })
        assertTrue(LightweightStateInput.options.any { it.mood == MoodType.RESEARCH })
        assertTrue(LightweightStateInput.options.any { it.mood == MoodType.ITCHY })
    }
}
