package com.nbaltas.nickos.master

import android.os.Build
import android.util.Log
import com.nbaltas.nickos.data.Hotspot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The Master hosts the car network itself: a Wi-Fi Direct group with a fixed name and passphrase, so the Nodes can
 * save it once and join it on their own. This file is the whole decision logic; every Android call sits behind
 * [P2pPort], so the races (the service stopping mid-group, a group that fails, WiFi toggling) run as plain tests.
 *
 * The passphrase is never logged and never appears in a [CarNetState].
 */
enum class Why { NO_PERMISSION, NO_LOCATION, WIFI_OFF, TETHERING_ON, MANUAL, UNSUPPORTED, FAILED }

sealed class CarNetState {
    /** Making the group. */
    object Starting : CarNetState() { override fun toString() = "Starting" }
    /** The group is up: [ssid] is its name, [ownIp] this Master's address on it (read from the system, not assumed). */
    data class On(val ssid: String, val ownIp: String?, val pass: String? = null) : CarNetState() {
        /** [pass] is only set on Android 7 to 9, where the system picks the credentials; it must stay out of every log line. */
        override fun toString() = "On($ssid, $ownIp)"
    }
    data class Off(val why: Why) : CarNetState()
}

/** A Wi-Fi Direct group as the system reports it. */
data class Group(val name: String, val passphrase: String?, val ownerIp: String?, val isOwner: Boolean, val clientCount: Int = 0)

enum class Outcome { OK, BUSY, ERROR, UNSUPPORTED }

/**
 * Every Wi-Fi Direct call, as an interface. Real: [AndroidP2pPort]. Each suspend function must give up and return
 * (null or ERROR or false) rather than wait for ever on a listener that never fires; [CarNet] also guards each one.
 */
interface P2pPort {
    /** Set by [CarNet]: call it whenever something the decision depends on may have changed. Any thread. */
    var onEvent: (() -> Unit)?
    /** Start and stop listening for system broadcasts. [close] is called after the group is removed. */
    fun open() {}
    fun close() {}

    fun supported(): Boolean
    fun hasPermission(): Boolean
    fun locationOn(): Boolean
    fun wifiOn(): Boolean
    fun tetheringOn(): Boolean
    suspend fun groupInfo(): Group?
    suspend fun createGroup(ssid: String, pass: String): Outcome
    /** True when no group is left. */
    suspend fun removeGroup(): Boolean
}

/** The choice of the next step, from facts only: no clock, no Android, no side effects. */
object CarNetPolicy {
    data class Timing(
        /** After a failed or odd attempt, wait this long before the next one. */
        val retryMs: Long = 3_000,
        /** Three attempts in a row without a working group is FAILED; it is tried again this often. */
        val failedRetryMs: Long = 60_000,
        val maxFailures: Int = 3,
        /** After a create the system said OK to, give its group this long to show up before creating again. */
        val settleMs: Long = 5_000,
    )

    data class Inputs(
        val sdk: Int,
        val permission: Boolean,
        val locationOn: Boolean,
        val wifiOn: Boolean,
        val tetheringOn: Boolean,
        val manual: Boolean,
        val live: Group?,
        val wanted: Hotspot,
        val failures: Int,
        val lastTryAt: Long,
        val now: Long,
        /** False when the device has no Wi-Fi Direct or the framework said P2P_UNSUPPORTED. */
        val supported: Boolean = true,
        /** When the system last answered OK to a create, 0 for never. */
        val createdOkAt: Long = 0,
        val timing: Timing = Timing(),
    )

    sealed class Action {
        data class Nothing(val state: CarNetState) : Action()
        object Create : Action() { override fun toString() = "Create" }
        object RemoveThenCreate : Action() { override fun toString() = "RemoveThenCreate" }
        data class Remove(val state: CarNetState) : Action()
        data class Wait(val untilMs: Long, val state: CarNetState) : Action()
    }

