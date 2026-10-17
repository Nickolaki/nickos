package com.nbaltas.nickos.sync

import com.nbaltas.nickos.master.NodeStatus
import com.nbaltas.nickos.master.SyncController
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
class StreamCodecTest {
    private val base = "http://192.168.1.5:8096"
    private val token = "sekrit-token"

    private val h264 = JfVideo("h264", "High", 8, 1920, 1080)
    private val hevc10 = JfVideo("hevc", "Main 10", 10, 1920, 1080)
    private val hevc10_4k = JfVideo("hevc", "Main 10", 10, 3840, 2160)
    private val hevc8 = JfVideo("hevc", "Main", 8, 1920, 1080)

    private val noMain10 = NodeDec(hevcMain = true, hevcMain10 = false, hevcW = 1920, hevcH = 1088, avcW = 1920, avcH = 1088, av1 = false, vp9 = true)
    private val withMain10 = noMain10.copy(hevcMain10 = true, hevcW = 4096, hevcH = 2160)
    private val smallAvc = noMain10.copy(avcW = 1280, avcH = 720)

    private fun url(v: JfVideo?, d: NodeDec?, node: String = "n1") = StreamCodec.urlFor(base, token, "ITEM1", v, d, node)

    // ---- parse ----

    @Test
    fun `a status dec round trips`() {
        val o = JSONObject().put(NodeDec.KEY, NodeDec.encode(withMain10))
        assertEquals(withMain10, NodeDec.decode(o))
    }

    @Test
    fun `a status without dec is unknown`() {
        assertNull(NodeDec.decode(JSONObject().put("volume", 50)))
        assertNull(NodeDec.decode(null))
        assertNull(NodeStatus.from(JSONObject().put("volume", 50)).dec)
    }

    @Test
    fun `the node status carries dec through`() {
        val o = JSONObject().put("volume", 50).put(NodeDec.KEY, NodeDec.encode(noMain10))
        assertEquals(noMain10, NodeStatus.from(o).dec)
    }

    @Test
    fun `the video stream is read from the first media source`() {
        val item = JSONObject("""{"MediaSources":[{"Id":"SRC1","MediaStreams":[
            {"Type":"Audio","Codec":"eac3"},
            {"Type":"Video","Codec":"hevc","Profile":"Main 10","BitDepth":10,"Width":3840,"Height":2160}]}]}""")
        assertEquals(JfVideo("hevc", "Main 10", 10, 3840, 2160, "SRC1"), JfVideo.from(item))
        assertNull(JfVideo.from(JSONObject("{}")))
        assertNull(JfVideo.from(JSONObject("""{"MediaSources":[{"MediaStreams":[{"Type":"Audio"}]}]}""")))
    }

    // ---- the choice ----

    @Test
    fun `h264 8 bit gets the file`() {
        assertEquals(JfStream.authedUrl(base, "ITEM1", token), url(h264, noMain10))
    }

    @Test
    fun `hevc 10 bit on a node without Main10 gets HLS`() {
        val u = url(hevc10, noMain10)
        assertTrue(u, u.contains("/Videos/ITEM1/master.m3u8?"))
        assertTrue(JfStream.isHls(u))
    }

    @Test
    fun `hevc 10 bit on a node with Main10 gets the file`() {
        assertEquals(JfStream.authedUrl(base, "ITEM1", token), url(hevc10, withMain10))
    }

    @Test
    fun `hevc 8 bit needs only Main`() {
        assertTrue(StreamCodec.canDecode(noMain10, hevc8))
        assertFalse(StreamCodec.canDecode(noMain10.copy(hevcMain = false), hevc8))
    }

    @Test
    fun `the profile text alone says 10 bit`() {
        assertFalse(StreamCodec.canDecode(noMain10, JfVideo("hevc", "Main 10", 0, 1920, 1080)))
    }

