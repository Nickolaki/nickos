package com.nbaltas.nickos.master

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class PairFallbackTest {
    @Test fun notDueBeforeThreeSeconds() = assertFalse(PairFallback.due(2_999, 0, false, false))

    @Test fun dueAfterTheNsdMiss() = assertTrue(PairFallback.due(3_000, 0, false, false))

    @Test fun notDueWhenNsdFoundSomething() = assertFalse(PairFallback.due(9_000, 1, false, false))

    @Test fun notDueWhenAnAddressWasTyped() = assertFalse(PairFallback.due(9_000, 0, true, false))

    @Test fun runsOnlyOnePass() = assertFalse(PairFallback.due(9_000, 0, false, true))

    @Test fun stopsAtTheFirstSuccess() = runBlocking {
        val tries = AtomicInteger()
        val r = PairFallback.sweep(listOf("192.168.1.10"), { host, _ -> if (host.endsWith(".20") || host.endsWith(".30")) "node-" + host else null }, 47470, maxInFlight = 1) { id, _ ->
            tries.incrementAndGet()
            if (id.endsWith(".20")) "paired" else null
        }
        assertEquals("paired", r)
        assertEquals(1, tries.get())
    }

    @Test fun outstandingProbesDoNotPairAfterTheWin() = runBlocking {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val tries = AtomicInteger()
        val r = PairFallback.sweep(listOf("192.168.1.10"), { host, _ ->
            if (host.endsWith(".20")) "n20" else { gate.await(); "n-" + host }
        }, 47470, maxInFlight = 30) { id, _ ->
            tries.incrementAndGet()
            gate.complete(Unit)
            if (id == "n20") "paired" else "late"
        }
        assertEquals("paired", r)
        assertEquals(1, tries.get())
    }

    @Test fun cancelledSweepPairsNothingMore() = runBlocking {
        val tries = AtomicInteger()
        val started = kotlinx.coroutines.CompletableDeferred<Unit>()
        val job = async(kotlinx.coroutines.Dispatchers.Default) {
            PairFallback.sweep(listOf("192.168.1.10"), { _, _ -> started.complete(Unit); kotlinx.coroutines.awaitCancellation() }, 47470, maxInFlight = 4) { _, _ ->
                tries.incrementAndGet(); "x"
            }
        }
        started.await()
        job.cancel()
        job.join()
        assertEquals(0, tries.get())
    }

    @Test fun twoReadyProbesPairOnlyOnce() = runBlocking {
        val tries = AtomicInteger()
        val r = PairFallback.sweep(listOf("192.168.1.10"), { host, _ -> if (host.endsWith(".20") || host.endsWith(".30")) "n" + host else null }, 47470, maxInFlight = 2) { id, _ ->
            tries.incrementAndGet()
            kotlinx.coroutines.delay(50) // the other probe is ready and waits at the lock meanwhile
            "paired-" + id
        }
        assertTrue(r!!.startsWith("paired-"))
        assertEquals(1, tries.get())
    }

    @Test fun cancelBeforeTheLockPairsNothing() = runBlocking {
        val tries = AtomicInteger()
        val inFirst = kotlinx.coroutines.CompletableDeferred<Unit>()
        val job = async(kotlinx.coroutines.Dispatchers.Default) {
            PairFallback.sweep(listOf("192.168.1.10"), { host, _ -> if (host.endsWith(".20") || host.endsWith(".30")) "n" + host else null }, 47470, maxInFlight = 2) { _, _ ->
                tries.incrementAndGet()
                inFirst.complete(Unit)
                kotlinx.coroutines.delay(10_000) // holds the lock; the second probe waits for it
                null as String?
            }
        }
        inFirst.await()
        job.cancel()
        job.join()
        assertEquals(1, tries.get())
    }

    @Test fun tryPairOnOpenHostsOnly() = runBlocking {
        val tried = mutableListOf<String>()
        val r = PairFallback.sweep(listOf("10.0.0.5"), { host, _ -> if (host == "10.0.0.7") "id7" else null }, 47470, maxInFlight = 1) { id, _ ->
            tried += id
            null as String?
        }
        assertNull(r)
        assertEquals(listOf("id7"), tried)
    }

    @Test fun noAddressMeansNoSweep() = runBlocking {
        val r = PairFallback.sweep(emptyList(), { _, _ -> "x" }, 47470) { _, _ -> "never" }
        assertNull(r)
    }
}
