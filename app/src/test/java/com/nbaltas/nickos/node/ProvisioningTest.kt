package com.nbaltas.nickos.node

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.Signature
import androidx.test.core.app.ApplicationProvider
import com.nbaltas.nickos.BuildConfig
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** What the kid tablet QR carries, and what the wizard's last step does. Only a real tablet proves the QR lands. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27])
class ProvisioningTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun signApp() {
        // Robolectric installs the app with no signing certificate; the payload needs one.
        shadowOf(ctx.packageManager).getInternalMutablePackageInfo(ctx.packageName).signatures = arrayOf(Signature("0102030405"))
    }

    private fun payload(ssid: String? = null, pass: String? = null, sig: String? = null) = JSONObject(Provisioning.qrPayload(ctx, ssid, pass, sig))

    @Test fun payloadHasExactlyTheDocumentedKeysAndValues() {
        val o = payload()
        val keys = o.keys().asSequence().toSet()
        assertEquals(
            setOf(
                "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME",
                "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION",
                "android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM",
                "android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED",
                "android.app.extra.PROVISIONING_SKIP_ENCRYPTION",
                "android.app.extra.PROVISIONING_SKIP_EDUCATION_SCREENS",
                "android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE",
            ),
            keys,
        )
        // package/fully.qualified.Class, not the short form adb accepts
        assertEquals("com.nbaltas.nickos/com.nbaltas.nickos.node.AdminReceiver", o.getString(Provisioning.K_ADMIN))
        assertEquals("${BuildConfig.UPDATE_BASE}/nickos/nickos-latest.apk", o.getString(Provisioning.K_DOWNLOAD))
        assertTrue(o.getString(Provisioning.K_CHECKSUM).matches(Regex("^[A-Za-z0-9_-]{43}$")))
        assertTrue(o.getBoolean(Provisioning.K_KEEP_SYSTEM_APPS))
        assertTrue(o.getBoolean(Provisioning.K_SKIP_ENCRYPTION))
        assertTrue(o.getBoolean(Provisioning.K_SKIP_EDUCATION))
        assertEquals("""{"nickos.role":"node"}""", o.getJSONObject(Provisioning.K_EXTRAS).toString())
    }

    @Test fun payloadKeysAreTheLiteralsAndroidExpects() {
        assertEquals("android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME", Provisioning.K_ADMIN)
        assertEquals("android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION", Provisioning.K_DOWNLOAD)
        assertEquals("android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM", Provisioning.K_CHECKSUM)
        assertEquals("android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED", Provisioning.K_KEEP_SYSTEM_APPS)
        assertEquals("android.app.extra.PROVISIONING_SKIP_ENCRYPTION", Provisioning.K_SKIP_ENCRYPTION)
        assertEquals("android.app.extra.PROVISIONING_SKIP_EDUCATION_SCREENS", Provisioning.K_SKIP_EDUCATION)
        assertEquals("android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE", Provisioning.K_EXTRAS)
        assertEquals("android.app.extra.PROVISIONING_WIFI_SSID", Provisioning.K_WIFI_SSID)
        assertEquals("android.app.extra.PROVISIONING_WIFI_PASSWORD", Provisioning.K_WIFI_PASSWORD)
        assertEquals("android.app.extra.PROVISIONING_WIFI_SECURITY_TYPE", Provisioning.K_WIFI_SECURITY)
        // and they match the platform constants where the platform has them
        assertEquals(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, Provisioning.K_ADMIN)
        assertEquals(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_ENCRYPTION, Provisioning.K_SKIP_ENCRYPTION)
        assertEquals(DevicePolicyManager.EXTRA_PROVISIONING_WIFI_SSID, Provisioning.K_WIFI_SSID)
    }

    @Test fun serverSignatureIsUsedWhenItIsValid() {
        val good = "x".repeat(43)
        assertEquals(good, payload(sig = good).getString(Provisioning.K_CHECKSUM))
    }

    @Test fun badServerSignatureFallsBackToThisTablet() {
        val local = payload().getString(Provisioning.K_CHECKSUM)
        assertEquals(local, payload(sig = "bad!").getString(Provisioning.K_CHECKSUM))
        assertEquals(local, payload(sig = "x".repeat(44)).getString(Provisioning.K_CHECKSUM))
        assertEquals(local, payload(sig = "").getString(Provisioning.K_CHECKSUM))
    }

    @Test fun wifiIsAddedWithItsSecurity() {
        val o = payload("Home", "pw")
        assertEquals("Home", o.getString(Provisioning.K_WIFI_SSID))
        assertEquals("pw", o.getString(Provisioning.K_WIFI_PASSWORD))
        assertEquals("WPA", o.getString(Provisioning.K_WIFI_SECURITY))
        val open = payload("Cafe", "")
        assertEquals("Cafe", open.getString(Provisioning.K_WIFI_SSID))
        assertEquals("NONE", open.getString(Provisioning.K_WIFI_SECURITY))
        assertFalse(open.has(Provisioning.K_WIFI_PASSWORD))
    }

    private fun preferredHome(): List<ComponentName> {
        val filters = mutableListOf<IntentFilter>()
        val acts = mutableListOf<ComponentName>()
        shadowOf(ctx.packageManager).getPersistentPreferredActivities(filters, acts, null)
        return acts
    }

    @Test fun lastWizardStepMakesNickOsTheHomeScreenWhenDeviceOwner() {
        val dpm = ctx.getSystemService(DevicePolicyManager::class.java)
        shadowOf(dpm).setDeviceOwner(ComponentName(ctx, AdminReceiver::class.java))
        val a = Robolectric.buildActivity(PolicyComplianceActivity::class.java, Intent()).create()
        assertEquals(Activity.RESULT_OK, shadowOf(a.get()).resultCode)
        assertTrue(a.get().isFinishing)
        assertTrue("NickOS was not set as the persistent home screen", preferredHome().contains(ComponentName(ctx, "com.nbaltas.nickos.Home")))
    }

    @Test fun lastWizardStepDoesNotThrowOrSetHomeWhenNotOwner() {
        val a = Robolectric.buildActivity(PolicyComplianceActivity::class.java, Intent()).create()
        assertEquals(Activity.RESULT_OK, shadowOf(a.get()).resultCode)
        assertTrue(a.get().isFinishing)
        assertTrue(preferredHome().isEmpty())
        assertFalse(NodeDevice.makeHome(ctx))
    }

    @Test fun homeFilterIsHomeAndDefault() {
        val f = NodeDevice.homeFilter()
        assertTrue(f.hasAction(Intent.ACTION_MAIN))
        assertTrue(f.hasCategory(Intent.CATEGORY_HOME))
        assertTrue(f.hasCategory(Intent.CATEGORY_DEFAULT))
    }
}
