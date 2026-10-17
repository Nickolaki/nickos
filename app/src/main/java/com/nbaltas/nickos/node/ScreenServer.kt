package com.nbaltas.nickos.node

import android.os.SystemClock
import android.util.Log
import com.nbaltas.nickos.link.FrameSeal
import com.nbaltas.nickos.link.LanAddress
import com.nbaltas.nickos.link.hmac
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Hands this tablet's screen to its parents over an encrypted, authenticated TCP stream. There is no HTTP and no plain
 * fallback: a connection that does not start with the exact hello below is closed.
 *
 *   Master -> tablet  "NKS1" | masterId (12 hex, ASCII) | cn (16 random bytes) | hmac(key, "screen-auth" | masterId | cn)
 *   tablet -> Master  sn (16 random bytes), only after the hmac checked out and the Master was admitted
 *
 * `key` is that Master's own per-Master screen key, never sent. Both sides then derive K = hmac(key, "screen-k" | cn | sn)
 * and every picture goes tablet -> Master as [4-byte big-endian length][FrameSeal(K, "n2m-screen", "m2n-screen")] of
 * (jpeg | 1 rotation byte, in quarter turns). A client cn is accepted once per server lifetime.
 *
 * The connection is the heartbeat. When the last Master stops reading and none comes back within [graceMs], [onIdle]
 * fires; the same if none connects at all within [firstGraceMs]. Only the newest picture is kept: a slow Master misses
 * pictures, it never holds the capture up.
 */
