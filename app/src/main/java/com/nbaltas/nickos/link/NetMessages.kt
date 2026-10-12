package com.nbaltas.nickos.link

import org.json.JSONArray
import org.json.JSONObject

/**
 * Messages for hands-off joining (capability "wifi1"):
 *  Master to Node  {"t":"cmd","c":"wifi","ssid","pass"}  save the car hotspot; empty ssid forgets it.
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
    data class WifiCmd(val ssid: String, val pass: String) { val forget get() = ssid.isEmpty() }
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

    fun wifiCmd(ssid: String, pass: String): JSONObject =
        JSONObject().put("t", "cmd").put("c", "wifi").put("ssid", ssid).put("pass", pass)

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
        return WifiCmd(ssid, pass)
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
