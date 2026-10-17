package com.nbaltas.nickos.sync

import com.nbaltas.nickos.master.StreamFail
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
class StreamTest {
    private val token = "tok+en/secret=1"

    @Test
    fun `the stream address has the exact shape Jellyfin wants`() {
        assertEquals(
            "http://192.168.1.5:8096/Videos/ITEM1/stream?static=true&api_key=tok%2Ben%2Fsecret%3D1",
            JfStream.authedUrl("http://192.168.1.5:8096", "ITEM1", token),
        )
    }

    @Test
    fun `an https server is streamed over https and a trailing slash is dropped`() {
        val u = JfStream.authedUrl("https://jf.example.com/", "ITEM1", "abc")
        assertEquals("https://jf.example.com/Videos/ITEM1/stream?static=true&api_key=abc", u)
    }

    @Test
    fun `the fid starts with jf and names the server and the item`() {
        val f = JfStream.fid("http://192.168.1.5:8096/", "ITEM1")
        assertTrue(f.startsWith("jf:"))
        assertTrue(f.endsWith(":ITEM1"))
        assertEquals(f, JfStream.fid("http://192.168.1.5:8096", "ITEM1"))
        assertFalse(f == JfStream.fid("http://192.168.1.6:8096", "ITEM1"))
        assertTrue(JfStream.isStreamFid(f))
        assertFalse(JfStream.isStreamFid("0a1b2c"))
        assertFalse(JfStream.isStreamFid(null))
        assertFalse(f.contains("192.168"))
    }

    @Test
    fun `redact takes the address and the token out of any text`() {
        val u = JfStream.authedUrl("http://192.168.1.5:8096", "ITEM1", token)
        val r = JfStream.redact("failed to open $u (timeout)")
        assertFalse(r.contains("192.168"))
        assertFalse(r.contains("secret"))
        assertFalse(r.contains("api_key=tok"))
        assertEquals("api_key=[x]", JfStream.redact("api_key=abc123"))
    }

    @Test
    fun `cmdSync carries the url and decodes it`() {
        val o = SyncMessages.cmdSync("jf:ab:I", 0, 5000, "Star", 2, "http://x/Videos/I/stream?api_key=t")
        assertEquals("http://x/Videos/I/stream?api_key=t", o.getString("url"))
        val d = SyncMessages.decodeSyncCmd(JSONObject(o.toString()))!!
        assertEquals("http://x/Videos/I/stream?api_key=t", d.url)
        assertEquals("jf:ab:I", d.fid)
        assertEquals(2, d.n)
    }

    @Test
    fun `an absent url stays null`() {
        val o = SyncMessages.cmdSync("f1", 0, 5000, "Star", 2)
        assertFalse(o.has("url"))
        assertNull(SyncMessages.decodeSyncCmd(JSONObject(o.toString()))!!.url)
        assertNull(SyncMessages.decodeSyncCmd(JSONObject(SyncMessages.cmdSync("f1", 0, 5000, "Star", 2, "").toString()))!!.url)
        assertNull(SyncMessages.decodeSyncCmd(JSONObject("""{"fid":"f","pos":1,"at":2,"title":"t","url":""}"""))!!.url)
    }

    @Test
    fun `the url never appears in a printed command or session`() {
        val url = JfStream.authedUrl("http://192.168.1.5:8096", "ITEM1", token)
        val msg = SyncMessages.decodeSyncCmd(JSONObject(SyncMessages.cmdSync("jf:ab:I", 0, 5000, "Star", 1, url).toString()))!!
        assertFalse(msg.toString().contains("secret"))
        assertFalse(msg.toString().contains("192.168"))
        assertFalse(SyncCmd("jf:ab:I", 0, 5000, "Star", url).toString().contains("secret"))
        assertFalse(SyncSession("jf:ab:I", "Star", 0, setOf("a"), 0, 0, true, url).toString().contains("secret"))
    }

