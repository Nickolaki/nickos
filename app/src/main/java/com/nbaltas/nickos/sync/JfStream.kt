package com.nbaltas.nickos.sync

import java.net.URLEncoder
import java.security.MessageDigest

/**
 * Streaming a Jellyfin movie to the tablets instead of sending them a file. The address carries the
 * Master's Jellyfin token, so it is built here in one place and only ever put on a sealed link to a
 * tablet that said "stream1" (see [StreamGate]). Never log it: use [redact] for anything printed.
 */
object JfStream {
    /** Fids of streamed movies start with this, so they are never mistaken for a file on a tablet. */
    const val FID_PREFIX = "jf:"

    /**
     * Jellyfin's static stream address for [itemId], with the token as the `api_key` query value.
     * [base] keeps its own scheme: an https server is reached over https.
     */
    fun authedUrl(base: String, itemId: String, token: String): String =
        base.trimEnd('/') + "/Videos/" + itemId + "/stream?static=true&api_key=" + URLEncoder.encode(token, "UTF-8")

    /** "jf:" + a short hash of the server address + ":" + the item id. The same movie on the same server gets the same fid. */
    fun fid(base: String, itemId: String): String {
        val host = base.trimEnd('/').lowercase()
        val h = MessageDigest.getInstance("SHA-256").digest(host.toByteArray()).take(4).joinToString("") { "%02x".format(it) }
        return "$FID_PREFIX$h:$itemId"
    }

    fun isStreamFid(fid: String?): Boolean = fid != null && fid.startsWith(FID_PREFIX)

    /** [text] with every address that has an `api_key` taken out. For any log or error string. */
    fun redact(text: String): String = text.replace(Regex("""https?://\S*"""), "[address]").replace(Regex("""api_key=[^&\s]*"""), "api_key=[x]")
}

/** Who may be sent or may act on a stream address. */
object StreamGate {
    /** A tablet follows a `sync` carrying an address only on a sealed link. Without an address nothing changes. */
    fun nodeAllows(sealed: Boolean, url: String?): Boolean = url.isNullOrEmpty() || sealed

    /** The Master sends an address only to a tablet that is sealed, says "stream1" and is in the session. */
    fun masterSends(sealed: Boolean, streamCap: Boolean, inSession: Boolean): Boolean = sealed && streamCap && inSession
}
