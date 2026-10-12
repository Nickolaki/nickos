package com.nbaltas.nickos.sync

import java.util.Locale
import kotlin.math.abs

/**
 * Every line of copy on the Watch together screens that is worked out from state, kept pure so
 * it is tested once and the screens only place it. No Android imports.
 */
object TogetherText {
    /** "18:02" or "1:32:05", the same shape as the Jellyfin remote's times. */
    fun fmtMs(ms: Long): String {
        val s = (ms.coerceAtLeast(0) / 1000).toInt()
        return if (s >= 3600) String.format(Locale.US, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60)
        else String.format(Locale.US, "%d:%02d", s / 60, s % 60)
    }

    /** "1 h 32 min", "45 min", or "Length unknown" when the tablet could not tell. */
    fun duration(durMs: Long): String {
        val min = (durMs / 60_000).toInt()
        return when {
            durMs <= 0 -> "Length unknown"
            min >= 60 -> "${min / 60} h ${min % 60} min"
            else -> "${min.coerceAtLeast(1)} min"
        }
    }

    /** "Hailey", "Hailey and Leo", "Hailey, Leo and Mia". */
    fun join(names: List<String>): String = when (names.size) {
        0 -> ""
        1 -> names[0]
        else -> names.dropLast(1).joinToString(", ") + " and " + names.last()
    }

    /** "S2 E5 · The Lighthouse", or "S2 E5" when the file name stops at the code. */
    fun episodeLine(e: Episode): String = "S${e.season} E${e.number}" + if (e.label.isNotEmpty()) " · ${e.label}" else ""

    /** The word under a tablet's chip in the library header. */
    fun chip(online: Boolean, capable: Boolean, scanning: Boolean, perm: Boolean, movies: Int, downloading: Int): String = when {
        !online -> "Offline"
        !capable -> "Update needed"
        downloading > 0 -> "Downloading $downloading"
        scanning -> "Scanning..."
        !perm -> "No video access"
        movies == 1 -> "1 movie"
        else -> "$movies movies"
    }

    /** A library card's availability line. [all] means draw it in the "online" colour. */
    data class Availability(val text: String, val all: Boolean)

    /**
     * [owners] and [expected] are tablet ids ([Library.SELF] included); [expected] is this tablet
     * plus every online tablet that can do Watch together. [order] fixes the order names are read
     * out in ("This tablet" first) and [name] turns an id into "Hailey" or "This tablet".
     */
    fun availability(owners: Set<String>, expected: Set<String>, order: List<String>, name: (String) -> String): Availability {
        if (expected.size > 1 && owners.containsAll(expected)) return Availability("On all tablets", true)
        val names = order.filter { it in owners }.map(name)
        return if (names.size == 1) Availability("Only on ${names[0]}", false) else Availability("On ${join(names)}", false)
    }

    /** "2.9 GB" from a gigabyte up, "640 MB" below. */
    fun size(bytes: Long): String =
        if (bytes >= GB) String.format(Locale.US, "%.1f GB", bytes / GB.toDouble()) else "${bytes / MB} MB"

    /** "9.1 GB" under 10 GB, "35 GB" above, and "" when [bytes] is unknown (negative). */
    fun gb(bytes: Long): String = when {
        bytes < 0 -> ""
        bytes >= 10 * GB -> "${bytes / GB} GB"
        else -> String.format(Locale.US, "%.1f GB", bytes / GB.toDouble())
    }

    /** The line above a tablet's library: "12 movies on this tablet · 9.1 GB free". */
    fun libraryHeader(movies: Int, free: Long): String =
        (if (movies == 1) "1 movie on this tablet" else "$movies movies on this tablet") + gb(free).let { if (it.isEmpty()) "" else " · $it free" }

    /** The toast after a delete finished. [err] null means it worked. */
    fun deleteResult(err: String?, title: String, kid: String): String = when (err) {
        null -> "Deleted $title from $kid"
        "notOwned" -> "NickOS can't delete $title from here. It was copied onto the tablet by hand, so it has to be deleted there."
        "missing" -> "$title was already gone."
        else -> "Couldn't delete $title. Try again."
    }

    fun deleteTimeout(kid: String): String = "$kid didn't answer. Try again."

    fun percent(done: Long, size: Long): Int = if (size <= 0) 0 else (done * 100 / size).toInt().coerceIn(0, 100)

