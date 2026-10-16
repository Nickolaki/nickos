package com.nbaltas.nickos.node

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.nbaltas.nickos.data.Store
import com.nbaltas.nickos.link.NetMessages

/** The two rotation settings Android keeps for the whole tablet. */
data class Rotation(val accelerometer: Int, val user: Int)

/** What to do about the landscape lock right now. Pure: see [LandscapeLock] for the part that touches the tablet. */
data class LandscapePlan(
    /** Show the invisible window that asks for sensor landscape (needs the overlay permission). */
    val overlay: Boolean,
    /** Fix the system rotation to landscape instead (needs write settings and no overlay). */
    val fixSettings: Boolean,
    /** Remember the current system rotation before the first change. */
    val saveFirst: Boolean,
    /** Put the remembered system rotation back and forget it. */
    val restore: Boolean,
    /** "on", "off" or "no_permission": what the Master is told. */
    val state: String,
)

object LandscapePolicy {
    /**
     * The overlay is preferred because it keeps BOTH landscape directions. Fixing the system rotation allows one
     * direction only, so it is the fallback for a tablet without the overlay permission. [saved] is the rotation we
     * remembered earlier, if any: it is saved once, before the first change, and given back once.
     */
    fun plan(wanted: Boolean, canOverlay: Boolean, canWrite: Boolean, saved: Rotation?): LandscapePlan {
        val overlay = wanted && canOverlay
        val fix = wanted && !canOverlay && canWrite
        return LandscapePlan(
            overlay = overlay,
            fixSettings = fix,
            saveFirst = fix && saved == null,
            restore = !fix && saved != null && canWrite,
            state = when {
                !wanted -> NetMessages.STATE_OFF
                overlay || fix -> NetMessages.STATE_ON
                else -> NetMessages.STATE_NO_PERMISSION
            },
        )
    }

    /** The system rotation that means landscape: auto rotate off, turned 90 degrees (Surface.ROTATION_90). */
    val LANDSCAPE = Rotation(accelerometer = 0, user = 1)

    const val NEEDS_PERMISSION = "Needs the show over other apps permission"
}

/** What the lock needs from the tablet, so the decisions can be tested with a fake. */
interface RotationPort {
    fun canOverlay(): Boolean
    fun canWrite(): Boolean
    fun read(): Rotation
    fun write(r: Rotation)
    /** Idempotent: shows or hides the invisible sensor landscape window. */
    fun overlay(on: Boolean)
    /** The remembered system rotation, kept on disk so it survives a restart. */
    var saved: Rotation?
}

/** Applies [LandscapePolicy] through a [RotationPort]. Safe to call as often as you like. */
class LandscapeLock(private val port: RotationPort) {
    @Volatile var state: String = NetMessages.STATE_OFF
        private set

    @Synchronized
    fun apply(wanted: Boolean): String {
        val plan = LandscapePolicy.plan(wanted, port.canOverlay(), port.canWrite(), port.saved)
        try {
            port.overlay(plan.overlay)
            if (plan.saveFirst) port.saved = port.read()
            if (plan.fixSettings && port.read() != LandscapePolicy.LANDSCAPE) port.write(LandscapePolicy.LANDSCAPE)
            if (plan.restore) port.saved?.let { port.write(it); port.saved = null }
        } catch (e: Exception) {
            Log.w("NickOS.Rotation", "landscape lock", e)
        }
        state = plan.state
        return plan.state
    }

    fun status(wanted: Boolean) = NetMessages.LandscapeStatus(wanted, state)
}

/** The real tablet. */
class AndroidRotationPort(private val ctx: Context, private val store: Store, private val window: (Boolean) -> Unit) : RotationPort {
    override fun canOverlay() = Settings.canDrawOverlays(ctx)
    override fun canWrite() = android.os.Build.VERSION.SDK_INT < 23 || Settings.System.canWrite(ctx)

    override fun read() = Rotation(
        Settings.System.getInt(ctx.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1),
        Settings.System.getInt(ctx.contentResolver, Settings.System.USER_ROTATION, 0),
    )

    override fun write(r: Rotation) {
        Settings.System.putInt(ctx.contentResolver, Settings.System.ACCELEROMETER_ROTATION, r.accelerometer)
        Settings.System.putInt(ctx.contentResolver, Settings.System.USER_ROTATION, r.user)
    }

    override fun overlay(on: Boolean) = window(on)

    override var saved: Rotation?
        get() = store.savedRotation()
        set(v) = store.setSavedRotation(v)
}
