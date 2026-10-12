package com.nbaltas.nickos.cam

import com.nbaltas.nickos.master.NodeStatus
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CamMessagesTest {
    /** What the other tablet reads: the same message after a trip down the line. */
    private fun wire(o: JSONObject) = JSONObject(o.toString())

    private fun roundTrip(c: NodeCam) = CamMessages.decodeState(wire(CamMessages.encodeState(c)), receivedAt = 900)

    @Test
    fun `camStart round trips`() {
        val o = wire(CamMessages.cmdStart("high", 600_000))
        assertEquals("cmd", o.getString("t"))
        assertEquals("camStart", o.getString("c"))
        assertEquals(CamMessages.StartCmd("high", 600_000), CamMessages.decodeStart(o))
    }

    @Test
    fun `camStop is a bare command`() {
        val o = wire(CamMessages.cmdStop())
        assertEquals("cmd", o.getString("t"))
        assertEquals("camStop", o.getString("c"))
        assertEquals(2, o.length())
    }

    @Test
    fun `camStart with a missing or bad field decodes to null`() {
        assertNull(CamMessages.decodeStart(JSONObject().put("profile", "normal")))
        assertNull(CamMessages.decodeStart(JSONObject().put("maxMs", 600_000)))
        assertNull(CamMessages.decodeStart(JSONObject().put("profile", "normal").put("maxMs", "600000")))
        assertNull(CamMessages.decodeStart(JSONObject().put("profile", "normal").put("maxMs", 0)))
        assertNull(CamMessages.decodeStart(JSONObject().put("profile", "normal").put("maxMs", -5)))
        assertNull(CamMessages.decodeStart(JSONObject().put("profile", "cool").put("maxMs", 600_000)))
        assertNull(CamMessages.decodeStart(JSONObject().put("profile", 3).put("maxMs", 600_000)))
        assertNull(CamMessages.decodeStart(JSONObject()))
    }

    @Test
    fun `every camState variant round trips`() {
        val variants = listOf(
            NodeCam(NodeCam.OFF, now = 5),
            NodeCam(NodeCam.WARMING, port = 47472, key = "0123abcd", profile = "high", onSince = 4_000, maxMs = 600_000, now = 4_100),
            NodeCam(NodeCam.LIVE, port = 47472, key = "0123abcd", profile = "normal", onSince = 4_000, maxMs = 300_000, now = 9_000),
            NodeCam(NodeCam.LIVE, level = NodeCam.LEVEL_HOT, port = 51234, key = "k", profile = "cool", onSince = 1, maxMs = 3_600_000, now = 2),
            NodeCam(NodeCam.OFF, reason = NodeCam.TOO_HOT, level = NodeCam.LEVEL_TOO_HOT, retryAt = 130_000, now = 10_000),
        ) + listOf(
            NodeCam.NO_CAMERA, NodeCam.PERMISSION, NodeCam.BACKGROUND, NodeCam.BUSY, NodeCam.CAMERA, NodeCam.AUTO_OFF,
            NodeCam.LOW_BATTERY, NodeCam.IDLE, NodeCam.LINK, NodeCam.STOPPED,
        ).map { NodeCam(NodeCam.OFF, reason = it, maxMs = 600_000, now = 77) }
        for (c in variants) assertEquals(c.copy(receivedAt = 900), roundTrip(c))
    }

    @Test
    fun `camState names its type and keeps port and key apart`() {
        val o = wire(CamMessages.encodeState(NodeCam(NodeCam.LIVE, port = 47472, key = "abc", now = 1)))
        assertEquals("camState", o.getString("t"))
        assertEquals("mjpeg", o.getString("kind"))
        assertEquals(47472, o.getInt("port"))
        assertEquals("abc", o.getString("key"))
        assertFalse(o.has("url"))
        assertFalse(o.has("reason"))
        assertFalse(o.has("retryAt"))
    }

    @Test
    fun `a camState with a wrong typed field decodes to null`() {
        fun base() = wire(CamMessages.encodeState(NodeCam(NodeCam.LIVE, port = 47472, key = "abc", onSince = 1, maxMs = 2, now = 3)))
        assertNotNull(CamMessages.decodeState(base(), 0))
        assertNull(CamMessages.decodeState(base().put("port", "47472"), 0))
        assertNull(CamMessages.decodeState(base().put("port", 70_000), 0))
        assertNull(CamMessages.decodeState(base().put("port", -1), 0))
        assertNull(CamMessages.decodeState(base().put("key", 5), 0))
        assertNull(CamMessages.decodeState(base().put("maxMs", "soon"), 0))
        assertNull(CamMessages.decodeState(base().put("retryAt", 1.5), 0))
        assertNull(CamMessages.decodeState(base().put("reason", true), 0))
        assertNull(CamMessages.decodeState(base().put("state", "exploded"), 0))
        assertNull(CamMessages.decodeState(base().put("level", "lukewarm"), 0))
        assertNull(CamMessages.decodeState(base().put("state", 1), 0))
    }

    @Test
    fun `a camState missing what it must have decodes to null`() {
        for (key in listOf("state", "level", "kind", "profile")) {
            val o = wire(CamMessages.encodeState(NodeCam(NodeCam.OFF)))
            o.remove(key)
            assertNull(key, CamMessages.decodeState(o, 0))
        }
        assertNull(CamMessages.decodeState(JSONObject(), 0))
    }

    @Test
    fun `a later kind of stream still decodes, for the Master to leave alone`() {
        val c = CamMessages.decodeState(wire(CamMessages.encodeState(NodeCam(NodeCam.LIVE, port = 1, key = "k", kind = "h264"))), 0)
        assertEquals("h264", c?.kind)
        assertEquals(CamPlan.Want.NONE, CamPlan.want(true, true, true, true, c, false, 0))
    }

    @Test
    fun `an old Node without cam1 does not support cameras`() {
        val old = NodeStatus.from(JSONObject().put("battery", 80).put("caps", JSONArray(listOf("sync1"))))
        assertFalse(old.supportsCam)
        assertTrue(old.supportsSync)
    }

    @Test
    fun `a Node with cam1 supports cameras`() {
        val s = NodeStatus.from(JSONObject().put("caps", JSONArray(listOf("sync1", "cam1"))))
        assertTrue(s.supportsCam)
        assertTrue(s.supportsSync)
    }

    @Test
    fun `a status with no caps supports nothing new and keeps every other field`() {
        fun status() = JSONObject()
            .put("battery", 82).put("charging", true).put("screenOn", true).put("volume", 60).put("brightness", 75)
            .put("fg", "org.jellyfin.mobile").put("fgLabel", "Jellyfin").put("allowed", JSONArray(listOf("a", "b")))
            .put("lockedApp", "a").put("lockedBy", "Dad").put("bedtimeAt", 0).put("owner", true).put("usage", true)
            .put("overlay", true).put("jf", "org.jellyfin.mobile").put("names", JSONArray(listOf("Tab"))).put("version", "0.3")
        val plain = NodeStatus.from(status())
        val capable = NodeStatus.from(status().put("caps", JSONArray(listOf("sync1", "cam1"))))
        assertFalse(plain.supportsCam)
        assertTrue(plain.caps.isEmpty())
        assertEquals(82, plain.battery)
        assertEquals("Jellyfin", plain.fgLabel)
        assertEquals(listOf("a", "b"), plain.allowed)
        assertEquals(plain, capable.copy(caps = emptyList()))
    }

    @Test
    fun `this build tells a Master it has cam1`() {
        assertEquals("cam1", CamMessages.CAP)
    }
}
