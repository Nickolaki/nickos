package com.nbaltas.nickos.talk

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** One speaker for one talk session: an AudioTrack and audio focus on a real tablet. A port so it can be tested. */
interface Speaker {
    /** Allocates and starts. Throws when it cannot, and then holds nothing: a half built speaker cleans up after itself. */
    fun open()
    /** Plays [len] bytes from [off]. Returns the bytes taken, or below zero for an error. May take fewer than asked. */
    fun write(pcm: ByteArray, off: Int, len: Int): Int
    /**
     * [flush]: let the speaker play on for at most [TalkBudget.DRAIN_MS], then pause and flush the track, release it and
     * give up focus. Otherwise cut it off at once. Either way nothing buffered is played out later. Safe to call twice.
     */
    fun close(flush: Boolean)
}

/** What tells the people near the tablet that a parent is talking: the banner, and a notification when it cannot be drawn over an app. */
interface TalkIndicator {
    /** True when "Parent is talking" can be made visible right now (the overlay banner, or a notification that will show). */
    fun available(): Boolean
    /**
     * Shows it and says whether it really is up: the overlay was added without an exception, or the notification was
     * posted and its channel is on. False means nothing is visible, and no audio may play.
     */
    fun show(text: String): Boolean
    /**
     * Called a few times a second until the session has finished playing (also while the last of it drains). Keeps the
     * indicator visible if the way it was shown was lost (the overlay permission taken away: post the notification
     * instead). False when no way is left: the audio must stop.
     */
    fun maintain(): Boolean
    fun clear()
}

/** Where "Parent is talking" must be seen. Pure, so the locked and missing permission cases are tested. */
object TalkBannerPolicy {
    /**
     * The banner is an attached window on every screen, including home and the locked wall.
     * This lets playback wait for actual window attachment before opening the speaker.
     */
    fun overlayShows(talking: Boolean, locked: Boolean, homeVisible: Boolean) = talking

    /** Without the display over other apps permission the overlay cannot appear: a loud notification does the job. */
    fun needsNotification(canOverlay: Boolean) = !canOverlay

    enum class Way { OVERLAY, NOTICE, NONE }

    /** How the indicator can be shown: the overlay banner, else a notification that will really show, else not at all. */
    fun way(canOverlay: Boolean, noticeReady: Boolean) = when {
        canOverlay -> Way.OVERLAY
        noticeReady -> Way.NOTICE
        else -> Way.NONE
    }

    /** Talk audio plays only when the people near the tablet can be shown that a parent is talking. */
    fun mayPlay(canOverlay: Boolean, noticeReady: Boolean) = way(canOverlay, noticeReady) != Way.NONE
}

/**
 * Plays a talk session's audio and keeps the "Parent is talking" indicator for exactly as long as it is audible.
 *  - The indicator comes first: the speaker is only opened (so nothing can play) after [TalkIndicator.show] said it is
 *    really up. If it was not, nothing is opened and [begin] says false.
 *  - Setup is all or nothing: if the speaker cannot start, it is closed, the indicator is taken down again, and [begin]
 *    says false so the receiver drops the session.
 *  - The indicator belongs to the session that showed it. It clears when THAT session has finished playing (also the
 *    short tail that is still queued after a normal end), and a session that was replaced cannot clear a newer one's.
 *    It is maintained until then, not only while the receiver's session is open ([indicatorHolds]).
 *  - A normal end keeps at most [TalkBudget.TAIL_FRAMES] of queued speech and drops the rest; the speaker then pauses and
 *    flushes: release to silence is short, not the length of whatever was queued.
 *  - A write that fails (error, or no progress) ends the session and tells [onFailed].
 * Each session has its own writer thread, so the link's reading thread only ever hands over a frame.
 */
