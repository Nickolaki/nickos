package com.nbaltas.nickos.talk

import com.nbaltas.nickos.link.B64
import org.json.JSONObject
import java.security.SecureRandom

/** Talk is only taken from a sealed, authenticated link. A legacy v1 session (clear text token) can still send commands, but not audio. */
object TalkGate {
    fun allows(sealed: Boolean) = sealed
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
