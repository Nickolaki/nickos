package com.nbaltas.nickos.master

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
