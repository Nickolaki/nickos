package com.nbaltas.nickos.node

import com.nbaltas.nickos.link.NetMessages
import org.json.JSONObject

/**
 * Pause everyone, the pure part. The Master keeps the message while it is the one that paused everyone ([pausedMsg]
 * non-null) and sends it again on every connect, so a tablet that was off or out of range gets the wall when it comes
 * back. A Master never sends the "off" command by itself: only the parent's Resume button does.
 */
object PauseAll {
    /** What a Master sends a tablet that has just connected: the wall again, or nothing when this Master did not pause. */
    fun intent(pausedMsg: String?): JSONObject? = pausedMsg?.let { NetMessages.pauseAllCmd(true, it) }

    /**
     * Whether an "off" command lifts the wall. The Master that paused may; so may a forced one (the parent tapped
     * "Resume everyone"). Another Master's plain "off" is ignored. A wall with no owner (saved by an older version) is open to all.
     */
    fun mayClear(owner: String?, sender: String?, force: Boolean): Boolean = force || owner == null || owner == sender

    /** What the Node shows: the message, or a plain default when the parent left it empty. */
    fun wallText(msg: String?): String = msg?.trim().orEmpty().ifEmpty { "Paused" }

    /** The sheet only lets the parent press the big button once they have chosen or typed something, unless they are resuming. */
    fun canSubmit(paused: Boolean, msg: String) = paused || msg.isNotBlank()
}

/**
 * Ring a lost tablet, the pure part. It decides when to start and stop; the Node does the sound, the volume and the
 * overlay. The alarm volume is saved when ringing starts and handed back exactly once, whichever way it stops.
 * [now] must be a monotonic clock (SystemClock.elapsedRealtime), so a clock correction cannot stretch the cap.
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

/** What [RingController] does to the tablet. The Node's real one is in NodeRuntime; tests use a fake. */
interface RingPort {
    fun alarmVolume(): Int
    fun setAlarmVolume(v: Int)
    /** Written to disk before the volume is raised, so a crash cannot lose it. */
    fun saveVolumeBackup(v: Int)
    fun clearVolumeBackup()
    fun wake()
    /** Raises the alarm volume and starts the looping sound. */
    fun startAlarm()
    fun stopAlarm()
    /** The overlay and the status follow this. */
    fun shown(ringing: Boolean)
}

/**
 * Start, stop and timeout of the alarm under one lock, device effects included, so a stop and a new start on another
 * session can never interleave: the new ring always reads the volume the old one has already put back.
 */
class RingController(private val port: RingPort, private val clock: () -> Long, capMs: Long = RingMachine.CAP_MS) {
    private val lock = Any()
    private val machine = RingMachine(capMs)
    private var gen = 0L

    val ringing: Boolean get() = synchronized(lock) { machine.ringing }

    /** The id of the ring that began, or 0 when it was already ringing. */
    fun start(): Long = synchronized(lock) {
        val vol = port.alarmVolume()
        if (!machine.start(clock(), vol)) return 0L
        gen++
        port.saveVolumeBackup(vol)
        port.wake()
        port.startAlarm()
        port.shown(true)
        gen
    }

    /** True when this call stopped a ringing alarm. Safe when not ringing. */
    fun stop(reason: RingMachine.Stop): Boolean = synchronized(lock) {
        val restore = machine.stop(reason) ?: return false
        finish(restore)
        true
    }

    /** About once a second; true when the cap just stopped the alarm. */
    fun tick(): Boolean = synchronized(lock) {
        val restore = machine.tick(clock()) ?: return false
        finish(restore)
        true
    }

    /** True while the ring with this id is the one sounding. */
    fun isCurrent(id: Long): Boolean = synchronized(lock) { machine.ringing && gen == id }

    private fun finish(restore: Int) {
        port.stopAlarm()
        port.setAlarmVolume(restore)
        port.clearVolumeBackup()
        port.shown(false)
    }
}

/** At app start: a volume still waiting to be put back (the process died while ringing) goes back, then the copy is cleared. */
object AlarmRecovery {
    fun recover(backup: Int?, setVolume: (Int) -> Unit, clear: () -> Unit): Boolean {
        if (backup == null) return false
        setVolume(backup)
        clear()
        return true
    }
}
