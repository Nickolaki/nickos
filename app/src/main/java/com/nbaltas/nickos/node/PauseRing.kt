package com.nbaltas.nickos.node

import com.nbaltas.nickos.link.NetMessages
import org.json.JSONObject

/**
 * Pause everyone, the pure part. The Master keeps the message while everyone is paused ([pausedMsg] non-null) and sends
 * it again on every connect, so a tablet that was off or out of range gets the wall when it comes back. When nobody is
 * paused it sends the "off" command on connect instead: a tablet that kept its wall while the Master was away loses it.
 */
object PauseAll {
    /** What a Master sends a tablet that has just connected: the wall again, or nothing when nobody is paused. */
    fun intent(pausedMsg: String?): JSONObject? = pausedMsg?.let { NetMessages.pauseAllCmd(true, it) }

    /**
     * A tablet that still shows the wall after the Master resumed everyone (it was off or away at the time) says so in its
     * status. The Master then sends the "off" command, so the wall never stays up with nobody to lift it.
     */
    fun staleWall(nodeSaysPaused: Boolean, pausedMsg: String?): JSONObject? =
        if (nodeSaysPaused && pausedMsg == null) NetMessages.pauseAllCmd(false, "") else null

    /** What the Node shows: the message, or a plain default when the parent left it empty. */
    fun wallText(msg: String?): String = msg?.trim().orEmpty().ifEmpty { "Paused" }

    /** The sheet only lets the parent press the big button once they have chosen or typed something, unless they are resuming. */
    fun canSubmit(paused: Boolean, msg: String) = paused || msg.isNotBlank()
}

/**
 * Ring a lost tablet, the pure part. It decides when to start and stop; the Node does the sound, the volume and the
 * overlay. The alarm volume is saved when ringing starts and handed back exactly once, whichever way it stops.
 */
class RingMachine(private val capMs: Long = CAP_MS) {
    enum class Stop { TAP, PARENT, TIMEOUT, LINK }

    var ringing = false
        private set
    private var startedAt = 0L
    private var savedVolume = 0

    /** True when ringing begins now. Already ringing: nothing changes, and the first saved volume is kept. */
    fun start(now: Long, currentAlarmVolume: Int): Boolean {
        if (ringing) return false
        ringing = true
        startedAt = now
        savedVolume = currentAlarmVolume
        return true
    }

    /** The alarm volume to put back, or null when it was not ringing (a second stop restores nothing). */
    fun stop(@Suppress("UNUSED_PARAMETER") reason: Stop): Int? {
        if (!ringing) return null
        ringing = false
        return savedVolume
    }

    /** Called about once a second: stops after the cap. Returns the volume to restore when it just stopped. */
    fun tick(now: Long): Int? = if (ringing && now - startedAt >= capMs) stop(Stop.TIMEOUT) else null

    companion object { const val CAP_MS = 60_000L }
}
