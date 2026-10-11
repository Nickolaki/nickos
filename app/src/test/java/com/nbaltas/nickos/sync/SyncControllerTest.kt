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
class SyncControllerTest {
    private val port = FakeMasterPort()
    private var now = 100_000L
    private val c = SyncController(port, clock = { now })

    private val H = "hailey"
    private val L = "leo"

    @Before
    fun setUp() {
        port.add(H, "Hailey's Tablet")
        port.add(L, "Leo's Tablet")
    }

    /** Connects [id] and answers one ping so its clock reads Master + [offset]. */
    private fun link(id: String, offset: Long) {
        c.onConnected(id)
        c.tick()
        pong(id, offset)
    }

    private fun pong(id: String, offset: Long) {
        val ping = port.cmds(id, "ping").last()
        val t0 = ping.getLong("t0")
        c.onMessage(id, SyncMessages.encodePong(ping.getInt("id"), t0, t0 + offset, t0 + offset))
    }

    private fun report(id: String, state: SyncState, pos: Long, fid: String = "f1", at: Long = now, dur: Long = 600_000, err: String? = null) =
        c.onMessage(id, SyncMessages.encodeSyncState(fid, state.wire, pos, at, dur, 1f, err))

    private fun startBoth() {
        link(H, 5000)
        link(L, -3000)
        c.start("f1", "Star Harbour", 600_000, setOf(H, L))
    }

    @Test
    fun `a tablet on an old NickOS is never sent a new command`() {
        port.add("old", "Mia's Tablet", capable = false)
        c.onConnected("old")
        repeat(10) { now += 150; c.tick() }
        c.rescan()
        assertTrue(port.sent.none { it.first == "old" })
    }

    @Test
    fun `a capable tablet is asked for its videos and pinged five times on connect`() {
        c.onConnected(H)
        repeat(10) { now += 150; c.tick() }
        assertEquals(1, port.cmds(H, "videos").size)
        assertEquals(5, port.cmds(H, "ping").size)
        assertTrue(H in c.scanning.value)
        c.onMessage(H, SyncMessages.encodeVideos(true, listOf(SyncMessages.VideoEntry("f1", "Star Harbour", "Star Harbour.mp4", 9_000_000, 600_000))))
        assertFalse(H in c.scanning.value)
        assertEquals(listOf("f1"), c.library().map { it.fid })
    }

    @Test
    fun `pings slow to every 30 s when idle and every 2 s with the screen open`() {
        link(H, 0)
        repeat(10) { now += 150; c.tick() }
        val base = port.cmds(H, "ping").size
        repeat(100) { now += 150; c.tick() } // 15 s
        assertEquals(base, port.cmds(H, "ping").size)
        c.screenOpen = true
        repeat(40) { now += 150; c.tick() } // 6 s
        assertEquals(base + 3, port.cmds(H, "ping").size)
    }

    @Test
    fun `each tablet gets the start time in its own clock and the same position`() {
        startBoth()
        val h = SyncMessages.decodeSyncCmd(port.cmds(H, "sync").single())!!
        val l = SyncMessages.decodeSyncCmd(port.cmds(L, "sync").single())!!
        assertEquals(now + 1500 + 5000, h.at)
        assertEquals(now + 1500 - 3000, l.at)
        assertEquals(0, h.pos)
        assertEquals(h.pos, l.pos)
        assertEquals(2, h.n)
        assertEquals("f1", h.fid)
        assertEquals(setOf(H, L), c.session.value!!.nodes)
    }

    @Test
    fun `a single tablet is a group of one`() {
        link(H, 0)
        c.start("f1", "Star Harbour", 600_000, setOf(H))
        assertEquals(1, SyncMessages.decodeSyncCmd(port.cmds(H, "sync").single())!!.n)
        assertTrue(port.cmds(L, "sync").isEmpty())
    }

    @Test
    fun `a plan waits until the tablet's clock offset is known`() {
        c.onConnected(H)
        c.start("f1", "Star Harbour", 600_000, setOf(H))
        c.tick()
        assertTrue(port.cmds(H, "sync").isEmpty())
        pong(H, 700)
        now += 150; c.tick()
        val plan = SyncMessages.decodeSyncCmd(port.cmds(H, "sync").single())!!
        assertEquals(now + 1500 + 700, plan.at)
    }

    @Test
    fun `pause freezes the group and resume starts everyone from there`() {
        startBoth()
        now += 1500 + 20_000
        c.pause()
        assertEquals(1, port.cmds(H, "syncPause").size)
        assertEquals(1, port.cmds(L, "syncPause").size)
        now += 60_000
        assertEquals(20_000, c.expectedPos())
        c.resume()
        val h = SyncMessages.decodeSyncCmd(port.cmds(H, "sync").last())!!
        assertEquals(20_000, h.pos)
        assertEquals(now + 1500 + 5000, h.at)
        assertTrue(c.session.value!!.playing)
    }

