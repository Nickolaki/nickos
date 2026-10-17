package com.nbaltas.nickos.node

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KioskLockTest {
    private val flat = "com.nbaltas.nickos/com.nbaltas.nickos.node.RemoteInputService"

    @Test fun `parent mode expiry re-locks exactly once`() {
        val e = KioskLock.ParentExpiry()
        assertFalse(e.tick(false))
        assertFalse(e.tick(true))
        assertFalse(e.tick(true))
        assertTrue(e.tick(false))
        assertFalse(e.tick(false))
        assertFalse(e.tick(false))
    }

    @Test fun `boot launches the kid home on a kid tablet only`() {
        assertTrue(KioskLock.bootLaunch("android.intent.action.BOOT_COMPLETED", isNode = true, parentMode = false))
        assertTrue(KioskLock.bootLaunch("android.intent.action.MY_PACKAGE_REPLACED", isNode = true, parentMode = false))
        assertFalse(KioskLock.bootLaunch("android.intent.action.BOOT_COMPLETED", isNode = false, parentMode = false))
        assertFalse(KioskLock.bootLaunch("android.intent.action.BOOT_COMPLETED", isNode = true, parentMode = true))
        assertFalse(KioskLock.bootLaunch("android.intent.action.OTHER", isNode = true, parentMode = false))
    }

    @Test fun `accessibility list appends once and keeps the others`() {
        assertEquals(flat, KioskLock.mergeAccessibility(null, flat))
        assertEquals(flat, KioskLock.mergeAccessibility("", flat))
        assertEquals(flat, KioskLock.mergeAccessibility("  ", flat))
        assertEquals("a/B:$flat", KioskLock.mergeAccessibility("a/B", flat))
        assertEquals("a/B:c/D:$flat", KioskLock.mergeAccessibility("a/B:c/D", flat))
        assertEquals("a/B:$flat", KioskLock.mergeAccessibility("a/B:$flat", flat))
        assertEquals(flat, KioskLock.mergeAccessibility(flat, flat))
    }

    @Test fun `silent re-enable never happens if remote control was never on`() {
        assertFalse(KioskLock.mayReenable(everConnected = false, running = false))
        assertFalse(KioskLock.mayReenable(everConnected = false, running = true))
        assertFalse(KioskLock.mayReenable(everConnected = true, running = true))
        assertTrue(KioskLock.mayReenable(everConnected = true, running = false))
    }

    @Test fun `settings and systemui are bounced in kid mode and left alone in parent mode`() {
        assertEquals(KioskLock.Bounce.HOME, KioskLock.bounce("com.android.settings", false, 34))
        assertEquals(KioskLock.Bounce.HOME, KioskLock.bounce("com.android.settings", false, 26))
        assertEquals(KioskLock.Bounce.COLLAPSE, KioskLock.bounce("com.android.systemui", false, 31))
        assertEquals(KioskLock.Bounce.NONE, KioskLock.bounce("com.android.systemui", false, 30))
        assertEquals(KioskLock.Bounce.NONE, KioskLock.bounce("org.jellyfin.mobile", false, 34))
        assertEquals(KioskLock.Bounce.NONE, KioskLock.bounce(null, false, 34))
        assertEquals(KioskLock.Bounce.NONE, KioskLock.bounce("com.android.settings", true, 34))
        assertEquals(KioskLock.Bounce.NONE, KioskLock.bounce("com.android.systemui", true, 34))
    }
}
