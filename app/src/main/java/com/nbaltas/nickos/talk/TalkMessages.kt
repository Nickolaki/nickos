package com.nbaltas.nickos.talk

import com.nbaltas.nickos.link.B64
import org.json.JSONObject
import java.security.SecureRandom

/** Talk is only taken from a sealed, authenticated link. A legacy v1 session (clear text token) can still send commands, but not audio. */
object TalkGate {
    fun allows(sealed: Boolean) = sealed
}

/**
 * What the parent should feel on the same WiFi, and the knobs that decide it. The numbers on a real tablet are on
 * Nick's device checklist; what a unit test can check is that the knobs add up inside the budget.
 *  - Start to hear: the first frame is ready after one frame of speech; the Node then holds [PREFILL_FRAMES] frames of cushion.
 *  - Release to silence: on talkEnd the Node keeps at most [TAIL_FRAMES] of queued speech (about 100 ms), drops the rest,
 *    lets the speaker play for at most [DRAIN_MS] more, then pauses and flushes the track. The speaker's own buffer
 *    ([TRACK_FRAMES]) is cut by that flush, not played out.
 *  - A sender that dies without an end: the Node goes quiet after [TalkReceiver.IDLE_MS].
 */
object TalkBudget {
    const val START_TO_HEAR_MS = 500
    const val RELEASE_TO_SILENCE_MS = 300
    /** Frames the Node holds back before playing (the jitter buffer depth): 3 x 40 ms. */
    const val PREFILL_FRAMES = 3
    /** The speaker's buffer, in frames. */
    const val TRACK_FRAMES = 4
    /** Queued speech kept at the end of a talk session: 3 x 40 ms, the same as the cushion. The rest is dropped. */
    const val TAIL_FRAMES = 3
    /** The longest the speaker plays on after the last tail frame is written, before the track is paused and flushed. */
    const val DRAIN_MS = 100L
    /** What the network and the Node's link thread may add between the parent letting go and talkEnd being handled. */
    const val NETWORK_ALLOWANCE_MS = 80
    /** Time the sender and the network get in the start to hear budget once the cushion is counted. */
    val startHeadroomMs get() = START_TO_HEAR_MS - TalkMessages.FRAME_MS * PREFILL_FRAMES
    /** Worst case from talkEnd being handled to silence: the tail frames still to be written, then the drain wait. */
    val releaseTailMs get() = TalkMessages.FRAME_MS * TAIL_FRAMES + DRAIN_MS.toInt()
}

/**
 * Push to talk, Master to Node (capability "talk1" in the Node's status "caps"). All three ride the sealed link:
 *  {"t":"talkStart","sid"}                    a session begins
 *  {"t":"talk","sid","seq","pcm"}             one 40 ms frame: 16 kHz, mono, 16 bit little endian PCM, base64
 *  {"t":"talkEnd","sid"}                      the session is over
 * "sid" is 16 random hex characters. "seq" starts at 1 and rises by one per frame (the sender may skip a number when
 * it drops a frame). They are not "cmd" messages, so an older Node falls through them without any effect.
 */
object TalkMessages {
    const val CAP = "talk1"
    /** The Node's status field "talk": it can show that a parent is talking, so it plays talk audio. */
    const val STATUS_OK = "ok"
    /** It cannot (no display over other apps and no usable notification): it refuses talk audio until a grown-up allows notifications. */
    const val STATUS_NEEDS_PERMISSION = "needs_permission"
    const val SAMPLE_RATE = 16_000
    const val FRAME_MS = 40
    /** 40 ms of 16 bit mono samples. */
    const val FRAME_BYTES = SAMPLE_RATE / 1000 * FRAME_MS * 2
    /** The most a Node accepts in one frame. A real frame is 1280 bytes. */
    const val MAX_FRAME_BYTES = 4096

    private val SID = Regex("^[0-9a-f]{16}$")
    private val random = SecureRandom()

    sealed interface Msg { val sid: String }
    data class Start(override val sid: String) : Msg
    data class Frame(override val sid: String, val seq: Long, val pcm: ByteArray) : Msg
    data class End(override val sid: String) : Msg

    fun newSid(): String = ByteArray(8).also { random.nextBytes(it) }.joinToString("") { "%02x".format(it) }

    fun start(sid: String): JSONObject = JSONObject().put("t", "talkStart").put("sid", sid)
    fun end(sid: String): JSONObject = JSONObject().put("t", "talkEnd").put("sid", sid)
    fun frame(sid: String, seq: Long, pcm: ByteArray): JSONObject =
        JSONObject().put("t", "talk").put("sid", sid).put("seq", seq).put("pcm", B64.enc(pcm))

    /** A whole number, or null: 1.5 is refused rather than cut to 1, and so is anything that is not a number. */
    private fun wholeNumber(v: Any?): Long? = when (v) {
        is Int -> v.toLong()
        is Long -> v
        is Number -> v.toDouble().takeIf { !it.isNaN() && it == Math.rint(it) && Math.abs(it) < 9.0e15 }?.toLong()
        else -> null
    }

    /** True for the three talk message types, valid or not. */
    fun isTalk(m: JSONObject?): Boolean = m?.optString("t").let { it == "talkStart" || it == "talk" || it == "talkEnd" }

    /**
     * Null for anything the Node must not act on: not a talk message, a bad sid, a frame with a missing or odd length
     * payload, a payload over [MAX_FRAME_BYTES] once decoded, or a sequence number below 1.
     */
    fun decode(m: JSONObject?): Msg? {
        if (m == null) return null
        val sid = m.optString("sid")
        if (!SID.matches(sid)) return null
        return when (m.optString("t")) {
            "talkStart" -> Start(sid)
            "talkEnd" -> End(sid)
            "talk" -> {
                val seq = wholeNumber(m.opt("seq"))?.takeIf { it >= 1 } ?: return null
                val text = m.optString("pcm")
                // Check the size before decoding, so a huge string is never turned into bytes.
                if (text.isEmpty() || text.length > MAX_FRAME_BYTES * 4 / 3 + 4) return null
                val pcm = B64.dec(text) ?: return null
                if (pcm.isEmpty() || pcm.size > MAX_FRAME_BYTES || pcm.size % 2 != 0) return null
                Frame(sid, seq, pcm)
            }
            else -> null
        }
    }
}
