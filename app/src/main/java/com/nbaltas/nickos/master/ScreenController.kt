package com.nbaltas.nickos.master

import android.graphics.Bitmap
import android.util.Log
import com.nbaltas.nickos.cam.CamPlan
import com.nbaltas.nickos.remote.RemoteMessages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/**
 * Watching, and touching, one kid tablet's screen at a time. [watch] asks the tablet to share; its `screenState` says
 * when the picture is live and where to read it; the pictures come through the same [MjpegClient] as a tablet camera.
 * Leaving the screen ([unwatch]) tells the tablet to stop. The tablet turns the stream off by itself too.
 */
class ScreenController(
    private val send: (String, JSONObject) -> Boolean,
    private val open: (url: String, onFrame: (MjpegClient.Frame) -> Unit, onEnded: (String) -> Unit) -> CamStream =
        { url, onFrame, onEnded -> MjpegClient(url, onFrame, onEnded).start() },
) {
    private val lock = Any()
    private val hosts = HashMap<String, String>()
    private var stream: CamStream? = null
    private var streamUrl: String? = null

    private val _states = MutableStateFlow<Map<String, RemoteMessages.State>>(emptyMap())
    /** Each tablet's last `screenState`. */
    val states: StateFlow<Map<String, RemoteMessages.State>> = _states

    private val _watching = MutableStateFlow<String?>(null)
    /** The tablet this Master is watching, or null. */
    val watching: StateFlow<String?> = _watching

    private val _frame = MutableStateFlow<Bitmap?>(null)
    /** The newest picture of the watched screen. */
    val frame: StateFlow<Bitmap?> = _frame

    private val _lost = MutableStateFlow(false)
    /** The picture stopped coming (the tablet turned it off, or the link dropped). Watching again starts it over. */
    val lost: StateFlow<Boolean> = _lost

    fun watch(id: String) {
        synchronized(lock) {
            closeStream()
            _watching.value = id
            _frame.value = null
            _lost.value = false
        }
        send(id, RemoteMessages.cmdStart())
    }

    fun unwatch() {
        val id: String?
        synchronized(lock) {
            id = _watching.value
            closeStream()
            _watching.value = null
            _frame.value = null
            _lost.value = false
        }
        if (id != null) send(id, RemoteMessages.cmdStop())
    }

    /** A `screenState` line from a tablet. [from] is where this Master reaches it. */
    fun onMessage(id: String, m: JSONObject, from: String?) {
        val s = RemoteMessages.decodeState(m) ?: return
        synchronized(lock) {
            if (from != null) hosts[id] = from
            _states.value = _states.value + (id to s)
            if (_watching.value != id) return
            if (s.state != RemoteMessages.LIVE) {
                if (stream != null) { closeStream(); _lost.value = true }
                return
            }
            val url = CamPlan.streamUrl(hosts[id], s.port, s.key) ?: return
            if (url == streamUrl && stream != null) return
            closeStream()
            _lost.value = false
            streamUrl = url
            val mine = url
            stream = open(url, { f -> if (streamUrl == mine) _frame.value = f.bitmap }, { reason -> onEnded(mine, reason) })
        }
    }

    private fun onEnded(url: String, reason: String) {
        synchronized(lock) {
            if (streamUrl != url) return
            Log.d(TAG, "screen stream ended: $reason")
            stream = null
            streamUrl = null
            _lost.value = true
        }
    }

    fun onDisconnected(id: String) {
        synchronized(lock) {
            _states.value = _states.value - id
            hosts.remove(id)
            if (_watching.value == id) { closeStream(); _lost.value = true }
        }
    }

    private fun closeStream() {
        stream?.close()
        stream = null
        streamUrl = null
    }

    // ---- touches, for the tablet being watched ----

    fun tap(x: Double, y: Double) = touch(RemoteMessages.cmdTap(x, y))
    fun swipe(x1: Double, y1: Double, x2: Double, y2: Double, ms: Long) = touch(RemoteMessages.cmdSwipe(x1, y1, x2, y2, ms))
    fun back() = touch(RemoteMessages.cmdBack())
    fun home() = touch(RemoteMessages.cmdHome())

    private fun touch(o: JSONObject) { _watching.value?.let { send(it, o) } }

    private companion object { const val TAG = "NickOS.Screen" }
}
