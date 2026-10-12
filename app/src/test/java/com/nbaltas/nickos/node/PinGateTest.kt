package com.nbaltas.nickos.node

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PinGateTest {
    @Test fun `correct pin is ok and counts down wrong ones`() {
        val g = PinGate()
        assertEquals(PinResult.Wrong(4), g.tryPin("1111", "2468", 0))
        assertEquals(PinResult.Wrong(3), g.tryPin("1111", "2468", 1))
        assertEquals(PinResult.Ok, g.tryPin("2468", "2468", 2))
        assertEquals(PinResult.Wrong(4), g.tryPin("1111", "2468", 3))
    }

    @Test fun `fifth wrong entry locks for thirty seconds and ignores even the right pin`() {
        val g = PinGate()
        repeat(4) { g.tryPin("1111", "2468", 0) }
        assertEquals(PinResult.Locked(30_000), g.tryPin("1111", "2468", 0))
        assertEquals(30_000L, g.lockedUntil(10_000))
        assertEquals(PinResult.Locked(30_000), g.tryPin("2468", "2468", 10_000))
        assertNull(g.lockedUntil(30_000))
    }

    @Test fun `each wrong entry after a lock doubles it up to the cap`() {
        val g = PinGate()
        repeat(5) { g.tryPin("1111", "2468", 0) }
        assertEquals(PinResult.Locked(30_000 + 60_000), g.tryPin("1111", "2468", 30_000))
        assertEquals(PinResult.Locked(90_000 + 120_000), g.tryPin("1111", "2468", 90_000))
        var t = 210_000L
        var last = 0L
        repeat(4) { val r = g.tryPin("1111", "2468", t) as PinResult.Locked; last = r.untilMs - t; t = r.untilMs }
        assertEquals(300_000L, last)
    }

    @Test fun `a right pin after a lock resets everything`() {
        val g = PinGate()
        repeat(5) { g.tryPin("1111", "2468", 0) }
        assertEquals(PinResult.Ok, g.tryPin("2468", "2468", 30_000))
        assertEquals(PinResult.Wrong(4), g.tryPin("1111", "2468", 30_001))
    }

    @Test fun `weak pins`() {
        assertTrue(PinPolicy.isWeak("0000"))
        assertTrue(PinPolicy.isWeak("7777"))
        assertTrue(PinPolicy.isWeak("123"))
        assertFalse(PinPolicy.isWeak("1234"))
        assertFalse(PinPolicy.isWeak("2468"))
    }
}
