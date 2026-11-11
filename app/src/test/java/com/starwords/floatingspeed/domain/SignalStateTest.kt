package com.starwords.floatingspeed.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SignalStateTest {

    @Test
    fun `initial state is searching`() {
        assertEquals(SignalState.Searching, SignalStateMachine().state)
    }

    @Test
    fun `accuracy bands use correct boundaries`() {
        val machine = SignalStateMachine()
        machine.onFix(20f, 1_000)
        assertEquals(SignalState.Good(20f), machine.state)
        machine.onFix(20.1f, 2_000)
        assertEquals(SignalState.Fair(20.1f), machine.state)
        machine.onFix(50f, 3_000)
        assertEquals(SignalState.Fair(50f), machine.state)
        machine.onFix(50.1f, 4_000)
        assertEquals(SignalState.Poor(50.1f), machine.state)
    }

    @Test
    fun `stays under threshold before three seconds then becomes lost`() {
        val machine = SignalStateMachine()
        machine.onFix(10f, 1_000)
        machine.onTick(3_999)
        assertEquals(SignalState.Good(10f), machine.state)
        machine.onTick(4_000)
        assertEquals(SignalState.Lost, machine.state)
    }

    @Test
    fun `fix after lost recovers`() {
        val machine = SignalStateMachine()
        machine.onFix(10f, 1_000)
        machine.onTick(5_000)
        assertEquals(SignalState.Lost, machine.state)
        machine.onFix(12f, 5_500)
        assertEquals(SignalState.Good(12f), machine.state)
    }

    @Test
    fun `never fixed stays searching`() {
        val machine = SignalStateMachine()
        machine.onTick(10_000)
        assertEquals(SignalState.Searching, machine.state)
    }

    @Test
    fun `permission loss and regain transitions`() {
        val machine = SignalStateMachine()
        machine.onFix(10f, 1_000)
        machine.onPermissionLost()
        assertEquals(SignalState.NoPermission, machine.state)
        machine.onPermissionGranted()
        assertEquals(SignalState.Searching, machine.state)
        machine.onTick(50_000)
        assertEquals(SignalState.Searching, machine.state)
    }

    @Test
    fun `tick after permission regain with stale fix does not resurrect lost`() {
        val machine = SignalStateMachine()
        machine.onFix(10f, 1_000)
        machine.onPermissionLost()
        machine.onPermissionGranted()
        assertNotEquals(SignalState.Lost, machine.state)
    }
}