class ScreenServer(
    private val onIdle: () -> Unit,
    private val preferredPort: Int = PORT,
    private val graceMs: Long = 5_000,
    private val firstGraceMs: Long = 15_000,
    private val allowPeer: (String) -> Boolean = ::lanOrLocal,
    /** The hello must be complete this soon after connecting, however slowly its bytes trickle in. */
    private val headMs: Long = 5_000,
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
    /** The key of each Master (by Master id) that may watch right now. Read afresh for every hello. */
    private val keys: () -> Map<String, String>,
) {
    private class Client(val socket: Socket, val key: String) {
        /** When the write in progress began, 0 while waiting for a picture. */
        @Volatile var writeStart = 0L
        fun close() = try { socket.close() } catch (_: Exception) {}
    }

    private var server: ServerSocket? = null
    private val clients = CopyOnWriteArrayList<Client>()
    /** Keys revoked for this server lifetime; guarded by clients. */
    private val revokedKeys = HashSet<String>()
    /** Client nonces already used; guarded by clients. */
    private val seenNonces = HashSet<String>()
    /** Test seam after authentication, before taking the admission lock. */
    @Volatile internal var beforeAdmission: ((String) -> Unit)? = null
    /** Connections that have not finished their hello yet, with when each connected. */
    private val greeting = ConcurrentHashMap<Socket, Long>()
    private val idleFired = AtomicBoolean(false)
    private val rnd = SecureRandom()
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

    /** Closes readers of [k]; that key also cannot be admitted again on this server. */
    fun dropKey(k: String) {
        synchronized(clients) {
            revokedKeys += k
            clients.filter { it.key == k }.forEach { it.close() }
        }
    }

    /** The keys that connected clients used. A key leaves this set when its client disconnects. */
    fun connectedKeys(): Set<String> = clients.map { it.key }.toSet()

    @Synchronized
    fun start(): Boolean {
        if (server != null) return true
        if (closed) return false
        val ss = try {
            ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(preferredPort)) }
        } catch (e: Exception) {
            try { ServerSocket(0) } catch (e2: Exception) { Log.w(TAG, "no port for the screen", e2); return false }
        }
        server = ss
        emptySince = clock()
        Thread({ acceptLoop(ss) }, "nickos-screen-accept").apply { isDaemon = true }.start()
        Thread({ watchLoop() }, "nickos-screen-watch").apply { isDaemon = true }.start()
        return true
    }

    /** The newest picture. Called from the capture thread; never blocks on a client. */
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
            Thread({ serve(s) }, "nickos-screen-client").apply { isDaemon = true }.start()
        }
    }

    private class Admitted(val client: Client, val seal: FrameSeal)

    private fun serve(s: Socket) {
        var client: Client? = null
        try {
            val admitted = try { greet(s) } finally { greeting.remove(s) }
            if (admitted == null) return
            val c = admitted.client
            client = c
            // The Master sends nothing more. Reading is how a closed connection is noticed at once,
            // even when the screen has no picture to write.
            Thread({ drain(c) }, "nickos-screen-reader").apply { isDaemon = true }.start()
            write(c, admitted.seal)
        } catch (e: Exception) {
            Log.d(TAG, "screen client ended: ${e.message}")
        } finally {
            try { s.close() } catch (_: Exception) {}
            if (client != null) leave(client)
        }
    }

    /** Reads and checks the hello. Returns the admitted client, or null with the socket left for the caller to close. */
    private fun greet(s: Socket): Admitted? {
        s.tcpNoDelay = true
        s.soTimeout = READ_TIMEOUT_MS
        if (!allowPeer(s.inetAddress?.hostAddress ?: "")) return null
        val hello = ByteArray(HELLO_SIZE)
        DataInputStream(s.getInputStream()).readFully(hello)
        if (!hello.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) return null
        val idBytes = hello.copyOfRange(MAGIC.size, MAGIC.size + ID_SIZE)
        val cn = hello.copyOfRange(MAGIC.size + ID_SIZE, MAGIC.size + ID_SIZE + NONCE_SIZE)
        val mac = hello.copyOfRange(MAGIC.size + ID_SIZE + NONCE_SIZE, HELLO_SIZE)
        val key = keys()[String(idBytes, Charsets.US_ASCII)] ?: return null
        val want = authMac(key, idBytes, cn)
        if (!MessageDigest.isEqual(want, mac)) return null
        beforeAdmission?.invoke(key)
        val c = Client(s, key)
        val sn = ByteArray(NONCE_SIZE).also { rnd.nextBytes(it) }
        val rejected = synchronized(clients) {
            if (key in revokedKeys || closed || clients.size >= MAX_CLIENTS || seenNonces.size >= MAX_NONCES
                || !seenNonces.add(String(cn, Charsets.ISO_8859_1))
            ) true
            else { clients += c; hadClient = true; emptySince = -1L; false }
        }
        if (rejected) return null
        s.getOutputStream().apply { write(sn); flush() }
        s.soTimeout = 0
        return Admitted(c, FrameSeal(sessionKey(key, cn, sn), "n2m-screen", "m2n-screen"))
    }

    private fun leave(c: Client) {
        synchronized(clients) {
            if (clients.remove(c) && clients.isEmpty()) emptySince = clock()
        }
        synchronized(slot) { slot.notifyAll() }
    }

    private fun write(c: Client, seal: FrameSeal) {
        val out = DataOutputStream(java.io.BufferedOutputStream(c.socket.getOutputStream(), 64 * 1024))
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
            val sealed = seal.seal(jpeg + byteArrayOf(((rot / 90) and 3).toByte()))
            c.writeStart = clock()
            out.writeInt(sealed.size)
            out.write(sealed)
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
            // A hello that is still arriving after the deadline is cut off, so a few slow strangers cannot hold every slot.
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
        const val PORT = 47473
        const val MAX_CLIENTS = 2
        /** The biggest sealed frame accepted: 2 MB. */
        const val MAX_FRAME = 2 * 1024 * 1024
        internal val MAGIC = "NKS1".toByteArray(Charsets.US_ASCII)
        internal const val ID_SIZE = 12
        internal const val NONCE_SIZE = 16
        internal const val MAC_SIZE = 32
        internal const val HELLO_SIZE = 4 + ID_SIZE + NONCE_SIZE + MAC_SIZE
        private const val MAX_PENDING = 6
        private const val MAX_NONCES = 1024
        private const val MAX_EXTRA = 16 * 1024
        private const val READ_TIMEOUT_MS = 5_000
        private const val STALL_MS = 10_000L
        private const val TAG = "NickOS.ScreenServer"

        /** A tablet on the same home network, or this tablet itself (an adb port forward when testing). The key is still needed. */
        fun lanOrLocal(ip: String): Boolean {
            if (LanAddress.isSiteLocal(ip)) return true
            val o = LanAddress.octets(ip) ?: return false
            return o[0].toInt() == 127
        }

        /** The proof a Master gives that it holds [key], bound to its id and to this connection's nonce. */
        internal fun authMac(key: String, masterId: ByteArray, cn: ByteArray): ByteArray =
            hmac(key.toByteArray(Charsets.UTF_8), "screen-auth".toByteArray(), masterId, cn)

        /** The key of one connection's frames. */
        internal fun sessionKey(key: String, cn: ByteArray, sn: ByteArray): ByteArray =
            hmac(key.toByteArray(Charsets.UTF_8), "screen-k".toByteArray(), cn, sn)
    }
}
