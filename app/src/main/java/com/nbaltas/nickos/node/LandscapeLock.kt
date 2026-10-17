package com.nbaltas.nickos.node

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.nbaltas.nickos.data.Role
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
    /** "on", "off" or "no_permission": what the Master is told when everything worked. */
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

    /**
     * The fixed system rotation that looks landscape. Rotation numbers are turns from the tablet's natural
     * orientation, not "portrait" or "landscape": a tablet that is naturally landscape stays at 0, a naturally
     * portrait one turns 90 degrees (Surface.ROTATION_90). Auto rotate is switched off. This is ONE landscape
     * direction only; both directions need the overlay.
     */
    fun landscapeRotation(naturalLandscape: Boolean) = Rotation(accelerometer = 0, user = if (naturalLandscape) 0 else 1)

    /** Whether the display's natural orientation is landscape, from its current rotation (0 to 3) and size. */
    fun naturalIsLandscape(rotation: Int, width: Int, height: Int): Boolean {
        val turned = rotation == 1 || rotation == 3
        val w = if (turned) height else width
        val h = if (turned) width else height
        return w > h
    }

    const val NEEDS_PERMISSION = "Needs the show over other apps permission"
    const val PENDING_ON = "Not applied yet, trying again"
    const val PENDING_OFF = "Putting the rotation back, trying again"
}

/** What the lock needs from the tablet, so the decisions can be tested with a fake. Writes say whether they worked. */
interface RotationPort {
    fun canOverlay(): Boolean
    fun canWrite(): Boolean
    /** Is the display naturally landscape? See [LandscapePolicy.landscapeRotation]. */
    fun naturalLandscape(): Boolean
    fun read(): Rotation
    /** True only when both settings were written. */
    fun write(r: Rotation): Boolean
    /** Idempotent: shows or hides the invisible sensor landscape window. True when it is now as asked (hiding always is). */
    fun overlay(on: Boolean): Boolean
    /** The remembered system rotation, kept on disk so it survives a restart. */
    fun saved(): Rotation?
    /** Remembers (or with null forgets) the system rotation. True only when it reached the disk. */
    fun save(r: Rotation?): Boolean
}

/** Applies [LandscapePolicy] through a [RotationPort]. Safe to call as often as you like. */
class LandscapeLock(private val port: RotationPort) {
    @Volatile var state: String = NetMessages.STATE_OFF
        private set

    @Synchronized
    fun apply(wanted: Boolean): String {
        var plan = LandscapePolicy.plan(wanted, port.canOverlay(), port.canWrite(), port.saved())
        var ok = run(plan)
        // The window would not appear: fall back to the system rotation when it can be written (one direction only).
        if (!ok && plan.overlay && port.canWrite()) {
            plan = LandscapePolicy.plan(wanted, false, true, port.saved())
            ok = run(plan)
        }
        // A restore that could not finish is not "off": the tablet's own settings are still changed.
        val unfinished = !wanted && runCatching { port.saved() }.getOrNull() != null
        state = if (!ok || unfinished) NetMessages.STATE_PENDING else plan.state
        return state
    }

    /** True when every step of the plan really happened. */
    private fun run(plan: LandscapePlan): Boolean {
        try {
            var ok = port.overlay(plan.overlay)
            // The first copy must be on disk before the tablet's settings change; otherwise leave them alone.
            if (plan.saveFirst && !port.save(port.read())) return false
            if (plan.fixSettings) {
                val target = LandscapePolicy.landscapeRotation(port.naturalLandscape())
                if (port.read() != target && !port.write(target)) ok = false
            }
            if (plan.restore) {
                val s = port.saved()
                // Keep the copy until the settings are really back.
                if (s != null && !(port.write(s) && port.save(null))) ok = false
            }
            return ok
        } catch (e: Exception) {
            Log.w("NickOS.Rotation", "landscape lock", e)
            return false
        }
    }

    fun status(wanted: Boolean) = NetMessages.LandscapeStatus(wanted, state)
}

/** The real tablet. [window] shows or hides the invisible window and says whether it ended up as asked. */
class AndroidRotationPort(private val ctx: Context, private val store: Store, private val window: (Boolean) -> Boolean) : RotationPort {
    override fun canOverlay() = Settings.canDrawOverlays(ctx)
    override fun canWrite() = android.os.Build.VERSION.SDK_INT < 23 || Settings.System.canWrite(ctx)

    override fun naturalLandscape(): Boolean {
        val d = ctx.getSystemService(android.hardware.display.DisplayManager::class.java).getDisplay(android.view.Display.DEFAULT_DISPLAY)
        val m = android.util.DisplayMetrics()
        @Suppress("DEPRECATION") d.getRealMetrics(m)
        return LandscapePolicy.naturalIsLandscape(d.rotation, m.widthPixels, m.heightPixels)
    }

    override fun read() = Rotation(
        Settings.System.getInt(ctx.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1),
        Settings.System.getInt(ctx.contentResolver, Settings.System.USER_ROTATION, 0),
    )

    override fun write(r: Rotation): Boolean {
        val a = Settings.System.putInt(ctx.contentResolver, Settings.System.ACCELEROMETER_ROTATION, r.accelerometer)
        val u = Settings.System.putInt(ctx.contentResolver, Settings.System.USER_ROTATION, r.user)
        return a && u
    }

    override fun overlay(on: Boolean) = window(on)

    override fun saved(): Rotation? = store.savedRotation()
    override fun save(r: Rotation?) = store.setSavedRotation(r)
}

/**
 * A restore that could not finish (permission taken away, a failed write) is kept on disk, also through a reset, and
 * tried again here whenever NickOS starts or comes to the front. Not for a Node that is holding the lock.
 */
object RotationRecovery {
    fun retry(ctx: Context, store: Store): String? {
        if (store.savedRotation() == null) return null
        if (store.role.value == Role.NODE && store.landscapeLock.value) return null
        return try { LandscapeLock(AndroidRotationPort(ctx, store) { true }).apply(false) } catch (e: Exception) { Log.w("NickOS.Rotation", "recovery", e); null }
    }
}
