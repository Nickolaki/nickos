package com.nbaltas.nickos.master

import android.util.Log
import com.nbaltas.nickos.jellyfin.JfItem
import com.nbaltas.nickos.jellyfin.Jellyfin
import com.nbaltas.nickos.node.JobIo
import com.nbaltas.nickos.sync.Library
import com.nbaltas.nickos.sync.LibraryItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer

enum class DlState { QUEUED, DOWNLOADING, DONE, FAILED, CANCELLED }

/** [total] is 0 when the server did not say how big the file is. [fid] is the saved copy's library id, known once it is DONE and found. */
data class Progress(val bytes: Long, val total: Long, val state: DlState, val err: String? = null, val fid: String? = null) {
    val fraction get() = if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else 0f
    val active get() = state == DlState.QUEUED || state == DlState.DOWNLOADING
}

/** One answer from the server: [code] 206 means it honoured the Range, 200 means the whole file. [range] is its Content-Range header. */
class SourceReply(val code: Int, val body: InputStream?, val length: Long, val range: String? = null, private val onClose: () -> Unit = {}) : Closeable {
    override fun close() {
        try { body?.close() } catch (_: IOException) {}
        try { onClose() } catch (_: Exception) {}
    }
}

/** A way to stop a download that is blocked on the network. The source registers what to call; [trigger] calls it, now or as soon as it is registered. */
class Abort {
    @Volatile private var fn: (() -> Unit)? = null
    @Volatile private var done = false
    fun set(f: () -> Unit) { fn = f; if (done) try { f() } catch (_: Exception) {} }
    fun trigger() { done = true; try { fn?.invoke() } catch (_: Exception) {} }
}

/** Where the bytes come from, so the queue is tested without a server. Blocking. */
fun interface DownloadSource {
    fun open(item: JfItem, from: Long, abort: Abort): SourceReply
}

/** The real thing: Jellyfin's /Download with the static stream as fallback. */
class JellyfinSource(private val jf: () -> Jellyfin?) : DownloadSource {
    override fun open(item: JfItem, from: Long, abort: Abort): SourceReply {
        val j = jf() ?: throw IOException("Jellyfin is not connected")
        val r = j.openDownload(item, from) { call -> abort.set { call.cancel() } }
        val body = r.body
        if (!r.isSuccessful || body == null) {
            val code = r.code
            r.close()
            return SourceReply(code, null, 0)
        }
        return SourceReply(r.code, body.byteStream(), body.contentLength(), r.header("Content-Range")) { r.close() }
    }
}

/** Destinations this app made and has not finished, kept across restarts so a killed download can be cleaned up. */
interface PendingLedger {
    fun all(): Set<String>
    fun add(dest: String)
    fun remove(dest: String)
}

/**
 * Saves Jellyfin movies and episodes onto this tablet (Movies/NickOS, episodes in a folder named
 * for the show), one at a time, then optionally sends the copy on to the kids' tablets. It runs in
 * the Master's own scope, so a download carries on when the screen changes. A copy that drops its
 * connection resumes with a Range request when the server answers with the matching Content-Range,
 * and starts over when not. A partial file never stays behind.
 */
