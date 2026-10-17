package com.nbaltas.nickos.link

import org.json.JSONArray
import org.json.JSONObject

/**
 * Messages for hands-off joining (capability "wifi1"):
 *  Master to Node  {"t":"cmd","c":"wifi","ssid","pass","gw"?}  save the car network; empty ssid forgets it. "gw" is
 *                  the Master's own address on that network, so the Node can tell it is on it.
 *  Master to Node  {"t":"who"} as the FIRST line gets {"t":"iam","nodeId","name"} and the socket closes.
 *  Node to Master  "wifi":{"saved","approve","do","doze"} inside "status", and "wifi1" inside "caps".
 *
 * The password crosses the LAN link in clear, exactly as the parent PIN already does: same trust model.
 * "who" is unauthenticated on purpose; the id is already broadcast by mDNS.
 */
object NetMessages {
    const val CAP = "wifi1"
    private val NODE_ID = Regex("^[0-9a-f]{12}$")

    data class Iam(val nodeId: String, val name: String)
    /** [gw] is the Master's own address on the network being described (192.168.49.1 for a Direct group), or null. */
    data class WifiCmd(val ssid: String, val pass: String, val gw: String? = null) { val forget get() = ssid.isEmpty() }
    data class WifiStatus(val saved: Boolean = false, val approve: Boolean = false, val deviceOwner: Boolean = false, val doze: Boolean = false)

    fun who(): JSONObject = JSONObject().put("t", "who")
    fun isWho(m: JSONObject?): Boolean = m?.optString("t") == "who"

    fun iam(nodeId: String, name: String): JSONObject = JSONObject().put("t", "iam").put("nodeId", nodeId).put("name", name)

    /** Null unless this is a well-formed iam carrying a plausible node id. */
    fun decodeIam(m: JSONObject?): Iam? {
        if (m == null || m.optString("t") != "iam") return null
        val id = m.optString("nodeId")
        if (!NODE_ID.matches(id)) return null
        return Iam(id, m.optString("name"))
    }

    fun decodeIamLine(line: String?): Iam? = try { decodeIam(line?.let { JSONObject(it) }) } catch (e: Exception) { null }

    fun wifiCmd(ssid: String, pass: String, gw: String? = null): JSONObject =
        JSONObject().put("t", "cmd").put("c", "wifi").put("ssid", ssid).put("pass", pass).apply { if (gw != null) put("gw", gw) }

    /**
     * What the Master sends on every connect: the saved network, or an empty ssid ("forget") when there is none.
     * [gw] lets a Node prove it is on that network: a Master link that arrives FROM [gw] means it is.
     */
    fun hotspotCmd(h: com.nbaltas.nickos.data.Hotspot?, gw: String? = null): JSONObject =
        wifiCmd(h?.ssid ?: "", h?.pass ?: "", if (h == null) null else gw)

    /**
     * Null for a command the Node must not act on: an SSID over 32 bytes, or a password that is neither
     * empty (open network) nor 8 to 63 characters (WPA2). Empty ssid means forget.
     */
    fun decodeWifiCmd(m: JSONObject): WifiCmd? {
        val ssid = m.optString("ssid")
        val pass = m.optString("pass")
        if (ssid.isEmpty()) return WifiCmd("", "")
        if (ssid.toByteArray(Charsets.UTF_8).size > 32) return null
        if (pass.isNotEmpty() && pass.length !in 8..63) return null
        // A gw that is not a plain dotted IPv4 address is dropped, not a reason to refuse the whole command.
        val gw = m.optString("gw").takeIf { LanAddress.octets(it) != null }
        return WifiCmd(ssid, pass, gw)
    }

    /**
     * Master to Node: "show your permission card now". A plain authenticated command like volume, so it rides the sealed
     * session. A Node that predates it falls through its command switch and ignores it.
     */
    const val ASK_PERMISSION = "askPerm"
    fun askPermissionCmd(): JSONObject = JSONObject().put("t", "cmd").put("c", ASK_PERMISSION)
    fun isAskPermission(m: JSONObject?): Boolean = m?.optString("t") == "cmd" && m.optString("c") == ASK_PERMISSION

    /**
     * Master to Node: {"t":"cmd","c":"orientation","landscape":true} locks the kid tablet to landscape (both directions),
     * false lets it turn freely again. The Node keeps the choice across reboots and answers in "status" with
     * "landscape":{"lock","state"}. A Node that predates it ignores the command and omits the field.
     */
    const val ORIENTATION = "orientation"
    fun orientationCmd(landscape: Boolean): JSONObject = JSONObject().put("t", "cmd").put("c", ORIENTATION).put("landscape", landscape)

    /** Null unless this is an orientation command that carries a real boolean (a missing one must not unlock by accident). */
    fun decodeOrientation(m: JSONObject?): Boolean? {
        if (m == null || m.optString("t") != "cmd" || m.optString("c") != ORIENTATION) return null
        if (m.opt("landscape") !is Boolean) return null
        return m.getBoolean("landscape")
    }

