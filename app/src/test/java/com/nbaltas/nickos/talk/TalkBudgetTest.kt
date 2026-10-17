package com.nbaltas.nickos.talk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The talk budgets on the same WiFi: start to hear at most 500 ms, release to silence at most 300 ms. Real numbers come
 * from the device checklist; here the settings that decide them are pinned so nobody makes them worse by accident.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TalkBudgetTest {
    private class Sink : TalkSink {
        val played = ArrayList<ByteArray>()
        var ended: Boolean? = null
        override fun begin(sid: String, masterId: String) = true
        override fun play(pcm: ByteArray) { played += pcm }
        override fun end(flush: Boolean) { ended = flush }
    }

    private val sid = "0123456789abcdef"
    private val frame = ByteArray(TalkMessages.FRAME_BYTES)

    @Test fun `the budgets are the ones Astra asked for`() {
        assertEquals(500, TalkBudget.START_TO_HEAR_MS)
        assertEquals(300, TalkBudget.RELEASE_TO_SILENCE_MS)
    }

    @Test fun `the jitter buffer is three frames deep, 120 ms, and leaves most of the start budget to the network`() {
        assertEquals(3, TalkReceiver.PREFILL)
        assertEquals(120, TalkReceiver.PREFILL * TalkMessages.FRAME_MS)
        assertTrue("headroom ${TalkBudget.startHeadroomMs}", TalkBudget.startHeadroomMs >= 350)
    }

    @Test fun `playing starts on exactly the third frame and not before`() {
        val sink = Sink()
        val rx = TalkReceiver(sink, { true }, { 1_000L })
        rx.onMessage("m", TalkMessages.start(sid))
        rx.onMessage("m", TalkMessages.frame(sid, 1, frame))
        rx.onMessage("m", TalkMessages.frame(sid, 2, frame))
        assertTrue("nothing yet: the cushion is filling", sink.played.isEmpty())
        rx.onMessage("m", TalkMessages.frame(sid, 3, frame))
        assertEquals(3, sink.played.size)
        rx.onMessage("m", TalkMessages.frame(sid, 4, frame))
        assertEquals("from then on a frame plays as it arrives", 4, sink.played.size)
    }

    @Test fun `what can still be heard after the parent lets go is inside the release budget`() {
        // At most the tail frames are still written, then the drain wait, then the track is paused and flushed.
        assertTrue("tail ${TalkBudget.releaseTailMs}", TalkBudget.releaseTailMs + TalkBudget.NETWORK_ALLOWANCE_MS <= TalkBudget.RELEASE_TO_SILENCE_MS)
        assertTrue(TalkBudget.DRAIN_MS <= 100)
        assertTrue(TalkBudget.TAIL_FRAMES * TalkMessages.FRAME_MS <= 120)
    }

    @Test fun `a talkEnd flushes the cushion at once, nothing waits for the idle timeout`() {
        var now = 1_000L
        val sink = Sink()
        val rx = TalkReceiver(sink, { true }, { now })
        rx.onMessage("m", TalkMessages.start(sid))
        rx.onMessage("m", TalkMessages.frame(sid, 1, frame))
        rx.onMessage("m", TalkMessages.frame(sid, 2, frame))
        now += 40
        rx.onMessage("m", TalkMessages.end(sid))
        assertFalse(rx.active)
        assertEquals("the two held frames were played out, not lost", 2, sink.played.size)
        assertEquals(true, sink.ended)
    }

    @Test fun `a sender that just stops is cut off at the idle timeout and not a moment before`() {
        var now = 1_000L
        val sink = Sink()
        val rx = TalkReceiver(sink, { true }, { now })
        rx.onMessage("m", TalkMessages.start(sid))
        for (i in 1L..4L) { rx.onMessage("m", TalkMessages.frame(sid, i, frame)); now += 40 }
        val last = now - 40
        now = last + TalkReceiver.IDLE_MS - 1
        rx.tick()
        assertTrue(rx.active)
        now = last + TalkReceiver.IDLE_MS
        rx.tick()
        assertFalse(rx.active)
        assertEquals(1_500L, TalkReceiver.IDLE_MS)
    }
}
