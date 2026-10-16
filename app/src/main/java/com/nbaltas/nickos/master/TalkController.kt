package com.nbaltas.nickos.master

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.nbaltas.nickos.talk.FrameQueue
import com.nbaltas.nickos.talk.TalkMessages
import com.nbaltas.nickos.talk.TalkRecipients
import com.nbaltas.nickos.talk.TalkStop
import com.nbaltas.nickos.talk.TalkStopPolicy
import com.nbaltas.nickos.talk.TalkTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

/** How a frame left: written, dropped because the link was busy, or the link is gone. */
enum class Sent { OK, BUSY, GONE }

/** One tablet's link as the talk sender uses it. Audio never goes through the command queue. */
interface TalkWire {
    /** Start and end: waits its turn, like any command. */
    fun control(o: JSONObject): Boolean
    /** A frame: written now or dropped, never queued behind other traffic. */
    fun frame(o: JSONObject): Sent
}

/** The microphone. A port so the controller can be tested without hardware. */
interface MicPort {
    fun start()
    /** Fills [buf] from [off]; returns the bytes read, or below zero on an error. */
    fun read(buf: ByteArray, off: Int, len: Int): Int
    fun stop()
}

/**
 * Sends one press's audio to its recipients. Every recipient has its own bounded queue and its own coroutine, so a
 * slow tablet drops its own oldest audio and never delays the microphone or the other tablets. A frame the link
 * cannot take right now is dropped, not queued ([TalkWire.frame]).
 */
class TalkSender(private val scope: CoroutineScope, val sid: String, wires: Map<String, TalkWire>, private val onChange: () -> Unit = {}) {
    private inner class Lane(val id: String, val wire: TalkWire) {
        val queue = FrameQueue<JSONObject>()
        val signal = Channel<Unit>(Channel.CONFLATED)
        @Volatile var gone = false
        @Volatile var busyDrops = 0L
        var job: Job? = null
    }

    private val lanes = wires.map { (id, w) -> Lane(id, w) }
    private val live = AtomicInteger(lanes.size)
    val liveRecipients get() = live.get()

    fun dropped(id: String): Long = lanes.firstOrNull { it.id == id }?.let { it.queue.dropped + it.busyDrops } ?: 0

    fun start() {
        lanes.forEach { lane ->
            lane.job = scope.launch(Dispatchers.IO) {
                var sentStart = false
                try {
                    if (!lane.wire.control(TalkMessages.start(sid))) return@launch lose(lane)
                    sentStart = true
                    for (ignored in lane.signal) {
                        while (true) {
                            val f = lane.queue.poll() ?: break
                            when (lane.wire.frame(f)) {
                                Sent.OK -> {}
                                Sent.BUSY -> lane.busyDrops++
                                Sent.GONE -> return@launch lose(lane)
                            }
                        }
                    }
                } finally {
                    if (sentStart && !lane.gone) lane.wire.control(TalkMessages.end(sid))
                }
            }
        }
    }

    private fun lose(lane: Lane) {
        if (!lane.gone) { lane.gone = true; live.decrementAndGet(); onChange() }
    }

    /** Hands one frame to every tablet that is still there. Cheap: the frame is built once. */
    fun push(seq: Long, pcm: ByteArray) {
        val msg = TalkMessages.frame(sid, seq, pcm)
        lanes.forEach { if (!it.gone) { it.queue.offer(msg); it.signal.trySend(Unit) } }
    }

    /** Stops at once: waiting audio is thrown away and each tablet is told the session is over. */
    fun finish() {
        lanes.forEach { it.queue.clear(); it.signal.close() }
    }
}

/** What the Cameras screen shows: nothing, or who is being talked to right now. */
data class TalkState(val sid: String? = null, val ids: List<String> = emptyList(), val names: List<String> = emptyList()) {
    val talking get() = sid != null
}

/**
 * Push to talk on the Master. [press] freezes the recipients and starts the microphone; [stop] ends it, and every
 * way out of talking ends in it: release, cancel, leaving the screen, the app going to the background, losing the
 * permission, a microphone error, every tablet disconnecting, the time cap. The microphone is open only in between.
 */
