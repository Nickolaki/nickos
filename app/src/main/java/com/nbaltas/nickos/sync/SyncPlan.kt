package com.nbaltas.nickos.sync

/**
 * The Master's model of one "watch together" session. All times are the Master's own
 * elapsedRealtime; a plan sent to a Node translates into that Node's clock (see [SyncPlan]).
 */
data class SyncSession(
    val fid: String,
    val title: String,
    val durMs: Long,
    val nodes: Set<String>,
    val anchorPos: Long,
    val anchorAt: Long,
    val playing: Boolean,
) {
    /** Where playback should be at master-clock time [now]. */
    fun expectedPos(now: Long): Long {
        val pos = if (playing) anchorPos + (now - anchorAt) else anchorPos
        val clamped = if (durMs > 0) pos.coerceIn(0, durMs) else pos
        return clamped.coerceAtLeast(0)
    }
}

/** What a Node is told: play [fid] so that at its own clock time [at] it is at position [pos]. */
data class SyncCmd(val fid: String, val pos: Long, val at: Long, val title: String)

object SyncPlan {
    /**
     * Builds the command for one Node. [nodeOffset] is that Node's [ClockSync.offset]
     * (nodeClock - masterClock). [at] is already translated into the Node's own clock, which
     * is the one detail a plan commonly gets wrong.
     */
    fun planFor(session: SyncSession, nodeOffset: Long, now: Long, leadMs: Long = 1500): SyncCmd {
        val targetMasterTime = now + leadMs
        val pos = if (session.playing) session.expectedPos(targetMasterTime) else session.anchorPos
        val at = targetMasterTime + nodeOffset
        return SyncCmd(session.fid, pos, at, session.title)
    }
}
