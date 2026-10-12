package com.nbaltas.nickos.master

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Log
import com.nbaltas.nickos.cam.CamPlan
import com.nbaltas.nickos.cam.MjpegParser
import com.nbaltas.nickos.cam.PartHeader
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.BufferedSource
import java.net.Proxy
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** An open camera stream. Closing it is safe from any thread, and more than once. */
fun interface CamStream {
    fun close()
}

/**
 * Reads one tablet's camera stream (see `node/CamServer.kt`) and hands each picture over as a
 * Bitmap. At most 15 pictures a second are decoded; the rest are read and dropped.
 *
 * It never reconnects. When the stream ends for any reason it says so once through [onEnded]
 * ([CamPlan.ENDED_KEY] when the tablet refused the key, else [CamPlan.ENDED_IO]) and the
 * controller decides what happens next. A stream closed with [close] ends silently.
 */
class MjpegClient(
    private val url: String,
    private val onFrame: (Frame) -> Unit,
    private val onEnded: (String) -> Unit,
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
    private val http: OkHttpClient = HTTP,
) : CamStream {
    /** One decoded picture. [rotation] is what the tablet's sensor says it needs; [at] is this tablet's clock. */
    data class Frame(val bitmap: Bitmap, val rotation: Int, val at: Long)

    @Volatile private var call: Call? = null
    @Volatile private var closed = false
    private val ended = AtomicBoolean(false)

    fun start(): MjpegClient {
        Thread({ run() }, "nickos-mjpeg").apply { isDaemon = true }.start()
        return this
    }

    override fun close() {
        closed = true
        call?.cancel()
    }

    private fun run() {
        var reason = CamPlan.ENDED_IO
        try {
            val c = http.newCall(Request.Builder().url(url).build())
            call = c
            if (closed) return
            c.execute().use { r ->
                if (r.code == 403) { reason = CamPlan.ENDED_KEY; return@use }
                if (!r.isSuccessful) return@use
                val src = r.body?.source() ?: return@use
                // Not reused through inBitmap: that is the first thing to add if garbage collection shows up in a trace.
                val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
                var last = 0L
                var any = false
                while (!closed) {
                    val h = readHeaders(src) ?: break
                    val bytes = src.readByteArray(h.length.toLong())
                    src.readUtf8LineStrict(2)
                    val now = clock()
                    if (any && now - last < MIN_GAP_MS) continue
                    // The byte count says nothing about how big a picture unpacks to: look at its size
                    // before decoding, and never decode one far beyond what a tablet camera sends.
                    val size = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, size)
                    if (size.outWidth <= 0 || size.outHeight <= 0 || size.outWidth > MAX_SIDE || size.outHeight > MAX_SIDE) continue
                    // A corrupt picture is skipped, not the end of the stream.
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: continue
                    last = now
                    any = true
                    if (!closed) onFrame(Frame(bmp, h.rotation, now))
                }
            }
        } catch (e: Exception) {
            if (!closed) Log.d(TAG, "camera stream ended: ${e.message}")
        } finally {
            if (!closed && ended.compareAndSet(false, true)) onEnded(reason)
        }
    }

    /** The header lines of the next part, up to its blank line. Null when they are not a part's. */
    private fun readHeaders(src: BufferedSource): PartHeader? {
        val lines = ArrayList<String>(6)
        repeat(MAX_HEADER_LINES) {
            val line = src.readUtf8LineStrict(1024)
            if (line.isEmpty()) {
                if (lines.any { ':' in it }) return MjpegParser.parsePartHeaders(lines)
            } else lines += line
        }
        return null
    }

    companion object {
        private const val TAG = "NickOS.Mjpeg"
        private const val MIN_GAP_MS = 1000L / 15
        private const val MAX_HEADER_LINES = 32
        /** The biggest profile is 960 by 720; twice that each way is already not a tablet camera. */
        private const val MAX_SIDE = 1920

        /** A stream has no end, so no call timeout; a silent one is given up on after 5 seconds. Home network only, so no proxy. */
        private val HTTP: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .callTimeout(0, TimeUnit.SECONDS)
                .proxy(Proxy.NO_PROXY)
                .retryOnConnectionFailure(false)
                // The tablet answers with the stream or not at all: it cannot send this Master somewhere else.
                .followRedirects(false)
                .followSslRedirects(false)
                .build()
        }
    }
}
