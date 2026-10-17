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
        assertEquals(settings, u.fold(42_000, true, listOf(res(settings)), strict = true))
    }

    @Test fun `no usage access is unknown and forgets the cache`() {
        val u = UsageForeground()
        u.from(10_000)
        u.fold(10_000, true, listOf(res(jelly)), strict = true)
        assertNull(u.fold(11_000, false, emptyList(), strict = true))
        u.from(12_000)
        assertNull(u.fold(12_000, true, emptyList(), strict = true))
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
