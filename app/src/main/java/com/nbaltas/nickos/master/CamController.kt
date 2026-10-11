package com.nbaltas.nickos.master

import android.os.SystemClock
import com.nbaltas.nickos.cam.CamMessages
import com.nbaltas.nickos.cam.CamPlan
import com.nbaltas.nickos.cam.CamText
import com.nbaltas.nickos.cam.NodeCam
import com.nbaltas.nickos.data.LinkedNode
import com.nbaltas.nickos.data.Store
import com.nbaltas.nickos.node.parentName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * The Master's side of tablet cameras: which tablets the parent has switched on, what each
 * tablet last said about its camera, and one stream per tablet that is on.
 *
 * A tablet camera runs only while the Cameras screen is open ([viewing]) and the parent's toggle
 * for it is on ([armed]). Once a second [tick] asks [CamPlan.want] what to do about each linked
 * tablet and does it: ask for the camera, open the stream, drop it, or turn the camera off.
 * Leaving the screen therefore turns every tablet camera off within a second; the tablet would
 * also do it by itself a few seconds after the stream closes.
 *
 * Screens read the StateFlows and call the plain functions; all of them are safe on the main thread.
 */
class CamController(
    private val port: MasterPort,
    private val store: Store,
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
    /** Opens a stream. Replaced in tests, where there is nothing to connect to. */
    private val open: (url: String, onFrame: (MjpegClient.Frame) -> Unit, onEnded: (String) -> Unit) -> CamStream =
        { url, onFrame, onEnded -> MjpegClient(url, onFrame, onEnded).start() },
    /** Runs a tick soon. Tests run it at once instead. */
    private val soon: ((() -> Unit) -> Unit)? = null,
    /**
     * The address a tablet's live link is connected to right now, or null. The stream key is only
     * ever sent there: to the peer that proved itself with its token, not to a remembered address.
     */
    private val linkHost: (id: String) -> String? = { null },
) {
    constructor(rt: MasterRuntime, store: Store) : this(RuntimePort(rt, store), store, linkHost = { rt.linkHost(it) })

    private class Client {
        var stream: CamStream? = null
        /** A picture has arrived on this stream. */
        @Volatile var got = false
    }

    /** How the last stream to a tablet ended, and how many in a row ended without a picture. */
    private class End(val kind: String, val at: Long, val retries: Int)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private var ticker: Job? = null

    private val clients = ConcurrentHashMap<String, Client>()
    private val ended = HashMap<String, End>()
    /** Tablets whose camera this Master asked for or watched: its own to turn off. */
    private val mine = HashSet<String>()
    private val lastStart = HashMap<String, Long>()

    private val _armed = MutableStateFlow(store.camArmed)
    /** The parent's toggles: tablets whose camera should be on while the Cameras screen is open. Remembered. */
    val armed: StateFlow<Set<String>> = _armed

    private val _states = MutableStateFlow<Map<String, NodeCam>>(emptyMap())
    /** Each connected tablet's last `camState`. */
    val states: StateFlow<Map<String, NodeCam>> = _states

    private val _frames = ConcurrentHashMap<String, MutableStateFlow<MjpegClient.Frame?>>()
    /** The newest picture from a tablet's current stream. Null while there is no stream or no picture yet. */
    fun frames(id: String): StateFlow<MjpegClient.Frame?> = frameFlow(id)
    internal fun frameFlow(id: String): MutableStateFlow<MjpegClient.Frame?> = _frames.getOrPut(id) { MutableStateFlow(null) }

    /** True while the Cameras screen is open. */
    @Volatile var viewing = false
        private set

    fun startTicker() {
        synchronized(lock) {
            if (ticker?.isActive == true) return
            ticker = scope.launch {
                while (isActive) {
                    delay(TICK_MS)
                    if (viewing || _armed.value.isNotEmpty() || clients.isNotEmpty()) tick()
                }
            }
        }
    }

    /** The Cameras screen opened or closed. */
    fun setViewing(on: Boolean) {
        synchronized(lock) {
            viewing = on
            // Back on the screen: a camera that turned itself off last time has been seen; start clean.
            if (on) _states.update { all -> all.filterNot { (_, cam) -> CamPlan.lingers(cam) && !CamPlan.coolingDown(cam, clock()) } }
        }
        kick()
    }

    /** The parent tapped a tablet's chip. */
    fun toggle(id: String) {
        if (id in _armed.value) disarm(id) else arm(id)
    }

    /** Switches a tablet's camera on. It starts as soon as the Cameras screen is open and the tablet is ready. */
    fun arm(id: String) {
        synchronized(lock) {
            // The parent is asking afresh: forget why it was off, unless it is still cooling down.
            val cam = _states.value[id]
            if (cam != null && !cam.on && !CamPlan.coolingDown(cam, clock())) _states.update { it - id }
            ended.remove(id)
            lastStart.remove(id)
            setArmed(_armed.value + id)
        }
        kick()
    }

    fun disarm(id: String) {
        synchronized(lock) { setArmed(_armed.value - id) }
        kick()
    }

    private fun setArmed(v: Set<String>) {
        if (v == _armed.value) return
        _armed.value = v
        store.setCamArmed(v)
    }

    /** A `camState` line from a tablet. */
    fun onMessage(id: String, m: JSONObject) {
        val cam = CamMessages.decodeState(m, clock()) ?: return
        var toast: String? = null
        synchronized(lock) {
            val prev = _states.value[id]
            _states.update { it + (id to cam) }
            if (!cam.on) {
                mine -= id
                ended.remove(id)
                closeClient(id)
                // The tablet turned its own camera off: the toggle flips off with it, and the parent is told why.
                if (cam.reason in NodeCam.SELF_OFF && id in _armed.value) {
                    setArmed(_armed.value - id)
                    val kid = port.nodes().firstOrNull { it.id == id }?.name?.let { n -> parentName(n).ifEmpty { n } } ?: "A tablet"
                    toast = CamText.toast(cam.reason, kid, cam.minutes ?: store.camPrefs.value.maxMin)
                }
            } else if (prev == null || prev.key != cam.key || prev.port != cam.port) {
                // A new run has a new key: whatever was open or failed belongs to the old one.
                ended.remove(id)
                closeClient(id)
            }
        }
        toast?.let { port.say(it) }
        kick()
    }

    /** The link to a tablet ended. Its camera turns itself off; there is nothing to send. */
    fun onDisconnected(id: String) {
        synchronized(lock) {
            closeClient(id)
            _states.update { it - id }
            mine -= id
            ended.remove(id)
            lastStart.remove(id)
        }
    }

    private fun kick() {
        val run = soon
        if (run != null) run(::tick) else scope.launch { tick() }
    }

    /** One pass over every linked tablet. */
    internal fun tick() {
        synchronized(lock) {
            val now = clock()
            val nodes = port.nodes()
            nodes.forEach { apply(it, now) }
            // A tablet that was unlinked takes its stream with it.
            val linked = nodes.map { it.id }.toSet()
            clients.keys.filterNot { it in linked }.forEach { closeClient(it) }
        }
    }

    private fun apply(n: LinkedNode, now: Long) {
        val id = n.id
        val cam = _states.value[id]
        val end = ended[id]
        val want = CamPlan.want(
            armed = id in _armed.value, online = port.isOnline(id), supportsCam = port.status(id)?.supportsCam == true,
            viewing = viewing, cam = cam, hasClient = clients.containsKey(id), now = now,
            ended = end?.kind, endedAt = end?.at ?: 0, retries = end?.retries ?: 0, mine = id in mine,
        )
        when (want) {
            CamPlan.Want.START -> {
                val last = lastStart[id]
                if (last != null && now - last < START_EVERY_MS) return
                val prefs = store.camPrefs.value
                if (port.send(id, CamMessages.cmdStart(prefs.profile, prefs.maxMs))) {
                    lastStart[id] = now
                    mine += id
                    ended.remove(id)
                }
            }
            CamPlan.Want.STOP -> {
                port.send(id, CamMessages.cmdStop())
                closeClient(id)
                mine -= id
                ended.remove(id)
                // Count it as off now; the tablet's own answer follows and says the same.
                if (cam != null) _states.update { it + (id to cam.copy(state = NodeCam.OFF, reason = NodeCam.STOPPED, receivedAt = now)) }
            }
            CamPlan.Want.CONNECT -> {
                val url = CamPlan.streamUrl(linkHost(id), cam?.port ?: 0, cam?.key) ?: return
                val c = Client()
                clients[id] = c
                mine += id
                c.stream = open(url, { f -> onFrame(id, c, f) }, { reason -> onEnded(id, c, reason) })
            }
            CamPlan.Want.DISCONNECT -> closeClient(id)
            CamPlan.Want.NONE -> {}
        }
    }

    /** Called on the stream's thread, up to 15 times a second. */
    private fun onFrame(id: String, c: Client, f: MjpegClient.Frame) {
        if (clients[id] !== c) return
        frameFlow(id).value = f
        if (!c.got) {
            c.got = true
            // A stream that shows a picture is a good one: earlier failures no longer count.
            synchronized(lock) { ended.remove(id) }
        }
    }

    private fun onEnded(id: String, c: Client, reason: String) {
        synchronized(lock) {
            if (clients[id] !== c) return
            clients.remove(id)
            frameFlow(id).value = null
            val before = ended[id]?.retries ?: 0
            ended[id] = End(reason, clock(), if (reason == CamPlan.ENDED_IO) before + 1 else 0)
        }
    }

    /** Stops reading and decoding a tablet's stream, and takes its picture off the screen. */
    private fun closeClient(id: String) {
        clients.remove(id)?.stream?.close()
        _frames[id]?.value = null
    }

    companion object {
        private const val TICK_MS = 1_000L
        /** At most one `camStart` to a tablet in this long. */
        const val START_EVERY_MS = 3_000L
    }
}
