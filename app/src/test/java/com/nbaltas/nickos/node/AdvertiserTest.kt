package com.nbaltas.nickos.node

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class AdvertiserTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @After fun tearDown() { scope.cancel() }

    /** A fake NSD: records every call, keeps count of live registrations and can hold back acks. */
    private class FakeGate : AdvertGate {
        val log = ConcurrentLinkedQueue<String>()
        val live = AtomicInteger()
        val peakLive = AtomicInteger()
        val registers = AtomicInteger()
        @Volatile var failFirst = 0
        @Volatile var holdAcks = false
        @Volatile var neverAck = false
        private val held = ConcurrentLinkedQueue<() -> Unit>()

        override fun register(port: Int, onResult: (Boolean) -> Unit): Any {
            val n = registers.incrementAndGet()
            log += "register#$n"
            val h = Any()
            if (n <= failFirst) { onResult(false); return h }
            val l = live.incrementAndGet()
            peakLive.accumulateAndGet(l) { a, b -> maxOf(a, b) }
            onResult(true)
            return h
        }

        override fun unregister(handle: Any, onDone: () -> Unit) {
            log += "unregister"
            live.decrementAndGet()
            if (neverAck) return
            if (holdAcks) held += onDone else onDone()
        }

        fun releaseAcks() { holdAcks = false; while (true) (held.poll() ?: break)() }
    }

    private fun advertiser(g: FakeGate, ackMs: Long = 200, retryMs: Long = 5) = Advertiser(scope, g, ackTimeoutMs = ackMs, resultTimeoutMs = 200, retryMs = retryMs)

    private fun waitFor(what: String, cond: () -> Boolean) {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!cond()) { check(System.nanoTime() < end) { "timed out: $what" }; Thread.sleep(1) }
    }

    @Test
    fun `start registers once`() {
        val g = FakeGate()
        advertiser(g).start(47470)
        waitFor("register") { g.registers.get() == 1 }
        assertEquals(listOf("register#1"), g.log.toList())
    }

    @Test
    fun `refresh unregisters, waits for the ack, then registers again with a fresh registration`() {
        val g = FakeGate()
        val a = advertiser(g)
        a.start(47470)
        waitFor("first") { g.registers.get() == 1 }
        a.refresh()
        waitFor("second") { g.registers.get() == 2 }
        assertEquals(listOf("register#1", "unregister", "register#2"), g.log.toList())
    }

    @Test
    fun `the new registration waits for the old one's ack`() {
        val g = FakeGate()
        val a = advertiser(g, ackMs = 2000)
        a.start(47470)
        waitFor("first") { g.registers.get() == 1 }
        g.holdAcks = true
        a.refresh()
        waitFor("unregister sent") { g.log.contains("unregister") }
        Thread.sleep(40)
        assertEquals("registered before the old one was acknowledged", 1, g.registers.get())
        g.releaseAcks()
        waitFor("second") { g.registers.get() == 2 }
    }

    @Test
    fun `a missing ack does not wedge it`() {
        val g = FakeGate()
        val a = advertiser(g, ackMs = 30)
        a.start(47470)
        waitFor("first") { g.registers.get() == 1 }
        g.neverAck = true
        a.refresh()
        waitFor("second despite no ack") { g.registers.get() == 2 }
    }

    @Test
    fun `a burst of network changes never leaves two live registrations and collapses queued refreshes`() {
        val g = FakeGate()
        val a = advertiser(g)
        a.start(47470)
        waitFor("first") { g.registers.get() == 1 }
        repeat(20) { a.refresh() }
        waitFor("settled") { g.registers.get() >= 2 }
        Thread.sleep(80)
        assertTrue("registers ${g.registers.get()}", g.registers.get() in 2..3)
        assertEquals(1, g.live.get())
        assertEquals(1, g.peakLive.get())
    }

    @Test
    fun `a registration that fails right after a network change is retried`() {
        val g = FakeGate()
        g.failFirst = 2
        advertiser(g).start(47470)
        waitFor("third try") { g.registers.get() == 3 }
        assertEquals(1, g.live.get())
    }

    @Test
    fun `stop during a refresh means nothing registers after the ack`() {
        val g = FakeGate()
        val a = advertiser(g, ackMs = 2000)
        a.start(47470)
        waitFor("first") { g.registers.get() == 1 }
        g.holdAcks = true
        a.refresh()
        waitFor("unregister sent") { g.log.contains("unregister") }
        a.stop()
        g.releaseAcks()
        Thread.sleep(60)
        assertEquals(1, g.registers.get())
        assertEquals(0, g.live.get())
    }

    @Test
    fun `refresh before start or after stop does nothing`() {
        val g = FakeGate()
        val a = advertiser(g)
        a.refresh()
        Thread.sleep(30)
        assertEquals(0, g.registers.get())
        a.start(47470)
        waitFor("first") { g.registers.get() == 1 }
        a.stop()
        waitFor("stopped") { g.live.get() == 0 }
        a.refresh()
        Thread.sleep(30)
        assertEquals(1, g.registers.get())
        assertFalse(g.log.contains("register#2"))
    }
}
