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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext

/** How a frame left: written, dropped because the link was busy, or the link is gone (closed, replaced, or stuck past its deadline). */
enum class Sent { OK, BUSY, GONE }

/** One tablet's link as the talk sender uses it. Audio never goes through the command queue. */
interface TalkWire {
    /** Start and end: waits its turn, like any command. */
    fun control(o: JSONObject): Boolean
    /** A frame: written now or dropped, never queued behind other traffic, and never allowed to block for long. */
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
 * Which press owns a tablet's link. A press takes over every tablet it talks to ([next]); from then on anything an
 * older press has not yet put on the wire is dropped, so an old, late "talkStart" can never arrive after a newer
 * press began and replace it on the Node. Checking and writing happen under one lock, so a message is either fully
 * written before the newer press starts writing, or not written at all.
 */
class TalkGate {
    private val generation = AtomicLong()
    private val lock = Any()

    /** A new press takes the link; returns its number. Never waits for a write. */
    fun next(): Long = generation.incrementAndGet()

    internal fun <T> whileCurrent(mine: Long, stale: T, send: () -> T): T = synchronized(lock) { if (generation.get() != mine) stale else send() }
}

private class GatedWire(private val inner: TalkWire, private val gate: TalkGate, private val mine: Long) : TalkWire {
    override fun control(o: JSONObject) = gate.whileCurrent(mine, false) { inner.control(o) }
    override fun frame(o: JSONObject) = gate.whileCurrent(mine, Sent.GONE) { inner.frame(o) }
}

/**
 * Sends one press's audio to its recipients. Every recipient has its own bounded queue and its own coroutine, so a
 * slow tablet drops its own oldest audio and never delays the microphone or the other tablets. A frame the link
 * cannot take right now is dropped, not queued ([TalkWire.frame]). [lanes] is where those coroutines run.
 */
class TalkSender(
    private val scope: CoroutineScope,
    val sid: String,
    wires: Map<String, TalkWire>,
    private val lanes: CoroutineContext = Dispatchers.IO,
    private val onChange: () -> Unit = {},
) {
    private inner class Lane(val id: String, val wire: TalkWire) {
        val queue = FrameQueue<JSONObject>()
        val signal = Channel<Unit>(Channel.CONFLATED)
        @Volatile var gone = false
        @Volatile var busyDrops = 0L
        var job: Job? = null
    }

    private val laneList = wires.map { (id, w) -> Lane(id, w) }
    private val live = AtomicInteger(laneList.size)
    val liveRecipients get() = live.get()

    fun dropped(id: String): Long = laneList.firstOrNull { it.id == id }?.let { it.queue.dropped + it.busyDrops } ?: 0

    fun start() {
        laneList.forEach { lane ->
            lane.job = scope.launch(lanes) {
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
        laneList.forEach { if (!it.gone) { it.queue.offer(msg); it.signal.trySend(Unit) } }
    }

    /** Stops at once: waiting audio is thrown away and each tablet is told the session is over. */
    fun finish() {
        laneList.forEach { it.queue.clear(); it.signal.close() }
    }
}

/** What the Cameras screen shows: nothing, or who is being talked to right now. */
data class TalkState(
    val sid: String? = null,
    val ids: List<String> = emptyList(),
    val names: List<String> = emptyList(),
    /** The last press could not get the microphone: the button says Try again until the next press. */
    val tryAgain: Boolean = false,
) {
    val talking get() = sid != null
}

/**
 * Push to talk on the Master. [press] freezes the recipients and starts the microphone; [stop] ends it, and every
 * way out of talking ends in it: release, cancel, leaving the screen, the app going to the background, losing the
 * permission, a microphone error, every tablet disconnecting, the time cap. The microphone is open only in between.
 *
 * Who owns what, so presses cannot trample each other:
 *  - Every stop names its press (its sid), also the ones raised inside the press (microphone error, all gone, time
 *    cap): an old press's late failure can never end a newer one.
 *  - The microphone is started and closed under one lock per press. A stop that comes first wins: the microphone is
 *    never even started. A new press waits for the old microphone to be closed before it opens its own.
 *  - Each tablet's link belongs to the newest press ([TalkGate]), so an old press cannot put a late talkStart on the
 *    wire after a newer press has begun.
 * [io] runs the microphone loop and its closing; [lanes] runs the per tablet senders.
 */
class TalkController(
    private val scope: CoroutineScope,
    private val openMic: () -> MicPort,
    private val wireFor: (String) -> TalkWire?,
    private val permission: () -> Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
    private val onStopped: (TalkStop) -> Unit = {},
    private val io: CoroutineContext = Dispatchers.IO,
    private val lanes: CoroutineContext = Dispatchers.IO,
    private val oldCloseWaitMs: Long = OLD_CLOSE_WAIT_MS,
) {
    enum class Press { STARTED, NEEDS_PERMISSION, BUSY, NOBODY }

    private inner class Session(val sender: TalkSender, val mic: MicPort) {
        @Volatile var active = true
        /** Start and close of the microphone never overlap, and a start never follows a close. */
        val micLock = Any()
        /** Set under [micLock] by the close: from then on this press never starts its microphone. */
        var closed = false
        /** Counted down once this press's microphone is closed. */
        val released = CountDownLatch(1)
    }

    private val _state = MutableStateFlow(TalkState())
    val state: StateFlow<TalkState> = _state
    private var session: Session? = null
    private var lastReleased: CountDownLatch? = null
    private val gates = ConcurrentHashMap<String, TalkGate>()

    /**
     * Without the permission nothing starts: the caller asks for it and the parent presses again. [shown] is every
     * kid tablet on the screen; the recipients are the ones among them that can hear, and stay so for this press.
     */
    @Synchronized
    fun press(shown: List<TalkTarget>): Press {
        if (!permission()) return Press.NEEDS_PERMISSION
        if (session != null) return Press.BUSY
        val chosen = TalkRecipients.freeze(shown)
        val wires = chosen.mapNotNull { t ->
            wireFor(t.id)?.let { w -> val g = gates.getOrPut(t.id) { TalkGate() }; t.id to (GatedWire(w, g, g.next()) as TalkWire) }
        }.toMap()
        if (wires.isEmpty()) return Press.NOBODY
        val names = chosen.filter { it.id in wires }.map { it.name }
        val sid = TalkMessages.newSid()
        lateinit var s: Session
        val sender = TalkSender(scope, sid, wires, lanes) { scope.launch(io) { checkGone(s) } }
        s = Session(sender, openMic())
        val before = lastReleased
        lastReleased = s.released
        session = s
        _state.value = TalkState(sid, wires.keys.toList(), names)
        sender.start()
        val began = clock()
        scope.launch(io) { capture(s, began, before) }
        return Press.STARTED
    }

    /**
     * Ends the press, whatever the reason. Safe to call again and from any thread. With [sid] it only ends that
     * press: a finger lifted from a button that never got to talk, or the late failure of an old press, must not cut
     * off another press.
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
        // The caller (a finger lifting, a lifecycle callback) never waits for the microphone: closing happens here,
        // after any start that is already in progress has finished.
        scope.launch(io) { closeMic(s) }
        onStopped(reason)
    }

    // Every stop ends here, under the same lock the start takes (run off the caller's thread so a finger lifting never
    // waits for a slow start). Once it has run, a start that was already past its check finds [closed] and does not open.
    private fun closeMic(s: Session) { synchronized(s.micLock) { s.closed = true; try { s.mic.stop() } catch (_: Exception) {} } }

    private fun checkGone(s: Session) {
        if (s.active && s.sender.liveRecipients <= 0) stop(TalkStop.ALL_GONE, s.sender.sid)
    }

    private fun capture(s: Session, began: Long, before: CountDownLatch?) {
        var seq = 0L
        var audioOk = true
        var oldStillOpen = false
        try {
            // A new press waits for the old microphone to be closed. If it is not closed in time this press FAILS
            // (nothing is recorded, the button says Try again): two presses never own the microphone together.
            if (before != null && !before.await(oldCloseWaitMs, TimeUnit.MILLISECONDS)) {
                oldStillOpen = true
                stop(TalkStop.MIC_BUSY, s.sender.sid)
                synchronized(this) { if (session == null) _state.value = TalkState(tryAgain = true) }
                // The next press must wait for the old microphone too: this one is released only after it.
                scope.launch(io) { before.await(); s.released.countDown() }
                return
            }
            val started = synchronized(s.micLock) {
                if (!s.active || s.closed) false else { s.mic.start(); true }
            }
            if (!started) return
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
                if (why != null) { stop(why, s.sender.sid); break }
                s.sender.push(++seq, buf.copyOf())
            }
        } catch (e: Exception) {
            stop(TalkStop.AUDIO_ERROR, s.sender.sid)
        } finally {
            // A stop that came before the microphone had started would have found nothing to close.
            closeMic(s)
            if (!oldStillOpen) s.released.countDown()
        }
    }

    companion object { const val OLD_CLOSE_WAIT_MS = 2_000L }
}

/** 16 kHz mono 16 bit from the voice communication source, which also cancels echo from the tablet's own speaker. */
class AndroidMic : MicPort {
    @Volatile private var rec: AudioRecord? = null

    @SuppressLint("MissingPermission")
    @Synchronized
    override fun start() {
        val min = AudioRecord.getMinBufferSize(TalkMessages.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val r = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION, TalkMessages.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, maxOf(min, TalkMessages.FRAME_BYTES * 4),
        )
        if (r.state != AudioRecord.STATE_INITIALIZED) { r.release(); throw IllegalStateException("microphone not ready") }
        try { r.startRecording() } catch (e: Exception) { r.release(); throw e }
        rec = r
    }

    // Not synchronized: a blocking read must not keep stop() waiting. stop() takes the recorder away first.
    override fun read(buf: ByteArray, off: Int, len: Int): Int = rec?.read(buf, off, len) ?: -1

    @Synchronized
    override fun stop() {
        val r = rec ?: return
        rec = null
        try { r.stop() } catch (_: Exception) {}
        r.release()
    }
}
