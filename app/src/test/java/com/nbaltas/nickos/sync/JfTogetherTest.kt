package com.nbaltas.nickos.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JfTogetherTest {
    private val kids = setOf("a", "b")

    @Test fun everywhereAlreadyStartsNow() {
        assertEquals(JfTogether.Plan.START_NOW, JfTogether.plan(kids, setOf("a", "b", Library.SELF), kids))
    }

    @Test fun onTheParentOrSomeTabletsSends() {
        assertEquals(JfTogether.Plan.SEND, JfTogether.plan(kids, setOf(Library.SELF), emptySet()))
        assertEquals(JfTogether.Plan.SEND, JfTogether.plan(kids, setOf("a"), kids))
    }

    @Test fun savedNowhereSavesFirst() {
        assertEquals(JfTogether.Plan.SAVE, JfTogether.plan(kids, emptySet(), kids))
    }

    @Test fun anOfflineOnlyOwnerSavesInsteadOfSending() {
        assertEquals(JfTogether.Plan.SAVE, JfTogether.plan(setOf("b"), setOf("a"), setOf("b")))
    }

    @Test fun stuckSaysWhyAndNeverWhileSomethingCanStillHappen() {
        val kid = setOf("b")
        assertEquals("No tablet has this movie right now", JfTogether.stuck(kid, emptySet(), kid, false, false, false))
        assertEquals("Saving was cancelled", JfTogether.stuck(kid, emptySet(), kid, false, false, true))
        assertEquals("The tablet that has this movie is offline", JfTogether.stuck(kid, setOf("a"), kid, false, false, false))
        assertNull(JfTogether.stuck(kid, emptySet(), kid, true, false, false))
        assertNull(JfTogether.stuck(kid, setOf("a"), kid + "a", false, false, false))
        assertNull(JfTogether.stuck(kid, setOf("b"), kid, false, false, false))
    }

    @Test fun stallNamesWhoIsAwaited() {
        assertEquals("Still waiting for Leo", JfTogether.stallText(listOf("Leo")))
        assertEquals(60_000L, JfTogether.STALL_MS)
    }

    @Test fun twoDispatchFailuresWithoutProgressKeepTheStallPrompt() {
        var shown = JfTogether.showStallPrompt(false, progressResumed = false, timedOut = true)
        repeat(2) {
            // Try again restarts the clock, but the dispatch produces no progress.
            shown = JfTogether.showStallPrompt(shown, progressResumed = false, timedOut = false)
            assertTrue(shown)
            shown = JfTogether.showStallPrompt(shown, progressResumed = false, timedOut = true)
            assertTrue(shown)
        }
        assertEquals(false, JfTogether.showStallPrompt(shown, progressResumed = true, timedOut = false))
    }

    @Test fun startsTheMomentEveryoneOwnsIt() {
        assertNull(JfTogether.step(kids, kids, setOf(Library.SELF, "a"), started = false).start)
        val s = JfTogether.step(kids, kids, setOf(Library.SELF, "a", "b"), started = false)
        assertEquals(kids, s.start)
        assertEquals(emptySet<String>(), s.dropped)
    }

    @Test fun neverStartsTwiceForOneTap() {
        val s = JfTogether.step(kids, kids, setOf("a", "b"), started = true)
        assertNull(s.start)
        assertEquals(emptySet<String>(), s.dropped)
    }

    @Test fun anOfflineTabletIsDroppedAndTheRestStart() {
        val s = JfTogether.step(kids, setOf("a"), setOf("a"), started = false)
        assertEquals(setOf("b"), s.dropped)
        assertEquals(setOf("a"), s.start)
    }

    @Test fun anOfflineTabletIsDroppedEvenIfTheOthersStillWait() {
        val s = JfTogether.step(kids, setOf("a"), emptySet(), started = false)
        assertEquals(setOf("b"), s.dropped)
        assertNull(s.start)
    }

    @Test fun everyoneOfflineStartsNothing() {
        val s = JfTogether.step(kids, emptySet(), setOf("a", "b"), started = false)
        assertEquals(kids, s.dropped)
        assertNull(s.start)
    }

    @Test fun theNoteNamesWhoLeft() {
        val name = { id: String -> if (id == "a") "Leo" else "Mia" }
        assertEquals("Leo went offline, starting without them", JfTogether.droppedNote(setOf("a"), true, name))
        assertEquals("Leo and Mia went offline", JfTogether.droppedNote(setOf("a", "b"), false, name))
    }

    @Test fun rowsSayReadyWaitingFailedOrProgress() {
        assertEquals("Ready", JfTogether.rowText(true, null))
        assertEquals("Waiting", JfTogether.rowText(false, null))
        assertEquals("Failed", JfTogether.rowText(false, SyncMessages.JobEntry("f", "t", JobState.FAILED.wire, 0, 100, "source")))
        assertTrue(JfTogether.rowText(false, SyncMessages.JobEntry("f", "t", JobState.RUNNING.wire, 50, 100)).startsWith("50%"))
    }
}
