package com.nbaltas.nickos.power

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import com.nbaltas.nickos.data.Store
import com.nbaltas.nickos.link.NetMessages
import com.nbaltas.nickos.node.AdminReceiver

/** What to do about the power settings right now. Pure: see [PowerScreenTimer] for the part that touches the tablet. */
data class PowerPlan(
    /** Remember the current screen off timeout before the first change. */
    val saveFirst: Boolean,
    /** The idle timeout to set, in milliseconds, or null to leave it alone. */
    val timeoutMs: Int?,
    /** Put the remembered timeout back and forget it. */
    val restore: Boolean,
    /** "on", "off" or "no_permission": what the Master is told. */
    val state: String,
)

object PowerPolicy {
    /** The minutes the parent can pick; 0 is off. */
    val CHOICES = listOf(0, 2, 5, 10, 15, 30)

    /**
     * While not charging and [offMin] > 0 the tablet's own idle timeout is set to [offMin] minutes, so a child in the
     * middle of a tap or a playing video is never cut off. Any other time the remembered timeout is given back.
     * [saved] is the timeout we remembered earlier, if any: it is saved once, before the first change, and given back once.
     */
    fun plan(wake: Boolean, offMin: Int, charging: Boolean, canChange: Boolean, saved: Int?): PowerPlan {
        val timer = offMin > 0 && !charging
        return PowerPlan(
            saveFirst = timer && canChange && saved == null,
            timeoutMs = if (timer && canChange) offMin * 60_000 else null,
            restore = !timer && saved != null && canChange,
            state = when {
                offMin > 0 && !canChange -> NetMessages.STATE_NO_PERMISSION
                wake || offMin > 0 -> NetMessages.STATE_ON
                else -> NetMessages.STATE_OFF
            },
        )
    }

    /** Wake the screen on a plug in only when asked to. */
    fun shouldWake(wake: Boolean, pluggedIn: Boolean) = wake && pluggedIn

    const val NEEDS_PERMISSION = "Needs permission to change screen settings"
    const val NEEDS_PERMISSION_TABLET = "Needs permission on the tablet"
}

/** What the timer needs from the tablet, so the decisions can be tested with a fake. Writes say whether they worked. */
interface ScreenTimeoutPort {
    fun canChange(): Boolean
    fun read(): Int
    /** True only when the timeout was written. */
    fun write(ms: Int): Boolean
    /** The remembered timeout, kept on disk so it survives a restart. */
    fun saved(): Int?
    /** Remembers (or with null forgets) the timeout. True only when it reached the disk. */
    fun save(ms: Int?): Boolean
}

/** Applies [PowerPolicy] through a [ScreenTimeoutPort]. Safe to call as often as you like. */
class PowerScreenTimer(private val port: ScreenTimeoutPort) {
    @Volatile var state: String = NetMessages.STATE_OFF
        private set

    @Synchronized
    fun apply(wake: Boolean, offMin: Int, charging: Boolean): String {
        val plan = PowerPolicy.plan(wake, offMin, charging, port.canChange(), port.saved())
        try {
            // The first copy must be on disk before the tablet's setting changes; otherwise leave it alone.
            if (plan.saveFirst && !port.save(port.read())) return plan.state.also { state = it }
            if (plan.timeoutMs != null && port.read() != plan.timeoutMs) port.write(plan.timeoutMs)
            if (plan.restore) {
                val s = port.saved()
                // Keep the copy until the setting is really back.
                if (s != null && port.write(s)) port.save(null)
            }
        } catch (e: Exception) {
            Log.w("NickOS.Power", "screen timeout", e)
        }
        state = plan.state
        return state
    }

    fun status(wake: Boolean, offMin: Int) = NetMessages.PowerStatus(wake, offMin, state)
}

