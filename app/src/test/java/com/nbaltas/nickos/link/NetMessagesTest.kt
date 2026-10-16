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

    @Test
    fun `a cleared hotspot is sent as forget so an offline node drops it on reconnect`() {
        assertTrue(NetMessages.decodeWifiCmd(wire(NetMessages.hotspotCmd(null)))!!.forget)
        val set = NetMessages.decodeWifiCmd(wire(NetMessages.hotspotCmd(com.nbaltas.nickos.data.Hotspot("CarSpot", "correct horse"))))!!
        assertEquals("CarSpot", set.ssid)
    }

    @Test
    fun `ask permission command round trips as an ordinary cmd`() {
        val m = wire(NetMessages.askPermissionCmd())
        assertEquals("cmd", m.getString("t"))
        assertEquals("askPerm", m.getString("c"))
        assertTrue(NetMessages.isAskPermission(m))
    }

    @Test
    fun `unknown or malformed commands are not an ask and never throw`() {
        assertFalse(NetMessages.isAskPermission(null))
        assertFalse(NetMessages.isAskPermission(JSONObject()))
        assertFalse(NetMessages.isAskPermission(JSONObject("""{"t":"cmd","c":"volume","v":3}""")))
        assertFalse(NetMessages.isAskPermission(JSONObject("""{"t":"status","c":"askPerm"}""")))
        assertFalse(NetMessages.isAskPermission(JSONObject("""{"t":"cmd","c":"somethingNewer"}""")))
    }

    @Test
    fun `orientation command round trips both ways`() {
        val on = wire(NetMessages.orientationCmd(true))
        assertEquals("cmd", on.getString("t"))
        assertEquals("orientation", on.getString("c"))
        assertEquals(true, NetMessages.decodeOrientation(on))
        assertEquals(false, NetMessages.decodeOrientation(wire(NetMessages.orientationCmd(false))))
    }

    @Test
    fun `orientation command with a missing or odd value is ignored`() {
        for (s in listOf("""{"t":"cmd","c":"orientation"}""", """{"t":"cmd","c":"orientation","landscape":"yes"}""",
            """{"t":"cmd","c":"orientation","landscape":1}""", """{"t":"cmd","c":"orientation","landscape":null}""",
            """{"t":"status","c":"orientation","landscape":true}""", """{"t":"cmd","c":"volume","landscape":true}""")) {
            assertNull(s, NetMessages.decodeOrientation(JSONObject(s)))
        }
        assertNull(NetMessages.decodeOrientation(null))
    }

    @Test
    fun `landscape status round trips and an old node reports none`() {
        for (st in listOf("on", "off", "no_permission")) {
            val s = NetMessages.LandscapeStatus(st != "off", st)
            val status = JSONObject().put("landscape", NetMessages.encodeLandscape(s))
            assertEquals(s, NetMessages.decodeLandscape(wire(status)))
        }
        assertTrue(NetMessages.LandscapeStatus(true, "no_permission").needsPermission)
        assertFalse(NetMessages.LandscapeStatus(true, "on").needsPermission)
        assertNull("old node", NetMessages.decodeLandscape(JSONObject("""{"t":"status"}""")))
        assertNull("unknown state", NetMessages.decodeLandscape(JSONObject("""{"landscape":{"lock":true,"state":"sideways"}}""")))
        assertNull("not an object", NetMessages.decodeLandscape(JSONObject("""{"landscape":true}""")))
    }
}
