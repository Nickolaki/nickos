package com.nbaltas.nickos.link

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class ChangeGateTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @After fun tearDown() { scope.cancel() }

    private val fired = AtomicInteger()
    private val gate = ChangeGate(scope, debounceMs = 25) { fired.incrementAndGet() }

    private fun settle() = Thread.sleep(90)

    @Test
    fun `a flapping network is one change after it settles`() {
        gate.lost(); gate.available(); gate.lost(); gate.available()
        assertEquals(0, fired.get())     // nothing yet: still inside the debounce
        settle()
        assertEquals(1, fired.get())
    }

    @Test
    fun `a later event is a later change`() {
        gate.available(); settle()
        gate.lost(); settle()
        assertEquals(2, fired.get())
    }

    @Test
    fun `an address event only counts when the ipv4 address differs`() {
        gate.address("192.168.43.17"); settle()
        assertEquals(1, fired.get())
        gate.address("192.168.43.17"); settle()          // an ipv6 or lifetime update on the same address
        assertEquals(1, fired.get())
        gate.address("192.168.50.4"); settle()
        assertEquals(2, fired.get())
    }

    @Test
    fun `the tablet's own hotspot appearing and going away both count`() {
        // The ap interface shows up (no Network callback for it) and the poll feeds the gate its address.
        gate.address(null); settle()
        assertEquals(0, fired.get())                      // nothing there yet, nothing changed
        gate.address("192.168.43.1"); settle()
        assertEquals(1, fired.get())
        gate.address(null); settle()                      // hotspot switched off
        assertEquals(2, fired.get())
    }

    @Test
    fun `after a loss the same address counts as new again`() {
        gate.address("192.168.43.17"); settle()
        gate.lost(); settle()
        gate.address("192.168.43.17"); settle()
        assertEquals(3, fired.get())
    }

    @Test
    fun `close cancels a pending change and ignores later ones`() {
        gate.available()
        gate.close()
        gate.available(); gate.address("10.0.0.2")
        settle()
        assertEquals(0, fired.get())
    }
}
