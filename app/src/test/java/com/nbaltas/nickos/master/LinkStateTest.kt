package com.nbaltas.nickos.master

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class LinkStateTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @After fun tearDown() { scope.cancel() }

    // ---- LinkBackoff ----

    @Test
    fun `three failures in a row clear the address and a success resets the count`() {
        val b = LinkBackoff()
        assertFalse(b.failure())
        assertFalse(b.failure())
        b.success()
        assertFalse(b.failure())
        assertFalse(b.failure())
        assertTrue(b.failure())      // the third in a row
        assertTrue(b.failure())      // and it stays tripped until something works
        b.success()
        assertFalse(b.failure())
    }

    // ---- FoundTable ----

    @Test
    fun `a result from before a network change cannot put a stale address back`() {
        val t = FoundTable()
        val old = t.epoch
        assertTrue(t.put(old, "a", "192.168.1.5" to 47470))
        val now = t.reset()
        assertTrue(t.ids().isEmpty())
        assertFalse(t.put(old, "a", "192.168.1.5" to 47470))   // late NSD resolve from the old network
        assertFalse(t.remove(old, "a"))
        assertNull(t["a"])
        assertTrue(t.put(now, "a", "192.168.43.7" to 47470))
        assertEquals("192.168.43.7" to 47470, t["a"])
    }

    @Test
    fun `dropping a dead address leaves a newer one alone`() {
        val t = FoundTable()
        val e = t.epoch
        t.put(e, "a", "192.168.43.7" to 47470)
        t.put(e, "a", "192.168.43.9" to 47470)         // the sweep found it somewhere else meanwhile
        assertFalse(t.removeIf("a", "192.168.43.7" to 47470))   // the loop that failed on .7 must not erase .9
        assertEquals("192.168.43.9" to 47470, t["a"])
        assertTrue(t.removeIf("a", "192.168.43.9" to 47470))
        assertNull(t["a"])
    }

    // ---- LinkTable: duplicate links to one Node ----

    @Test
    fun `a replaced link cannot release its successor`() {
        val t = LinkTable<String>()
        assertNull(t.claim("n", "first"))
        assertEquals("first", t.claim("n", "second"))          // reconnect raced discovery: two links
        assertFalse(t.release("n", "first"))                   // the old one's cleanup finds it is stale
        assertEquals("second", t["n"])
        assertTrue(t.release("n", "second"))
        assertNull(t["n"])
    }

    @Test
    fun `drop removes whatever is current`() {
        val t = LinkTable<String>()
        t.claim("n", "x")
        assertEquals("x", t.drop("n"))
        assertNull(t.drop("n"))
        assertFalse(t.release("n", "x"))
    }

    // ---- LinkLoops: one loop per Node ----

    @Test
    fun `many threads asking for a loop start exactly one`() {
        val started = AtomicInteger()
        val loops = LinkLoops(scope) { started.incrementAndGet(); awaitCancellation() }
        val go = CountDownLatch(1)
        val threads = (1..16).map { Thread { go.await(); loops.ensure("n") } }
        threads.forEach { it.start() }
        go.countDown()
        threads.forEach { it.join(2000) }
        Thread.sleep(50)
        assertEquals(1, started.get())
        loops.ensure("n")
        Thread.sleep(20)
        assertEquals(1, started.get())
    }

    @Test
    fun `restart cancels the old loop and runs exactly one new one`() {
        val started = AtomicInteger()
        val cancelledOld = CountDownLatch(1)
        val loops = LinkLoops(scope) {
            if (started.incrementAndGet() == 1) { try { awaitCancellation() } finally { cancelledOld.countDown() } } else awaitCancellation()
        }
        loops.ensure("n")
        while (started.get() < 1) Thread.sleep(1)
        loops.restart("n")
        assertTrue(cancelledOld.await(2, TimeUnit.SECONDS))
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        while (started.get() < 2 && System.nanoTime() < end) Thread.sleep(1)
        assertEquals(2, started.get())
        assertEquals(setOf("n"), loops.ids())
        loops.cancel("n")
        assertTrue(loops.ids().isEmpty())
    }

    @Test
    fun `a finished loop is started again by ensure`() {
        val started = AtomicInteger()
        val loops = LinkLoops(scope) { started.incrementAndGet() }   // returns at once: the Node was unlinked
        loops.ensure("n")
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        while (started.get() < 1 && System.nanoTime() < end) Thread.sleep(1)
        Thread.sleep(20)
        loops.ensure("n")
        while (started.get() < 2 && System.nanoTime() < end) Thread.sleep(1)
        assertEquals(2, started.get())
        assertSame(loops, loops)
    }
}
