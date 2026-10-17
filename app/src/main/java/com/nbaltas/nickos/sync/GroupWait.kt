package com.nbaltas.nickos.sync

/** What the group does while a streamed movie plays. */
enum class WaitMode { PLAYING, AUTO_WAIT, MANUAL_PAUSE, STOPPED }

/** What [GroupWait] asks the Master to do. */
sealed class WaitAct {
    /** Pause everyone: these tablets are still loading. */
    data class Pause(val who: Set<String>) : WaitAct()
    /** Everyone is ready: play on. */
    object Resume : WaitAct()
    /** This tablet took too long. The others carry on without waiting for it. */
    data class TooSlow(val id: String) : WaitAct()
}

/**
 * The group wait for one streamed session, as a small state machine with no clock or sockets of its
 * own: the caller hands in "now" (ms) and every report. A tablet that buffers for more than
 * [BUFFER_MS] while the group plays pauses everyone, and everyone resumes when all are ready.
 * A tablet that is failed, gone or too slow is left out; a pause, seek or stop by the parent
 * cancels the wait and the group never resumes by itself after the parent paused it.
 *
 * [enabled] false (a movie that is a file on the tablets) makes every call do nothing.
 * Not thread safe: the caller holds its lock.
 */
class GroupWait(val enabled: Boolean) {
    var mode = WaitMode.PLAYING
        private set
    /** The seek or start generation. A report from an older one is ignored. */
    var gen = 0
        private set

    private val last = HashMap<String, SyncState>()
    private val bufferingSince = HashMap<String, Long>()
    private val out = HashSet<String>()
    private var lastPause = Long.MIN_VALUE / 2
    private var waitStart = 0L

    /** A new movie, or a restart: forget everything. */
    fun start(nodes: Set<String>) {
        gen++
        mode = WaitMode.PLAYING
        last.clear(); bufferingSince.clear(); out.clear()
        lastPause = Long.MIN_VALUE / 2
        nodes.forEach { last[it] = SyncState.LOADING }
    }

    /** The tablets we are still waiting for, in the order they are named. Empty unless waiting. */
    fun waitingFor(): Set<String> = if (mode == WaitMode.AUTO_WAIT) notReady() else emptySet()

    /** A report from tablet [id]. [reportGen] is the generation the report belongs to. */
    fun report(id: String, state: SyncState, now: Long, reportGen: Int = gen): List<WaitAct> {
        if (!enabled || mode == WaitMode.STOPPED || reportGen != gen || id !in last) return emptyList()
        last[id] = state
        when {
            state.failed -> { out += id; bufferingSince -= id }
            state == SyncState.BUFFERING -> bufferingSince.getOrPut(id) { now }
            else -> {
                bufferingSince -= id
                if (state.ready) out -= id // a tablet that was too slow is counted again once it is ready
            }
        }
        return decide(now)
    }

    /** Time passing. Call at least every few hundred ms. */
    fun tick(now: Long): List<WaitAct> = if (!enabled || mode == WaitMode.STOPPED) emptyList() else decide(now)

    /** The tablet lost its link: leave it out until it reports ready again. */
    fun offline(id: String, now: Long): List<WaitAct> {
        if (!enabled || id !in last || id in out) return emptyList()
        out += id; bufferingSince -= id
        return if (mode == WaitMode.STOPPED) emptyList() else decide(now)
    }

    /** The tablet is gone for good (removed, finished): leave it out. */
    fun gone(id: String, now: Long): List<WaitAct> {
        if (!enabled || id !in last) return emptyList()
        last.remove(id); bufferingSince -= id; out -= id
        return if (mode == WaitMode.STOPPED) emptyList() else decide(now)
    }

    /** The parent paused. An automatic wait is over and nothing resumes by itself. */
    fun manualPause() { if (enabled && mode != WaitMode.STOPPED) { mode = WaitMode.MANUAL_PAUSE; bufferingSince.clear() } }

    /** The parent pressed play. */
    fun manualResume() { if (enabled && mode != WaitMode.STOPPED) { mode = WaitMode.PLAYING; bufferingSince.clear() } }

    /** The parent moved the group to a new spot; [playing] says whether the group plays after it. Older reports are stale now. */
    fun seek(playing: Boolean) {
        if (!enabled || mode == WaitMode.STOPPED) return
        gen++
        bufferingSince.clear()
        mode = if (playing) WaitMode.PLAYING else WaitMode.MANUAL_PAUSE
    }

    /** The session is over. */
    fun stop() { mode = WaitMode.STOPPED; gen++; last.clear(); bufferingSince.clear(); out.clear() }

    private fun notReady(): Set<String> = last.filter { (id, s) -> id !in out && s.loading }.keys.sorted().toCollection(LinkedHashSet())

    private fun decide(now: Long): List<WaitAct> {
        when (mode) {
            WaitMode.PLAYING -> {
                val late = bufferingSince.filter { (id, since) -> id !in out && now - since > BUFFER_MS }.keys
                if (late.isNotEmpty() && now - lastPause >= FLAP_MS) {
                    mode = WaitMode.AUTO_WAIT
                    lastPause = now
                    waitStart = now
                    return listOf(WaitAct.Pause(notReady()))
                }
            }
            WaitMode.AUTO_WAIT -> {
                val acts = ArrayList<WaitAct>()
                if (now - waitStart >= MAX_WAIT_MS) for (id in notReady()) { out += id; acts += WaitAct.TooSlow(id) }
                if (notReady().isEmpty()) { mode = WaitMode.PLAYING; bufferingSince.clear(); acts += WaitAct.Resume }
                return acts
            }
            else -> {}
        }
        return emptyList()
    }

    private val SyncState.failed get() = this == SyncState.ERROR || this == SyncState.STOPPED || this == SyncState.ENDED
    private val SyncState.loading get() = this == SyncState.BUFFERING || this == SyncState.LOADING
    private val SyncState.ready get() = this == SyncState.READY || this == SyncState.PLAYING || this == SyncState.PAUSED

    companion object {
        /** A tablet buffering longer than this pauses the group. */
        const val BUFFER_MS = 3000L
        /** At most one automatic pause in this time. */
        const val FLAP_MS = 10_000L
        /** The longest the group waits for one tablet. */
        const val MAX_WAIT_MS = 30_000L
    }
}
