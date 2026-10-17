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
    /** The stream address (token inside) when the tablets stream this movie; null for a file on the tablets. Never log it. */
    val url: String? = null,
    /** An image URL for the parent's player when there is no decoded thumb. Never persisted, never sent to nodes. */
    val art: String? = null,
    /** Per tablet address (token inside) where it differs from [url]: a converted stream for a tablet that cannot decode the file. Never log it. */
    val nodeUrls: Map<String, String> = emptyMap(),
) {
    /** True when the tablets stream this movie from Jellyfin. */
    val streaming: Boolean get() = JfStream.isStreamFid(fid)

    /** The address tablet [id] is sent: its own when it has one, else the shared [url]. */
    fun urlFor(id: String): String? = nodeUrls[id] ?: url

    /** True when any tablet in the session gets a converted stream (a longer lead, as it takes longer to start). */
    val converted: Boolean get() = nodes.any { JfStream.isHls(nodeUrls[it]) }

    override fun toString() = "SyncSession(fid=$fid, nodes=$nodes, playing=$playing, stream=${url != null})"

    /**
     * Where playback should be at master-clock time [now]. Before [anchorAt] (the lead time after
     * Play, a seek or a resume) every tablet is holding at [anchorPos], so that is the answer.
     */
    fun expectedPos(now: Long): Long {
        val pos = if (playing) anchorPos + (now - anchorAt).coerceAtLeast(0) else anchorPos
        val clamped = if (durMs > 0) pos.coerceIn(0, durMs) else pos
        return clamped.coerceAtLeast(0)
    }
}

/** What a Node is told: play [fid] so that at its own clock time [at] it is at position [pos]. */
data class SyncCmd(val fid: String, val pos: Long, val at: Long, val title: String, val url: String? = null) {
    override fun toString() = "SyncCmd(fid=$fid, pos=$pos, at=$at, stream=${url != null})"
}

object SyncPlan {
    /**
     * Builds the command for one Node. [nodeOffset] is that Node's [ClockSync.offset]
     * (nodeClock - masterClock). [at] is already translated into the Node's own clock, which
     * is the one detail a plan commonly gets wrong.
     */
    fun planFor(session: SyncSession, nodeOffset: Long, now: Long, leadMs: Long = 1500, nodeId: String? = null): SyncCmd {
        val targetMasterTime = now + leadMs
        val pos = if (session.playing) session.expectedPos(targetMasterTime) else session.anchorPos
        val at = targetMasterTime + nodeOffset
        return SyncCmd(session.fid, pos, at, session.title, if (nodeId != null) session.urlFor(nodeId) else session.url)
    }
}
