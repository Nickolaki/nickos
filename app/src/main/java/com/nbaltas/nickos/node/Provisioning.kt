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

    // Payload keys as literals, not DevicePolicyManager.EXTRA_*: several of those constants post-date API 24.
    // ProvisioningTest pins every value.
    const val K_ADMIN = "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME"
    const val K_DOWNLOAD = "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION"
    const val K_CHECKSUM = "android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM"
    const val K_KEEP_SYSTEM_APPS = "android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED"
    const val K_SKIP_ENCRYPTION = "android.app.extra.PROVISIONING_SKIP_ENCRYPTION"
    const val K_SKIP_EDUCATION = "android.app.extra.PROVISIONING_SKIP_EDUCATION_SCREENS"
    const val K_EXTRAS = "android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE"
    const val K_WIFI_SSID = "android.app.extra.PROVISIONING_WIFI_SSID"
    const val K_WIFI_PASSWORD = "android.app.extra.PROVISIONING_WIFI_PASSWORD"
    const val K_WIFI_SECURITY = "android.app.extra.PROVISIONING_WIFI_SECURITY_TYPE"

    private val CHECKSUM_FORMAT = Regex("^[A-Za-z0-9_-]{43}$")

    /** True for a base64url SHA-256 without padding: the only shape Android accepts as a checksum. */
    fun validChecksum(s: String?) = s != null && CHECKSUM_FORMAT.matches(s)

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

    /**
     * The QR contents. [signature] is the release certificate checksum the update server publishes (the APK
     * the new tablet will really download). Anything that is not a valid checksum falls back to this tablet's own.
     */
    fun qrPayload(ctx: Context, wifiSsid: String?, wifiPassword: String?, signature: String? = null): String {
        val o = JSONObject()
            .put(K_ADMIN, "${ctx.packageName}/${AdminReceiver::class.java.name}")
            .put(K_DOWNLOAD, "${BuildConfig.UPDATE_BASE}/nickos/nickos-latest.apk")
            .put(K_CHECKSUM, if (validChecksum(signature)) signature else signatureChecksum(ctx))
            .put(K_KEEP_SYSTEM_APPS, true)
            .put(K_SKIP_ENCRYPTION, true)
            .put(K_SKIP_EDUCATION, true)
            .put(K_EXTRAS, JSONObject().put(EXTRA_ROLE, "node"))
        if (!wifiSsid.isNullOrBlank()) {
            o.put(K_WIFI_SSID, wifiSsid)
            if (!wifiPassword.isNullOrEmpty()) {
                o.put(K_WIFI_PASSWORD, wifiPassword)
                o.put(K_WIFI_SECURITY, "WPA")
            } else o.put(K_WIFI_SECURITY, "NONE")
        }
        return o.toString()
    }

    /** Set when this tablet was provisioned from a NickOS QR: setup skips straight to the Node steps. */
    fun provisionedAsNode(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("node", false)

    /** A reset puts the tablet back to normal: it is no longer a QR kid tablet in the making, so it is not offered as a home screen. */
    fun forget(ctx: Context) { ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("node").commit() }

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

/**
 * Last step of QR setup. It already runs as Device Owner, so NickOS becomes the home screen here: the
 * wizard's final "Done" then lands on NickOS's name screen instead of the stock launcher.
 */
class PolicyComplianceActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        Provisioning.remember(this, intent.getParcelableExtra(DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE))
        applyHomeAlias(this)
        NodeDevice.makeHome(this)
        setResult(RESULT_OK)
        finish()
    }
}
