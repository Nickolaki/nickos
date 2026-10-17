package com.nbaltas.nickos.node

import com.nbaltas.nickos.data.LinkedNode
import com.nbaltas.nickos.link.NetMessages
import com.nbaltas.nickos.ui.master.TouchText
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TouchShieldTest {
    private fun wire(o: JSONObject) = JSONObject(o.toString())

    @Test fun `touch command encodes and parses`() {
        val off = wire(NetMessages.touchCmd(true))
        assertEquals("cmd", off.getString("t")); assertEquals("touch", off.getString("c"))
        assertEquals(true, NetMessages.parseTouch(off))
        assertEquals(false, NetMessages.parseTouch(wire(NetMessages.touchCmd(false))))
    }

    @Test fun `a touch command without a real boolean is refused`() {
        assertNull(NetMessages.parseTouch(null))
        assertNull(NetMessages.parseTouch(JSONObject("""{"t":"cmd","c":"touch"}""")))
        assertNull(NetMessages.parseTouch(JSONObject("""{"t":"cmd","c":"touch","off":"true"}""")))
        assertNull(NetMessages.parseTouch(JSONObject("""{"t":"cmd","c":"touch","off":1}""")))
        assertNull(NetMessages.parseTouch(JSONObject("""{"t":"cmd","c":"power","off":true}""")))
    }

    @Test fun `status reads the wish and the shield`() {
        val s = NetMessages.readTouchStatus(JSONObject("""{"touchOff":true,"touchShield":"inapp"}"""))
        assertEquals(NetMessages.TouchStatus(true, "inapp"), s)
        assertEquals(NetMessages.TouchStatus(false, "none"), NetMessages.readTouchStatus(JSONObject("""{"touchOff":false,"touchShield":"none"}""")))
        // An unknown shield word reads as none; no touchOff at all is an old Node.
        assertEquals("none", NetMessages.readTouchStatus(JSONObject("""{"touchOff":true,"touchShield":"weird"}"""))!!.shield)
        assertEquals("none", NetMessages.readTouchStatus(JSONObject("""{"touchOff":true}"""))!!.shield)
        assertNull(NetMessages.readTouchStatus(JSONObject("""{"battery":50}""")))
    }

    @Test fun `linked node keeps the touch choice through json`() {
        val n = LinkedNode("a", "n", 1, "initial", "t", touchOff = true)
        assertEquals(true, LinkedNode.from(wire(n.toJson())).touchOff)
        assertEquals(false, LinkedNode.from(wire(n.copy(touchOff = false).toJson())).touchOff)
        assertNull(LinkedNode.from(wire(n.copy(touchOff = null).toJson())).touchOff)
        assertNull(LinkedNode.from(n.toJson().apply { remove("touchOff") }).touchOff)
    }

    @Test fun `the shield is wanted only when touch is off and nothing else blocks touches`() {
        assertTrue(TouchShield.wanted(touchOff = true, parentMode = false, locked = false, paused = false))
        assertFalse(TouchShield.wanted(touchOff = false, parentMode = false, locked = false, paused = false))
        assertFalse("parent mode", TouchShield.wanted(true, parentMode = true, locked = false, paused = false))
        assertFalse("locked wall", TouchShield.wanted(true, parentMode = false, locked = true, paused = false))
        assertFalse("paused wall", TouchShield.wanted(true, parentMode = false, locked = false, paused = true))
        assertFalse("pin prompt up", TouchShield.wanted(true, false, false, false, suspended = true))
    }

    @Test fun `the corner hold needs three seconds inside the corner`() {
        val h = CornerHold(zonePx = 100f)
        h.start(10f, 10f, now = 1_000)
        assertFalse(h.ready(3_999))
        assertTrue(h.ready(4_000))
        // Outside the corner nothing starts.
        h.start(500f, 10f, now = 1_000)
        assertFalse(h.holding); assertFalse(h.ready(60_000))
        // Sliding out starts over; lifting cancels.
        h.start(10f, 10f, 0); h.move(300f, 10f)
        assertFalse(h.ready(9_000))
        h.start(10f, 10f, 0); h.cancel()
        assertFalse(h.ready(9_000))
        // It fires once.
        h.start(1f, 1f, 0)
        assertTrue(h.consume(3_000)); assertFalse(h.consume(3_001))
    }

    @Test fun `one injection at a time and the shield comes back when it ends`() {
        val g = InjectGate()
        val t1 = g.begin()!!
        assertTrue(g.busy)
        assertNull("an extra gesture is dropped while one is in flight", g.begin())
        assertTrue("Android answered: restore", g.finish(t1))
        assertFalse(g.busy)
        assertFalse("a second answer does nothing", g.finish(t1))
    }

    @Test fun `when Android never answers the timeout restores`() {
        val g = InjectGate()
        val t = g.begin()!!
        // No callback came; the fallback after the gesture length plus the slack calls finish with the same token.
        assertTrue(g.finish(t))
        assertTrue(g.begin() != null)
    }

    @Test fun `a stale callback or timeout cannot restore for a newer gesture`() {
        val g = InjectGate()
        val old = g.begin()!!
        assertTrue(g.finish(old))          // the timeout fired first
        val newer = g.begin()!!
        assertFalse("the late callback of the old gesture", g.finish(old))
        assertTrue("the newer gesture is still in flight", g.busy)
        assertTrue(g.finish(newer))
    }

    @Test fun `a refused gesture restores at once`() {
        val g = InjectGate()
        val t = g.begin()!!
        // dispatchGesture returned false: the caller finishes with the token right away.
        assertTrue(g.finish(t))
        assertFalse(g.busy)
        assertTrue(g.begin() != null)
    }

    @Test fun `cancelling the emergency PIN puts the shield back at once`() {
        val s = ShieldSuspension()
        assertTrue(TouchShield.wanted(true, false, false, false, s.active(1_000)))
        s.suspend(1_000)
        assertFalse("the shield is away while the PIN pad is up", TouchShield.wanted(true, false, false, false, s.active(2_000)))
        s.restore()
        assertTrue("cancel or a wrong PIN", TouchShield.wanted(true, false, false, false, s.active(2_001)))
    }

    @Test fun `a forgotten PIN prompt ends by itself`() {
        val s = ShieldSuspension()
        s.suspend(0)
        assertTrue(s.active(TouchShield.SUSPEND_MAX_MS - 1))
        assertFalse(s.active(TouchShield.SUSPEND_MAX_MS))
    }

    @Test fun `effective state follows what is really in place`() {
        assertEquals("none", NetMessages.effectiveShield(off = false, overlayAttached = true, inAppBox = true))
        assertEquals("on", NetMessages.effectiveShield(off = true, overlayAttached = true, inAppBox = false))
        assertEquals("inapp", NetMessages.effectiveShield(off = true, overlayAttached = false, inAppBox = true))
        assertEquals("none", NetMessages.effectiveShield(off = true, overlayAttached = false, inAppBox = false))
    }

    @Test fun `the switch tells the truth`() {
        assertEquals(null, TouchText.status(NetMessages.TouchStatus(false, "none"), "Mia"))
        assertEquals("Touch is off", TouchText.status(NetMessages.TouchStatus(true, "on"), "Mia"))
        assertEquals("Turning off...", TouchText.status(NetMessages.TouchStatus(true, "none"), "Mia"))
        assertEquals("Only blocks touches inside NickOS. Allow 'Display over other apps' on Mia", TouchText.status(NetMessages.TouchStatus(true, "inapp"), "Mia"))
        assertTrue(TouchText.offerAllow(NetMessages.TouchStatus(true, "inapp")))
        assertFalse(TouchText.offerAllow(NetMessages.TouchStatus(true, "on")))
        assertTrue(TouchText.touchOn(null)); assertTrue(TouchText.touchOn(NetMessages.TouchStatus(false, "none")))
        assertFalse(TouchText.touchOn(NetMessages.TouchStatus(true, "on")))
    }
}