    @Test
    fun `seek and skip move everyone and clamp to the movie`() {
        startBoth()
        now += 1500 + 30_000
        c.skip(-10_000)
        assertEquals(20_000, SyncMessages.decodeSyncCmd(port.cmds(L, "sync").last())!!.pos)
        c.seek(999_999_999)
        assertEquals(600_000, c.session.value!!.anchorPos)
        c.seek(-5)
        assertEquals(0, c.session.value!!.anchorPos)
    }

    @Test
    fun `seeking while paused sends the spot and keeps everyone paused`() {
        startBoth()
        now += 5000
        c.pause()
        c.seek(120_000)
        assertEquals(120_000, SyncMessages.decodeSyncCmd(port.cmds(H, "sync").last())!!.pos)
        assertEquals(2, port.cmds(H, "syncPause").size)
        assertFalse(c.session.value!!.playing)
    }

    @Test
    fun `reports are never answered with a seek`() {
        startBoth()
        now += 10_000
        val before = port.sent.size
        repeat(20) { report(H, SyncState.PLAYING, 2000); report(L, SyncState.BUFFERING, 90_000) }
        assertEquals(before, port.sent.size)
    }

    @Test
    fun `drift is read through the tablet's clock offset`() {
        startBoth()
        now += 1500 + 10_000
        report(H, SyncState.PLAYING, 10_000, at = now + 5000)
        assertEquals(0L, c.drift(H))
        assertEquals("In sync", c.stateLine(H))
        assertTrue(c.inSync(H))
        report(L, SyncState.PLAYING, 9_400, at = now - 3000)
        assertEquals(-600L, c.drift(L))
        assertEquals("0.6 s behind", c.stateLine(L))
        now += 2000 // both carried on playing: the lines hold
        assertEquals(0L, c.drift(H))
        port.online -= L
        assertEquals("Offline, will catch up", c.stateLine(L))
    }

    @Test
    fun `starting counts tablets still getting ready`() {
        startBoth()
        report(H, SyncState.LOADING, 0)
        report(L, SyncState.READY, 0)
        assertEquals(2, c.starting())
        report(H, SyncState.PLAYING, 0)
        assertEquals(1, c.starting())
    }

    @Test
    fun `the movie length comes from the tablet when the library did not know it`() {
        link(H, 0)
        c.start("f1", "Star Harbour", 0, setOf(H))
        report(H, SyncState.PLAYING, 0, dur = 480_000)
        assertEquals(480_000, c.session.value!!.durMs)
    }

    @Test
    fun `every sixty seconds the anchor is sent again with no lead`() {
        startBoth()
        val sent = port.cmds(H, "sync").size
        repeat(399) { now += 150; c.tick() } // 59.85 s
        assertEquals(sent, port.cmds(H, "sync").size)
        now += 150; c.tick()
        val plan = SyncMessages.decodeSyncCmd(port.cmds(H, "sync").last())!!
        assertEquals(sent + 1, port.cmds(H, "sync").size)
        assertEquals(now + 5000, plan.at)
        assertEquals(60_000L - 1500, plan.pos)
    }

    @Test
    fun `no re-anchor while paused`() {
        startBoth()
        c.pause()
        val sent = port.cmds(H, "sync").size
        repeat(500) { now += 150; c.tick() }
        assertEquals(sent, port.cmds(H, "sync").size)
    }

    @Test
    fun `a tablet that reconnects is sent where the group is`() {
        startBoth()
        now += 1500 + 40_000
        port.online -= L
        c.onDisconnected(L)
        now += 10_000; c.tick()
        port.online += L
        c.onConnected(L)
        c.tick()
        assertEquals(1, port.cmds(L, "sync").size) // its clock may have restarted: nothing until a fresh ping
        pong(L, 250)
        now += 150; c.tick()
        val plan = SyncMessages.decodeSyncCmd(port.cmds(L, "sync").last())!!
        assertEquals(now + 1500 + 250, plan.at)
        assertEquals(40_000L + 10_000 + 150 + 1500, plan.pos)
    }

    @Test
    fun `a tablet offline for more than a minute is dropped and the rest carry on`() {
        startBoth()
        port.online -= L
        c.onDisconnected(L)
        repeat(399) { now += 150; c.tick() }
        assertEquals(setOf(H, L), c.session.value!!.nodes)
        repeat(3) { now += 150; c.tick() }
        assertEquals(setOf(H), c.session.value!!.nodes)
    }

    @Test
    fun `finished on every tablet ends the session with a toast`() {
        startBoth()
        report(H, SyncState.ENDED, 600_000)
        assertEquals(setOf(L), c.session.value!!.nodes)
        assertTrue(port.said.isEmpty())
        report(L, SyncState.ENDED, 600_000)
        assertNull(c.session.value)
        assertEquals(listOf("Finished on everyone's tablet"), port.said)
    }

