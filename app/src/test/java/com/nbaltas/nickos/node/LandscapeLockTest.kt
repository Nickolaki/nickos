package com.nbaltas.nickos.node

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** android.util.Log inside LandscapeLock needs Robolectric; nothing else here touches Android. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LandscapeLockTest {
    private class Fake(var overlayOk: Boolean, var writeOk: Boolean, var current: Rotation = Rotation(1, 0)) : RotationPort {
        var overlayShown = false
        var writes = mutableListOf<Rotation>()
        var saveCount = 0
        private var keep: Rotation? = null
        override fun canOverlay() = overlayOk
        override fun canWrite() = writeOk
        override fun read() = current
        override fun write(r: Rotation) { writes += r; current = r }
        override fun overlay(on: Boolean) { overlayShown = on }
        override var saved: Rotation?
            get() = keep
            set(v) { if (v != null) saveCount++; keep = v }
    }

    private val land = LandscapePolicy.LANDSCAPE

    // ---- the pure decision, every permission combination ----
    @Test fun `unlocked is off whatever is granted`() {
        for (o in listOf(true, false)) for (w in listOf(true, false)) {
            val p = LandscapePolicy.plan(false, o, w, null)
            assertEquals("off", p.state)
            assertFalse(p.overlay); assertFalse(p.fixSettings); assertFalse(p.saveFirst); assertFalse(p.restore)
        }
    }

    @Test fun `overlay alone is on and leaves the settings alone`() {
        val p = LandscapePolicy.plan(true, canOverlay = true, canWrite = false, saved = null)
        assertEquals("on", p.state); assertTrue(p.overlay); assertFalse(p.fixSettings); assertFalse(p.saveFirst)
    }

    @Test fun `write settings alone is the fallback and saves first`() {
        val p = LandscapePolicy.plan(true, canOverlay = false, canWrite = true, saved = null)
        assertEquals("on", p.state); assertFalse(p.overlay); assertTrue(p.fixSettings); assertTrue(p.saveFirst)
    }

    @Test fun `both granted prefers the overlay so both landscape directions work`() {
        val p = LandscapePolicy.plan(true, canOverlay = true, canWrite = true, saved = null)
        assertTrue(p.overlay); assertFalse(p.fixSettings); assertFalse(p.saveFirst)
    }

    @Test fun `neither granted reports no permission`() {
        val p = LandscapePolicy.plan(true, canOverlay = false, canWrite = false, saved = null)
        assertEquals("no_permission", p.state); assertFalse(p.overlay); assertFalse(p.fixSettings)
    }

    @Test fun `a saved rotation is restored once the fallback is not needed and settings can be written`() {
        val saved = Rotation(1, 0)
        assertTrue(LandscapePolicy.plan(false, false, true, saved).restore)
        assertTrue(LandscapePolicy.plan(true, true, true, saved).restore)
        assertFalse("still using the fallback", LandscapePolicy.plan(true, false, true, saved).restore)
        assertFalse("cannot write yet, keep the copy", LandscapePolicy.plan(false, false, false, saved).restore)
        assertFalse("nothing saved", LandscapePolicy.plan(false, false, true, null).restore)
    }

    // ---- the apply, through a fake tablet ----
    @Test fun `overlay path shows the window and writes nothing`() {
        val f = Fake(overlayOk = true, writeOk = false)
        val lock = LandscapeLock(f)
        assertEquals("on", lock.apply(true))
        assertTrue(f.overlayShown); assertTrue(f.writes.isEmpty())
        assertEquals("off", lock.apply(false))
        assertFalse(f.overlayShown)
    }

    @Test fun `fallback saves the old rotation before the first change`() {
        val f = Fake(overlayOk = false, writeOk = true, current = Rotation(1, 3))
        val lock = LandscapeLock(f)
        assertEquals("on", lock.apply(true))
        assertEquals(Rotation(1, 3), f.saved)
        assertEquals(listOf(land), f.writes)
    }

    @Test fun `turning it off puts the saved rotation back once`() {
        val f = Fake(overlayOk = false, writeOk = true, current = Rotation(1, 3))
        val lock = LandscapeLock(f)
        lock.apply(true)
        assertEquals("off", lock.apply(false))
        assertEquals(Rotation(1, 3), f.current)
        assertNull(f.saved)
        val writesAfter = f.writes.size
        lock.apply(false); lock.apply(false)
        assertEquals("restore happens once", writesAfter, f.writes.size)
    }

    @Test fun `applying again changes nothing and never saves over the first copy`() {
        val f = Fake(overlayOk = false, writeOk = true, current = Rotation(1, 3))
        val lock = LandscapeLock(f)
        repeat(5) { lock.apply(true) }
        assertEquals(1, f.saveCount)
        assertEquals(Rotation(1, 3), f.saved)
        assertEquals("one write for five applies", 1, f.writes.size)
    }

    @Test fun `a restart in the middle keeps the first copy`() {
        val f = Fake(overlayOk = false, writeOk = true, current = Rotation(1, 3))
        LandscapeLock(f).apply(true)
        // A new LandscapeLock after a reboot sees the rotation already fixed to landscape and the copy on disk.
        val again = LandscapeLock(f)
        again.apply(true)
        assertEquals(Rotation(1, 3), f.saved)
        again.apply(false)
        assertEquals(Rotation(1, 3), f.current)
    }

    @Test fun `no permission changes nothing and a late grant takes over`() {
        val f = Fake(overlayOk = false, writeOk = false)
        val lock = LandscapeLock(f)
        assertEquals("no_permission", lock.apply(true))
        assertFalse(f.overlayShown); assertTrue(f.writes.isEmpty()); assertNull(f.saved)
        f.overlayOk = true
        assertEquals("on", lock.apply(true))
        assertTrue(f.overlayShown)
    }

    @Test fun `granting the overlay after the fallback hands the settings back`() {
        val f = Fake(overlayOk = false, writeOk = true, current = Rotation(1, 0))
        val lock = LandscapeLock(f)
        lock.apply(true)
        f.overlayOk = true
        assertEquals("on", lock.apply(true))
        assertTrue(f.overlayShown)
        assertEquals(Rotation(1, 0), f.current)
        assertNull(f.saved)
    }

    @Test fun `losing write settings while unlocked keeps the copy for later`() {
        val f = Fake(overlayOk = false, writeOk = true, current = Rotation(0, 2))
        val lock = LandscapeLock(f)
        lock.apply(true)
        f.writeOk = false
        lock.apply(false)
        assertEquals(Rotation(0, 2), f.saved)
        f.writeOk = true
        lock.apply(false)
        assertEquals(Rotation(0, 2), f.current)
        assertNull(f.saved)
    }

    @Test fun `the status carries what was asked and what happened`() {
        val f = Fake(overlayOk = false, writeOk = false)
        val lock = LandscapeLock(f)
        lock.apply(true)
        val s = lock.status(true)
        assertTrue(s.lock); assertEquals("no_permission", s.state); assertTrue(s.needsPermission)
    }
}
