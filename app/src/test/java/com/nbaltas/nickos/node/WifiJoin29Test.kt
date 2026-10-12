package com.nbaltas.nickos.node

import android.content.Context
import android.net.wifi.WifiManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Android 10 (API 29) has no WifiManager.getNetworkSuggestions(). Calling it there is a NoSuchMethodError, which is
 * an Error and sails past catch (Exception), so replacing or clearing a suggestion must not touch it on 29.
 * Robolectric runs this against the real API 29 jar, where the method does not exist.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class WifiJoin29Test {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun clean() { ctx.getSharedPreferences("nickos_wifi", Context.MODE_PRIVATE).edit().clear().commit() }

    private fun join() = WifiJoin(ctx, { false }, 29)

    @Test
    fun `replacing a suggestion on api 29 does not crash and leaves one`() {
        val j = join()
        assertTrue(j.save("TestCar", "testpass1"))
        assertTrue(j.save("TestCar", "testpass2"))
        assertTrue(j.save("OtherCar", "testpass3"))
        assertTrue(j.state("OtherCar").approve)
    }

    @Test
    fun `clearing the hotspot on api 29 does not crash`() {
        val j = join()
        j.save("TestCar", "testpass1")
        assertTrue(j.save("", ""))
        assertEquals(false, j.state("TestCar").approve)
    }
}
