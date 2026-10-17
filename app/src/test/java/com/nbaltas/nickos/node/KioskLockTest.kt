package com.nbaltas.nickos.node

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KioskLockTest {
    private val flat = "com.nbaltas.nickos/com.nbaltas.nickos.node.RemoteInputService"

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

    @Test fun `consent is set when remote control connects in a parent or settings window only`() {
        assertTrue(KioskLock.consentOnConnect(parentMode = true, maintenance = false))
        assertTrue(KioskLock.consentOnConnect(parentMode = false, maintenance = true))
        assertFalse(KioskLock.consentOnConnect(parentMode = false, maintenance = false))
    }

    @Test fun `consent is cleared when switched off with the app alive, not during an update`() {
        assertTrue(KioskLock.consentClearedOnUnbind(runningVersion = 11, installedVersion = 11))
        assertFalse(KioskLock.consentClearedOnUnbind(runningVersion = 11, installedVersion = 12))
    }

    @Test fun `silent re-enable needs consent, is off now, and happens once per update`() {
        assertFalse(KioskLock.mayReenable(consent = false, running = false, unsupported = false, lastTriedVersion = 11, version = 12))
        assertFalse(KioskLock.mayReenable(consent = true, running = true, unsupported = false, lastTriedVersion = 11, version = 12))
        assertTrue(KioskLock.mayReenable(consent = true, running = false, unsupported = false, lastTriedVersion = 11, version = 12))
        // already tried for this version: not again
        assertFalse(KioskLock.mayReenable(consent = true, running = false, unsupported = false, lastTriedVersion = 12, version = 12))
        // the next update gets its own try
        assertTrue(KioskLock.mayReenable(consent = true, running = false, unsupported = false, lastTriedVersion = 12, version = 13))
    }

    @Test fun `once Android refused the re-enable it is never tried again`() {
        assertFalse(KioskLock.mayReenable(consent = true, running = false, unsupported = true, lastTriedVersion = 11, version = 12))
        assertFalse(KioskLock.mayReenable(consent = true, running = false, unsupported = true, lastTriedVersion = 0, version = 99))
    }

    @Test fun `NickOS is brought forward except over an allowed kid app`() {
        val allowed = listOf("org.jellyfin.mobile", "com.kid.game")
        assertFalse(KioskLock.relockGoesHome("org.jellyfin.mobile", allowed))
        assertFalse(KioskLock.relockGoesHome("com.kid.game", allowed))
        assertTrue(KioskLock.relockGoesHome(KioskLock.SETTINGS_PKG, allowed))
        assertTrue(KioskLock.relockGoesHome(KioskLock.SETTINGS_PKG, allowed + KioskLock.SETTINGS_PKG))
        assertTrue(KioskLock.relockGoesHome("com.other.app", allowed))
        assertTrue(KioskLock.relockGoesHome(null, allowed))
    }

    @Test fun `MainActivity comes to the front only for a Device Owner tablet that is still outside lock task`() {
        assertTrue(KioskLock.needsFront(deviceOwner = true, lockTaskNone = true))
        assertFalse(KioskLock.needsFront(deviceOwner = true, lockTaskNone = false))
        assertFalse(KioskLock.needsFront(deviceOwner = false, lockTaskNone = true))
    }

    private fun bounce(pkg: String?, isNode: Boolean = true, parent: Boolean = false, maint: Boolean = false, sdk: Int = 34, now: Long = 10_000, last: Long = 0) =
        KioskLock.bounce(pkg, isNode, parent, maint, sdk, now, last)

    @Test fun `settings and systemui are bounced in kid mode only`() {
        assertEquals(KioskLock.Bounce.HOME, bounce("com.android.settings"))
        assertEquals(KioskLock.Bounce.HOME, bounce("com.android.settings", sdk = 26))
        assertEquals(KioskLock.Bounce.COLLAPSE, bounce("com.android.systemui", sdk = 31))
        assertEquals(KioskLock.Bounce.NONE, bounce("com.android.systemui", sdk = 30))
        assertEquals(KioskLock.Bounce.NONE, bounce("org.jellyfin.mobile"))
        assertEquals(KioskLock.Bounce.NONE, bounce(null))
    }

    @Test fun `the backstop stays out of parent mode, the settings window and non kid tablets`() {
        assertEquals(KioskLock.Bounce.NONE, bounce("com.android.settings", parent = true))
        assertEquals(KioskLock.Bounce.NONE, bounce("com.android.systemui", parent = true))
        assertEquals(KioskLock.Bounce.NONE, bounce("com.android.settings", maint = true))
        assertEquals(KioskLock.Bounce.NONE, bounce("com.android.systemui", maint = true))
        assertEquals(KioskLock.Bounce.NONE, bounce("com.android.settings", isNode = false))
    }

    @Test fun `the shade is pushed back at most once a second`() {
        assertEquals(KioskLock.Bounce.NONE, bounce("com.android.systemui", now = 10_500, last = 10_000))
        assertEquals(KioskLock.Bounce.NONE, bounce("com.android.systemui", now = 10_999, last = 10_000))
        assertEquals(KioskLock.Bounce.COLLAPSE, bounce("com.android.systemui", now = 11_000, last = 10_000))
    }
}
