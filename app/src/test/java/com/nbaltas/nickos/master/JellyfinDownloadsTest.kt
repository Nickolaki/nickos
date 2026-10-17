package com.nbaltas.nickos.master

import com.nbaltas.nickos.jellyfin.JfItem
import com.nbaltas.nickos.jellyfin.Jellyfin
import com.nbaltas.nickos.node.JobFile
import com.nbaltas.nickos.node.JobIo
import com.nbaltas.nickos.sync.Library
import com.nbaltas.nickos.sync.LibraryItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JellyfinDownloadsTest {
    @get:Rule val tmp = TemporaryFolder()

    private val data = ByteArray(300_000).also { Random(5).nextBytes(it) }
    private lateinit var root: File
    private val scope = CoroutineScope(Dispatchers.IO)

    private fun item(
        id: String = "m1", name: String = "Heat", type: String = "Movie", year: Int? = 1995, container: String? = "mkv", path: String? = null,
        series: String? = null, season: Int? = null, index: Int? = null, size: Long = data.size.toLong(),
    ) = JfItem(
        id, name, type, null, series, null, index, season, 0, 0, false, null, year, null, null, false, false, false, null, null,
        container = container, path = path, size = size,
    )

    // ---- fakes ----

    inner class FakeIo : JobIo {
        var space = Long.MAX_VALUE
        var created = 0
        val discarded = ArrayList<String>()
        val published = ArrayList<String>()
        val folders = ArrayList<String?>()
        override fun free() = space
        override fun has(fid: String) = false
        override fun create(name: String): String? = create(name, null)
        override fun create(name: String, sub: String?): String? {
            created++; folders += sub
            val d = if (sub == null) root else File(root, sub).apply { mkdirs() }
            return File(d, "$name.part").apply { createNewFile() }.path
        }
        override fun open(dest: String): JobFile? = File(dest).takeIf { it.exists() }?.let { RandomAccessFile(it, "rw").channel.let { ch -> JobFile(ch, ch) } }
        override fun publish(dest: String, name: String) = File(dest).renameTo(File(File(dest).parentFile, name)).also { if (it) published += name }
        override fun discard(dest: String) { discarded += dest; File(dest).delete() }
        override fun awake(on: Boolean) {}
    }

    /** Serves [data]. [breakAt]: the first read past this many bytes throws once. [ranged]: honours Range. */
    inner class FakeSource : DownloadSource {
        var ranged = true
        var breakAt = -1
        var failWith: IOException? = null
        var code = 200
        val opens = ArrayList<Pair<String, Long>>()
        @Volatile var active = 0
        @Volatile var most = 0
        var onRead: (String) -> Unit = {}

        override fun open(item: JfItem, from: Long): SourceReply {
            opens += item.id to from
            failWith?.let { throw it }
            if (code != 200) return SourceReply(code, null, 0)
            val start = if (ranged) from.toInt() else 0
            val slice = data.copyOfRange(start, data.size)
            val raw = ByteArrayInputStream(slice)
            var served = 0
            var broke = false
            val stream = object : InputStream() {
                override fun read() = raw.read()
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    onRead(item.id)
                    if (breakAt >= 0 && !broke && start + served >= breakAt) { broke = true; breakAt = -1; throw IOException("reset") }
                    val n = raw.read(b, off, minOf(len, 8192))
                    if (n > 0) served += n
                    return n
                }
            }
            active++; most = maxOf(most, active)
            return SourceReply(if (ranged && from > 0) 206 else 200, stream, slice.size.toLong()) { active-- }
        }
    }

    private val src = FakeSource()
    private val io = FakeIo()
    private val said = ArrayList<String>()
    private var libraryNow: List<LibraryItem> = emptyList()
    private val sent = ArrayList<Pair<String, Set<String>>>()
    private var rescans = 0

    @Before fun setUp() { root = tmp.newFolder("NickOS") }
    @After fun tearDown() { scope.coroutineContext[kotlinx.coroutines.Job]?.cancel() }

    private fun queue() = JellyfinDownloads(
        io, src, scope, rescan = { rescans++ }, library = { libraryNow }, send = { f, t -> sent += f to t },
        say = { said += it }, waitStepMs = 5, waitSteps = 40, retries = 2,
    )

    private fun waitFor(what: String, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + 8000
        while (!cond()) { if (System.currentTimeMillis() > end) throw AssertionError("timed out waiting for $what"); Thread.sleep(5) }
    }

    private fun JellyfinDownloads.state(id: String) = progress.value[id]?.state

    // ---- names ----

    @Test fun movieName() {
        assertEquals("Heat (1995).mkv", Jellyfin.fileName(item()))
        assertNull(Jellyfin.subfolder(item()))
    }

    @Test fun movieWithoutYearOrContainerUsesPathThenMp4() {
        assertEquals("Heat.avi", Jellyfin.fileName(item(year = null, container = null, path = "/media/films/Heat.AVI")))
        assertEquals("Heat (1995).mp4", Jellyfin.fileName(item(container = null, path = null)))
        assertEquals("Heat (1995).mp4", Jellyfin.fileName(item(container = null, path = "/media/films/Heat")))
    }

    @Test fun episodeNameAndFolder() {
        val e = item(id = "e1", name = "Pilot", type = "Episode", series = "Bluey", season = 2, index = 5, year = null)
        assertEquals("S02E05 - Pilot.mkv", Jellyfin.fileName(e))
        assertEquals("Bluey", Jellyfin.subfolder(e))
    }

    @Test fun illegalCharactersAreStripped() {
        assertEquals("Who Am I 1 (2000).mkv", Jellyfin.fileName(item(name = "Who: Am I? <1>", year = 2000)))
        assertEquals("ABC (1995).mkv", Jellyfin.fileName(item(name = "A/B\\C")))
        val e = item(name = "What?", type = "Episode", series = "Is: This/That", season = 1, index = 1)
        assertEquals("S01E01 - What.mkv", Jellyfin.fileName(e))
        assertEquals("Is ThisThat", Jellyfin.subfolder(e))
    }

    // ---- the queue ----

    @Test fun savesAMovieAndPublishesIt() {
        val q = queue()
        q.save(item())
        waitFor("done") { q.state("m1") == DlState.DONE }
        assertEquals(listOf("Heat (1995).mkv"), io.published)
        assertArrayEquals(data, File(root, "Heat (1995).mkv").readBytes())
        assertEquals(1, rescans)
        assertTrue(q.progress.value.getValue("m1").fraction == 1f)
        assertTrue(sent.isEmpty())
    }

    @Test fun episodesGoToTheShowFolder() {
        val q = queue()
        q.save(item(id = "e1", name = "Pilot", type = "Episode", series = "Bluey", season = 1, index = 1))
        waitFor("done") { q.state("e1") == DlState.DONE }
        assertEquals(listOf<String?>("Bluey"), io.folders)
        assertTrue(File(root, "Bluey/S01E01 - Pilot.mkv").exists())
    }

    @Test fun oneAtATime() {
        val q = queue()
        q.save(item("a", "One")); q.save(item("b", "Two")); q.save(item("c", "Three"))
        waitFor("all done") { listOf("a", "b", "c").all { q.state(it) == DlState.DONE } }
        assertEquals(1, src.most)
        assertEquals(listOf("a", "b", "c"), src.opens.map { it.first })
    }

    @Test fun savingTheSameItemTwiceQueuesOnce() {
        val q = queue()
        var gate = true
        src.onRead = { if (gate) Thread.sleep(2) }
        q.save(item()); q.save(item())
        waitFor("done") { q.state("m1") == DlState.DONE }
        gate = false
        assertEquals(1, src.opens.size)
    }

    @Test fun cancelWhileDownloadingRemovesThePartialFile() {
        val q = queue()
        var reads = 0
        src.onRead = { if (++reads == 3) q.cancel("m1") }
        q.save(item())
        waitFor("cancelled") { q.state("m1") == DlState.CANCELLED }
        assertEquals(1, io.discarded.size)
        assertTrue(root.walkTopDown().none { it.isFile })
        assertTrue(io.published.isEmpty())
        assertEquals(0, rescans)
    }

    @Test fun cancelWhileQueuedNeverStarts() {
        val q = queue()
        var reads = 0
        src.onRead = { if (++reads == 2) q.cancel("b") }
        q.save(item("a", "One")); q.save(item("b", "Two"))
        waitFor("a done") { q.state("a") == DlState.DONE }
        assertEquals(DlState.CANCELLED, q.state("b"))
        assertEquals(listOf("a"), src.opens.map { it.first })
    }

    @Test fun failureCleansUpAndSaysWhy() {
        src.failWith = IOException("no route")
        val q = queue()
        q.save(item())
        waitFor("failed") { q.state("m1") == DlState.FAILED }
        assertEquals("Lost the connection to Jellyfin", q.progress.value.getValue("m1").err)
        assertEquals(1, io.discarded.size)
        assertTrue(root.walkTopDown().none { it.isFile })
        assertTrue(said.isNotEmpty())
        assertEquals(3, src.opens.size) // first try plus two retries
    }

    @Test fun serverRefusalFailsAtOnce() {
        src.code = 403
        val q = queue()
        q.save(item())
        waitFor("failed") { q.state("m1") == DlState.FAILED }
        assertEquals("Jellyfin would not allow the download", q.progress.value.getValue("m1").err)
        assertEquals(1, src.opens.size)
        assertEquals(1, io.discarded.size)
    }

    @Test fun failedItemCanBeTriedAgain() {
        src.code = 404
        val q = queue()
        q.save(item())
        waitFor("failed") { q.state("m1") == DlState.FAILED }
        src.code = 200
        q.save(item())
        waitFor("done") { q.state("m1") == DlState.DONE }
    }

    @Test fun notEnoughSpaceFailsBeforeAnyFileIsMade() {
        io.space = data.size + JellyfinDownloads.SPARE - 1
        val q = queue()
        q.save(item())
        waitFor("failed") { q.state("m1") == DlState.FAILED }
        assertEquals("Not enough space on this tablet", q.progress.value.getValue("m1").err)
        assertEquals(0, io.created)
        assertTrue(src.opens.isEmpty())
    }

    @Test fun exactlyEnoughSpaceIsFine() {
        io.space = data.size + JellyfinDownloads.SPARE
        val q = queue()
        q.save(item())
        waitFor("done") { q.state("m1") == DlState.DONE }
    }

    @Test fun resumesWithARangeWhenTheServerAllows() {
        src.breakAt = 100_000
        val q = queue()
        q.save(item())
        waitFor("done") { q.state("m1") == DlState.DONE }
        assertEquals(2, src.opens.size)
        assertTrue(src.opens[1].second > 0)
        assertArrayEquals(data, File(root, "Heat (1995).mkv").readBytes())
    }

    @Test fun startsOverWhenTheServerIgnoresTheRange() {
        src.ranged = false
        src.breakAt = 100_000
        val q = queue()
        q.save(item())
        waitFor("done") { q.state("m1") == DlState.DONE }
        assertEquals(2, src.opens.size)
        assertArrayEquals(data, File(root, "Heat (1995).mkv").readBytes())
    }

    @Test fun saveAllSkipsWhatIsAlreadyOnTheTablet() {
        val a = item("a", "One", type = "Episode", series = "S", season = 1, index = 1)
        val b = item("b", "Two", type = "Episode", series = "S", season = 1, index = 2)
        libraryNow = listOf(LibraryItem("f1", "S", Jellyfin.fileName(a), 1, 1, setOf(Library.SELF), null))
        val q = queue()
        q.saveAll(listOf(a, b))
        waitFor("b done") { q.state("b") == DlState.DONE }
        assertNull(q.state("a"))
    }

    // ---- hand-off to the kids ----

    @Test fun sendsOnceTheCopyShowsUpInTheLibrary() {
        val q = queue()
        var looks = 0
        val name = "Heat (1995).mkv"
        val appears = LibraryItem("fid9", "Heat", name, data.size.toLong(), 1, setOf(Library.SELF), null)
        val lib = JellyfinDownloads(
            io, src, scope, rescan = { rescans++ }, library = { if (++looks > 3) listOf(appears) else emptyList() },
            send = { f, t -> sent += f to t }, say = { said += it }, waitStepMs = 5, waitSteps = 40,
        )
        lib.save(item(), setOf("kid1", "kid2"))
        waitFor("sent") { sent.isNotEmpty() }
        assertEquals(listOf("fid9" to setOf("kid1", "kid2")), sent)
        assertEquals(DlState.DONE, lib.state("m1"))
        assertEquals(1, rescans)
        assertTrue(q.progress.value.isEmpty())
    }

    @Test fun neverSendsACopyThatDidNotLand() {
        src.code = 500
        val lib = JellyfinDownloads(io, src, scope, rescan = {}, library = { emptyList() }, send = { f, t -> sent += f to t }, say = { said += it }, waitStepMs = 5, waitSteps = 3)
        lib.save(item(), setOf("kid1"))
        waitFor("failed") { lib.state("m1") == DlState.FAILED }
        assertTrue(sent.isEmpty())
    }

    @Test fun saysSoWhenTheCopyNeverAppears() {
        val lib = JellyfinDownloads(io, src, scope, rescan = {}, library = { emptyList() }, send = { f, t -> sent += f to t }, say = { said += it }, waitStepMs = 2, waitSteps = 3)
        lib.save(item(), setOf("kid1"))
        waitFor("said") { said.isNotEmpty() }
        assertTrue(sent.isEmpty())
        assertFalse(said.first().isEmpty())
    }

    @Test fun onTabletMatchesByFileNameOnThisTabletOnly() {
        val m = item()
        val name = Jellyfin.fileName(m)
        assertTrue(JellyfinDownloads.onTablet(m, listOf(LibraryItem("f", "Heat", name, 1, 1, setOf(Library.SELF), null))))
        assertFalse(JellyfinDownloads.onTablet(m, listOf(LibraryItem("f", "Heat", name, 1, 1, setOf("kid1"), null))))
        assertFalse(JellyfinDownloads.onTablet(m, emptyList()))
    }
}
