package com.nbaltas.nickos.master

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.nbaltas.nickos.link.LanAddress
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * The real [P2pPort] over [WifiP2pManager]. Every call wraps a listener in a coroutine under a timeout, so a
 * listener that never fires cannot hang the caller. The [WifiP2pManager.Channel] is held here for the life of
 * the port: the framework removes a group when the client that created it goes away.
 *
 * Needs NEARBY_WIFI_DEVICES (Android 13+) or fine location (29 to 32). Never logs the passphrase.
 */
@SuppressLint("MissingPermission")
class AndroidP2pPort(ctx: Context) : P2pPort {
    private val app = ctx.applicationContext
    override var onEvent: (() -> Unit)? = null

    private val manager: WifiP2pManager? = app.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    @Volatile private var channel: WifiP2pManager.Channel? = null
    @Volatile private var apEnabled = false
    private var receiver: BroadcastReceiver? = null

    @Synchronized
    private fun channel(): WifiP2pManager.Channel? {
        channel?.let { return it }
        val m = manager ?: return null
        return try {
            m.initialize(app, Looper.getMainLooper()) {
                // The framework dropped the channel (Wi-Fi service died or restarted): start over on the next call.
                Log.w(TAG, "channel lost")
                channel = null
                onEvent?.invoke()
            }.also { channel = it }
        } catch (e: Exception) { Log.w(TAG, "no p2p channel: ${e.javaClass.simpleName}"); null }
    }

    @Synchronized
    override fun open() {
        if (receiver != null) return
        val rx = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                if (i.action == AP_STATE) apEnabled = i.getIntExtra(EXTRA_AP_STATE, 0).let { it == 12 || it == 13 }
                onEvent?.invoke()
            }
        }
        try {
            val f = IntentFilter().apply {
                addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
                addAction(AP_STATE)
                addAction(LocationManager.MODE_CHANGED_ACTION)
            }
            ContextCompat.registerReceiver(app, rx, f, ContextCompat.RECEIVER_EXPORTED)
            receiver = rx
        } catch (e: Exception) { Log.w(TAG, "no p2p receiver: ${e.javaClass.simpleName}") }
    }

    @Synchronized
    override fun close() {
        receiver?.let { try { app.unregisterReceiver(it) } catch (_: Exception) {} }
        receiver = null
        channel = null
    }

    override fun supported(): Boolean =
        Build.VERSION.SDK_INT >= 29 && manager != null && app.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)

    override fun hasPermission(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= 33) CarNet.NEARBY_WIFI_DEVICES else CarNet.ACCESS_FINE_LOCATION
        return app.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED
    }

    override fun locationOn(): Boolean {
        if (Build.VERSION.SDK_INT >= 33 || Build.VERSION.SDK_INT < 28) return true
        return try { app.getSystemService(LocationManager::class.java).isLocationEnabled } catch (e: Exception) { true }
    }

    @Suppress("DEPRECATION")
    override fun wifiOn(): Boolean = try { (app.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).isWifiEnabled } catch (e: Exception) { false }

    /** The WiFi hotspot (this tablet as an access point). The public API has no getter, so the broadcast and a reflective read. */
    @Suppress("DEPRECATION")
    override fun tetheringOn(): Boolean {
        if (apEnabled) return true
        return try {
            val wm = app.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wm.javaClass.getMethod("isWifiApEnabled").invoke(wm) as? Boolean ?: false
        } catch (e: Throwable) { false }
    }

    override suspend fun groupInfo(): Group? {
        val m = manager ?: return null
        val ch = channel() ?: return null
        val g: WifiP2pGroup? = withTimeoutOrNull(TIMEOUT_MS) {
            suspendCancellableCoroutine { k ->
                try { m.requestGroupInfo(ch) { if (k.isActive) k.resume(it) } } catch (e: Exception) { if (k.isActive) k.resume(null) }
            }
        }
        if (g == null) return null
        val ip = ownerIp(m, ch, g)
        return Group(g.networkName.orEmpty(), g.passphrase, ip, g.isGroupOwner, g.clientList?.size ?: 0)
    }

    /** The group owner's address as the system reports it; failing that the address on the p2p interface. Read, not assumed. */
    private suspend fun ownerIp(m: WifiP2pManager, ch: WifiP2pManager.Channel, g: WifiP2pGroup): String? {
        if (!g.isGroupOwner) return null
        val info: WifiP2pInfo? = withTimeoutOrNull(TIMEOUT_MS) {
            suspendCancellableCoroutine { k ->
                try { m.requestConnectionInfo(ch) { if (k.isActive) k.resume(it) } } catch (e: Exception) { if (k.isActive) k.resume(null) }
            }
        }
        info?.groupOwnerAddress?.hostAddress?.takeIf { LanAddress.isSiteLocal(it) }?.let { return it }
        return LanAddress.all(app).firstOrNull { LanAddress.isDirect(it.first) }?.second
    }

    override suspend fun createGroup(ssid: String, pass: String): Outcome {
        val m = manager ?: return Outcome.UNSUPPORTED
        if (Build.VERSION.SDK_INT < 29) return Outcome.UNSUPPORTED
        val ch = channel() ?: return Outcome.ERROR
        val cfg = try {
            WifiP2pConfig.Builder().setNetworkName(ssid).setPassphrase(pass)
                .setGroupOperatingBand(WifiP2pConfig.GROUP_OWNER_BAND_2GHZ).enablePersistentMode(true).build()
        } catch (e: Exception) { return Outcome.ERROR }
        // NonCancellable: the framework makes the group whether or not we still wait. If the caller is stopped mid-call
        // it must still hear the answer, or the stop's group removal would run before the group exists and leak it.
        // Still bounded by the timeout, so a listener that never fires cannot hang the stop.
        return withContext(NonCancellable) { withTimeoutOrNull(TIMEOUT_MS) {
            suspendCancellableCoroutine { k ->
                val l = object : WifiP2pManager.ActionListener {
                    override fun onSuccess() { if (k.isActive) k.resume(Outcome.OK) }
                    override fun onFailure(reason: Int) {
                        if (k.isActive) k.resume(when (reason) {
                            WifiP2pManager.BUSY -> Outcome.BUSY
                            WifiP2pManager.P2P_UNSUPPORTED -> Outcome.UNSUPPORTED
                            else -> Outcome.ERROR
                        })
                    }
                }
                try { m.createGroup(ch, cfg, l) } catch (e: SecurityException) { if (k.isActive) k.resume(Outcome.ERROR) }
                catch (e: Exception) { if (k.isActive) k.resume(Outcome.ERROR) }
            }
        } ?: Outcome.ERROR }
    }

    override suspend fun removeGroup(): Boolean {
        val m = manager ?: return true
        val ch = channel() ?: return false
        if (groupInfo() == null) return true
        return withTimeoutOrNull(TIMEOUT_MS) {
            suspendCancellableCoroutine { k ->
                val l = object : WifiP2pManager.ActionListener {
                    override fun onSuccess() { if (k.isActive) k.resume(true) }
                    override fun onFailure(reason: Int) { if (k.isActive) k.resume(false) }
                }
                try { m.removeGroup(ch, l) } catch (e: Exception) { if (k.isActive) k.resume(false) }
            }
        } ?: false
    }

    companion object {
        private const val TAG = "NickOS.CarNet"
        private const val TIMEOUT_MS = 10_000L
        private const val AP_STATE = "android.net.wifi.WIFI_AP_STATE_CHANGED"
        private const val EXTRA_AP_STATE = "wifi_state"
    }
}
