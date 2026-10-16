package com.nbaltas.nickos.master

import com.nbaltas.nickos.talk.TalkMessages
import com.nbaltas.nickos.talk.TalkStop
import com.nbaltas.nickos.talk.TalkTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.json.JSONObject
import org.junit.After
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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TalkControllerTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @After fun tearDown() { scope.cancel() }

    /** A tablet's link: records what it is given; [gate] holds frames back like a stalled socket; [mode] says how it answers. */
    private class Wire : TalkWire {
        val got = CopyOnWriteArrayList<JSONObject>()
        @Volatile var mode = Sent.OK
        @Volatile var gate: CountDownLatch? = null
        @Volatile var controlOk = true
        val inFrame = CountDownLatch(1)
        override fun control(o: JSONObject): Boolean { if (controlOk) got += o; return controlOk }
        override fun frame(o: JSONObject): Sent {
            inFrame.countDown()
            gate?.await(5, TimeUnit.SECONDS)
            if (mode == Sent.OK) got += o
            return mode
        }
        fun types() = got.map { it.optString("t") }
        fun frames() = got.filter { it.optString("t") == "talk" }
    }

    /** A microphone that hands out one silent frame per [pace] ms, or stops with an error. */
    private class Mic(private val pace: Long = 5) : MicPort {
        val started = AtomicBoolean(false)
        val stopped = AtomicInteger(0)
        @Volatile var fail = false
        override fun start() { started.set(true) }
        override fun read(buf: ByteArray, off: Int, len: Int): Int {
            Thread.sleep(pace)
            if (fail) return -1
            return if (started.get() && stopped.get() == 0) len else -1
        }
        override fun stop() { stopped.incrementAndGet() }
    }

    private fun tgt(id: String, name: String) = TalkTarget(id, name, online = true, capable = true)
    private fun until(what: String, ms: Long = 3000, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + ms
        while (!cond()) {
            if (System.currentTimeMillis() > end) throw AssertionError("timed out waiting for $what")
            Thread.sleep(5)
        }
    }

    private class Rig(scope: CoroutineScope, val mic: Mic, val wires: Map<String, Wire>, clock: () -> Long) {
        val permission = AtomicBoolean(true)
        val stops = CopyOnWriteArrayList<TalkStop>()
        val opened = AtomicInteger(0)
        val ctl = TalkController(
            scope, openMic = { opened.incrementAndGet(); mic }, wireFor = { wires[it] },
            permission = { permission.get() }, clock = clock, onStopped = { stops += it },
        )
    }

    private fun rig(vararg ids: String, clock: () -> Long = System::currentTimeMillis, mic: Mic = Mic()) =
        Rig(scope, mic, ids.associateWith { Wire() }, clock)

    // ---- starting ----
    @Test fun `without the permission a press asks and does not talk`() {
        val r = rig("a")
        r.permission.set(false)
        assertEquals(TalkController.Press.NEEDS_PERMISSION, r.ctl.press(listOf(tgt("a", "Hailey"))))
        assertFalse(r.ctl.state.value.talking)
        assertEquals(0, r.opened.get())
        assertTrue(r.wires.getValue("a").got.isEmpty())
        // Pressed again once allowed, it talks.
        r.permission.set(true)
        assertEquals(TalkController.Press.STARTED, r.ctl.press(listOf(tgt("a", "Hailey"))))
        r.ctl.stop(TalkStop.RELEASED)
    }

    @Test fun `a press with nobody to hear does nothing`() {
        val r = rig("a")
        assertEquals(TalkController.Press.NOBODY, r.ctl.press(emptyList()))
        assertEquals(TalkController.Press.NOBODY, r.ctl.press(listOf(TalkTarget("a", "Hailey", online = true, capable = false))))
        // A tablet that is shown and online but has no link right now is not a recipient either.
        assertEquals(TalkController.Press.NOBODY, r.ctl.press(listOf(tgt("zzz", "Ghost"))))
        assertEquals(0, r.opened.get())
    }

    @Test fun `a second press while talking is busy`() {
        val r = rig("a", "b")
        assertEquals(TalkController.Press.STARTED, r.ctl.press(listOf(tgt("a", "Hailey"))))
        assertEquals(TalkController.Press.BUSY, r.ctl.press(listOf(tgt("b", "Leo"))))
        r.ctl.stop(TalkStop.RELEASED)
    }

    @Test fun `recipients are frozen when the press starts`() {
        val r = rig("a", "b", "c")
        val shown = mutableListOf(tgt("a", "Hailey"), tgt("b", "Leo"))
        r.ctl.press(shown)
        shown += tgt("c", "Mia"); shown.removeAt(0)
        assertEquals(listOf("a", "b"), r.ctl.state.value.ids)
        assertEquals(listOf("Hailey", "Leo"), r.ctl.state.value.names)
        until("frames") { r.wires.getValue("a").frames().size >= 2 }
        r.ctl.stop(TalkStop.RELEASED)
        assertTrue(r.wires.getValue("c").got.isEmpty())
    }

    @Test fun `the session sends start then frames in order then end`() {
        val r = rig("a")
        r.ctl.press(listOf(tgt("a", "Hailey")))
        val sid = r.ctl.state.value.sid!!
        until("frames") { r.wires.getValue("a").frames().size >= 3 }
        r.ctl.stop(TalkStop.RELEASED)
        until("end") { r.wires.getValue("a").types().lastOrNull() == "talkEnd" }
        val w = r.wires.getValue("a")
        assertEquals("talkStart", w.types().first())
        assertEquals(1, w.types().count { it == "talkStart" })
        assertEquals(1, w.types().count { it == "talkEnd" })
        assertTrue(w.got.all { it.optString("sid") == sid })
        val seqs = w.frames().map { it.getLong("seq") }
        assertEquals(seqs.sorted(), seqs)
        assertEquals(seqs.distinct(), seqs)
        assertEquals(1L, seqs.first())
    }

    // ---- every way out ----
    private fun talking(r: Rig, id: String = "a") {
        r.ctl.press(listOf(tgt(id, "Hailey")))
        until("talking") { r.mic.started.get() }
    }

    private fun assertClosed(r: Rig, why: TalkStop) {
        assertFalse(r.ctl.state.value.talking)
        until("mic closed") { r.mic.stopped.get() > 0 }
        until("reason") { r.stops.isNotEmpty() }
        assertEquals(listOf(why), r.stops.toList())
        until("end sent") { r.wires.values.all { it.types().lastOrNull() == "talkEnd" } }
        // And it stays quiet: no frame after the end.
        val sizes = r.wires.mapValues { it.value.got.size }
        Thread.sleep(60)
        assertEquals(sizes, r.wires.mapValues { it.value.got.size })
    }

    @Test fun `letting go stops the microphone and tells the tablet`() {
        val r = rig("a"); talking(r)
        r.ctl.stop(TalkStop.RELEASED)
        assertClosed(r, TalkStop.RELEASED)
    }

    @Test fun `a cancelled press stops the same way`() {
        val r = rig("a"); talking(r)
        r.ctl.stop(TalkStop.CANCELLED)
        assertClosed(r, TalkStop.CANCELLED)
    }

    @Test fun `leaving the screen and the app going to the background stop it`() {
        for (why in listOf(TalkStop.LEFT_SCREEN, TalkStop.BACKGROUNDED)) {
            val r = rig("a"); talking(r)
            r.ctl.stop(why)
            assertClosed(r, why)
        }
    }

    @Test fun `losing the permission stops it`() {
        val r = rig("a"); talking(r)
        r.permission.set(false)
        until("stop") { !r.ctl.state.value.talking }
        assertClosed(r, TalkStop.PERMISSION_LOST)
    }

    @Test fun `a microphone error stops it`() {
        val mic = Mic(); val r = rig("a", mic = mic); talking(r)
        mic.fail = true
        until("stop") { !r.ctl.state.value.talking }
        assertClosed(r, TalkStop.AUDIO_ERROR)
    }

    @Test fun `a microphone that will not start stops it`() {
        val bad = object : MicPort {
            override fun start() { throw IllegalStateException("no mic") }
            override fun read(buf: ByteArray, off: Int, len: Int) = -1
            override fun stop() {}
        }
        val w = Wire()
        val stops = CopyOnWriteArrayList<TalkStop>()
        val ctl = TalkController(scope, { bad }, { w }, { true }, onStopped = { stops += it })
        ctl.press(listOf(tgt("a", "Hailey")))
        until("stop") { stops.isNotEmpty() }
        assertFalse(ctl.state.value.talking)
        assertEquals(listOf(TalkStop.AUDIO_ERROR), stops.toList())
    }

    @Test fun `every tablet disconnecting stops it`() {
        val r = rig("a", "b")
        r.wires.values.forEach { it.mode = Sent.GONE }
        r.ctl.press(listOf(tgt("a", "Hailey"), tgt("b", "Leo")))
        until("stop") { !r.ctl.state.value.talking }
        until("reason") { r.stops.isNotEmpty() }
        assertEquals(listOf(TalkStop.ALL_GONE), r.stops.toList())
        until("mic closed") { r.mic.stopped.get() > 0 }
    }

    @Test fun `one tablet disconnecting does not stop the others`() {
        val r = rig("a", "b")
        r.wires.getValue("a").mode = Sent.GONE
        r.ctl.press(listOf(tgt("a", "Hailey"), tgt("b", "Leo")))
        until("b hears") { r.wires.getValue("b").frames().size >= 3 }
        assertTrue(r.ctl.state.value.talking)
        r.ctl.stop(TalkStop.RELEASED)
    }

    @Test fun `a link that will not take the start drops that tablet at once`() {
        val r = rig("a", "b")
        r.wires.getValue("a").controlOk = false
        r.ctl.press(listOf(tgt("a", "Hailey"), tgt("b", "Leo")))
        until("b hears") { r.wires.getValue("b").frames().size >= 2 }
        assertTrue(r.wires.getValue("a").got.isEmpty())
        r.ctl.stop(TalkStop.RELEASED)
    }

    @Test fun `the time cap stops it`() {
        var t = 1_000_000L
        val r = rig("a", clock = { t }); talking(r)
        until("a frame") { r.wires.getValue("a").frames().isNotEmpty() }
        t += 120_000
        until("stop") { r.stops.isNotEmpty() }
        assertEquals(listOf(TalkStop.TIME_CAP), r.stops.toList())
    }

    @Test fun `stopping twice reports once`() {
        val r = rig("a"); talking(r)
        r.ctl.stop(TalkStop.RELEASED); r.ctl.stop(TalkStop.LEFT_SCREEN)
        assertEquals(listOf(TalkStop.RELEASED), r.stops.toList())
    }

    @Test fun `a release from a button that never talked leaves another press alone`() {
        val r = rig("a"); talking(r)
        r.ctl.stop(TalkStop.RELEASED, sid = "0000000000000000")
        assertTrue(r.ctl.state.value.talking)
        r.ctl.stop(TalkStop.RELEASED, sid = r.ctl.state.value.sid)
        assertFalse(r.ctl.state.value.talking)
    }

    // ---- the sender: one slow tablet ----
    @Test fun `a stalled tablet does not delay the others`() {
        val r = rig("slow", "fast")
        val slow = r.wires.getValue("slow"); val fast = r.wires.getValue("fast")
        slow.gate = CountDownLatch(1) // its link never takes a frame
        r.ctl.press(listOf(tgt("slow", "Hailey"), tgt("fast", "Leo")))
        // 60 frames is a second and a half of speech; the fast tablet has all of them within a moment of real time.
        until("fast tablet", 5000) { fast.frames().size >= 60 }
        assertTrue(slow.frames().isEmpty())
        slow.gate!!.countDown()
        r.ctl.stop(TalkStop.RELEASED)
    }

    @Test fun `a stalled tablet drops its own oldest audio and keeps about a second`() {
        val w = Wire(); w.gate = CountDownLatch(1)
        val sender = TalkSender(scope, "0123456789abcdef", mapOf("a" to w))
        sender.start()
        until("lane stuck on its first frame") { sender.push(1, ByteArray(TalkMessages.FRAME_BYTES)); w.inFrame.count == 0L }
        for (i in 2L..100L) sender.push(i, ByteArray(TalkMessages.FRAME_BYTES))
        // The lane holds one frame in flight; the queue behind it never grows past a second.
        assertTrue("dropped ${sender.dropped("a")}", sender.dropped("a") >= 70)
        w.gate!!.countDown()
        until("newest frame") { w.frames().any { it.getLong("seq") == 100L } }
        sender.finish()
        until("end") { w.types().lastOrNull() == "talkEnd" }
        // What came out is the newest audio: the last frame pushed is there, in order.
        val seqs = w.frames().map { it.getLong("seq") }
        assertTrue(seqs.isNotEmpty() && seqs.last() == 100L)
        assertEquals(seqs.sorted(), seqs)
        assertTrue("sent ${seqs.size}", seqs.size <= 26 + 3)
    }

    @Test fun `a link too busy for a frame drops it and carries on`() {
        val w = Wire(); w.mode = Sent.BUSY
        val sender = TalkSender(scope, "0123456789abcdef", mapOf("a" to w))
        sender.start()
        for (i in 1L..10L) { sender.push(i, ByteArray(TalkMessages.FRAME_BYTES)); Thread.sleep(3) }
        until("drops counted") { sender.dropped("a") >= 5 }
        assertEquals(1, sender.liveRecipients)
        w.mode = Sent.OK
        sender.push(11, ByteArray(TalkMessages.FRAME_BYTES))
        until("frame 11") { w.frames().any { it.getLong("seq") == 11L } }
        sender.finish()
    }

    @Test fun `frames queued behind a stall are thrown away on stop, not sent late`() {
        val w = Wire(); w.gate = CountDownLatch(1)
        val sender = TalkSender(scope, "0123456789abcdef", mapOf("a" to w))
        sender.start()
        until("stuck") { sender.push(1, ByteArray(TalkMessages.FRAME_BYTES)); w.inFrame.count == 0L }
        for (i in 2L..20L) sender.push(i, ByteArray(TalkMessages.FRAME_BYTES))
        sender.finish()
        w.gate!!.countDown()
        until("end") { w.types().lastOrNull() == "talkEnd" }
        assertTrue("sent ${w.frames().size}", w.frames().size <= 2)
    }

    @Test fun `stop does not wait for the microphone thread`() {
        val r = rig("a"); talking(r)
        val t0 = System.nanoTime()
        r.ctl.stop(TalkStop.RELEASED)
        val ms = (System.nanoTime() - t0) / 1e6
        assertTrue("stop took $ms ms", ms < 100)
    }
}
