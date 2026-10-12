package com.nbaltas.nickos

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/**
 * Android 7.0 has no ISRG Root X1 and its DST cross-sign expired, so Let's Encrypt fails there. The app carries the
 * roots itself, and keeps cleartext on (a config replaces usesCleartextTraffic; the LAN link is cleartext).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrustAnchorsTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun subject(raw: Int): String {
        val cert = ctx.resources.openRawResource(raw).use { CertificateFactory.getInstance("X.509").generateCertificate(it) }
        return (cert as X509Certificate).subjectX500Principal.name
    }

    @Test
    fun `the bundled roots parse and are the ones we expect`() {
        assertTrue(subject(R.raw.isrg_root_x1), subject(R.raw.isrg_root_x1).contains("ISRG Root X1"))
        assertTrue(subject(R.raw.gts_root_r1), subject(R.raw.gts_root_r1).contains("GTS Root R1"))
        assertTrue(subject(R.raw.gts_root_r4), subject(R.raw.gts_root_r4).contains("GTS Root R4"))
    }

    @Test
    fun `the network security config trusts them and still allows cleartext`() {
        val x = ctx.resources.getXml(R.xml.network_security_config)
        var baseConfig = false
        var cleartext: String? = null
        var certificates = 0
        while (x.next() != XmlPullParser.END_DOCUMENT) {
            if (x.eventType != XmlPullParser.START_TAG) continue
            when (x.name) {
                "base-config" -> { baseConfig = true; cleartext = x.getAttributeValue(null, "cleartextTrafficPermitted") }
                "certificates" -> certificates++
            }
        }
        assertTrue(baseConfig)
        assertEquals("true", cleartext)
        assertEquals(4, certificates)
    }
}
