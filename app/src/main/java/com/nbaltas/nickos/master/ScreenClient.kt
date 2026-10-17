package com.nbaltas.nickos.master

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.nbaltas.nickos.cam.CamPlan
import com.nbaltas.nickos.link.FrameSeal
import com.nbaltas.nickos.node.ScreenServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.DataInputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reads one tablet's screen (see `node/ScreenServer.kt`): an encrypted stream, authenticated with this Master's own
 * screen key, which is never sent. There is no HTTP and no plain fallback.
 *
 * It never reconnects. When the stream ends for any reason it says so once through [onEnded]
 * ([CamPlan.ENDED_KEY] when the tablet closed the connection before answering the hello, else [CamPlan.ENDED_IO]) and
 * the controller decides what happens next. A stream closed with [close] ends silently.
 */
class ScreenClient(
    private val host: String,
    private val port: Int,
    private val masterId: String,
    private val key: String,
    private val onFrame: (Bitmap) -> Unit = {},
    private val onEnded: (String) -> Unit,
) : CamStream {
    private val _frame = MutableStateFlow<Bitmap?>(null)
    /** The newest picture. */
    val frame: StateFlow<Bitmap?> = _frame

    /** Test seam: every opened picture, before it is decoded. */
    @Volatile internal var tap: ((jpeg: ByteArray, quarterTurns: Int) -> Unit)? = null

    @Volatile private var socket: Socket? = null
    @Volatile private var closed = false
    private val ended = AtomicBoolean(false)

    fun start(): ScreenClient {
        Thread({ run() }, "nickos-screen-client").apply { isDaemon = true }.start()
        return this
    }

    override fun close() {
        closed = true
        try { socket?.close() } catch (_: Exception) {}
    }

    private fun run() {
        var reason = CamPlan.ENDED_IO
        try {
            val s = Socket()
            socket = s
            if (closed) { s.close(); return }
            s.tcpNoDelay = true
            s.connect(InetSocketAddress(host, port), CONNECT_MS)
            s.soTimeout = READ_MS
            val id = masterId.toByteArray(Charsets.US_ASCII)
            val cn = ByteArray(ScreenServer.NONCE_SIZE).also { SecureRandom().nextBytes(it) }
            s.getOutputStream().apply {
                write(ScreenServer.MAGIC + id + cn + ScreenServer.authMac(key, id, cn))
                flush()
            }
            val input = DataInputStream(s.getInputStream())
            val sn = ByteArray(ScreenServer.NONCE_SIZE)
            try { input.readFully(sn) } catch (e: java.io.EOFException) { reason = CamPlan.ENDED_KEY; throw e }
            s.soTimeout = STREAM_READ_MS
            val seal = FrameSeal(ScreenServer.sessionKey(key, cn, sn), "m2n-screen", "n2m-screen")
            val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
            while (!closed) {
                val n = input.readInt()
                if (n < 0 || n > ScreenServer.MAX_FRAME) break
                val raw = ByteArray(n)
                input.readFully(raw)
                val plain = seal.open(raw) ?: break
                if (plain.size < 2) break
                val jpeg = plain.copyOfRange(0, plain.size - 1)
                tap?.invoke(jpeg, plain[plain.size - 1].toInt() and 3)
                // The byte count says nothing about how big a picture unpacks to: look at its size before decoding.
                val size = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, size)
                if (size.outWidth <= 0 || size.outHeight <= 0 || size.outWidth > MAX_SIDE || size.outHeight > MAX_SIDE) continue
                // A corrupt picture is skipped, not the end of the stream.
                val bmp = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, opts) ?: continue
                if (!closed) { _frame.value = bmp; onFrame(bmp) }
            }
        } catch (e: Exception) {
            if (!closed) Log.d(TAG, "screen stream ended: ${e.message}")
        } finally {
            try { socket?.close() } catch (_: Exception) {}
            if (!closed && ended.compareAndSet(false, true)) onEnded(reason)
        }
    }

    private companion object {
        const val TAG = "NickOS.ScreenClient"
        const val CONNECT_MS = 3_000
        /** The tablet's answer to the hello. */
        const val READ_MS = 5_000
        /** A screen that does not change makes no new picture, so a quiet stream is not a dead one for a while. */
        const val STREAM_READ_MS = 30_000
        /** The tablet captures at most 1280 on a side; twice that is already not a tablet screen. */
        const val MAX_SIDE = 1920
    }
}
