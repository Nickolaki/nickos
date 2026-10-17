package com.nbaltas.nickos.sync

import com.nbaltas.nickos.master.SyncController
import com.nbaltas.nickos.master.TransferController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TransferControllerTest {
    private val port = FakeMasterPort()
    private var now = 100_000L
    private val sync = SyncController(port, clock = { now })
    private val t = TransferController(port, sync, clock = { now })

    private val H = "hailey"
    private val L = "leo"
    private val M = "mia"
    private val star = SyncMessages.VideoEntry("f1", "Star Harbour", "Star Harbour.mp4", 3_000_000_000, 5_520_000)
    private val comet = SyncMessages.VideoEntry("f2", "Captain Comet", "Captain Comet.mkv", 900_000_000, 3_000_000)

    @Before
    fun setUp() {
        port.add(H, "Hailey's Tablet")
        port.add(L, "Leo's Tablet")
        port.add(M, "Mia's Tablet")
    }

    private fun has(id: String, vararg v: SyncMessages.VideoEntry) = sync.onMessage(id, SyncMessages.encodeVideos(true, v.toList()))

    private fun self(vararg v: SyncMessages.VideoEntry) { port.local = SyncMessages.VideosMsg(true, v.toList()); sync.scanSelf() }

    private fun job(fid: String, state: JobState, done: Long = 0, size: Long = 100, err: String? = null, title: String = "Captain Comet") =
        SyncMessages.JobEntry(fid, title, state.wire, done, size, err)

    private fun jobs(id: String, vararg j: SyncMessages.JobEntry) = t.onMessage(id, SyncMessages.encodeJobs(j.toList()))

    @Test
    fun `this tablet is the source when it has the movie`() {
        self(star)
        t.sendNow("f1", setOf(H, L))
        assertTrue(port.sent.none { it.second.optString("c") == "serve" })
        for (id in listOf(H, L)) {
            val f = SyncMessages.decodeFetchCmd(port.cmds(id, "fetch").single())!!
            assertEquals("http://192.168.1.2:47471/v/f1?k=selfkey", f.url)
            assertEquals(3_000_000_000, f.size)
            assertEquals("Star Harbour", f.title)
            assertEquals("Star Harbour.mp4", f.name)
        }
        assertEquals(listOf("Sending Star Harbour to Hailey and Leo"), port.said)
    }

    @Test
    fun `tablets that already have it, are offline or are too old are skipped`() {
        self(star)
        has(H, star)
        port.online -= L
        port.add("old", "Zed's Tablet", capable = false)
        t.sendNow("f1", setOf(H, L, M, "old"))
        assertEquals(listOf(M), port.sent.filter { it.second.optString("c") == "fetch" }.map { it.first })
        assertEquals("Sending Star Harbour to Mia", port.said.single())
        assertTrue(port.sent.none { it.first == "old" })
    }

    @Test
    fun `not on WiFi means nothing is sent and the parent is told`() {
        self(star)
        port.lan = null
        assertEquals("This tablet isn't on WiFi", t.sendBlocked("f1"))
        t.sendNow("f1", setOf(H))
        assertTrue(port.sent.isEmpty())
        assertEquals(listOf("This tablet isn't on WiFi"), port.said)
    }

    @Test
    fun `two tablets lacking a movie only another tablet has get one serve and two fetches`() {
        has(H, comet)
        t.sendNow("f2", setOf(L, M))
        assertEquals(1, port.cmds(H, "serve").size)
        assertEquals("f2", port.cmds(H, "serve").single().getString("fid"))
        assertTrue(port.cmds(L, "fetch").isEmpty())
        t.sendNow("f2", setOf(M)) // tapped again while waiting: still one serve
        assertEquals(1, port.cmds(H, "serve").size)

        t.onMessage(H, SyncMessages.encodeServing("f2", true, 47471, "k3y", 900_000_000))
        for (id in listOf(L, M)) {
            val f = SyncMessages.decodeFetchCmd(port.cmds(id, "fetch").single())!!
            assertEquals("the url uses the address this Master reaches Hailey at", "http://192.168.1.20:47471/v/f2?k=k3y", f.url)
            assertEquals(900_000_000, f.size)
        }
    }

    @Test
    fun `cancel before the source is serving prevents transfer and sync start`() {
        has(H, comet)
        t.sendNow("f2", setOf(L))
        assertEquals(1, port.cmds(H, "serve").size)
        t.cancel("f2", L)
        t.onMessage(H, SyncMessages.encodeServing("f2", true, 47471, "k3y", 900_000_000))
        jobs(L, job("f2", JobState.WAITING, err = "source"))
        assertEquals(1, port.cmds(L, "cancel").size)
        assertTrue(port.cmds(L, "fetch").isEmpty())
        assertTrue(port.cmds(L, "sync").isEmpty())
        assertNull(sync.session.value)
        assertEquals("cancelled copies are not restarted by job reports", 1, port.cmds(H, "serve").size)
    }

    @Test
    fun `cancel during serving dispatch skips that target and preserves other targets`() {
        has(H, comet)
        t.sendNow("f2", setOf(L, M))
        port.onSend = { id, message ->
            if (id == L && message.optString("c") == "fetch") t.cancel("f2", M)
        }
        t.onMessage(H, SyncMessages.encodeServing("f2", true, 47471, "k3y", 900_000_000))
        assertEquals(1, port.cmds(L, "fetch").size)
        assertEquals(1, port.cmds(M, "cancel").size)
        assertTrue(port.cmds(M, "fetch").isEmpty())
        assertTrue(port.cmds(M, "sync").isEmpty())
        assertNull(sync.session.value)
    }

    @Test
    fun `a serving answer nobody asked for sends nothing`() {
        has(H, comet)
        t.onMessage(H, SyncMessages.encodeServing("f2", true, 47471, "k3y", 900_000_000))
        t.onMessage(L, SyncMessages.encodeServing("f2", true, 47471, "k3y", 900_000_000))
        assertTrue(port.sent.isEmpty())
    }

    @Test
    fun `a serving answer cannot point tablets somewhere else`() {
        has(H, comet)
        t.sendNow("f2", setOf(L))
        t.onMessage(H, SyncMessages.encodeServing("f2", true, 47471, "x@evil.example/", 900_000_000))
        assertTrue(port.cmds(L, "fetch").isEmpty())
        t.sendNow("f2", setOf(L))
        t.onMessage(H, SyncMessages.encodeServing("f2", true, 0, "k3y", 900_000_000))
        assertTrue(port.cmds(L, "fetch").isEmpty())
    }

    @Test
    fun `a source that lost the movie is reported`() {
        has(H, comet)
        t.sendNow("f2", setOf(L))
        port.said.clear()
        t.onMessage(H, SyncMessages.encodeServing("f2", false, err = "missing"))
        assertTrue(port.cmds(L, "fetch").isEmpty())
        assertEquals(listOf("Hailey can't find Captain Comet any more"), port.said)
    }

    @Test
    fun `a source that never answers is given up on after five seconds`() {
        has(H, comet)
        t.sendNow("f2", setOf(L))
        port.said.clear()
        now += 5000; t.tick()
        assertTrue(port.said.isEmpty())
        now += 1; t.tick()
        assertEquals(listOf("Hailey didn't answer. Try sending Captain Comet again."), port.said)
        t.onMessage(H, SyncMessages.encodeServing("f2", true, 47471, "k3y", 900_000_000))
        assertTrue("a late answer is ignored", port.cmds(L, "fetch").isEmpty())
    }

    @Test
    fun `no online tablet has it`() {
        has(H, comet)
        port.online -= H
        assertEquals("No tablet that has it is online", t.sendBlocked("f2"))
        t.sendNow("f2", setOf(L))
        assertTrue(port.sent.isEmpty())
        assertEquals(listOf("No tablet that has it is online"), port.said)
        port.online += H
        assertNull(t.sendBlocked("f2"))
        assertEquals("No tablet that has it is online", t.sendBlocked("unknown"))
    }

    @Test
    fun `job reports feed the screens`() {
        self(comet)
        has(H, comet)
        jobs(L, job("f2", JobState.RUNNING, 42), job("f9", JobState.QUEUED), job("f8", JobState.DONE, 100))
        jobs(M, job("f2", JobState.FAILED, err = "nospace:5"))
        assertEquals(42, t.jobFor(L, "f2")!!.done)
        assertNull("finished jobs are not shown as jobs", t.jobFor(L, "f8"))
        assertNull(t.jobFor(H, "f2"))
        assertEquals(2, t.downloading(L))
        assertEquals(0, t.downloading(M))
        assertEquals("f2", t.receiving(L)!!.fid)
        assertNull(t.receiving(M))
        assertEquals(listOf("Leo", "Mia"), t.cardJobs("f2").map { it.first })
        assertEquals("Mia: not enough space", TogetherText.jobLine(t.cardJobs("f2")))
        assertEquals("Leo has a copy on the way, Mia's failed so she can be sent it again", setOf(M), t.lacking("f2"))
    }

    @Test
    fun `the parent is told when a copy this Master started arrives, once`() {
        self(comet)
        t.sendNow("f2", setOf(L))
        port.said.clear()
        jobs(L, job("f2", JobState.RUNNING, 50))
        assertTrue(port.said.isEmpty())
        jobs(L, job("f2", JobState.DONE, 100))
        jobs(L, job("f2", JobState.DONE, 100))
        assertEquals(listOf("Leo has Captain Comet now"), port.said)
        jobs(M, job("f2", JobState.DONE, 100)) // an old finished job seen on reconnect
        assertEquals(1, port.said.size)
    }

    @Test
    fun `failures are not toasted`() {
        self(comet)
        t.sendNow("f2", setOf(L))
        port.said.clear()
        jobs(L, job("f2", JobState.FAILED, err = "mismatch"))
        assertTrue(port.said.isEmpty())
    }

    @Test
    fun `a job whose source went away is pointed at another tablet, not more than every 15 s`() {
        has(H, comet)
        has(M, comet)
        t.sendNow("f2", setOf(L))
        t.onMessage(H, SyncMessages.encodeServing("f2", true, 47471, "k1", 900_000_000))
        assertEquals(1, port.cmds(L, "fetch").size)
        port.online -= H
        port.said.clear()
        jobs(L, job("f2", JobState.WAITING, 10, err = "source"))
        assertEquals(1, port.cmds(M, "serve").size)
        jobs(L, job("f2", JobState.WAITING, 10, err = "source"))
        assertEquals("no second serve while the first is pending", 1, port.cmds(M, "serve").size)
        t.onMessage(M, SyncMessages.encodeServing("f2", true, 47999, "k2", 900_000_000))
        assertEquals("http://192.168.1.22:47999/v/f2?k=k2", SyncMessages.decodeFetchCmd(port.cmds(L, "fetch").last())!!.url)
        jobs(L, job("f2", JobState.WAITING, 10, err = "source"))
        assertEquals(1, port.cmds(M, "serve").size)
        now += 15_000
        jobs(L, job("f2", JobState.WAITING, 10, err = "source"))
        assertEquals(2, port.cmds(M, "serve").size)
        assertTrue("re-pointing a copy is silent", port.said.isEmpty())
    }

    @Test
    fun `a tablet's report alone never makes the Master ask another tablet to serve`() {
        has(H, comet)
        self(star)
        jobs(L, job("f2", JobState.WAITING, 10, err = "source"), job("f1", JobState.WAITING, 0, err = "source", title = "Star Harbour"))
        assertTrue(port.sent.isEmpty())
        assertTrue(port.said.isEmpty())
    }

    @Test
    fun `a very long jobs list is cut`() {
        t.onMessage(L, SyncMessages.encodeJobs(List(500) { job("f$it", JobState.QUEUED) }))
        assertEquals(50, t.jobs.value[L]!!.size)
    }

    @Test
    fun `a tablet is asked for its jobs once it is seen to be capable, and an old one never`() {
        port.add("old", "Zed's Tablet", capable = false)
        t.onConnected(L)
        t.onConnected("old")
        t.tick(); t.tick()
        assertEquals(1, port.cmds(L, "jobs").size)
        assertTrue(port.sent.none { it.first == "old" })
    }

    @Test
    fun `an offline tablet's jobs are dropped`() {
        jobs(L, job("f2", JobState.RUNNING, 42))
        t.onDisconnected(L)
        assertNull(t.jobs.value[L])
        assertEquals(0, t.downloading(L))
    }

    @Test
    fun `cancel and retry`() {
        self(comet)
        t.cancel("f2", L)
        assertEquals("f2", port.cmds(L, "cancel").single().getString("fid"))
    }

    private fun deleted(id: String, fid: String, ok: Boolean, err: String? = null) =
        t.onMessage(id, SyncMessages.encodeDeleted(fid, ok, err, if (ok) 1 else 0, 1_000L))

    @Test
    fun `delete sends the command and shows Deleting until the answer`() {
        has(H, star)
        t.delete("f1", H)
        assertEquals("f1", port.cmds(H, "delete").single().getString("fid"))
        assertTrue(t.deleting(H, "f1"))
        t.delete("f1", H) // a second tap while waiting sends nothing more
        assertEquals(1, port.cmds(H, "delete").size)
    }

    @Test
    fun `delete sends nothing to an offline tablet or one without del1`() {
        has(H, star)
        port.online -= H
        t.delete("f1", H)
        port.add("old", "Zed's Tablet", canDelete = false)
        t.delete("f1", "old")
        assertTrue(port.sent.isEmpty())
        assertTrue(!t.deleting(H, "f1") && !t.deleting("old", "f1"))
    }

    @Test
    fun `a good answer says Deleted, clears the wait and drops the movie from the grid at once`() {
        has(H, star, comet)
        t.delete("f1", H)
        deleted(H, "f1", true)
        assertEquals(listOf("Deleted Star Harbour from Hailey"), port.said)
        assertTrue(!t.deleting(H, "f1"))
        assertEquals(listOf("f2"), sync.videos.value[H]!!.list.map { it.fid })
    }

    @Test
    fun `an answer that arrives before send returns still clears the wait`() {
        has(H, star)
        port.onSend = { id, _ -> deleted(id, "f1", true) }
        t.delete("f1", H)
        assertTrue(!t.deleting(H, "f1"))
        assertEquals(listOf("Deleted Star Harbour from Hailey"), port.said)
    }

    @Test
    fun `the open movie is gone from the pick panel once the delete succeeds`() {
        has(H, star, comet)
        val open = { com.nbaltas.nickos.ui.master.openItemFor(Library.ownedBy(sync.library(), H), "f1") }
        assertEquals("f1", open()!!.fid)
        t.delete("f1", H)
        deleted(H, "f1", true)
        assertNull(open())
    }

    @Test
    fun `a refused delete says the movie was copied by hand and keeps it in the grid`() {
        has(H, star)
        t.delete("f1", H)
        deleted(H, "f1", false, "notOwned")
        assertEquals(
            listOf("NickOS can't delete Star Harbour from here. It was copied onto the tablet by hand, so it has to be deleted there."),
            port.said,
        )
        assertEquals(listOf("f1"), sync.videos.value[H]!!.list.map { it.fid })
    }

    @Test
    fun `no answer in ten seconds says so`() {
        has(H, star)
        t.delete("f1", H)
        t.tick(now + 9_999)
        assertTrue(port.said.isEmpty())
        t.tick(now + 10_001)
        assertEquals(listOf("Hailey didn't answer. Try again."), port.said)
        assertTrue(!t.deleting(H, "f1"))
    }

    @Test
    fun `each tablet is given this master's address on its own link`() {
        self(star)
        port.lanFor[H] = "192.168.49.1"            // Hailey is on the car network, Leo on the home WiFi
        t.sendNow("f1", setOf(H, L))
        assertEquals("http://192.168.49.1:47471/v/f1?k=selfkey", SyncMessages.decodeFetchCmd(port.cmds(H, "fetch").single())!!.url)
        assertEquals("http://192.168.1.2:47471/v/f1?k=selfkey", SyncMessages.decodeFetchCmd(port.cmds(L, "fetch").single())!!.url)
    }

    @Test
    fun `a tablet with no usable address is skipped while the others still get the movie`() {
        self(star)
        port.noUrlFor += H
        t.sendNow("f1", setOf(H, L))
        assertTrue(port.cmds(H, "fetch").isEmpty())
        assertEquals(1, port.cmds(L, "fetch").size)
    }

    @Test
    fun `a movie that left the library cannot be sent`() {
        t.sendNow("gone", setOf(L))
        assertTrue(port.sent.isEmpty())
        assertEquals(listOf("That movie isn't on any tablet any more"), port.said)
    }
}
