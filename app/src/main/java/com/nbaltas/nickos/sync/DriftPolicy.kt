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
class DriftPolicy {
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

        if (magnitude > 400) {
            if (now - lastSeekAt < 3000) return DriftAction.None
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

    fun reset() {
        nudging = false
        wasBuffering = false
        lastSeekAt = NEVER
        pendingSeekMeasure = false
        seekLeadMs = 200
    }

    private companion object {
        /** Far enough in the past that "now - lastSeekAt" never overflows and never cools down. */
        const val NEVER = -1_000_000_000_000L
    }
}
