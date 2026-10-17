package com.nbaltas.nickos.node

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KioskRelockTest {
    private val jelly = "org.jellyfin.mobile"

    private class Rig {
        var elapsed = 5_000L
        var wall = 1_700_000_000_000L
        var marker = false
        var parentUntil = 0L
        var home = false
        var fg: String? = null
        var allowed = listOf("org.jellyfin.mobile", "com.kid.game")
        var needsFront = true
        var lockdowns = 0
        var homes = 0
        val relock = KioskRelock(
            elapsed = { elapsed }, wall = { wall }, markerOpen = { marker }, setMarker = { marker = it },
            parentUntil = { parentUntil }, clearParent = { parentUntil = 0 }, homeVisible = { home },
            foreground = { fg }, allowed = { allowed }, needsFront = { needsFront },
            applyLockdown = { lockdowns++ }, goHome = { homes++ },
        )
        fun advance(ms: Long) { elapsed += ms; wall += ms }
    }

    @Test fun `moving the wall clock back a day does not stretch the 90 second window`() {
        val r = Rig().apply { fg = "com.android.settings" }
        r.relock.begin()
        assertTrue(r.relock.active)
        r.elapsed += KioskLock.MAINTENANCE_MS
        r.wall -= 24 * 3_600_000L
        r.relock.relockIfExpired()
        assertFalse(r.relock.active)
        assertFalse(r.marker)
        assertEquals(1, r.lockdowns)
        assertEquals(1, r.homes)
    }

    @Test fun `going home after the grace period closes the window despite a backwards wall clock`() {
        val r = Rig()
        r.relock.begin()
        r.advance(KioskLock.MAINTENANCE_GRACE_MS + 1_000)
        r.wall -= 24 * 3_600_000L
        r.home = true
        r.relock.onForeground()
        assertFalse(r.relock.active)
        assertEquals(1, r.lockdowns)
    }

    @Test fun `a marker left from before a restart closes the window at once`() {
        val r = Rig().apply { marker = true }
        r.relock.onStart()
        assertFalse(r.marker)
        assertFalse(r.relock.active)
        assertEquals(1, r.lockdowns)
    }

    @Test fun `an allowed app in front at expiry is locked down but never pulled home`() {
        for (pkg in listOf(jelly, "com.kid.game")) {
            val r = Rig().apply { parentUntil = wall + 1_000; fg = pkg }
            r.advance(2_000)
            repeat(10) { r.relock.relockIfExpired(); r.advance(1_000) }
            assertEquals(1, r.lockdowns)
            assertEquals(0, r.homes)
        }
    }

    @Test fun `settings, a disallowed app or an unknown foreground is brought home at expiry`() {
        for (pkg in listOf("com.android.settings", "com.other.app", null)) {
            val r = Rig().apply { parentUntil = wall + 1_000; fg = pkg }
            r.advance(2_000)
            r.relock.relockIfExpired()
            assertEquals(1, r.lockdowns)
            assertEquals(1, r.homes)
        }
    }

    @Test fun `a valid parent window causes neither lockdown nor home`() {
        val r = Rig().apply { parentUntil = wall + 60_000; fg = "com.other.app" }
        r.relock.begin()
        r.elapsed += KioskLock.MAINTENANCE_MS + 1
        repeat(3) { r.relock.relockIfExpired() }
        assertEquals(0, r.lockdowns)
        assertEquals(0, r.homes)
    }

    @Test fun `home in front through the grace period ends the window on a later tick`() {
        val r = Rig()
        r.relock.begin()
        r.advance(500)
        r.home = true
        r.relock.onForeground()
        assertTrue(r.relock.active) // the screen waking, still inside the grace period
        r.advance(KioskLock.MAINTENANCE_GRACE_MS)
        r.relock.relockIfExpired()
        assertFalse(r.relock.active)
        assertEquals(1, r.lockdowns)
    }

    @Test fun `a wake resume followed by Settings keeps the window open`() {
        val r = Rig()
        r.relock.begin()
        r.advance(500)
        r.home = true
        r.relock.onForeground()
        r.advance(300)
        r.home = false // Settings came up
        r.advance(KioskLock.MAINTENANCE_GRACE_MS * 2)
        r.relock.relockIfExpired()
        assertTrue(r.relock.active)
        assertEquals(0, r.lockdowns)
    }
}
