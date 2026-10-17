package com.nbaltas.nickos.talk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** The speaker, the indicator and the failure paths, through ports. Robolectric only because the receiver reads JSON. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TalkPlayerTest {
    private class FakeSpeaker(
        val openFails: String? = null,            // "after allocate" style failure: open throws
        val holdWrites: CountDownLatch? = null,   // a speaker that is slow: writes wait for this
        val writeResult: (Int) -> Int = { it },   // bytes taken for a write of this many
    ) : Speaker {
        val allocated = AtomicInteger(0)
        val closes = CopyOnWriteArrayList<Boolean>()
        val written = AtomicInteger(0)
        override fun open() {
            allocated.incrementAndGet()
            if (openFails != null) throw IllegalStateException(openFails)
        }
        override fun write(pcm: ByteArray, off: Int, len: Int): Int {
            holdWrites?.await(5, TimeUnit.SECONDS)
            val n = writeResult(len)
            if (n > 0) written.addAndGet(n)
            return n
        }
        override fun close(flush: Boolean) { closes += flush }
    }

    private class Indicator : TalkIndicator {
        val log = CopyOnWriteArrayList<String>()
        @Volatile var text: String? = null
        override fun show(text: String) { this.text = text; log += "show $text" }
        override fun clear() { text = null; log += "clear" }
    }

    private fun until(what: String, ms: Long = 3000, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + ms
        while (!cond()) {
            if (System.currentTimeMillis() > end) throw AssertionError("timed out waiting for $what")
            Thread.sleep(5)
        }
    }

    private val failed = CopyOnWriteArrayList<String>()
    private fun player(ind: Indicator, vararg speakers: FakeSpeaker, thread: (String, () -> Unit) -> Unit = { n, r -> Thread(r, n).also { it.isDaemon = true }.start() }): TalkPlayer {
        val q = ArrayDeque(speakers.toList())
        return TalkPlayer({ q.removeFirst() }, ind, { "Dad" }, { failed += it; failedHook(it) }, startThread = thread)
    }

    private val frame = ByteArray(1280)

    // ---- the indicator lives as long as the sound ----
    @Test fun `a normal end keeps the banner until what is queued has played`() {
        val gate = CountDownLatch(1)
        val sp = FakeSpeaker(holdWrites = gate)
        val ind = Indicator()
        val p = player(ind, sp)
        assertTrue(p.begin("s1", "m"))
        assertEquals("Dad", ind.text)
        repeat(10) { p.play(frame) }
        p.end(flush = true)
        Thread.sleep(100)
        assertEquals("still audible, so still shown", "Dad", ind.text)
        gate.countDown()
        until("banner cleared") { ind.text == null }
        assertEquals("every queued frame played", 10 * 1280, sp.written.get())
        assertEquals(listOf(true), sp.closes.toList())
    }

    @Test fun `an idle timeout drains the same way and clears the banner afterwards`() {
        val gate = CountDownLatch(1)
        val sp = FakeSpeaker(holdWrites = gate)
        val ind = Indicator()
        val p = player(ind, sp)
        var now = 1_000L
        val rx = TalkReceiver(p, { true }, { now })
        val sid = "0123456789abcdef"
        rx.onMessage("m", TalkMessages.start(sid))
        for (i in 1L..5L) rx.onMessage("m", TalkMessages.frame(sid, i, frame))
        now += TalkReceiver.IDLE_MS + 1
        rx.tick()
        assertFalse(rx.active)
        Thread.sleep(100)
        assertEquals("the speaker is still playing the tail", "Dad", ind.text)
        gate.countDown()
        until("banner cleared") { ind.text == null }
        assertEquals(listOf(true), sp.closes.toList())
    }

    @Test fun `a cut off end clears the banner promptly and drops what was queued`() {
        val gate = CountDownLatch(1)
        val sp = FakeSpeaker(holdWrites = gate)
        val ind = Indicator()
        val p = player(ind, sp)
        p.begin("s1", "m")
        repeat(10) { p.play(frame) }
        p.end(flush = false)
        gate.countDown()
        until("banner cleared") { ind.text == null }
        assertTrue("cut, not drained: ${sp.written.get()}", sp.written.get() <= 1280)
        assertEquals(listOf(false), sp.closes.toList())
    }

    @Test fun `an old session finishing cannot clear a newer session's banner`() {
        val gateA = CountDownLatch(1)
        val a = FakeSpeaker(holdWrites = gateA)
        val b = FakeSpeaker()
        val ind = Indicator()
        val p = player(ind, a, b)
        p.begin("a", "m1")
        p.play(frame)
        assertTrue(p.begin("b", "m2")) // replaces a while a is still stuck writing
        assertEquals("Dad", ind.text)
        gateA.countDown()                // a finishes now
        until("a closed") { a.closes.isNotEmpty() }
        Thread.sleep(80)
        assertEquals("b is still talking, its banner stays", "Dad", ind.text)
        assertEquals(listOf("show Dad", "show Dad"), ind.log.toList())
        p.end(flush = true)
        until("b done") { ind.text == null }
    }

    // ---- setup is all or nothing ----
    @Test fun `a speaker that fails to start is closed, nothing is shown and begin says no`() {
        val sp = FakeSpeaker(openFails = "play failed")
        val ind = Indicator()
        val p = player(ind, sp)
        assertFalse(p.begin("s1", "m"))
        assertEquals("allocated once", 1, sp.allocated.get())
        assertEquals("released", listOf(false), sp.closes.toList())
        assertTrue("no banner", ind.log.isEmpty())
        assertTrue(failed.isEmpty())
    }

    @Test fun `a writer thread that cannot start releases the speaker too`() {
        val sp = FakeSpeaker()
        val ind = Indicator()
        val p = player(ind, sp, thread = { _, _ -> throw OutOfMemoryError("no thread") })
        assertFalse(p.begin("s1", "m"))
        assertEquals(listOf(false), sp.closes.toList())
        assertTrue(ind.log.isEmpty())
    }

    @Test fun `the receiver drops a session whose speaker would not start`() {
        val ind = Indicator()
        val p = player(ind, FakeSpeaker(openFails = "boom"))
        val rx = TalkReceiver(p, { true }, { 1_000L })
        val sid = "0123456789abcdef"
        assertEquals(TalkReceiver.Result.REJECTED, rx.onMessage("m", TalkMessages.start(sid)))
        assertFalse(rx.active)
        assertTrue(ind.log.isEmpty())
    }

    // ---- write errors ----
    @Test fun `a negative write ends the session, tells the receiver and clears the banner`() {
        val sp = FakeSpeaker(writeResult = { -3 })
        val ind = Indicator()
        val p = player(ind, sp)
        val rx = TalkReceiver(p, { true }, { 1_000L })
        val sid = "0123456789abcdef"
        failedHook = { rx.sinkFailed(it) }
        rx.onMessage("m", TalkMessages.start(sid))
        for (i in 1L..4L) rx.onMessage("m", TalkMessages.frame(sid, i, frame))
        until("banner cleared") { ind.text == null }
        until("reported") { failed.contains(sid) }
        until("receiver notified") { !rx.active }
        assertEquals(listOf(false), sp.closes.toList())
    }

    @Test fun `a partial write carries on with the rest`() {
        val sp = FakeSpeaker(writeResult = { maxOf(1, it / 2) })
        val ind = Indicator()
        val p = player(ind, sp)
        p.begin("s1", "m")
        p.play(frame)
        p.end(flush = true)
        until("done") { ind.text == null }
        assertEquals(1280, sp.written.get())
        assertTrue(failed.isEmpty())
    }

    @Test fun `a speaker that takes nothing over and over is an error`() {
        val sp = FakeSpeaker(writeResult = { 0 })
        val ind = Indicator()
        val p = player(ind, sp)
        p.begin("s1", "m")
        p.play(frame)
        until("reported") { failed.contains("s1") }
        until("banner cleared") { ind.text == null }
    }

    // ---- where it is shown ----
    @Test fun `the banner shows over other apps, and over the locked wall, but on the home screen NodeApp draws it`() {
        assertTrue(TalkBannerPolicy.overlayShows(talking = true, locked = false, homeVisible = false))
        assertFalse(TalkBannerPolicy.overlayShows(talking = true, locked = false, homeVisible = true))
        assertTrue("locked, home visible", TalkBannerPolicy.overlayShows(talking = true, locked = true, homeVisible = true))
        assertTrue("locked, in another app", TalkBannerPolicy.overlayShows(talking = true, locked = true, homeVisible = false))
        assertFalse("nobody talking", TalkBannerPolicy.overlayShows(talking = false, locked = true, homeVisible = false))
    }

    @Test fun `without the overlay permission a notification stands in for the banner`() {
        assertTrue(TalkBannerPolicy.needsNotification(canOverlay = false))
        assertFalse(TalkBannerPolicy.needsNotification(canOverlay = true))
    }

    private var failedHook: (String) -> Unit = {}
}
