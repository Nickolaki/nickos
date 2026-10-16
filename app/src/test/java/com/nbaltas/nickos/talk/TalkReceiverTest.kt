package com.nbaltas.nickos.talk

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
class TalkReceiverTest {
    private val a = "aaaaaaaaaaaaaaaa"
    private val b = "bbbbbbbbbbbbbbbb"
    private val dad = "master-dad"
    private val mum = "master-mum"

    private class Sink : TalkSink {
        val log = ArrayList<String>()
        var played = 0
        override fun begin(sid: String, masterId: String) { log += "begin $sid $masterId" }
        override fun play(pcm: ByteArray) { played++; log += "play ${pcm.size}" }
        override fun end(flush: Boolean) { log += "end flush=$flush" }
    }

    private var now = 10_000L
    private val sink = Sink()
    private var linked = mutableSetOf(dad, mum)
    private val rx = TalkReceiver(sink, { it in linked }, { now })

    private fun pcm(n: Int = 1280) = ByteArray(n)
    private fun start(sid: String, who: String = dad) = rx.onMessage(who, TalkMessages.start(sid))
    private fun frame(sid: String, seq: Long, who: String = dad, n: Int = 1280) = rx.onMessage(who, TalkMessages.frame(sid, seq, pcm(n)))
    private fun end(sid: String, who: String = dad) = rx.onMessage(who, TalkMessages.end(sid))

    @Test fun `start frames end`() {
        assertEquals(TalkReceiver.Result.ACCEPTED, start(a))
        assertTrue(rx.active)
        frame(a, 1); frame(a, 2)
        // Two frames are not a cushion yet.
        assertEquals(0, sink.played)
        frame(a, 3)
        assertEquals(3, sink.played)
        frame(a, 4)
        assertEquals(4, sink.played)
        assertEquals(TalkReceiver.Result.ACCEPTED, end(a))
        assertFalse(rx.active)
        assertEquals("end flush=true", sink.log.last())
    }

    @Test fun `a short utterance is not lost when it ends before the cushion fills`() {
        start(a); frame(a, 1); frame(a, 2)
        end(a)
        assertEquals(2, sink.played)
    }

    @Test fun `no frame for 1500 ms ends the session without talkEnd`() {
        start(a); frame(a, 1)
        now += 1_499; rx.tick()
        assertTrue(rx.active)
        now += 1; rx.tick()
        assertFalse(rx.active)
        assertEquals("end flush=true", sink.log.last())
    }

    @Test fun `frames keep the session alive`() {
        start(a)
        repeat(20) { i -> now += 1_000; frame(a, i + 1L); rx.tick() }
        assertTrue(rx.active)
    }

    @Test fun `a session is cut off at the hard cap even while frames keep coming`() {
        start(a)
        var seq = 0L
        while (now < 10_000 + TalkReceiver.CAP_MS - 40) { now += 40; frame(a, ++seq); rx.tick() }
        assertTrue(rx.active)
        now += 40; rx.tick()
        assertFalse(rx.active)
        assertEquals("end flush=false", sink.log.last())
    }

    @Test fun `a newer session id replaces the old one`() {
        start(a); frame(a, 1)
        assertEquals(TalkReceiver.Result.ACCEPTED, start(b, mum))
        assertEquals(b, rx.activeSid)
        assertTrue(sink.log.any { it == "end flush=false" })
        assertEquals("begin $b $mum", sink.log.last())
        // The old speaker's frames no longer count, nor can the old session come back.
        assertEquals(TalkReceiver.Result.REJECTED, frame(a, 2))
        assertEquals(TalkReceiver.Result.REJECTED, start(a))
        assertEquals(b, rx.activeSid)
    }

    @Test fun `the same session id starting again changes nothing`() {
        start(a); frame(a, 1)
        assertEquals(TalkReceiver.Result.IGNORED, start(a))
        assertEquals(1, sink.log.count { it.startsWith("begin") })
        // Sequence numbers were not reset.
        assertEquals(TalkReceiver.Result.REJECTED, frame(a, 1))
    }

    @Test fun `stale and repeated sequence numbers are rejected`() {
        start(a)
        assertEquals(TalkReceiver.Result.ACCEPTED, frame(a, 5))
        assertEquals(TalkReceiver.Result.REJECTED, frame(a, 5))
        assertEquals(TalkReceiver.Result.REJECTED, frame(a, 4))
        // A gap (the sender dropped frames) is fine.
        assertEquals(TalkReceiver.Result.ACCEPTED, frame(a, 9))
    }

    @Test fun `oversize and odd frames are rejected and do not touch the session`() {
        start(a)
        assertEquals(TalkReceiver.Result.REJECTED, frame(a, 1, n = TalkMessages.MAX_FRAME_BYTES + 2))
        assertEquals(TalkReceiver.Result.REJECTED, frame(a, 2, n = 3))
        assertTrue(rx.active)
        assertEquals(TalkReceiver.Result.ACCEPTED, frame(a, 1))
    }

    @Test fun `malformed messages are rejected`() {
        start(a)
        for (m in listOf<JSONObject?>(null, JSONObject(), JSONObject().put("t", "talk"), JSONObject().put("t", "talk").put("sid", a),
            JSONObject().put("t", "talk").put("sid", a).put("seq", 1).put("pcm", "@@@"), JSONObject().put("t", "talkEnd")))
            assertEquals(TalkReceiver.Result.REJECTED, rx.onMessage(dad, m))
        assertTrue(rx.active)
    }

    @Test fun `a frame with no session is rejected and does not start one`() {
        assertEquals(TalkReceiver.Result.REJECTED, frame(a, 1))
        assertFalse(rx.active)
        assertTrue(sink.log.isEmpty())
    }

    @Test fun `a late frame after talkEnd does not bring the session back`() {
        start(a); frame(a, 1); end(a)
        assertEquals(TalkReceiver.Result.REJECTED, frame(a, 2))
        assertEquals(TalkReceiver.Result.REJECTED, start(a))
        assertFalse(rx.active)
    }

    @Test fun `only a linked master is heard`() {
        assertEquals(TalkReceiver.Result.REJECTED, start(a, "stranger"))
        assertFalse(rx.active)
        start(a)
        linked.remove(dad)
        assertEquals(TalkReceiver.Result.REJECTED, frame(a, 1))
    }

    @Test fun `another master cannot feed or end a session it did not start`() {
        start(a, dad)
        assertEquals(TalkReceiver.Result.REJECTED, frame(a, 1, mum))
        assertEquals(TalkReceiver.Result.REJECTED, end(a, mum))
        assertTrue(rx.active)
    }

    @Test fun `the master going away ends its session`() {
        start(a, dad)
        rx.onMasterGone(mum)
        assertTrue(rx.active)
        rx.onMasterGone(dad)
        assertFalse(rx.active)
        assertEquals("end flush=false", sink.log.last())
    }

    @Test fun `stop ends whatever is running and is safe when idle`() {
        rx.stop()
        assertTrue(sink.log.isEmpty())
        start(a); rx.stop()
        assertFalse(rx.active)
        assertNull(rx.activeSid)
    }

    @Test fun `a gap longer than the cushion fills it again before playing on`() {
        start(a)
        frame(a, 1); frame(a, 2); frame(a, 3)
        assertEquals(3, sink.played)
        now += 200; rx.tick()
        frame(a, 4); frame(a, 5)
        // Held back again: two frames are not a cushion.
        assertEquals(3, sink.played)
        frame(a, 6)
        assertEquals(6, sink.played)
    }
}
