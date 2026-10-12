package com.nbaltas.nickos.link

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HandshakeTest {
    private val mid = "0123456789ab"
    private val token = "00112233445566778899aabbccddeeff"

    private fun nodeServe(l: Link, tokens: Map<String, String>, flags: SecFlags = MemFlags()) = l.onNode {
        val first = l.node.read(Conn.FIRST_LINE_MAX, 5000)!!
        Handshake.nodeHello(l.node, first, { tokens[it] }, flags)
    }

    @Test
    fun `right token seals both sides and a command round trips`() {
        Link().use { l ->
            val flags = MemFlags()
            val served = l.onNode {
                val id = Handshake.nodeHello(l.node, l.node.read(Conn.FIRST_LINE_MAX, 5000)!!, { if (it == mid) token else null }, flags)
                val cmd = l.node.read()
                l.node.send(JSONObject().put("t", "echo").put("c", cmd?.optString("c")))
                id
            }
            assertTrue(Handshake.hello(l.master, mid, "Dad", token))
            assertTrue(l.master.send(JSONObject().put("t", "cmd").put("c", "volume")))
            assertEquals("volume", l.master.read()!!.optString("c"))
            assertEquals(mid, served.get(5, TimeUnit.SECONDS))
            assertTrue("the ratchet is set", flags.sec2(mid))
            // The sniffer never sees the token or the command in clear.
            assertFalse(l.rawToNode().contains(token))
            assertFalse(l.rawToNode().contains("volume"))
            assertFalse(l.rawToMaster().contains("echo"))
            assertFalse(l.rawToMaster().contains("welcome"))
        }
    }

    @Test
    fun `a Node with the wrong token is dropped and the Master never sent the token`() {
        Link().use { l ->
            val other = "ffeeddccbbaa99887766554433221100"
            val served = nodeServe(l, mapOf(mid to other))
            assertFalse(Handshake.hello(l.master, mid, "Dad", token))
            assertNull(served.get(5, TimeUnit.SECONDS))
            assertFalse(l.master.isOpen)
            val seen = l.rawToNode()
            assertFalse(seen.contains(token))
            assertFalse(seen.contains(other))
            assertFalse("no auth line was sent", seen.contains("\"auth\""))
        }
    }

    @Test
    fun `a rogue that answers any challenge with a made up mac gets nothing`() {
        Link().use { l ->
            l.onNode {
                l.node.read(Conn.FIRST_LINE_MAX, 5000)
                l.node.send(JSONObject().put("t", "challenge").put("nn", B64.enc(ByteArray(16))).put("mac", B64.enc(ByteArray(32))))
                l.node.read(Conn.FIRST_LINE_MAX, 1000)
            }
            assertEquals(Handshake.Hello.FAIL, Handshake.helloResult(l.master, mid, "Dad", token))
            assertFalse(l.rawToNode().contains(token))
            assertFalse(l.rawToNode().contains("\"auth\""))
        }
    }

    @Test
    fun `an unknown masterId is denied`() {
        Link().use { l ->
            val served = nodeServe(l, mapOf("aaaaaaaaaaaa" to token))
            assertEquals(Handshake.Hello.DENIED, Handshake.helloResult(l.master, mid, "Dad", token))
            assertNull(served.get(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `a Master with the wrong token is denied after the challenge`() {
        Link().use { l ->
            val served = nodeServe(l, mapOf(mid to token))
            // The Master side never verifies the Node's mac as right, so it fails first: use a raw client instead.
            val nm = ByteArray(16) { 1 }
            l.master.send(JSONObject().put("t", "hello").put("v", 2).put("masterId", mid).put("masterName", "x").put("nm", B64.enc(nm)))
            val ch = l.master.read(Conn.FIRST_LINE_MAX, 5000)!!
            assertEquals("challenge", ch.optString("t"))
            l.master.send(JSONObject().put("t", "auth").put("mac", B64.enc(ByteArray(32))))
            assertEquals("denied", l.master.read(Conn.FIRST_LINE_MAX, 5000)!!.optString("t"))
            assertNull(served.get(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `legacy hello is accepted while the flag is false and denied once it is true`() {
        for ((flag, expect) in listOf(false to mid, true to null)) {
            Link().use { l ->
                val flags = MemFlags().also { it.setSec2(mid, flag) }
                val served = nodeServe(l, mapOf(mid to token), flags)
                assertEquals(!flag, Handshake.legacyHello(l.master, mid, "Dad", token))
                assertEquals(expect, served.get(5, TimeUnit.SECONDS))
                assertEquals("flag stays $flag", flag, flags.sec2(mid))
            }
        }
    }

    @Test
    fun `legacy hello with a wrong token is denied`() {
        Link().use { l ->
            val served = nodeServe(l, mapOf(mid to token))
            assertFalse(Handshake.legacyHello(l.master, mid, "Dad", "0".repeat(32)))
            assertNull(served.get(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `a v2 hello with a malformed nonce is denied`() {
        Link().use { l ->
            val served = nodeServe(l, mapOf(mid to token))
            l.master.send(JSONObject().put("t", "hello").put("v", 2).put("masterId", mid).put("nm", "AAAA"))
            assertEquals("denied", l.master.read(Conn.FIRST_LINE_MAX, 5000)!!.optString("t"))
            assertNull(served.get(5, TimeUnit.SECONDS))
        }
    }
}
