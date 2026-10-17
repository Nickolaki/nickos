package com.nbaltas.nickos.node

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nbaltas.nickos.data.Role
import com.nbaltas.nickos.data.Store
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
    private class Fake(
        var overlayOk: Boolean, var writeOk: Boolean, var current: Rotation = Rotation(1, 0), var naturalLandscape: Boolean = false,
    ) : RotationPort {
        var overlayShown = false
        var writes = mutableListOf<Rotation>()
        var saveCount = 0
        var failWrites = false      // a write that returns false
        var throwWrites = false     // a write that throws
        var failSave = false        // the disk refuses
        var overlayFails = false    // the window cannot be added
        var keep: Rotation? = null
        override fun canOverlay() = overlayOk
        override fun canWrite() = writeOk
        override fun naturalLandscape() = naturalLandscape
        override fun read() = current
        override fun write(r: Rotation): Boolean {
            if (throwWrites) throw SecurityException("no write")
            if (failWrites) return false
            writes += r; current = r; return true
        }
        override fun overlay(on: Boolean): Boolean {
            if (on && overlayFails) return false
            overlayShown = on; return true
        }
        override fun saved() = keep
        override fun save(r: Rotation?): Boolean {
            if (failSave) return false
            if (r != null) saveCount++
            keep = r; return true
        }
    }

    private val land = LandscapePolicy.landscapeRotation(false)

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

    // ---- the natural orientation decides which turn looks landscape ----
    @Test fun `the target rotation follows the natural orientation`() {
        assertEquals(Rotation(0, 1), LandscapePolicy.landscapeRotation(naturalLandscape = false))
        assertEquals(Rotation(0, 0), LandscapePolicy.landscapeRotation(naturalLandscape = true))
    }

    @Test fun `natural orientation is read from the rotation and the size`() {
        assertFalse(LandscapePolicy.naturalIsLandscape(0, 800, 1280))
        assertTrue(LandscapePolicy.naturalIsLandscape(0, 1280, 800))
        assertFalse("portrait tablet turned sideways", LandscapePolicy.naturalIsLandscape(1, 1280, 800))
        assertTrue("landscape tablet turned upright", LandscapePolicy.naturalIsLandscape(1, 800, 1280))
        assertFalse(LandscapePolicy.naturalIsLandscape(3, 1280, 800))
        assertTrue(LandscapePolicy.naturalIsLandscape(2, 1280, 800))
    }

    @Test fun `the fallback writes the right turn on a naturally portrait and a naturally landscape tablet`() {
        val portrait = Fake(overlayOk = false, writeOk = true, naturalLandscape = false)
        LandscapeLock(portrait).apply(true)
        assertEquals(Rotation(0, 1), portrait.current)
        val wide = Fake(overlayOk = false, writeOk = true, current = Rotation(1, 2), naturalLandscape = true)
        LandscapeLock(wide).apply(true)
        assertEquals(Rotation(0, 0), wide.current)
        assertEquals(Rotation(1, 2), wide.keep)
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
        assertEquals(Rotation(1, 3), f.keep)
        assertEquals(listOf(land), f.writes)
    }

    @Test fun `turning it off puts the saved rotation back once`() {
        val f = Fake(overlayOk = false, writeOk = true, current = Rotation(1, 3))
        val lock = LandscapeLock(f)
        lock.apply(true)
        assertEquals("off", lock.apply(false))
        assertEquals(Rotation(1, 3), f.current)
        assertNull(f.keep)
        val writesAfter = f.writes.size
        lock.apply(false); lock.apply(false)
        assertEquals("restore happens once", writesAfter, f.writes.size)
    }

    @Test fun `applying again changes nothing and never saves over the first copy`() {
        val f = Fake(overlayOk = false, writeOk = true, current = Rotation(1, 3))
        val lock = LandscapeLock(f)
        repeat(5) { lock.apply(true) }
        assertEquals(1, f.saveCount)
        assertEquals(Rotation(1, 3), f.keep)
        assertEquals("one write for five applies", 1, f.writes.size)
    }

    @Test fun `a restart in the middle keeps the first copy`() {
        val f = Fake(overlayOk = false, writeOk = true, current = Rotation(1, 3))
        LandscapeLock(f).apply(true)
        val again = LandscapeLock(f)
        again.apply(true)
        assertEquals(Rotation(1, 3), f.keep)
        again.apply(false)
        assertEquals(Rotation(1, 3), f.current)
    }

    @Test fun `no permission changes nothing and a late grant takes over`() {
        val f = Fake(overlayOk = false, writeOk = false)
        val lock = LandscapeLock(f)
        assertEquals("no_permission", lock.apply(true))
        assertFalse(f.overlayShown); assertTrue(f.writes.isEmpty()); assertNull(f.keep)
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
        assertNull(f.keep)
    }

    @Test fun `losing write settings while unlocked keeps the copy and says it is still pending`() {
        val f = Fake(overlayOk = false, writeOk = true, current = Rotation(0, 2))
        val lock = LandscapeLock(f)
        lock.apply(true)
        f.writeOk = false
        assertEquals("pending", lock.apply(false))
        assertEquals(Rotation(0, 2), f.keep)
        f.writeOk = true
        assertEquals("off", lock.apply(false))
        assertEquals(Rotation(0, 2), f.current)
        assertNull(f.keep)
    }

    @Test fun `the status carries what was asked and what happened`() {
        val f = Fake(overlayOk = false, writeOk = false)
        val lock = LandscapeLock(f)
        lock.apply(true)
        val s = lock.status(true)
        assertTrue(s.lock); assertEquals("no_permission", s.state); assertTrue(s.needsPermission)
    }

    // ---- failures: the reported state is what really happened ----
    @Test fun `a write that returns false is pending and keeps the copy`() {
        val f = Fake(overlayOk = false, writeOk = true, current = Rotation(1, 3))
        f.failWrites = true
        val lock = LandscapeLock(f)
        assertEquals("pending", lock.apply(true))
        assertEquals(Rotation(1, 3), f.keep)
        f.failWrites = false
        assertEquals("on", lock.apply(true))
        assertEquals(1, f.saveCount)
    }

    @Test fun `a write that throws is pending, not on`() {
        val f = Fake(overlayOk = false, writeOk = true)
        f.throwWrites = true
        assertEquals("pending", LandscapeLock(f).apply(true))
    }

    @Test fun `a failed restore keeps the only saved copy`() {
        val f = Fake(overlayOk = false, writeOk = true, current = Rotation(1, 3))
        val lock = LandscapeLock(f)
        lock.apply(true)
        f.failWrites = true
        assertEquals("pending", lock.apply(false))
        assertEquals(Rotation(1, 3), f.keep)
        f.failWrites = false
        assertEquals("off", lock.apply(false))
        assertEquals(Rotation(1, 3), f.current)
        assertNull(f.keep)
    }

    @Test fun `a restore that throws keeps the copy too`() {
        val f = Fake(overlayOk = false, writeOk = true, current = Rotation(1, 3))
        val lock = LandscapeLock(f)
        lock.apply(true)
        f.throwWrites = true
        assertEquals("pending", lock.apply(false))
        assertEquals(Rotation(1, 3), f.keep)
    }

    @Test fun `if the copy cannot be saved the tablet settings are not touched`() {
        val f = Fake(overlayOk = false, writeOk = true, current = Rotation(1, 3))
        f.failSave = true
        assertEquals("pending", LandscapeLock(f).apply(true))
        assertTrue("nothing written", f.writes.isEmpty())
        assertEquals(Rotation(1, 3), f.current)
        f.failSave = false
        assertEquals("on", LandscapeLock(f).apply(true))
        assertEquals(Rotation(1, 3), f.keep)
    }

    @Test fun `an overlay that cannot be added is pending when nothing else is allowed`() {
        val f = Fake(overlayOk = true, writeOk = false)
        f.overlayFails = true
        assertEquals("pending", LandscapeLock(f).apply(true))
        assertFalse(f.overlayShown)
    }

    @Test fun `an overlay that cannot be added falls back to the system rotation when it can be written`() {
        val f = Fake(overlayOk = true, writeOk = true, current = Rotation(1, 0))
        f.overlayFails = true
        assertEquals("on", LandscapeLock(f).apply(true))
        assertEquals(Rotation(1, 0), f.keep)
        assertEquals(land, f.current)
    }

    @Test fun `unlocking with the restore still waiting for permission is pending, not off`() {
        val f = Fake(overlayOk = false, writeOk = true, current = Rotation(1, 0))
        val lock = LandscapeLock(f)
        lock.apply(true)
        f.writeOk = false
        val s = lock.apply(false)
        assertEquals("pending", s)
        assertTrue(lock.status(false).pending)
    }

    // ---- reset must not lose a restore that has not finished ----
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    /** A port whose saved copy lives in the real Store, so reset really clears whatever it clears. */
    private class StorePort(val store: Store, val f: Fake) : RotationPort by f {
        override fun saved() = store.savedRotation()
        override fun save(r: Rotation?) = store.setSavedRotation(r)
    }

    @Test fun `reset with a revoked permission and a failed restore keeps the copy and a later retry puts it back`() {
        val store = Store(ctx)
        store.setRole(Role.NODE)
        val f = Fake(overlayOk = false, writeOk = true, current = Rotation(1, 3))
        val port = StorePort(store, f)
        val lock = LandscapeLock(port)
        store.setLandscapeLock(true)
        assertEquals("on", lock.apply(true))
        f.writeOk = false                        // the permission is taken away
        f.failWrites = true                      // and the write would fail anyway
        store.setLandscapeLock(false)
        assertEquals("pending", lock.apply(false))
        store.resetAll()                         // the reset wipes NickOS data, not the tablet's own saved rotation
        assertEquals(Rotation(1, 3), store.savedRotation())
        assertEquals(Rotation(1, 3), Store(ctx).savedRotation())
        assertFalse(store.landscapeLock.value)
        f.writeOk = true; f.failWrites = false  // permission comes back later
        assertEquals("off", LandscapeLock(StorePort(store, f)).apply(false))
        assertEquals(Rotation(1, 3), f.current)
        assertNull(store.savedRotation())
    }

    @Test fun `a reset with nothing pending leaves nothing behind`() {
        val store = Store(ctx)
        store.resetAll()
        assertNull(store.savedRotation())
    }

    @Test fun `the choice is only published when it reached the disk`() {
        val store = Store(ctx)
        assertTrue(store.setLandscapeLock(true))
        assertTrue(store.landscapeLock.value)
        assertTrue(store.setSavedRotation(Rotation(1, 2)))
        assertEquals(Rotation(1, 2), store.savedRotation())
    }
}
