package com.nbaltas.nickos.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupWaitTest {
    private val a = "ann"
    private val b = "ben"
    private val c = "cat"

    private fun playing(vararg ids: String): GroupWait = GroupWait(true).also { w ->
        w.start(ids.toSet())
        ids.forEach { w.report(it, SyncState.PLAYING, 0) }
    }

    @Test
    fun `a tablet buffering for more than 3 s pauses everyone once`() {
        val w = playing(a, b)
        assertTrue(w.report(b, SyncState.BUFFERING, 1000).isEmpty())
        assertTrue(w.tick(4000).isEmpty()) // exactly 3 s is not more than 3 s
        assertEquals(listOf<WaitAct>(WaitAct.Pause(setOf(b))), w.tick(4001))
        assertEquals(WaitMode.AUTO_WAIT, w.mode)
        assertEquals(setOf(b), w.waitingFor())
        assertTrue(w.tick(5000).isEmpty()) // no second pause
    }

    @Test
    fun `a short stall of 3 s or less never pauses the group`() {
        val w = playing(a, b)
        w.report(b, SyncState.BUFFERING, 1000)
        assertTrue(w.tick(3500).isEmpty())
        assertTrue(w.report(b, SyncState.PLAYING, 3900).isEmpty())
        assertTrue(w.tick(9000).isEmpty())
        assertEquals(WaitMode.PLAYING, w.mode)
    }

    @Test
    fun `everyone ready resumes the group`() {
        val w = playing(a, b)
        w.report(b, SyncState.BUFFERING, 0)
        w.tick(3001)
        assertEquals(listOf<WaitAct>(WaitAct.Resume), w.report(b, SyncState.READY, 5000))
        assertEquals(WaitMode.PLAYING, w.mode)
        assertTrue(w.waitingFor().isEmpty())
    }

    @Test
    fun `a tablet that paused with a full buffer counts as ready`() {
        val w = playing(a, b)
        w.report(b, SyncState.BUFFERING, 0)
        w.tick(3001)
        w.report(a, SyncState.PAUSED, 3100)
        assertEquals(listOf<WaitAct>(WaitAct.Resume), w.report(b, SyncState.PAUSED, 6000))
    }

    @Test
    fun `waits for every tablet that is still not ready`() {
        val w = playing(a, b, c)
        w.report(b, SyncState.BUFFERING, 0)
        w.tick(3001)
        w.report(c, SyncState.BUFFERING, 3500)
        assertEquals(setOf(b, c), w.waitingFor())
        assertTrue(w.report(b, SyncState.PAUSED, 4000).isEmpty())
        assertEquals(setOf(c), w.waitingFor())
        assertEquals(listOf<WaitAct>(WaitAct.Resume), w.report(c, SyncState.PAUSED, 5000))
    }

    @Test
    fun `at most one automatic pause in 10 s`() {
        val w = playing(a, b)
        w.report(b, SyncState.BUFFERING, 0)
        assertEquals(listOf<WaitAct>(WaitAct.Pause(setOf(b))), w.tick(3001))
        w.report(b, SyncState.READY, 4000)
        w.report(b, SyncState.BUFFERING, 4500)
        assertTrue(w.tick(8000).isEmpty()) // 4.9 s after the last pause: held back
        assertEquals(WaitMode.PLAYING, w.mode)
        assertTrue(w.tick(13_000).isEmpty()) // 9.999 s after it
        assertEquals(listOf<WaitAct>(WaitAct.Pause(setOf(b))), w.tick(13_002))
        assertEquals(WaitMode.AUTO_WAIT, w.mode)
    }

    @Test
    fun `a pause by the parent cancels the wait and nothing resumes by itself`() {
        val w = playing(a, b)
        w.report(b, SyncState.BUFFERING, 0)
        w.tick(3001)
        w.manualPause()
        assertEquals(WaitMode.MANUAL_PAUSE, w.mode)
        assertTrue(w.report(b, SyncState.READY, 5000).isEmpty())
        assertTrue(w.report(a, SyncState.PAUSED, 5100).isEmpty())
        assertTrue(w.tick(60_000).isEmpty())
        assertEquals(WaitMode.MANUAL_PAUSE, w.mode)
        w.manualResume()
        assertEquals(WaitMode.PLAYING, w.mode)
    }

    @Test
    fun `a seek by the parent cancels the wait`() {
        val w = playing(a, b)
        w.report(b, SyncState.BUFFERING, 0)
        w.tick(3001)
        w.seek(playing = false)
        assertEquals(WaitMode.MANUAL_PAUSE, w.mode)
        assertTrue(w.report(b, SyncState.READY, 5000, w.gen).isEmpty())
        w.seek(playing = true)
        assertEquals(WaitMode.PLAYING, w.mode)
    }

    @Test
    fun `a stop ends it`() {
        val w = playing(a, b)
        w.stop()
        assertEquals(WaitMode.STOPPED, w.mode)
        assertTrue(w.report(b, SyncState.BUFFERING, 0).isEmpty())
        assertTrue(w.tick(100_000).isEmpty())
    }

    @Test
    fun `a failed tablet is left out and does not hold the group`() {
        val w = playing(a, b)
        w.report(b, SyncState.BUFFERING, 0)
        w.tick(3001)
        assertEquals(listOf<WaitAct>(WaitAct.Resume), w.report(b, SyncState.ERROR, 4000))
        // And a failed tablet never starts a wait.
        w.report(b, SyncState.BUFFERING, 20_000)
        assertTrue(w.tick(40_000).let { it.isEmpty() || it.none { x -> x is WaitAct.Pause } })
    }

    @Test
    fun `a tablet that lost its link is left out`() {
        val w = playing(a, b)
        w.report(b, SyncState.BUFFERING, 0)
        w.tick(3001)
        assertEquals(listOf<WaitAct>(WaitAct.Resume), w.offline(b, 4000))
        assertEquals(listOf<WaitAct>(), w.offline(b, 4100))
    }

    @Test
    fun `a tablet that is too slow is dropped after 30 s and the others go on`() {
        val w = playing(a, b)
        w.report(b, SyncState.BUFFERING, 0)
        assertEquals(listOf<WaitAct>(WaitAct.Pause(setOf(b))), w.tick(3001))
        assertTrue(w.tick(32_000).isEmpty())
        assertEquals(listOf(WaitAct.TooSlow(b), WaitAct.Resume), w.tick(33_001))
        assertEquals(WaitMode.PLAYING, w.mode)
    }

    @Test
    fun `a too slow tablet is counted again once it is ready`() {
        val w = playing(a, b)
        w.report(b, SyncState.BUFFERING, 0)
        w.tick(3001); w.tick(33_001)
        w.report(b, SyncState.PLAYING, 40_000)
        w.report(b, SyncState.BUFFERING, 50_000)
        assertEquals(listOf<WaitAct>(WaitAct.Pause(setOf(b))), w.tick(53_001))
    }

    @Test
    fun `a stale report from before a seek is ignored`() {
        val w = playing(a, b)
        val old = w.gen
        w.seek(playing = true)
        w.report(b, SyncState.BUFFERING, 0, old)
        assertTrue(w.tick(10_000).isEmpty())
        assertEquals(WaitMode.PLAYING, w.mode)
    }

    @Test
    fun `a report from a tablet that is not in the session is ignored`() {
        val w = playing(a, b)
        assertTrue(w.report(c, SyncState.BUFFERING, 0).isEmpty())
        assertTrue(w.tick(10_000).isEmpty())
    }

    @Test
    fun `a file on the tablets is never held by the wait`() {
        val w = GroupWait(false)
        w.start(setOf(a, b))
        assertTrue(w.report(b, SyncState.BUFFERING, 0).isEmpty())
        assertTrue(w.tick(100_000).isEmpty())
        assertEquals(WaitMode.PLAYING, w.mode)
        assertTrue(w.waitingFor().isEmpty())
    }

    @Test
    fun `the words`() {
        assertEquals("Waiting for Leo", TogetherText.waitingFor(listOf("Leo")))
        assertEquals("Waiting for Leo and Mia", TogetherText.waitingFor(listOf("Leo", "Mia")))
        assertEquals("Leo is too slow, carrying on without them", TogetherText.tooSlow("Leo"))
    }
}