    /**
     * Master to Node: {"t":"cmd","c":"pauseAll","on":true,"msg":"Time to eat"} pauses the video and puts a wall with the
     * message on the kid tablet; on=false takes the wall away (the video is not resumed). Capability "pause1".
     * An old Node falls through the command and ignores it.
     */
    const val PAUSE_ALL = "pauseAll"
    const val PAUSE_CAP = "pause1"
    const val MAX_PAUSE_MSG = 120
    /** [force]: only the explicit "Resume everyone" button sets it; it lifts a pause another Master started. */
    data class PauseAll(val on: Boolean, val msg: String, val force: Boolean = false)
    fun pauseAllCmd(on: Boolean, msg: String, force: Boolean = false): JSONObject =
        JSONObject().put("t", "cmd").put("c", PAUSE_ALL).put("on", on).put("msg", msg.trim().take(MAX_PAUSE_MSG)).apply { if (force) put("force", true) }

    /** Null unless this is a pauseAll with a real boolean "on" (a missing one must not pause or resume by accident). */
    fun decodePauseAll(m: JSONObject?): PauseAll? {
        if (m == null || m.optString("t") != "cmd" || m.optString("c") != PAUSE_ALL) return null
        if (m.opt("on") !is Boolean) return null
        return PauseAll(m.getBoolean("on"), m.optString("msg").trim().take(MAX_PAUSE_MSG), m.opt("force") == true)
    }

    /**
     * Master to Node: {"t":"cmd","c":"ring","on":true} makes a lost kid tablet wake and sound its alarm until it is
     * stopped. The Node reports "ringing" in its status. Capability "ring1".
     */
    const val RING = "ring"
    const val RING_CAP = "ring1"
    fun ringCmd(on: Boolean): JSONObject = JSONObject().put("t", "cmd").put("c", RING).put("on", on)

    /** Null unless this is a ring command with a real boolean. */
    fun decodeRing(m: JSONObject?): Boolean? {
        if (m == null || m.optString("t") != "cmd" || m.optString("c") != RING) return null
        if (m.opt("on") !is Boolean) return null
        return m.getBoolean("on")
    }

    /**
     * What the parent asked for ([lock]) and what the Node manages to do ([state]: on, off, no_permission, or pending
     * when applying it or putting the old rotation back has not worked yet and is being tried again).
     */
    data class LandscapeStatus(val lock: Boolean, val state: String) {
        val needsPermission get() = state == STATE_NO_PERMISSION
        val pending get() = state == STATE_PENDING
    }
    const val STATE_ON = "on"
    const val STATE_OFF = "off"
    const val STATE_NO_PERMISSION = "no_permission"
    const val STATE_PENDING = "pending"

    fun encodeLandscape(s: LandscapeStatus): JSONObject = JSONObject().put("lock", s.lock).put("state", s.state)

    /** Null when the Node predates the feature and sent none: the Master hides the switch. */
    fun decodeLandscape(status: JSONObject): LandscapeStatus? {
        val o = status.optJSONObject("landscape") ?: return null
        val state = o.optString("state").takeIf { it == STATE_ON || it == STATE_OFF || it == STATE_NO_PERMISSION || it == STATE_PENDING } ?: return null
        return LandscapeStatus(o.optBoolean("lock"), state)
    }

    /**
     * Master to Node: {"t":"cmd","c":"power","wake":true,"offMin":5}. [wake] wakes the screen when power is plugged in;
     * [offMin] is the minutes of idle before the screen goes off on battery (0 is off). The Node answers in "status" with
     * "power":{"wake","offMin","state"} (state: on, off or no_permission). A Node that predates it omits the field.
     */
    const val POWER = "power"
    data class PowerCmd(val wake: Boolean, val offMin: Int)
    fun powerCmd(wake: Boolean, offMin: Int): JSONObject = JSONObject().put("t", "cmd").put("c", POWER).put("wake", wake).put("offMin", offMin)

    /** Null unless this is a power command with a real boolean and a whole number of minutes from 0 to 600. */
    fun decodePower(m: JSONObject?): PowerCmd? {
        if (m == null || m.optString("t") != "cmd" || m.optString("c") != POWER) return null
        if (m.opt("wake") !is Boolean || m.opt("offMin") !is Int) return null
        val min = m.getInt("offMin")
        if (min !in 0..600) return null
        return PowerCmd(m.getBoolean("wake"), min)
    }

    data class PowerStatus(val wake: Boolean, val offMin: Int, val state: String) {
        val needsPermission get() = state == STATE_NO_PERMISSION
    }

    fun encodePower(s: PowerStatus): JSONObject = JSONObject().put("wake", s.wake).put("offMin", s.offMin).put("state", s.state)

    /** Null when the Node predates the feature and sent none: the Master hides the controls. */
    fun decodePowerStatus(status: JSONObject): PowerStatus? {
        val o = status.optJSONObject("power") ?: return null
        val state = o.optString("state").takeIf { it == STATE_ON || it == STATE_OFF || it == STATE_NO_PERMISSION } ?: return null
        return PowerStatus(o.optBoolean("wake"), o.optInt("offMin"), state)
    }

    fun encodeWifi(s: WifiStatus): JSONObject =
        JSONObject().put("saved", s.saved).put("approve", s.approve).put("do", s.deviceOwner).put("doze", s.doze)

    /** All false when the Node predates "wifi1" and sent none. */
    fun decodeWifi(status: JSONObject): WifiStatus {
        val o = status.optJSONObject("wifi") ?: return WifiStatus()
        return WifiStatus(o.optBoolean("saved"), o.optBoolean("approve"), o.optBoolean("do"), o.optBoolean("doze"))
    }

    fun supportsWifi(status: JSONObject): Boolean {
        val a: JSONArray = status.optJSONArray("caps") ?: return false
        return (0 until a.length()).any { a.optString(it) == CAP }
    }
}
