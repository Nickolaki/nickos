package com.nbaltas.nickos.link

import com.nbaltas.nickos.node.PauseAll
import com.nbaltas.nickos.node.RingMachine
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PauseRingTest {
    private fun wire(o: JSONObject) = JSONObject(o.toString())

    // ---- codecs ----
    @Test fun `pauseAll round trips`() {
        val on = wire(NetMessages.pauseAllCmd(true, "  Time to eat "))
        assertEquals("cmd", on.getString("t")); assertEquals("pauseAll", on.getString("c"))
        assertEquals(NetMessages.PauseAll(true, "Time to eat"), NetMessages.decodePauseAll(on))
        assertEquals(NetMessages.PauseAll(false, ""), NetMessages.decodePauseAll(wire(NetMessages.pauseAllCmd(false, ""))))
    }

    @Test fun `pauseAll keeps the message short and refuses a missing on`() {
        assertEquals(NetMessages.MAX_PAUSE_MSG, NetMessages.decodePauseAll(wire(NetMessages.pauseAllCmd(true, "x".repeat(500))))!!.msg.length)
        assertNull(NetMessages.decodePauseAll(JSONObject("""{"t":"cmd","c":"pauseAll","msg":"hi"}""")))
        assertNull(NetMessages.decodePauseAll(JSONObject("""{"t":"cmd","c":"pauseAll","on":"yes"}""")))
        assertNull(NetMessages.decodePauseAll(JSONObject("""{"t":"cmd","c":"ring","on":true}""")))
        assertNull(NetMessages.decodePauseAll(null))
    }

    @Test fun `ring round trips and refuses a missing on`() {
        assertEquals(true, NetMessages.decodeRing(wire(NetMessages.ringCmd(true))))
        assertEquals(false, NetMessages.decodeRing(wire(NetMessages.ringCmd(false))))
        assertEquals("ring", NetMessages.ringCmd(true).getString("c"))
        assertNull(NetMessages.decodeRing(JSONObject("""{"t":"cmd","c":"ring"}""")))
        assertNull(NetMessages.decodeRing(JSONObject("""{"t":"cmd","c":"pauseAll","on":true}""")))
        assertNull(NetMessages.decodeRing(null))
    }

    @Test fun `capabilities are named ring1 and pause1`() {
        assertEquals("ring1", NetMessages.RING_CAP)
        assertEquals("pause1", NetMessages.PAUSE_CAP)
    }

    // ---- pause state ----
    @Test fun `a paused Master sends the wall again on connect and an unpaused one sends nothing`() {
        assertEquals(NetMessages.PauseAll(true, "Toilet stop"), NetMessages.decodePauseAll(wire(PauseAll.intent("Toilet stop")!!)))
        assertNull(PauseAll.intent(null))
    }

    @Test fun `an owner that resumed while the tablet was offline clears its wall on reconnect`() {
        val off = PauseAll.resumeIntent(null, nodePaused = true, owner = "A", masterId = "A")!!
        assertEquals(NetMessages.PauseAll(false, ""), NetMessages.decodePauseAll(wire(off)))
        assertFalse(off.has("force"))
        assertNull(PauseAll.resumeIntent(null, true, "A", "B"))
        assertNull(PauseAll.resumeIntent(null, true, null, "B"))
        assertNull(PauseAll.resumeIntent("Still paused", true, "A", "A"))
        assertNull(PauseAll.resumeIntent(null, false, "A", "A"))
    }

    @Test fun `only the pausing Master or a forced resume lifts a pause`() {
        assertTrue(PauseAll.mayClear(owner = "A", sender = "A", force = false))
        assertFalse("a second parent must not cancel it", PauseAll.mayClear(owner = "A", sender = "B", force = false))
        assertTrue("the explicit Resume everyone button may", PauseAll.mayClear(owner = "A", sender = "B", force = true))
        assertTrue("a wall without an owner is open", PauseAll.mayClear(owner = null, sender = "B", force = false))
    }

    @Test fun `the force flag only travels when set`() {
        assertFalse(wire(NetMessages.pauseAllCmd(false, "")).has("force"))
        assertFalse(NetMessages.decodePauseAll(wire(NetMessages.pauseAllCmd(false, "")))!!.force)
        assertTrue(NetMessages.decodePauseAll(wire(NetMessages.pauseAllCmd(false, "", force = true)))!!.force)
        assertFalse(NetMessages.decodePauseAll(JSONObject("""{"t":"cmd","c":"pauseAll","on":false,"force":"yes"}"""))!!.force)
    }

    @Test fun `the connect intent only ever pauses`() {
        assertTrue(NetMessages.decodePauseAll(wire(PauseAll.intent("Time to eat")!!))!!.on)
        assertFalse(NetMessages.decodePauseAll(wire(PauseAll.intent("Time to eat")!!))!!.force)
    }

    @Test fun `the wall has words even when the parent typed none`() {
        assertEquals("Paused", PauseAll.wallText("  "))
        assertEquals("Paused", PauseAll.wallText(null))
        assertEquals("Time to eat", PauseAll.wallText(" Time to eat "))
        assertTrue(PauseAll.canSubmit(paused = true, msg = ""))
        assertFalse(PauseAll.canSubmit(paused = false, msg = " "))
        assertTrue(PauseAll.canSubmit(paused = false, msg = "Toilet stop"))
    }

    // ---- ring state ----
    @Test fun `each way of stopping restores the volume once`() {
        for (reason in RingMachine.Stop.entries) {
            val r = RingMachine()
            assertTrue(r.start(1000, 3))
            assertTrue(r.ringing)
            assertEquals(reason.name, 3, r.stop(reason))
            assertFalse(r.ringing)
            assertNull("second stop restores nothing", r.stop(reason))
        }
    }

    @Test fun `it stops by itself after sixty seconds`() {
        val r = RingMachine()
        r.start(0, 5)
        assertNull(r.tick(59_999))
        assertTrue(r.ringing)
        assertEquals(5, r.tick(60_000))
        assertFalse(r.ringing)
        assertNull(r.tick(120_000))
        assertNull("the cap already restored it", r.stop(RingMachine.Stop.PARENT))
    }

    @Test fun `ringing twice keeps the first saved volume`() {
        val r = RingMachine()
        assertTrue(r.start(0, 2))
        assertFalse(r.start(10, 7))
        assertEquals(2, r.stop(RingMachine.Stop.TAP))
    }

    @Test fun `it can ring again after stopping`() {
        val r = RingMachine()
        r.start(0, 2); r.stop(RingMachine.Stop.TAP)
        assertTrue(r.start(5_000, 4))
        assertNull(r.tick(64_999))
        assertNotNull(r.tick(65_000))
    }
}
