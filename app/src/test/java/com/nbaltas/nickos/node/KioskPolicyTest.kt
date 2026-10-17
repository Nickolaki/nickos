package com.nbaltas.nickos.node

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KioskPolicyTest {
    private fun st(owner: Boolean = false, home: Boolean = false, overlay: Boolean = false, usage: Boolean = false, admin: Boolean = false) =
        KioskState(owner, home, overlay, usage, admin)

    @Test fun `parent screens expire and only initial setup may stay without a PIN`() {
        for (existing in listOf(false, true)) {
            assertFalse(KioskPolicy.parentScreenMayStay(false, existing, false))
            assertTrue(KioskPolicy.parentScreenMayStay(false, existing, true))
            assertTrue(KioskPolicy.parentScreenMayStay(true, existing, true))
            assertEquals(!existing, KioskPolicy.parentScreenMayStay(true, existing, false))
        }
    }

    @Test fun `device owner is the full lock whatever else is true`() {
        assertEquals(KioskStatus.Owner, KioskPolicy.status(st(owner = true)))
        assertFalse(KioskPolicy.setupNeeded(st(owner = true)))
    }

    @Test fun `home role alone is ready`() {
        assertEquals(KioskStatus.Ready, KioskPolicy.status(st(home = true)))
        assertFalse(KioskPolicy.setupNeeded(st(home = true)))
    }

    @Test fun `no home role lists every undone step in order`() {
        assertEquals(
            listOf(KioskStep.OVERLAY, KioskStep.USAGE),
            KioskPolicy.missing(st(home = true, admin = true)),
        )
        assertEquals(
            KioskStatus.Incomplete(listOf(KioskStep.HOME, KioskStep.OVERLAY, KioskStep.USAGE, KioskStep.ADMIN)),
            KioskPolicy.status(st()),
        )
        assertTrue(KioskPolicy.setupNeeded(st()))
    }

    @Test fun `system dialogs and NickOS are never bounced`() {
        for (p in listOf("com.nbaltas.nickos", "android", "com.android.systemui", "com.google.android.permissioncontroller", "com.google.android.packageinstaller")) {
            assertTrue(p, KioskPolicy.isSystem(p, "com.nbaltas.nickos"))
        }
        assertFalse(KioskPolicy.isSystem("com.some.game", "com.nbaltas.nickos"))
    }

    @Test fun `bounce only a stray app in kid mode on a non owner tablet`() {
        val allowed = listOf("org.jellyfin")
        fun r(owner: Boolean = false, parent: Boolean = false, homeVis: Boolean = false, fg: String? = "com.some.game") =
            KioskPolicy.returnHome(owner, parent, homeVis, fg, allowed, "com.nbaltas.nickos")
        assertTrue(r())
        assertFalse(r(owner = true))
        assertFalse(r(parent = true))
        assertFalse(r(homeVis = true))
        assertFalse(r(fg = null))
        assertFalse(r(fg = "org.jellyfin"))
        assertFalse(r(fg = "com.android.systemui"))
    }

    @Test fun `reset hands the home role back only when not owner and home`() {
        assertTrue(KioskPolicy.afterReset(deviceOwner = false, home = true))
        assertFalse(KioskPolicy.afterReset(deviceOwner = true, home = true))
        assertFalse(KioskPolicy.afterReset(deviceOwner = false, home = false))
    }
}