class TalkPlayer(
    private val newSpeaker: () -> Speaker,
    private val indicator: TalkIndicator,
    private val nameOf: (String) -> String,
    private val onFailed: (String) -> Unit,
    private val log: (String) -> Unit = {},
    private val startThread: (String, () -> Unit) -> Unit = { n, r -> Thread(r, n).also { it.isDaemon = true }.start() },
) : TalkSink {
    private var current: Playing? = null
    /** Whose indicator is up. Only this session may clear it. */
    private var shown: Playing? = null

    @Synchronized
    override fun begin(sid: String, masterId: String): Boolean {
        current?.close(flush = false)
        current = null
        // No visible indicator, no audio: nothing is opened, the receiver drops the session.
        if (!indicator.available()) { log("talk refused: no way to show that a parent is talking"); return false }
        val p = Playing(sid, newSpeaker())
        // Nothing can sound before the indicator is up: the speaker is only opened after it.
        val up = try { indicator.show(nameOf(masterId)) } catch (e: Throwable) { log("talk indicator failed: ${e.message}"); false }
        if (!up) {
            try { indicator.clear() } catch (_: Throwable) {}
            log("talk refused: the indicator could not be shown")
            return false
        }
        shown = p
        if (!p.open()) {
            shown = null
            try { indicator.clear() } catch (_: Throwable) {}
            return false
        }
        current = p
        return true
    }

    @Synchronized
    override fun play(pcm: ByteArray) { current?.offer(pcm) }

    /**
     * False when audio is still audible (also the tail draining after the end) and its indicator can no longer be
     * kept visible: the audio is cut off here, and the caller ends the session.
     */
    @Synchronized
    fun indicatorHolds(): Boolean {
        val p = shown ?: return true
        val holds = try { indicator.maintain() } catch (e: Throwable) { false }
        if (!holds) { log("talk audio stopped: the indicator was lost"); p.close(flush = false) }
        return holds
    }

    /** The indicator stays until the writer has played out what is left. */
    @Synchronized
    override fun end(flush: Boolean) {
        current?.close(flush)
        current = null
    }

    @Synchronized
    private fun finished(p: Playing) {
        if (current === p) current = null
        if (shown === p) { shown = null; indicator.clear() }
    }

    private enum class Mode { OPEN, FLUSHING, CUT }

    private inner class Playing(val sid: String, private val speaker: Speaker) {
        private val queue = LinkedBlockingQueue<ByteArray>(MAX_WAITING)
        @Volatile private var mode = Mode.OPEN
        private val closeLock = Any()
        private var speakerClosed = false

        private fun closeSpeaker(flush: Boolean) = synchronized(closeLock) {
            // The writer must wait for a concurrent cutoff to finish closing before clearing the indicator.
            if (!speakerClosed) { speakerClosed = true; speaker.close(flush) }
        }

        /** Speaker and writer thread, or neither. */
        fun open(): Boolean {
            try {
                speaker.open()
                startThread("NickOS.Talk") { run() }
            } catch (e: Throwable) {
                log("talk playback could not start: ${e.message}")
                try { speaker.close(false) } catch (_: Throwable) {}
                return false
            }
            return true
        }

        /** Never blocks: when the speaker is behind, the oldest waiting frame goes. */
        fun offer(pcm: ByteArray) { if (mode == Mode.OPEN) while (!queue.offer(pcm)) queue.poll() }

        /** [flush]: keep only the short tail and play it. A cut always wins, also over a tail that is already playing. */
        fun close(flush: Boolean) {
            if (mode == Mode.OPEN) {
                if (flush) dropAllButTail()
                mode = if (flush) Mode.FLUSHING else Mode.CUT
                // A blocking AudioTrack write must not extend the tail. Pause and flush on another thread
                // at the deadline; AndroidSpeaker allows close to interrupt a write already in progress.
                cutoff.schedule({
                    mode = Mode.CUT
                    try { closeSpeaker(false) } catch (_: Throwable) {}
                }, if (flush) TalkBudget.DRAIN_MS else 0L, TimeUnit.MILLISECONDS)
            } else if (!flush && mode == Mode.FLUSHING) mode = Mode.CUT
        }

        /** The oldest [TalkBudget.TAIL_FRAMES] stay (the speech carries on from what is playing), the rest is dropped. */
        private fun dropAllButTail() {
            val keep = ArrayList<ByteArray>(TalkBudget.TAIL_FRAMES)
            queue.drainTo(keep, TalkBudget.TAIL_FRAMES)
            queue.clear()
            keep.forEach { queue.offer(it) }
        }

        private fun writeAll(f: ByteArray): Boolean {
            var off = 0
            var stuck = 0
            while (off < f.size) {
                if (mode == Mode.CUT) return true
                val n = speaker.write(f, off, f.size - off)
                if (n < 0) return false
                if (n == 0) { if (++stuck >= MAX_STUCK) return false } else { stuck = 0; off += n }
            }
            return true
        }

        private fun run() {
            var failed = false
            try {
                while (true) {
                    val f = (if (mode == Mode.OPEN) queue.poll(POLL_MS, TimeUnit.MILLISECONDS) else queue.poll()) ?: if (mode == Mode.OPEN) continue else break
                    if (mode == Mode.CUT) break
                    if (!writeAll(f)) { failed = true; break }
                }
            } catch (e: Throwable) {
                log("talk write failed: ${e.message}")
                failed = true
            } finally {
                try { closeSpeaker(flush = !failed && mode == Mode.FLUSHING) } catch (_: Throwable) {}
                if (failed) onFailed(sid)
                finished(this)
            }
        }
    }

    companion object {
        /** About a second of audio: more than that is the speaker falling behind, and old audio is no use. */
        const val MAX_WAITING = 25
        private val cutoff = java.util.concurrent.Executors.newScheduledThreadPool(2) { r ->
            Thread(r, "NickOS.TalkEnd").also { it.isDaemon = true }
        }
        private const val POLL_MS = 50L
        /** A speaker that takes nothing this many times in a row is broken. */
        private const val MAX_STUCK = 5
    }
}