    @Test
    fun `4K on a node with max 1920 gets HLS`() {
        assertFalse(StreamCodec.canDecode(withMain10.copy(hevcW = 1920, hevcH = 1088), hevc10_4k))
        assertTrue(JfStream.isHls(url(JfVideo("h264", "High", 8, 3840, 2160), noMain10)))
        assertTrue(StreamCodec.canDecode(withMain10, hevc10_4k))
    }

    @Test
    fun `an unknown or old node gets the file`() {
        assertEquals(JfStream.authedUrl(base, "ITEM1", token), url(hevc10_4k, null))
        assertEquals(JfStream.authedUrl(base, "ITEM1", token), url(null, noMain10))
        assertTrue(StreamCodec.canDecode(noMain10, JfVideo(null, null, 0, 0, 0)))
    }

    @Test
    fun `av1 and vp9 follow the yes or no`() {
        assertFalse(StreamCodec.canDecode(noMain10, JfVideo("av1", null, 10, 1920, 1080)))
        assertTrue(StreamCodec.canDecode(noMain10.copy(av1 = true), JfVideo("av1", null, 10, 1920, 1080)))
        assertTrue(StreamCodec.canDecode(noMain10, JfVideo("vp9", null, 8, 1920, 1080)))
        assertFalse(StreamCodec.canDecode(noMain10.copy(vp9 = false), JfVideo("vp9", null, 8, 1920, 1080)))
    }

    // ---- the HLS address ----

    @Test
    fun `the HLS address has the agreed parameters and a device id per node`() {
        val a = url(hevc10, noMain10, "leo")
        assertTrue(a, a.startsWith("$base/Videos/ITEM1/master.m3u8?MediaSourceId=ITEM1&VideoCodec=h264&AudioCodec=aac&MaxWidth=1920&VideoBitrate=8000000&AudioBitrate=192000&TranscodingMaxAudioChannels=2&SegmentContainer=ts&api_key=$token"))
        assertTrue(a.endsWith("&DeviceId=nickos-leo"))
        assertTrue(url(hevc10, noMain10, "mia").endsWith("&DeviceId=nickos-mia"))
    }

    @Test
    fun `the HLS width is capped by the node's avc width`() {
        assertTrue(url(hevc10, smallAvc).contains("&MaxWidth=1280&"))
        assertTrue(url(hevc10, noMain10.copy(avcW = 3840, avcH = 2160)).contains("&MaxWidth=1920&"))
        assertTrue(url(hevc10, noMain10.copy(avcW = 0)).contains("&MaxWidth=1920&"))
    }

    @Test
    fun `the media source id is used when the server gave one`() {
        assertTrue(url(hevc10.copy(sourceId = "SRC9"), noMain10).contains("MediaSourceId=SRC9&"))
    }

    // ---- who is converted, and the line ----

    @Test
    fun `converted names the tablets that cannot decode`() {
        assertEquals(setOf("leo"), StreamCodec.converted(hevc10, mapOf("hailey" to withMain10, "leo" to noMain10, "old" to null)))
        assertTrue(StreamCodec.converted(null, mapOf("leo" to noMain10)).isEmpty())
    }

    @Test
    fun `the converted line`() {
        assertNull(StreamCodec.convertedLine(emptyList()))
        assertEquals("Leo's tablet gets a converted stream (it can't play this file directly)", StreamCodec.convertedLine(listOf("Leo")))
        assertEquals("Leo and Mia's tablets get a converted stream (they can't play this file directly)", StreamCodec.convertedLine(listOf("Leo", "Mia")))
    }

    // ---- the secret stays secret ----

    @Test
    fun `the token is not in anything printed or reported`() {
        val u = url(hevc10, noMain10)
        assertTrue(u.contains(token))
        assertFalse(JfStream.redact("fetching $u failed").contains(token))
        val s = SyncSession("jf:x:ITEM1", "T", 1000, setOf("n1"), 0, 0, true, u, null, mapOf("n1" to u))
        assertFalse(s.toString().contains(token))
        assertFalse(SyncCmd("jf:x:ITEM1", 0, 0, "T", u).toString().contains(token))
        assertFalse(NodeDec.encode(noMain10).toString().contains(token))
    }

