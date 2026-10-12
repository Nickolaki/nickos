package com.nbaltas.nickos.link

import org.json.JSONObject
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The "sec2" link handshake and pairing, over a [Conn] so tests can drive both ends with two sockets on localhost.
 *
 * hello (Master dials Node), token T is the 32-hex pre-shared key minted at pairing, never sent again:
 *   M -> N  {hello, v:2, masterId, masterName, nm}                      nm = 16 random bytes
 *   N -> M  {challenge, nn, mac = HMAC(T, "n"|nm|nn)}                    the Node proves it holds T first
 *   M -> N  {auth, mac = HMAC(T, "m"|nm|nn)}                             only then does the Master answer
 *   both    K = HMAC(T, "k"|nm|nn); every later line is AES-GCM sealed under K (see [Seal])
 *   N -> M  {welcome}  (sealed)
 * A stranger who answers as the Node cannot produce the first mac, so the Master drops it having revealed nothing.
 *
 * pair (the parent types the 6-digit code C shown on the kid tablet), ECDH P-256 authenticated by C:
 *   M -> N  {pair, v:2, masterId, masterName, pub = Pm, mac = HMAC(C, "m"|Pm)}      no PIN, no token
 *   N -> M  {paired, v:2, pub = Pn, mac = HMAC(C, "n"|Pn|Pm), nodeId, ...status}
 *   both    T = first 16 bytes of HMAC(ECDH(Pm,Pn), "token"|Pm|Pn), as hex
 * A passive sniffer sees only public keys and macs, so it does not learn T. Residual (documented in the audit): an
 * ACTIVE attacker inside the pairing window can brute-force the 6-digit code offline from the macs.
 */
object Handshake {
    /** Reported in the Node's status "caps" so a new Master can see it. */
    const val CAP = "sec2"
    /** The unauthenticated reads (hello, auth, challenge, welcome) must finish inside this. */
    const val PRE_AUTH_MS = 10_000L
    private const val PAIR_REPLY_MAX = 64 * 1024

    val ID_RE = Regex("^[0-9a-f]{12}$")

    private val rng = SecureRandom()
    private fun rand(n: Int) = ByteArray(n).also { rng.nextBytes(it) }
    private fun s(x: String) = x.toByteArray(Charsets.UTF_8)
    private fun same(a: ByteArray, b: ByteArray) = MessageDigest.isEqual(a, b)

    enum class Hello { OK, DENIED, FAIL }

    /** Master side. [Hello.DENIED] means the far end said so (an old Node does); [Hello.FAIL] means it could not prove itself. */
    fun helloResult(c: Conn, masterId: String, masterName: String, token: String): Hello {
        val t = s(token)
        val nm = rand(16)
        val sent = c.send(JSONObject().put("t", "hello").put("v", 2).put("masterId", masterId).put("masterName", masterName).put("nm", B64.enc(nm)))
        if (!sent) return Hello.FAIL
        val ch = c.read(Conn.FIRST_LINE_MAX, PRE_AUTH_MS) ?: return Hello.FAIL
        if (ch.optString("t") == "denied") { c.close(); return Hello.DENIED }
        val nn = B64.dec(ch.optString("nn"))
        val mac = B64.dec(ch.optString("mac"))
        if (ch.optString("t") != "challenge" || nn == null || nn.size != 16 || mac == null || !same(mac, hmac(t, s("n"), nm, nn))) {
            c.close(); return Hello.FAIL
        }
        if (!c.send(JSONObject().put("t", "auth").put("mac", B64.enc(hmac(t, s("m"), nm, nn))))) return Hello.FAIL
        c.seal(Seal(hmac(t, s("k"), nm, nn), "m2n", "n2m"))
        val w = c.read(Conn.FIRST_LINE_MAX, PRE_AUTH_MS) ?: return Hello.FAIL
        if (w.optString("t") != "welcome") { c.close(); return Hello.FAIL }
        return Hello.OK
    }

    fun hello(c: Conn, masterId: String, masterName: String, token: String): Boolean =
        helloResult(c, masterId, masterName, token) == Hello.OK

    /** The v1 hello (token in clear). Only for a Node that has never completed a sec2 handshake with us. */
    fun legacyHello(c: Conn, masterId: String, masterName: String, token: String): Boolean {
        if (!c.send(JSONObject().put("t", "hello").put("masterId", masterId).put("token", token).put("masterName", masterName))) return false
        return c.read(Conn.FIRST_LINE_MAX, PRE_AUTH_MS)?.optString("t") == "welcome"
    }