class TalkController(
    private val scope: CoroutineScope,
    private val openMic: () -> MicPort,
    private val wireFor: (String) -> TalkWire?,
    private val permission: () -> Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
    private val onStopped: (TalkStop) -> Unit = {},
) {
    enum class Press { STARTED, NEEDS_PERMISSION, BUSY, NOBODY }

    private inner class Session(val sender: TalkSender, val mic: MicPort) {
        @Volatile var active = true
    }

    private val _state = MutableStateFlow(TalkState())
    val state: StateFlow<TalkState> = _state
    private var session: Session? = null

    /**
     * Without the permission nothing starts: the caller asks for it and the parent presses again. [shown] is every
     * kid tablet on the screen; the recipients are the ones among them that can hear, and stay so for this press.
     */
    @Synchronized
    fun press(shown: List<TalkTarget>): Press {
        if (!permission()) return Press.NEEDS_PERMISSION
        if (session != null) return Press.BUSY
        val chosen = TalkRecipients.freeze(shown)
        val wires = chosen.mapNotNull { t -> wireFor(t.id)?.let { t.id to it } }.toMap()
        if (wires.isEmpty()) return Press.NOBODY
        val names = chosen.filter { it.id in wires }.map { it.name }
        val sid = TalkMessages.newSid()
        lateinit var s: Session
        val sender = TalkSender(scope, sid, wires) { scope.launch(Dispatchers.IO) { checkGone(s) } }
        s = Session(sender, openMic())
        session = s
        _state.value = TalkState(sid, wires.keys.toList(), names)
        sender.start()
        val began = clock()
        scope.launch(Dispatchers.IO) { capture(s, began) }
        return Press.STARTED
    }

    /**
     * Ends the press, whatever the reason. Safe to call again and from any thread. With [sid] it only ends that
     * press: a finger lifted from a button that never got to talk must not cut off another button's press.
     */
    fun stop(reason: TalkStop, sid: String? = null) {
        val s = synchronized(this) {
            val s = session ?: return
            if (sid != null && s.sender.sid != sid) return
            session = null
            _state.value = TalkState()
            s.active = false
            s
        }
        s.sender.finish()
        try { s.mic.stop() } catch (_: Exception) {}
        onStopped(reason)
    }

    private fun checkGone(s: Session) {
        if (s.active && s.sender.liveRecipients <= 0) stopIf(s, TalkStop.ALL_GONE)
    }

    private fun stopIf(s: Session, r: TalkStop) { if (synchronized(this) { session === s }) stop(r) }

    private fun capture(s: Session, began: Long) {
        var seq = 0L
        var audioOk = true
        try {
            s.mic.start()
            val buf = ByteArray(TalkMessages.FRAME_BYTES)
            while (s.active) {
                var off = 0
                while (off < buf.size && s.active) {
                    val n = s.mic.read(buf, off, buf.size - off)
                    if (n < 0) { audioOk = false; break }
                    off += n
                }
                if (!s.active) break
                val why = TalkStopPolicy.reason(TalkStopPolicy.Inputs(permission(), audioOk, s.sender.liveRecipients, clock() - began))
                if (why != null) { stopIf(s, why); break }
                s.sender.push(++seq, buf.copyOf())
            }
        } catch (e: Exception) {
            stopIf(s, TalkStop.AUDIO_ERROR)
        } finally {
            // A stop that came before the microphone had started would have found nothing to close.
            try { s.mic.stop() } catch (_: Exception) {}
        }
    }
}

/** 16 kHz mono 16 bit from the voice communication source, which also cancels echo from the tablet's own speaker. */
class AndroidMic : MicPort {
    private var rec: AudioRecord? = null

    @SuppressLint("MissingPermission")
    override fun start() {
        val min = AudioRecord.getMinBufferSize(TalkMessages.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val r = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION, TalkMessages.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, maxOf(min, TalkMessages.FRAME_BYTES * 4),
        )
        if (r.state != AudioRecord.STATE_INITIALIZED) { r.release(); throw IllegalStateException("microphone not ready") }
        rec = r
        r.startRecording()
    }

    override fun read(buf: ByteArray, off: Int, len: Int): Int = rec?.read(buf, off, len) ?: -1

    override fun stop() {
        val r = rec ?: return
        rec = null
        try { r.stop() } catch (_: Exception) {}
        r.release()
    }
}
