package com.nbaltas.nickos.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JfTogetherTest {
    private val kids = setOf("a", "b")

    @Test fun everywhereAlreadyStartsNow() {
        assertEquals(JfTogether.Plan.START_NOW, JfTogether.plan(kids, setOf("a", "b", Library.SELF)))
    }

    @Test fun onTheParentOrSomeTabletsSends() {
        assertEquals(JfTogether.Plan.SEND, JfTogether.plan(kids, setOf(Library.SELF)))
        assertEquals(JfTogether.Plan.SEND, JfTogether.plan(kids, setOf("a")))
    }

    @Test fun savedNowhereSavesFirst() {
        assertEquals(JfTogether.Plan.SAVE, JfTogether.plan(kids, emptySet()))
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