    /**
     * Node side of a v2 hello that has already been read as [first]. Returns the masterId on success; on failure it
     * has answered `denied` and closed. [lookup] maps a masterId to its token.
     */
    fun serve(c: Conn, first: JSONObject, lookup: (String) -> String?): String? {
        val mid = first.optString("masterId")
        val token = lookup(mid)
        val nm = B64.dec(first.optString("nm"))
        if (token == null || nm == null || nm.size != 16) return deny(c)
        val t = s(token)
        val nn = rand(16)
        if (!c.send(JSONObject().put("t", "challenge").put("nn", B64.enc(nn)).put("mac", B64.enc(hmac(t, s("n"), nm, nn))))) return null
        val auth = c.read(Conn.FIRST_LINE_MAX, PRE_AUTH_MS) ?: return null
        val mac = B64.dec(auth.optString("mac"))
        if (auth.optString("t") != "auth" || mac == null || !same(mac, hmac(t, s("m"), nm, nn))) return deny(c)
        c.seal(Seal(hmac(t, s("k"), nm, nn), "n2m", "m2n"))
        if (!c.send(JSONObject().put("t", "welcome"))) return null
        return mid
    }

    /**
     * The Node's whole answer to a first line of `hello`: v2 goes to [serve]; a v1 hello (token in clear) is honoured
     * only while [flags] says this Master has never done sec2 (the ratchet). Returns the masterId or null (denied).
     */
    fun nodeHello(c: Conn, first: JSONObject, lookup: (String) -> String?, flags: SecFlags): String? {
        val mid = first.optString("masterId")
        if (first.optInt("v", 1) >= 2) {
            val id = serve(c, first, lookup) ?: return null
            flags.setSec2(id, true)
            return id
        }
        val token = lookup(mid)
        if (token == null || flags.sec2(mid) || !same(s(first.optString("token")), s(token))) return deny(c)
        if (!c.send(JSONObject().put("t", "welcome"))) return null
        return mid
    }

    /**
     * May the Master answer a `denied` with the clear-token v1 hello? Only with positive evidence the Node is
     * pre-sec2: it has never completed a sec2 handshake with us AND has never reported the sec2 capability. Anything
     * else means `denied` came from a rogue (or a real refusal), and the token stays put.
     */
    fun mayFallBack(flags: SecFlags, nodeId: String): Boolean = !flags.sec2(nodeId) && !flags.sec2Capable(nodeId)

    private fun deny(c: Conn): String? {
        c.send(JSONObject().put("t", "denied"))
        c.close()
        return null
    }

    class PairOutcome(val token: String, val nodeId: String, val reply: JSONObject)

    /**
     * Master side of pairing. Null when the Node's mac does not verify (it does not know the code), when its nodeId is
     * malformed, or when [expectNodeId] is given and differs (a tablet claiming another's identity).
     */
    fun pair(c: Conn, masterId: String, masterName: String, code: String, expectNodeId: String = ""): PairOutcome? {
        val kp = Ecdh.keyPair()
        val pubM = Ecdh.encode(kp.public)
        val cb = s(code)
        val sent = c.send(JSONObject().put("t", "pair").put("v", 2).put("masterId", masterId).put("masterName", masterName)
            .put("pub", B64.enc(pubM)).put("mac", B64.enc(hmac(cb, s("m"), pubM))))
        if (!sent) return null
        val r = c.read(PAIR_REPLY_MAX, PRE_AUTH_MS) ?: return null
        if (r.optString("t") != "paired") return null
        val pubN = B64.dec(r.optString("pub")) ?: return null
        val mac = B64.dec(r.optString("mac")) ?: return null
        if (!same(mac, hmac(cb, s("n"), pubN, pubM))) return null
        val nodeId = r.optString("nodeId")
        if (!ID_RE.matches(nodeId) || (expectNodeId.isNotEmpty() && nodeId != expectNodeId)) return null
        val token = try { tokenOf(Ecdh.shared(kp.private, Ecdh.decode(pubN)), pubM, pubN) } catch (e: Exception) { return null }
        return PairOutcome(token, nodeId, r)
    }

    private fun tokenOf(shared: ByteArray, pubM: ByteArray, pubN: ByteArray): String =
        B64.hex(hmac(shared, s("token"), pubM, pubN).copyOf(16))

