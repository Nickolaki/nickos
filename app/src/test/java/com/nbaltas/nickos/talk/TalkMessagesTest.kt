package com.nbaltas.nickos.talk

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TalkMessagesTest {
    private val sid = "0123456789abcdef"
    private fun wire(o: JSONObject) = JSONObject(o.toString())
    private fun pcm(n: Int) = ByteArray(n) { (it * 7).toByte() }

    @Test fun `the three messages round trip`() {
        assertEquals(TalkMessages.Start(sid), TalkMessages.decode(wire(TalkMessages.start(sid))))
        assertEquals(TalkMessages.End(sid), TalkMessages.decode(wire(TalkMessages.end(sid))))
        val f = TalkMessages.decode(wire(TalkMessages.frame(sid, 42, pcm(1280)))) as TalkMessages.Frame
        assertEquals(sid, f.sid)
        assertEquals(42L, f.seq)
        assertArrayEquals(pcm(1280), f.pcm)
    }

    @Test fun `a frame is 40 ms of 16 kHz mono 16 bit`() {
        assertEquals(1280, TalkMessages.FRAME_BYTES)
    }

    @Test fun `new session ids are 16 hex characters and differ`() {
        val a = TalkMessages.newSid()
        val b = TalkMessages.newSid()
        assertTrue(Regex("^[0-9a-f]{16}$").matches(a))
        assertNotEquals(a, b)
    }

    @Test fun `isTalk tells talk messages from the rest`() {
        assertTrue(TalkMessages.isTalk(TalkMessages.start(sid)))
        assertTrue(TalkMessages.isTalk(TalkMessages.frame(sid, 1, pcm(2))))
        assertTrue(TalkMessages.isTalk(TalkMessages.end(sid)))
        assertTrue(!TalkMessages.isTalk(JSONObject().put("t", "cmd")))
        assertTrue(!TalkMessages.isTalk(null))
    }

    @Test fun `malformed messages decode to null`() {
        val ok = wire(TalkMessages.frame(sid, 1, pcm(8)))
        val cases = listOf(
            null,
            JSONObject(),
            JSONObject().put("t", "talkStart"),
            JSONObject().put("t", "talkStart").put("sid", ""),
            JSONObject().put("t", "talkStart").put("sid", "XYZ"),
            JSONObject().put("t", "talkStart").put("sid", "0123456789ABCDEF"),
            JSONObject().put("t", "talkStart").put("sid", sid + "0"),
            JSONObject().put("t", "talkEnd").put("sid", "short"),
            JSONObject().put("t", "other").put("sid", sid),
            JSONObject(ok.toString()).apply { remove("seq") },
            JSONObject(ok.toString()).put("seq", 0),
            JSONObject(ok.toString()).put("seq", -3),
            JSONObject(ok.toString()).put("seq", "7"),
            JSONObject(ok.toString()).apply { remove("pcm") },
            JSONObject(ok.toString()).put("pcm", ""),
            JSONObject(ok.toString()).put("pcm", "!!! not base64 !!!"),
            // Three bytes is an odd length, which is not 16 bit audio.
            JSONObject(ok.toString()).put("pcm", com.nbaltas.nickos.link.B64.enc(ByteArray(3))),
        )
        for (c in cases) assertNull(c?.toString(), TalkMessages.decode(c))
    }

    @Test fun `a frame over the size cap is refused and one at the cap is kept`() {
        assertNull(TalkMessages.decode(wire(TalkMessages.frame(sid, 1, pcm(TalkMessages.MAX_FRAME_BYTES + 2)))))
        assertTrue(TalkMessages.decode(wire(TalkMessages.frame(sid, 1, pcm(TalkMessages.MAX_FRAME_BYTES)))) is TalkMessages.Frame)
        // A huge string is turned away before it is decoded.
        val huge = JSONObject(TalkMessages.frame(sid, 1, pcm(8)).toString()).put("pcm", "A".repeat(100_000))
        assertNull(TalkMessages.decode(huge))
    }

    @Test fun `encoding 40 ms frames is cheap`() {
        val frame = pcm(TalkMessages.FRAME_BYTES)
        repeat(200) { TalkMessages.frame(sid, it + 1L, frame).toString() } // warm up
        val n = 2000
        val t0 = System.nanoTime()
        var bytes = 0L
        for (i in 1..n) bytes += TalkMessages.frame(sid, i.toLong(), frame).toString().length
        val perFrameMs = (System.nanoTime() - t0) / 1e6 / n
        println("talk encode: %.4f ms per 40 ms frame, %d bytes per frame as JSON".format(perFrameMs, bytes / n))
        // A frame is due every 40 ms. Encoding must be a tiny part of that even on a slow CI box.
        assertTrue("encode took $perFrameMs ms per frame", perFrameMs < 2.0)
        // About 1.7 KB of JSON per 40 ms, roughly 43 KB/s: nothing for a Wi-Fi link.
        assertTrue(bytes / n < 2000)
    }
}
