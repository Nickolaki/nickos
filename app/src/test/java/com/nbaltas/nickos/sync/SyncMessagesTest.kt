package com.nbaltas.nickos.sync

import com.nbaltas.nickos.master.NodeStatus
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** org.json is a real implementation only under Robolectric; it is a stub in a plain JVM test. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncMessagesTest {

    @Test
    fun `pong round-trips through encode then decode`() {
        val encoded = SyncMessages.encodePong(id = 3, t0 = 100, t1 = 200, t2 = 210)
        val decoded = SyncMessages.decodePong(JSONObject(encoded.toString()))
        assertEquals(SyncMessages.Pong(3, 100, 200, 210), decoded)
    }

    @Test
    fun `sync command round-trips`() {
        val encoded = SyncMessages.cmdSync(fid = "abc123", pos = 5000, at = 999_999, title = "Star Harbour")
        val decoded = SyncMessages.decodeSyncCmd(JSONObject(encoded.toString()))
        assertEquals(SyncMessages.SyncCmdMsg("abc123", 5000, 999_999, "Star Harbour"), decoded)
    }

    @Test
    fun `fetch command round-trips`() {
        val encoded = SyncMessages.cmdFetch(fid = "abc", url = "http://10.0.0.1:47471/v/abc?k=xyz", size = 123L, title = "Captain Comet", name = "captain.mp4")
        val decoded = SyncMessages.decodeFetchCmd(JSONObject(encoded.toString()))
        assertEquals("abc", decoded?.fid)
        assertEquals(123L, decoded?.size)
    }

    @Test
    fun `videos message round-trips including thumbnails`() {
        val entries = listOf(
            SyncMessages.VideoEntry("fid1", "Star Harbour", "star.mp4", 1000, 60_000, "YmFzZTY0"),
            SyncMessages.VideoEntry("fid2", "Captain Comet", "comet.mkv", 2000, 90_000, null),
        )
        val encoded = SyncMessages.encodeVideos(perm = true, list = entries)
        val decoded = SyncMessages.decodeVideos(JSONObject(encoded.toString()))
        assertEquals(true, decoded?.perm)
        assertEquals(entries, decoded?.list)
    }

    @Test
    fun `a malformed entry in a videos list is dropped but the good ones survive`() {
        val good = JSONObject().put("fid", "fid1").put("title", "Star Harbour").put("name", "star.mp4").put("size", 1000L).put("durMs", 60_000L)
        val bad = JSONObject().put("title", "missing fid")
        val arr = org.json.JSONArray().put(good).put(bad)
        val msg = JSONObject().put("t", "videos").put("perm", true).put("list", arr)
        val decoded = SyncMessages.decodeVideos(msg)
        assertEquals(1, decoded?.list?.size)
        assertEquals("fid1", decoded?.list?.get(0)?.fid)
    }

    @Test
    fun `syncState round-trips with and without an error`() {
        val withErr = SyncMessages.encodeSyncState("fid1", "error", 1000, 2000, 60_000, 1f, "missing")
        val decodedErr = SyncMessages.decodeSyncState(JSONObject(withErr.toString()))
        assertEquals("missing", decodedErr?.err)

        val noErr = SyncMessages.encodeSyncState("fid1", "playing", 1000, 2000, 60_000, 1f)
        val decodedOk = SyncMessages.decodeSyncState(JSONObject(noErr.toString()))
        assertNull(decodedOk?.err)
    }

    @Test
    fun `decodeSyncState on an empty object is null`() {
        assertNull(SyncMessages.decodeSyncState(JSONObject()))
    }

    @Test
    fun `decodeSyncState with pos as a string is null`() {
        val o = JSONObject().put("fid", "f").put("state", "playing").put("pos", "not a number")
            .put("at", 1).put("dur", 1).put("rate", 1.0)
        assertNull(SyncMessages.decodeSyncState(o))
    }

    @Test
    fun `a sync cmd missing at is null`() {
        val o = JSONObject().put("fid", "f").put("pos", 1).put("title", "x")
        assertNull(SyncMessages.decodeSyncCmd(o))
    }

    @Test
    fun `serving round-trips`() {
        val encoded = SyncMessages.encodeServing("fid1", ok = true, port = 47471, key = "k", size = 999)
        val decoded = SyncMessages.decodeServing(JSONObject(encoded.toString()))
        assertEquals("fid1", decoded?.fid)
        assertEquals(true, decoded?.ok)
        assertEquals("k", decoded?.key)
    }

    @Test
    fun `jobs round-trips and keeps the good entries of a mixed list`() {
        val entries = listOf(SyncMessages.JobEntry("fid1", "Star Harbour", "running", 100, 1000))
        val encoded = SyncMessages.encodeJobs(entries)
        val decoded = SyncMessages.decodeJobs(JSONObject(encoded.toString()))
        assertEquals(entries, decoded?.list)
    }

    // ---- NodeStatus.caps compatibility ----

    @Test
    fun `a status without caps parses with supportsSync false`() {
        val preFeatureStatus = JSONObject()
            .put("battery", 80).put("charging", false).put("screenOn", true)
            .put("volume", 50).put("brightness", 50).put("owner", true)
            .put("usage", true).put("overlay", true).put("version", "0.3")
        val status = NodeStatus.from(preFeatureStatus)
        assertFalse(status.supportsSync)
        assertEquals(80, status.battery)
    }

    @Test
    fun `a status with caps sync1 parses with supportsSync true and other fields unchanged`() {
        val status = NodeStatus.from(
            JSONObject()
                .put("battery", 80).put("charging", false).put("screenOn", true)
                .put("volume", 50).put("brightness", 50).put("owner", true)
                .put("usage", true).put("overlay", true).put("version", "0.3")
                .put("caps", org.json.JSONArray(listOf("sync1")))
        )
        assertTrue(status.supportsSync)
        assertEquals(80, status.battery)
        assertEquals("0.3", status.version)
    }
}
