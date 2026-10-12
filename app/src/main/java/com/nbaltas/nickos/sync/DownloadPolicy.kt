package com.nbaltas.nickos.sync

/** A download job's state, shared by the Node's job runner and the Master's display of it. */
enum class JobState {
    QUEUED, RUNNING, WAITING, VERIFYING, DONE, FAILED, CANCELLED;

    /** The lowercase name in a `jobs` message. */
    val wire: String get() = name.lowercase()

    companion object {
        /** Null for a state this build does not know. */
        fun of(wire: String?): JobState? = entries.firstOrNull { it.wire == wire }
    }
}

/**
 * Pure state-machine and backoff maths for [JobState]. The runner (`node/DownloadJobs.kt`) is
 * the only thing that touches the filesystem or the network; everything decidable without
 * those lives here so it can be tested on the JVM.
 */
object DownloadPolicy {
    private val TERMINAL = setOf(JobState.DONE, JobState.FAILED, JobState.CANCELLED)

    fun isTerminal(state: JobState): Boolean = state in TERMINAL

    /** 5 s, 10 s, 20 s, 40 s, 60 s, 60 s... in ms. */
    fun backoff(tries: Int): Long = (5_000L * (1L shl (tries - 1).coerceAtLeast(0))).coerceAtMost(60_000L)

    fun onStart(state: JobState): JobState = if (state == JobState.QUEUED || state == JobState.WAITING) JobState.RUNNING else state

    fun onError(state: JobState): JobState = if (!isTerminal(state)) JobState.WAITING else state

    fun onBytesComplete(done: Long, size: Long): JobState = if (done == size) JobState.VERIFYING else JobState.FAILED

    fun onVerify(fingerprintMatches: Boolean): JobState = if (fingerprintMatches) JobState.DONE else JobState.FAILED

    fun cancel(state: JobState): JobState = if (!isTerminal(state)) JobState.CANCELLED else state

    /** After this many consecutive failures the Master should re-pick a source. */
    fun shouldFlagSource(tries: Int): Boolean = tries >= 30

    /** A fresh `fetch` (new url from the Master) resets the retry counter. */
    fun onFreshFetch(): Int = 0
}
