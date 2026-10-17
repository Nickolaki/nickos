package com.nbaltas.nickos.remote

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
class ScreenStage2Test {
    private fun wire(o: JSONObject) = JSONObject(o.toString())

    // ---- the touch codec ----

    @Test
    fun `tap swipe back and home round trip`() {
        assertEquals(RemoteMessages.Touch.Tap(0.25, 0.75), RemoteMessages.decodeTouch(wire(RemoteMessages.cmdTap(0.25, 0.75))))
        assertEquals(
            RemoteMessages.Touch.Swipe(0.1, 0.2, 0.9, 0.8, 300),
            RemoteMessages.decodeTouch(wire(RemoteMessages.cmdSwipe(0.1, 0.2, 0.9, 0.8, 300))),
        )
        assertEquals(RemoteMessages.Touch.Back, RemoteMessages.decodeTouch(wire(RemoteMessages.cmdBack())))
        assertEquals(RemoteMessages.Touch.Home, RemoteMessages.decodeTouch(wire(RemoteMessages.cmdHome())))
        val o = wire(RemoteMessages.cmdTap(0.5, 0.5))
        assertEquals("cmd", o.getString("t"))
        assertEquals("touch", o.getString("c"))
        assertEquals("tap", o.getString("k"))
    }

    @Test
    fun `the edges 0 and 1 are on the screen`() {
        assertEquals(RemoteMessages.Touch.Tap(0.0, 1.0), RemoteMessages.decodeTouch(JSONObject().put("k", "tap").put("x", 0).put("y", 1)))
    }

    @Test
    fun `a coordinate outside 0 to 1 is rejected`() {
        assertNull(RemoteMessages.decodeTouch(JSONObject().put("k", "tap").put("x", 1.01).put("y", 0.5)))
        assertNull(RemoteMessages.decodeTouch(JSONObject().put("k", "tap").put("x", -0.01).put("y", 0.5)))
        assertNull(RemoteMessages.decodeTouch(JSONObject().put("k", "tap").put("x", 0.5).put("y", 2)))
        assertNull(RemoteMessages.decodeTouch(JSONObject().put("k", "swipe").put("x1", 0.1).put("y1", 0.1).put("x2", 0.5).put("y2", 1.5).put("ms", 200)))
    }

    @Test
    fun `a missing or wrong typed field is rejected`() {
        assertNull(RemoteMessages.decodeTouch(JSONObject().put("k", "tap").put("x", 0.5)))
        assertNull(RemoteMessages.decodeTouch(JSONObject().put("k", "tap").put("x", "0.5").put("y", 0.5)))
        assertNull(RemoteMessages.decodeTouch(JSONObject().put("k", "tap").put("x", true).put("y", 0.5)))
        assertNull(RemoteMessages.decodeTouch(JSONObject().put("k", "tap").put("x", JSONObject.NULL).put("y", 0.5)))
        assertNull(RemoteMessages.decodeTouch(JSONObject().put("k", 7)))
        assertNull(RemoteMessages.decodeTouch(JSONObject()))
    }

    @Test
    fun `an unknown kind is rejected`() {
        assertNull(RemoteMessages.decodeTouch(JSONObject().put("k", "pinch")))
        assertNull(RemoteMessages.decodeTouch(JSONObject().put("k", "TAP").put("x", 0.5).put("y", 0.5)))
    }

    @Test
    fun `a swipe time must be 50 to 2000 ms`() {
        fun swipe(ms: Any) = JSONObject().put("k", "swipe").put("x1", 0.1).put("y1", 0.1).put("x2", 0.9).put("y2", 0.9).put("ms", ms)
        assertNull(RemoteMessages.decodeTouch(swipe(49)))
        assertNotNull(RemoteMessages.decodeTouch(swipe(50)))
        assertNotNull(RemoteMessages.decodeTouch(swipe(2000)))
        assertNull(RemoteMessages.decodeTouch(swipe(2001)))
        assertNull(RemoteMessages.decodeTouch(swipe("300")))
        assertNull(RemoteMessages.decodeTouch(swipe(300.5)))
        assertNull(RemoteMessages.decodeTouch(JSONObject().put("k", "swipe").put("x1", 0.1).put("y1", 0.1).put("x2", 0.9).put("y2", 0.9)))
    }

    // ---- 20 a second ----

    @Test
    fun `the 21st touch in one second is dropped`() {
        var now = 0L
        val l = TouchLimiter(clock = { now })
        repeat(20) { assertTrue("touch $it", l.allow()); now += 10 }
        assertFalse(l.allow())
        now = 999
        assertFalse(l.allow())
    }

    @Test
    fun `touches are allowed again as the second passes`() {
        var now = 0L
        val l = TouchLimiter(clock = { now })
        repeat(20) { assertTrue(l.allow()) }
        assertFalse(l.allow())
        now = 1000
        assertTrue(l.allow())
    }

