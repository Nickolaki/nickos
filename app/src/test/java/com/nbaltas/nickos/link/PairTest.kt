package com.nbaltas.nickos.link

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PairTest {
    private val masterId = "0123456789ab"
    private val nodeId = "ba9876543210"

    private class Paired(val id: String, val name: String, val token: String)

    private class Round(val outcome: Handshake.PairOutcome?, val served: String?, val stored: Paired?, val link: Link)

    /** One pairing attempt: the Node serves [gate], the Master types [typed] and expects [expectId]. */
    private fun attempt(gate: PairGate, typed: String, expectId: String = nodeId): Round {
        val l = Link()
        var stored: Paired? = null
        val served = l.onNode {
            val first = l.node.read(Conn.FIRST_LINE_MAX, 5000)!!
            Handshake.servePair(l.node, first, gate, { JSONObject().put("t", "status").put("name", "Kid") },
                { id, name, tok -> stored = Paired(id, name, tok) }, failDelayMs = 0, nodeId = nodeId)
        }
        val out = Handshake.pair(l.master, masterId, "Dad", typed, expectId)
        val s = served.get(5, TimeUnit.SECONDS)
        return Round(out, s, stored, l)
    }

    @Test
    fun `a valid code gives both sides the same token and the wire carries neither the token nor a pin`() {
        val gate = PairGate()
        val code = gate.open()
        val r = attempt(gate, code)
        r.link.use { l ->
            assertNotNull(r.outcome)
            assertEquals(masterId, r.served)
            assertEquals(r.outcome!!.token, r.stored!!.token)
            assertEquals(32, r.outcome.token.length)
            assertEquals(nodeId, r.outcome.nodeId)
            assertEquals("Dad", r.stored.name)
            assertNull("the code is used up", gate.code())
            for (raw in listOf(l.rawToNode(), l.rawToMaster())) {
                assertFalse(raw.contains(r.outcome.token))
                assertFalse(raw.contains("\"pin\""))
                assertFalse(raw.contains("\"token\""))
            }
        }
    }

    @Test
    fun `a wrong code is pairFail and counted`() {
        val gate = PairGate()
        gate.open()
        val r = attempt(gate, "000000")
        r.link.use {
            assertNull(r.outcome)
            assertNull(r.served)
            assertNull(r.stored)
            assertNotNull("still open after one miss", gate.code())
        }
    }

    @Test
    fun `no open code is pairFail`() {
        val gate = PairGate()
        val r = attempt(gate, "123456")
        r.link.use { assertNull(r.outcome); assertNull(r.stored) }
    }

    @Test
    fun `a paired reply with a bad mac is rejected by the Master`() {
        Link().use { l ->
            l.onNode {
                l.node.read(Conn.FIRST_LINE_MAX, 5000)
                val kp = Ecdh.keyPair()
                l.node.send(JSONObject().put("t", "paired").put("v", 2).put("pub", B64.enc(Ecdh.encode(kp.public)))
                    .put("mac", B64.enc(ByteArray(32))).put("nodeId", nodeId))
            }
            assertNull(Handshake.pair(l.master, masterId, "Dad", "123456", nodeId))
        }
    }

    @Test
    fun `a rogue that learns nothing cannot fake a paired reply`() {
        // A tablet that answers "paired" to any code (the old attack) now also needs HMAC(code, ...): it has no code.
        Link().use { l ->
            l.onNode {
                val first = l.node.read(Conn.FIRST_LINE_MAX, 5000)!!
                val pubM = B64.dec(first.optString("pub"))!!
                val kp = Ecdh.keyPair()
                val pubN = Ecdh.encode(kp.public)
                val guess = "111111".toByteArray()
                l.node.send(JSONObject().put("t", "paired").put("v", 2).put("pub", B64.enc(pubN))
                    .put("mac", B64.enc(hmac(guess, "n".toByteArray(), pubN, pubM))).put("nodeId", nodeId))
            }
            assertNull(Handshake.pair(l.master, masterId, "Dad", "654321", nodeId))
        }
    }

    @Test
    fun `a nodeId that differs from the dialed id is rejected`() {
        val gate = PairGate()
        val code = gate.open()
        val r = attempt(gate, code, expectId = "ffffffffffff")
        r.link.use { assertNull(r.outcome) }
    }

    @Test
    fun `a malformed nodeId is rejected`() {
        for (bad in listOf("", "XYZ", "BA9876543210", "ba98765432100")) {
            Link().use { l ->
                val gate = PairGate(); val code = gate.open()
                l.onNode {
                    val first = l.node.read(Conn.FIRST_LINE_MAX, 5000)!!
                    Handshake.servePair(l.node, first, gate, { JSONObject() }, { _, _, _ -> }, failDelayMs = 0, nodeId = bad)
                }
                assertNull(bad, Handshake.pair(l.master, masterId, "Dad", code))
            }
        }
    }

    @Test
    fun `five wrong tries close pairing`() {
        var shown: String? = "unset"
        val gate = PairGate(onChange = { shown = it })
        val code = gate.open()
        assertEquals(code, shown)
        repeat(5) { attempt(gate, "000000").link.close() }
        assertNull(gate.code())
        assertNull("the UI code is cleared", shown)
        // And now even the right code fails.
        val r = attempt(gate, code)
        r.link.use { assertNull(r.outcome) }
    }

    @Test
    fun `four wrong tries then the right code still works`() {
        val gate = PairGate()
        val code = gate.open()
        repeat(4) { attempt(gate, "000000").link.close() }
        val r = attempt(gate, code)
        r.link.use { assertNotNull(r.outcome) }
    }

    @Test
    fun `an attempt after the deadline gets pairFail and the code is gone`() {
        var now = 1_000_000L
        var shown: String? = null
        val gate = PairGate(now = { now }, onChange = { shown = it })
        val code = gate.open()
        now += PairGate.TTL_MS + 1
        val r = attempt(gate, code)
        r.link.use { assertNull(r.outcome); assertNull(r.stored) }
        assertNull(shown)
    }

    @Test
    fun `an attempt just inside the deadline works`() {
        var now = 1_000_000L
        val gate = PairGate(now = { now })
        val code = gate.open()
        now += PairGate.TTL_MS
        val r = attempt(gate, code)
        r.link.use { assertNotNull(r.outcome) }
    }

    @Test
    fun `a second valid pair after the first succeeded gets pairFail`() {
        val gate = PairGate()
        val code = gate.open()
        attempt(gate, code).link.close()
        val second = attempt(gate, code)
        second.link.use { assertNull(second.outcome); assertNull(second.stored) }
    }

    @Test
    fun `a masterId that is not 12 hex is refused`() {
        Link().use { l ->
            val gate = PairGate(); val code = gate.open()
            val served = l.onNode {
                val first = l.node.read(Conn.FIRST_LINE_MAX, 5000)!!
                Handshake.servePair(l.node, first, gate, { JSONObject() }, { _, _, _ -> throw AssertionError("stored") }, failDelayMs = 0, nodeId = nodeId)
            }
            assertNull(Handshake.pair(l.master, "../../evil", "Dad", code))
            assertNull(served.get(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `the gate hands the code out once even when two callers race`() {
        val gate = PairGate()
        gate.open()
        val wins = java.util.concurrent.atomic.AtomicInteger()
        val ts = (1..8).map { Thread { if (gate.consume()) wins.incrementAndGet() }.also { it.start() } }
        ts.forEach { it.join() }
        assertEquals(1, wins.get())
        assertTrue(gate.code() == null)
    }
}