class JellyfinDownloads(
    private val io: JobIo,
    private val source: DownloadSource,
    private val scope: CoroutineScope,
    private val rescan: () -> Unit,
    private val library: () -> List<LibraryItem>,
    private val send: (fid: String, targets: Set<String>) -> Unit,
    private val say: (String) -> Unit = {},
    private val waitStepMs: Long = 500,
    private val waitSteps: Int = 60,
    private val retries: Int = 3,
    private val ledger: PendingLedger? = null,
) {
    private class Entry(val item: JfItem, val sendTo: MutableSet<String>, val sub: String?) {
        @Volatile var cancelled = false
        val abort = Abort()
    }

    /** Bytes written so far and the size expected, shared by the attempts of one download. */
    private class Tally(var bytes: Long, var total: Long)

    private enum class Kind { DONE, AGAIN, CANCEL, FAIL }
    private class Step(val kind: Kind, val why: String? = null)
    private class OutOfSpace : RuntimeException("out of space")

    private val lock = Any()
    private val queue = ArrayDeque<Entry>()
    private var current: Entry? = null
    private var running = false

    private val _progress = MutableStateFlow<Map<String, Progress>>(emptyMap())
    /** Progress by Jellyfin item id. An id is absent until it is first queued. */
    val progress: StateFlow<Map<String, Progress>> = _progress

    /** Queue [item] to be saved. When it is already queued or running, only adds [sendTo] to it. [sendTo] are tablet ids to send it to when it lands. */
    fun save(item: JfItem, sendTo: Set<String> = emptySet()) {
        synchronized(lock) {
            if (_progress.value[item.id]?.active == true) {
                val live = current?.takeIf { it.item.id == item.id } ?: queue.firstOrNull { it.item.id == item.id }
                live?.sendTo?.addAll(sendTo)
                return
            }
            queue.addLast(Entry(item, sendTo.toMutableSet(), Jellyfin.subfolder(item)))
            set(item.id, Progress(0, item.size, DlState.QUEUED))
            if (!running) { running = true; scope.launch { drain() } }
        }
    }

    /**
     * Queue the episodes of a season that are not on this tablet, not saved a moment ago and not
     * already queued. At most [MAX_SEASON] at a time.
     */
    fun saveAll(items: List<JfItem>, sendTo: Set<String> = emptySet()) {
        val have = library()
        val now = _progress.value
        val todo = items.filterNot { e ->
            onTablet(e, have) || now[e.id]?.let { it.active || it.state == DlState.DONE } == true
        }
        if (todo.size > MAX_SEASON) say("Saving the first $MAX_SEASON of ${todo.size} episodes. Save the season again for the rest.")
        todo.take(MAX_SEASON).forEach { save(it, sendTo) }
    }

    fun cancel(itemId: String) {
        val live: Entry?
        synchronized(lock) {
            val cur = current
            if (cur != null && cur.item.id == itemId) { cur.cancelled = true; live = cur }
            else {
                live = null
                val q = queue.firstOrNull { it.item.id == itemId } ?: return
                queue.remove(q)
                set(itemId, Progress(0, q.item.size, DlState.CANCELLED))
            }
        }
        live?.abort?.trigger()
    }

    /** Forget a finished, failed or cancelled entry so its buttons come back. */
    fun clear(itemId: String) {
        _progress.update { m -> if (m[itemId]?.active == true) m else m - itemId }
    }

    /** At app start: remove copies a killed download left half made. Does nothing while a download runs. */
    fun cleanLeftovers() {
        val l = ledger ?: return
        synchronized(lock) { if (running) return }
        val mine = l.all()
        if (mine.isEmpty()) return
        io.dropPending(mine)
        mine.forEach { l.remove(it) }
    }

    private fun set(id: String, p: Progress) { _progress.update { it + (id to p) } }

    private suspend fun drain() {
        while (true) {
            val e = synchronized(lock) {
                val n = queue.removeFirstOrNull()
                if (n == null) running = false
                current = n
                n
            } ?: return
            try { run(e) } catch (x: Exception) {
                Log.w(TAG, "download ${e.item.id}: ${x.message}")
                set(e.item.id, Progress(0, e.item.size, DlState.FAILED, "Download failed"))
            } finally {
                synchronized(lock) { current = null }
            }
        }
    }

    private suspend fun run(e: Entry) {
        val id = e.item.id
        val known = e.item.size
        fun failed(why: String, bytes: Long = 0) = Progress(bytes, known, DlState.FAILED, why)
        val cancelled = Progress(0, known, DlState.CANCELLED)
        if (e.cancelled) { set(id, cancelled); return }
        if (known > 0 && io.free() < known + SPARE) {
            set(id, failed("Not enough space on this tablet")); say("${Jellyfin.fileName(e.item)}: Not enough space on this tablet")
            return
        }
        // A file of the same name that is not this one stays as it is: this copy gets " (2)".
        val have = library().filter { Library.SELF in it.owners }
        val wanted = Jellyfin.fileName(e.item)
        if (known > 0 && have.any { it.name == wanted && it.size == known }) {
            set(id, Progress(known, known, DlState.DONE))
            finish(e, wanted, known)
            return
        }
        val name = uniqueName(wanted, have)
        val dest = io.create(name, e.sub)
        if (dest == null) { set(id, failed("Can't save on this tablet")); say("$name: Can't save on this tablet"); return }
        ledger?.add(dest)
        io.awake(true)
        val t = Tally(0, known)
        var published = false
        var end: Progress? = null
        try {
            var tries = 0
            var restarts = 0
            while (true) {
                if (e.cancelled) { end = cancelled; break }
                set(id, Progress(t.bytes, t.total, DlState.DOWNLOADING))
                var step: Step
                try {
                    step = attempt(e, dest, t)
                } catch (x: OutOfSpace) {
                    step = Step(Kind.FAIL, "Not enough space on this tablet")
                } catch (x: IOException) {
                    if (e.cancelled) step = Step(Kind.CANCEL)
                    else if (++tries > retries) step = Step(Kind.FAIL, "Lost the connection to Jellyfin")
                    else {
                        Log.w(TAG, "download $id retry $tries at ${t.bytes}: ${x.message}")
                        delay(waitStepMs)
                        continue
                    }
                }
                when (step.kind) {
                    Kind.DONE -> break
                    Kind.AGAIN -> if (++restarts > MAX_RESTARTS) { end = failed("Jellyfin would not resume the download", t.bytes); break }
                    Kind.CANCEL -> { end = cancelled; break }
                    Kind.FAIL -> { end = failed(step.why ?: "Download failed", t.bytes); break }
                }
            }
            if (end == null) {
                if (e.cancelled) end = cancelled
                else if (!io.publish(dest, name)) end = failed("Can't save on this tablet", t.bytes)
                else { published = true; end = Progress(t.bytes, if (t.total > 0) t.total else t.bytes, DlState.DONE) }
            }
        } finally {
            // The streams are closed by now (attempt closes them), so the file can go.
            if (!published) try { io.discard(dest) } catch (x: Exception) { Log.d(TAG, "discard: ${x.message}") }
            ledger?.remove(dest)
            io.awake(false)
        }
        val fin = checkNotNull(end)
        set(id, fin)
        if (fin.state == DlState.FAILED) say("$name: ${fin.err}")
        if (fin.state == DlState.DONE) finish(e, name, t.bytes)
    }

    /** One connection: opens the file and the answer, copies until the end. Closes both before it returns. */
    private fun attempt(e: Entry, dest: String, t: Tally): Step {
        val id = e.item.id
        val known = e.item.size
        val file = io.open(dest) ?: return Step(Kind.FAIL, "Can't save on this tablet")
        file.use {
            val r = source.open(e.item, t.bytes, e.abort)
            r.use {
                if (e.cancelled) return Step(Kind.CANCEL)
                fun startOver(): Step { file.out.truncate(0); t.bytes = 0; return Step(Kind.AGAIN) }
                if (r.code == 416 && t.bytes > 0) return startOver()
                val body = r.body
                if (body == null || r.code !in 200..299) {
                    val why = if (r.code == 401 || r.code == 403) "Jellyfin would not allow the download" else "Jellyfin answered ${r.code}"
                    return Step(Kind.FAIL, why)
                }
                if (r.code == 206) {
                    val cr = parseRange(r.range)
                    if (cr == null || cr.start != t.bytes) return startOver()
                    t.total = cr.total ?: known.takeIf { it > 0 } ?: (cr.end + 1)
                } else {
                    // The whole file again: start over on this same answer.
                    if (t.bytes > 0) { file.out.truncate(0); t.bytes = 0 }
                    t.total = if (r.length > 0) r.length else known
                }
                if (t.total > 0 && io.free() < (t.total - t.bytes) + SPARE) return Step(Kind.FAIL, "Not enough space on this tablet")
                file.out.position(t.bytes)
                val buf = ByteArray(BUFFER)
                var lastShown = t.bytes
                while (true) {
                    if (e.cancelled) return Step(Kind.CANCEL)
                    val n = body.read(buf)
                    if (n < 0) break
                    val bb = ByteBuffer.wrap(buf, 0, n)
                    try {
                        while (bb.hasRemaining()) file.out.write(bb)
                    } catch (x: IOException) {
                        if (noSpace(x)) throw OutOfSpace()
                        throw x
                    }
                    t.bytes += n
                    if (t.bytes - lastShown >= SHOW_EVERY) { lastShown = t.bytes; set(id, Progress(t.bytes, t.total, DlState.DOWNLOADING)) }
                }
                if (t.total > 0 && t.bytes > t.total) return startOver()
                if (t.total > 0 && t.bytes < t.total) throw IOException("Connection closed early")
            }
        }
        return Step(Kind.DONE)
    }

    /**
     * The copy is in the library: look again, and hand it to the kids when asked. Sends only the one
     * file with exactly this name and size, and only when there is exactly one.
     */
    private suspend fun finish(e: Entry, name: String, bytes: Long) {
        rescan()
        // Nobody to send to: look for the copy's fid beside the queue, so the next download is not held up.
        if (e.sendTo.isEmpty()) scope.launch { resolve(e, name, bytes) } else resolve(e, name, bytes)
    }

    private suspend fun resolve(e: Entry, name: String, bytes: Long) {
        val id = e.item.id
        repeat(waitSteps) {
            val hits = library().filter { Library.SELF in it.owners && it.name == name && it.size == bytes }
            if (hits.size == 1) {
                val fid = hits[0].fid
                _progress.update { m -> m[id]?.let { m + (id to it.copy(fid = fid)) } ?: m }
                val to = synchronized(lock) { e.sendTo.toSet() }
                if (to.isNotEmpty()) send(fid, to)
                return
            }
            if (hits.size > 1) { if (e.sendTo.isNotEmpty()) say("$name is saved. Send it from Watch together"); return }
            delay(waitStepMs)
        }
        if (e.sendTo.isNotEmpty()) say("$name is saved. Send it from Watch together")
    }

    companion object {
        private const val TAG = "NickOS.JfDownload"
        const val BUFFER = 8 * 1024
        private const val SHOW_EVERY = 256 * 1024L
        private const val MAX_RESTARTS = 3
        /** Always leave this much free after a copy. */
        const val SPARE = 200L * 1024 * 1024
        /** Most episodes one "Save season" queues. */
        const val MAX_SEASON = 100

        /** "bytes 100-299/300": [total] is null for "*". */
        class ContentRange(val start: Long, val end: Long, val total: Long?)

        fun parseRange(h: String?): ContentRange? {
            val m = Regex("""^\s*bytes\s+(\d+)-(\d+)/(\d+|\*)\s*$""", RegexOption.IGNORE_CASE).find(h ?: return null) ?: return null
            val start = m.groupValues[1].toLongOrNull() ?: return null
            val end = m.groupValues[2].toLongOrNull() ?: return null
            val total = m.groupValues[3].toLongOrNull()
            if (end < start || (total != null && end >= total)) return null
            return ContentRange(start, end, total)
        }

        private fun noSpace(x: IOException): Boolean {
            val m = x.message.orEmpty()
            return m.contains("ENOSPC") || m.contains("No space left", ignoreCase = true)
        }

        /** [name] when nothing on this tablet has it, else "Name (2).ext", "Name (3).ext"... */
        fun uniqueName(name: String, mine: List<LibraryItem>): String {
            val taken = mine.filter { Library.SELF in it.owners }.mapTo(HashSet()) { it.name }
            if (name !in taken) return name
            val base = name.substringBeforeLast('.', name)
            val ext = if ('.' in name) "." + name.substringAfterLast('.') else ""
            var n = 2
            while ("$base ($n)$ext" in taken) n++
            return "$base ($n)$ext"
        }

        /** This item's saved copy is in this tablet's library: same name, and the same size when Jellyfin told us it. */
        fun onTablet(item: JfItem, lib: List<LibraryItem>): Boolean = saved(item, lib) != null

        /** The library entry for [item] on any tablet: by [fid] when the download found it, else by name and size. */
        fun inLibrary(item: JfItem, lib: List<LibraryItem>, fid: String? = null): LibraryItem? {
            if (fid != null) lib.firstOrNull { it.fid == fid }?.let { return it }
            val name = Jellyfin.fileName(item)
            return lib.firstOrNull { it.name == name && (item.size <= 0 || it.size == item.size) }
        }

        /** The library entry of this item's saved copy on this tablet, for the Send button. */
        fun saved(item: JfItem, lib: List<LibraryItem>): LibraryItem? {
            val name = Jellyfin.fileName(item)
            return lib.firstOrNull { Library.SELF in it.owners && it.name == name && (item.size <= 0 || it.size == item.size) }
        }
    }
}
