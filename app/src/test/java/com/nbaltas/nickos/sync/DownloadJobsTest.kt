package com.nbaltas.nickos.sync

import android.content.Context
import android.content.SharedPreferences
import com.nbaltas.nickos.link.MediaServer
import com.nbaltas.nickos.link.Served
import com.nbaltas.nickos.node.DownloadJob
import com.nbaltas.nickos.node.DownloadJobs
import com.nbaltas.nickos.node.JobFile
import com.nbaltas.nickos.node.JobIo
import com.nbaltas.nickos.node.LocalVideos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.util.Random

/** The real runner against a real [MediaServer] on localhost, with plain files standing in for MediaStore. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DownloadJobsTest {
    private val mib = 1024 * 1024
    private val bytes = ByteArray(3 * mib).also { Random(11).nextBytes(it) }
    private val fid = VideoId.fingerprint(bytes.size.toLong(), bytes.copyOfRange(0, mib), bytes.copyOfRange(2 * mib, 3 * mib))
    private lateinit var dir: File
    private lateinit var source: File
    private lateinit var server: MediaServer
    private lateinit var prefs: SharedPreferences
    private val io = FileIo()
    private var now = 50_000L
    private val reports = ArrayList<JSONObject>()
    private var arrived = 0
    /** Range header of each request the runner made, "" when it sent none. */
    private val ranges = ArrayList<String>()
    private var stripRange = false
    private var onRequest: () -> Unit = {}
    private val http = OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor(Interceptor { chain ->
        ranges += chain.request().header("Range") ?: ""
        onRequest()
        chain.proceed(if (stripRange) chain.request().newBuilder().removeHeader("Range").build() else chain.request())
    }).build()

    inner class FileIo : JobIo {
        var space = Long.MAX_VALUE
        val library = HashSet<String>()
        var created = 0
        var awake = 0
        override fun free() = space
        override fun has(fid: String) = fid in library
        override fun create(name: String): String? { created++; return File(dir, "$name.part").apply { createNewFile() }.path }
        override fun open(dest: String): JobFile? = File(dest).takeIf { it.exists() }?.let { RandomAccessFile(it, "rw").channel.let { ch -> JobFile(ch, ch) } }
        override fun publish(dest: String, name: String) = File(dest).renameTo(File(dir, name))
        override fun discard(dest: String) { File(dest).delete() }
        override fun awake(on: Boolean) { if (on) awake++ else awake = 0 }
    }

    @Before
    fun setUp() {
        dir = File.createTempFile("nickos", "jobs").apply { delete(); mkdirs() }
        source = File.createTempFile("nickos", ".src").apply { writeBytes(bytes) }
        server = MediaServer(0, allowPeer = { true }) { _ -> Served(source.length(), "video/mp4") { FileInputStream(source) } }
        assertTrue(server.start())
        prefs = RuntimeEnvironment.getApplication().getSharedPreferences("jobs-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @After
    fun tearDown() {
        server.stop()
        source.delete()
        dir.deleteRecursively()
    }

    private fun jobs(allowHost: (String) -> Boolean = { true }) = DownloadJobs(
        io, prefs, CoroutineScope(Dispatchers.IO), report = { reports += it }, onDone = { arrived++ },
        allowHost = allowHost, clock = { now }, http = http, auto = false,
    )

    private fun fetch(id: String = fid, size: Long = bytes.size.toLong(), name: String = "Star Harbour.mp4", url: String = server.url("127.0.0.1", id)) =
        SyncMessages.FetchCmdMsg(id, url, size, "Star Harbour", name)

    private fun states() = reports.mapNotNull { SyncMessages.decodeJobs(it)?.list?.firstOrNull()?.state }.fold(ArrayList<String>()) { a, s -> if (a.lastOrNull() != s) a += s; a }

    private fun partials() = dir.listFiles()!!.filter { it.name.endsWith(".part") }

    @Test
    fun `a movie is copied, checked and published`() {
        val j = jobs()
        j.fetch(fetch())
        assertEquals(JobState.QUEUED, j.list().single().state)
        j.drain()
        val job = j.list().single()
        assertEquals(JobState.DONE, job.state)
        assertEquals(bytes.size.toLong(), job.done)
        assertNull(job.err)
        assertArrayEquals(bytes, File(dir, "Star Harbour.mp4").readBytes())
        assertTrue(partials().isEmpty())
        assertEquals(1, arrived)
        assertEquals(listOf("queued", "running", "verifying", "done"), states())
        assertEquals(listOf(""), ranges)
        assertEquals("the locks are let go", 0, io.awake)
    }

    @Test
    fun `the copy has the same fingerprint as its source, so the library merges them`() {
        val j = jobs()
        j.fetch(fetch())
        j.drain()
        RandomAccessFile(File(dir, "Star Harbour.mp4"), "r").channel.use { assertEquals(fid, LocalVideos.fingerprint(it, it.size())) }
    }

    @Test
    fun `a restart resumes by range from the bytes on disk`() {
        val part = File(dir, "Star Harbour.mp4.part").apply { writeBytes(bytes.copyOfRange(0, mib + 5000)) }
        val saved = DownloadJob(fid, "Star Harbour", "Star Harbour.mp4", server.url("127.0.0.1", fid), bytes.size.toLong(), JobState.RUNNING, done = mib.toLong(), dest = part.path)
        prefs.edit().putString("list", JSONArray().put(saved.toJson()).toString()).commit()
        val j = jobs()
        j.start()
        assertEquals(JobState.QUEUED, j.list().single().state)
        j.drain()
        assertEquals(JobState.DONE, j.list().single().state)
        assertEquals(listOf("bytes=$mib-"), ranges)
        assertEquals(0, io.created)
        assertArrayEquals(bytes, File(dir, "Star Harbour.mp4").readBytes())
    }

    @Test
    fun `a whole-file answer to a resume starts again from zero instead of appending`() {
        val part = File(dir, "Star Harbour.mp4.part").apply { writeBytes(ByteArray(mib) { 7 }) }
        val saved = DownloadJob(fid, "Star Harbour", "Star Harbour.mp4", server.url("127.0.0.1", fid), bytes.size.toLong(), JobState.WAITING, done = mib.toLong(), dest = part.path)
        prefs.edit().putString("list", JSONArray().put(saved.toJson()).toString()).commit()
        stripRange = true
        val j = jobs()
        j.start()
        j.drain()
        assertEquals(JobState.DONE, j.list().single().state)
        assertArrayEquals(bytes, File(dir, "Star Harbour.mp4").readBytes())
    }

    @Test
    fun `a half-copied file that vanished starts over`() {
        val saved = DownloadJob(fid, "Star Harbour", "Star Harbour.mp4", server.url("127.0.0.1", fid), bytes.size.toLong(), JobState.WAITING, done = mib.toLong(), dest = File(dir, "gone.part").path)
        prefs.edit().putString("list", JSONArray().put(saved.toJson()).toString()).commit()
        val j = jobs()
        j.start()
        j.drain()
        assertEquals(JobState.WAITING, j.list().single().state)
        assertEquals(0, j.list().single().done)
        now += 5000
        j.drain()
        assertEquals(JobState.DONE, j.list().single().state)
        assertEquals(listOf(""), ranges)
    }

    @Test
    fun `a size that disagrees with the server fails at once and writes nothing`() {
        val j = jobs()
        j.fetch(fetch(size = bytes.size + 1L))
        j.drain()
        val job = j.list().single()
        assertEquals(JobState.FAILED, job.state)
        assertEquals("mismatch", job.err)
        assertEquals(0, job.done)
        assertTrue("no ghost file", dir.listFiles()!!.isEmpty())
        now += 600_000
        j.drain()
        assertEquals("no retry", 1, ranges.size)
    }

    @Test
    fun `a copy whose fingerprint is not the one asked for is deleted`() {
        val other = "0123456789abcdef"
        val j = jobs()
        j.fetch(fetch(id = other))
        j.drain()
        assertEquals(JobState.FAILED, j.list().single().state)
        assertEquals("mismatch", j.list().single().err)
        assertTrue(dir.listFiles()!!.isEmpty())
        assertEquals(0, arrived)
    }

    @Test
    fun `not enough space fails before anything is created and says how much there is`() {
        io.space = bytes.size + DownloadJobs.HEADROOM - 1
        val j = jobs()
        j.fetch(fetch())
        j.drain()
        assertEquals(JobState.FAILED, j.list().single().state)
        assertEquals("nospace:${io.space}", j.list().single().err)
        assertEquals(0, io.created)
        assertTrue(ranges.isEmpty())
        // "Try again" is a fresh fetch.
        io.space = Long.MAX_VALUE
        j.fetch(fetch())
        j.drain()
        assertEquals(JobState.DONE, j.list().single().state)
    }

    @Test
    fun `a movie the tablet already has is done without a download`() {
        io.library += fid
        val j = jobs()
        j.fetch(fetch())
        assertEquals(JobState.DONE, j.list().single().state)
        j.drain()
        assertTrue(ranges.isEmpty())
        assertEquals(0, io.created)
    }

    @Test
    fun `a source that is not answering waits with backoff, and a fresh fetch carries on at once`() {
        val dead = server.url("127.0.0.1", fid)
        server.stop()
        val j = jobs()
        j.fetch(fetch(url = dead))
        j.drain()
        var job = j.list().single()
        assertEquals(JobState.WAITING, job.state)
        assertEquals(1, job.tries)
        assertEquals(now + 5000, job.nextAt)
        assertNull(job.err)
        j.drain()
        assertEquals("not before its time", 1, ranges.size)
        now += 5000
        j.drain()
        job = j.list().single()
        assertEquals(2, job.tries)
        assertEquals(now + 10_000, job.nextAt)

        assertTrue(server.start())
        j.fetch(fetch())
        assertEquals(1, j.list().size)
        assertEquals(0, j.list().single().tries)
        j.drain()
        assertEquals(JobState.DONE, j.list().single().state)
    }

    @Test
    fun `thirty failures in a row tell the Master to find another source, and it keeps trying`() {
        val dead = server.url("127.0.0.1", fid)
        server.stop()
        val j = jobs()
        j.fetch(fetch(url = dead))
        repeat(29) { j.drain(); now += 60_000 }
        assertNull(j.list().single().err)
        j.drain()
        assertEquals("source", j.list().single().err)
        assertEquals(JobState.WAITING, j.list().single().state)
        assertEquals(30, j.list().single().tries)
    }

    @Test
    fun `a key the source no longer accepts is flagged straight away`() {
        val j = jobs()
        j.fetch(fetch(url = "http://127.0.0.1:${server.port}/v/$fid?k=stale"))
        j.drain()
        assertEquals(JobState.WAITING, j.list().single().state)
        assertEquals("source", j.list().single().err)
    }

    @Test
    fun `cancel removes a queued job's partial file`() {
        val part = File(dir, "Star Harbour.mp4.part").apply { writeBytes(ByteArray(100)) }
        val saved = DownloadJob(fid, "Star Harbour", "Star Harbour.mp4", server.url("127.0.0.1", fid), bytes.size.toLong(), JobState.WAITING, done = 100, dest = part.path)
        prefs.edit().putString("list", JSONArray().put(saved.toJson()).toString()).commit()
        val j = jobs()
        j.start()
        j.cancel(fid)
        assertEquals(JobState.CANCELLED, j.list().single().state)
        assertFalse(part.exists())
        j.drain()
        assertTrue(ranges.isEmpty())
    }

    @Test
    fun `cancel during a copy stops it and leaves no file`() {
        val j = jobs()
        onRequest = { j.cancel(fid) }
        j.fetch(fetch())
        j.drain()
        assertEquals(JobState.CANCELLED, j.list().single().state)
        assertTrue(dir.listFiles()!!.isEmpty())
        assertEquals(0, arrived)
        // And it can be sent again afterwards.
        onRequest = {}
        j.fetch(fetch())
        j.drain()
        assertEquals(JobState.DONE, j.list().single().state)
    }

    @Test
    fun `a second fetch for a movie on its way only changes where it comes from`() {
        val j = jobs()
        j.fetch(fetch(url = "http://127.0.0.1:1/v/$fid?k=x"))
        j.fetch(fetch())
        assertEquals(1, j.list().size)
        assertEquals(server.url("127.0.0.1", fid), j.list().single().url)
        j.drain()
        assertEquals(JobState.DONE, j.list().single().state)
        assertEquals(1, io.created)
    }

    @Test
    fun `jobs run one at a time, oldest first`() {
        val two = ByteArray(mib) { 3 }
        val fid2 = VideoId.fingerprint(two.size.toLong(), two, ByteArray(0))
        val src2 = File.createTempFile("nickos", ".src2").apply { writeBytes(two) }
        server.stop()
        server = MediaServer(0, allowPeer = { true }) { f -> (if (f == fid) source else src2).let { s -> Served(s.length(), "video/mp4") { FileInputStream(s) } } }
        assertTrue(server.start())
        val j = jobs()
        j.fetch(fetch())
        j.fetch(fetch(id = fid2, size = two.size.toLong(), name = "Captain Comet.mkv"))
        j.drain()
        assertEquals(listOf(JobState.DONE, JobState.DONE), j.list().map { it.state })
        assertArrayEquals(two, File(dir, "Captain Comet.mkv").readBytes())
        assertEquals(2, arrived)
        src2.delete()
    }

    @Test
    fun `only another tablet on the home network is ever fetched from`() {
        val j = jobs(allowHost = { it.startsWith("192.168.") })
        j.fetch(fetch())
        j.drain()
        assertEquals(JobState.FAILED, j.list().single().state)
        assertEquals("source", j.list().single().err)
        for (bad in listOf("https://192.168.1.5/v/$fid?k=x", "http://192.168.1.5/other/$fid", "http://192.168.1.5/v/${fid}x", "file:///sdcard/x", "not a url")) {
            j.fetch(fetch(url = bad))
            j.drain()
            assertEquals(bad, "source", j.list().single().err)
        }
        assertTrue("nothing was requested", ranges.isEmpty())
    }

    @Test
    fun `finished jobs stay for a minute so the Master sees them, then go`() {
        val j = jobs()
        j.fetch(fetch())
        j.drain()
        now += DownloadJobs.KEEP_MS
        assertFalse(j.purge())
        now += 1
        assertTrue(j.purge())
        assertTrue(j.list().isEmpty())
        assertTrue(SyncMessages.decodeJobs(j.message())!!.list.isEmpty())
    }

    @Test
    fun `the queue survives a restart`() {
        server.stop()
        val a = jobs()
        a.fetch(fetch(url = "http://127.0.0.1:1/v/$fid?k=x"))
        a.drain()
        assertEquals(JobState.WAITING, a.list().single().state)
        val b = jobs()
        b.start()
        assertEquals(JobState.QUEUED, b.list().single().state)
        assertEquals("Star Harbour", b.list().single().title)
    }

    @Test
    fun `file names are made safe`() {
        assertEquals("Star_Harbour.mp4", DownloadJobs.cleanName("Star/Harbour.mp4"))
        assertEquals("_.._etc_passwd", DownloadJobs.cleanName("/../etc/passwd"))
        assertEquals("hidden.mp4", DownloadJobs.cleanName(".hidden.mp4"))
        assertEquals("video", DownloadJobs.cleanName(".."))
        val long = DownloadJobs.cleanName("a".repeat(300) + ".mkv")
        assertTrue(long.length <= 120 && long.endsWith(".mkv"))
        assertTrue(listOf("../x", "..\\x", "a/b").map { DownloadJobs.cleanName(it) }.none { '/' in it || '\\' in it })
    }

    @Test
    fun `a nonsense fetch is ignored`() {
        val j = jobs()
        j.fetch(fetch(size = 0))
        j.fetch(fetch(id = "../x"))
        j.fetch(fetch(id = ""))
        assertTrue(j.list().isEmpty())
    }
}
