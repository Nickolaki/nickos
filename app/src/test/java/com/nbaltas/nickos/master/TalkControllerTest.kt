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

    // ---- ownership: starting, stopping and presses that overlap ----
    /** Runs what it is given only when the test says so, so an interleaving can be set up exactly. */
    private class Manual : kotlinx.coroutines.CoroutineDispatcher() {
        val tasks = CopyOnWriteArrayList<Runnable>()
        override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) { tasks += block }
        fun runAll() { while (tasks.isNotEmpty()) tasks.removeAt(0).run() }
        fun runNewest() { if (tasks.isNotEmpty()) tasks.removeAt(tasks.size - 1).run() }
        fun runOldest() { if (tasks.isNotEmpty()) tasks.removeAt(0).run() }
    }

    @Test fun `letting go before the microphone has started means it never starts`() {
        val io = Manual()
        val mic = Mic(); val w = Wire()
        val ctl = TalkController(scope, { mic }, { w }, { true }, io = io)
        ctl.press(listOf(tgt("a", "Hailey")))
        ctl.stop(TalkStop.RELEASED)          // the start is still waiting to run
        io.runAll()                          // now the start and the close run, in that order
        assertFalse("the microphone was opened after the press ended", mic.started.get())
        assertTrue(mic.stopped.get() >= 1)
    }

    @Test fun `going to the background during microphone start returns immediately and records nothing`() {
        val entered = CountDownLatch(1); val go = CountDownLatch(1)
        val closed = AtomicInteger(0); val reads = AtomicInteger(0)
        val slow = object : MicPort {
            override fun start() { entered.countDown(); go.await(5, TimeUnit.SECONDS) }
            override fun read(buf: ByteArray, off: Int, len: Int): Int { reads.incrementAndGet(); return -1 }
            override fun stop() { closed.incrementAndGet() }
        }
        val w = Wire()
        val ctl = TalkController(scope, { slow }, { w }, { true })
        ctl.press(listOf(tgt("a", "Hailey")))
        assertTrue(entered.await(3, TimeUnit.SECONDS))
        val began = System.nanoTime()
        ctl.stop(TalkStop.BACKGROUNDED)
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - began) < 100)
        assertEquals(1L, go.count)
        go.countDown()
        until("closed after the start") { closed.get() >= 1 }
        assertEquals("nothing was recorded", 0, reads.get())
        assertTrue(w.frames().isEmpty())
    }

    @Test fun `a released press never opens the microphone after stop returned`() {
        val late = AtomicInteger(0)
        repeat(60) { i ->
            val returned = AtomicBoolean(false)
            val mic = object : MicPort {
                override fun start() { if (returned.get()) late.incrementAndGet() else Thread.sleep(if (i % 2 == 0) 3 else 0) }
                override fun read(buf: ByteArray, off: Int, len: Int): Int { Thread.sleep(2); return -1 }
                override fun stop() {}
            }
            val w = Wire()
            val ctl = TalkController(scope, { mic }, { w }, { true })
            ctl.press(listOf(tgt("a", "Hailey")))
            if (i % 3 == 0) Thread.sleep((i % 5).toLong())
            ctl.stop(TalkStop.RELEASED)
            returned.set(true)
            Thread.sleep(8)
        }
        assertEquals("a microphone was opened after its press was released", 0, late.get())
    }

    @Test fun `an admitted start does not block release and is closed before recording`() {
        val inStart = CountDownLatch(1); val go = CountDownLatch(1)
        val starts = AtomicInteger(0); val finished = AtomicBoolean(false)
        val mic = object : MicPort {
            override fun start() { starts.incrementAndGet(); inStart.countDown(); go.await(5, TimeUnit.SECONDS); finished.set(true) }
            override fun read(buf: ByteArray, off: Int, len: Int): Int = -1
            override fun stop() {}
        }
        val ctl = TalkController(scope, { mic }, { Wire() }, { true })
        ctl.press(listOf(tgt("a", "Hailey")))
        assertTrue(inStart.await(3, TimeUnit.SECONDS))
        ctl.stop(TalkStop.RELEASED)
        assertFalse("stop must return while startup is blocked", finished.get())
        go.countDown()
        Thread.sleep(100)
        assertEquals(1, starts.get())
    }

    @Test fun `a new press waits for the old microphone to be closed before opening its own`() {
        val closeGate = CountDownLatch(1)
        val first = object : MicPort {
            val started = AtomicBoolean(false); val stopped = AtomicInteger(0)
            override fun start() { started.set(true) }
            override fun read(buf: ByteArray, off: Int, len: Int): Int { Thread.sleep(5); return if (stopped.get() == 0) len else -1 }
            override fun stop() { stopped.incrementAndGet(); closeGate.await(5, TimeUnit.SECONDS) }
        }
        val second = Mic()
        val mics = java.util.ArrayDeque<MicPort>(listOf(first, second))
        val w = Wire()
        val ctl = TalkController(scope, { mics.removeFirst() }, { w }, { true })
        ctl.press(listOf(tgt("a", "Hailey")))
        until("first open") { first.started.get() }
        ctl.stop(TalkStop.RELEASED)
        until("old close begun") { first.stopped.get() >= 1 }
        assertEquals(TalkController.Press.STARTED, ctl.press(listOf(tgt("a", "Hailey"))))
        Thread.sleep(300)
        assertFalse("the new press opened its microphone while the old one was still closing", second.started.get())
        closeGate.countDown()
        until("second open") { second.started.get() }
        ctl.stop(TalkStop.RELEASED)
    }

    @Test fun `a new press whose old microphone is still closing after the wait fails and records nothing`() {
        val closeGate = CountDownLatch(1)
        val order = CopyOnWriteArrayList<String>()
        val first = object : MicPort {
            val started = AtomicBoolean(false)
            override fun start() { started.set(true); order += "start1" }
            override fun read(buf: ByteArray, off: Int, len: Int): Int { Thread.sleep(5); return len }
            override fun stop() { closeGate.await(10, TimeUnit.SECONDS); order += "stop1" }
        }
        val second = Mic(); val third = Mic()
        val mics = java.util.ArrayDeque<MicPort>(listOf(first, second, third))
        val w = Wire()
        val stops = CopyOnWriteArrayList<TalkStop>()
        val ctl = TalkController(scope, { mics.removeFirst() }, { w }, { true }, onStopped = { stops += it }, oldCloseWaitMs = 200)
        ctl.press(listOf(tgt("a", "Hailey")))
        until("first open") { first.started.get() }
        ctl.stop(TalkStop.RELEASED)
        assertEquals(TalkController.Press.STARTED, ctl.press(listOf(tgt("a", "Hailey"))))
        until("second press failed") { ctl.state.value.tryAgain }
        assertFalse(ctl.state.value.talking)
        assertFalse("no recording", second.started.get())
        assertEquals(listOf(TalkStop.RELEASED, TalkStop.MIC_BUSY), stops.toList())
        // Still stuck: the next press must not slip in behind the failed one.
        assertEquals(TalkController.Press.STARTED, ctl.press(listOf(tgt("a", "Hailey"))))
        assertFalse(ctl.state.value.tryAgain)
        until("third press failed") { ctl.state.value.tryAgain }
        assertFalse("no recording", third.started.get())
        closeGate.countDown()
        until("old closed") { order.contains("stop1") }
        // Once the old microphone is closed a press works again.
        val fourth = Mic(); mics.addFirst(fourth)
        until("fourth press starts", 4000) {
            if (!ctl.state.value.talking) ctl.press(listOf(tgt("a", "Hailey")))
            fourth.started.get()
        }
        assertFalse(ctl.state.value.tryAgain)
        ctl.stop(TalkStop.RELEASED)
    }

    @Test fun `a late failure of an old press cannot stop a newer one`() {
        val boom = CountDownLatch(1)
        val old = object : MicPort {
            override fun start() {}
            override fun read(buf: ByteArray, off: Int, len: Int): Int { boom.await(5, TimeUnit.SECONDS); throw IllegalStateException("late error") }
            override fun stop() { boom.countDown() }
        }
        val fresh = Mic()
        val mics = java.util.ArrayDeque<MicPort>(listOf(old, fresh))
        val w = Wire()
        val stops = CopyOnWriteArrayList<TalkStop>()
        val ctl = TalkController(scope, { mics.removeFirst() }, { w }, { true }, onStopped = { stops += it })
        ctl.press(listOf(tgt("a", "Hailey")))
        val oldSid = ctl.state.value.sid!!
        ctl.stop(TalkStop.RELEASED, oldSid)
        assertEquals(TalkController.Press.STARTED, ctl.press(listOf(tgt("a", "Hailey"))))
        val newSid = ctl.state.value.sid!!
        until("fresh open") { fresh.started.get() }
        Thread.sleep(150) // the old press's error has surfaced by now
        assertTrue("newer press still live", ctl.state.value.talking)
        assertEquals(newSid, ctl.state.value.sid)
        assertEquals(listOf(TalkStop.RELEASED), stops.toList())
        ctl.stop(TalkStop.RELEASED, newSid)
    }

    @Test fun `an old press's delayed talkStart never reaches the wire after a newer press began`() {
        val lanes = Manual()
        val w = Wire()
        val mics = { Mic() }
        val ctl = TalkController(scope, mics, { w }, { true }, lanes = lanes)
        ctl.press(listOf(tgt("a", "Hailey")))       // press A: its sender has not run yet
        val a = ctl.state.value.sid!!
        ctl.stop(TalkStop.RELEASED, a)
        ctl.press(listOf(tgt("a", "Hailey")))       // press B
        val b = ctl.state.value.sid!!
        lanes.runNewest()                            // B's sender runs first and sends its start
        lanes.runOldest()                            // then the delayed A runs: its start must not be sent
        until("b frames", 4000) { lanes.runAll(); w.frames().isNotEmpty() }
        assertEquals(listOf("talkStart"), w.types().take(1))
        assertEquals(1, w.types().count { it == "talkStart" })
        assertTrue("only the newer press is on the wire", w.got.all { it.optString("sid") == b })
        assertTrue(ctl.state.value.talking)
        assertEquals(b, ctl.state.value.sid)
        ctl.stop(TalkStop.RELEASED, b)
        until("b end") { lanes.runAll(); w.types().lastOrNull() == "talkEnd" }
        assertTrue(w.got.none { it.optString("sid") == a })
    }

    @Test fun `an old press's end is not sent after a newer press took the tablet`() {
        val lanes = Manual()
        val w = Wire()
        val ctl = TalkController(scope, { Mic() }, { w }, { true }, lanes = lanes)
        ctl.press(listOf(tgt("a", "Hailey")))
        val a = ctl.state.value.sid!!
        lanes.runAll()                               // A's start is sent
        ctl.stop(TalkStop.RELEASED, a)
        ctl.press(listOf(tgt("a", "Hailey")))        // B takes the tablet before A's end went out
        val b = ctl.state.value.sid!!
        lanes.runAll()
        until("b start", 4000) { lanes.runAll(); w.got.any { it.optString("sid") == b && it.optString("t") == "talkStart" } }
        assertTrue("A's end must not follow B's start", w.got.none { it.optString("sid") == a && it.optString("t") == "talkEnd" } ||
            w.got.indexOfFirst { it.optString("sid") == a && it.optString("t") == "talkEnd" } < w.got.indexOfFirst { it.optString("sid") == b })
        ctl.stop(TalkStop.RELEASED, b)
    }

    @Test fun `stop does not wait for the microphone thread`() {
        val r = rig("a"); talking(r)
        val t0 = System.nanoTime()
        r.ctl.stop(TalkStop.RELEASED)
        val ms = (System.nanoTime() - t0) / 1e6
        assertTrue("stop took $ms ms", ms < 100)
    }
}