/** The real tablet. A Device Owner may change the timeout without the write settings permission. */
class AndroidScreenTimeoutPort(private val ctx: Context, private val store: Store) : ScreenTimeoutPort {
    private val dpm = ctx.getSystemService(DevicePolicyManager::class.java)
    private val admin = ComponentName(ctx, AdminReceiver::class.java)
    private val owner get() = android.os.Build.VERSION.SDK_INT >= 28 && dpm.isDeviceOwnerApp(ctx.packageName)
    private val writeOk get() = android.os.Build.VERSION.SDK_INT < 23 || Settings.System.canWrite(ctx)

    override fun canChange() = writeOk || owner

    override fun read() = Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 60_000)

    override fun write(ms: Int): Boolean {
        if (writeOk) return Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, ms)
        return try {
            dpm.setSystemSetting(admin, Settings.System.SCREEN_OFF_TIMEOUT, ms.toString())
            read() == ms
        } catch (e: Exception) { Log.w("NickOS.Power", "setSystemSetting", e); false }
    }

    override fun saved(): Int? = store.savedScreenTimeout()
    override fun save(ms: Int?) = store.setSavedScreenTimeout(ms)
}

/** Wakes the screen. Shared by the Node's Master command and the wake on charge setting. */
object ScreenWake {
    @Suppress("DEPRECATION")
    fun wake(ctx: Context) {
        val power = ctx.getSystemService(PowerManager::class.java)
        val wl = power.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
            "nickos:wake",
        )
        wl.acquire(3000)
    }
}

/**
 * Runs the power settings for one role: a dynamic receiver for plug in and unplug, started and stopped with the role's
 * runtime. [release] gives the tablet's own screen timeout back.
 */
class PowerRunner(private val ctx: Context, private val store: Store) {
    private val timer = PowerScreenTimer(AndroidScreenTimeoutPort(ctx, store))
    private var receiver: BroadcastReceiver? = null

    val state: String get() = timer.state

    fun charging(): Boolean {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        return plugged != 0 || status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }

    @Synchronized
    fun start() {
        if (receiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val connected = intent.action == Intent.ACTION_POWER_CONNECTED
                if (PowerPolicy.shouldWake(store.powerWakeOnCharge.value, connected)) {
                    try { ScreenWake.wake(ctx) } catch (e: Exception) { Log.w("NickOS.Power", "wake", e) }
                }
                timer.apply(store.powerWakeOnCharge.value, store.powerBatteryOffMin.value, connected)
            }
        }
        val f = IntentFilter().apply { addAction(Intent.ACTION_POWER_CONNECTED); addAction(Intent.ACTION_POWER_DISCONNECTED) }
        if (android.os.Build.VERSION.SDK_INT >= 33) ctx.registerReceiver(r, f, Context.RECEIVER_EXPORTED) else ctx.registerReceiver(r, f)
        receiver = r
        refresh()
    }

    /** Apply the stored settings to the way the tablet is right now. Call after they change. */
    fun refresh(): String = timer.apply(store.powerWakeOnCharge.value, store.powerBatteryOffMin.value, charging())

    @Synchronized
    fun stop() {
        receiver?.let { try { ctx.unregisterReceiver(it) } catch (e: Exception) { Log.w("NickOS.Power", "unregister", e) } }
        receiver = null
    }

    /** Turns the settings off and gives the tablet's own screen timeout back (unlink, reset, a Master that stops using it). */
    fun release() {
        store.setPower(false, 0)
        timer.apply(false, 0, true)
    }

    fun status() = timer.status(store.powerWakeOnCharge.value, store.powerBatteryOffMin.value)
}

/** A restore that could not finish is kept on disk, also through a reset, and tried again here when NickOS starts. */
object PowerRecovery {
    fun retry(ctx: Context, store: Store) {
        if (store.savedScreenTimeout() == null) return
        if (store.powerBatteryOffMin.value > 0) return
        try { PowerScreenTimer(AndroidScreenTimeoutPort(ctx, store)).apply(false, 0, true) } catch (e: Exception) { Log.w("NickOS.Power", "recovery", e) }
    }
}
