package com.nbaltas.nickos.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DriftPolicyTest {

    @Test
    fun `dead band returns None`() {
        val p = DriftPolicy()
        assertEquals(DriftAction.None, p.decide(driftMs = 10, playerState = PlayerState.READY, playing = true, now = 0, expected = 1000))
        assertEquals(DriftAction.None, p.decide(driftMs = -40, playerState = PlayerState.READY, playing = true, now = 0, expected = 1000))
    }

    @Test
    fun `nudges both directions`() {
        val ahead = DriftPolicy().decide(driftMs = 100, playerState = PlayerState.READY, playing = true, now = 0, expected = 1000)
        val behind = DriftPolicy().decide(driftMs = -100, playerState = PlayerState.READY, playing = true, now = 0, expected = 1000)
        assertEquals(DriftAction.Rate(0.96f), ahead)
        assertEquals(DriftAction.Rate(1.04f), behind)
    }

    @Test
    fun `hysteresis keeps nudging down to 15ms then returns to normal speed once`() {
        val p = DriftPolicy()
        assertEquals(DriftAction.Rate(0.96f), p.decide(60, PlayerState.READY, true, 0, 1000))
        assertEquals(DriftAction.Rate(0.96f), p.decide(30, PlayerState.READY, true, 1000, 1000)) // still nudging between 15 and 40
        assertEquals(DriftAction.Rate(1f), p.decide(10, PlayerState.READY, true, 2000, 1000)) // <=15, stop nudging
        assertEquals(DriftAction.None, p.decide(10, PlayerState.READY, true, 3000, 1000)) // back to dead band, not nudging
    }

    @Test
    fun `seek over 400ms uses initial lead of 200ms`() {
        val p = DriftPolicy()
        val action = p.decide(driftMs = 500, playerState = PlayerState.READY, playing = true, now = 0, expected = 10_000)
        assertEquals(DriftAction.Seek(10_200), action)
    }

    @Test
    fun `no second seek within 3 seconds`() {
        val p = DriftPolicy()
        p.decide(500, PlayerState.READY, true, now = 0, expected = 10_000)
        val second = p.decide(500, PlayerState.READY, true, now = 1000, expected = 10_000)
        assertEquals(DriftAction.None, second)
        val third = p.decide(500, PlayerState.READY, true, now = 3001, expected = 10_000)
        assertTrue(third is DriftAction.Seek)
    }

    @Test
    fun `seekLead adapts from the first measurement after a seek and clamps`() {
        val p = DriftPolicy()
        p.decide(500, PlayerState.READY, true, now = 0, expected = 10_000) // Seek issued, lead starts at 200
        // First measurement after the seek: still 300ms ahead -> seekLeadMs -= 300, clamped to 0
        p.decide(300, PlayerState.READY, true, now = 100, expected = 10_000)
        assertEquals(0L, p.seekLeadMs)
    }

    @Test
    fun `seekLead clamps at 1000 when behind after the seek`() {
        val p = DriftPolicy()
        p.decide(500, PlayerState.READY, true, now = 0, expected = 10_000) // lead starts 200
        p.decide(-5000, PlayerState.READY, true, now = 100, expected = 10_000) // seekLeadMs -= -5000 -> 5200 clamp 1000
        assertEquals(1000L, p.seekLeadMs)
    }

    @Test
    fun `None during buffering and Seek on the first READY after buffering`() {
        val p = DriftPolicy()
        assertEquals(DriftAction.None, p.decide(0, PlayerState.BUFFERING, true, now = 0, expected = 5000))
        val afterBuffer = p.decide(0, PlayerState.READY, true, now = 500, expected = 5000)
        assertEquals(DriftAction.Seek(5200), afterBuffer)
    }

    @Test
    fun `None when not playing`() {
        val p = DriftPolicy()
        assertEquals(DriftAction.None, p.decide(driftMs = 9999, playerState = PlayerState.READY, playing = false, now = 0, expected = 1000))
    }
}
