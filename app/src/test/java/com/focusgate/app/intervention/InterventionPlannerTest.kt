package com.focusgate.app.intervention

import com.focusgate.app.rule.ReminderIntensity
import org.junit.Assert.assertEquals
import org.junit.Test

class InterventionPlannerTest {

    @Test
    fun normalIntensity_midpointUsesNotification() {
        val planner = InterventionPlanner()

        val action = planner.plan(
            stage = InterventionStage.FIVE_MINUTES,
            intensity = ReminderIntensity.NORMAL,
            overlayAvailable = false
        )

        assertEquals(InterventionType.NOTIFICATION, action.type)
    }

    @Test
    fun strictIntensity_midpointUsesDialog() {
        val planner = InterventionPlanner()

        val action = planner.plan(
            stage = InterventionStage.FIVE_MINUTES,
            intensity = ReminderIntensity.STRICT,
            overlayAvailable = false
        )

        assertEquals(InterventionType.DIALOG, action.type)
    }

    @Test
    fun strictIntensity_timeoutFallsBackWhenOverlayUnavailable() {
        val planner = InterventionPlanner()

        val action = planner.plan(
            stage = InterventionStage.TIME_UP,
            intensity = ReminderIntensity.STRICT,
            overlayAvailable = false
        )

        assertEquals(InterventionType.FULL_SCREEN, action.type)
    }
}
