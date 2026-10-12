package com.nbaltas.nickos.node

import android.content.Context
import android.net.wifi.WifiManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Orbic Node is Android 12 and, with the QR setup gone, is not Device Owner: it joins the car network through
 * ONE suggestion and a single approval tap. Fake credentials only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class WifiJoin31Test {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    @Suppress("DEPRECATION")
    private val wm: WifiManager get() = ctx.getSystemService(Context.WIFI_SERVICE) as WifiManager

    @Before fun clean() { ctx.getSharedPreferences("nickos_wifi", Context.MODE_PRIVATE).edit().clear().commit() }

    private fun join() = WifiJoin(ctx, { false }, 31)

    @Test
    fun `a node that is not device owner adds one suggestion and asks for the one tap`() {
        val j = join()
        assertTrue(j.save("DIRECT-ab-Android_1234", "testpass1", "192.168.49.1"))
        assertEquals(1, wm.networkSuggestions.size)
        assertTrue(j.state("DIRECT-ab-Android_1234").approve)
        assertFalse(j.state("DIRECT-ab-Android_1234").saved)
    }

    @Test
    fun `a link from the group owner address is the approval proof`() {
        val j = join()
        j.save("DIRECT-ab-Android_1234", "testpass1", "192.168.49.1")
        j.linkedFrom("192.168.49.1")
        val s = j.state("DIRECT-ab-Android_1234")
        assertTrue(s.saved)
        assertFalse(s.approve)
    }

    @Test
    fun `a rotated network name swaps the suggestion for the new one`() {
        val j = join()
        j.save("DIRECT-ab-Android_1234", "testpass1", "192.168.49.1")
        assertTrue(j.save("DIRECT-cd-Android_9999", "testpass2", "192.168.49.1"))
        assertEquals(listOf("DIRECT-cd-Android_9999"), wm.networkSuggestions.map { it.ssid })
    }
}
