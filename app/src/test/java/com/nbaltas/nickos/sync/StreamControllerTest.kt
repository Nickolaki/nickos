package com.nbaltas.nickos.sync

import com.nbaltas.nickos.master.SyncController
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
class StreamControllerTest {
    private val port = FakeMasterPort()
    private var now = 100_000L
    private val c = SyncController(port, clock = { now })

    private val H = "hailey"
    private val L = "leo"
    private val fid = "jf:ab12cd34:ITEM1"
    private val token = "sekrit-token"
    private val url = JfStream.authedUrl("http://192.168.1.5:8096", "ITEM1", token)
    private val offsets = mapOf(H to 5000L, L to -3000L)

    @Before
    fun setUp() {
        port.add(H, "Hailey's Tablet", stream = true, sealed = true)
        port.add(L, "Leo's Tablet", stream = true, sealed = true)
    }

    private fun link(id: String) {
        c.onConnected(id)
        c.tick()
        val ping = port.cmds(id, "ping").last()
        val t0 = ping.getLong("t0")
        val off = offsets[id] ?: 0L
        c.onMessage(id, SyncMessages.encodePong(ping.getInt("id"), t0, t0 + off, t0 + off))
    }

    private fun report(id: String, state: SyncState, pos: Long = 0, f: String = fid, err: String? = null) =
        c.onMessage(id, SyncMessages.encodeSyncState(f, state.wire, pos, now + (offsets[id] ?: 0L), 600_000, 1f, err))

    private fun startStream(ids: List<String> = listOf(H, L)) {
        ids.forEach { link(it) }
        c.start(fid, "Star Harbour", 600_000, ids.toSet(), url)
    }

    private fun syncs(id: String) = port.cmds(id, "sync").map { SyncMessages.decodeSyncCmd(it)!! }

    // ---- who is sent the address ----

    @Test
    fun `start with a url keeps only sealed stream1 tablets and sends the url to them`() {
        port.add("old", "Mia's Tablet", stream = false, sealed = true)
        port.add("clear", "Zoe's Tablet", stream = true, sealed = false)
        listOf(H, L, "old", "clear").forEach { link(it) }
        c.start(fid, "Star Harbour", 600_000, setOf(H, L, "old", "clear"), url)
        assertEquals(setOf(H, L), c.session.value!!.nodes)
        assertEquals(url, syncs(H).single().url)
        assertEquals(url, syncs(L).single().url)
        assertTrue(port.cmds("old", "sync").isEmpty() && port.cmds("clear", "sync").isEmpty())
        assertTrue(port.sent.none { (it.first == "old" || it.first == "clear") && it.second.has("url") })
    }

    @Test
    fun `start with a url and no eligible tablet starts nothing`() {
        port.add("clear", "Zoe's Tablet", stream = true, sealed = false)
        link("clear")
        c.start(fid, "Star Harbour", 600_000, setOf("clear"), url)
        assertNull(c.session.value)
        assertTrue(port.sent.none { it.second.has("url") })
    }

    @Test
    fun `a movie on the tablets is sent with no url`() {
        link(H)
        c.start("f1", "Star Harbour", 600_000, setOf(H))
        assertNull(syncs(H).single().url)
    }

    @Test
    fun `the start is four seconds ahead for a stream and 1_5 s for a file`() {
        startStream()
        assertEquals(now + 4000 + 5000, syncs(H).single().at)
        assertEquals(now + 4000 - 3000, syncs(L).single().at)
        assertEquals(now + 4000, c.session.value!!.anchorAt)
        val file = SyncController(port, clock = { now })
        port.add("solo", "Mia's Tablet")
        file.onConnected("solo"); file.tick()
        val ping = port.cmds("solo", "ping").last()
        file.onMessage("solo", SyncMessages.encodePong(ping.getInt("id"), ping.getLong("t0"), ping.getLong("t0"), ping.getLong("t0")))
        file.start("f1", "Star", 600_000, setOf("solo"))
        assertEquals(now + 1500, SyncMessages.decodeSyncCmd(port.cmds("solo", "sync").single())!!.at)
    }

