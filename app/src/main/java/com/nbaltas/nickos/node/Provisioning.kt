package com.nbaltas.nickos.node

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.util.Base64
import com.nbaltas.nickos.BuildConfig
import org.json.JSONObject
import java.security.MessageDigest

/**
 * QR "set up this tablet" provisioning (Android's own zero-adb Device Owner setup): on a new or
 * factory-reset tablet, tap the welcome screen 6 times and scan the Master's QR. Android
 * downloads NickOS from the public update track and makes it Device Owner, so the kid lock is on
 * from the first boot.
 */
object Provisioning {
    const val EXTRA_ROLE = "nickos.role"
    private const val PREFS = "nickos-provisioning"

    /** base64url SHA-256 of this APK's signing certificate. Master and Node share the key. */
    fun signatureChecksum(ctx: Context): String {
        val pm = ctx.packageManager
        val cert = if (Build.VERSION.SDK_INT >= 28) {
            pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo!!.apkContentsSigners[0]
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNATURES).signatures!![0]
        }
        val d = MessageDigest.getInstance("SHA-256").digest(cert.toByteArray())
        return Base64.encodeToString(d, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    fun qrPayload(ctx: Context, wifiSsid: String?, wifiPassword: String?): String {
        val o = JSONObject()
            .put(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, "${ctx.packageName}/${AdminReceiver::class.java.name}")
            .put(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION, "${BuildConfig.UPDATE_BASE}/nickos/nickos-latest.apk")
            .put(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM, signatureChecksum(ctx))
            .put(DevicePolicyManager.EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED, true)
            .put(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_ENCRYPTION, true)
            .put(DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE, JSONObject().put(EXTRA_ROLE, "node"))
        if (!wifiSsid.isNullOrBlank()) {
            o.put(DevicePolicyManager.EXTRA_PROVISIONING_WIFI_SSID, wifiSsid)
            if (!wifiPassword.isNullOrEmpty()) {
                o.put(DevicePolicyManager.EXTRA_PROVISIONING_WIFI_PASSWORD, wifiPassword)
                o.put(DevicePolicyManager.EXTRA_PROVISIONING_WIFI_SECURITY_TYPE, "WPA")
            } else o.put(DevicePolicyManager.EXTRA_PROVISIONING_WIFI_SECURITY_TYPE, "NONE")
        }
        return o.toString()
    }

    /** Set when this tablet was provisioned from a NickOS QR: setup skips straight to the Node steps. */
    fun provisionedAsNode(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("node", false)

    internal fun remember(ctx: Context, extras: PersistableBundle?) {
        if (extras?.getString(EXTRA_ROLE) == "node") ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("node", true).apply()
    }
}

/** Android 12+ asks the DPC which mode it wants during QR setup: always a fully managed device. */
class ProvisioningModeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        Provisioning.remember(this, intent.getParcelableExtra(DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE))
        setResult(RESULT_OK, Intent().putExtra(DevicePolicyManager.EXTRA_PROVISIONING_MODE, DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE))
        finish()
    }
}

/** Last step of QR setup: nothing to enforce up front, NickOS's own setup takes over after. */
class PolicyComplianceActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        Provisioning.remember(this, intent.getParcelableExtra(DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE))
        setResult(RESULT_OK)
        finish()
    }
}