    fun next(i: Inputs): Action {
        // 1. UNSUPPORTED (below Android 7 there is no usable Wi-Fi Direct group owner; the manual hotspot is the only way)
        if (!i.supported || i.sdk < 24) return Action.Nothing(CarNetState.Off(Why.UNSUPPORTED))
        // LEGACY (24..28): no WifiP2pConfig.Builder, so the system picks the name and passphrase. We learn them from
        // the live group (which we must own) instead of prescribing them, and need no permission or Location toggle.
        val legacy = i.sdk in 24..28
        val ours = i.live?.takeIf { if (legacy) it.isOwner else it.name == i.wanted.ssid }
        fun pause(why: Why) = if (ours != null) Action.Remove(CarNetState.Off(why)) else Action.Nothing(CarNetState.Off(why))
        // 2. MANUAL: the parent typed a hotspot in; our group would only get in its way
        if (i.manual) return pause(Why.MANUAL)
        // 3. NO_PERMISSION, 4. NO_LOCATION (29..32 only: 33+ has NEARBY_WIFI_DEVICES and needs no location)
        if (!legacy && !i.permission) return Action.Nothing(CarNetState.Off(Why.NO_PERMISSION))
        if (!legacy && i.sdk in 29..32 && !i.locationOn) return Action.Nothing(CarNetState.Off(Why.NO_LOCATION))
        // 5. WIFI_OFF: the system took the group with it, and the Master cannot turn WiFi on
        if (!i.wifiOn) return Action.Nothing(CarNetState.Off(Why.WIFI_OFF))
        // 6. TETHERING_ON: a chip that cannot be an access point and a group owner at once. A pause, never a retry.
        if (i.tetheringOn) return pause(Why.TETHERING_ON)

        val live = i.live
        // Never adopt (or push to the Nodes) a group without a real WPA2 passphrase (8 to 63 characters): remove it.
        if (legacy && ours != null && ours.passphrase != null && ours.passphrase.length in 8..63) {
            return Action.Nothing(CarNetState.On(ours.name, ours.ownerIp, ours.passphrase))
        }
        // A group left over from a crash is normal: adopt it. It must be ours, we must own it, and (when the system
        // says) the passphrase must be the one the Nodes saved: an old persistent group with our name and an old
        // passphrase is exactly what a Node cannot join.
        if (!legacy && live != null && live.name == i.wanted.ssid && live.isOwner && (live.passphrase == null || live.passphrase == i.wanted.pass)) {
            return Action.Nothing(CarNetState.On(live.name, live.ownerIp))
        }
        val make = if (live != null) Action.RemoveThenCreate else Action.Create
        val t = i.timing
        // The system said OK but its group is not visible yet (the broadcast can beat the query): do not create twice.
        if (live == null && i.createdOkAt > 0 && i.now - i.createdOkAt < t.settleMs) {
            return Action.Wait(i.createdOkAt + t.settleMs, CarNetState.Starting)
        }
        if (i.failures >= t.maxFailures) {
            val due = i.lastTryAt + t.failedRetryMs
            return if (i.now >= due) make else Action.Wait(due, CarNetState.Off(Why.FAILED))
        }
        if (i.failures > 0 && i.now - i.lastTryAt < t.retryMs) return Action.Wait(i.lastTryAt + t.retryMs, CarNetState.Starting)
        return make
    }
}

/** The names the Nodes save. The passphrase lives in the Store ([com.nbaltas.nickos.data.Store.carNetPass]). */
object CarNetNames {
    /** "DIRECT-" plus two letters or digits is what setNetworkName demands; the device id is stable, a display name is not. */
    fun ssid(deviceId: String): String = "DIRECT-NK-NickOS-" + deviceId.take(4)
}

/**
 * Keeps the car network alive for as long as it is [start]ed. One loop reads the facts, asks [CarNetPolicy], does the
 * one thing it says, then waits for an event ([poke], a system broadcast) or the watchdog, whichever is first.
 *
 *  - [wanted]: the name and passphrase to host. [manual]: the parent typed a hotspot in, so stay off.
 *  - [onUp]: called when the group is up, so the credentials reach the Store before the first Node pairs.
 */
