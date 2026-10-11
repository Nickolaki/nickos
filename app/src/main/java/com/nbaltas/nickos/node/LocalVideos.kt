package com.nbaltas.nickos.node

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.util.Size
import com.nbaltas.nickos.sync.SyncMessages
import com.nbaltas.nickos.sync.VideoId
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.ConcurrentHashMap

/** One video file on this tablet's shared storage. [fid] is what "the same movie" means. */
data class LocalVideo(
    val id: Long,
    val uri: Uri,
    val fid: String,
    val title: String,
    val name: String,
    val size: Long,
    val durMs: Long,
    val mime: String,
)

/**
 * This tablet's movie library: ordinary video files in shared storage (Movies, Download, and
 * Movies/NickOS where download jobs land), read through MediaStore. Used by both runtimes. Every
 * method does disk work, so call from a background thread, never from the main thread.
 */
class LocalVideos(private val ctx: Context) {
    private val resolver get() = ctx.contentResolver
    private val fids = ctx.getSharedPreferences("nickos.fids", Context.MODE_PRIVATE)
    /** fid -> base64 JPEG, or "" when the file gave no thumbnail, so it is tried once only. */
    private val thumbs = ConcurrentHashMap<String, String>()
    @Volatile private var last: List<LocalVideo> = emptyList()

    /** Full video access. On Android 14 "selected photos only" does not count. */
    fun hasPermission(): Boolean {
        val p = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE
        return ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
    }

    /** The most recent [scan] result, without touching the disk. */
    fun cached(): List<LocalVideo> = last