    /**
     * Node side of a v2 `pair` line. [status] builds the status object the reply carries (the old reply's shape,
     * without a token); [onPaired] stores the new Master and runs BEFORE the reply goes out and AFTER the code has been
     * used up, so two Masters racing on one code cannot both win. Returns the masterId, or null after `pairFail`.
     * A wrong code or mac is counted on [gate] and answered after [failDelayMs] (that delay is the rate limit).
     */
    fun servePair(
        c: Conn, first: JSONObject, gate: PairGate, status: () -> JSONObject,
        onPaired: (masterId: String, masterName: String, token: String) -> Unit,
        failDelayMs: Long = 1000, nodeId: String,
    ): String? {
        val code = gate.code() ?: return refuse(c)
        val mid = first.optString("masterId")
        val pubM = B64.dec(first.optString("pub"))
        val mac = B64.dec(first.optString("mac"))
        val peer = try { if (pubM != null) Ecdh.decode(pubM) else null } catch (e: Exception) { null }
        val ok = ID_RE.matches(mid) && pubM != null && mac != null && peer != null && same(mac, hmac(s(code), s("m"), pubM))
        if (!ok) {
            gate.fail()
            if (failDelayMs > 0) try { Thread.sleep(failDelayMs) } catch (_: InterruptedException) {}
            return refuse(c)
        }
        if (!gate.consume()) return refuse(c)
        val kp = Ecdh.keyPair()
        val pubN = Ecdh.encode(kp.public)
        val token = tokenOf(Ecdh.shared(kp.private, peer!!), pubM!!, pubN)
        onPaired(mid, first.optString("masterName", "Parent's Tablet"), token)
        val reply = status().put("t", "paired").put("v", 2).put("pub", B64.enc(pubN))
            .put("mac", B64.enc(hmac(s(code), s("n"), pubN, pubM))).put("nodeId", nodeId)
        reply.remove("token")
        val sent = c.send(reply)
        // A pairing connection never becomes a command session (it has no Seal): the Master reconnects with a hello.
        c.close()
        return if (sent) mid else null
    }

    /** `pairFail` and close. Also what the legacy pair path answers. */
    fun refuse(c: Conn): String? {
        c.send(JSONObject().put("t", "pairFail"))
        c.close()
        return null
    }
}

/**
 * The pairing window: one 6-digit code, live for [TTL_MS], dead after [MAX_FAILS] wrong attempts, and usable once.
 * [onChange] is told the code (on open) or null (on close, expiry, use) so the UI's StateFlow follows.
 */
class PairGate(
    private val now: () -> Long = System::currentTimeMillis,
    private val onChange: (String?) -> Unit = {},
) {
    private var code: String? = null
    private var until = 0L
    private var fails = 0

    @Synchronized
    fun open(): String {
        val c = (SecureRandom().nextInt(900000) + 100000).toString()
        code = c; until = now() + TTL_MS; fails = 0
        onChange(c)
        return c
    }

    /** The live code, or null when none is open or it has expired (which also clears it). */
    @Synchronized
    fun code(): String? {
        if (code != null && now() > until) clear()
        return code
    }

    /** Counts one wrong attempt; the fifth closes pairing. Returns the count. */
    @Synchronized
    fun fail(): Int {
        if (code == null) return fails
        fails++
        if (fails >= MAX_FAILS) clear()
        return fails
    }

    /** Takes the code for one successful pairing. True for exactly one caller. */
    @Synchronized
    fun consume(): Boolean {
        if (code() == null) return false
        clear()
        return true
    }

    @Synchronized fun close() { if (code != null) clear() }

    /** For the timer that makes a stale code vanish from the screen. */
    @Synchronized fun expireIfDue() { code() }

    private fun clear() { code = null; onChange(null) }

    companion object {
        const val TTL_MS = 120_000L
        const val MAX_FAILS = 5
    }
}

/**
 * Bounds the connections that have not authenticated yet: at most [max] at once, the rest are closed on arrival.
 * Each accepted socket also gets a 10 s read timeout, so a silent peer cannot hold a slot.
 */
class AcceptGate(max: Int = 8, private val soTimeoutMs: Int = 10_000) {
    private val slots = Semaphore(max)

    class Ticket internal constructor(private val slots: Semaphore) {
        private val done = AtomicBoolean(false)
        /** Idempotent. */
        fun release() { if (done.compareAndSet(false, true)) slots.release() }
    }

    fun enter(): Ticket? = if (slots.tryAcquire()) Ticket(slots) else null

    /** Blocks accepting on [ss] until it closes or [alive] turns false; [handler] runs on its own thread per connection. */
    fun run(ss: ServerSocket, alive: () -> Boolean, handler: (Socket, Ticket) -> Unit) {
        while (alive() && !ss.isClosed) {
            val sock = try { ss.accept() } catch (e: Exception) { break }
            val t = enter()
            if (t == null) { try { sock.close() } catch (_: Exception) {}; continue }
            try { sock.soTimeout = soTimeoutMs } catch (_: Exception) {}
            Thread { try { handler(sock, t) } finally { t.release() } }.start()
        }
    }
}
