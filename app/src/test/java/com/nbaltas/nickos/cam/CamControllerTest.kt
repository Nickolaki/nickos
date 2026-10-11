package com.nbaltas.nickos.cam

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.nbaltas.nickos.App
import com.nbaltas.nickos.data.Store
import com.nbaltas.nickos.master.CamController
import com.nbaltas.nickos.master.CamStream
import com.nbaltas.nickos.master.MjpegClient
import com.nbaltas.nickos.master.NodeStatus
import com.nbaltas.nickos.sync.FakeMasterPort
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CamControllerTest {
    /** A stream that was asked for, with the two callbacks the real client would call. */
    private class Opened(val url: String, val onFrame: (MjpegClient.Frame) -> Unit, val onEnded: (String) -> Unit) : CamStream {
        var closed = false
        override fun close() { closed = true }
    }

    private val port = FakeMasterPort()
    private val store: Store get() = ApplicationProvider.getApplicationContext<App>().store
    private var now = 100_000L
    private val opened = ArrayList<Opened>()
    private lateinit var c: CamController

    private val H = "hailey"
    private val L = "leo"

    /** The far end of each tablet's live link. By default, the address the Master remembers for it. */
    private val linkHosts = HashMap<String, String?>()
    private fun from(id: String) = if (id in linkHosts) linkHosts[id] else port.linked.firstOrNull { it.id == id }?.host

    private fun controller() = CamController(
        port, store, clock = { now },
        open = { url, f, e -> Opened(url, f, e).also { opened += it } },
        soon = { it() },
    )

    private fun capable(id: String) {
        port.statuses[id] = NodeStatus.from(JSONObject().put("caps", JSONArray(listOf("sync1", "cam1"))))
    }

    @Before
    fun setUp() {
        store.resetAll()
        port.add(H, "Hailey's Tablet", host = "192.168.1.21")
        port.add(L, "Leo's Tablet", host = "192.168.1.22")
        capable(H)
        capable(L)
        c = controller()
    }

    private fun starts(id: String) = port.cmds(id, "camStart")
    private fun stops(id: String) = port.cmds(id, "camStop")

    private fun say(id: String, cam: NodeCam) = c.onMessage(id, CamMessages.encodeState(cam), from(id))
    private fun live(key: String = "abc123") = NodeCam(NodeCam.LIVE, port = 47472, key = key, maxMs = 600_000, now = 5_000)
    private fun frame() = MjpegClient.Frame(Bitmap.createBitmap(4, 3, Bitmap.Config.RGB_565), 90, now)

    /** Hailey's camera on and being watched. */
    private fun watching(): Opened {
        c.setViewing(true)
        c.arm(H)
        say(H, live())
        return opened.last()
    }

    @Test
    fun `nothing is asked of a tablet until its camera is switched on and the screen is open`() {
        c.tick()
        c.setViewing(true)
        c.tick()
        assertTrue(port.sent.isEmpty())
        c.setViewing(false)
        c.arm(H)
        c.tick()
        assertTrue(port.sent.isEmpty())
    }

    @Test
    fun `switching a camera on with the screen open asks the tablet for it`() {
        c.setViewing(true)
        c.arm(H)
        val start = CamMessages.decodeStart(starts(H).single())
        assertEquals(CamMessages.StartCmd("normal", 600_000), start)
        assertTrue(starts(L).isEmpty())
    }

    @Test
    fun `the tablet is not asked again inside three seconds`() {
        c.setViewing(true)
        c.arm(H)
        now += 1_000; c.tick()
        now += 1_000; c.tick()
        assertEquals(1, starts(H).size)
        now += 1_000; c.tick()
        assertEquals(2, starts(H).size)
    }

    @Test
    fun `a camera that is on gets a stream at the tablet's own address`() {
        val s = watching()
        assertEquals("http://192.168.1.21:47472/cam?k=abc123", s.url)
        c.tick()
        assertEquals(1, opened.size)
    }

    @Test
    fun `the key only goes back to where the camState came from`() {
        // The Master remembers 192.168.1.21 for Hailey, but her link is up at another address.
        linkHosts[H] = "10.0.0.9"
        assertEquals("http://10.0.0.9:47472/cam?k=abc123", watching().url)
    }

    @Test
    fun `a camState from nowhere known gets no stream, whatever address is remembered`() {
        linkHosts[H] = null
        c.setViewing(true)
        c.arm(H)
        say(H, live())
        assertTrue(opened.isEmpty())
    }

    @Test
    fun `a tablet reached somewhere new loses the stream to the old place`() {
        val old = watching()
        linkHosts[H] = "192.168.1.77"
        say(H, live())
        assertTrue(old.closed)
        assertEquals("http://192.168.1.77:47472/cam?k=abc123", opened.last().url)
    }

    @Test
    fun `after the link drops the old address is forgotten until the tablet speaks again`() {
        watching()
        c.onDisconnected(H)
        val before = opened.size
        now += 5_000; c.tick()
        assertEquals(before, opened.size)
    }

    @Test
    fun `pictures reach the screen and go when the stream ends`() {
        val s = watching()
        assertNull(c.frames(H).value)
        val f = frame()
        s.onFrame(f)
        assertEquals(f, c.frames(H).value)
        s.onEnded(CamPlan.ENDED_IO)
        assertNull(c.frames(H).value)
    }

    @Test
    fun `a dropped stream is opened again after two seconds`() {
        val s = watching()
        s.onFrame(frame())
        s.onEnded(CamPlan.ENDED_IO)
        now += 1_000; c.tick()
        assertEquals(1, opened.size)
        now += 1_000; c.tick()
        assertEquals(2, opened.size)
        assertTrue(stops(H).isEmpty())
    }

    @Test
    fun `three streams in a row with no picture start the camera fresh`() {
        watching().onEnded(CamPlan.ENDED_IO)
        now += 2_000; c.tick()
        opened.last().onEnded(CamPlan.ENDED_IO)
        now += 2_000; c.tick()
        opened.last().onEnded(CamPlan.ENDED_IO)
        assertEquals(3, opened.size)
        val before = starts(H).size
        now += 2_000; c.tick()
        assertEquals(1, stops(H).size)
        assertEquals(3, opened.size)
        now += 1_000; c.tick()
        assertEquals(before + 1, starts(H).size)
    }

    @Test
    fun `a refused key asks the tablet again and the new key gets a new stream`() {
        val s = watching()
        val before = starts(H).size
        now += 5_000
        s.onEnded(CamPlan.ENDED_KEY)
        c.tick()
        assertEquals(before + 1, starts(H).size)
        say(H, live(key = "newkey9"))
        assertEquals("http://192.168.1.21:47472/cam?k=newkey9", opened.last().url)
    }

    @Test
    fun `leaving the screen turns the camera off and closes the stream`() {
        val s = watching()
        c.setViewing(false)
        assertEquals(1, stops(H).size)
        assertTrue(s.closed)
        assertNull(c.frames(H).value)
        // Still switched on for next time, and not asked for while away.
        assertTrue(H in c.armed.value)
        val before = starts(H).size
        now += 10_000; c.tick()
        assertEquals(before, starts(H).size)
        assertEquals(1, stops(H).size)
    }

    @Test
    fun `coming back to the screen starts a switched on camera again`() {
        watching()
        c.setViewing(false)
        say(H, NodeCam(NodeCam.OFF, reason = NodeCam.STOPPED))
        val before = starts(H).size
        now += 10_000
        c.setViewing(true)
        assertEquals(before + 1, starts(H).size)
    }

    @Test
    fun `switching a camera off turns it off`() {
        val s = watching()
        c.toggle(H)
        assertFalse(H in c.armed.value)
        assertEquals(1, stops(H).size)
        assertTrue(s.closed)
    }

    @Test
    fun `the switches are remembered`() {
        c.arm(H)
        assertEquals(setOf(H), store.camArmed)
        assertEquals(setOf(H), controller().armed.value)
        c.disarm(H)
        assertTrue(controller().armed.value.isEmpty())
    }

    @Test
    fun `auto off flips the switch off, says why and leaves the tile`() {
        watching()
        say(H, NodeCam(NodeCam.OFF, reason = NodeCam.AUTO_OFF, maxMs = 300_000))
        assertFalse(H in c.armed.value)
        assertEquals(listOf("Hailey's camera turned off after 5 min to keep it cool"), port.said)
        assertTrue(CamPlan.lingers(c.states.value[H]))
        val before = starts(H).size
        now += 20_000; c.tick()
        assertEquals(before, starts(H).size)
        // The same news again (a reconnect) is not another toast.
        say(H, NodeCam(NodeCam.OFF, reason = NodeCam.AUTO_OFF, maxMs = 300_000))
        assertEquals(1, port.said.size)
    }

    @Test
    fun `too hot and low battery flip the switch off with their own words`() {
        watching()
        say(H, NodeCam(NodeCam.OFF, reason = NodeCam.TOO_HOT, level = NodeCam.LEVEL_TOO_HOT, retryAt = 125_000, now = 5_000))
        assertFalse(H in c.armed.value)
        c.setViewing(true)
        c.arm(L)
        say(L, live())
        say(L, NodeCam(NodeCam.OFF, reason = NodeCam.LOW_BATTERY))
        assertEquals(
            listOf("Hailey's tablet is too hot. Camera off for 2 minutes", "Leo's battery is too low for the camera"),
            port.said,
        )
    }

    @Test
    fun `turning a too hot camera on again waits for the cooldown`() {
        watching()
        say(H, NodeCam(NodeCam.OFF, reason = NodeCam.TOO_HOT, retryAt = 125_000, now = 5_000))
        val before = starts(H).size
        c.arm(H)
        now += 60_000; c.tick()
        assertEquals(before, starts(H).size)
        assertEquals(NodeCam.TOO_HOT, c.states.value[H]?.reason)
        now += 60_000; c.tick()
        assertEquals(before + 1, starts(H).size)
    }

    @Test
    fun `a finished tile is gone the next time the screen opens`() {
        watching()
        say(H, NodeCam(NodeCam.OFF, reason = NodeCam.AUTO_OFF, maxMs = 600_000))
        c.setViewing(false)
        c.setViewing(true)
        assertNull(c.states.value[H])
    }

    @Test
    fun `a missing permission stays switched on and is not asked for again until the parent does`() {
        c.setViewing(true)
        c.arm(H)
        say(H, NodeCam(NodeCam.OFF, reason = NodeCam.PERMISSION))
        assertTrue(H in c.armed.value)
        assertTrue(port.said.isEmpty())
        now += 60_000; c.tick()
        assertEquals(1, starts(H).size)
        c.toggle(H)
        c.toggle(H)
        assertEquals(2, starts(H).size)
    }

    @Test
    fun `a camera that failed to start is tried again after 15 seconds`() {
        c.setViewing(true)
        c.arm(H)
        say(H, NodeCam(NodeCam.OFF, reason = NodeCam.BACKGROUND))
        now += 14_000; c.tick()
        assertEquals(1, starts(H).size)
        now += 1_000; c.tick()
        assertEquals(2, starts(H).size)
    }

    @Test
    fun `an old tablet is never sent a camera command`() {
        port.add("old", "Mia's Tablet", capable = true)
        c.setViewing(true)
        c.arm("old")
        repeat(5) { now += 5_000; c.tick() }
        c.setViewing(false)
        assertTrue(port.sent.none { it.first == "old" })
    }

    @Test
    fun `an offline tablet is sent nothing and loses its stream`() {
        val s = watching()
        port.online -= H
        c.tick()
        assertTrue(s.closed)
        c.onDisconnected(H)
        assertNull(c.states.value[H])
        c.setViewing(false)
        assertTrue(stops(H).isEmpty())
    }

    @Test
    fun `another parent's camera is watched when wanted but never turned off by this Master`() {
        // Leo's camera is on because another parent tablet asked. This one has it switched on from last time.
        c.arm(L)
        say(L, live())
        now += 5_000; c.tick()
        assertTrue(stops(L).isEmpty())
        assertTrue(opened.isEmpty())
        // Opening the screen joins the stream without asking for a start.
        c.setViewing(true)
        assertEquals(1, opened.size)
        assertTrue(starts(L).isEmpty())
    }

    @Test
    fun `an unlinked tablet takes its stream with it`() {
        val s = watching()
        port.linked = port.linked.filterNot { it.id == H }
        c.tick()
        assertTrue(s.closed)
    }

    @Test
    fun `a line that is not a camState changes nothing`() {
        c.onMessage(H, JSONObject().put("t", "camState").put("state", "sideways"), from(H))
        assertTrue(c.states.value.isEmpty())
    }

    @Test
    fun `a tablet with no address yet gets no stream`() {
        port.add("x", "Mia's Tablet", host = null)
        capable("x")
        c.setViewing(true)
        c.arm("x")
        say("x", live())
        assertTrue(opened.isEmpty())
        assertNotNull(c.states.value["x"])
    }

    @Test
    fun `stream addresses are built only from plain parts`() {
        assertEquals("http://192.168.1.21:47472/cam?k=abc", CamPlan.streamUrl("192.168.1.21", 47472, "abc"))
        assertEquals("http://[fe80::1]:47472/cam?k=abc", CamPlan.streamUrl("fe80::1", 47472, "abc"))
        assertNull(CamPlan.streamUrl(null, 47472, "abc"))
        assertNull(CamPlan.streamUrl("192.168.1.21", 0, "abc"))
        assertNull(CamPlan.streamUrl("192.168.1.21", 47472, null))
        assertNull(CamPlan.streamUrl("192.168.1.21", 47472, "a&b=c"))
        assertNull(CamPlan.streamUrl("evil.example/path?", 47472, "abc"))
        assertNull(CamPlan.streamUrl("user@host", 47472, "abc"))
    }
}
