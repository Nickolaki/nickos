package com.nbaltas.nickos.talk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        @Volatile var onOpen: () -> Unit = {}
        override fun open() {
            onOpen()
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
        @Volatile var availableNow = true
        @Volatile var holds = true
        @Volatile var showOk = true
        override fun available() = availableNow
        override fun maintain() = holds
        override fun show(text: String): Boolean { log += "show $text"; if (showOk) this.text = text; return showOk }
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
    @Test fun `a normal end keeps the banner until the short tail has played, and drops the rest of the queue`() {
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
        // The frame the writer already held, plus the tail. Not the ten that were queued.
        assertTrue("written ${sp.written.get()}", sp.written.get() <= (1 + TalkBudget.TAIL_FRAMES) * 1280)
        assertTrue("the tail was played", sp.written.get() >= TalkBudget.TAIL_FRAMES * 1280)
        assertEquals(listOf(true), sp.closes.toList())
    }

    @Test fun `release to silence does not depend on how much was queued`() {
        // A slow speaker that takes 20 ms per write: a second of queued speech must not play after the end.
        val sp = FakeSpeaker(writeResult = { Thread.sleep(20); it })
        val ind = Indicator()
        val p = player(ind, sp)
        p.begin("s1", "m")
        repeat(TalkPlayer.MAX_WAITING) { p.play(frame) }
        val t0 = System.currentTimeMillis()
        p.end(flush = true)
        until("banner cleared", 2000) { ind.text == null }
        val took = System.currentTimeMillis() - t0
        assertTrue("took $took ms", took <= TalkBudget.RELEASE_TO_SILENCE_MS - TalkBudget.NETWORK_ALLOWANCE_MS)
        assertTrue("written ${sp.written.get()}", sp.written.get() < TalkPlayer.MAX_WAITING * 1280 / 2)
    }

    @Test fun `a cut during the drain wins over the tail that is playing`() {
        val gate = CountDownLatch(1)
        val sp = FakeSpeaker(holdWrites = gate)
        val ind = Indicator()
        val p = player(ind, sp)
        p.begin("s1", "m")
        repeat(6) { p.play(frame) }
        p.end(flush = true)
        ind.holds = false
        assertFalse(p.indicatorHolds())
        gate.countDown()
        until("banner cleared") { ind.text == null }
        assertEquals("cut, not drained", listOf(false), sp.closes.toList())
    }

    @Test fun `the indicator is maintained while the tail drains, after the session ended`() {
        val gate = CountDownLatch(1)
        val sp = FakeSpeaker(holdWrites = gate)
        val maintained = AtomicInteger(0)
        val ind = object : TalkIndicator {
            @Volatile var text: String? = null
            override fun available() = true
            override fun show(text: String): Boolean { this.text = text; return true }
            override fun maintain(): Boolean { maintained.incrementAndGet(); return true }
            override fun clear() { text = null }
        }
        val q = ArrayDeque(listOf(sp))
        val p = TalkPlayer({ q.removeFirst() }, ind, { "Dad" }, { })
        p.begin("s1", "m")
        repeat(4) { p.play(frame) }
        p.end(flush = true)               // the receiver's session is over, the speaker is not
        assertTrue(p.indicatorHolds())
        assertEquals("still asked while audible", 1, maintained.get())
        gate.countDown()
        until("banner cleared") { ind.text == null }
        assertTrue("idle again", p.indicatorHolds())
        assertEquals("not asked once nothing is audible", 1, maintained.get())
    }

    // ---- the indicator comes first ----
    @Test fun `the speaker is opened only after the indicator is really up`() {
        val ind = Indicator()
        val sp = FakeSpeaker()
        var upAtOpen: String? = "unset"
        sp.onOpen = { upAtOpen = ind.text }
        val p = player(ind, sp)
        assertTrue(p.begin("s1", "m"))
        assertEquals("Dad", upAtOpen)
        p.end(flush = false)
        until("banner cleared") { ind.text == null }
    }

    @Test fun `an indicator that could not be shown means no speaker at all`() {
        val ind = Indicator().also { it.showOk = false }
        val sp = FakeSpeaker()
        val p = player(ind, sp)
        assertFalse(p.begin("s1", "m"))
        assertEquals("nothing was opened", 0, sp.allocated.get())
        assertTrue(sp.closes.isEmpty())
        assertNull(ind.text)
        assertEquals(listOf("show Dad", "clear"), ind.log.toList())
    }

    @Test fun `an indicator that throws means no speaker at all`() {
        val sp = FakeSpeaker()
        val ind = object : TalkIndicator {
            override fun available() = true
            override fun show(text: String): Boolean = throw IllegalStateException("window manager")
            override fun maintain() = true
            override fun clear() {}
        }
        val q = ArrayDeque(listOf(sp))
        val p = TalkPlayer({ q.removeFirst() }, ind, { "Dad" }, { })
        assertFalse(p.begin("s1", "m"))
        assertEquals(0, sp.allocated.get())
    }

    @Test fun `a receiver whose indicator cannot be shown plays nothing`() {
        val ind = Indicator().also { it.showOk = false }
        val sp = FakeSpeaker()
        val p = player(ind, sp)
        val rx = TalkReceiver(p, { true }, { 1_000L })
        val sid = "0123456789abcdef"
        assertEquals(TalkReceiver.Result.REJECTED, rx.onMessage("m", TalkMessages.start(sid)))
        for (i in 1L..4L) rx.onMessage("m", TalkMessages.frame(sid, i, frame))
        assertEquals(0, sp.written.get())
        assertEquals(0, sp.allocated.get())
    }

    @Test fun `losing the indicator while it plays stops the playback`() {
        val gate = CountDownLatch(1)
        val sp = FakeSpeaker(holdWrites = gate)
        val ind = Indicator()
        val p = player(ind, sp)
        p.begin("s1", "m")
        repeat(5) { p.play(frame) }
        ind.holds = false
        assertFalse(p.indicatorHolds())
        gate.countDown()
        until("stopped and cleared") { ind.text == null }
        assertEquals(listOf(false), sp.closes.toList())
        assertTrue("written ${sp.written.get()}", sp.written.get() <= 1280)
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
    @Test fun `a speaker that fails to start is closed, the banner is taken down and begin says no`() {
        val sp = FakeSpeaker(openFails = "play failed")
        val ind = Indicator()
        val p = player(ind, sp)
        assertFalse(p.begin("s1", "m"))
        assertEquals("allocated once", 1, sp.allocated.get())
        assertEquals("released", listOf(false), sp.closes.toList())
        assertNull("no banner left", ind.text)
        assertEquals(listOf("show Dad", "clear"), ind.log.toList())
        assertTrue(failed.isEmpty())
    }

    @Test fun `a writer thread that cannot start releases the speaker too`() {
        val sp = FakeSpeaker()
        val ind = Indicator()
        val p = player(ind, sp, thread = { _, _ -> throw OutOfMemoryError("no thread") })
        assertFalse(p.begin("s1", "m"))
        assertEquals(listOf(false), sp.closes.toList())
        assertNull(ind.text)
    }

    @Test fun `the receiver drops a session whose speaker would not start`() {
        val ind = Indicator()
        val p = player(ind, FakeSpeaker(openFails = "boom"))
        val rx = TalkReceiver(p, { true }, { 1_000L })
        val sid = "0123456789abcdef"
        assertEquals(TalkReceiver.Result.REJECTED, rx.onMessage("m", TalkMessages.start(sid)))
        assertFalse(rx.active)
        assertNull("no banner left", ind.text)
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

    // ---- no visible indicator, no audio ----
    @Test fun `with no way to show the banner or a notification the session is refused and nothing is opened`() {
        val sp = FakeSpeaker()
        val ind = Indicator().also { it.availableNow = false }
        val p = player(ind, sp)
        val rx = TalkReceiver(p, { true }, { 1_000L })
        val sid = "0123456789abcdef"
        assertEquals(TalkReceiver.Result.REJECTED, rx.onMessage("m", TalkMessages.start(sid)))
        assertFalse(rx.active)
        assertEquals("no speaker was allocated", 0, sp.allocated.get())
        assertTrue("nothing shown", ind.log.isEmpty())
        // The frames of a refused session play nothing either.
        assertEquals(TalkReceiver.Result.REJECTED, rx.onMessage("m", TalkMessages.frame(sid, 1, frame)))
        assertEquals(0, sp.written.get())
    }

    @Test fun `once a notification can be posted the next session plays`() {
        val sp = FakeSpeaker()
        val ind = Indicator().also { it.availableNow = false }
        val p = player(ind, sp)
        assertFalse(p.begin("s1", "m"))
        ind.availableNow = true
        assertTrue(p.begin("s2", "m"))
        assertEquals("Dad", ind.text)
        p.end(flush = false)
        until("banner cleared") { ind.text == null }
    }

    @Test fun `losing the indicator in the middle of a session is noticed so the session can end`() {
        val sp = FakeSpeaker()
        val ind = Indicator()
        val p = player(ind, sp)
        assertTrue(p.indicatorHolds())          // nothing playing: nothing to hold
        assertTrue(p.begin("s1", "m"))
        assertTrue(p.indicatorHolds())
        ind.holds = false                       // overlay lost and no notification possible
        assertFalse(p.indicatorHolds())
        p.end(flush = false)
        until("banner cleared") { ind.text == null }
        assertTrue("idle again", p.indicatorHolds())
    }

    @Test fun `the indicator policy picks the overlay, then a notification that will show, else nothing`() {
        assertEquals(TalkBannerPolicy.Way.OVERLAY, TalkBannerPolicy.way(canOverlay = true, noticeReady = false))
        assertEquals(TalkBannerPolicy.Way.OVERLAY, TalkBannerPolicy.way(canOverlay = true, noticeReady = true))
        assertEquals(TalkBannerPolicy.Way.NOTICE, TalkBannerPolicy.way(canOverlay = false, noticeReady = true))
        assertEquals(TalkBannerPolicy.Way.NONE, TalkBannerPolicy.way(canOverlay = false, noticeReady = false))
        assertTrue(TalkBannerPolicy.mayPlay(canOverlay = true, noticeReady = false))
        assertTrue(TalkBannerPolicy.mayPlay(canOverlay = false, noticeReady = true))
        assertFalse(TalkBannerPolicy.mayPlay(canOverlay = false, noticeReady = false))
    }

    @Test fun `the node status says talk needs permission and the master decodes it, an old node stays ok`() {
        val s = com.nbaltas.nickos.master.NodeStatus.from(org.json.JSONObject("""{"talk":"needs_permission","caps":["talk1"]}"""))
        assertTrue(s.talkNeedsPermission)
        assertFalse(com.nbaltas.nickos.master.NodeStatus.from(org.json.JSONObject("""{"talk":"ok"}""")).talkNeedsPermission)
        assertFalse(com.nbaltas.nickos.master.NodeStatus.from(org.json.JSONObject("{}")).talkNeedsPermission)
    }

    @Test fun `a tablet that needs permission is left out of the press and the bar says why`() {
        val a = TalkTarget("a", "Hailey", online = true, capable = true, canHear = false)
        val b = TalkTarget("b", "Leo", online = true, capable = true)
        assertEquals(listOf("b"), TalkRecipients.freeze(listOf(a, b)).map { it.id })
        val bar = TalkBar.of(listOf(a), null)
        assertFalse(bar.enabled)
        assertEquals("Can't hear you: allow notifications on this tablet", bar.line)
        assertTrue(TalkBar.of(listOf(a, b), null).enabled)
        assertFalse(TalkTarget("a", "Hailey", online = false, capable = true, canHear = false).needsPermission)
    }

    private var failedHook: (String) -> Unit = {}
}
