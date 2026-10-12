package com.nbaltas.nickos.master

import com.nbaltas.nickos.data.LinkedNode
import com.nbaltas.nickos.link.NetMessages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlinePolicyTest {
    private val now = 1_000_000L
    private val up = CarNetState.On("DIRECT-NK-NickOS-test", "192.168.49.1")

    /** A typed-in hotspot: the old four reasons apply, the hosted network is not part of the picture. */
    private fun manual(lan: String?, saved: Boolean?, seen: Long) = OnlinePolicy.reason(CarNetState.Off(Why.MANUAL), true, lan, saved, seen, now)
    private fun hosted(saved: Boolean?, seen: Long, car: CarNetState = up) = OnlinePolicy.reason(car, false, null, saved, seen, now)

    @Test
    fun `no lan address on the master wins over everything when a hotspot was typed in`() {
        assertEquals(Reason.MasterNotOnWifi, manual(null, false, 0))
        assertEquals(Reason.MasterNotOnWifi, manual(null, true, now - 5))
    }

    @Test
    fun `a node without the hotspot details is the second reason`() {
        assertEquals(Reason.NoHotspotDetails, manual("192.168.43.1", false, now - 5))
        // never reported since linking counts as no evidence
        assertEquals(Reason.NoHotspotDetails, manual("192.168.43.1", null, now - 5))
    }

    @Test
    fun `everything in place means looking, with how long ago it was seen`() {
        assertEquals(Reason.Looking(5_000), manual("192.168.43.1", true, now - 5_000))
        assertEquals(Reason.Looking(null), manual("192.168.43.1", true, 0))
        // a clock that went backwards never gives a negative age
        assertEquals(Reason.Looking(0), manual("192.168.43.1", true, now + 10))
    }

    @Test
    fun `an old node without wifi1 is reason b even if it claims saved`() {
        val old = NodeNet(supportsWifi = false, wifi = NetMessages.WifiStatus(saved = true))
        assertEquals(false, OnlinePolicy.wifiSaved(old, true))
        assertEquals(Reason.NoHotspotDetails, manual("192.168.43.1", OnlinePolicy.wifiSaved(old, true), now - 1))
    }

    @Test
    fun `the live report wins over the remembered one, and memory fills in when offline`() {
        val saved = NodeNet(true, NetMessages.WifiStatus(saved = true))
        val notSaved = NodeNet(true, NetMessages.WifiStatus(saved = false))
        assertEquals(true, OnlinePolicy.wifiSaved(saved, false))
        assertEquals(false, OnlinePolicy.wifiSaved(notSaved, true))
        assertEquals(true, OnlinePolicy.wifiSaved(null, true))
        assertNull(OnlinePolicy.wifiSaved(null, null))
    }

    @Test
    fun `offline lists only the linked tablets that are not online, in order`() {
        fun node(id: String) = LinkedNode(id, id, 0, "initial", "t")
        val nodes = listOf(node("a"), node("b"), node("c"))
        assertEquals(listOf("a", "c"), OnlinePolicy.offline(nodes, setOf("b")).map { it.id })
        assertTrue(OnlinePolicy.offline(nodes, setOf("a", "b", "c")).isEmpty())
    }

    @Test
    fun `a master that is not on wifi is fine when it hosts the car network`() {
        // The Master hosts the network, so having no WiFi LAN address is the normal case, not a reason.
        assertEquals(Reason.Looking(5_000), hosted(true, now - 5_000))
    }

    @Test
    fun `a car network that is down is the first reason, whatever the node says`() {
        assertEquals(Reason.CarNetDown(Why.WIFI_OFF), hosted(true, now - 5, CarNetState.Off(Why.WIFI_OFF)))
        assertEquals(Reason.CarNetDown(Why.NO_PERMISSION), hosted(false, 0, CarNetState.Off(Why.NO_PERMISSION)))
        assertEquals(Reason.CarNetDown(Why.TETHERING_ON), hosted(null, 0, CarNetState.Off(Why.TETHERING_ON)))
    }

    @Test
    fun `starting is not an error until it has been starting too long`() {
        val starting = CarNetState.Starting
        assertEquals(Reason.Looking(null), OnlinePolicy.reason(starting, false, null, true, 0, now, startingSince = now - 5_000))
        assertEquals(Reason.CarNetDown(Why.FAILED), OnlinePolicy.reason(starting, false, null, true, 0, now, startingSince = now - 25_000))
    }

    @Test
    fun `a node that never got the details is the next reason on the hosted network`() {
        assertEquals(Reason.NoHotspotDetails, hosted(false, now - 5))
        assertEquals(Reason.NoHotspotDetails, hosted(null, now - 5))
    }

    @Test
    fun `a typed in hotspot hides the hosted network's own state`() {
        // The parent chose the manual hotspot: the group being off is deliberate and must not read as a fault.
        assertEquals(Reason.Looking(null), OnlinePolicy.reason(CarNetState.Off(Why.WIFI_OFF), true, "192.168.43.1", true, 0, now))
    }
}
