package com.nbaltas.nickos.master

import com.nbaltas.nickos.link.LanAddress
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class SweepTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @After fun tearDown() { scope.cancel() }

    private val nodeA = "aaaaaaaaaaaa"
    private val nodeB = "bbbbbbbbbbbb"

    private class Rig(
        val table: FoundTable = FoundTable(),
        val found: ConcurrentLinkedQueue<Pair<String, Pair<String, Int>>> = ConcurrentLinkedQueue(),
    )

    private fun sweep(
        rig: Rig,
        ownIp: () -> String? = { "192.168.43.1" },
        missing: () -> Set<String>,
        prober: Prober,
        config: Sweep.Config = Sweep.Config(),
        clock: () -> Long = System::currentTimeMillis,
        ownIps: (() -> List<String>)? = null,
    ) = Sweep(scope, ownIps ?: { listOfNotNull(ownIp()) }, missing, prober, rig.table, { id, addr -> rig.found += id to addr }, config, clock)

    private fun waitFor(what: String, cond: () -> Boolean) {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!cond()) { check(System.nanoTime() < end) { "timed out: $what" }; Thread.sleep(1) }
    }

    @Test
    fun `a host answering iam for a missing node is reported once`() = runBlocking {
        val rig = Rig()
        val s = sweep(rig, missing = { setOf(nodeA) }, prober = { host, _ -> if (host == "192.168.43.50" || host == "192.168.43.51") nodeA else null })
        val reported = s.pass()
        assertEquals(setOf(nodeA), reported)
        assertEquals(1, rig.found.size)           // two hosts claimed the id; only one is taken
        assertEquals(nodeA, rig.found.first().first)
        assertEquals(rig.found.first().second, rig.table[nodeA])
    }

    @Test
    fun `a host that closes the socket is not a node`() = runBlocking {
        val rig = Rig()
        val s = sweep(rig, missing = { setOf(nodeA) }, prober = { _, _ -> null })
        assertTrue(s.pass().isEmpty())
        assertTrue(rig.found.isEmpty())
        assertNull(rig.table[nodeA])
    }

    @Test
    fun `a stranger answering with an id nobody asked about is ignored`() = runBlocking {
        val rig = Rig()
        val s = sweep(rig, missing = { setOf(nodeA) }, prober = { host, _ -> if (host == "192.168.43.9") nodeB else null })
        assertTrue(s.pass().isEmpty())
        assertNull(rig.table[nodeB])
        assertTrue(rig.found.isEmpty())
    }

    @Test
    fun `no sweep when no node is missing`() = runBlocking {
        val rig = Rig()
        val calls = AtomicInteger()
        val s = sweep(rig, missing = { emptySet() }, prober = { _, _ -> calls.incrementAndGet(); null })
        s.pass()
        assertEquals(0, calls.get())
    }

    @Test
    fun `no sweep without a lan address, or on a public one`() = runBlocking {
        val calls = AtomicInteger()
        val p = Prober { _, _ -> calls.incrementAndGet(); null }
        sweep(Rig(), ownIp = { null }, missing = { setOf(nodeA) }, prober = p).pass()
        sweep(Rig(), ownIp = { "8.8.8.8" }, missing = { setOf(nodeA) }, prober = p).pass()
        assertEquals(0, calls.get())
    }

    @Test
    fun `never more than 32 probes in flight and every host is tried`() = runBlocking {
        val inFlight = AtomicInteger()
        val peak = AtomicInteger()
        val tried = ConcurrentLinkedQueue<String>()
        val s = sweep(Rig(), missing = { setOf(nodeA) }, prober = { host, _ ->
            val now = inFlight.incrementAndGet()
            peak.accumulateAndGet(now) { a, b -> maxOf(a, b) }
            tried += host
            delay(2)
            inFlight.decrementAndGet()
            null
        })
        s.pass()
        assertTrue("peak ${peak.get()}", peak.get() <= 32)
        assertTrue("peak ${peak.get()}", peak.get() > 1)
        assertEquals(253, tried.toSet().size)
    }

    @Test
    fun `the pass has a hard cut off even if every probe hangs`() = runBlocking {
        val s = sweep(Rig(), missing = { setOf(nodeA) }, prober = { _, _ -> awaitCancellation() },
            config = Sweep.Config(passBudgetMs = 60))
        val t0 = System.nanoTime()
        assertTrue(s.pass().isEmpty())
        assertTrue("took ${(System.nanoTime() - t0) / 1_000_000} ms", (System.nanoTime() - t0) / 1_000_000 < 1500)
    }

    @Test
    fun `the pass stops probing once the node links another way`() = runBlocking {
        val missingNow = java.util.concurrent.atomic.AtomicBoolean(true)
        val calls = AtomicInteger()
        val s = sweep(Rig(), missing = { if (missingNow.get()) setOf(nodeA) else emptySet() }, prober = { _, _ ->
            if (calls.incrementAndGet() == 5) missingNow.set(false)   // mDNS found it mid-pass
            delay(1)
            null
        })
        s.pass()
        assertTrue("probed ${calls.get()}", calls.get() < 100)
    }

    @Test
    fun `an answer that arrives after the network changed is dropped`() = runBlocking {
        val rig = Rig()
        val gate = CompletableDeferred<Unit>()
        val started = CountDownLatch(1)
        val s = sweep(rig, missing = { setOf(nodeA) }, prober = { host, _ ->
            if (host == "192.168.43.60") { started.countDown(); gate.await(); nodeA } else null
        })
        val pass = scope.async { s.pass() }
        assertTrue(started.await(2, TimeUnit.SECONDS))
        rig.table.reset()                 // the network changed: discovery restarted, the table was cleared
        gate.complete(Unit)               // the old subnet's late answer arrives now
        assertTrue(pass.await().isEmpty())
        assertTrue(rig.found.isEmpty())
        assertNull(rig.table[nodeA])
    }

    @Test
    fun `an answer is dropped when the own address moved to another subnet mid pass`() = runBlocking {
        val rig = Rig()
        val ip = java.util.concurrent.atomic.AtomicReference("192.168.43.1")
        val gate = CompletableDeferred<Unit>()
        val started = CountDownLatch(1)
        val s = sweep(rig, ownIp = { ip.get() }, missing = { setOf(nodeA) }, prober = { host, _ ->
            if (host == "192.168.43.60") { started.countDown(); gate.await(); nodeA } else null
        })
        val pass = scope.async { s.pass() }
        assertTrue(started.await(2, TimeUnit.SECONDS))
        ip.set("10.1.2.3")
        gate.complete(Unit)
        assertTrue(pass.await().isEmpty())
        assertTrue(rig.found.isEmpty())
    }

    @Test
    fun `now cancels the pass in flight and starts over on the new subnet`() {
        val rig = Rig()
        val ip = java.util.concurrent.atomic.AtomicReference("192.168.43.1")
        val cancelled = CountDownLatch(1)
        val newSubnetProbed = CountDownLatch(1)
        val s = sweep(rig, ownIp = { ip.get() }, missing = { setOf(nodeA) }, prober = { host, _ ->
            if (host.startsWith("192.168.43.")) {
                try { awaitCancellation() } finally { cancelled.countDown() }
            } else {
                newSubnetProbed.countDown()
                if (host == "10.1.2.77") nodeA else null
            }
        })
        s.now()
        Thread.sleep(30)
        ip.set("10.1.2.3")
        rig.table.reset()
        s.now()
        assertTrue("old pass was not cancelled", cancelled.await(2, TimeUnit.SECONDS))
        assertTrue(newSubnetProbed.await(2, TimeUnit.SECONDS))
        waitFor("node found on the new subnet") { rig.found.isNotEmpty() }
        assertEquals(nodeA to ("10.1.2.77" to 47470), rig.found.first())
    }

    @Test
    fun `a master hosting its own hotspot sweeps the subnet of its ap interface`() = runBlocking {
        // The tablet is on no WiFi at all; its only private address is the hotspot interface it hosts.
        val own = LanAddress.choose(listOf("rmnet_data0" to "10.55.1.9", "ap0" to "192.168.43.1"), wifi = null)
        assertEquals("192.168.43.1", own)
        val hosts = ConcurrentLinkedQueue<String>()
        val rig = Rig()
        val s = sweep(rig, ownIp = { own }, missing = { setOf(nodeA) }, prober = { host, _ ->
            hosts += host
            if (host == "192.168.43.2") nodeA else null     // the first client the hotspot handed an address to
        })
        assertEquals(setOf(nodeA), s.pass())
        assertEquals(nodeA to ("192.168.43.2" to 47470), rig.found.first())
        assertTrue(hosts.isNotEmpty() && hosts.all { it.startsWith("192.168.43.") })
        assertFalse("192.168.43.1" in hosts)             // never probes itself
    }

    @Test
    fun `swlan counts as the hotspot interface too`() {
        assertEquals("192.168.1.1", LanAddress.choose(listOf("rmnet0" to "10.55.1.9", "swlan0" to "192.168.1.1"), wifi = null))
    }

    // ---- the timer ----

    @Test
    fun `the timer waits 15 s, then sweeps, then repeats every 20 s, and idles when nothing is missing`() {
        var now = 1_000_000L
        val rig = Rig()
        val missing = java.util.concurrent.atomic.AtomicReference<Set<String>>(setOf(nodeA))
        val s = sweep(rig, missing = { missing.get() }, prober = { _, _ -> null }, clock = { now })
        s.tick()                                   // first sight of the node missing
        now += 14_000; s.tick()                    // 14 s: not yet
        Thread.sleep(20)
        assertEquals(0, s.passCount)
        now += 1_000; s.tick()                     // 15 s: sweep
        waitFor("first pass") { s.passCount == 1 }
        now += 10_000; s.tick()                    // 10 s later: too soon to repeat
        Thread.sleep(20)
        assertEquals(1, s.passCount)
        now += 10_000; s.tick()                    // 20 s after the first: repeat
        waitFor("second pass") { s.passCount == 2 }
        missing.set(emptySet())                    // it linked
        now += 60_000; s.tick()
        Thread.sleep(20)
        assertEquals(2, s.passCount)
        missing.set(setOf(nodeA))                  // and went missing again: the 15 s clock restarts
        now += 60_000; s.tick()
        Thread.sleep(20)
        assertEquals(2, s.passCount)
    }

    @Test
    fun `home wifi and the hosted car network are both swept, the car network first`() = runBlocking {
        val tried = ConcurrentLinkedQueue<String>()
        val s = sweep(Rig(), ownIps = { listOf("192.168.1.20", "192.168.49.1") }, missing = { setOf(nodeA) }, prober = { host, _ ->
            tried += host; null
        })
        s.pass()
        val order = tried.toList()
        assertTrue(order.first().startsWith("192.168.49."))
        assertEquals(506, order.toSet().size)
    }

    @Test
    fun `a node found on the car network is reported with the car network address`() = runBlocking {
        val rig = Rig()
        val s = sweep(rig, ownIps = { listOf("192.168.1.20", "192.168.49.1") }, missing = { setOf(nodeA) },
            prober = { host, _ -> if (host == "192.168.49.2") nodeA else null })
        assertEquals(setOf(nodeA), s.pass())
        assertEquals(nodeA to ("192.168.49.2" to 47470), rig.found.first())
    }

    @Test
    fun `none of this tablet's own addresses is ever probed`() = runBlocking {
        val tried = ConcurrentLinkedQueue<String>()
        val s = sweep(Rig(), ownIps = { listOf("192.168.1.20", "192.168.49.1") }, missing = { setOf(nodeA) }, prober = { host, _ ->
            tried += host; null
        })
        s.pass()
        assertFalse("192.168.1.20" in tried)
        assertFalse("192.168.49.1" in tried)
    }

    @Test
    fun `an answer is dropped when the car network came up mid pass`() = runBlocking {
        val rig = Rig()
        val ips = java.util.concurrent.atomic.AtomicReference(listOf("192.168.1.20"))
        val gate = CompletableDeferred<Unit>()
        val started = CountDownLatch(1)
        val s = sweep(rig, ownIps = { ips.get() }, missing = { setOf(nodeA) }, prober = { host, _ ->
            if (host == "192.168.1.60") { started.countDown(); gate.await(); nodeA } else null
        })
        val pass = scope.async { s.pass() }
        assertTrue(started.await(2, TimeUnit.SECONDS))
        ips.set(listOf("192.168.1.20", "192.168.49.1"))
        gate.complete(Unit)
        assertTrue(pass.await().isEmpty())
        assertTrue(rig.found.isEmpty())
    }
}
