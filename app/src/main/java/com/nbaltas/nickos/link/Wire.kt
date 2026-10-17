package com.nbaltas.nickos.link

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket

/**
 * How tablets talk: one TCP connection per Master↔Node pair, one JSON object per line.
 * The Node listens (preferred port [PORT], so the Master can reconnect straight to the last IP
 * even before mDNS answers) and advertises itself as "NickOS-<deviceId>" over NSD/mDNS.
 *
 * Link v2 ("sec2", see link/Handshake.kt): {"t":"hello","v":2,...} is answered by a challenge and the token is never
 * sent; everything after the handshake is AES-GCM sealed. The v1 forms below still work for a peer that has not yet
 * completed a v2 handshake (the ratchet in link/LinkFlags.kt).
 *
 * Master → Node: {"t":"pair",code,...} | {"t":"hello",masterId,token} | {"t":"cmd","c":...}
 * Node → Master: {"t":"paired",token,...} | {"t":"welcome"} | {"t":"denied"} | {"t":"status",...}
 *                | {"t":"apps",list} | {"t":"want",itemId} | {"t":"reset"} (about to wipe itself)
 *
 * Watch together adds nine commands and six replies (see sync/SyncMessages.kt for encode/decode):
 * cmd "ping"/"videos"/"sync"/"syncPause"/"syncStop"/"serve"/"fetch"/"cancel"/"jobs"/"delete" from the
 * Master, and "pong"/"videos"/"syncState"/"serving"/"jobs"/"deleted" from the Node, plus a "caps" array
 * inside "status" (a constant, so it does not change the status dedupe key). An old Node with
 * no "caps" field, or an old Master that never sends the new commands, both keep working: the
 * Node's command "when" has no else branch, and NodeStatus.from treats a missing "caps" as none.
 *
 * Tablet cameras add cmd "camStart"/"camStop", the reply "camState" and "cam1" in "caps" (see cam/CamMessages.kt).
 *
 * Hands-off joining adds cmd "wifi" (ssid, pass: the car hotspot; sealed like everything after the handshake), a "wifi" object in
 * "status", "wifi1" in "caps", and a first-line {"t":"who"} answered by {"t":"iam",nodeId,name} and a close, which
 * the Master's subnet sweep uses to find a Node without multicast (see link/NetMessages.kt).
 */
object Wire {
    const val SERVICE_TYPE = "_nickos._tcp."
    const val PORT = 47470
    private const val TAG = "NickOS.Wire"

    fun serviceName(deviceId: String) = "NickOS-$deviceId"

    /** Android appends " (2)" etc. on name clashes, so match the id prefix only. */
    fun idFromServiceName(name: String): String? =
        Regex("^NickOS-([0-9a-f]{12})").find(name)?.groupValues?.get(1)

    fun multicastLock(context: Context): WifiManager.MulticastLock? = try {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifi.createMulticastLock("nickos").apply { setReferenceCounted(false); acquire() }
    } catch (e: Exception) { Log.w(TAG, "no multicast lock", e); null }
}

/**
 * A line-based JSON connection. Writes are serialised; reads happen on the owner's thread.
 * Lines are capped ([FIRST_LINE_MAX] before the peer has proved anything, [LINE_MAX] after), and once a [Seal] is
 * set every line in both directions is AES-GCM sealed: an unopenable line closes the socket.
 */
class Conn(val socket: Socket) {
    private val input = socket.getInputStream()
    private val output = socket.getOutputStream()
    private val buf = ByteArray(8192)
    private var pos = 0
    private var lim = 0
    @Volatile private var sealer: Seal? = null

    /** From now on every line sent is sealed and every line read must open. */
    fun seal(s: Seal) { sealer = s }

    /** True once a v2 handshake sealed this link. A legacy v1 session never is. */
    val isSealed get() = sealer != null

    /**
     * Reads one line as JSON. Null at end of stream, when the line is longer than [maxBytes], when [deadlineMs]
     * (0 = none) has passed, or when a sealed line fails to open; the socket is closed in the last three cases.
     */
    fun read(maxBytes: Int = LINE_MAX, deadlineMs: Long = 0): JSONObject? {
        val line = readLine(maxBytes, deadlineMs) ?: return null
        val s = sealer
        val text = if (s == null) line else {
            val plain = s.open(line)
            if (plain == null) { close(); return null }
            String(plain, Charsets.UTF_8)
        }
        return try { JSONObject(text) } catch (e: Exception) { JSONObject() }
    }

    private fun readLine(maxBytes: Int, deadlineMs: Long): String? {
        val out = java.io.ByteArrayOutputStream()
        val start = System.nanoTime()
        while (true) {
            if (pos == lim) {
                if (deadlineMs > 0 && (System.nanoTime() - start) / 1_000_000 > deadlineMs) { close(); return null }
                val n = input.read(buf)
                if (n < 0) return if (out.size() == 0) null else finish(out)
                pos = 0; lim = n
            }
            var i = pos
            while (i < lim && buf[i] != '\n'.code.toByte()) i++
            if (out.size() + (i - pos) > maxBytes) { close(); return null }
            out.write(buf, pos, i - pos)
            if (i < lim) { pos = i + 1; return finish(out) }
            pos = lim
        }
    }

