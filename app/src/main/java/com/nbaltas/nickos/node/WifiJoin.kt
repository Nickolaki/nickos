package com.nbaltas.nickos.node

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.nbaltas.nickos.link.NetMessages
import java.security.MessageDigest

/**
 * Saves the car hotspot on this tablet so Android joins it on its own.
 *
 * Device Owner (the kiosk path): [WifiManager.addNetwork] keeps working for a DO app on Android 10+, so the
 * network is saved silently and nobody touches the tablet. Anything else (a Node set up without QR provisioning):
 * addNetwork returns -1 on 10+, so ONE [WifiNetworkSuggestion] is added instead, and [state] reports `approve`
 * until Android reports a connection through it, because the first use needs one tap on the system notification.
 * That is today's behaviour plus one tap at home; it never crashes and never blocks pairing.
 *
 * Never calls disconnect() or reconnect(): Android chooses. Forcing a join from inside lock task drops the link
 * that is carrying the command.
 */
class WifiJoin(
    private val ctx: Context,
    private val isDeviceOwner: () -> Boolean,
    private val sdk: Int = Build.VERSION.SDK_INT,
    /** The SSID of the WiFi this tablet is on right now (unquoted), or null when unknown or unreadable. */
    private val currentSsid: () -> String? = { null },
) {
    private val wifi get() = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val prefs: SharedPreferences = ctx.getSharedPreferences("nickos_wifi", Context.MODE_PRIVATE)

    /** Turns WiFi on when it is off. Device Owner only; anything else cannot, and says nothing. */
    @Suppress("DEPRECATION")
    fun ensureOn(): Boolean {
        if (!isDeviceOwner()) return false
        return try {
            if (wifi.isWifiEnabled) false else wifi.setWifiEnabled(true)
        } catch (e: Exception) { Log.w(TAG, "cannot turn WiFi on", e); false }
    }

    /**
     * Saves (or with an empty [ssid], forgets) the hotspot. Idempotent: the Master resends on every connect, and
     * removing and re-adding the configuration of the network we are ON would cut the very link that carried the
     * command, so an unchanged ssid and password with a config that still exists does nothing.
     */
    // NewApi: every suggestion call below is behind the injected [sdk] check (>= 29, >= 30), which lint cannot see.
    @SuppressLint("NewApi")
    @Suppress("DEPRECATION")
    @Synchronized
    fun save(ssid: String, pass: String, gw: String? = null): Boolean {
        val oldSsid = prefs.getString(K_SSID, null)
        val oldId = prefs.getInt(K_NETID, -1)
        val fp = fingerprint(ssid, pass)
        if (ssid.isEmpty()) {
            forget()
            return true
        }
        if (oldSsid == ssid && prefs.getString(K_FP, null) == fp && configExists(oldId, prefs.getString(K_MODE, null))) {
            // Same network again. A Master that does not know its address right now sends none: keep what we had.
            if (gw != null && prefs.getString(K_GW, null) != gw) prefs.edit().putString(K_GW, gw).apply()
            return true
        }

        // A changed ssid or password replaces the old entry; the same ssid keeps its slot only if remove succeeds.
        forget()
        var mode = MODE_NONE
        var netId = -1
        if (isDeviceOwner() || sdk < 29) {
            try {
                val id = wifi.addNetwork(config(ssid, pass))
                if (id >= 0) {
                    wifi.enableNetwork(id, false)
                    netId = id
                    mode = MODE_CONFIG
                }
            } catch (e: Exception) { Log.w(TAG, "addNetwork failed", e) }
        }
        if (mode == MODE_NONE && sdk >= 29) {
            // addNetwork answers -1 for an ordinary app on Android 10+: that is the suggestion path, not an error.
            try {
                val status = wifi.addNetworkSuggestions(listOf(suggestion(ssid, pass)))
                if (status == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS ||
                    status == WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_DUPLICATE) mode = MODE_SUGGEST
                else Log.w(TAG, "suggestion refused: $status")
            } catch (e: Exception) { Log.w(TAG, "suggestion failed", e) }
        }
        if (mode == MODE_NONE) return false
        // The suggestion's passphrase is kept (this app's private prefs) so the EXACT suggestion can be rebuilt to
        // remove it on API 29, where WifiManager.getNetworkSuggestions() does not exist.
        prefs.edit().putString(K_SSID, ssid).putString(K_FP, fp).putInt(K_NETID, netId).putString(K_MODE, mode)
            .putString(K_PASS, if (mode == MODE_SUGGEST) pass else null)
            .putString(K_GW, gw)
            .putBoolean(K_APPROVED, false).apply()
        return true
    }

    /**
     * A Master link just arrived FROM [remoteIp]. When that is the Master's address on the saved network (the "gw"
     * it sent), this Node is demonstrably on that network, which is the approval proof Android will not give an app
     * without location. Only the suggestion path needs it: a configured (Device Owner) network is never pending.
     */
    @Synchronized
    fun linkedFrom(remoteIp: String?) {
        if (remoteIp == null || prefs.getString(K_MODE, null) != MODE_SUGGEST || prefs.getBoolean(K_APPROVED, false)) return
        val gw = prefs.getString(K_GW, null) ?: return
        if (remoteIp == gw) prefs.edit().putBoolean(K_APPROVED, true).apply()
    }

    @SuppressLint("NewApi") // guarded by the injected [sdk], as in save()
    @Suppress("DEPRECATION")
    private fun forget() {
        val ssid = prefs.getString(K_SSID, null)
        val id = prefs.getInt(K_NETID, -1)
        val mode = prefs.getString(K_MODE, null)
        if (mode == MODE_CONFIG && id >= 0) try { wifi.removeNetwork(id) } catch (e: Exception) { Log.w(TAG, "removeNetwork", e) }
        if (mode == MODE_SUGGEST && ssid != null && sdk >= 29) try {
            // getNetworkSuggestions() is API 30: on 29 it is a NoSuchMethodError (an Error, not an Exception), so
            // rebuild the one suggestion this app added and remove that.
            val mine = if (sdk >= 30) wifi.networkSuggestions.filter { it.ssid == ssid }
                else listOf(suggestion(ssid, prefs.getString(K_PASS, "") ?: ""))
            wifi.removeNetworkSuggestions(mine)
        } catch (e: Exception) { Log.w(TAG, "removeSuggestions", e) }
        prefs.edit().remove(K_SSID).remove(K_FP).remove(K_NETID).remove(K_MODE).remove(K_APPROVED).remove(K_PASS).remove(K_GW).apply()
    }

    @Suppress("DEPRECATION")
    private fun configExists(netId: Int, mode: String?): Boolean = when (mode) {
        MODE_CONFIG -> try { wifi.configuredNetworks.orEmpty().any { it.networkId == netId } } catch (e: Exception) { true }
        MODE_SUGGEST -> true
        else -> false
    }

    /**
     * What status() reports. [ssid] is the hotspot being asked about (null: whatever was last saved). `saved` is
     * true only for that SSID, so a stale entry for an old hotspot never reads as ready.
     */
    fun state(ssid: String? = null): NetMessages.WifiStatus {
        refreshApproval()
        val saved = prefs.getString(K_SSID, null)
        val mode = prefs.getString(K_MODE, null)
        val matches = saved != null && (ssid == null || ssid == saved)
        return NetMessages.WifiStatus(
            saved = matches && mode != null && (mode == MODE_CONFIG || prefs.getBoolean(K_APPROVED, false)),
            approve = matches && mode == MODE_SUGGEST && !prefs.getBoolean(K_APPROVED, false),
            deviceOwner = isDeviceOwner(),
            doze = dozeExempt(),
        )
    }

    /**
     * The suggestion is added with app interaction NOT required, and Android only sends the post-connection
     * broadcast to apps that ask for interaction and hold location, so that broadcast never arrives here. Approval
     * is proved instead by being connected to the saved SSID right now (no location permission is requested; where
     * Android hides the SSID from this app it reads as null and the Node keeps saying "needs approval", which is
     * the safe way to be wrong).
     */
    fun refreshApproval() {
        if (prefs.getString(K_MODE, null) != MODE_SUGGEST || prefs.getBoolean(K_APPROVED, false)) return
        val saved = prefs.getString(K_SSID, null) ?: return
        val now = try { currentSsid()?.trim('"') } catch (e: Exception) { null }
        if (now != null && now == saved) prefs.edit().putBoolean(K_APPROVED, true).apply()
    }

    private fun dozeExempt(): Boolean = try {
        ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
    } catch (e: Exception) { false }

    /** Android tells an app when it first connects through its suggestion, which is the proof the parent allowed it. */
    fun watchApproval(): BroadcastReceiver? {
        if (sdk < 29) return null
        val rx = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { prefs.edit().putBoolean(K_APPROVED, true).apply() }
        }
        return try {
            ContextCompat.registerReceiver(ctx.applicationContext, rx,
                IntentFilter(WifiManager.ACTION_WIFI_NETWORK_SUGGESTION_POST_CONNECTION), ContextCompat.RECEIVER_NOT_EXPORTED)
            rx
        } catch (e: Exception) { Log.w(TAG, "no approval receiver", e); null }
    }

    @Suppress("DEPRECATION")
    private fun config(ssid: String, pass: String) = WifiConfiguration().apply {
        // The quotes matter: an unquoted SSID is read as hex and never joins.
        SSID = "\"$ssid\""
        if (pass.isEmpty()) allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
        else { preSharedKey = "\"$pass\""; allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK) }
        status = WifiConfiguration.Status.ENABLED
    }

    @androidx.annotation.RequiresApi(29)
    private fun suggestion(ssid: String, pass: String) = WifiNetworkSuggestion.Builder().apply {
        setSsid(ssid)
        if (pass.isNotEmpty()) setWpa2Passphrase(pass)
        setIsAppInteractionRequired(false)
    }.build()

    private fun fingerprint(ssid: String, pass: String): String =
        MessageDigest.getInstance("SHA-256").digest("$ssid\u0000$pass".toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        private const val TAG = "NickOS.Wifi"
        private const val K_SSID = "ssid"
        private const val K_FP = "fp"
        private const val K_NETID = "netId"
        private const val K_MODE = "mode"
        private const val K_APPROVED = "approved"
        private const val K_PASS = "sugpass"
        private const val K_GW = "gw"
        private const val MODE_NONE = "none"
        private const val MODE_CONFIG = "config"
        private const val MODE_SUGGEST = "suggest"
    }
}
