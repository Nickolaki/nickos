package com.nbaltas.nickos.master

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconnectTest {
    @Test
    fun `a link that dropped is redialed at once then backs off 500 1000 2000 3000`() {
        val r = RetryDelay()
        assertEquals(0L, r.dropped())
        assertEquals(listOf(500L, 1_000L, 2_000L, 3_000L, 3_000L), List(5) { r.failed() })
    }

    @Test
    fun `a node never reached keeps the plain 3 s`() {
        val r = RetryDelay()
        assertEquals(listOf(3_000L, 3_000L), List(2) { r.failed() })
    }

    @Test
    fun `a new drop starts the fast schedule again`() {
        val r = RetryDelay()
        r.dropped(); r.failed(); r.failed()
        assertEquals(0L, r.dropped())
        assertEquals(500L, r.failed())
    }

    @Test
    fun `online is kept for 10 s after the drop and then ends`() {
        val g = OnlineGrace()
        g.dropped("a", 1_000)
        assertTrue(g.holding("a", 1_000))
        assertTrue(g.holding("a", 10_999))
        assertEquals(emptySet<String>(), g.expire(10_999))
        assertFalse(g.holding("a", 11_000))
        assertEquals(setOf("a"), g.expire(11_000))
        assertEquals(emptySet<String>(), g.expire(12_000)) // only reported once
    }

    @Test
    fun `reconnecting inside the grace keeps it online`() {
        val g = OnlineGrace()
        g.dropped("a", 0)
        g.reconnected("a")
        assertEquals(emptySet<String>(), g.expire(20_000))
    }

    @Test
    fun `a second drop gets its own 10 s`() {
        val g = OnlineGrace()
        g.dropped("a", 0); g.reconnected("a"); g.dropped("a", 8_000)
        assertEquals(emptySet<String>(), g.expire(10_000)) // the first drop's timer wakes: not yet
        assertEquals(setOf("a"), g.expire(18_000))
    }

    @Test
    fun `an address arriving while the link is down or sleeping is dialed now`() {
        val a = "192.168.1.9" to 47470
        val b = "192.168.1.20" to 47470
        assertTrue(AdoptPolicy.redialNow(linked = false, dialing = null, addr = a))   // looping, in its delay
        assertTrue(AdoptPolicy.redialNow(linked = false, dialing = a, addr = b))      // dialing a stale address
        assertFalse(AdoptPolicy.redialNow(linked = false, dialing = a, addr = a))     // already dialing exactly that
        assertFalse(AdoptPolicy.redialNow(linked = true, dialing = null, addr = a))   // a live link is left alone
    }
}
