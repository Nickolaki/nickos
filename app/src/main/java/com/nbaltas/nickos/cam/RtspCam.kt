package com.nbaltas.nickos.cam

/** Building and hiding the address of a Tapo camera. Pure: no Android here. */
object RtspUrl {
    private const val UNRESERVED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"

    /**
     * RFC 3986 userinfo percent-encoding: unreserved bytes pass, everything else is %XX (uppercase), UTF-8.
     * Not URLEncoder: that is form encoding, where a space becomes + and Media3 would send the plus literally.
     */
    fun enc(s: String): String {
        val sb = StringBuilder()
        for (b in s.toByteArray(Charsets.UTF_8)) {
            val c = (b.toInt() and 0xff).toChar()
            if (c in UNRESERVED) sb.append(c)
            else sb.append('%').append("0123456789ABCDEF"[(b.toInt() shr 4) and 0xf]).append("0123456789ABCDEF"[b.toInt() and 0xf])
        }
        return sb.toString()
    }

    fun tapo(host: String, user: String, pass: String, hd: Boolean): String =
        "rtsp://${enc(user)}:${enc(pass)}@$host:554/${if (hd) "stream1" else "stream2"}"

    /** "rtsp://u:secret@h/x" becomes "rtsp://u:***@h/x"; a url with no userinfo is returned unchanged. */
    fun redact(url: String): String {
        val scheme = url.indexOf("://")
        if (scheme < 0) return url
        val authStart = scheme + 3
        // The last @ anywhere: a raw password may hold a slash, and hiding too much in a log beats showing too little.
        val at = url.lastIndexOf('@')
        if (at < authStart) return url
        val colon = url.indexOf(':', authStart)
        if (colon < 0 || colon > at) return url
        return url.substring(0, colon + 1) + "***" + url.substring(at)
    }
}

/** What a camera tile can be telling the parent. */
enum class RtspNews { CONNECTING, LIVE, UNREACHABLE, WRONG_PASSWORD, CANT_PLAY, LOOKING, MOVED, ERROR }

object RtspNewsFor {
    private val NETWORK = setOf("ConnectException", "SocketTimeoutException", "NoRouteToHostException", "UnknownHostException")

    // Media3 raises a 401 as an RtspPlaybackException; the reason phrase is "Unauthorized" and the status is 401.
    private val AUTH = Regex("unauthorized|\\b401\\b", RegexOption.IGNORE_CASE)

    /** [e] and its causes, outermost first, as (simple class name, message). Stops on a cause that points back at itself. */
    fun chainOf(e: Throwable): List<Pair<String, String?>> {
        val out = ArrayList<Pair<String, String?>>()
        var t: Throwable? = e
        while (t != null && out.size < 8) {
            out += t.javaClass.simpleName to t.message
            val next = t.cause
            if (next === t) break
            t = next
        }
        return out
    }

    /** [chain] is the exception and its causes, outermost first, as (simple class name, message). */
    fun of(errorCode: Int, chain: List<Pair<String, String?>>): RtspNews = when {
        chain.any { (_, m) -> m != null && AUTH.containsMatchIn(m) } -> RtspNews.WRONG_PASSWORD
        errorCode in 4001..4005 -> RtspNews.CANT_PLAY
        chain.any { (c, _) -> c in NETWORK } -> RtspNews.UNREACHABLE
        errorCode == 2001 || errorCode == 2002 -> RtspNews.UNREACHABLE // IO_NETWORK_CONNECTION_FAILED / _TIMEOUT
        else -> RtspNews.ERROR
    }
}

/** What to do after a rescan for a camera whose saved address stopped answering. */
sealed class Adopt {
    data class Move(val host: String) : Adopt()
    object Keep : Adopt()        // the saved host answered after all
    object Ambiguous : Adopt()   // more than one candidate
    object NotFound : Adopt()
}

object AdoptPolicy {
    /** [found] = RTSP hosts that answered, [current] = the saved host, [taken] = hosts other saved cameras use. */
    fun decide(found: List<String>, current: String, taken: Set<String>): Adopt {
        if (current in found) return Adopt.Keep
        val free = found.filterNot { it in taken }
        return when (free.size) { 0 -> Adopt.NotFound; 1 -> Adopt.Move(free[0]); else -> Adopt.Ambiguous }
    }
}
