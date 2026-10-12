package com.nbaltas.nickos.master

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nbaltas.nickos.App
import com.nbaltas.nickos.data.Hotspot
import com.nbaltas.nickos.data.Store
import com.nbaltas.nickos.link.LanAddress
import com.nbaltas.nickos.link.NetMessages
import com.nbaltas.nickos.node.WifiJoin
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The pieces the car network leans on: the stored credentials, the wire field, address choice, and the join proof. Fake names only. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CarNetSupportTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val store: Store get() = ctx.let { (it as App).store }

    @Before fun clean() {
        ctx.getSharedPreferences("nickos_wifi", Context.MODE_PRIVATE).edit().clear().commit()
        store.setHotspot(null)
    }

    // ---- Store ----

    @Test fun `the passphrase is made once, is 16 letters and digits, and survives a new store`() {
        val a = store.carNetPass()
        assertEquals(16, a.length)
        assertTrue(a.all { it in 'a'..'z' || it in '0'..'9' })
        assertEquals(a, store.carNetPass())
        assertEquals(a, Store(ctx).carNetPass())
    }

    @Test fun `a reset keeps the passphrase so the saved nodes still match`() {
        val a = store.carNetPass()
        store.resetAll()
        assertEquals(a, store.carNetPass())
    }

    @Test fun `two made passphrases differ`() {
        assertFalse(Store.newCarNetPass() == Store.newCarNetPass())
    }

    @Test fun `the direct flag round trips through a fresh store`() {
        store.setHotspot(Hotspot("DIRECT-NK-NickOS-test", "testpassphrase01", direct = true))
        assertEquals(true, Store(ctx).hotspot.value?.direct)
        store.setHotspot(Hotspot("TypedCar", "typedpass1"))
        assertEquals(false, Store(ctx).hotspot.value?.direct)
    }

    @Test fun `an older stored hotspot without the flag reads as typed in`() {
        ctx.getSharedPreferences("nickos", Context.MODE_PRIVATE).edit()
            .putString("hotspot", JSONObject().put("ssid", "OldCar").put("pass", "oldpass123").toString()).commit()
        assertEquals(Hotspot("OldCar", "oldpass123", direct = false), Store(ctx).hotspot.value)
    }

    @Test fun `adopting the car network never overwrites a typed in hotspot`() {
        store.setHotspot(Hotspot("TypedCar", "typedpass1"))
        assertFalse(store.adoptDirectHotspot(Hotspot("DIRECT-NK-NickOS-test", "testpassphrase01")))
        assertEquals("TypedCar", store.hotspot.value?.ssid)
    }

    @Test fun `adopting the car network records it when there is none or it replaces an older one of ours`() {
        assertTrue(store.adoptDirectHotspot(Hotspot("DIRECT-NK-NickOS-test", "testpassphrase01")))
        assertEquals(Hotspot("DIRECT-NK-NickOS-test", "testpassphrase01", direct = true), store.hotspot.value)
        assertTrue(store.adoptDirectHotspot(Hotspot("DIRECT-NK-NickOS-test", "testpassphrase02")))
        assertEquals("testpassphrase02", store.hotspot.value?.pass)
    }

    @Test fun `a group finishing at the same moment as a typed hotspot cannot win both`() {
        val direct = Hotspot("DIRECT-NK-NickOS-test", "testpassphrase01")
        repeat(200) {
            store.setHotspot(null)
            val t = Thread { store.adoptDirectHotspot(direct) }
            t.start()
            store.setHotspot(Hotspot("TypedCar", "typedpass1"))
            t.join()
            val h = store.hotspot.value!!
            // Either order is legal, but the stored value is always one whole hotspot, never a mix.
            assertTrue(h == Hotspot("TypedCar", "typedpass1") || h == direct.copy(direct = true))
        }
    }

    // ---- wire ----

    @Test fun `the gateway rides along only when given`() {
        val h = Hotspot("DIRECT-NK-NickOS-test", "testpassphrase01", direct = true)
        assertEquals("192.168.49.1", NetMessages.decodeWifiCmd(JSONObject(NetMessages.hotspotCmd(h, "192.168.49.1").toString()))!!.gw)
        assertNull(NetMessages.decodeWifiCmd(JSONObject(NetMessages.hotspotCmd(h).toString()))!!.gw)
        assertFalse(NetMessages.hotspotCmd(h).has("gw"))
    }

    @Test fun `a forget command carries no gateway and a junk gateway is dropped not fatal`() {
        assertNull(NetMessages.decodeWifiCmd(JSONObject(NetMessages.hotspotCmd(null, "192.168.49.1").toString()))!!.gw)
        val junk = NetMessages.wifiCmd("Ok", "okpassword", "not an ip")
        val d = NetMessages.decodeWifiCmd(JSONObject(junk.toString()))!!
        assertEquals("Ok", d.ssid)
        assertNull(d.gw)
    }

    // ---- addresses ----

    @Test fun `the home wifi beats the direct group for the single address, and the direct group is still listed`() {
        val found = listOf("p2p-wlan0-0" to "192.168.49.1", "wlan0" to "192.168.1.20")
        assertEquals("192.168.1.20", LanAddress.choose(found, wifi = null))
        assertEquals(setOf("192.168.1.20", "192.168.49.1"), LanAddress.all(found).map { it.second }.toSet())
    }

    @Test fun `with only the direct group up it is the address`() {
        assertEquals("192.168.49.1", LanAddress.choose(listOf("rmnet0" to "100.64.1.2", "p2p-wlan0-0" to "192.168.49.1"), wifi = null))
        assertEquals("192.168.49.1", LanAddress.choose(listOf("p2p0" to "192.168.49.1"), wifi = null))
        assertTrue(LanAddress.isDirect("p2p-wlan0-0"))
        assertFalse(LanAddress.isDirect("wlan0"))
    }

    @Test fun `all drops public and repeated addresses`() {
        val found = listOf("rmnet0" to "100.64.1.2", "wlan0" to "10.1.2.3", "wlan1" to "10.1.2.3", "lo" to "127.0.0.1")
        assertEquals(listOf("10.1.2.3"), LanAddress.all(found).map { it.second })
    }

    @Test fun `the address on a node's own link wins, else the fallback`() {
        assertEquals("192.168.49.1", LanAddress.localFor("192.168.49.1", "192.168.1.20"))
        assertEquals("192.168.1.20", LanAddress.localFor(null, "192.168.1.20"))
        assertEquals("192.168.1.20", LanAddress.localFor("8.8.8.8", "192.168.1.20"))
    }

    // ---- the join proof ----

    private fun plain(): WifiJoin = WifiJoin(ctx, { false }, 34)

    @Test fun `a link arriving from the gateway proves the node is on the car network`() {
        val j = plain()
        j.save("DIRECT-NK-NickOS-test", "testpassphrase01", gw = "192.168.49.1")
        assertTrue(j.state("DIRECT-NK-NickOS-test").approve)
        j.linkedFrom("192.168.1.99")                          // the home LAN proves nothing
        assertTrue(j.state("DIRECT-NK-NickOS-test").approve)
        j.linkedFrom("192.168.49.1")
        val s = j.state("DIRECT-NK-NickOS-test")
        assertTrue(s.saved)
        assertFalse(s.approve)
    }

    @Test fun `no gateway sent means no proof, so a typed hotspot cannot be falsely approved`() {
        val j = plain()
        j.save("TypedCar", "typedpass1")
        j.linkedFrom("192.168.1.99")
        assertTrue(j.state("TypedCar").approve)
    }

    @Test fun `a new password forgets the proof`() {
        val j = plain()
        j.save("DIRECT-NK-NickOS-test", "testpassphrase01", gw = "192.168.49.1")
        j.linkedFrom("192.168.49.1")
        assertTrue(j.state("DIRECT-NK-NickOS-test").saved)
        j.save("DIRECT-NK-NickOS-test", "testpassphrase02", gw = "192.168.49.1")
        assertTrue(j.state("DIRECT-NK-NickOS-test").approve)
    }

    @Test fun `a null or unrelated remote address is ignored`() {
        val j = plain()
        j.save("DIRECT-NK-NickOS-test", "testpassphrase01", gw = "192.168.49.1")
        j.linkedFrom(null)
        assertTrue(j.state("DIRECT-NK-NickOS-test").approve)
    }
}
