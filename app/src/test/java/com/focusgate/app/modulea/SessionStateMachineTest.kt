package com.focusgate.app.modulea

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SessionStateMachineTest {

    private val targets = setOf("com.target.one", "com.target.two")
    private val ignored = setOf("com.focusgate.app")

    @Test
    fun onForeground_startsSessionWhenEnteringTargetApp() {
        val machine = SessionStateMachine(targetPackages = targets, ignoredPackages = ignored)

        val transition = machine.onForegroundChanged("com.target.one", 1_000L)

        assertNotNull(transition.startedSession)
        assertEquals("com.target.one", transition.startedSession?.packageName)
        assertEquals(1_000L, transition.startedSession?.startTime)
        assertEquals("com.target.one", transition.activeSession?.packageName)
        assertNull(transition.endedSession)
    }

    @Test
    fun onForeground_keepsSessionAliveWhenSwitchingToIgnoredPackage() {
        val machine = SessionStateMachine(targetPackages = targets, ignoredPackages = ignored)
        machine.onForegroundChanged("com.target.one", 1_000L)

        val transition = machine.onForegroundChanged("com.focusgate.app", 2_000L)

        assertNull(transition.endedSession)
        assertEquals("com.target.one", transition.activeSession?.packageName)
    }

    @Test
    fun onForeground_endsSessionWhenLeavingTargetFlow() {
        val machine = SessionStateMachine(targetPackages = targets, ignoredPackages = ignored)
        machine.onForegroundChanged("com.target.one", 1_000L)
        machine.onForegroundChanged("com.focusgate.app", 2_000L)

        val transition = machine.onForegroundChanged("com.android.launcher", 3_000L)

        assertEquals("com.target.one", transition.endedSession?.packageName)
        assertEquals(3_000L, transition.endedSession?.endTime)
        assertNull(transition.activeSession)
    }

    @Test
    fun onForeground_switchingBetweenTargetsEndsAndStartsSessions() {
        val machine = SessionStateMachine(targetPackages = targets, ignoredPackages = ignored)
        machine.onForegroundChanged("com.target.one", 1_000L)

        val transition = machine.onForegroundChanged("com.target.two", 2_000L)

        assertEquals("com.target.one", transition.endedSession?.packageName)
        assertEquals(2_000L, transition.endedSession?.endTime)
        assertEquals("com.target.two", transition.startedSession?.packageName)
        assertEquals("com.target.two", transition.activeSession?.packageName)
    }
}
