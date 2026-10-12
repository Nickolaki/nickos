package com.nbaltas.nickos.node

import android.content.Context
import android.net.wifi.WifiManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Fake credentials only: nothing here is a real network. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WifiJoinTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    @Suppress("DEPRECATION")
    private val wm: WifiManager get() = ctx.getSystemService(Context.WIFI_SERVICE) as WifiManager

    @Before fun clean() {
        ctx.getSharedPreferences("nickos_wifi", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun owner(sdk: Int = 34) = WifiJoin(ctx, { true }, sdk)
    private fun plain(sdk: Int = 34) = WifiJoin(ctx, { false }, sdk)

    @Suppress("DEPRECATION")
    private fun configured() = wm.configuredNetworks.orEmpty()

    @Test
    fun `device owner saves the network with a quoted ssid and reports saved`() {
        val j = owner()
        assertTrue(j.save("TestCar", "testpass1"))
        val c = configured().single()
        assertEquals("\"TestCar\"", c.SSID)
        assertEquals("\"testpass1\"", c.preSharedKey)
        val s = j.state("TestCar")
        assertTrue(s.saved)
        assertFalse(s.approve)
        assertTrue(s.deviceOwner)
    }

    @Test
    fun `saving the same details again changes nothing`() {
        val j = owner()
        j.save("TestCar", "testpass1")
        val id = configured().single().networkId
        assertTrue(j.save("TestCar", "testpass1"))   // the Master resends on every connect
        assertEquals(listOf(id), configured().map { it.networkId })
    }

    @Test
    fun `a changed password replaces the old entry rather than adding a second`() {
        val j = owner()
        j.save("TestCar", "testpass1")
        j.save("TestCar", "testpass2")
        val c = configured().single()
        assertEquals("\"testpass2\"", c.preSharedKey)
    }

    @Test
    fun `a changed ssid replaces the old one`() {
        val j = owner()
        j.save("TestCar", "testpass1")
        j.save("OtherCar", "testpass1")
        assertEquals(listOf("\"OtherCar\""), configured().map { it.SSID })
        assertFalse(j.state("TestCar").saved)     // a stale entry never reads as ready for the old hotspot
        assertTrue(j.state("OtherCar").saved)
    }

    @Test
    fun `an empty ssid forgets the network`() {
        val j = owner()
        j.save("TestCar", "testpass1")
        assertTrue(j.save("", ""))
        assertTrue(configured().isEmpty())
        assertFalse(j.state().saved)
    }

    @Test
    fun `an open hotspot is saved without a key`() {
        val j = owner()
        assertTrue(j.save("OpenCar", ""))
        assertEquals("\"OpenCar\"", configured().single().SSID)
    }

    @Test
    fun `without device owner on android 10 plus one suggestion is added and approval is asked for`() {
        val j = plain(34)
        assertTrue(j.save("TestCar", "testpass1"))
        assertTrue(configured().isEmpty())                 // addNetwork is not even attempted
        assertEquals(1, wm.networkSuggestions.size)
        val s = j.state("TestCar")
        assertFalse(s.saved)                               // not usable until the parent taps once
        assertTrue(s.approve)
        assertFalse(s.deviceOwner)
    }

    @Test
    fun `saving a suggestion twice does not stack suggestions`() {
        val j = plain(34)
        j.save("TestCar", "testpass1")
        j.save("TestCar", "testpass1")
        assertEquals(1, wm.networkSuggestions.size)
    }

    @Test
    fun `an old android without device owner can still add a config`() {
        val j = plain(28)
        assertTrue(j.save("TestCar", "testpass1"))
        assertEquals(1, configured().size)
        assertTrue(j.state("TestCar").saved)
    }

    @Test
    fun `ensure on turns wifi on for the device owner only`() {
        @Suppress("DEPRECATION") wm.setWifiEnabled(false)
        assertFalse(plain().ensureOn())
        assertFalse(wm.isWifiEnabled)
        assertTrue(owner().ensureOn())
        assertTrue(wm.isWifiEnabled)
        assertFalse(owner().ensureOn())                    // already on: nothing to do
    }

    @Test
    fun `state with nothing saved is quiet`() {
        val s = owner().state("TestCar")
        assertFalse(s.saved)
        assertFalse(s.approve)
        assertNull(null)
    }

    @Test
    fun `the approval receiver exists on android 10 plus and not before`() {
        assertTrue(plain(34).watchApproval() != null)
        assertNull(plain(28).watchApproval())
    }

    @Test
    fun `approval is detected by being connected to the saved ssid, with no broadcast`() {
        var on: String? = null
        val j = WifiJoin(ctx, { false }, 34, currentSsid = { on })
        j.save("TestCar", "testpass1")
        assertTrue(j.state("TestCar").approve)
        on = "\"SomeoneElse\""
        assertTrue(j.state("TestCar").approve)            // another network proves nothing
        on = null                                          // SSID hidden from this app: stay on "needs approval"
        assertTrue(j.state("TestCar").approve)
        on = "\"TestCar\""
        val s = j.state("TestCar")
        assertTrue(s.saved)
        assertFalse(s.approve)
        on = null
        assertTrue(j.state("TestCar").saved)               // approval sticks once seen
    }

    @Test
    fun `a new password asks for approval again`() {
        var on: String? = "TestCar"
        val j = WifiJoin(ctx, { false }, 34, currentSsid = { on })
        j.save("TestCar", "testpass1")
        assertTrue(j.state("TestCar").saved)
        on = null
        j.save("TestCar", "testpass2")
        assertTrue(j.state("TestCar").approve)
    }
}
