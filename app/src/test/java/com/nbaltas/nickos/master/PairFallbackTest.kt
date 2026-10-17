package com.nbaltas.nickos.master

import kotlinx.coroutines.async
import kotlinx.coroutines.launch
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

    private class FakeSocket : java.io.Closeable {
        val closed = java.util.concurrent.atomic.AtomicBoolean(false)
        override fun close() { closed.set(true) }
    }

    /** Mirrors tryPair: track, blocking handshake, then commit the writes. */
    private fun fakeTryPair(a: PairAttempt, job: kotlinx.coroutines.Job, s: FakeSocket, handshake: () -> Boolean, writes: AtomicInteger): String? {
        if (!a.track(s)) return null
        val ok = try { handshake() } finally { a.untrack(s); s.close() }
        return if (ok) a.commit(job) { writes.incrementAndGet(); "paired" } else null
    }

    /** Blocking handshake in a real coroutine; [cancelIt] cancels it for real, then a valid reply is released. */
    @OptIn(kotlinx.coroutines.InternalCoroutinesApi::class)
    private fun cancelRun(timeout: Boolean) {
        val a = PairAttempt()
        val s = FakeSocket()
        val writes = AtomicInteger()
        val started = java.util.concurrent.CountDownLatch(1)
        val gate = java.util.concurrent.CountDownLatch(1)
        val result = java.util.concurrent.atomic.AtomicReference<String?>("unset")
        val finished = java.util.concurrent.CountDownLatch(1)
        runBlocking {
            val pairing = launch(kotlinx.coroutines.Dispatchers.IO) {
                val owner = kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]!!
                val h1 = owner.invokeOnCompletion(onCancelling = true) { a.cancel() }
                try {
                    if (timeout) {
                        kotlinx.coroutines.withTimeoutOrNull(150) {
                            val sj = kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]!!
                            sj.invokeOnCompletion(onCancelling = true) { a.cancel() }
                            result.set(fakeTryPair(a, sj, s, { started.countDown(); gate.await(); true }, writes))
                        }
                    } else {
                        result.set(fakeTryPair(a, owner, s, { started.countDown(); gate.await(); true }, writes))
                    }
                } finally { h1.dispose(); finished.countDown() }
            }
            started.await()
            if (timeout) {
                // Wait for the timeout to close the socket, with no help from us.
                val end = System.currentTimeMillis() + 5_000
                while (!s.closed.get() && System.currentTimeMillis() < end) Thread.sleep(10)
            } else {
                pairing.cancel()
            }
            assertTrue(s.closed.get())
            gate.countDown() // the blocked handshake returns a valid reply after the cancel
            finished.await(5, java.util.concurrent.TimeUnit.SECONDS)
        }
        assertNull(result.get())
        assertEquals(0, writes.get())
        assertTrue(s.closed.get())
    }

    @Test fun cancellingThePairingJobNeverSaves() = cancelRun(timeout = false)

    @Test fun timeoutNeverSaves() = cancelRun(timeout = true)

    @Test fun socketOpenedAfterCancelIsClosed() {
        val a = PairAttempt()
        a.cancel()
        val s = FakeSocket()
        assertFalse(a.track(s))
        assertTrue(s.closed.get())
    }

    @Test fun liveAttemptSavesOnce() {
        val a = PairAttempt()
        val s = FakeSocket()
        val writes = AtomicInteger()
        assertEquals("paired", fakeTryPair(a, kotlinx.coroutines.Job(), s, { true }, writes))
        assertEquals(1, writes.get())
        assertTrue(s.closed.get())
    }
}
