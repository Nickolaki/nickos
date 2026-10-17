package com.nbaltas.nickos.node

import android.os.SystemClock
import android.util.Log
import com.nbaltas.nickos.link.LanAddress
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Hands this tablet's camera pictures to a Master as MJPEG over plain HTTP: one long reply of
 * JPEGs (multipart/x-mixed-replace). Raw sockets, no library.
 *
 * The connection is the heartbeat. When the last Master stops reading (it closed the Cameras
 * screen, died, or lost the WiFi) and none comes back within [graceMs], [onIdle] fires and the
 * camera is turned off; the same happens if no Master connects at all within [firstGraceMs]. No
 * message from the Master is needed for the camera to go off.
 *
 * Only the newest picture is kept. A Master that reads slowly misses pictures; it never holds
 * the camera up.
 */
class CamServer(
    private val key: String,
    private val onIdle: () -> Unit,
    private val preferredPort: Int = PORT,
    private val graceMs: Long = 5_000,
    private val firstGraceMs: Long = 15_000,
    private val allowPeer: (String) -> Boolean = ::lanOrLocal,
    /** A request must be complete this soon after connecting, however slowly its bytes trickle in. */
    private val headMs: Long = 5_000,
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
    /** When set, any of these keys opens the stream (one per Master) and [key] is ignored. Read afresh for every request. */
    private val keys: (() -> Collection<String>)? = null,
) {
    private class Client(val socket: Socket, val key: String) {
        /** When the write in progress began, 0 while waiting for a picture. */
        @Volatile var writeStart = 0L
        fun close() = try { socket.close() } catch (_: Exception) {}
    }

    private var server: ServerSocket? = null
    private val clients = CopyOnWriteArrayList<Client>()
    /** Connections that have not finished asking yet, with when each connected. */
    private val greeting = ConcurrentHashMap<Socket, Long>()
    private val idleFired = AtomicBoolean(false)
    @Volatile private var closed = false
    @Volatile private var hadClient = false
    /** When the server last had nobody reading, or -1 while somebody is. */
    @Volatile private var emptySince = -1L

    private val slot = Object()
    private var frame: ByteArray? = null
    private var rotation = 0
    private var seq = 0L

    /** The port actually bound: [preferredPort], or whatever was free if that was taken. 0 before [start]. */
    val port: Int get() = server?.localPort ?: 0

    val clientCount: Int get() = clients.size

    /** Closes every client that used [k] (its Master stopped watching or was unlinked). */
    fun dropKey(k: String) { clients.filter { it.key == k }.forEach { it.close() } }

    /** The keys that connected clients used. A key leaves this set when its client disconnects. */
    fun connectedKeys(): Set<String> = clients.map { it.key }.toSet()

    @Synchronized
    fun start(): Boolean {
        if (server != null) return true
        if (closed) return false
        val ss = try {
            ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(preferredPort)) }
        } catch (e: Exception) {
            try { ServerSocket(0) } catch (e2: Exception) { Log.w(TAG, "no port for the camera", e2); return false }
        }
        server = ss
        emptySince = clock()
        Thread({ acceptLoop(ss) }, "nickos-cam-accept").apply { isDaemon = true }.start()
        Thread({ watchLoop() }, "nickos-cam-watch").apply { isDaemon = true }.start()
        return true
    }

    /** The newest picture. Called from the camera thread; never blocks on a client. */
    fun push(jpeg: ByteArray, rotationDegrees: Int) {
        synchronized(slot) {
            frame = jpeg
            rotation = rotationDegrees
            seq++
            slot.notifyAll()
        }
    }

    /** Closes the port and every client. Idempotent. [onIdle] does not fire after this. */
    @Synchronized
    fun stop() {
        if (closed) return
        closed = true
        idleFired.set(true)
        try { server?.close() } catch (_: Exception) {}
        clients.forEach { it.close() }
        clients.clear()
        greeting.keys.forEach { try { it.close() } catch (_: Exception) {} }
        synchronized(slot) { slot.notifyAll() }
    }

    private fun acceptLoop(ss: ServerSocket) {
        while (!closed) {
            val s = try { ss.accept() } catch (e: Exception) { break }
            // A handful of half-open connections is all a stranger can hold.
            if (greeting.size >= MAX_PENDING) { try { s.close() } catch (_: Exception) {}; continue }
            greeting[s] = clock()
            Thread({ serve(s) }, "nickos-cam-client").apply { isDaemon = true }.start()
        }
    }

    private fun serve(s: Socket) {
        var client: Client? = null
        try {
            client = try { greet(s) } finally { greeting.remove(s) }
            if (client == null) return
            // The Master sends nothing more. Reading is how a closed connection is noticed at once,
            // even when the camera has no picture to write.
            val c = client
            Thread({ drain(c) }, "nickos-cam-reader").apply { isDaemon = true }.start()
            write(c)
        } catch (e: Exception) {
            Log.d(TAG, "camera client ended: ${e.message}")
        } finally {
            try { s.close() } catch (_: Exception) {}
            if (client != null) leave(client)
        }
    }

    /** Reads the request and answers it. Returns the client when it may have the stream, else null with the socket answered. */
    private fun greet(s: Socket): Client? {
        s.tcpNoDelay = true
        s.soTimeout = READ_TIMEOUT_MS
        if (!allowPeer(s.inetAddress?.hostAddress ?: "")) { refuse(s, "403 Forbidden"); return null }
        val head = readHead(BufferedInputStream(s.getInputStream(), 1024)) ?: return null
        val parts = head.lineSequence().firstOrNull()?.trim()?.split(' ') ?: emptyList()
        if (parts.size < 2 || parts[0] != "GET" || parts[1].substringBefore('?') != "/cam") { refuse(s, "404 Not Found"); return null }
        val k = parts[1].substringAfter('?', "").split('&').firstOrNull { it.startsWith("k=") }?.substring(2) ?: ""
        // Every key is compared, so the time taken does not tell which one matched.
        var ok = false
        for (good in keys?.invoke() ?: listOf(key)) {
            if (MessageDigest.isEqual(k.toByteArray(Charsets.UTF_8), good.toByteArray(Charsets.UTF_8))) ok = true
        }
        if (!ok || k.isEmpty()) { refuse(s, "403 Forbidden"); return null }
        val c = Client(s, k)
        val joined = synchronized(clients) {
            if (closed || clients.size >= MAX_CLIENTS) false
            else { clients += c; hadClient = true; emptySince = -1L; true }
        }
        if (!joined) { refuse(s, "503 Service Unavailable"); return null }
        s.soTimeout = 0
        return c
    }

    private fun leave(c: Client) {
        synchronized(clients) {
            if (clients.remove(c) && clients.isEmpty()) emptySince = clock()
        }
        synchronized(slot) { slot.notifyAll() }
    }

    /** The request line and headers, up to the blank line. Null if it does not end within 4 KB. */
    private fun readHead(input: InputStream): String? {
        val buf = ByteArray(MAX_HEAD)
        var n = 0
        while (n < MAX_HEAD) {
            val b = input.read()
            if (b < 0) return null
            buf[n++] = b.toByte()
            if (n >= 4 && buf[n - 1] == LF && buf[n - 2] == CR && buf[n - 3] == LF && buf[n - 4] == CR) {
                return String(buf, 0, n, Charsets.ISO_8859_1)
            }
        }
        return null
    }

    private fun refuse(s: Socket, status: String) {
        try {
            s.getOutputStream().apply {
                write("HTTP/1.1 $status\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
                flush()
            }
        } catch (_: Exception) {}
    }

    private fun write(c: Client) {
        val out = BufferedOutputStream(c.socket.getOutputStream(), 64 * 1024)
        c.writeStart = clock()
        out.write(STREAM_HEAD)
        out.flush()
        c.writeStart = 0L
        var sent = 0L
        while (!closed && !c.socket.isClosed) {
            var bytes: ByteArray? = null
            var rot = 0
            var n = sent
            synchronized(slot) {
                if (seq == sent && !closed && !c.socket.isClosed) slot.wait(500)
                if (seq != sent) { bytes = frame; rot = rotation; n = seq }
            }
            val jpeg = bytes ?: continue
            c.writeStart = clock()
            out.write("--$BOUNDARY\r\nContent-Type: image/jpeg\r\nContent-Length: ${jpeg.size}\r\nX-Rotation: $rot\r\nX-Seq: $n\r\n\r\n".toByteArray(Charsets.US_ASCII))
            out.write(jpeg)
            out.write(CRLF)
            out.flush()
            c.writeStart = 0L
            sent = n
        }
    }

    /** Ends when the Master closes its end. Anything it does send is thrown away, and too much of it ends the connection. */
    private fun drain(c: Client) {
        try {
            val input = c.socket.getInputStream()
            val buf = ByteArray(512)
            var total = 0
            while (total < MAX_EXTRA) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
            }
        } catch (_: Exception) {
        } finally {
            c.close()
            leave(c)
        }
    }

    /** Closes a client whose write has hung (a Master that fell off the WiFi), and calls [onIdle] once nobody has been reading for the grace time. */
    private fun watchLoop() {
        val step = (minOf(graceMs, firstGraceMs) / 4).coerceIn(20, 500)
        while (!closed) {
            try { Thread.sleep(step) } catch (_: InterruptedException) { return }
            val now = clock()
            clients.forEach { c -> c.writeStart.let { w -> if (w != 0L && now - w > STALL_MS) c.close() } }
            // A request that is still arriving after the deadline is cut off, so a few slow strangers cannot hold every slot.
            greeting.forEach { (s, since) -> if (now - since > headMs) try { s.close() } catch (_: Exception) {} }
            val since = emptySince
            if (since >= 0 && clients.isEmpty() && now - since >= (if (hadClient) graceMs else firstGraceMs)) {
                if (idleFired.compareAndSet(false, true)) {
                    try { onIdle() } catch (e: Exception) { Log.w(TAG, "onIdle", e) }
                    return
                }
            }
        }
    }

    companion object {
        const val PORT = 47472
        const val BOUNDARY = "nickos"
        const val MAX_CLIENTS = 2
        private const val MAX_PENDING = 6
        private const val MAX_HEAD = 4096
        private const val MAX_EXTRA = 16 * 1024
        private const val READ_TIMEOUT_MS = 10_000
        private const val STALL_MS = 10_000L
        private const val TAG = "NickOS.CamServer"
        private const val CR: Byte = 13
        private const val LF: Byte = 10
        private val CRLF = byteArrayOf(CR, LF)
        private val STREAM_HEAD =
            "HTTP/1.1 200 OK\r\nContent-Type: multipart/x-mixed-replace; boundary=$BOUNDARY\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n"
                .toByteArray(Charsets.US_ASCII)

        /** A tablet on the same home network, or this tablet itself (an adb port forward when testing). The key is still needed. */
        fun lanOrLocal(ip: String): Boolean {
            if (LanAddress.isSiteLocal(ip)) return true
            val o = LanAddress.octets(ip) ?: return false
            return o[0].toInt() == 127
        }
    }
}