    @Test
    fun `the plan passes the url through`() {
        val s = SyncSession("jf:ab:I", "Star", 0, setOf("a"), 0, 5000, true, "http://x/s?api_key=t")
        assertEquals("http://x/s?api_key=t", SyncPlan.planFor(s, 0, 1000, 4000).url)
        assertNull(SyncPlan.planFor(s.copy(url = null), 0, 1000, 4000).url)
    }

    @Test
    fun `the node follows an address only on a sealed link`() {
        assertTrue(StreamGate.nodeAllows(sealed = true, url = "http://x"))
        assertFalse(StreamGate.nodeAllows(sealed = false, url = "http://x"))
        assertTrue(StreamGate.nodeAllows(sealed = false, url = null))
        assertTrue(StreamGate.nodeAllows(sealed = false, url = ""))
    }

    @Test
    fun `the master sends an address only to a sealed stream1 tablet in the session`() {
        assertTrue(StreamGate.masterSends(sealed = true, streamCap = true, inSession = true))
        assertFalse(StreamGate.masterSends(sealed = false, streamCap = true, inSession = true))
        assertFalse(StreamGate.masterSends(sealed = true, streamCap = false, inSession = true))
        assertFalse(StreamGate.masterSends(sealed = true, streamCap = true, inSession = false))
    }

    @Test
    fun `player errors become one of four codes`() {
        assertEquals("cant_reach", StreamError.code(2001, 0)) // connection failed
        assertEquals("cant_reach", StreamError.code(2002, 0)) // connection timeout
        assertEquals("auth", StreamError.code(2004, 401))
        assertEquals("auth", StreamError.code(2004, 403))
        assertEquals("cant_reach", StreamError.code(2004, 500))
        assertEquals("unsupported", StreamError.code(4001, 0)) // decoder init failed
        assertEquals("unsupported", StreamError.code(3001, 0)) // broken container
        assertEquals("unsupported", StreamError.code(1000, 0))
        assertEquals(20_000L, StreamError.LOAD_TIMEOUT_MS)
        assertEquals(30_000L, StreamError.REBUFFER_TIMEOUT_MS)
    }

    @Test
    fun `the fallback to save is offered only when the stream could not be reached`() {
        assertTrue(StreamFail("a", "cant_reach", "jf:x:I", "Star").canSaveInstead)
        assertTrue(StreamFail("a", "timeout", "jf:x:I", "Star").canSaveInstead)
        assertFalse(StreamFail("a", "unsupported", "jf:x:I", "Star").canSaveInstead)
        assertFalse(StreamFail("a", "auth", "jf:x:I", "Star").canSaveInstead)
        assertEquals("unsupported", StreamFail.clean("http://leak/api_key=1"))
        assertEquals("cant_reach", StreamFail.clean("cant_reach"))
        assertEquals("unsupported", StreamFail.clean(null))
    }

    @Test
    fun `the words for a failed tablet`() {
        assertEquals("Leo can't reach Jellyfin", TogetherText.streamError("Leo", "cant_reach"))
        assertEquals("Leo's tablet can't play this one", TogetherText.streamError("Leo", "unsupported"))
        assertEquals("Save to tablets instead", TogetherText.SAVE_INSTEAD)
        assertEquals("Streaming needs good WiFi. For long drives, save to tablets.", TogetherText.STREAM_HINT)
        for (code in StreamFail.CODES) assertFalse(TogetherText.streamError("Leo", code).let { it.contains(Char(0x2014)) || it.contains(Char(0x2013)) })
    }

    @Test
    fun `the stream button counts the tablets that can really stream`() {
        assertEquals("Stream now on 2 tablets", JfTogether.streamLabel(2, emptyList()))
        assertEquals("Stream now on 1 tablet", JfTogether.streamLabel(1, emptyList()))
        assertEquals("Stream now on 2 tablets (Leo can't stream yet)", JfTogether.streamLabel(2, listOf("Leo")))
        assertEquals("Stream now on 1 tablet (Leo and Mia can't stream yet)", JfTogether.streamLabel(1, listOf("Leo", "Mia")))
        val (ok, no) = JfTogether.streamSplit(setOf("a", "b", "c")) { it != "b" }
        assertEquals(setOf("a", "c"), ok)
        assertEquals(setOf("b"), no)
    }
}
