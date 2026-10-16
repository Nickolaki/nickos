package com.nbaltas.nickos.talk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TalkPlanTest {
    private fun t(id: String, name: String, online: Boolean = true, capable: Boolean = true) = TalkTarget(id, name, online, capable)

    // ---- recipients ----
    @Test fun `only online tablets that can hear are recipients`() {
        val shown = listOf(t("a", "Hailey"), t("b", "Leo", online = false), t("c", "Mia", capable = false), t("d", "Zoe"))
        assertEquals(listOf("a", "d"), TalkRecipients.freeze(shown).map { it.id })
    }

    @Test fun `a tablet listed twice is one recipient`() {
        assertEquals(listOf("a"), TalkRecipients.freeze(listOf(t("a", "Hailey"), t("a", "Hailey"))).map { it.id })
    }

    @Test fun `frozen recipients do not change when the screen does`() {
        val shown = mutableListOf(t("a", "Hailey"), t("b", "Leo"))
        val frozen = TalkRecipients.freeze(shown)
        shown += t("c", "Mia")
        shown.removeAt(0)
        assertEquals(listOf("a", "b"), frozen.map { it.id })
    }

    // ---- words ----
    @Test fun `who lists names and counts when there are many`() {
        assertEquals("Hailey", TalkText.who(listOf("Hailey")))
        assertEquals("Hailey, Leo", TalkText.who(listOf("Hailey", "Leo")))
        assertEquals("Hailey, Leo, Mia", TalkText.who(listOf("Hailey", "Leo", "Mia")))
        assertEquals("4 tablets", TalkText.who(listOf("Hailey", "Leo", "Mia", "Zoe")))
        assertEquals("Talking to Hailey, Leo", TalkText.talking(listOf("Hailey", "Leo")))
    }

    @Test fun `the banner uses the parent's name when there is one`() {
        assertEquals("Dad is talking", TalkText.banner("Dad's Tablet"))
        assertEquals("Mum is talking", TalkText.banner("Mum"))
        assertEquals("Dad is talking", TalkText.banner("Dad’s tablet"))
        assertEquals("Parent is talking", TalkText.banner(null))
        assertEquals("Parent is talking", TalkText.banner("  "))
        // The default name for an unnamed Master.
        assertEquals("Parent is talking", TalkText.banner("Parent's Tablet"))
    }

    @Test fun `no talk words contain a dash`() {
        val words = listOf(
            TalkText.HOLD, TalkText.NOBODY, TalkText.UPDATE, TalkText.UPDATE_HINT, TalkText.BANNER_FALLBACK, TalkText.NEEDS_MIC,
            TalkText.MIC_FAILED, TalkText.MIC_ALLOWED, TalkText.MIC_DENIED, TalkBar.TALKING, TalkText.talking(listOf("Hailey")),
            TalkText.ready(listOf("Hailey")), TalkText.banner("Dad's Tablet"),
        )
        val em = 0x2014.toChar()
        val en = 0x2013.toChar()
        for (w in words) assertTrue(w, em !in w && en !in w && " - " !in w)
    }

    // ---- the bar ----
    @Test fun `the bar offers to talk to who can hear`() {
        val bar = TalkBar.of(listOf(t("a", "Hailey"), t("b", "Leo")), null)
        assertTrue(bar.enabled); assertFalse(bar.talking)
        assertEquals("Hold to talk", bar.button)
        assertEquals("Hold to talk to Hailey, Leo", bar.line)
    }

    @Test fun `while talking the bar shows the frozen names, not the screen`() {
        val bar = TalkBar.of(listOf(t("a", "Hailey")), listOf("Hailey", "Leo"))
        assertTrue(bar.talking)
        assertEquals("Talking", bar.button)
        assertEquals("Talking to Hailey, Leo", bar.line)
    }

    @Test fun `an old tablet greys the bar and says to update`() {
        val bar = TalkBar.of(listOf(t("a", "Hailey", capable = false)), null)
        assertFalse(bar.enabled)
        assertEquals("Update this tablet", bar.line)
    }

    @Test fun `one old tablet does not stop the others`() {
        val bar = TalkBar.of(listOf(t("a", "Hailey", capable = false), t("b", "Leo")), null)
        assertTrue(bar.enabled)
        assertEquals("Hold to talk to Leo", bar.line)
    }

    @Test fun `an offline tablet greys the bar with nobody to talk to`() {
        val bar = TalkBar.of(listOf(t("a", "Hailey", online = false)), null)
        assertFalse(bar.enabled)
        assertEquals("No tablet to talk to", bar.line)
    }

    // ---- stop policy ----
    private fun inputs(permission: Boolean = true, audioOk: Boolean = true, live: Int = 2, elapsed: Long = 1_000) =
        TalkStopPolicy.Inputs(permission, audioOk, live, elapsed)

    @Test fun `talking carries on while everything is fine`() {
        assertNull(TalkStopPolicy.reason(inputs()))
        assertNull(TalkStopPolicy.reason(inputs(live = 1, elapsed = TalkStopPolicy.MAX_MS - 1)))
    }

    @Test fun `each failure stops it with its own reason`() {
        assertEquals(TalkStop.PERMISSION_LOST, TalkStopPolicy.reason(inputs(permission = false)))
        assertEquals(TalkStop.AUDIO_ERROR, TalkStopPolicy.reason(inputs(audioOk = false)))
        assertEquals(TalkStop.ALL_GONE, TalkStopPolicy.reason(inputs(live = 0)))
        assertEquals(TalkStop.TIME_CAP, TalkStopPolicy.reason(inputs(elapsed = TalkStopPolicy.MAX_MS)))
    }

    @Test fun `when several things fail at once the permission is named first`() {
        assertEquals(TalkStop.PERMISSION_LOST, TalkStopPolicy.reason(inputs(permission = false, audioOk = false, live = 0, elapsed = TalkStopPolicy.MAX_MS)))
    }

    @Test fun `the parent is told about failures but not about letting go`() {
        for (r in TalkStop.values()) {
            val quiet = r in setOf(TalkStop.RELEASED, TalkStop.CANCELLED, TalkStop.LEFT_SCREEN, TalkStop.BACKGROUNDED)
            assertEquals(r.name, !quiet, TalkStopPolicy.worthSaying(r))
        }
    }

    // ---- the queue ----
    @Test fun `the queue keeps order`() {
        val q = FrameQueue<Int>(5)
        listOf(1, 2, 3).forEach { q.offer(it) }
        assertEquals(listOf(1, 2, 3), generateSequence { q.poll() }.toList())
    }

    @Test fun `a full queue drops its oldest frame`() {
        val q = FrameQueue<Int>(3)
        assertEquals(0, q.offer(1)); assertEquals(0, q.offer(2)); assertEquals(0, q.offer(3))
        assertEquals(1, q.offer(4))
        assertEquals(1, q.offer(5))
        assertEquals(3, q.size)
        assertEquals(2L, q.dropped)
        assertEquals(listOf(3, 4, 5), generateSequence { q.poll() }.toList())
    }

    @Test fun `the default queue holds about a second`() {
        assertEquals(25, FrameQueue.DEFAULT_CAPACITY)
        assertEquals(1_000, FrameQueue.DEFAULT_CAPACITY * TalkMessages.FRAME_MS)
    }

    @Test fun `clear empties the queue`() {
        val q = FrameQueue<Int>(3)
        q.offer(1); q.clear()
        assertNull(q.poll())
    }
}
