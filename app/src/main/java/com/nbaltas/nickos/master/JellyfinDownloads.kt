package com.nbaltas.nickos.master

import android.util.Log
import com.nbaltas.nickos.jellyfin.JfItem
import com.nbaltas.nickos.jellyfin.Jellyfin
import com.nbaltas.nickos.node.JobFile
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

/** [total] is 0 when the server did not say how big the file is. */
data class Progress(val bytes: Long, val total: Long, val state: DlState, val err: String? = null) {
    val fraction get() = if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else 0f
    val active get() = state == DlState.QUEUED || state == DlState.DOWNLOADING
}

/** One answer from the server: [code] 206 means it honoured the Range, 200 means the whole file. */
class SourceReply(val code: Int, val body: InputStream?, val length: Long, private val onClose: () -> Unit = {}) : Closeable {
    override fun close() {
        try { body?.close() } catch (_: IOException) {}
        try { onClose() } catch (_: Exception) {}
    }
}

/** Where the bytes come from, so the queue is tested without a server. Blocking. */
fun interface DownloadSource {
    fun open(item: JfItem, from: Long): SourceReply
}

/** The real thing: Jellyfin's /Download with the static stream as fallback. */
class JellyfinSource(private val jf: () -> Jellyfin?) : DownloadSource {
    override fun open(item: JfItem, from: Long): SourceReply {
        val j = jf() ?: throw IOException("Jellyfin is not connected")
        val r = j.openDownload(item, from)
        val body = r.body
        if (!r.isSuccessful || body == null) {
            val code = r.code
            r.close()
            return SourceReply(code, null, 0)
        }
        return SourceReply(r.code, body.byteStream(), body.contentLength()) { r.close() }
    }
}

