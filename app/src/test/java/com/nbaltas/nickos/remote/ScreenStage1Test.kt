package com.nbaltas.nickos.remote

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
class ScreenStage1Test {
    private fun wire(o: JSONObject) = JSONObject(o.toString())

    // ---- codec ----

    @Test
    fun `screenStart and screenStop are bare commands`() {
        val a = wire(RemoteMessages.cmdStart())
        assertEquals("cmd", a.getString("t"))
        assertEquals("screenStart", a.getString("c"))
        assertEquals(2, a.length())
        val b = wire(RemoteMessages.cmdStop())
        assertEquals("screenStop", b.getString("c"))
        assertEquals(2, b.length())
    }

    @Test
    fun `screenState round trips`() {
        val s = RemoteMessages.State(RemoteMessages.LIVE, RemoteMessages.CONTROL_READY, 47473, "abc123")
        assertEquals(s, RemoteMessages.decodeState(wire(RemoteMessages.encodeState(s))))
        val off = RemoteMessages.State(RemoteMessages.NEEDS_CONSENT)
        val o = wire(RemoteMessages.encodeState(off))
        assertFalse(o.has("port"))
        assertFalse(o.has("key"))
        assertEquals(off, RemoteMessages.decodeState(o))
    }

    @Test
    fun `screenState with a bad field decodes to null`() {
        assertNull(RemoteMessages.decodeState(JSONObject()))
        assertNull(RemoteMessages.decodeState(JSONObject().put("state", "sideways")))
        assertNull(RemoteMessages.decodeState(JSONObject().put("state", "live").put("port", "47473")))
        assertNull(RemoteMessages.decodeState(JSONObject().put("state", "live").put("port", 70000)))
        assertNull(RemoteMessages.decodeState(JSONObject().put("state", "live").put("control", "maybe")))
        assertNull(RemoteMessages.decodeState(JSONObject().put("state", "live").put("key", 5)))
    }

    // ---- consent: grant, revoke, reboot, unlink ----

    @Test
    fun `a fresh tablet is off`() {
        assertEquals(RemoteMessages.OFF, ScreenConsent().state)
    }

    @Test
    fun `a parent asking before any grant needs consent, and the grant then goes live`() {
        val c = ScreenConsent()
        assertEquals(RemoteMessages.NEEDS_CONSENT, c.start())
        assertFalse(c.granted)
        assertEquals(RemoteMessages.LIVE, c.grant())
        assertTrue(c.granted)
    }

    @Test
    fun `a grant nobody asked for is ready, and a parent asking then goes live`() {
        val c = ScreenConsent()
        assertEquals(RemoteMessages.READY, c.grant())
        assertEquals(RemoteMessages.LIVE, c.start())
    }

    @Test
    fun `stopping keeps the grant, so the next viewing needs no new consent`() {
        val c = ScreenConsent()
        c.grant(); c.start()
        assertEquals(RemoteMessages.READY, c.stop())
        assertTrue(c.granted)
        assertEquals(RemoteMessages.LIVE, c.start())
    }

    @Test
    fun `stopping while still waiting for consent drops the request`() {
        val c = ScreenConsent()
        c.start()
        assertEquals(RemoteMessages.OFF, c.stop())
        assertEquals(RemoteMessages.READY, c.grant())
    }

    @Test
    fun `the system revoking the capture turns everything off and the next ask needs consent again`() {
        val c = ScreenConsent()
        c.grant(); c.start()
        assertEquals(RemoteMessages.OFF, c.revoke())
        assertFalse(c.granted)
        assertEquals(RemoteMessages.NEEDS_CONSENT, c.start())
    }

    @Test
    fun `unlink and reset release the grant`() {
        val c = ScreenConsent()
        c.grant()
        assertEquals(RemoteMessages.OFF, c.release())
        assertFalse(c.granted)
    }

    @Test
    fun `a reboot is a new object and starts off`() {
        val before = ScreenConsent().also { it.grant(); it.start() }
        assertEquals(RemoteMessages.LIVE, before.state)
        assertEquals(RemoteMessages.OFF, ScreenConsent().state)
    }

    @Test
    fun `asking again while live stays live`() {
        val c = ScreenConsent()
        c.grant(); c.start()
        assertEquals(RemoteMessages.LIVE, c.start())
    }

    // ---- the streamer gate ----

    @Test
    fun `nothing is encoded with no viewer`() {
        var now = 0L
        val g = FrameGate(clock = { now })
        assertFalse(g.shouldEncode(0))
        now = 10_000
        assertFalse(g.shouldEncode(0))
    }

    @Test
    fun `with a viewer at most 8 pictures a second`() {
        var now = 0L
        val g = FrameGate(clock = { now })
        var encoded = 0
        // A capture that offers a picture every 10 ms for 5 seconds.
        while (now < 5_000) {
            if (g.shouldEncode(1)) encoded++
            now += 10
        }
        // Offers come every 10 ms, so the 125 ms gap lands on 130 ms: just under 8 a second, never over.
        assertTrue("encoded $encoded", encoded in 35..40)
    }

    @Test
    fun `the first picture for a new viewer is not held back`() {
        var now = 1_000_000L
        val g = FrameGate(clock = { now })
        assertTrue(g.shouldEncode(1))
        assertFalse(g.shouldEncode(1))
        now += 125
        assertTrue(g.shouldEncode(1))
    }

    @Test
    fun `a viewer leaving stops the encoding at once`() {
        var now = 0L
        val g = FrameGate(clock = { now })
        assertTrue(g.shouldEncode(2))
        now += 500
        assertTrue(g.shouldEncode(1))
        now += 500
        assertFalse(g.shouldEncode(0))
    }

    // ---- the size of the shared picture ----

    @Test
    fun `the picture is at most 1280 on its longer side and keeps its shape`() {
        assertEquals(1280 to 800, ScreenSize.capture(2560, 1600))
        assertEquals(800 to 1280, ScreenSize.capture(1600, 2560))
        assertEquals(1024 to 600, ScreenSize.capture(1024, 600))
    }
}
