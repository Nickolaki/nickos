package com.nbaltas.nickos.cam

import com.nbaltas.nickos.data.Camera
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RtspCamTest {
    @Test
    fun `tapo url encodes user and password`() {
        assertEquals(
            "rtsp://nick%20os:p%40ss%2Fw%3Ard%231@192.168.49.23:554/stream2",
            RtspUrl.tapo("192.168.49.23", "nick os", "p@ss/w:rd#1", false),
        )
        assertTrue(RtspUrl.tapo("192.168.49.23", "u", "p", true).endsWith("/stream1"))
    }

    @Test
    fun `enc leaves unreserved alone`() {
        assertEquals("Ab9-._~", RtspUrl.enc("Ab9-._~"))
        assertEquals("%C3%A9", RtspUrl.enc("é"))
        assertEquals("%20", RtspUrl.enc(" "))
    }

    @Test
    fun `redact hides the password`() {
        assertEquals("rtsp://u:***@h:554/x", RtspUrl.redact("rtsp://u:secret@h:554/x"))
        assertEquals("rtsp://h/x", RtspUrl.redact("rtsp://h/x"))
        assertEquals("http://a:***@h", RtspUrl.redact("http://a:b@h"))
        assertEquals("not a url", RtspUrl.redact("not a url"))
        // A raw password holding a slash must not survive either.
        assertFalse(RtspUrl.redact("rtsp://u:sec/ret@h/x").contains("sec"))
    }

    @Test
    fun `camera toString never contains the password`() {
        assertFalse(Camera("i", "n", "rtsp://u:secret@h/s").toString().contains("secret"))
        assertFalse(Camera("i", "n", host = "h", user = "u", pass = "secret").toString().contains("secret"))
    }

    @Test
    fun `the stream url is built from the fields and never stored`() {
        val c = Camera("i", "n", host = "192.168.49.9", user = "u", pass = "p w", hd = false)
        assertTrue(c.isTapo)
        assertEquals("rtsp://u:p%20w@192.168.49.9:554/stream2", c.streamUrl())
        assertEquals("", c.url)
        assertFalse(c.toJson().toString().contains("rtsp://"))
    }

    @Test
    fun `json round trip keeps every field`() {
        val c = Camera("i", "Baby", "", 90, "192.168.49.9", "nick", "pw", true)
        assertEquals(c, Camera.from(JSONObject(c.toJson().toString())))
        val raw = Camera("j", "Door", "rtsp://x/y", 180)
        assertEquals(raw, Camera.from(JSONObject(raw.toJson().toString())))
    }

    @Test
    fun `a pre-change json with only url still loads`() {
        val c = Camera.from(JSONObject("""{"id":"a","name":"Old","url":"rtsp://u:p@h/s","rotation":90}"""))
        assertNull(c.host)
        assertFalse(c.isTapo)
        assertEquals("rtsp://u:p@h/s", c.streamUrl())
        assertEquals(90, c.rotation)
    }

    @Test
    fun `news mapping`() {
        assertEquals(RtspNews.UNREACHABLE, RtspNewsFor.of(2001, emptyList()))
        assertEquals(RtspNews.UNREACHABLE, RtspNewsFor.of(2002, emptyList()))
        assertEquals(RtspNews.WRONG_PASSWORD, RtspNewsFor.of(1000, listOf("RtspPlaybackException" to "Unauthorized")))
        assertEquals(RtspNews.WRONG_PASSWORD, RtspNewsFor.of(1000, listOf("RtspPlaybackException" to "DESCRIBE 401")))
        assertEquals(RtspNews.CANT_PLAY, RtspNewsFor.of(4001, emptyList()))
        assertEquals(RtspNews.CANT_PLAY, RtspNewsFor.of(4005, emptyList()))
        assertEquals(RtspNews.UNREACHABLE, RtspNewsFor.of(1000, listOf("IOException" to null, "ConnectException" to "Connection refused")))
        // A refused connect arrives as an unspecified IO error with the real cause underneath.
        assertEquals(RtspNews.UNREACHABLE, RtspNewsFor.of(2000, listOf("RtspPlaybackException" to null, "SocketTimeoutException" to "timed out")))
        assertEquals(RtspNews.ERROR, RtspNewsFor.of(1000, emptyList()))
    }

    @Test
    fun `adopt policy`() {
        assertEquals(Adopt.Keep, AdoptPolicy.decide(listOf("a", "b"), "a", emptySet()))
        assertEquals(Adopt.Move("b"), AdoptPolicy.decide(listOf("b"), "a", emptySet()))
        assertEquals(Adopt.Ambiguous, AdoptPolicy.decide(listOf("b", "c"), "a", emptySet()))
        assertEquals(Adopt.NotFound, AdoptPolicy.decide(listOf("b"), "a", setOf("b")))
        assertEquals(Adopt.NotFound, AdoptPolicy.decide(emptyList(), "a", emptySet()))
        // One free host and one taken: the dead camera must not be moved onto the live one.
        assertEquals(Adopt.Move("c"), AdoptPolicy.decide(listOf("b", "c"), "a", setOf("b")))
    }

    @Test
    fun `no dashes in camera copy`() {
        val dashes = listOf(Char(0x2014), Char(0x2013))
        RtspNews.values().forEach { n ->
            val s = CamText.rtsp(n)
            dashes.forEach { assertFalse("dash in $n", s.contains(it)) }
        }
        assertEquals("", CamText.rtsp(RtspNews.LIVE))
        assertEquals(CamText.RTSP_ERROR, CamText.rtsp(RtspNews.ERROR))
    }

    @Test
    fun `no dashes in editor copy`() {
        val dashes = listOf(Char(0x2014), Char(0x2013))
        listOf(
            com.nbaltas.nickos.ui.master.EDITOR_LOOKING, com.nbaltas.nickos.ui.master.EDITOR_FOUND_AT,
            com.nbaltas.nickos.ui.master.EDITOR_NOT_FOUND, com.nbaltas.nickos.ui.master.EDITOR_NO_NETWORK,
            com.nbaltas.nickos.ui.master.EDITOR_COLON, com.nbaltas.nickos.ui.master.EDITOR_ACCOUNT_HINT,
            com.nbaltas.nickos.ui.master.EDITOR_PICTURE_HINT, com.nbaltas.nickos.ui.master.EDITOR_URL_HINT,
            com.nbaltas.nickos.ui.master.NO_CAMERAS_TEXT,
        ).forEach { s -> dashes.forEach { assertFalse("dash in: $s", s.contains(it)) } }
    }

    @Test
    fun `hostOf reads the host out of a stream address`() {
        assertEquals("192.168.49.23", RtspUrl.hostOf("rtsp://nick:pa%40ss@192.168.49.23:554/stream2"))
        assertEquals("192.168.49.9", RtspUrl.hostOf("rtsp://192.168.49.9/live"))
        assertEquals("cam.local", RtspUrl.hostOf("rtsp://u:a/b@cam.local:8554/x"))
        assertNull(RtspUrl.hostOf("no scheme"))
        assertNull(RtspUrl.hostOf(""))
    }

    @Test
    fun `an older url only camera's host is taken`() {
        val legacy = Camera("old", "Porch", "rtsp://admin:pw@192.168.49.30:554/ch1")
        val tapo = Camera("t", "Baby", host = "192.168.49.23")
        val taken = AdoptPolicy.takenHosts(listOf(legacy, tapo))
        assertEquals(setOf("192.168.49.30", "192.168.49.23"), taken)
        // The legacy camera answered and the Tapo's old address did not: it must not be moved onto the legacy one.
        assertEquals(Adopt.NotFound, AdoptPolicy.decide(listOf("192.168.49.30"), "192.168.49.99", taken))
    }

    @Test
    fun `a move needs the candidate to accept the saved account`() = kotlinx.coroutines.runBlocking {
        assertEquals(Adopt.Move("b"), AdoptPolicy.settle(listOf("b"), "a", emptySet()) { true })
        assertEquals(Adopt.Unverified("b"), AdoptPolicy.settle(listOf("b"), "a", emptySet()) { false })
        // Keep and NotFound never ask the camera.
        assertEquals(Adopt.Keep, AdoptPolicy.settle(listOf("a"), "a", emptySet()) { error("no") })
        assertEquals(Adopt.NotFound, AdoptPolicy.settle(emptyList(), "a", emptySet()) { error("no") })
    }

    @Test
    fun `chainOf walks causes and stops on a self cause`() {
        val inner = java.net.ConnectException("refused")
        val outer = RuntimeException("outer", inner)
        val chain = RtspNewsFor.chainOf(outer)
        assertEquals(listOf("RuntimeException", "ConnectException"), chain.map { it.first })
        assertEquals(RtspNews.UNREACHABLE, RtspNewsFor.of(0, chain))
        assertEquals(1, RtspNewsFor.chainOf(IllegalStateException("x")).size)
    }
}