/**
 * Saves Jellyfin movies and episodes onto this tablet (Movies/NickOS, episodes in a folder named
 * for the show), one at a time, then optionally sends the copy on to the kids' tablets. It runs in
 * the Master's own scope, so a download carries on when the screen changes. A copy that drops its
 * connection resumes with a Range request when the server allows it, and starts over when not.
 * A partial file never stays behind.
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
    private val waitSteps: Int = 20,
    private val retries: Int = 3,
) {
    private class Entry(val item: JfItem, val sendTo: Set<String>, val name: String, val sub: String?) {
        @Volatile var cancelled = false
    }

    private val lock = Any()
    private val queue = ArrayDeque<Entry>()
    private var current: Entry? = null
    private var running = false

    private val _progress = MutableStateFlow<Map<String, Progress>>(emptyMap())
    /** Progress by Jellyfin item id. An id is absent until it is first queued. */
    val progress: StateFlow<Map<String, Progress>> = _progress

    /** Queue [item] to be saved. Does nothing when it is already queued or running. [sendTo] are tablet ids to send it to when it lands. */
    fun save(item: JfItem, sendTo: Set<String> = emptySet()) {
        synchronized(lock) {
            if (_progress.value[item.id]?.active == true) return
            queue.addLast(Entry(item, sendTo, Jellyfin.fileName(item), Jellyfin.subfolder(item)))
            set(item.id, Progress(0, item.size, DlState.QUEUED))
            if (!running) { running = true; scope.launch { drain() } }
        }
    }

    /** Queue every episode of a season, skipping ones already on this tablet. */
    fun saveAll(items: List<JfItem>, sendTo: Set<String> = emptySet()) {
        val have = library()
        items.filterNot { onTablet(it, have) }.forEach { save(it, sendTo) }
    }

    fun cancel(itemId: String) {
        synchronized(lock) {
            val cur = current
            if (cur != null && cur.item.id == itemId) { cur.cancelled = true; return }
            val q = queue.firstOrNull { it.item.id == itemId } ?: return
            queue.remove(q)
            set(itemId, Progress(0, q.item.size, DlState.CANCELLED))
        }
    }

    /** Forget a finished, failed or cancelled entry so its buttons come back. */
    fun clear(itemId: String) {
        _progress.update { m -> if (m[itemId]?.active == true) m else m - itemId }
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
                Log.w(TAG, "download ${e.name}: ${x.message}")
                set(e.item.id, Progress(0, e.item.size, DlState.FAILED, "Download failed"))
            } finally {
                synchronized(lock) { current = null }
            }
        }
    }

    private suspend fun run(e: Entry) {
        val id = e.item.id
        val known = e.item.size
        fun fail(why: String, dest: String? = null, bytes: Long = 0) {
            dest?.let { io.discard(it) }
            set(id, Progress(bytes, known, DlState.FAILED, why))
            say("${e.name}: $why")
        }
        fun stop(dest: String?) {
            dest?.let { io.discard(it) }
            set(id, Progress(0, known, DlState.CANCELLED))
        }
        if (e.cancelled) return stop(null)
        if (io.free() < known + SPARE) return fail("Not enough space on this tablet")
        val dest = io.create(e.name, e.sub) ?: return fail("Can't save on this tablet")
        io.awake(true)
        try {
            var bytes = 0L
            var total = known
            var tries = 0
            while (true) {
                if (e.cancelled) return stop(dest)
                set(id, Progress(bytes, total, DlState.DOWNLOADING))
                val file = io.open(dest) ?: return fail("Can't save on this tablet", dest)
                try {
                    file.use {
                        val r = source.open(e.item, bytes)
                        r.use {
                            val body = r.body
                            if (body == null || (r.code !in 200..299)) {
                                val why = if (r.code == 401 || r.code == 403) "Jellyfin would not allow the download" else "Jellyfin answered ${r.code}"
                                return fail(why, dest, bytes)
                            }
                            if (r.code != 206 && bytes > 0) {
                                // The server sent the whole file again: start over.
                                file.out.truncate(0); bytes = 0
                            }
                            val len = if (r.length > 0) r.length + bytes else 0L
                            if (len > 0) total = len
                            if (total > 0 && r.code != 206 && io.free() < total + SPARE) return fail("Not enough space on this tablet", dest)
                            file.out.position(bytes)
                            val buf = ByteArray(BUFFER)
                            var lastShown = 0L
                            while (true) {
                                if (e.cancelled) return stop(dest)
                                val n = body.read(buf)
                                if (n < 0) break
                                val bb = ByteBuffer.wrap(buf, 0, n)
                                while (bb.hasRemaining()) file.out.write(bb)
                                bytes += n
                                if (bytes - lastShown >= SHOW_EVERY) { lastShown = bytes; set(id, Progress(bytes, total, DlState.DOWNLOADING)) }
                            }
                            if (total > 0 && bytes < total) throw IOException("Connection closed early")
                        }
                    }
                    break
                } catch (x: IOException) {
                    if (e.cancelled) return stop(dest)
                    if (++tries > retries) return fail("Lost the connection to Jellyfin", dest, bytes)
                    Log.w(TAG, "download ${e.name} retry $tries at $bytes: ${x.message}")
                    delay(waitStepMs)
                }
            }
            if (e.cancelled) return stop(dest)
            if (!io.publish(dest, e.name)) return fail("Can't save on this tablet", dest, bytes)
            set(id, Progress(bytes, if (total > 0) total else bytes, DlState.DONE))
            finish(e, bytes)
        } finally {
            io.awake(false)
        }
    }

    /** The copy is in the library: look again, and hand it to the kids when asked. */
    private suspend fun finish(e: Entry, bytes: Long) {
        rescan()
        if (e.sendTo.isEmpty()) return
        repeat(waitSteps) {
            val fid = library().firstOrNull { Library.SELF in it.owners && it.name == e.name && it.size == bytes }?.fid
            if (fid != null) { send(fid, e.sendTo); return }
            delay(waitStepMs)
        }
        say("${e.name} is saved, but didn't show up in the library to send")
    }

    companion object {
        private const val TAG = "NickOS.JfDownload"
        const val BUFFER = 8 * 1024
        private const val SHOW_EVERY = 256 * 1024L
        /** Always leave this much free after a copy. */
        const val SPARE = 200L * 1024 * 1024

        /** This item's saved copy is in this tablet's library. */
        fun onTablet(item: JfItem, lib: List<LibraryItem>): Boolean {
            val name = Jellyfin.fileName(item)
            return lib.any { Library.SELF in it.owners && it.name == name }
        }

        /** The library entry of this item's saved copy on this tablet, for the Send button. */
        fun saved(item: JfItem, lib: List<LibraryItem>): LibraryItem? {
            val name = Jellyfin.fileName(item)
            return lib.firstOrNull { Library.SELF in it.owners && it.name == name }
        }
    }
}