class CarNet(
    private val port: P2pPort,
    private val scope: CoroutineScope,
    private val wanted: () -> Hotspot,
    private val manual: () -> Boolean,
    private val onUp: (Hotspot) -> Unit = {},
    private val sdk: Int = Build.VERSION.SDK_INT,
    private val clock: () -> Long = System::currentTimeMillis,
    private val config: Config = Config(),
) {
    data class Config(
        /** No Wi-Fi Direct call may take longer than this: a listener that never fires must not hang the loop. */
        val callTimeoutMs: Long = 10_000,
        /** Re-check at least this often: the framework quietly drops a group sometimes, with no broadcast. */
        val watchdogMs: Long = 60_000,
        val timing: CarNetPolicy.Timing = CarNetPolicy.Timing(),
    )

    private val _state = MutableStateFlow<CarNetState>(CarNetState.Starting)
    val state: StateFlow<CarNetState> = _state

    /** When [state] last became Starting, for the "Starting longer than 20 s counts as failed" rule. */
    @Volatile var startingSince: Long = clock()
        private set

    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var loop: Job? = null
    private var stopping: Job? = null

    // Only the loop touches these.
    private var failures = 0
    private var lastTryAt = 0L
    private var createdOkAt = 0L
    private var forceRemove = false
    @Volatile private var unsupportedSeen = false
    @Volatile private var recheckPending = false

    /** Something may have changed (a broadcast, the hotspot setting, a permission): look again now. Any thread. */
    fun poke() { wake.trySend(Unit) }

    /** The parent answered the permission prompt, or pressed try again: forget past failures and look now. */
    fun recheck() { recheckPending = true; poke() }

    /** The permissions to ask for in one prompt on this Android version, empty when held or not applicable. */
    fun permissionToRequest(): List<String> {
        if (sdk < 29 || unsupportedSeen || !safe(true) { port.supported() } || safe(false) { port.hasPermission() }) return emptyList()
        return permissionsFor(sdk)
    }

    /** Idempotent. A start right after a [stop] waits for that stop's group removal to finish first. */
    @Synchronized
    fun start() {
        if (loop?.isActive == true) return
        val prior = stopping
        loop = scope.launch {
            prior?.join()
            port.onEvent = { poke() }
            port.open()
            run()
        }
    }

    /**
     * Removes the group and goes quiet. The removal runs apart from the loop (which is cancelled, perhaps in the
     * middle of a create whose result the system will still deliver) and after it has finished.
     */
    @Synchronized
    fun stop() {
        val l = loop ?: return
        loop = null
        l.cancel()
        port.onEvent = null
        val ssid = wanted().ssid
        stopping = scope.launch {
            withContext(NonCancellable) {
                l.join()
                val g = withTimeoutOrNull(config.callTimeoutMs) { try { port.groupInfo() } catch (e: Exception) { null } }
                // A query that failed or timed out (null) is not proof there is no group: remove best-effort then.
                // Only a group that is positively someone else's is left alone.
                // Legacy (24..28): the system named the group, so ours is the one we own, not the one named [ssid].
                val legacy = sdk in 24..28
                if (g == null || (if (legacy) g.isOwner else g.name == ssid)) withTimeoutOrNull(config.callTimeoutMs) { try { port.removeGroup() } catch (e: Exception) { false } }
                try { port.close() } catch (_: Exception) {}
            }
        }
        publish(CarNetState.Starting)
    }

    private suspend fun run() {
        failures = 0; lastTryAt = 0; createdOkAt = 0; forceRemove = false
        publish(CarNetState.Starting)
        while (currentCoroutineContext().isActive) {
            val waitMs = cycle()
            if (waitMs > 0) withTimeoutOrNull(waitMs) { wake.receive() }
        }
    }

    private fun publish(s: CarNetState) {
        if (_state.value == s) return
        if (s is CarNetState.Starting) startingSince = clock()
        _state.value = s
    }

    private fun <T> safe(default: T, f: () -> T): T = try { f() } catch (e: Exception) { default }

    /** One look and one step. Returns how long to wait before the next (0: look again at once). */
    private suspend fun cycle(): Long {
        if (recheckPending) { recheckPending = false; failures = 0; createdOkAt = 0; forceRemove = false }
        val now = clock()
        val want = wanted()
        val supported = !unsupportedSeen && safe(false) { port.supported() }
        val permission = safe(false) { port.hasPermission() }
        val wifiOn = safe(false) { port.wifiOn() }
        val legacy = sdk in 24..28
        val live = if (supported && wifiOn && (legacy || (sdk >= 29 && permission))) {
            withTimeoutOrNull(config.callTimeoutMs) { try { port.groupInfo() } catch (e: Exception) { null } }
        } else null
        val action = CarNetPolicy.next(CarNetPolicy.Inputs(
            sdk = sdk, permission = permission, locationOn = safe(true) { port.locationOn() }, wifiOn = wifiOn,
            tetheringOn = safe(false) { port.tetheringOn() }, manual = manual(), live = live, wanted = want,
            failures = failures, lastTryAt = lastTryAt, now = now, supported = supported, createdOkAt = createdOkAt,
            timing = config.timing,
        ))
        log("step $action")
        return when (action) {
            is CarNetPolicy.Action.Nothing -> {
                settle(action.state, want)
                config.watchdogMs
            }
            is CarNetPolicy.Action.Wait -> {
                publish(action.state)
                (action.untilMs - now).coerceIn(1, config.watchdogMs)
            }
            is CarNetPolicy.Action.Remove -> {
                publish(action.state)
                resetCounters()
                val gone = withTimeoutOrNull(config.callTimeoutMs) { try { port.removeGroup() } catch (e: Exception) { false } } == true
                if (gone) config.watchdogMs else config.timing.retryMs
            }
            CarNetPolicy.Action.Create -> { create(want, now, removeFirst = false); 0 }
            CarNetPolicy.Action.RemoveThenCreate -> { create(want, now, removeFirst = true); 0 }
        }
    }

    /** The state the policy settled on. Anything but a failure to make the group clears the failure count. */
    private fun settle(s: CarNetState, want: Hotspot) {
        when {
            s is CarNetState.On -> {
                val changed = _state.value != s
                failures = 0; createdOkAt = 0; forceRemove = false
                publish(s)
                // Legacy: the credentials are the live group's, not the wanted ones.
                val up = if (s.pass != null) Hotspot(s.ssid, s.pass, direct = true) else want
                if (changed) try { onUp(up) } catch (e: Exception) { log("onUp failed: ${e.javaClass.simpleName}") }
            }
            s is CarNetState.Off && s.why == Why.FAILED -> publish(s)
            else -> { resetCounters(); publish(s) }
        }
    }

    private fun resetCounters() { failures = 0; createdOkAt = 0; forceRemove = false }

    private suspend fun create(want: Hotspot, now: Long, removeFirst: Boolean) {
        failures++
        lastTryAt = now
        val cur = _state.value
        if (!(cur is CarNetState.Off && cur.why == Why.FAILED)) publish(CarNetState.Starting)
        if (removeFirst || forceRemove) {
            forceRemove = false
            withTimeoutOrNull(config.callTimeoutMs) { try { port.removeGroup() } catch (e: Exception) { false } }
        }
        val out = withTimeoutOrNull(config.callTimeoutMs) {
            try { port.createGroup(want.ssid, want.pass) } catch (e: SecurityException) { Outcome.ERROR } catch (e: Exception) { Outcome.ERROR }
        } ?: Outcome.ERROR
        log("create answered $out")
        when (out) {
            Outcome.OK -> createdOkAt = clock()
            // Something already holds the radio: clear it before the next try rather than hammering createGroup.
            Outcome.BUSY -> forceRemove = true
            Outcome.UNSUPPORTED -> unsupportedSeen = true
            Outcome.ERROR -> {}
        }
    }

    private fun log(msg: String) { try { Log.d(TAG, msg) } catch (_: Throwable) {} }

    companion object {
        const val TAG = "NickOS.CarNet"
        const val NEARBY_WIFI_DEVICES = "android.permission.NEARBY_WIFI_DEVICES"
        const val ACCESS_FINE_LOCATION = "android.permission.ACCESS_FINE_LOCATION"
        const val ACCESS_COARSE_LOCATION = "android.permission.ACCESS_COARSE_LOCATION"

        /** What to ask for on [sdk]. Android 12 ignores a fine-location request unless coarse is asked in the same prompt. */
        fun permissionsFor(sdk: Int): List<String> = when {
            sdk >= 33 -> listOf(NEARBY_WIFI_DEVICES)
            sdk >= 31 -> listOf(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION)
            else -> listOf(ACCESS_FINE_LOCATION)
        }
    }
}
