package com.nbaltas.nickos.sync

import kotlin.math.abs

enum class PlayerState { IDLE, BUFFERING, READY, ENDED }

sealed interface DriftAction {
    data object None : DriftAction
    data class Rate(val speed: Float) : DriftAction
    data class Seek(val targetPos: Long) : DriftAction
}

/**
 * Node-side drift correction, run every 500 ms while a sync session is loaded. Stateful: one
 * instance per session, [reset] on a new fid or on stop. Never corrects from what the Master
 * reports; only from this Node's own measured [DriftAction] against its own anchor.
 */
class DriftPolicy(
    /**
     * Drift beyond this is a seek; below it the speed is nudged. A converted (HLS) stream seeks slowly, so
     * its policy uses [CONVERTED_SEEK_OVER_MS] and leans on rate nudges.
     */
    val seekOverMs: Long = SEEK_OVER_MS,
) {
    private var nudging = false
    private var wasBuffering = false
    private var lastSeekAt = NEVER
    private var pendingSeekMeasure = false

    var seekLeadMs: Long = 200
        private set

    /**
     * [driftMs] = currentPosition - expected(now); positive means ahead. [now] is this Node's
     * own clock, used only to space seeks at least 3 s apart.
     */
    fun decide(driftMs: Long, playerState: PlayerState, playing: Boolean, now: Long, expected: Long): DriftAction {
        if (!playing || playerState == PlayerState.BUFFERING || playerState == PlayerState.IDLE || playerState == PlayerState.ENDED) {
            if (playerState == PlayerState.BUFFERING) wasBuffering = true
            return DriftAction.None
        }

        if (pendingSeekMeasure) {
            pendingSeekMeasure = false
            seekLeadMs = (seekLeadMs - driftMs).coerceIn(0, 1000)
        }

        if (wasBuffering) {
            wasBuffering = false
            // A seek makes the player buffer, so buffering inside the cool-down is our own seek
            // landing, not a stall: measure it, do not seek again (that would loop forever).
            if (now - lastSeekAt < SEEK_COOLDOWN_MS) return DriftAction.None
            nudging = false
            lastSeekAt = now
            pendingSeekMeasure = true
            return DriftAction.Seek(expected + seekLeadMs)
        }

        val magnitude = abs(driftMs)

        if (nudging && magnitude <= 15) {
            nudging = false
            return DriftAction.Rate(1f)
        }

        if (magnitude > seekOverMs) {
            if (now - lastSeekAt < SEEK_COOLDOWN_MS) return DriftAction.None
            lastSeekAt = now
            nudging = false
            pendingSeekMeasure = true
            return DriftAction.Seek(expected + seekLeadMs)
        }

        if (magnitude > 40 || nudging) {
            nudging = true
            return DriftAction.Rate(if (driftMs > 0) 0.96f else 1.04f)
        }

        return DriftAction.None
    }

    /**
     * The player itself just seeked at [now] (a late join or a resume that had to jump). The
     * buffering that follows is that seek landing: measure it, and hold the cool-down.
     */
    fun noteSeek(now: Long) {
        nudging = false
        lastSeekAt = now
        pendingSeekMeasure = true
    }

    /** [keepLead] keeps the learned seek lead: same file, same decoder, only the anchor moved. */
    fun reset(keepLead: Boolean = false) {
        nudging = false
        wasBuffering = false
        lastSeekAt = NEVER
        pendingSeekMeasure = false
        if (!keepLead) seekLeadMs = 200
    }

    companion object {
        /** Beyond this a file or a plain stream seeks instead of nudging its speed. */
        const val SEEK_OVER_MS = 400L
        /** The same for a converted stream. */
        const val CONVERTED_SEEK_OVER_MS = 1500L

        /** The policy for the address a tablet was given. */
        fun forUrl(url: String?) = DriftPolicy(if (JfStream.isHls(url)) CONVERTED_SEEK_OVER_MS else SEEK_OVER_MS)

        /** Far enough in the past that "now - lastSeekAt" never overflows and never cools down. */
        const val NEVER = -1_000_000_000_000L
        const val SEEK_COOLDOWN_MS = 3000L
    }
}
