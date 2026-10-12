package com.nbaltas.nickos.link

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NetMessagesTest {
    private fun wire(o: JSONObject) = JSONObject(o.toString())

    @Test
    fun `who and iam round trip`() {
        assertTrue(NetMessages.isWho(wire(NetMessages.who())))
        val iam = NetMessages.decodeIam(wire(NetMessages.iam("0123456789ab", "Hailey's Tablet")))
        assertEquals(NetMessages.Iam("0123456789ab", "Hailey's Tablet"), iam)
    }

    @Test
    fun `a stranger's reply is rejected`() {
        for (line in listOf(null, "", "garbage", "[]", "{}", """{"t":"welcome"}""", """{"t":"iam"}""", """{"t":"iam","nodeId":""}""",
            """{"t":"iam","nodeId":"NOT-A-NODE-ID"}""", """{"t":"iam","nodeId":"0123456789abcdef0123"}""", """{"t":"iam","nodeId":"0123456789AB"}""")) {
            assertNull(line, NetMessages.decodeIamLine(line))
        }
        assertEquals("0123456789ab", NetMessages.decodeIamLine("""{"t":"iam","nodeId":"0123456789ab"}""")?.nodeId)
    }

    @Test
    fun `wifi command round trips and forgets on an empty ssid`() {
        val cmd = wire(NetMessages.wifiCmd("CarSpot", "correct horse"))
        assertEquals("cmd", cmd.getString("t"))
        assertEquals("wifi", cmd.getString("c"))
        assertEquals(NetMessages.WifiCmd("CarSpot", "correct horse"), NetMessages.decodeWifiCmd(cmd))
        val forget = NetMessages.decodeWifiCmd(wire(NetMessages.wifiCmd("", "")))!!
        assertTrue(forget.forget)
    }

    @Test
    fun `wifi command with an impossible ssid or password is refused`() {
        assertNull(NetMessages.decodeWifiCmd(wire(NetMessages.wifiCmd("x".repeat(33), "password1"))))
        assertNull(NetMessages.decodeWifiCmd(wire(NetMessages.wifiCmd("Ok", "short"))))
        assertNull(NetMessages.decodeWifiCmd(wire(NetMessages.wifiCmd("Ok", "p".repeat(64)))))
        // an open network (no password) is allowed
        assertEquals(NetMessages.WifiCmd("Ok", ""), NetMessages.decodeWifiCmd(wire(NetMessages.wifiCmd("Ok", ""))))
    }

    @Test
    fun `status wifi round trips`() {
        val s = NetMessages.WifiStatus(saved = true, approve = false, deviceOwner = true, doze = true)
        val status = JSONObject().put("t", "status").put("wifi", NetMessages.encodeWifi(s)).put("caps", JSONArray(listOf("sync1", NetMessages.CAP)))
        val back = wire(status)
        assertEquals(s, NetMessages.decodeWifi(back))
        assertTrue(NetMessages.supportsWifi(back))
    }

    @Test
    fun `status from an old Node decodes with defaults`() {
        val old = JSONObject().put("t", "status").put("caps", JSONArray(listOf("sync1")))
        assertEquals(NetMessages.WifiStatus(), NetMessages.decodeWifi(old))
        assertFalse(NetMessages.supportsWifi(old))
        assertFalse(NetMessages.supportsWifi(JSONObject().put("t", "status")))
    }
}
