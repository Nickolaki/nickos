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

    /** What the parent asked for ([lock]) and what the Node manages to do ([state]: on, off or no_permission). */
    data class LandscapeStatus(val lock: Boolean, val state: String) {
        val needsPermission get() = state == STATE_NO_PERMISSION
    }
    const val STATE_ON = "on"
    const val STATE_OFF = "off"
    const val STATE_NO_PERMISSION = "no_permission"

    fun encodeLandscape(s: LandscapeStatus): JSONObject = JSONObject().put("lock", s.lock).put("state", s.state)

    /** Null when the Node predates the feature and sent none: the Master hides the switch. */
    fun decodeLandscape(status: JSONObject): LandscapeStatus? {
        val o = status.optJSONObject("landscape") ?: return null
        val state = o.optString("state").takeIf { it == STATE_ON || it == STATE_OFF || it == STATE_NO_PERMISSION } ?: return null
        return LandscapeStatus(o.optBoolean("lock"), state)
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