    @Test
    fun `a steady 20 a second is never dropped`() {
        var now = 0L
        val l = TouchLimiter(clock = { now })
        var dropped = 0
        repeat(200) { if (!l.allow()) dropped++; now += 50 }
        assertEquals(0, dropped)
    }

    // ---- where a touch lands ----

    @Test
    fun `a picture that fills the view maps straight through`() {
        val n = TouchMap.toNorm(100f, 50f, 400f, 200f, 800, 400)!!
        assertEquals(0.25, n.x, 1e-6)
        assertEquals(0.25, n.y, 1e-6)
    }

    @Test
    fun `a portrait picture in a landscape view has black bars at the sides`() {
        // View 1000 by 500, picture 300 by 500: it is 300 wide, centred, so it spans x 350 to 650.
        val box = TouchMap.content(1000f, 500f, 300, 500)!!
        assertEquals(350f, box.left, 1e-3f)
        assertEquals(300f, box.width, 1e-3f)
        assertEquals(0f, box.top, 1e-3f)
        assertEquals(500f, box.height, 1e-3f)
        assertNull(TouchMap.toNorm(100f, 250f, 1000f, 500f, 300, 500))
        assertNull(TouchMap.toNorm(900f, 250f, 1000f, 500f, 300, 500))
        val left = TouchMap.toNorm(350f, 0f, 1000f, 500f, 300, 500)!!
        assertEquals(0.0, left.x, 1e-6)
        assertEquals(0.0, left.y, 1e-6)
        val mid = TouchMap.toNorm(500f, 250f, 1000f, 500f, 300, 500)!!
        assertEquals(0.5, mid.x, 1e-6)
        assertEquals(0.5, mid.y, 1e-6)
        val right = TouchMap.toNorm(650f, 500f, 1000f, 500f, 300, 500)!!
        assertEquals(1.0, right.x, 1e-6)
        assertEquals(1.0, right.y, 1e-6)
    }

    @Test
    fun `a landscape picture in a tall view has black bars above and below`() {
        // View 400 by 1000, picture 800 by 400: 400 wide and 200 tall, centred, so it spans y 400 to 600.
        val box = TouchMap.content(400f, 1000f, 800, 400)!!
        assertEquals(0f, box.left, 1e-3f)
        assertEquals(400f, box.top, 1e-3f)
        assertEquals(200f, box.height, 1e-3f)
        assertNull(TouchMap.toNorm(200f, 100f, 400f, 1000f, 800, 400))
        assertNull(TouchMap.toNorm(200f, 900f, 400f, 1000f, 800, 400))
        val n = TouchMap.toNorm(100f, 450f, 400f, 1000f, 800, 400)!!
        assertEquals(0.25, n.x, 1e-6)
        assertEquals(0.25, n.y, 1e-6)
    }

    @Test
    fun `an empty view or picture maps nowhere`() {
        assertNull(TouchMap.content(0f, 100f, 10, 10))
        assertNull(TouchMap.content(100f, 100f, 0, 10))
        assertNull(TouchMap.toNorm(5f, 5f, 100f, 100f, 10, 0))
    }

    @Test
    fun `the same place scales to the screen in each rotation`() {
        // The centre is the centre whichever way the tablet is held.
        assertEquals(639.5f to 399.5f, TouchMap.toPixels(0.5, 0.5, 1280, 800))
        assertEquals(399.5f to 639.5f, TouchMap.toPixels(0.5, 0.5, 800, 1280))
        // A quarter across and three quarters down.
        val land = TouchMap.toPixels(0.25, 0.75, 1280, 800)
        val port = TouchMap.toPixels(0.25, 0.75, 800, 1280)
        assertEquals(0.25f * 1279f, land.first, 1e-3f)
        assertEquals(0.75f * 799f, land.second, 1e-3f)
        assertEquals(0.25f * 799f, port.first, 1e-3f)
        assertEquals(0.75f * 1279f, port.second, 1e-3f)
    }

    @Test
    fun `the corners land on the last pixel, inside the screen`() {
        assertEquals(0f to 0f, TouchMap.toPixels(0.0, 0.0, 1280, 800))
        assertEquals(1279f to 799f, TouchMap.toPixels(1.0, 1.0, 1280, 800))
    }

    @Test
    fun `a letterboxed tap reaches the right pixel on a turned tablet`() {
        // The parent taps the middle of a portrait picture shown in a landscape window.
        val n = TouchMap.toNorm(500f, 250f, 1000f, 500f, 300, 500)!!
        // The kid tablet has since turned to landscape; the same 0 to 1 point is still its middle.
        val p = TouchMap.toPixels(n.x, n.y, 1280, 800)
        assertEquals(639.5f, p.first, 1e-3f)
        assertEquals(399.5f, p.second, 1e-3f)
    }
}
