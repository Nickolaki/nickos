package com.nbaltas.nickos.node

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import com.nbaltas.nickos.data.optStr
import com.nbaltas.nickos.link.LanAddress
import com.nbaltas.nickos.link.WifiHold
import com.nbaltas.nickos.sync.DownloadPolicy
import com.nbaltas.nickos.sync.JobState
import com.nbaltas.nickos.sync.SyncMessages
import com.nbaltas.nickos.sync.VideoId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.Proxy
import java.net.UnknownHostException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One movie on its way to this tablet. [done] bytes are on disk at [dest] (a MediaStore uri, or a
 * file path before Android 10). [nextAt] is on the monotonic clock and only means something while
 * WAITING. [endedAt] is when it reached a final state; it leaves the list a minute later.
 */
data class DownloadJob(
    val fid: String,
    val title: String,
    val name: String,
    val url: String,
    val size: Long,
    val state: JobState = JobState.QUEUED,
    val done: Long = 0,
    val err: String? = null,
    val dest: String? = null,
    val tries: Int = 0,
    val nextAt: Long = 0,
    val endedAt: Long = 0,
) {
    fun toJson(): JSONObject = JSONObject().put("fid", fid).put("title", title).put("name", name).put("url", url).put("size", size)
        .put("state", state.wire).put("done", done).put("err", err).put("dest", dest).put("tries", tries)

    companion object {
        fun from(o: JSONObject): DownloadJob? = try {
            DownloadJob(
                o.getString("fid"), o.getString("title"), o.getString("name"), o.getString("url"), o.getLong("size"),
                JobState.of(o.getString("state")) ?: JobState.QUEUED, o.optLong("done"), o.optStr("err"), o.optStr("dest"), o.optInt("tries"),
            )
        } catch (e: Exception) { null }
    }
}

/** A destination file open for the copy: [out] to append, [inp] to read it back for the check. */
class JobFile(val out: FileChannel, val inp: FileChannel, private val onClose: () -> Unit = {}) : Closeable {
    override fun close() {
        try { out.close() } catch (_: IOException) {}
        try { inp.close() } catch (_: IOException) {}
        try { onClose() } catch (_: Exception) {}
    }
}

/** Everything a download touches on the tablet, so the runner itself is tested without a tablet. */
interface JobIo {
    /** Bytes free where movies are kept. */
    fun free(): Long
    /** This movie is already in the tablet's library. */
    fun has(fid: String): Boolean
    /** A new, empty, not yet visible destination for a file called [name]. Null when it cannot be made. */
    fun create(name: String): String?
    /** Same, inside folder [sub] of the library folder. Implementations without folders ignore it. */
    fun create(name: String, sub: String?): String? = create(name)
    /** Null when the destination is gone. */
    fun open(dest: String): JobFile?
    /** The copy is complete and checked: make it a normal file in the library. */
    fun publish(dest: String, name: String): Boolean
    /** Remove a partial copy, leaving no ghost file behind. */
    fun discard(dest: String)
    /** Keep the CPU and WiFi awake while a copy runs. Called again while running to renew. */
    fun awake(on: Boolean)
    /** Remove half-made copies this app left in the movie folder, but only those in [mine] (destinations it made and never finished). */
    fun dropPending(mine: Set<String>) {}
}

/**
 * This tablet's queue of movies being copied to it from another tablet (see `link/MediaServer`).
 * One copy at a time, oldest first. The queue is saved after every change, so a restart or a
 * reboot carries on from the bytes already on disk instead of starting again. A copy that loses
 * its connection waits and retries for as long as it takes; the parent sees "Waiting for WiFi".
 * A finished copy only counts once its size and fingerprint match what the Master asked for,
 * which is also what makes it "the same movie" for playing in step.
 *
 * Nothing here is shown on the kid's screen. Every method may be called from any thread except
 * the main thread ([report] writes to sockets).
 */
