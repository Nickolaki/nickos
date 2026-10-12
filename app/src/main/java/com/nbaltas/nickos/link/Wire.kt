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
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket

/**
 * How tablets talk: one TCP connection per Master↔Node pair, one JSON object per line.
 * The Node listens (preferred port [PORT], so the Master can reconnect straight to the last IP
 * even before mDNS answers) and advertises itself as "NickOS-<deviceId>" over NSD/mDNS.
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

/** A line-based JSON connection. Writes are serialised; reads happen on the owner's thread. */
class Conn(val socket: Socket) {
    private val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
    private val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))

    fun read(): JSONObject? {
        val line = reader.readLine() ?: return null
        return try { JSONObject(line) } catch (e: Exception) { JSONObject() }
    }

    @Synchronized
    fun send(o: JSONObject): Boolean = try {
        writer.write(o.toString()); writer.write("\n"); writer.flush(); true
    } catch (e: Exception) { close(); false }

    fun close() = try { socket.close() } catch (_: Exception) {}

    val isOpen get() = !socket.isClosed

    companion object {
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
