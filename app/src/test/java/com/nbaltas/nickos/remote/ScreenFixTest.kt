package com.nbaltas.nickos.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The security fix round: sealed links only, an indicator before capture, control only while that Master watches. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScreenFixTest {
    // ---- sealed links only ----

    private var n = 0
    private fun keys() = ScreenKeys { "key${++n}".padEnd(32, '0') }

    @Test
    fun `an unsealed session next to a sealed viewer gets no key and no port`() {
        val k = keys()
        val sealedKey = k.keyFor("A")
        k.keyFor("V1") // a legacy Master that also asked
        val sealed = k.stateFor(RemoteMessages.LIVE, RemoteMessages.CONTROL_READY, 47473, "A", sealed = true)
        assertEquals(sealedKey, sealed.key)
        assertEquals(47473, sealed.port)
        val legacy = k.stateFor(RemoteMessages.LIVE, RemoteMessages.CONTROL_READY, 47473, "V1", sealed = false)
        assertNull(legacy.key)
        assertEquals(0, legacy.port)
        assertEquals(RemoteMessages.LIVE, legacy.state)
        // And on the wire there is no key text at all.
        val line = RemoteMessages.encodeState(legacy).toString()
        assertFalse(line.contains("key"))
        assertFalse(line.contains(sealedKey))
    }

    @Test
    fun `an unsealed link cannot start, stop or touch`() {
        assertFalse(ScreenGate.allows(false))
        assertTrue(ScreenGate.allows(true))
        assertTrue(RemoteMessages.isScreenCmd("screenStart"))
        assertTrue(RemoteMessages.isScreenCmd("screenStop"))
        assertTrue(RemoteMessages.isScreenCmd("touch"))
        assertFalse(RemoteMessages.isScreenCmd("camStart"))
    }

    @Test
    fun `a Master that never asked gets no key even on a sealed link`() {
        val k = keys()
        k.keyFor("A")
        assertNull(k.stateFor(RemoteMessages.LIVE, RemoteMessages.CONTROL_OFF, 1, "B", sealed = true).key)
        assertNull(k.stateFor(RemoteMessages.READY, RemoteMessages.CONTROL_OFF, 1, "A", sealed = true).key)
    }

    @Test
    fun `each Master has its own key`() {
        val k = keys()
        assertNotEquals(k.keyFor("A"), k.keyFor("B"))
        assertEquals(k.keyFor("A"), k.keyFor("A"))
    }

    // ---- the indicator comes first ----

    private class Fake(
        var overlayPerm: Boolean = false,
        var addWorks: Boolean = true,
        var noticeOk: Boolean = true,
    ) : ScreenIndicatorPort {
        var view = false
        var notice = false
        override fun canOverlay() = overlayPerm
        override fun showOverlay(): Boolean { view = addWorks; return view }
        override fun overlayUp() = view
        override fun hideOverlay() { view = false }
        override fun noticeReady() = noticeOk
        override fun showNotice(): Boolean { notice = noticeOk; return notice }
        override fun noticeUp() = notice
        override fun clearNotice() { notice = false }
    }

    @Test
    fun `notifications denied and no overlay means no capture`() {
        val p = Fake(overlayPerm = false, noticeOk = false)
        val i = ScreenIndicator(p)
        assertFalse(i.show())
        assertEquals(ScreenIndicator.Way.NONE, i.way)
        assertFalse(p.notice)
    }

    @Test
    fun `a blocked channel means no capture`() {
        // Permission is there but the screenwatch channel is switched off: ready() is false, nothing is posted.
        val p = Fake(overlayPerm = false, noticeOk = false)
        assertFalse(ScreenIndicator(p).show())
        assertFalse(p.notice)
    }

    @Test
    fun `an overlay that fails to add falls back to the notification`() {
        val p = Fake(overlayPerm = true, addWorks = false, noticeOk = true)
        val i = ScreenIndicator(p)
        assertTrue(i.show())
        assertEquals(ScreenIndicator.Way.NOTICE, i.way)
        assertTrue(p.notice)
    }

    @Test
    fun `an overlay that fails to add with no notification means no capture`() {
        val p = Fake(overlayPerm = true, addWorks = false, noticeOk = false)
        val i = ScreenIndicator(p)
        assertFalse(i.show())
        assertEquals(ScreenIndicator.Way.NONE, i.way)
    }

    @Test
    fun `the overlay is used when it works and the notification stays away`() {
        val p = Fake(overlayPerm = true)
        val i = ScreenIndicator(p)
        assertTrue(i.show())
        assertEquals(ScreenIndicator.Way.OVERLAY, i.way)
        assertTrue(p.view)
        assertFalse(p.notice)
    }

    @Test
    fun `overlay revoked mid session switches to the notification`() {
        val p = Fake(overlayPerm = true)
        val i = ScreenIndicator(p)
        assertTrue(i.show())
        p.overlayPerm = false
        p.view = false
        assertTrue(i.maintain())
        assertEquals(ScreenIndicator.Way.NOTICE, i.way)
        assertTrue(p.notice)
        assertTrue(i.maintain())
    }

    @Test
    fun `overlay revoked and notifications unavailable stops capture`() {
        val p = Fake(overlayPerm = true, noticeOk = false)
        val i = ScreenIndicator(p)
        assertTrue(i.show())
        p.overlayPerm = false
        p.view = false
        assertFalse(i.maintain())
        assertEquals(ScreenIndicator.Way.NONE, i.way)
    }

    @Test
    fun `the notification channel blocked mid session stops capture`() {
        val p = Fake(overlayPerm = false)
        val i = ScreenIndicator(p)
        assertTrue(i.show())
        p.noticeOk = false
        assertFalse(i.maintain())
    }

    @Test
    fun `a swiped away notification is posted again`() {
        val p = Fake(overlayPerm = false)
        val i = ScreenIndicator(p)
        assertTrue(i.show())
        p.notice = false
        assertTrue(i.maintain())
        assertTrue(p.notice)
    }

    @Test
    fun `clear takes everything down`() {
        val p = Fake(overlayPerm = true)
        val i = ScreenIndicator(p)
        i.show()
        i.clear()
        assertFalse(p.view)
        assertFalse(p.notice)
        assertEquals(ScreenIndicator.Way.NONE, i.way)
    }

    @Test
    fun `needs_permission is a state a Master understands`() {
        val s = RemoteMessages.State(RemoteMessages.NEEDS_PERMISSION)
        assertEquals(s, RemoteMessages.decodeState(org.json.JSONObject(RemoteMessages.encodeState(s).toString())))
    }

    // Control needs a real viewer of that Master: see ScreenServerTest.
}
