package com.nbaltas.nickos.master

import com.nbaltas.nickos.data.LinkedNode
import com.nbaltas.nickos.link.NetMessages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlinePolicyTest {
    private val now = 1_000_000L

    @Test
    fun `no lan address on the master wins over everything`() {
        assertEquals(Reason.MasterNotOnWifi, OnlinePolicy.reason(null, false, 0, now))
        assertEquals(Reason.MasterNotOnWifi, OnlinePolicy.reason(null, true, now - 5, now))
    }

    @Test
    fun `a node without the hotspot details is the second reason`() {
        assertEquals(Reason.NoHotspotDetails, OnlinePolicy.reason("192.168.43.1", false, now - 5, now))
        // never reported since linking counts as no evidence
        assertEquals(Reason.NoHotspotDetails, OnlinePolicy.reason("192.168.43.1", null, now - 5, now))
    }

    @Test
    fun `everything in place means looking, with how long ago it was seen`() {
        assertEquals(Reason.Looking(5_000), OnlinePolicy.reason("192.168.43.1", true, now - 5_000, now))
        assertEquals(Reason.Looking(null), OnlinePolicy.reason("192.168.43.1", true, 0, now))
        // a clock that went backwards never gives a negative age
        assertEquals(Reason.Looking(0), OnlinePolicy.reason("192.168.43.1", true, now + 10, now))
    }

    @Test
    fun `an old node without wifi1 is reason b even if it claims saved`() {
        val old = NodeNet(supportsWifi = false, wifi = NetMessages.WifiStatus(saved = true))
        assertEquals(false, OnlinePolicy.wifiSaved(old, true))
        assertEquals(Reason.NoHotspotDetails, OnlinePolicy.reason("192.168.43.1", OnlinePolicy.wifiSaved(old, true), now - 1, now))
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
}
