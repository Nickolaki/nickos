package com.nbaltas.nickos.link

import org.junit.Assert.*
import org.junit.Test

class CommandDeliveryTest {
    @Test fun `closing a stalled link reports its queued and in flight commands once`() {
        var failures = 0
        val d = CommandDelivery { failures++ }
        val flight = d.enqueue()!!
        val queued = d.enqueue()!!
        assertTrue(d.maySend(flight))
        d.close()
        assertEquals(1, failures)
        assertFalse(d.maySend(queued))
        assertNull(d.enqueue())
        d.complete(flight, false)
        d.close()
        assertEquals(1, failures)
    }

    @Test fun `successful writes do not report failure and failed writes do`() {
        var failures = 0
        val d = CommandDelivery { failures++ }
        d.complete(d.enqueue()!!, true)
        d.complete(d.enqueue()!!, false)
        assertEquals(1, failures)
        d.close()
        assertEquals(1, failures)
    }

    @Test fun `a replacement connection is independent and never replays old commands`() {
        var failures = 0
        val old = CommandDelivery { failures++ }
        val next = CommandDelivery { failures++ }
        val ticket = old.enqueue()!!
        old.close()
        assertFalse(old.maySend(ticket))
        val fresh = next.enqueue()!!
        assertTrue(next.maySend(fresh))
        next.complete(fresh, true)
        next.close()
        assertEquals(1, failures)
    }
}
