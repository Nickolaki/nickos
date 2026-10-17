package com.nbaltas.nickos.link

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
    fun `a band switch that comes back with the same ipv4 is not a change`() {
        gate.address("192.168.1.9"); settle()
        assertEquals(1, fired.get())
        gate.lost(); gate.available(); gate.address("192.168.1.9"); settle()
        assertEquals(1, fired.get())
    }

    @Test
    fun `a loss followed by a different ipv4 is a change`() {
        gate.address("192.168.1.9"); settle()
        gate.lost(); gate.available(); gate.address("192.168.1.30"); settle()
        assertEquals(2, fired.get())
    }

    @Test
    fun `the same ipv4 on another interface or gateway is a change, not a band switch`() {
        val home = LanAddress.identity("wlan0", "192.168.49.5", "192.168.49.1")
        val car = LanAddress.identity("p2p-wlan0-0", "192.168.49.5", "192.168.49.1")
        val otherGw = LanAddress.identity("wlan0", "192.168.49.5", "192.168.49.254")
        gate.address(home); settle()
        assertEquals(1, fired.get())
        gate.lost(); gate.available(); gate.address(car); settle()
        assertEquals(2, fired.get())
        gate.lost(); gate.available(); gate.address(otherGw); settle()
        assertEquals(3, fired.get())
    }

    @Test
    fun `a band switch on the same network is not a change`() {
        val id = LanAddress.identity("wlan0", "192.168.1.9", "192.168.1.1")
        gate.address(id); settle()
        gate.lost(); gate.available(); gate.address(id); settle()
        assertEquals(1, fired.get())
    }

    @Test
    fun `a different ssid or dns with the same interface, ip and gateway is a change`() {
        val a = LanAddress.identity("wlan0", "192.168.1.9", "192.168.1.1", listOf("8.8.8.8"), null, "192.168.1.1", "\"Home\"")
        val otherSsid = LanAddress.identity("wlan0", "192.168.1.9", "192.168.1.1", listOf("8.8.8.8"), null, "192.168.1.1", "\"Cafe\"")
        val noSsid = LanAddress.identity("wlan0", "192.168.1.9", "192.168.1.1", listOf("8.8.8.8"), null, "192.168.1.1", "<unknown ssid>")
        val otherDns = LanAddress.identity("wlan0", "192.168.1.9", "192.168.1.1", listOf("1.1.1.1"), null, "192.168.1.1", "\"Home\"")
        assertNotEquals(a, otherSsid)
        assertNotEquals(a, noSsid)
        assertNotEquals(a, otherDns)
        gate.address(a); settle()
        assertEquals(1, fired.get())
        gate.lost(); gate.available(); gate.address(otherSsid); settle()
        assertEquals(2, fired.get())
        gate.lost(); gate.available(); gate.address(otherDns); settle()
        assertEquals(3, fired.get())
    }

    @Test
    fun `the same network seen on another band keeps its identity`() {
        // The band (BSSID, frequency) is not part of identity(), so a 2.4 to 5 GHz roam produces the same key.
        val g = LanAddress.identity("wlan0", "192.168.1.9", "192.168.1.1", listOf("8.8.4.4", "8.8.8.8"), "lan", "192.168.1.1", "\"Home\"")
        val roamed = LanAddress.identity("wlan0", "192.168.1.9", "192.168.1.1", listOf("8.8.8.8", "8.8.4.4"), "lan", "192.168.1.1", "\"Home\"")
        assertEquals(g, roamed)
        gate.address(g); settle()
        gate.lost(); gate.available(); gate.address(roamed); settle()
        assertEquals(1, fired.get())
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
