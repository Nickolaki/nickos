package com.nbaltas.nickos.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncPlanTest {

    private fun session(playing: Boolean, anchorPos: Long = 0, anchorAt: Long = 0, durMs: Long = 100_000) =
        SyncSession(fid = "abc", title = "Star Harbour", durMs = durMs, nodes = setOf("n1"), anchorPos = anchorPos, anchorAt = anchorAt, playing = playing)

    @Test
    fun `expectedPos advances with time while playing and clamps to duration`() {
        val s = session(playing = true, anchorPos = 1000, anchorAt = 5000, durMs = 10_000)
        assertEquals(1000L, s.expectedPos(5000))
        assertEquals(3000L, s.expectedPos(7000))
        assertEquals(10_000L, s.expectedPos(50_000)) // clamped to dur
    }

    @Test
    fun `expectedPos returns anchorPos while paused regardless of time`() {
        val s = session(playing = false, anchorPos = 4000, anchorAt = 5000)
        assertEquals(4000L, s.expectedPos(5000))
        assertEquals(4000L, s.expectedPos(999_999))
    }

    @Test
    fun `planFor returns at in the node clock and pos for the lead time`() {
        val s = session(playing = true, anchorPos = 0, anchorAt = 10_000, durMs = 1_000_000)
        val masterNow = 10_000L
        val offset = 300L
        val lead = 1500L
        val cmd = SyncPlan.planFor(s, offset, masterNow, lead)
        assertEquals(masterNow + lead + offset, cmd.at)
        assertEquals(s.expectedPos(masterNow + lead), cmd.pos)
        assertEquals("abc", cmd.fid)
        assertEquals("Star Harbour", cmd.title)
    }

    @Test
    fun `two nodes with different offsets get different at and the same pos`() {
        val s = session(playing = true, anchorPos = 0, anchorAt = 10_000, durMs = 1_000_000)
        val now = 10_000L
        val plus = SyncPlan.planFor(s, nodeOffset = 5000, now = now)
        val minus = SyncPlan.planFor(s, nodeOffset = -3000, now = now)
        assertEquals(plus.pos, minus.pos)
        assertEquals(now + 1500 + 5000, plus.at)
        assertEquals(now + 1500 - 3000, minus.at)
    }

    @Test
    fun `re-anchor uses lead zero`() {
        val s = session(playing = true, anchorPos = 2000, anchorAt = 0, durMs = 1_000_000)
        val now = 60_000L
        val cmd = SyncPlan.planFor(s, nodeOffset = 0, now = now, leadMs = 0)
        assertEquals(now, cmd.at)
        assertEquals(s.expectedPos(now), cmd.pos)
    }

    @Test
    fun `a single-node session behaves identically to a multi-node one`() {
        val single = session(playing = true, anchorPos = 1000, anchorAt = 5000).copy(nodes = setOf("solo"))
        val group = session(playing = true, anchorPos = 1000, anchorAt = 5000).copy(nodes = setOf("a", "b", "c"))
        assertEquals(single.expectedPos(7000), group.expectedPos(7000))
        val cmdSingle = SyncPlan.planFor(single, 100, 5000)
        val cmdGroup = SyncPlan.planFor(group, 100, 5000)
        assertEquals(cmdSingle, cmdGroup)
    }
}