    @Test
    fun `a resend to a reconnected tablet carries the url only while it is still eligible`() {
        startStream()
        c.onDisconnected(H)
        port.online -= H
        port.online += H // back on a new link
        c.onConnected(H)
        c.tick()
        val ping = port.cmds(H, "ping").last()
        c.onMessage(H, SyncMessages.encodePong(ping.getInt("id"), ping.getLong("t0"), ping.getLong("t0") + 5000, ping.getLong("t0") + 5000))
        now += 200; c.tick()
        assertEquals(2, syncs(H).size)
        assertEquals(url, syncs(H).last().url)
    }

    @Test
    fun `a reconnected tablet on an unsealed link gets no url and leaves the session`() {
        startStream()
        val before = syncs(H).size
        c.onDisconnected(H)
        port.sealedLinks -= H // it came back on an old clear text link
        c.onConnected(H)
        c.tick()
        val ping = port.cmds(H, "ping").last()
        c.onMessage(H, SyncMessages.encodePong(ping.getInt("id"), ping.getLong("t0"), ping.getLong("t0") + 5000, ping.getLong("t0") + 5000))
        now += 200; c.tick()
        assertEquals(before, syncs(H).size)
        assertEquals(setOf(L), c.session.value!!.nodes)
        assertEquals(1, port.cmds(H, "syncStop").size)
    }

    @Test
    fun `the minute re anchor resends the url only to eligible tablets`() {
        startStream()
        now += 61_000
        c.tick()
        assertTrue(syncs(H).all { it.url == url })
        assertEquals(2, syncs(H).size)
    }

    @Test
    fun `a report that tries to join a stream session is ignored`() {
        port.add("sneak", "Mia's Tablet", stream = true, sealed = true)
        link("sneak")
        // No session at all: a report must not bring one back (it would have no url).
        report("sneak", SyncState.PLAYING)
        assertNull(c.session.value)
        startStream()
        report("sneak", SyncState.PLAYING)
        assertEquals(setOf(H, L), c.session.value!!.nodes)
        assertTrue(port.sent.none { it.first == "sneak" && it.second.optString("c") == "sync" })
    }

    // ---- the group wait ----

    private fun playBoth() {
        startStream()
        now += 4500
        report(H, SyncState.PLAYING, 500)
        report(L, SyncState.PLAYING, 500)
    }

    @Test
    fun `a tablet buffering for more than 3 s pauses the group and the parent sees who`() {
        playBoth()
        report(L, SyncState.BUFFERING, 800)
        now += 2900; c.tick()
        assertTrue(port.cmds(H, "syncPause").isEmpty())
        now += 300; c.tick()
        assertEquals(1, port.cmds(H, "syncPause").size)
        assertEquals(1, port.cmds(L, "syncPause").size)
        assertFalse(c.session.value!!.playing)
        assertEquals("Waiting for Leo", c.waiting.value)
    }

    @Test
    fun `everyone ready resumes the group on its own`() {
        playBoth()
        report(L, SyncState.BUFFERING, 800)
        now += 3200; c.tick()
        val plans = syncs(H).size
        report(L, SyncState.PAUSED, 800)
        assertTrue(c.session.value!!.playing)
        assertNull(c.waiting.value)
        assertEquals(plans + 1, syncs(H).size)
        assertEquals(url, syncs(L).last().url)
    }

    @Test
    fun `a pause by the parent is never undone by the wait`() {
        playBoth()
        report(L, SyncState.BUFFERING, 800)
        now += 3200; c.tick()
        c.pause()
        assertNull(c.waiting.value)
        report(L, SyncState.PAUSED, 800)
        now += 20_000; c.tick()
        assertFalse(c.session.value!!.playing)
        c.resume()
        assertTrue(c.session.value!!.playing)
    }

