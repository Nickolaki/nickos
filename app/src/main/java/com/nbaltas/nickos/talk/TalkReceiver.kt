package com.nbaltas.nickos.talk

import org.json.JSONObject

/** What a [TalkReceiver] drives: the speaker and the banner. Calls come one at a time. */
interface TalkSink {
    fun begin(sid: String, masterId: String)
    fun play(pcm: ByteArray)
    /** [flush]: let what was already played out finish (a normal end) instead of cutting it off. */
    fun end(flush: Boolean)
}

/**
 * The Node's side of push to talk, with no Android in it. One session at a time, owned by the Master that started it.
 *  - A newer talkStart replaces the session in progress (the newest speaker wins).
 *  - Frames count only for the live session, from its own Master, with a sequence number above the last one.
 *  - A session with no frame for [IDLE_MS] ends by itself, and none lasts past [CAP_MS], so a sender that dies,
 *    loses its network or never sends talkEnd cannot leave the speaker open.
 *  - The first [PREFILL] frames are held back (about 120 ms) so the speaker starts with a little cushion; if frames
 *    stop for longer than that the cushion is filled again before playing on.
 * [linked] says whether a Master id is still linked to this tablet, checked on every message.
 */
class TalkReceiver(
    private val sink: TalkSink,
    private val linked: (String) -> Boolean,
    private val clock: () -> Long,
) {
    enum class Result { ACCEPTED, IGNORED, REJECTED }

    private var sid: String? = null
    private var owner: String? = null
    private var startedAt = 0L
    private var lastAt = 0L
    private var lastSeq = 0L
    private var playing = false
    private val held = ArrayList<ByteArray>()
    // The last few sessions that ended, so a late frame cannot bring one back.
    private val ended = ArrayDeque<String>()

    val active: Boolean @Synchronized get() = sid != null
    val activeSid: String? @Synchronized get() = sid

    @Synchronized
    fun onMessage(masterId: String, m: JSONObject?): Result {
        if (!linked(masterId)) return Result.REJECTED
        val msg = TalkMessages.decode(m) ?: return Result.REJECTED
        val now = clock()
        return when (msg) {
            is TalkMessages.Start -> {
                if (msg.sid == sid) return Result.IGNORED
                if (msg.sid in ended) return Result.REJECTED
                if (sid != null) finish(flush = false)
                sid = msg.sid; owner = masterId; startedAt = now; lastAt = now; lastSeq = 0; playing = false; held.clear()
                sink.begin(msg.sid, masterId)
                Result.ACCEPTED
            }
            is TalkMessages.Frame -> {
                if (msg.sid != sid || masterId != owner) return Result.REJECTED
                if (msg.seq <= lastSeq) return Result.REJECTED
                lastSeq = msg.seq; lastAt = now
                if (playing) sink.play(msg.pcm)
                else {
                    held += msg.pcm
                    if (held.size >= PREFILL) release()
                }
                Result.ACCEPTED
            }
            is TalkMessages.End -> {
                if (msg.sid != sid || masterId != owner) return Result.REJECTED
                finish(flush = true)
                Result.ACCEPTED
            }
        }
    }

    /** Call a few times a second. Ends a session that went quiet or ran too long. */
    @Synchronized
    fun tick() {
        if (sid == null) return
        val now = clock()
        when {
            now - startedAt >= CAP_MS -> finish(flush = false)
            now - lastAt >= IDLE_MS -> finish(flush = true)
            // A gap longer than the cushion: gather it again rather than play in stutters.
            playing && now - lastAt >= PREFILL * 40L -> playing = false
        }
    }

    /** The Master's link closed or it was unlinked. */
    @Synchronized
    fun onMasterGone(masterId: String) {
        if (sid != null && owner == masterId) finish(flush = false)
    }

    /** Everything stops (the Node is shutting down). */
    @Synchronized
    fun stop() { if (sid != null) finish(flush = false) }

    private fun release() {
        playing = true
        held.forEach { sink.play(it) }
        held.clear()
    }

    private fun finish(flush: Boolean) {
        val done = sid ?: return
        if (flush && held.isNotEmpty()) release()
        held.clear()
        playing = false
        sid = null; owner = null
        ended += done
        while (ended.size > REMEMBER) ended.removeFirst()
        sink.end(flush)
    }

    companion object {
        /** No frame for this long ends the session. */
        const val IDLE_MS = 1_500L
        /** The longest session, whatever the sender does. */
        const val CAP_MS = 120_000L
        /** Frames held back before playing: 3 x 40 ms. */
        const val PREFILL = 3
        private const val REMEMBER = 8
    }
}