    // ---- the drift policy per node ----

    @Test
    fun `a converted stream seeks later than a file`() {
        assertEquals(1500L, DriftPolicy.forUrl(url(hevc10, noMain10)).seekOverMs)
        assertEquals(400L, DriftPolicy.forUrl(url(h264, noMain10)).seekOverMs)
        assertEquals(400L, DriftPolicy.forUrl(null).seekOverMs)
        val p = DriftPolicy(1500)
        assertTrue(p.decide(900, PlayerState.READY, true, 10_000, 5000) is DriftAction.Rate)
        assertTrue(p.decide(2000, PlayerState.READY, true, 20_000, 5000) is DriftAction.Seek)
        assertTrue(DriftPolicy().decide(900, PlayerState.READY, true, 10_000, 5000) is DriftAction.Seek)
    }

    // ---- per node urls in the plan ----

    @Test
    fun `sendPlan gives each tablet its own address`() {
        val port = FakeMasterPort()
        val c = SyncController(port, clock = { 100_000L })
        port.add("hailey", "Hailey's Tablet", stream = true, sealed = true)
        port.add("leo", "Leo's Tablet", stream = true, sealed = true)
        for (id in listOf("hailey", "leo")) {
            c.onConnected(id); c.tick()
            val ping = port.cmds(id, "ping").last()
            c.onMessage(id, SyncMessages.encodePong(ping.getInt("id"), ping.getLong("t0"), ping.getLong("t0"), ping.getLong("t0")))
        }
        val file = JfStream.authedUrl(base, "ITEM1", token)
        val hls = url(hevc10, noMain10, "leo")
        c.start("jf:ab12cd34:ITEM1", "T", 600_000, setOf("hailey", "leo"), file, nodeUrls = mapOf("hailey" to file, "leo" to hls))

        fun last(id: String) = SyncMessages.decodeSyncCmd(port.cmds(id, "sync").last())!!
        assertEquals(file, last("hailey").url)
        assertEquals(hls, last("leo").url)
        // A resend (rejoin, resume, the minute re-anchor) sends the same per tablet addresses again.
        c.rejoin("leo")
        assertEquals(hls, last("leo").url)
        c.resume()
        assertEquals(file, last("hailey").url)
        assertEquals(hls, last("leo").url)
        // A converted stream starts later, to give the server time to begin.
        val s = c.session.value!!
        assertTrue(s.converted)
        assertEquals(SyncController.CONVERTED_LEAD_MS, s.anchorAt - 100_000L)
        assertNotNull(s.urlFor("leo"))
        // It is gone when the session stops.
        c.stop()
        assertNull(c.session.value)
    }

    @Test
    fun `an all file session keeps the stream lead`() {
        val port = FakeMasterPort()
        val c = SyncController(port, clock = { 100_000L })
        port.add("hailey", "Hailey's Tablet", stream = true, sealed = true)
        c.onConnected("hailey")
        val file = JfStream.authedUrl(base, "ITEM1", token)
        c.start("jf:ab12cd34:ITEM1", "T", 600_000, setOf("hailey"), file, nodeUrls = mapOf("hailey" to file))
        assertFalse(c.session.value!!.converted)
        assertEquals(SyncController.STREAM_LEAD_MS, c.session.value!!.anchorAt - 100_000L)
    }

    @Test
    fun `an ineligible tablet never gets an address, converted or not`() {
        val port = FakeMasterPort()
        val c = SyncController(port, clock = { 100_000L })
        port.add("clear", "Zoe's Tablet", stream = true, sealed = false)
        c.onConnected("clear")
        c.start("jf:ab12cd34:ITEM1", "T", 600_000, setOf("clear"), "x", nodeUrls = mapOf("clear" to url(hevc10, noMain10)))
        assertNull(c.session.value)
        assertTrue(port.sent.none { it.second.has("url") })
    }
}
