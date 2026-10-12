package com.nbaltas.nickos.master

import androidx.test.core.app.ApplicationProvider
import com.nbaltas.nickos.App
import com.nbaltas.nickos.data.Hotspot
import com.nbaltas.nickos.data.LinkedNode
import com.nbaltas.nickos.data.Store
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StoreHotspotTest {
    private val store: Store get() = ApplicationProvider.getApplicationContext<App>().store

    @Test
    fun `hotspot round trips and survives a fresh store`() {
        store.setHotspot(null)
        assertNull(store.hotspot.value)
        store.setHotspot(Hotspot("TestCar", "testpass1"))
        assertEquals(Hotspot("TestCar", "testpass1"), store.hotspot.value)
        val again = Store(ApplicationProvider.getApplicationContext())
        assertEquals(Hotspot("TestCar", "testpass1"), again.hotspot.value)
        store.setHotspot(null)
        assertNull(Store(ApplicationProvider.getApplicationContext()).hotspot.value)
    }

    @Test
    fun `an empty ssid is no hotspot`() {
        store.setHotspot(Hotspot("", "x"))
        assertNull(store.hotspot.value)
    }

    @Test
    fun `wifiSaved round trips as true false and unknown`() {
        fun node(v: Boolean?) = LinkedNode("0123456789ab", "N", 0, "initial", "tok", wifiSaved = v)
        for (v in listOf(true, false, null)) {
            assertEquals(v, LinkedNode.from(org.json.JSONObject(node(v).toJson().toString())).wifiSaved)
        }
    }

    @Test
    fun `an older saved node without the field reads as unknown`() {
        val o = node().toJson().apply { remove("wifiSaved") }
        assertNull(LinkedNode.from(org.json.JSONObject(o.toString())).wifiSaved)
    }

    @Test
    fun `a new network name clears every saved flag but a new passphrase does not`() {
        store.upsertNode(LinkedNode("0123456789ab", "N1", 0, "initial", "t1", wifiSaved = true))
        store.upsertNode(LinkedNode("0123456789cd", "N2", 0, "initial", "t2", wifiSaved = true))
        store.setHotspot(Hotspot("A", "p1", true))
        store.setHotspot(Hotspot("A", "p1b", true))
        // The first save (no network before) may clear; what matters is that a same-name re-save leaves a flag alone.
        store.updateNode("0123456789ab") { it.copy(wifiSaved = true) }
        store.updateNode("0123456789cd") { it.copy(wifiSaved = true) }
        store.setHotspot(Hotspot("A", "p2", true))
        assertEquals(listOf<Boolean?>(true, true), store.nodes.value.map { it.wifiSaved })
        store.setHotspot(Hotspot("B", "p2", true))
        assertEquals(listOf<Boolean?>(null, null), store.nodes.value.map { it.wifiSaved })
        assertEquals(listOf<Boolean?>(null, null), Store(ApplicationProvider.getApplicationContext()).nodes.value.map { it.wifiSaved })
    }

    private fun node() =LinkedNode("0123456789ab", "N", 0, "initial", "tok")
}