class DownloadJobs(
    private val io: JobIo,
    private val prefs: SharedPreferences,
    private val scope: CoroutineScope,
    /** Sends a `jobs` message to every connected Master. */
    private val report: (JSONObject) -> Unit,
    /** A movie arrived: the library should be scanned and sent again. */
    private val onDone: () -> Unit,
    /** Which hosts a movie may be fetched from. Other tablets on the home WiFi only. */
    private val allowHost: (String) -> Boolean = LanAddress::isSiteLocal,
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
    private val http: OkHttpClient = CLIENT,
    /** False in tests, which call [drain] and [purge] themselves instead of running the loops. */
    private val auto: Boolean = true,
) {
    private val lock = Any()
    private val jobs = ArrayList<DownloadJob>()
    private val cancelled = HashSet<String>()
    private val busy = AtomicBoolean(false)
    private var runner: Job? = null
    private var reporter: Job? = null
    private var liveFid: String? = null
    @Volatile private var live: Call? = null
    /** The destination the runner has open. Runner thread only. */
    private var liveFile: JobFile? = null
    @Volatile private var stopped = false

    /** The queue as it is now, oldest first. */
    fun list(): List<DownloadJob> = synchronized(lock) { jobs.toList() }

    /** The `jobs` message for a Master. */
    fun message(): JSONObject = SyncMessages.encodeJobs(list().map { SyncMessages.JobEntry(it.fid, it.title, it.state.wire, it.done, it.size, it.err) })

    /** Loads the saved queue and carries on. Called when the runtime starts. */
    fun start() {
        stopped = false
        synchronized(lock) {
            if (jobs.isEmpty()) {
                val now = clock()
                val saved = try { JSONArray(prefs.getString(KEY, "[]")) } catch (e: Exception) { JSONArray() }
                for (i in 0 until saved.length()) {
                    val j = saved.optJSONObject(i)?.let { DownloadJob.from(it) } ?: continue
                    jobs += when {
                        DownloadPolicy.isTerminal(j.state) -> j.copy(endedAt = now)
                        // Whatever it was doing when the app stopped, it starts again from the bytes on disk.
                        else -> j.copy(state = JobState.QUEUED, nextAt = 0, err = null)
                    }
                }
            }
        }
        kick()
    }

    /** The runtime is stopping. Jobs stay saved and resume on the next [start]. */
    fun stop() {
        stopped = true
        synchronized(lock) { runner?.cancel(); runner = null; reporter?.cancel(); reporter = null }
        live?.cancel()
    }

    /**
     * The Master asked for a movie. Already here: done at once. Already on its way: only the
     * address changes (and the retry count starts over), so a second Send never restarts a copy.
     */
    fun fetch(m: SyncMessages.FetchCmdMsg) {
        if (m.size <= 0 || m.fid.isEmpty() || !m.fid.all { it.isLetterOrDigit() }) return
        val updated = synchronized(lock) {
            if (jobs.size >= MAX_JOBS && jobs.none { it.fid == m.fid }) return // a queue this long is not a parent tapping Send
            val i = jobs.indexOfFirst { it.fid == m.fid && !DownloadPolicy.isTerminal(it.state) }
            if (i >= 0) { jobs[i] = jobs[i].copy(url = m.url, tries = DownloadPolicy.onFreshFetch(), nextAt = 0, err = null); save() }
            i >= 0
        }
        if (!updated) {
            val have = try { io.has(m.fid) } catch (e: Exception) { false }
            synchronized(lock) {
                if (jobs.any { it.fid == m.fid && !DownloadPolicy.isTerminal(it.state) }) return@synchronized
                jobs.removeAll { it.fid == m.fid }
                jobs += DownloadJob(
                    m.fid, m.title.take(200), cleanName(m.name), m.url, m.size,
                    state = if (have) JobState.DONE else JobState.QUEUED, done = if (have) m.size else 0, endedAt = if (have) clock() else 0,
                )
                save()
            }
        }
        tell()
        kick()
    }

    /** Stop a copy and remove what was copied so far. */
    fun cancel(fid: String) {
        var dest: String? = null
        synchronized(lock) {
            val i = jobs.indexOfFirst { it.fid == fid && !DownloadPolicy.isTerminal(it.state) }
            if (i < 0) return
            if (liveFid == fid) {
                // The runner has it: flag it and break its read. It tidies up on its way out.
                cancelled += fid
                live?.cancel()
                return
            }
            dest = jobs[i].dest
            jobs[i] = jobs[i].copy(state = DownloadPolicy.cancel(jobs[i].state), dest = null, err = null, endedAt = clock())
            save()
        }
        dest?.let { safeDiscard(it) }
        tell()
    }

    /** Drops jobs that finished, failed or were cancelled more than a minute ago. True when any went. */
    fun purge(): Boolean {
        val now = clock()
        return synchronized(lock) {
            jobs.removeAll { DownloadPolicy.isTerminal(it.state) && now - it.endedAt > KEEP_MS }.also { if (it) save() }
        }
    }

    /**
     * Runs every job that can run right now, one after another, and returns when none is left
     * (all finished, or waiting for their retry time). Blocking. Only one caller runs at a time.
     */
    fun drain(alive: () -> Boolean = { true }) {
        if (!busy.compareAndSet(false, true)) return
        try {
            while (alive() && !stopped) {
                val fid = synchronized(lock) {
                    val now = clock()
                    jobs.firstOrNull { it.state == JobState.QUEUED || (it.state == JobState.WAITING && it.nextAt <= now) }?.fid?.also { liveFid = it }
                } ?: return
                attempt(fid, alive)
            }
        } finally { busy.set(false) }
    }

    // ---- loops ----

    private fun kick() {
        if (!auto || stopped) return
        synchronized(lock) {
            if (jobs.isEmpty()) return
            if (reporter?.isActive != true) reporter = scope.launch(Dispatchers.IO) {
                while (isActive) {
                    delay(REPORT_MS)
                    val gone = purge()
                    val (any, empty) = synchronized(lock) {
                        val empty = jobs.isEmpty()
                        if (empty) reporter = null
                        jobs.any { !DownloadPolicy.isTerminal(it.state) } to empty
                    }
                    if (any || gone) tell()
                    if (empty) break
                }
            }
            if (runner?.isActive != true && jobs.any { !DownloadPolicy.isTerminal(it.state) }) runner = scope.launch(Dispatchers.IO) {
                while (isActive) {
                    drain { isActive }
                    val more = synchronized(lock) { jobs.any { !DownloadPolicy.isTerminal(it.state) }.also { if (!it) runner = null } }
                    if (!more) break
                    delay(POLL_MS)
                }
            }
        }
    }

    // ---- one attempt ----

    private class SourceGone(msg: String) : IOException(msg)

    private fun attempt(fid: String, alive: () -> Boolean) {
        var file: JobFile? = null
        var progressed = false
        io.awake(true)
        try {
            var j = change(fid) { it.copy(state = DownloadPolicy.onStart(it.state), err = null) } ?: return
            // Space is checked before every start and every resume, with room to spare.
            val free = io.free()
            if (free < j.size - j.done + HEADROOM) return fail(fid, "nospace:$free")
            if (sourceUrl(j.url, fid) == null) return fail(fid, "source")

            val fresh = j.dest == null
            val dest = j.dest ?: io.create(j.name) ?: return fail(fid, "io")
            if (fresh) j = change(fid, tell = false) { it.copy(dest = dest, done = 0) } ?: return
            file = io.open(dest)
            liveFile = file
            if (file == null) {
                if (fresh) return fail(fid, "io")
                // The half-copied file is gone (deleted by hand, or the pending row expired): start over.
                safeDiscard(dest)
                change(fid, tell = false) { it.copy(dest = null, done = 0) }
                throw IOException("destination gone")
            }
            var done = minOf(j.done, file.out.size())

            val req = Request.Builder().url(sourceUrl(j.url, fid) ?: return fail(fid, "source")).apply { if (done > 0) header("Range", "bytes=$done-") }.build()
            val call = http.newCall(req)
            live = call
            if (isCancelled(fid)) return
            call.execute().use { r ->
                if (r.code == 403 || r.code == 404 || r.code == 416) throw SourceGone("http ${r.code}")
                if (!r.isSuccessful) throw IOException("http ${r.code}")
                if (r.code == 206) {
                    val from = r.header("Content-Range")?.substringAfter("bytes ", "")?.substringBefore('-')?.toLongOrNull()
                    if (from != done) throw IOException("range answered from $from, wanted $done")
                } else if (done > 0) {
                    // A whole-file answer to a resume: appending it would corrupt the copy. Start from zero.
                    file.out.truncate(0)
                    done = 0
                }
                val body = r.body ?: throw IOException("no body")
                val length = body.contentLength()
                if (length >= 0 && length + done != j.size) return fail(fid, "mismatch")

                file.out.position(done)
                val buf = ByteArray(CHUNK)
                val src = body.byteStream()
                var shown = done
                var saved = done
                while (true) {
                    if (!alive() || stopped || isCancelled(fid)) return
                    val n = src.read(buf)
                    if (n < 0) break
                    if (done + n > j.size) return fail(fid, "mismatch")
                    try {
                        val bb = ByteBuffer.wrap(buf, 0, n)
                        while (bb.hasRemaining()) file.out.write(bb)
                    } catch (e: IOException) {
                        // The tablet filled up under us: say so, rather than retrying into a full disk.
                        val left = io.free()
                        if (left < LOW_SPACE) return fail(fid, "nospace:$left")
                        throw e
                    }
                    done += n
                    progressed = true
                    if (done - shown >= SHOW_EVERY) {
                        shown = done
                        val persist = done - saved >= SAVE_EVERY
                        if (persist) saved = done
                        change(fid, persist = persist, tell = false) { it.copy(done = shown) }
                        io.awake(true)
                    }
                }
            }
            change(fid, tell = false) { it.copy(done = done) }
            if (done < j.size) throw IOException("stream ended at $done of ${j.size}")

            change(fid) { it.copy(state = DownloadPolicy.onBytesComplete(done, j.size)) }
            file.out.force(true)
            val print = LocalVideos.fingerprint(file.inp, j.size)
            file.close()
            file = null
            if (DownloadPolicy.onVerify(print == fid) != JobState.DONE) return fail(fid, "mismatch")
            if (!io.publish(dest, j.name)) return fail(fid, "io")
            change(fid) { it.copy(state = JobState.DONE, dest = null, err = null, endedAt = clock()) }
            try { onDone() } catch (e: Exception) { Log.w(TAG, "onDone", e) }
        } catch (e: IOException) {
            Log.d(TAG, "job $fid: ${e.message}")
            if (!isCancelled(fid) && !stopped) retryLater(fid, progressed, e is SourceGone)
        } catch (e: Exception) {
            Log.w(TAG, "job $fid", e)
            fail(fid, "io")
        } finally {
            live = null
            file?.close()
            liveFile = null
            io.awake(false)
            val wasCancelled = synchronized(lock) { liveFid = null; cancelled.remove(fid) }
            if (wasCancelled) {
                val dest = synchronized(lock) { jobs.firstOrNull { it.fid == fid }?.dest }
                dest?.let { safeDiscard(it) }
                change(fid) { if (DownloadPolicy.isTerminal(it.state)) it else it.copy(state = JobState.CANCELLED, dest = null, err = null, endedAt = clock()) }
            }
        }
    }

    private fun retryLater(fid: String, progressed: Boolean, sourceGone: Boolean) {
        val now = clock()
        change(fid) {
            // Bytes arrived this time, so the link works: the count of failures in a row starts again.
            val tries = if (progressed) 1 else it.tries + 1
            it.copy(
                state = DownloadPolicy.onError(it.state), tries = tries, nextAt = now + DownloadPolicy.backoff(tries),
                err = if (sourceGone || DownloadPolicy.shouldFlagSource(tries)) "source" else null,
            )
        }
    }

    private fun fail(fid: String, err: String) {
        // Closed before it is removed: a file still open is not always gone when deleted.
        liveFile?.close()
        liveFile = null
        val dest = synchronized(lock) { jobs.firstOrNull { it.fid == fid }?.dest }
        dest?.let { safeDiscard(it) }
        change(fid) { it.copy(state = JobState.FAILED, err = err, dest = null, endedAt = clock()) }
    }

    private fun isCancelled(fid: String) = synchronized(lock) { fid in cancelled }

    private fun change(fid: String, persist: Boolean = true, tell: Boolean = true, f: (DownloadJob) -> DownloadJob): DownloadJob? {
        val out = synchronized(lock) {
            val i = jobs.indexOfFirst { it.fid == fid }
            if (i < 0) return null
            jobs[i] = f(jobs[i])
            if (persist) save()
            jobs[i]
        }
        if (tell) tell()
        return out
    }

    private fun tell() { try { report(message()) } catch (e: Exception) { Log.d(TAG, "report: ${e.message}") } }

    /** Hold [lock]. */
    private fun save() {
        val a = JSONArray()
        jobs.forEach { a.put(it.toJson()) }
        prefs.edit().putString(KEY, a.toString()).apply()
    }

    private fun safeDiscard(dest: String) { try { io.discard(dest) } catch (e: Exception) { Log.d(TAG, "discard: ${e.message}") } }

    /**
     * The address a movie is really fetched from, or null when [url] is not acceptable. Only plain
     * http to another tablet on the home network, by its numeric address, and only that movie's
     * `/v/<fid>?k=<key>`. The request is rebuilt from the checked parts, so what was checked is
     * exactly what is asked for: no user name, no fragment, no other query, no host name to look up.
     */
    private fun sourceUrl(url: String, fid: String): HttpUrl? {
        val u = url.toHttpUrlOrNull() ?: return null
        val key = u.queryParameter("k") ?: return null
        if (u.scheme != "http" || u.username.isNotEmpty() || u.password.isNotEmpty()) return null
        if (LanAddress.octets(u.host) == null || !allowHost(u.host)) return null
        if (u.encodedPath != "/v/$fid" || !key.all { it.isLetterOrDigit() } || key.length > 64) return null
        return HttpUrl.Builder().scheme("http").host(u.host).port(u.port).addPathSegment("v").addPathSegment(fid).addQueryParameter("k", key).build()
    }

    companion object {
        private const val TAG = "NickOS.Jobs"
        private const val KEY = "list"
        const val PREFS = "nickos.jobs"
        const val CHUNK = 256 * 1024
        const val SHOW_EVERY = 1024L * 1024
        const val SAVE_EVERY = 8L * 1024 * 1024
        /** Free space kept beyond the movie itself. */
        const val HEADROOM = 200L * 1024 * 1024
        private const val LOW_SPACE = 16L * 1024 * 1024
        const val KEEP_MS = 60_000L
        const val MAX_JOBS = 50
        const val REPORT_MS = 2000L
        private const val POLL_MS = 500L

        /** Connects to the numeric address in the url and nothing else: no name lookups at all. */
        private object NumericOnly : Dns {
            override fun lookup(hostname: String): List<InetAddress> =
                LanAddress.octets(hostname)?.let { listOf(InetAddress.getByAddress(it)) } ?: throw UnknownHostException(hostname)
        }

        /**
         * No redirects, no proxy, no name lookups: a movie comes from the address the Master gave,
         * or not at all.
         */
        private val CLIENT: OkHttpClient by lazy {
            OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
                .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
                .proxy(Proxy.NO_PROXY).dns(NumericOnly).build()
        }

        /** The only kinds of file a job will create. Anything else is saved as .mp4, where it is just a video that will not play. */
        private val VIDEO_EXT = setOf("mp4", "m4v", "mkv", "webm", "mov", "avi", "3gp", "ts", "mpg", "mpeg", "wmv", "flv")

        /** A file name safe to create: a video extension, no path characters, not hidden, not absurdly long. */
        fun cleanName(name: String): String {
            var n = VideoId.safeName(name).filter { it >= ' ' }.trimStart('.').trim().ifEmpty { "video" }
            val ext = n.substringAfterLast('.', "").lowercase()
            if ('.' !in n || ext !in VIDEO_EXT) n = "$n.mp4"
            val dot = n.lastIndexOf('.')
            return if (n.length <= 120) n else n.take(120 - (n.length - dot)) + n.substring(dot)
        }
    }
}