    /** Up to [MAX_FILES] videos of at least [MIN_BYTES], sorted by file name. Empty without permission. */
    @Synchronized
    fun scan(): List<LocalVideo> {
        if (!hasPermission()) { last = emptyList(); return last }
        nudgeScanner()
        val out = ArrayList<LocalVideo>()
        val cols = buildList {
            add(MediaStore.Video.Media._ID); add(MediaStore.Video.Media.DISPLAY_NAME); add(MediaStore.Video.Media.SIZE)
            add(MediaStore.Video.Media.DURATION); add(MediaStore.Video.Media.DATE_MODIFIED); add(MediaStore.Video.Media.MIME_TYPE)
            if (Build.VERSION.SDK_INT >= 29) { add(MediaStore.Video.Media.RELATIVE_PATH); add(MediaStore.Video.Media.IS_PENDING) }
        }.toTypedArray()
        try {
            resolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cols,
                "${MediaStore.Video.Media.SIZE} >= ?", arrayOf(MIN_BYTES.toString()),
                "${MediaStore.Video.Media.DISPLAY_NAME} ASC",
            )?.use { c ->
                val pending = c.getColumnIndex("is_pending")
                while (c.moveToNext() && out.size < MAX_FILES) {
                    if (pending >= 0 && c.getInt(pending) == 1) continue // a download still in progress
                    val id = c.getLong(0)
                    val name = c.getString(1) ?: continue
                    val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                    val (fid, size) = identify(uri, "$id:${c.getLong(2)}:${c.getLong(4)}") ?: continue
                    out += LocalVideo(id, uri, fid, VideoId.title(name), name, size, c.getLong(3).coerceAtLeast(0), c.getString(5) ?: "video/mp4")
                }
            }
        } catch (e: Exception) { Log.w(TAG, "scan", e) }
        // Two copies of one movie (Movies and Download, say) are one library entry.
        last = out.distinctBy { it.fid }
        return last
    }

    /** The video with this [fid]; looks at the last scan first and rescans once on a miss. */
    fun find(fid: String, rescan: Boolean = true): LocalVideo? =
        last.firstOrNull { it.fid == fid } ?: if (rescan) scan().firstOrNull { it.fid == fid } else null

    fun open(v: LocalVideo): ParcelFileDescriptor? = try { resolver.openFileDescriptor(v.uri, "r") } catch (e: Exception) { null }

    /**
     * The `videos` reply for a Master, thumbnails included, kept under about 2 MB: when the
     * budget runs out, later files go without a thumbnail rather than being left out.
     */
    fun reply(): SyncMessages.VideosMsg {
        if (!hasPermission()) return SyncMessages.VideosMsg(false, emptyList())
        var budget = REPLY_BUDGET
        return SyncMessages.VideosMsg(true, scan().map { v ->
            val t = thumb(v)?.takeIf { it.length <= budget }
            if (t != null) budget -= t.length
            SyncMessages.VideoEntry(v.fid, v.title, v.name, v.size, v.durMs, t)
        })
    }

    /** Base64 JPEG (320 x 180 or so), or null when this file gives none. One bad file never throws. */
    fun thumb(v: LocalVideo): String? = thumbs.getOrPut(v.fid) {
        try {
            @Suppress("DEPRECATION")
            val bmp = if (Build.VERSION.SDK_INT >= 29) resolver.loadThumbnail(v.uri, Size(320, 180), null)
            else MediaStore.Video.Thumbnails.getThumbnail(resolver, v.id, MediaStore.Video.Thumbnails.MINI_KIND, null)
            if (bmp == null) "" else {
                val o = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.JPEG, 70, o)
                Base64.encodeToString(o.toByteArray(), Base64.NO_WRAP)
            }
        } catch (e: Exception) { "" }
    }.ifEmpty { null }

    /** fid and true size, cached per (id, size, modified) so a rescan does not re-read every file. */
    private fun identify(uri: Uri, key: String): Pair<String, Long>? {
        fids.getString(key, null)?.split(':')?.let { p -> p.getOrNull(1)?.toLongOrNull()?.let { return p[0] to it } }
        return try {
            resolver.openFileDescriptor(uri, "r")?.use { pfd ->
                FileInputStream(pfd.fileDescriptor).channel.use { ch ->
                    val size = ch.size()
                    if (size < MIN_BYTES) null else (fingerprint(ch, size) to size).also { fids.edit().putString(key, "${it.first}:$size").apply() }
                }
            }
        } catch (e: Exception) { Log.w(TAG, "fingerprint $uri: ${e.message}"); null }
    }

    /** Best effort, so a file copied in with adb or a file manager shows up by the next scan. */
    private fun nudgeScanner() = try {
        @Suppress("DEPRECATION")
        val dirs = listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), DOWNLOAD_DIR),
        )
        MediaScannerConnection.scanFile(ctx, dirs.map { it.absolutePath }.toTypedArray(), null, null)
    } catch (e: Exception) { Log.d(TAG, "scanFile: ${e.message}") }

    companion object {
        private const val TAG = "NickOS.Videos"
        const val MIN_BYTES = 5L * 1024 * 1024
        const val MAX_FILES = 200
        const val REPLY_BUDGET = 2_000_000
        /** Folder under Movies where download jobs put their copies. */
        const val DOWNLOAD_DIR = "NickOS"
        private const val MIB = 1024 * 1024

        /** The runtime permissions the library and download jobs need on Android [sdk]. */
        fun permissions(sdk: Int = Build.VERSION.SDK_INT): List<String> = when {
            sdk >= 33 -> listOf(Manifest.permission.READ_MEDIA_VIDEO)
            sdk >= 29 -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            else -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        /**
         * [VideoId.fingerprint] of an open file of [size] bytes: the first and last MiB, or the whole
         * file once when it is no bigger than 2 MiB. The download verifier runs this on its finished
         * copy, so a good copy always gets the fid of its source.
         */
        fun fingerprint(ch: FileChannel, size: Long): String {
            fun read(at: Long, n: Int): ByteArray {
                val buf = ByteBuffer.allocate(n)
                var pos = at
                while (buf.hasRemaining()) { val r = ch.read(buf, pos); if (r <= 0) break; pos += r }
                return buf.array().copyOf(buf.position())
            }
            return if (size <= 2L * MIB) VideoId.fingerprint(size, read(0, size.toInt()), ByteArray(0))
            else VideoId.fingerprint(size, read(0, MIB), read(size - MIB, MIB))
        }
    }
}
