package com.nbaltas.nickos.node

import com.nbaltas.nickos.node.UsageForeground.Ev
import com.nbaltas.nickos.node.UsageForeground.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsageForegroundTest {
    private val jelly = "org.jellyfin.mobile"
    private val settings = "com.android.settings"
    private fun res(p: String) = Ev(Kind.RESUMED, p)
    private fun pau(p: String) = Ev(Kind.PAUSED, p)

    @Test fun `resumed and still current is that package`() {
        val u = UsageForeground()
        u.from(10_000)
        assertEquals(jelly, u.fold(10_000, true, listOf(res(jelly)), strict = true))
    }

    @Test fun `resumed then paused with no resume is unknown when strict`() {
        val u = UsageForeground()
        u.from(10_000)
        assertNull(u.fold(10_000, true, listOf(res(jelly), pau(jelly)), strict = true))
        u.from(11_000)
        assertNull(u.fold(11_000, true, emptyList(), strict = true))
        u.from(12_000)
        assertEquals(jelly, u.fold(12_000, true, listOf(res(jelly)), strict = true))
    }

    @Test fun `the normal lookup keeps the cached package after a pause`() {
        val u = UsageForeground()
        u.from(10_000)
        assertEquals(jelly, u.fold(10_000, true, listOf(res(jelly), pau(jelly)), strict = false))
    }

    @Test fun `a clock rollback drops the cached allowed package and is unknown`() {
        val u = UsageForeground()
        u.from(100_000)
        assertEquals(jelly, u.fold(100_000, true, listOf(res(jelly)), strict = true))
        val from = u.from(40_000)
        assertEquals(40_000 - 6 * 60 * 60 * 1000L, from)
        assertNull(u.fold(40_000, true, emptyList(), strict = true))
        // The next read is incremental again, but the dropped cache does not come back.
        u.from(41_000)
        assertNull(u.fold(41_000, true, emptyList(), strict = true))
        u.from(42_000)
        assertNull(u.fold(42_000, true, listOf(res(settings)), strict = true))
    }

    @Test fun `no usage access is unknown and forgets the cache`() {
        val u = UsageForeground()
        u.from(10_000)
        u.fold(10_000, true, listOf(res(jelly)), strict = true)
        assertNull(u.fold(11_000, false, emptyList(), strict = true))
        u.from(12_000)
        assertNull(u.fold(12_000, true, emptyList(), strict = true))
    }

    private fun rolledBack(): UsageForeground {
        val u = UsageForeground()
        u.from(100_000)
        u.fold(100_000, true, listOf(res(jelly)), strict = true)
        u.from(40_000)
        assertNull(u.fold(40_000, true, listOf(res(jelly)), strict = true))
        return u
    }

    @Test fun `historical recovery events do not become trusted on a later empty query`() {
        val u = rolledBack()
        u.from(43_000)
        assertNull(u.fold(43_000, true, emptyList(), strict = true))
    }

    @Test fun `a status read after the rollback does not launder the historical cache`() {
        val u = rolledBack()
        u.from(43_000)
        assertEquals(jelly, u.fold(43_000, true, emptyList(), strict = false))
        u.from(44_000)
        assertNull(u.fold(44_000, true, emptyList(), strict = true))
    }

    @Test fun `events after a clock change never make strict trusted`() {
        val u = rolledBack()
        u.from(43_000)
        assertNull(u.fold(43_000, true, emptyList(), strict = true))
        u.from(44_000)
        assertNull(u.fold(44_000, true, listOf(res(settings)), strict = true))
        u.from(45_000)
        assertNull(u.fold(45_000, true, listOf(res(jelly)), strict = true))
        assertEquals(jelly, u.fold(46_000, true, emptyList(), strict = false))
    }

    @Test fun `a rollback seen by a non strict call makes strict null`() {
        val u = UsageForeground()
        u.from(100_000)
        u.fold(100_000, true, listOf(res(jelly)), strict = false)
        u.from(40_000)
        assertEquals(null, u.fold(40_000, true, emptyList(), strict = false))
        u.from(41_000)
        assertNull(u.fold(41_000, true, listOf(res(jelly)), strict = true))
    }

    @Test fun `after clearUntrusted an allowed foreground is trusted again`() {
        val u = rolledBack()
        u.from(43_000)
        assertNull(u.fold(43_000, true, listOf(res(settings)), strict = true))
        u.clearUntrusted()
        u.from(44_000)
        assertEquals(settings, u.fold(44_000, true, emptyList(), strict = true))
        u.from(45_000)
        assertEquals(jelly, u.fold(45_000, true, listOf(res(jelly)), strict = true))
    }

    @Test fun `without a discontinuity strict follows the events`() {
        val u = UsageForeground()
        u.from(10_000)
        assertEquals(jelly, u.fold(10_000, true, listOf(res(jelly)), strict = true))
        u.from(11_000)
        assertEquals(settings, u.fold(11_000, true, listOf(pau(jelly), res(settings)), strict = true))
    }

    @Test fun `relock goes home when the foreground is unknown`() {
        var homes = 0
        val u = UsageForeground()
        u.from(100_000)
        u.fold(100_000, true, listOf(res(jelly)), strict = true)
        u.from(40_000)
        val fg = u.fold(40_000, true, emptyList(), strict = true)
        val relock = KioskRelock(
            elapsed = { 5_000 }, wall = { 1_700_000_000_000L }, markerOpen = { false }, setMarker = {},
            parentUntil = { 0L }, clearParent = {}, homeVisible = { false },
            foreground = { fg }, allowed = { listOf(jelly) }, needsFront = { true },
            applyLockdown = {}, goHome = { homes++ },
        )
        relock.begin()
        relock.end()
        assertEquals(1, homes)
    }
}
