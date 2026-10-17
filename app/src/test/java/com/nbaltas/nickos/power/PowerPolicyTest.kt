package com.nbaltas.nickos.power

import com.nbaltas.nickos.link.NetMessages
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class FakeScreen(var canChange: Boolean = true, var timeout: Int = 60_000, var kept: Int? = null, var diskOk: Boolean = true) : ScreenTimeoutPort {
    var writes = 0
    override fun canChange() = canChange
    override fun read() = timeout
    override fun write(ms: Int): Boolean { writes++; timeout = ms; return true }
    override fun saved() = kept
    override fun save(ms: Int?): Boolean { if (!diskOk) return false; kept = ms; return true }
}

/** Robolectric because the codec tests need a real org.json. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PowerPolicyTest {
    @Test fun `on battery with a time the timeout is set and the old one saved first`() {
        val p = PowerPolicy.plan(false, 5, charging = false, canChange = true, saved = null)
        assertTrue(p.saveFirst); assertEquals(300_000, p.timeoutMs); assertFalse(p.restore); assertEquals(NetMessages.STATE_ON, p.state)
    }

    @Test fun `charging gives the saved timeout back`() {
        val p = PowerPolicy.plan(false, 5, charging = true, canChange = true, saved = 60_000)
        assertFalse(p.saveFirst); assertNull(p.timeoutMs); assertTrue(p.restore)
    }

    @Test fun `charging with nothing saved does nothing`() {
        val p = PowerPolicy.plan(true, 5, charging = true, canChange = true, saved = null)
        assertFalse(p.saveFirst); assertNull(p.timeoutMs); assertFalse(p.restore)
    }

    @Test fun `setting turned off on battery restores`() {
        val p = PowerPolicy.plan(false, 0, charging = false, canChange = true, saved = 30_000)
        assertTrue(p.restore); assertNull(p.timeoutMs); assertEquals(NetMessages.STATE_OFF, p.state)
    }

    @Test fun `without permission a time reports no_permission and changes nothing`() {
        val p = PowerPolicy.plan(false, 10, charging = false, canChange = false, saved = null)
        assertFalse(p.saveFirst); assertNull(p.timeoutMs); assertFalse(p.restore); assertEquals(NetMessages.STATE_NO_PERMISSION, p.state)
    }

    @Test fun `wake alone is on and needs no permission`() {
        assertEquals(NetMessages.STATE_ON, PowerPolicy.plan(true, 0, false, false, null).state)
        assertEquals(NetMessages.STATE_OFF, PowerPolicy.plan(false, 0, false, false, null).state)
    }

    @Test fun `wake only when asked and plugged in`() {
        assertTrue(PowerPolicy.shouldWake(true, true))
        assertFalse(PowerPolicy.shouldWake(true, false))
        assertFalse(PowerPolicy.shouldWake(false, true))
    }

    @Test fun `choices are off and 2 5 10 15 30`() = assertEquals(listOf(0, 2, 5, 10, 15, 30), PowerPolicy.CHOICES)

    @Test fun `the first value is saved once and given back once`() {
        val port = FakeScreen(timeout = 60_000)
        val t = PowerScreenTimer(port)
        t.apply(false, 5, charging = false)
        assertEquals(300_000, port.timeout); assertEquals(60_000, port.kept)
        // A new choice while on battery must not overwrite the saved original with our own value.
        t.apply(false, 10, charging = false)
        assertEquals(600_000, port.timeout); assertEquals(60_000, port.kept)
        t.apply(false, 10, charging = true)
        assertEquals(60_000, port.timeout); assertNull(port.kept)
    }

    @Test fun `applying again changes nothing`() {
        val port = FakeScreen()
        val t = PowerScreenTimer(port)
        t.apply(false, 5, false)
        val w = port.writes
        t.apply(false, 5, false); t.apply(false, 5, false)
        assertEquals(w, port.writes)
        t.apply(false, 5, true)
        val w2 = port.writes
        t.apply(false, 5, true)
        assertEquals(w2, port.writes)
    }

    @Test fun `a copy that did not reach the disk leaves the tablet alone`() {
        val port = FakeScreen(diskOk = false)
        PowerScreenTimer(port).apply(false, 5, false)
        assertEquals(60_000, port.timeout); assertEquals(0, port.writes)
    }

    @Test fun `two timers on one store keep the original timeout`() {
        val port = FakeScreen(timeout = 60_000)
        val master = PowerScreenTimer(port)
        val node = PowerScreenTimer(port)
        // Interleaved unplug and refresh from both runners, as when a surviving Master races the Node.
        master.apply(false, 5, charging = false)
        node.apply(false, 10, charging = false)
        master.apply(false, 10, charging = false)
        node.apply(false, 10, charging = false)
        assertEquals(60_000, port.kept)
        node.apply(false, 10, charging = true)
        assertEquals(60_000, port.timeout); assertNull(port.kept)
    }

    @Test fun `two timers on threads share one backup`() {
        val port = FakeScreen(timeout = 60_000)
        val ts = listOf(PowerScreenTimer(port), PowerScreenTimer(port))
        val threads = ts.map { t -> Thread { repeat(200) { t.apply(false, 5, charging = false) } } }
        threads.forEach { it.start() }; threads.forEach { it.join() }
        assertEquals(60_000, port.kept); assertEquals(300_000, port.timeout)
    }

    @Test fun `a restore waits for permission and the copy is kept`() {
        val port = FakeScreen(canChange = false, timeout = 300_000, kept = 60_000)
        val t = PowerScreenTimer(port)
        t.apply(false, 0, true)
        assertEquals(300_000, port.timeout); assertEquals(60_000, port.kept)
        port.canChange = true
        t.apply(false, 0, true)
        assertEquals(60_000, port.timeout); assertNull(port.kept)
    }

    @Test fun `unlink and reset style release restores`() {
        val port = FakeScreen()
        val t = PowerScreenTimer(port)
        t.apply(true, 15, false)
        t.apply(false, 0, true)
        assertEquals(60_000, port.timeout); assertNull(port.kept); assertEquals(NetMessages.STATE_OFF, t.state)
    }

    @Test fun `power command round trips and rejects bad input`() {
        val c = NetMessages.decodePower(NetMessages.powerCmd(true, 15))!!
        assertTrue(c.wake); assertEquals(15, c.offMin)
        assertNull(NetMessages.decodePower(JSONObject().put("t", "cmd").put("c", "power").put("wake", true)))
        assertNull(NetMessages.decodePower(JSONObject().put("t", "cmd").put("c", "power").put("wake", "yes").put("offMin", 5)))
        assertNull(NetMessages.decodePower(NetMessages.powerCmd(false, -1)))
        assertNull(NetMessages.decodePower(NetMessages.orientationCmd(true)))
    }

    @Test fun `power status round trips and an old node has none`() {
        val s = NetMessages.PowerStatus(true, 5, NetMessages.STATE_NO_PERMISSION)
        val back = NetMessages.decodePowerStatus(JSONObject().put("power", NetMessages.encodePower(s)))!!
        assertEquals(s, back); assertTrue(back.needsPermission)
        assertNull(NetMessages.decodePowerStatus(JSONObject()))
        assertNull(NetMessages.decodePowerStatus(JSONObject().put("power", JSONObject().put("state", "weird"))))
    }
}
