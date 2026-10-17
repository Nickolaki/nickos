package com.nbaltas.nickos.remote

import org.json.JSONObject

/**
 * Encode/decode for seeing and controlling a kid tablet's screen (see the doc comment on `link/Wire.kt`).
 * Decoders return null on a missing, wrong-typed or out of range field and never throw, so a bad line from
 * a peer never ends the session loop.
 */
object RemoteMessages {
    /** In a Node's `status.caps` when it can share its screen. Old Nodes lack it. */
    const val CAP = "screen1"

    /** `status.screen` and `screenState.state`. */
    const val OFF = "off"
    const val NEEDS_CONSENT = "needs_consent"
    const val READY = "ready"
    const val LIVE = "live"

    /** `status.control` and `screenState.control`. */
    const val CONTROL_OFF = "off"
    const val CONTROL_READY = "ready"

    const val MIN_SWIPE_MS = 50L
    const val MAX_SWIPE_MS = 2000L

    private val STATES = setOf(OFF, NEEDS_CONSENT, READY, LIVE)

    // ---- Master -> Node commands: {"t":"cmd","c":...} ----

    fun cmdStart(): JSONObject = cmd("screenStart")
    fun cmdStop(): JSONObject = cmd("screenStop")
    fun cmdTap(x: Double, y: Double): JSONObject = touch("tap").put("x", x).put("y", y)
    fun cmdSwipe(x1: Double, y1: Double, x2: Double, y2: Double, ms: Long): JSONObject =
        touch("swipe").put("x1", x1).put("y1", y1).put("x2", x2).put("y2", y2).put("ms", ms)
    fun cmdBack(): JSONObject = touch("back")
    fun cmdHome(): JSONObject = touch("home")

    private fun cmd(name: String): JSONObject = JSONObject().put("t", "cmd").put("c", name)
    private fun touch(kind: String): JSONObject = cmd("touch").put("k", kind)

    /** A decoded `touch` command. Coordinates are 0..1 of the screen, from its top left. */
    sealed class Touch {
        data class Tap(val x: Double, val y: Double) : Touch()
        data class Swipe(val x1: Double, val y1: Double, val x2: Double, val y2: Double, val ms: Long) : Touch()
        object Back : Touch()
        object Home : Touch()
    }

    /** Null for an unknown kind, a missing or non number coordinate, one outside 0..1, or a swipe time outside 50..2000 ms. */
    fun decodeTouch(o: JSONObject): Touch? = try {
        when (str(o, "k")) {
            "tap" -> Touch.Tap(unit(o, "x"), unit(o, "y"))
            "swipe" -> {
                val ms = whole(o, "ms")
                if (ms < MIN_SWIPE_MS || ms > MAX_SWIPE_MS) throw Bad()
                Touch.Swipe(unit(o, "x1"), unit(o, "y1"), unit(o, "x2"), unit(o, "y2"), ms)
            }
            "back" -> Touch.Back
            "home" -> Touch.Home
            else -> null
        }
    } catch (e: Exception) { null }

    // ---- Node -> Master ----

    /** What a Node says about its screen. [port] and [key] are set only while it is [LIVE]. */
    data class State(val state: String, val control: String = CONTROL_OFF, val port: Int = 0, val key: String? = null)

    fun encodeState(s: State): JSONObject {
        val o = JSONObject().put("t", "screenState").put("state", s.state).put("control", s.control)
        if (s.port > 0) o.put("port", s.port)
        if (s.key != null) o.put("key", s.key)
        return o
    }

    fun decodeState(o: JSONObject): State? = try {
        val state = str(o, "state")?.takeIf { it in STATES } ?: throw Bad()
        val control = (str(o, "control") ?: CONTROL_OFF).takeIf { it == CONTROL_OFF || it == CONTROL_READY } ?: throw Bad()
        val port = if (o.has("port")) whole(o, "port") else 0L
        if (port < 0 || port > 65535) throw Bad()
        State(state, control, port.toInt(), str(o, "key"))
    } catch (e: Exception) { null }

    private class Bad : Exception()

    private fun str(o: JSONObject, key: String): String? {
        if (!o.has(key) || o.isNull(key)) return null
        return o.get(key) as? String ?: throw Bad()
    }

    /** A whole number. org.json would happily read "47473" as a number; this does not. */
    private fun whole(o: JSONObject, key: String): Long = when (val v = if (o.has(key)) o.get(key) else throw Bad()) {
        is Int -> v.toLong()
        is Long -> v
        else -> throw Bad()
    }

    /** A number from 0 to 1 inclusive. Strings, NaN and infinity are not numbers here. */
    private fun unit(o: JSONObject, key: String): Double {
        val d = when (val v = if (o.has(key)) o.get(key) else throw Bad()) {
            is Int -> v.toDouble()
            is Long -> v.toDouble()
            is Double -> v
            is Float -> v.toDouble()
            else -> throw Bad()
        }
        if (d.isNaN() || d < 0.0 || d > 1.0) throw Bad()
        return d
    }
}