    /** "42% · 1.2 of 2.9 GB" for a running copy. */
    fun progress(done: Long, size: Long): String {
        val part = if (size >= GB) String.format(Locale.US, "%.1f", done / GB.toDouble()) else "${done / MB}"
        return "${percent(done, size)}% · $part of ${size(size)}"
    }

    /** The line next to a job in the pick panel while it is on its way. */
    fun jobProgress(j: SyncMessages.JobEntry): String = when (JobState.of(j.state)) {
        JobState.WAITING -> "Waiting for WiFi"
        JobState.VERIFYING -> "Checking the copy..."
        else -> progress(j.done, j.size)
    }

    /**
     * A failed job's reason in the pick panel. The Node reports "nospace:<free bytes>" so the
     * parent sees both numbers; a bare "nospace" still reads properly.
     */
    fun jobError(err: String?, size: Long): String {
        val code = err?.substringBefore(':')
        val free = err?.substringAfter(':', "")?.toLongOrNull()
        return when (code) {
            "nospace" -> if (free != null) "Not enough space (needs ${size(size)}, has ${size(free)})" else "Not enough space (needs ${size(size)})"
            "mismatch" -> "The copy didn't match, try again"
            "source" -> "The tablet that has it went offline"
            else -> "Couldn't save the file"
        }
    }

    /**
     * The accent line on a library card when tablets have a job for it. [jobs] pairs each kid's
     * name with their job. A failed copy wins, then one waiting for WiFi, else the copies on
     * their way with the lowest percentage. Null when there is nothing to say.
     */
    fun jobLine(jobs: List<Pair<String, SyncMessages.JobEntry>>): String? {
        val states = jobs.map { it to JobState.of(it.second.state) }
        states.firstOrNull { it.second == JobState.FAILED }?.let { (j, _) ->
            return "${j.first}: " + when (j.second.err?.substringBefore(':')) {
                "nospace" -> "not enough space"
                "mismatch" -> "the copy didn't match"
                "source" -> "the tablet that has it went offline"
                else -> "couldn't save the file"
            }
        }
        states.firstOrNull { it.second == JobState.WAITING }?.let { (j, _) -> return "${j.first}: waiting for WiFi" }
        val live = states.filter { it.second == JobState.QUEUED || it.second == JobState.RUNNING || it.second == JobState.VERIFYING }.map { it.first }
        if (live.isEmpty()) return null
        return "Sending to ${join(live.map { it.first })} ${live.minOf { percent(it.second.done, it.second.size) }}%"
    }

    /** True when a playing tablet is close enough to call "In sync" (and draw the green dot). */
    fun inSync(state: SyncState?, driftMs: Long?): Boolean = state == SyncState.PLAYING && driftMs != null && abs(driftMs) <= IN_SYNC_MS

    /**
     * A tablet's line in the remote. [driftMs] is positive when the tablet is ahead of the group.
     * [sinceReportMs] is how long ago its last report arrived, null when it has sent none.
     */
    fun stateLine(online: Boolean, state: SyncState?, driftMs: Long?, err: String?, sinceReportMs: Long?): String = when {
        !online -> "Offline, will catch up"
        state == null || sinceReportMs == null || sinceReportMs > STALE_MS -> "Waiting..."
        state == SyncState.PLAYING -> when {
            driftMs == null || abs(driftMs) <= IN_SYNC_MS -> "In sync"
            else -> String.format(Locale.US, "%.1f s %s", abs(driftMs) / 1000.0, if (driftMs < 0) "behind" else "ahead")
        }
        state == SyncState.LOADING || state == SyncState.READY -> "Starting..."
        state == SyncState.BUFFERING -> "Catching up"
        state == SyncState.PAUSED -> "Paused"
        state == SyncState.LOCKED -> "Locked"
        state == SyncState.ENDED -> "Finished"
        state == SyncState.ERROR -> error(err)
        else -> "Waiting..."
    }

    /** Why a tablet could not play. */
    fun error(err: String?): String = if (err == "missing") "Can't find this movie on the tablet" else "Can't play this file"

    const val IN_SYNC_MS = 250L
    const val STALE_MS = 5000L
    private const val MB = 1024L * 1024
    private const val GB = 1024L * MB
}