    private fun finish(out: java.io.ByteArrayOutputStream): String {
        val b = out.toByteArray()
        val n = if (b.isNotEmpty() && b[b.size - 1] == '\r'.code.toByte()) b.size - 1 else b.size
        return String(b, 0, n, Charsets.UTF_8)
    }

    // One writer at a time: a sealed line's counter must reach the wire in the order it was taken.
    private val writeLock = java.util.concurrent.locks.ReentrantLock()

    fun send(o: JSONObject): Boolean {
        writeLock.lock()
        try { return write(o) } finally { writeLock.unlock() }
    }

    /** How an audio frame went: on the wire, dropped because the link was busy, or the link is stuck or gone. */
    enum class Lossy { WRITTEN, BUSY, STALLED, FAILED }

    /**
     * For audio frames: writes only if no other line is being written right now, otherwise gives up at once
     * ([Lossy.BUSY], the frame is dropped). A command therefore never waits behind a queue of audio, only ever
     * behind the one frame already on the wire, and that write has a deadline: a peer that stopped reading would
     * otherwise block it (and the write lock, and every command to this tablet) for as long as its buffers stay full.
     * Past [deadlineMs] the link is closed (a socket cannot be un-blocked any other way), which frees the lock and
     * returns [Lossy.STALLED]; the caller ends that tablet's talk session and the link comes back by reconnecting.
     */
    fun sendLossy(o: JSONObject, deadlineMs: Long = AUDIO_WRITE_MS): Lossy {
        if (!writeLock.tryLock()) return Lossy.BUSY
        val stalled = java.util.concurrent.atomic.AtomicBoolean(false)
        val watchdog = watchdogs.schedule({ stalled.set(true); close() }, deadlineMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        try {
            val ok = write(o)
            watchdog.cancel(false)
            return if (ok) Lossy.WRITTEN else if (stalled.get()) Lossy.STALLED else Lossy.FAILED
        } finally { writeLock.unlock() }
    }

    private fun write(o: JSONObject): Boolean = try {
        val s = sealer
        val line = if (s == null) o.toString() else s.seal(o.toString().toByteArray(Charsets.UTF_8))
        output.write(line.toByteArray(Charsets.UTF_8)); output.write('\n'.code); output.flush(); true
    } catch (e: Exception) { close(); false }

    fun close() = try { socket.close() } catch (_: Exception) {}

    val isOpen get() = !socket.isClosed

    companion object {
        /** The longest one audio frame may take to write. A command to any tablet waits at most this long behind it. */
        const val AUDIO_WRITE_MS = 250L

        private val watchdogs = java.util.concurrent.ScheduledThreadPoolExecutor(1) { r ->
            Thread(r, "NickOS.WriteWatch").also { it.isDaemon = true }
        }.also { it.removeOnCancelPolicy = true }

        /** The longest line accepted before the peer has authenticated. */
        const val FIRST_LINE_MAX = 4 * 1024
        /** After auth: the Node's "apps" reply carries every app icon and the Master's "resume" cards carry posters. */
        const val LINE_MAX = 8 * 1024 * 1024

        fun open(host: String, port: Int, timeoutMs: Int = 3000): Conn {
            val s = Socket()
            s.connect(InetSocketAddress(host, port), timeoutMs)
            s.tcpNoDelay = true
            s.soTimeout = 0
            return Conn(s)
        }
    }
}

/**
 * NSD resolve wrapper. Before Android 14 NsdManager only resolves one service at a time
 * (FAILURE_ALREADY_ACTIVE otherwise), so every resolve goes through one mutex.
 */
class Resolver(private val nsd: NsdManager) {
    private val mutex = Mutex()

    suspend fun resolve(info: NsdServiceInfo): Pair<String, Int>? = mutex.withLock {
        repeat(3) {
            val done = CompletableDeferred<Pair<String, Int>?>()
            @Suppress("DEPRECATION")
            nsd.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(si: NsdServiceInfo, code: Int) {
                    done.complete(if (code == NsdManager.FAILURE_ALREADY_ACTIVE) RETRY else null)
                }

                override fun onServiceResolved(si: NsdServiceInfo) {
                    val host = if (Build.VERSION.SDK_INT >= 34) si.hostAddresses.firstOrNull { it is java.net.Inet4Address }?.hostAddress
                        ?: si.hostAddresses.firstOrNull()?.hostAddress
                    else si.host?.hostAddress
                    done.complete(if (host != null) host to si.port else null)
                }
            })
            val r = withTimeoutOrNull(6000) { done.await() }
            if (r !== RETRY) return@withLock r
            delay(400)
        }
        null
    }

    private companion object { val RETRY = "" to -1 }
}