    @Test
    fun `the group is not held for more than 30 s`() {
        playBoth()
        report(L, SyncState.BUFFERING, 800)
        now += 3200; c.tick()
        now += 26_000; c.tick()
        assertFalse(c.session.value!!.playing)
        now += 4000; c.tick()
        assertTrue(c.session.value!!.playing)
        assertTrue(port.said.any { it == "Leo is too slow, carrying on without them" })
        assertNull(c.waiting.value)
    }

    @Test
    fun `a stale report from before a seek does not start a wait`() {
        playBoth()
        val staleAt = now - 10_000
        c.seek(30_000)
        c.onMessage(L, SyncMessages.encodeSyncState(fid, "buffering", 800, staleAt + -3000, 600_000, 1f))
        now += 10_000; c.tick()
        assertTrue(port.cmds(H, "syncPause").isEmpty())
    }

    @Test
    fun `a movie on the tablets is never held by the wait`() {
        link(H); link(L)
        c.start("f1", "Star Harbour", 600_000, setOf(H, L))
        now += 2000
        report(H, SyncState.PLAYING, 500, "f1")
        report(L, SyncState.BUFFERING, 500, "f1")
        now += 60_000; c.tick()
        assertTrue(port.cmds(H, "syncPause").isEmpty())
        assertTrue(c.session.value!!.playing)
        assertNull(c.waiting.value)
    }

    @Test
    fun `a tablet that went offline does not hold the group`() {
        playBoth()
        report(L, SyncState.BUFFERING, 800)
        now += 3200; c.tick()
        assertFalse(c.session.value!!.playing)
        port.online -= L
        c.onDisconnected(L)
        now += 150; c.tick()
        assertTrue(c.session.value!!.playing)
    }

    // ---- failures ----

    @Test
    fun `cant_reach is shown for that tablet only and the others keep playing`() {
        playBoth()
        report(L, SyncState.ERROR, 0, err = "cant_reach")
        assertEquals(setOf(H), c.session.value!!.nodes)
        assertTrue(c.session.value!!.playing)
        val f = c.failed.value.getValue(L)
        assertEquals("cant_reach", f.code)
        assertTrue(f.canSaveInstead)
        assertEquals(fid, f.fid)
        assertTrue(port.said.contains("Leo can't reach Jellyfin"))
    }

    @Test
    fun `unsupported gets no save offer`() {
        playBoth()
        report(L, SyncState.ERROR, 0, err = "unsupported")
        assertFalse(c.failed.value.getValue(L).canSaveInstead)
        assertTrue(port.said.contains("Leo's tablet can't play this one"))
    }

    @Test
    fun `a failed tablet never stalls the others`() {
        playBoth()
        report(L, SyncState.BUFFERING, 800)
        now += 3200; c.tick()
        assertFalse(c.session.value!!.playing)
        report(L, SyncState.ERROR, 0, err = "timeout")
        assertTrue(c.session.value!!.playing)
        assertTrue(c.failed.value.getValue(L).canSaveInstead)
    }

    @Test
    fun `an unknown error text is read as unsupported and never kept`() {
        playBoth()
        report(L, SyncState.ERROR, 0, err = "http://192.168.1.5/x?api_key=$token")
        assertEquals("unsupported", c.failed.value.getValue(L).code)
        assertTrue(port.said.none { it.contains(token) || it.contains("192.168") })
    }

    @Test
    fun `the failure is kept when it ends the session and cleared by a new start`() {
        link(L)
        c.start(fid, "Star Harbour", 600_000, setOf(L), url)
        now += 4500
        report(L, SyncState.ERROR, 0, err = "cant_reach")
        assertNull(c.session.value)
        assertNotNull(c.failed.value[L])
        c.dismissFailure(L)
        assertTrue(c.failed.value.isEmpty())
    }

    @Test
    fun `nothing the controller says or keeps holds the token`() {
        playBoth()
        report(L, SyncState.ERROR, 0, err = "auth")
        assertFalse(c.session.value.toString().contains(token))
        assertFalse(c.failed.value.toString().contains(token))
        assertTrue(port.said.none { it.contains(token) })
    }
}
