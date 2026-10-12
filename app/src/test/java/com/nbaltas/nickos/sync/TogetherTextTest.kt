package com.nbaltas.nickos.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TogetherTextTest {
    private val gb = 1024L * 1024 * 1024

    @Test
    fun `free space reads one decimal under 10 GB, none above, and nothing when unknown`() {
        assertEquals("9.1 GB", TogetherText.gb(9_770_000_000))
        assertEquals("38 GB", TogetherText.gb(38 * gb))
        assertEquals("", TogetherText.gb(-1))
        assertEquals("12 movies on this tablet · 9.1 GB free", TogetherText.libraryHeader(12, 9_770_000_000))
        assertEquals("1 movie on this tablet", TogetherText.libraryHeader(1, -1))
    }

    @Test
    fun `delete toasts use the exact copy`() {
        assertEquals("Deleted Star Harbour from Hailey", TogetherText.deleteResult(null, "Star Harbour", "Hailey"))
        assertEquals("Star Harbour was already gone.", TogetherText.deleteResult("missing", "Star Harbour", "Hailey"))
        assertEquals("Couldn't delete Star Harbour. Try again.", TogetherText.deleteResult("io", "Star Harbour", "Hailey"))
        assertEquals("Hailey didn't answer. Try again.", TogetherText.deleteTimeout("Hailey"))
    }

    @Test
    fun `times and lengths`() {
        assertEquals("18:02", TogetherText.fmtMs(1_082_000))
        assertEquals("1:32:05", TogetherText.fmtMs(5_525_000))
        assertEquals("0:00", TogetherText.fmtMs(-5))
        assertEquals("1 h 32 min", TogetherText.duration(92 * 60_000L))
        assertEquals("45 min", TogetherText.duration(45 * 60_000L))
        assertEquals("1 min", TogetherText.duration(20_000))
        assertEquals("Length unknown", TogetherText.duration(0))
    }

    @Test
    fun `names are joined the way they are read out`() {
        assertEquals("", TogetherText.join(emptyList()))
        assertEquals("Hailey", TogetherText.join(listOf("Hailey")))
        assertEquals("Hailey and Leo", TogetherText.join(listOf("Hailey", "Leo")))
        assertEquals("Hailey, Leo and Mia", TogetherText.join(listOf("Hailey", "Leo", "Mia")))
    }

    @Test
    fun `episode line uses a middle dot`() {
        assertEquals("S2 E5 · The Lighthouse", TogetherText.episodeLine(Episode("Star Harbour", 2, 5, "The Lighthouse")))
        assertEquals("S1 E2", TogetherText.episodeLine(Episode("Star Harbour", 1, 2, "")))
    }

    @Test
    fun `chip word precedence`() {
        assertEquals("Offline", TogetherText.chip(false, true, true, true, 3, 1))
        assertEquals("Update needed", TogetherText.chip(true, false, false, true, 3, 0))
        assertEquals("Downloading 2", TogetherText.chip(true, true, true, true, 3, 2))
        assertEquals("Scanning...", TogetherText.chip(true, true, true, true, 3, 0))
        assertEquals("No video access", TogetherText.chip(true, true, false, false, 0, 0))
        assertEquals("1 movie", TogetherText.chip(true, true, false, true, 1, 0))
        assertEquals("12 movies", TogetherText.chip(true, true, false, true, 12, 0))
    }

    @Test
    fun `availability line`() {
        val order = listOf(Library.SELF, "h", "l")
        val name = { id: String -> mapOf(Library.SELF to "This tablet", "h" to "Hailey", "l" to "Leo")[id]!! }
        val all = setOf(Library.SELF, "h", "l")
        assertEquals(TogetherText.Availability("On all tablets", true), TogetherText.availability(all, all, order, name))
        assertEquals(TogetherText.Availability("On This tablet and Hailey", false), TogetherText.availability(setOf("h", Library.SELF), all, order, name))
        assertEquals(TogetherText.Availability("Only on Leo", false), TogetherText.availability(setOf("l"), all, order, name))
        // Nobody else is online: "all tablets" would be a boast about one.
        assertEquals("Only on This tablet", TogetherText.availability(setOf(Library.SELF), setOf(Library.SELF), order, name).text)
    }

    @Test
    fun `sizes and progress`() {
        assertEquals("2.9 GB", TogetherText.size((2.9 * gb).toLong()))
        assertEquals("640 MB", TogetherText.size(640L * 1024 * 1024))
        assertEquals(42, TogetherText.percent(42, 100))
        assertEquals(0, TogetherText.percent(5, 0))
        assertEquals("41% · 1.2 of 2.9 GB", TogetherText.progress((1.2 * gb).toLong(), (2.9 * gb).toLong()))
    }

    private fun job(state: JobState, done: Long = 42, size: Long = 100, err: String? = null) =
        SyncMessages.JobEntry("f", "Captain Comet", state.wire, done, size, err)

    @Test
    fun `job progress and errors in the pick panel`() {
        assertEquals("Waiting for WiFi", TogetherText.jobProgress(job(JobState.WAITING)))
        assertEquals("Checking the copy...", TogetherText.jobProgress(job(JobState.VERIFYING)))
        assertTrue(TogetherText.jobProgress(job(JobState.RUNNING)).startsWith("42%"))
        assertEquals("Not enough space (needs 2.0 GB, has 512 MB)", TogetherText.jobError("nospace:${512L * 1024 * 1024}", 2 * gb))
        assertEquals("Not enough space (needs 2.0 GB)", TogetherText.jobError("nospace", 2 * gb))
        assertEquals("The copy didn't match, try again", TogetherText.jobError("mismatch", 1))
        assertEquals("The tablet that has it went offline", TogetherText.jobError("source", 1))
        assertEquals("Couldn't save the file", TogetherText.jobError("io", 1))
        assertEquals("Couldn't save the file", TogetherText.jobError(null, 1))
    }

    @Test
    fun `job line on a library card`() {
        assertNull(TogetherText.jobLine(emptyList()))
        assertNull(TogetherText.jobLine(listOf("Leo" to job(JobState.DONE))))
        assertEquals("Sending to Leo 42%", TogetherText.jobLine(listOf("Leo" to job(JobState.RUNNING))))
        assertEquals("Sending to Leo and Mia 10%", TogetherText.jobLine(listOf("Leo" to job(JobState.RUNNING), "Mia" to job(JobState.QUEUED, done = 10))))
        assertEquals("Leo: waiting for WiFi", TogetherText.jobLine(listOf("Mia" to job(JobState.RUNNING), "Leo" to job(JobState.WAITING))))
        assertEquals("Leo: not enough space", TogetherText.jobLine(listOf("Mia" to job(JobState.WAITING), "Leo" to job(JobState.FAILED, err = "nospace:5"))))
    }

    @Test
    fun `a tablet's line in the remote`() {
        fun line(state: SyncState?, drift: Long? = 0, online: Boolean = true, err: String? = null, since: Long? = 100) =
            TogetherText.stateLine(online, state, drift, err, since)
        assertEquals("In sync", line(SyncState.PLAYING, 250))
        assertEquals("In sync", line(SyncState.PLAYING, null))
        assertEquals("0.4 s behind", line(SyncState.PLAYING, -400))
        assertEquals("0.4 s ahead", line(SyncState.PLAYING, 400))
        assertEquals("Starting...", line(SyncState.LOADING))
        assertEquals("Starting...", line(SyncState.READY))
        assertEquals("Catching up", line(SyncState.BUFFERING))
        assertEquals("Paused", line(SyncState.PAUSED))
        assertEquals("Locked", line(SyncState.LOCKED))
        assertEquals("Finished", line(SyncState.ENDED))
        assertEquals("Can't find this movie on the tablet", line(SyncState.ERROR, err = "missing"))
        assertEquals("Can't play this file", line(SyncState.ERROR, err = "decode"))
        assertEquals("Offline, will catch up", line(SyncState.PLAYING, online = false))
        assertEquals("Waiting...", line(SyncState.PLAYING, since = 5001))
        assertEquals("Waiting...", line(null, since = null))
        assertTrue(TogetherText.inSync(SyncState.PLAYING, -250))
        assertFalse(TogetherText.inSync(SyncState.PLAYING, 251))
        assertFalse(TogetherText.inSync(SyncState.PAUSED, 0))
        assertFalse(TogetherText.inSync(SyncState.PLAYING, null))
    }
}
