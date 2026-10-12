package com.nbaltas.nickos.node

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
    @Suppress("DEPRECATION")
    @Synchronized
    fun save(ssid: String, pass: String): Boolean {
        val oldSsid = prefs.getString(K_SSID, null)
        val oldId = prefs.getInt(K_NETID, -1)
        val fp = fingerprint(ssid, pass)
        if (ssid.isEmpty()) {
            forget()
            return true
        }
        if (oldSsid == ssid && prefs.getString(K_FP, null) == fp && configExists(oldId, prefs.getString(K_MODE, null))) return true

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
        prefs.edit().putString(K_SSID, ssid).putString(K_FP, fp).putInt(K_NETID, netId).putString(K_MODE, mode)
            .putBoolean(K_APPROVED, false).apply()
        return true
    }

    @Suppress("DEPRECATION")
    private fun forget() {
        val ssid = prefs.getString(K_SSID, null)
        val id = prefs.getInt(K_NETID, -1)
        val mode = prefs.getString(K_MODE, null)
        if (mode == MODE_CONFIG && id >= 0) try { wifi.removeNetwork(id) } catch (e: Exception) { Log.w(TAG, "removeNetwork", e) }
        if (mode == MODE_SUGGEST && ssid != null && sdk >= 29) try {
            wifi.removeNetworkSuggestions(wifi.networkSuggestions.filter { it.ssid == ssid })
        } catch (e: Exception) { Log.w(TAG, "removeSuggestions", e) }
        prefs.edit().remove(K_SSID).remove(K_FP).remove(K_NETID).remove(K_MODE).remove(K_APPROVED).apply()
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
        private const val MODE_NONE = "none"
        private const val MODE_CONFIG = "config"
        private const val MODE_SUGGEST = "suggest"
    }
}