    @Test
    fun `a tablet that cannot play it is dropped and the parent is told why`() {
        startBoth()
        report(L, SyncState.ERROR, 0, err = "missing")
        assertEquals(setOf(H), c.session.value!!.nodes)
        assertEquals(listOf("Leo: Can't find this movie on the tablet"), port.said)
        report(H, SyncState.ERROR, 0, err = "decode")
        assertNull(c.session.value)
        assertEquals("Hailey: Can't play this file", port.said.last())
    }

    @Test
    fun `stop tells everyone and a late report does not bring the session back`() {
        startBoth()
        c.stop()
        assertEquals(1, port.cmds(H, "syncStop").size)
        assertEquals(1, port.cmds(L, "syncStop").size)
        assertNull(c.session.value)
        now += 300
        report(H, SyncState.PLAYING, 5000)
        assertNull(c.session.value)
    }

    @Test
    fun `removing one tablet stops only that one`() {
        startBoth()
        c.remove(L)
        assertEquals(1, port.cmds(L, "syncStop").size)
        assertTrue(port.cmds(H, "syncStop").isEmpty())
        assertEquals(setOf(H), c.session.value!!.nodes)
        report(L, SyncState.PLAYING, 100)
        assertEquals(setOf(H), c.session.value!!.nodes)
        c.remove(H)
        assertNull(c.session.value)
        assertTrue("no Finished toast for a removal", port.said.isEmpty())
    }

    @Test
    fun `a tablet sent home by the parent leaves the session`() {
        startBoth()
        report(L, SyncState.STOPPED, 0)
        assertEquals(setOf(H), c.session.value!!.nodes)
    }

    @Test
    fun `starting another movie stops tablets that are not in the new group`() {
        startBoth()
        c.start("f2", "Captain Comet", 300_000, setOf(H))
        assertEquals(1, port.cmds(L, "syncStop").size)
        assertTrue(port.cmds(H, "syncStop").isEmpty())
        assertEquals("f2", SyncMessages.decodeSyncCmd(port.cmds(H, "sync").last())!!.fid)
        report(H, SyncState.PLAYING, 50_000, fid = "f1") // still on the old movie for a moment
        assertNull(c.states.value[H])
    }

    @Test
    fun `next episode plays on the same tablets when they all have it`() {
        fun e(fid: String, title: String) = SyncMessages.VideoEntry(fid, title, "$title.mp4", 9_000_000, 600_000)
        link(H, 0); link(L, 0)
        c.onMessage(H, SyncMessages.encodeVideos(true, listOf(e("e1", "Star Harbour S01E01"), e("e2", "Star Harbour S01E02"), e("e3", "Star Harbour S01E03"))))
        c.onMessage(L, SyncMessages.encodeVideos(true, listOf(e("e1", "Star Harbour S01E01"), e("e2", "Star Harbour S01E02"))))
        c.start("e1", "Star Harbour S01E01", 600_000, setOf(H, L))
        assertEquals("e2", c.next()?.fid)
        assertTrue(c.playNext())
        assertEquals("e2", c.session.value!!.fid)
        assertEquals(setOf(H, L), c.session.value!!.nodes)
        assertNull("Leo lacks episode 3", c.next())
        assertFalse(c.playNext())
    }

    @Test
    fun `a restarted Master takes the session back from the tablets' reports`() {
        link(H, 5000)
        c.onMessage(H, SyncMessages.encodeVideos(true, listOf(SyncMessages.VideoEntry("f1", "Star Harbour", "Star Harbour.mp4", 9_000_000, 600_000))))
        report(H, SyncState.PLAYING, 77_000, at = now + 5000)
        val s = c.session.value
        assertNotNull(s)
        assertEquals("Star Harbour", s!!.title)
        assertEquals(setOf(H), s.nodes)
        now += 4000
        assertEquals(81_000, c.expectedPos())
    }

    @Test
    fun `a report for a movie the tablet is not known to have starts nothing`() {
        link(H, 0)
        report(H, SyncState.PLAYING, 77_000)
        assertNull(c.session.value)
        startBoth()
        port.add("mia", "Mia's Tablet")
        link("mia", 0)
        report("mia", SyncState.PLAYING, 10)
        assertEquals(setOf(H, L), c.session.value!!.nodes)
    }

    @Test
    fun `this tablet's own library sits under SELF`() {
        port.local = SyncMessages.VideosMsg(true, listOf(SyncMessages.VideoEntry("f9", "Moss and Pebble", "Moss and Pebble.mkv", 9_000_000, 0)))
        c.scanSelf()
        assertEquals(setOf(Library.SELF), c.library().single().owners)
        assertTrue(c.videos.value[Library.SELF]!!.perm)
    }

    @Test
    fun `kid name comes from the linked tablet`() {
        assertEquals("Hailey", c.kid(H))
        assertEquals("A tablet", c.kid("nobody"))
    }
}
