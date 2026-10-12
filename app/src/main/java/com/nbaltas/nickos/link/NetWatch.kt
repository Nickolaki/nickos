package com.nbaltas.nickos.link

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.Inet4Address

/**
 * Turns a burst of raw network events into one [onChange]. Pure apart from the coroutine scope, so it is
 * unit tested with a fake event feed.
 *  - [available] and [lost] always count.
 *  - [address] (an interface address was seen) counts only when the IPv4 address differs from the last one.
 * Events within [debounceMs] of each other collapse into one call, fired [debounceMs] after the LAST event, so a
 * reconnect that flaps on, off, on produces a single restart after things settle.
 */
class ChangeGate(private val scope: CoroutineScope, private val debounceMs: Long = 1500, private val onChange: () -> Unit) {
    private var pending: Job? = null
    private var lastIp: String? = null
    private var closed = false

    fun available() = fire()
    fun lost() { synchronized(this) { lastIp = null }; fire() }

    /** [ip] is the current IPv4 address on the watched interface, null when it has none. */
    fun address(ip: String?) {
        val changed = synchronized(this) { (ip != lastIp).also { if (it) lastIp = ip } }
        if (changed) fire()
    }

    @Synchronized
    private fun fire() {
        if (closed) return
        pending?.cancel()
        pending = scope.launch {
            delay(debounceMs)
            if (synchronized(this@ChangeGate) { closed }) return@launch
            onChange()
        }
    }

    @Synchronized
    fun close() { closed = true; pending?.cancel(); pending = null }
}

/**
 * Tells both runtimes when the network under them changed. Three sources, because no single one covers every case:
 *  - a WiFi [ConnectivityManager] callback (this tablet joined, lost, or was re-addressed on someone else's WiFi),
 *  - the WiFi AP state broadcast (this tablet's OWN hotspot came up or went down: Android gives a hosting tablet
 *    no Network for that, so the callback never fires for it), and
 *  - a light poll of [LanAddress.own] every [pollMs], which is what actually catches an ap/swlan interface
 *    appearing on any OEM, and a stuck callback.
 * The first event of any kind fires too, so each runtime restarts discovery once shortly after it starts.
 * That is cheap and wanted.
 */
class NetWatch(private val ctx: Context, debounceMs: Long = 1500, private val pollMs: Long = 5000, onChange: () -> Unit) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val gate = ChangeGate(scope, debounceMs, onChange)
    private var cm: ConnectivityManager? = null
    private var callback: ConnectivityManager.NetworkCallback? = null
    private var apReceiver: BroadcastReceiver? = null
    private var started = false

    @Synchronized
    fun start() {
        if (started) return
        started = true
        val manager = ctx.getSystemService(ConnectivityManager::class.java)
        cm = manager
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = gate.available()
            override fun onLost(network: Network) = gate.lost()
            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                gate.address(linkProperties.linkAddresses.map { it.address }.filterIsInstance<Inet4Address>().firstOrNull()?.hostAddress)
            }
        }
        callback = cb
        try {
            manager.registerNetworkCallback(NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), cb)
        } catch (e: Exception) { Log.w(TAG, "no network callback", e); callback = null }
        val rx = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = gate.available()
        }
        apReceiver = rx
        try {
            // The two Wi-Fi Direct broadcasts matter on the Master: a group it owns has no Network object, so the
            // callback above is silent when a Node joins or leaves it; CONNECTION_CHANGED is the only prompt.
            val f = IntentFilter().apply { addAction(AP_STATE); addAction(TETHER_STATE); addAction(P2P_CONNECTION); addAction(P2P_STATE) }
            ContextCompat.registerReceiver(ctx.applicationContext, rx, f, ContextCompat.RECEIVER_EXPORTED)
        } catch (e: Exception) { Log.w(TAG, "no ap receiver", e); apReceiver = null }
        scope.launch {
            while (isActive) {
                gate.address(LanAddress.own(ctx))
                delay(pollMs)
            }
        }
    }

    @Synchronized
    fun stop() {
        gate.close()
        scope.cancel()
        callback?.let { try { cm?.unregisterNetworkCallback(it) } catch (_: Exception) {} }
        callback = null
        apReceiver?.let { try { ctx.applicationContext.unregisterReceiver(it) } catch (_: Exception) {} }
        apReceiver = null
    }

    companion object {
        private const val TAG = "NickOS.Net"
        private const val AP_STATE = "android.net.wifi.WIFI_AP_STATE_CHANGED"
        private const val TETHER_STATE = "android.net.conn.TETHER_STATE_CHANGED"
        /** WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION and WIFI_P2P_STATE_CHANGED_ACTION. */
        const val P2P_CONNECTION = "android.net.wifi.p2p.CONNECTION_STATE_CHANGE"
        const val P2P_STATE = "android.net.wifi.p2p.STATE_CHANGED"
    }
}
