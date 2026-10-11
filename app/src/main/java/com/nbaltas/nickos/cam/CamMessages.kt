package com.nbaltas.nickos.cam

import org.json.JSONObject

/**
 * Encode/decode for the messages tablet cameras add to the line link (see the doc comment on
 * `link/Wire.kt`). Decoders return null on a missing or wrong-typed field and never throw, so a
 * bad line from a peer never ends the session loop.
 */
object CamMessages {
    /** In a Node's `status.caps` when it can stream its front camera. Old Nodes lack it. */
    const val CAP = "cam1"

    /** The longest auto-off there is. A tablet never runs its camera longer on one start, whatever it is sent. */
    const val MAX_MS = 60 * 60_000L

    private val PROFILES = setOf("low", "normal", "high")

    // ---- Master -> Node commands: {"t":"cmd","c":...} ----

    fun cmdStart(profile: String, maxMs: Long): JSONObject = cmd("camStart").put("profile", profile).put("maxMs", maxMs)
    fun cmdStop(): JSONObject = cmd("camStop")

    private fun cmd(name: String): JSONObject = JSONObject().put("t", "cmd").put("c", name)

    data class StartCmd(val profile: String, val maxMs: Long)

    /** Null if `profile` is not one of low, normal, high, or `maxMs` is missing or not a positive whole number. */
    fun decodeStart(o: JSONObject): StartCmd? = try {
        val profile = str(o, "profile")?.takeIf { it in PROFILES } ?: throw Bad()
        val maxMs = num(o, "maxMs")?.takeIf { it > 0 } ?: throw Bad()
        StartCmd(profile, maxMs)
    } catch (e: Exception) { null }

    // ---- Node -> Master ----

    /**
     * `camState`. `port` and `key` travel as separate fields and the Master builds the address
     * from where it already reaches the tablet. `now` is the tablet's clock when this was sent,
     * so `onSince` and `retryAt` (also the tablet's clock) can be read on the Master.
     */
    fun encodeState(c: NodeCam): JSONObject {
        val o = JSONObject().put("t", "camState").put("state", c.state).put("level", c.level)
            .put("kind", c.kind).put("profile", c.profile).put("now", c.now)
        if (c.reason != null) o.put("reason", c.reason)
        if (c.port > 0) o.put("port", c.port)
        if (c.key != null) o.put("key", c.key)
        if (c.onSince != null) o.put("onSince", c.onSince)
        if (c.maxMs != null) o.put("maxMs", c.maxMs)
        if (c.retryAt != null) o.put("retryAt", c.retryAt)
        return o
    }

    /** [receivedAt] is the Master's clock now. */
    fun decodeState(o: JSONObject, receivedAt: Long): NodeCam? = try {
        val state = str(o, "state")?.takeIf { it in NodeCam.STATES } ?: throw Bad()
        val level = str(o, "level")?.takeIf { it in NodeCam.LEVELS } ?: throw Bad()
        val kind = str(o, "kind") ?: throw Bad()
        val profile = str(o, "profile") ?: throw Bad()
        val port = num(o, "port") ?: 0
        if (port < 0 || port > 65535) throw Bad()
        NodeCam(
            state = state, reason = str(o, "reason"), level = level, port = port.toInt(), key = str(o, "key"),
            kind = kind, profile = profile, onSince = num(o, "onSince"), maxMs = num(o, "maxMs"),
            retryAt = num(o, "retryAt"), now = num(o, "now") ?: 0, receivedAt = receivedAt,
        )
    } catch (e: Exception) { null }

    private class Bad : Exception()

    /** The string at [key], null when absent. Present but not a string is an error, not a guess. */
    private fun str(o: JSONObject, key: String): String? {
        if (!o.has(key) || o.isNull(key)) return null
        return o.get(key) as? String ?: throw Bad()
    }

    /** The whole number at [key], null when absent. org.json would happily read "47472" as a number; this does not. */
    private fun num(o: JSONObject, key: String): Long? {
        if (!o.has(key) || o.isNull(key)) return null
        return when (val v = o.get(key)) {
            is Int -> v.toLong()
            is Long -> v
            else -> throw Bad()
        }
    }
}