/** [JobIo] on a real tablet: MediaStore from Android 10, plain files in Movies/NickOS before it. */
class AndroidJobIo(private val ctx: Context, private val videos: LocalVideos, private val hold: WifiHold?) : JobIo {
    private val resolver get() = ctx.contentResolver
    private val wake: PowerManager.WakeLock? = try {
        (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "nickos:download").apply { setReferenceCounted(false) }
    } catch (e: Exception) { null }
    private var wifi = false

    @Suppress("DEPRECATION")
    private fun moviesDir() = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), LocalVideos.DOWNLOAD_DIR)

    override fun free(): Long = try {
        @Suppress("DEPRECATION")
        StatFs(Environment.getExternalStorageDirectory().path).availableBytes
    } catch (e: Exception) { Long.MAX_VALUE }

    override fun has(fid: String): Boolean = videos.find(fid) != null

    override fun create(name: String): String? = create(name, null)

    override fun create(name: String, sub: String?): String? = try {
        val folder = if (sub.isNullOrBlank()) "" else "/$sub"
        if (Build.VERSION.SDK_INT >= 29) {
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())?.takeIf { it.startsWith("video/") }
            fun values(m: String?) = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                if (m != null) put(MediaStore.Video.Media.MIME_TYPE, m)
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/${LocalVideos.DOWNLOAD_DIR}$folder")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = try { resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values(mime)) }
            catch (e: Exception) { resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values("video/mp4")) }
            uri?.toString()
        } else {
            val dir = (if (sub.isNullOrBlank()) moviesDir() else File(moviesDir(), sub)).apply { mkdirs() }
            File(dir, "$name.part").apply { delete(); createNewFile() }.absolutePath
        }
    } catch (e: Exception) { Log.w(TAG, "create $name: ${e.message}"); null }

    override fun open(dest: String): JobFile? = try {
        if (dest.startsWith("content:")) {
            resolver.openFileDescriptor(Uri.parse(dest), "rw")?.let { pfd ->
                JobFile(FileOutputStream(pfd.fileDescriptor).channel, FileInputStream(pfd.fileDescriptor).channel) { pfd.close() }
            }
        } else {
            File(dest).takeIf { it.exists() }?.let { f -> RandomAccessFile(f, "rw").channel.let { JobFile(it, it) } }
        }
    } catch (e: Exception) { Log.w(TAG, "open: ${e.message}"); null }

    override fun publish(dest: String, name: String): Boolean = try {
        if (dest.startsWith("content:")) {
            resolver.update(Uri.parse(dest), ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null) > 0
        } else {
            val dir = File(dest).parentFile ?: moviesDir()
            var target = File(dir, name)
            var n = 1
            while (target.exists()) target = File(dir, "${name.substringBeforeLast('.')} (${n++})" + name.substringAfterLast('.', "").let { if (it.isEmpty() || '.' !in name) "" else ".$it" })
            File(dest).renameTo(target).also { if (it) MediaScannerConnection.scanFile(ctx, arrayOf(target.absolutePath), null, null) }
        }
    } catch (e: Exception) { Log.w(TAG, "publish: ${e.message}"); false }

    override fun discard(dest: String) {
        if (dest.startsWith("content:")) resolver.delete(Uri.parse(dest), null, null) else File(dest).delete()
    }

    override fun dropPending(mine: Set<String>) {
        try {
            val owned = HashSet<String>()
            if (Build.VERSION.SDK_INT >= 29) {
                val q = Bundle().apply {
                    putString(ContentResolver.QUERY_ARG_SQL_SELECTION, "${MediaStore.Video.Media.IS_PENDING} = 1 AND ${MediaStore.Video.Media.OWNER_PACKAGE_NAME} = ? AND ${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?")
                    putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf(ctx.packageName, "${Environment.DIRECTORY_MOVIES}/${LocalVideos.DOWNLOAD_DIR}%"))
                    putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
                }
                resolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Video.Media._ID), q, null)?.use { c ->
                    while (c.moveToNext()) owned += ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, c.getLong(0)).toString()
                }
            }
            for (d in mine) {
                if (d.startsWith("content:")) { if (d in owned) discard(d) } else File(d).delete()
            }
        } catch (e: Exception) { Log.w(TAG, "dropPending: ${e.message}") }
    }

    @Synchronized
    override fun awake(on: Boolean) {
        try {
            if (on) {
                wake?.acquire(WAKE_MS)
                if (!wifi) { wifi = true; hold?.acquire() }
            } else {
                if (wake?.isHeld == true) wake.release()
                if (wifi) { wifi = false; hold?.release() }
            }
        } catch (e: Exception) { Log.d(TAG, "awake: ${e.message}") }
    }

    companion object {
        private const val TAG = "NickOS.Jobs"
        private const val WAKE_MS = 30 * 60_000L
    }
}
