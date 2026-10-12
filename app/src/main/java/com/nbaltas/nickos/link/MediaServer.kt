package com.nbaltas.nickos.link

import android.content.Context
import android.net.wifi.WifiManager
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import com.nbaltas.nickos.data.Store
import com.nbaltas.nickos.node.LocalVideos
import com.nbaltas.nickos.sync.HttpRange
import fi.iki.elonen.NanoHTTPD
import java.io.FileInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.Timer
import java.util.TimerTask

/** One file the server may hand out: its true [size], its [mime], and a fresh stream at byte 0. */
class Served(val size: Long, val mime: String, val open: () -> FileInputStream?)

/**
 * Keeps WiFi at full power while movies are being served or fetched, and lets it go 30 s after
 * the last one finishes. Without it a sleeping tablet's WiFi dozes and a copy crawls.
 */
class WifiHold(ctx: Context) {
    private val lock: WifiManager.WifiLock? = try {
        @Suppress("DEPRECATION")
        (ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
            .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "nickos:media").apply { setReferenceCounted(false) }
    } catch (e: Exception) { Log.w(TAG, "no wifi lock", e); null }
    private val timer = Timer("nickos-wifi", true)
    private var users = 0
    private var gen = 0

    @Synchronized
    fun acquire() {
        users++; gen++
        try { lock?.acquire() } catch (_: Exception) {}
    }

    @Synchronized
    fun release() {
        if (users > 0) users--
        if (users > 0) return
        val mine = ++gen
        timer.schedule(object : TimerTask() { override fun run() = idle(mine) }, IDLE_MS)
    }

    @Synchronized
    private fun idle(mine: Int) {
        if (gen == mine && users == 0) drop()
    }

    /** The runtime is stopping: let go now. */
    @Synchronized
    fun close() { users = 0; gen++; drop() }

    private fun drop() { try { if (lock?.isHeld == true) lock.release() } catch (_: Exception) {} }

    companion object {
        private const val TAG = "NickOS.Media"
        private const val IDLE_MS = 30_000L
    }
}

/**
 * Hands movies to other tablets over HTTP on the home WiFi, so a copy needs no server and no
 * internet. Runs on any tablet that has the file. The only route is `GET /v/<fid>?k=<key>`:
 * a movie is named by its fingerprint, never by a path, and there is no listing. Nothing is
 * served until a linked Master asks for that one movie ([grant]); the key it gets back opens that
 * movie only, and stops working [GRANT_MS] after the last time it was asked for. Requests from
 * anywhere but a private home-network address are refused, whatever key they carry, so a tablet
 * on mobile data is not offering files to the internet. `Range` requests are honoured so an
 * interrupted copy carries on from the byte it reached.
 *
 * Serving happens on the server's own threads. It never touches the player or the main thread.
 */
class MediaServer(
    private val wanted: Int = PORT,
    private val hold: WifiHold? = null,
    /** Who may ask. The default is other devices on the same private network. */
    private val allowPeer: (String) -> Boolean = LanAddress::isSiteLocal,
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
    private val resolve: (String) -> Served?,
) {
    private class Grant(val key: String, var until: Long)
    private val grants = HashMap<String, Grant>()
    private var http: Http? = null

    /**
     * Lets [fid] be fetched and returns its key. Asking again keeps the same key (copies already
     * running carry on) and pushes the expiry out.
     */
    fun grant(fid: String): String = synchronized(grants) {
        val now = clock()
        grants.values.removeAll { it.until < now }
        grants.getOrPut(fid) { Grant(Store.randomHex(16), 0) }.also { it.until = now + GRANT_MS }.key
    }

    private fun granted(fid: String, key: String): Boolean = synchronized(grants) {
        val g = grants[fid] ?: return false
        g.until >= clock() && MessageDigest.isEqual(key.toByteArray(), g.key.toByteArray())
    }

    /** The port really listened on, 0 while stopped. */
    val port: Int @Synchronized get() = http?.listeningPort?.takeIf { it > 0 } ?: 0

    /** Starts listening if not already: on [wanted], or on any free port when that one is taken. */
    @Synchronized
    fun start(): Boolean {
        if (http != null) return true
        for (p in listOf(wanted, 0).distinct()) {
            val h = Http(p)
            try {
                h.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true)
                http = h
                return true
            } catch (e: IOException) { Log.w(TAG, "port $p: ${e.message}") }
        }
        return false
    }

    @Synchronized
    fun stop() {
        try { http?.stop() } catch (_: Exception) {}
        http = null
        synchronized(grants) { grants.clear() }
    }

    /** Where another tablet fetches [fid] from, given the address this tablet is reached at. Grants it. */
    fun url(host: String, fid: String): String = "http://$host:$port/v/$fid?k=${grant(fid)}"

    private inner class Http(p: Int) : NanoHTTPD(p) {
        override fun serve(session: IHTTPSession): Response {
            if (!allowPeer(session.remoteIpAddress ?: "")) return plain(Response.Status.FORBIDDEN)
            val path = session.uri ?: ""
            if ((session.method != Method.GET && session.method != Method.HEAD) || !path.startsWith("/v/")) return plain(Response.Status.NOT_FOUND)
            val fid = path.substring(3)
            if (fid.isEmpty() || !fid.all { it.isLetterOrDigit() }) return plain(Response.Status.NOT_FOUND)
            val k = session.parameters["k"]?.firstOrNull() ?: ""
            if (!granted(fid, k)) return plain(Response.Status.FORBIDDEN)
            val v = try { resolve(fid) } catch (e: Exception) { null } ?: return plain(Response.Status.NOT_FOUND)

            val header = session.headers["range"]
            val range = HttpRange.parse(header, v.size)
            if (header != null && range == null) {
                return plain(Response.Status.RANGE_NOT_SATISFIABLE).apply { addHeader("Content-Range", "bytes */${v.size}") }
            }
            val start = range?.first ?: 0L
            val length = (range?.last ?: (v.size - 1)) - start + 1

            val file = v.open() ?: return plain(Response.Status.NOT_FOUND)
            try { file.channel.position(start) } catch (e: IOException) {
                try { file.close() } catch (_: IOException) {}
                return plain(Response.Status.INTERNAL_ERROR)
            }
            hold?.acquire()
            // NanoHTTPD reads exactly [length] bytes and then closes the stream, also when the other tablet hangs up.
            val body = object : FilterInputStream(file) {
                private var open = true
                override fun close() {
                    if (open) { open = false; hold?.release() }
                    super.close()
                }
            }
            return newFixedLengthResponse(if (range != null) Response.Status.PARTIAL_CONTENT else Response.Status.OK, v.mime, body, length).apply {
                addHeader("Accept-Ranges", "bytes")
                if (range != null) addHeader("Content-Range", "bytes ${range.first}-${range.last}/${v.size}")
            }
        }

        private fun plain(status: Response.Status): Response = newFixedLengthResponse(status, MIME_PLAINTEXT, status.description)
    }

    companion object {
        private const val TAG = "NickOS.Media"
        const val PORT = 47471
        /** How long a movie stays fetchable after a Master last asked for it. A copy that outlives it asks again. */
        const val GRANT_MS = 6 * 60 * 60_000L

        /** A server over this tablet's own library. A movie not in the last scan gets one rescan. */
        fun of(videos: LocalVideos, hold: WifiHold?): MediaServer = MediaServer(PORT, hold) { fid ->
            videos.find(fid)?.let { v -> Served(v.size, v.mime) { videos.open(v)?.let { ParcelFileDescriptor.AutoCloseInputStream(it) } } }
        }
    }
}
