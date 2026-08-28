package com.focusgate.app.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GateEventPolicyTest {
    @Test
    fun hyperOsSystemUiPluginCannotEndAnActiveSession() {
        assertTrue(GateEventPolicy.shouldIgnoreSystemUiEvent("miui.systemui.plugin", null))
        assertTrue(GateEventPolicy.shouldIgnoreSystemUiEvent("com.miui.systemui.plugin", null))
        assertTrue(GateEventPolicy.shouldIgnoreSystemUiEvent("com.android.systemui", "android.widget.FrameLayout"))
        assertFalse(GateEventPolicy.shouldIgnoreSystemUiEvent("com.xingin.xhs", "android.widget.FrameLayout"))
    }

    @Test
    fun hyperOsSecurityCenterOverlayIsIgnoredButRealSettingsPageIsNot() {
        assertTrue(
            GateEventPolicy.shouldIgnoreSystemUiEvent(
                "com.miui.securitycenter",
                "android.widget.FrameLayout"
            )
        )
        assertFalse(
            GateEventPolicy.shouldIgnoreSystemUiEvent(
                "com.miui.securitycenter",
                "com.miui.appmanager.ApplicationsDetailsActivity"
            )
        )
    }

    @Test
    fun confirmationIsConsumedBeforeMissingSurfaceRecovery() {
        assertTrue(GateEventPolicy.hasReadyOutcome("request-1", "request-1", "confirmed"))
        assertFalse(GateEventPolicy.hasReadyOutcome("request-1", "request-2", "confirmed"))
        assertFalse(GateEventPolicy.hasReadyOutcome("request-1", "request-1", null))
    }

    @Test
    fun steadyContentChangesDoNotCreateDelayedGate() {
        assertTrue(GateEventPolicy.shouldIgnoreSteadyContentEvent(true, "xhs", "xhs"))
        assertFalse(GateEventPolicy.shouldIgnoreSteadyContentEvent(false, "xhs", "xhs"))
        assertFalse(GateEventPolicy.shouldIgnoreSteadyContentEvent(true, null, "xhs"))
    }

    @Test
    fun cooldownIsPerTargetInsteadOfGlobal() {
        assertTrue(GateEventPolicy.isCoolingDown("xhs", "xhs", 3_000L, 10_000L))
        assertFalse(GateEventPolicy.isCoolingDown("xhs", "douyin", 3_000L, 10_000L))
        assertFalse(GateEventPolicy.isCoolingDown("xhs", "xhs", 10_000L, 10_000L))
    }
}
