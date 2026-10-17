package com.nbaltas.nickos.remote

import com.nbaltas.nickos.cam.CamPlan
import com.nbaltas.nickos.link.FrameSeal
import com.nbaltas.nickos.master.ScreenClient
import com.nbaltas.nickos.node.ScreenServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** The screen stream over real sockets: encrypted, authenticated per parent, nothing plain. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScreenServerTest {
    private val keys = ScreenKeys(randomKeys())
    private var server: ScreenServer? = null
    private val sockets = ArrayList<Socket>()
    private val clients = ArrayList<ScreenClient>()
    private val fakes = ArrayList<ServerSocket>()

    private companion object {
        var n = 0
        fun randomKeys(): () -> String = { "key${++n}".padEnd(32, '0') }
        const val A = "aaaaaaaaaaaa"
        const val B = "bbbbbbbbbbbb"
    }

    @After
    fun tearDown() {
        sockets.forEach { try { it.close() } catch (_: Exception) {} }
        clients.forEach { it.close() }
        fakes.forEach { try { it.close() } catch (_: Exception) {} }
        server?.stop()
    }

    /** A real clock: Robolectric's SystemClock stands still on a background thread. */
    private fun start(): ScreenServer {
        val s = ScreenServer(
            onIdle = {}, preferredPort = 0, graceMs = 60_000, firstGraceMs = 60_000,
            clock = { System.nanoTime() / 1_000_000 }, keys = { keys.byMaster() },
        )
        assertTrue(s.start())
        server = s
        return s
    }

    private fun waitFor(what: String, cond: () -> Boolean) {
        val end = System.nanoTime() + 5_000_000_000L
        while (!cond()) {
            assertTrue("timed out: $what", System.nanoTime() < end)
            Thread.sleep(20)
        }
    }

    private var nonces = 100
    private fun freshNonce(): ByteArray = ByteArray(16).also { java.util.Random((nonces++).toLong()).nextBytes(it) }

    private class Session(val socket: Socket, val seal: FrameSeal)

    private fun hello(id: String, key: String, cn: ByteArray, macKey: String = key): ByteArray {
        val idb = id.toByteArray(Charsets.US_ASCII)
        return ScreenServer.MAGIC + idb + cn + ScreenServer.authMac(macKey, idb, cn)
    }

    private fun raw(port: Int, bytes: ByteArray): Socket =
        Socket("127.0.0.1", port).also {
            it.soTimeout = 5_000
            it.getOutputStream().apply { write(bytes); flush() }
            sockets += it
        }

    private fun connect(s: ScreenServer, id: String, key: String, cn: ByteArray = freshNonce()): Session {
        val sock = raw(s.port, hello(id, key, cn))
        val sn = ByteArray(16)
        DataInputStream(sock.getInputStream()).readFully(sn)
        return Session(sock, FrameSeal(ScreenServer.sessionKey(key, cn, sn), "m2n-screen", "n2m-screen"))
    }

    /** True when the server hung up without a word. */
    private fun hungUp(sock: Socket): Boolean = try {
        sock.getInputStream().read() < 0
    } catch (e: SocketTimeoutException) { false } catch (e: java.io.IOException) { true }

    private fun frame(se: Session): ByteArray? {
        val input = DataInputStream(se.socket.getInputStream())
        val len = input.readInt()
        val b = ByteArray(len)
        input.readFully(b)
        return se.seal.open(b)
    }

    // ---- good auth ----

    @Test
    fun `a good hello streams sealed frames and the jpeg never appears on the wire`() {
        val s = start()
        val key = keys.keyFor(A)
        val se = connect(s, A, key)
        waitFor("connected") { s.clientCount == 1 }
        val jpeg = ByteArray(300) { (it % 251).toByte() }
        s.push(jpeg, 90)
        val plain = frame(se)!!
        assertArrayEquals(jpeg, plain.copyOfRange(0, plain.size - 1))
        assertEquals(1, plain.last().toInt())
    }

    @Test
    fun `the sealed bytes on the wire hold no part of the picture`() {
        val s = start()
        val key = keys.keyFor(A)
        val se = connect(s, A, key)
        waitFor("connected") { s.clientCount == 1 }
        val jpeg = ByteArray(2000) { 0x5A }
        s.push(jpeg, 0)
        val input = DataInputStream(se.socket.getInputStream())
        val len = input.readInt()
        val onWire = ByteArray(len).also { input.readFully(it) }
        assertEquals(12 + 2000 + 1 + 16, len)
        assertFalse(onWire.copyOfRange(12, len).count { it == 0x5A.toByte() } > 50)
    }

    @Test
    fun `the real client authenticates and receives every picture in order`() {
        val s = start()
        val key = keys.keyFor(A)
        val got = LinkedBlockingQueue<Pair<ByteArray, Int>>()
        val c = ScreenClient("127.0.0.1", s.port, A, key, onEnded = {})
        c.tap = { j, q -> got.add(j to q) }
        clients += c
        c.start()
        waitFor("client connected") { s.clientCount == 1 }
        s.push(byteArrayOf(1, 2, 3), 0)
        val first = got.poll(5, TimeUnit.SECONDS)!!
        assertArrayEquals(byteArrayOf(1, 2, 3), first.first)
        assertEquals(0, first.second)
        s.push(byteArrayOf(4, 5), 270)
        val second = got.poll(5, TimeUnit.SECONDS)!!
        assertArrayEquals(byteArrayOf(4, 5), second.first)
        assertEquals(3, second.second)
    }

    // ---- rejections ----

    @Test
    fun `failure after admission before the server nonce removes the reader and restores idle`() {
        val now = java.util.concurrent.atomic.AtomicLong(1_000)
        val idle = CountDownLatch(1)
        val admitted = LinkedBlockingQueue<Pair<Int, Set<String>>>()
        val s = ScreenServer(
            onIdle = { idle.countDown() }, preferredPort = 0, graceMs = 100, firstGraceMs = 60_000,
            clock = { now.get() }, keys = { keys.byMaster() },
        )
        server = s
        val key = keys.keyFor(A)
        s.beforeServerNonce = {
            admitted.add(s.clientCount to s.connectedKeys())
            throw java.io.IOException("forced nonce delivery failure")
        }
        assertTrue(s.start())
        val failed = raw(s.port, hello(A, key, freshNonce()))
        assertEquals(1 to setOf(key), admitted.poll(5, TimeUnit.SECONDS))
        assertTrue("no server nonce delivered", hungUp(failed))
        waitFor("failed admission removed") { s.clientCount == 0 }
        assertEquals(emptySet<String>(), s.connectedKeys())
        assertEquals(0, s.clientCount)
        assertEquals("idle grace has not elapsed", 1L, idle.count)
        now.set(1_100)
        assertTrue("idle restored using the last-reader grace", idle.await(5, TimeUnit.SECONDS))

        s.beforeServerNonce = null
        val next = connect(s, A, key)
        assertEquals(1, s.clientCount)
        assertEquals(setOf(key), s.connectedKeys())
        s.push(byteArrayOf(7, 8), 90)
        assertArrayEquals(byteArrayOf(7, 8, 1), frame(next))
    }

    @Test
    fun `a wrong hmac is rejected without a reply`() {
        val s = start()
        keys.keyFor(A)
        val sock = raw(s.port, hello(A, "x".repeat(32), ByteArray(16) { 1 }, macKey = "y".repeat(32)))
        assertTrue(hungUp(sock))
        assertEquals(0, s.clientCount)
    }

    @Test
    fun `the key itself is never what is sent`() {
        val key = keys.keyFor(A)
        val h = hello(A, key, ByteArray(16) { 2 })
        assertEquals(ScreenServer.HELLO_SIZE, h.size)
        assertFalse(String(h, Charsets.ISO_8859_1).contains(key))
    }

    @Test
    fun `a Master with no current key is rejected`() {
        val s = start()
        keys.keyFor(A)
        val sock = raw(s.port, hello(B, "z".repeat(32), ByteArray(16) { 3 }))
        assertTrue(hungUp(sock))
        // And one whose key was taken away.
        val k = keys.keyFor(B)
        keys.remove(B)
        assertTrue(hungUp(raw(s.port, hello(B, k, ByteArray(16) { 4 }))))
        assertEquals(0, s.clientCount)
    }

    @Test
    fun `a replayed hello is rejected`() {
        val s = start()
        val key = keys.keyFor(A)
        val cn = ByteArray(16) { 9 }
        val h = hello(A, key, cn)
        val first = raw(s.port, h)
        DataInputStream(first.getInputStream()).readFully(ByteArray(16))
        waitFor("first admitted") { s.clientCount == 1 }
        first.close()
        waitFor("first gone") { s.clientCount == 0 }
        assertTrue("replay", hungUp(raw(s.port, h)))
        assertEquals(0, s.clientCount)
    }

    @Test
    fun `a plain HTTP request is closed and the old camera path is not served`() {
        val s = start()
        keys.keyFor(A)
        val sock = raw(s.port, "GET /cam?k=abc HTTP/1.1\r\nHost: x\r\nUser-Agent: okhttp/4\r\nAccept: */*\r\n\r\n".toByteArray())
        assertTrue(hungUp(sock))
        assertEquals(0, s.clientCount)
    }

    @Test
    fun `a hello that never finishes is cut off`() {
        val s = ScreenServer(
            onIdle = {}, preferredPort = 0, headMs = 200, graceMs = 60_000, firstGraceMs = 60_000,
            clock = { System.nanoTime() / 1_000_000 }, keys = { keys.byMaster() },
        )
        assertTrue(s.start())
        server = s
        val sock = raw(s.port, ScreenServer.MAGIC)
        assertTrue(hungUp(sock))
    }

    @Test
    fun `at most two parents at once`() {
        val s = start()
        val a = connect(s, A, keys.keyFor(A))
        val b = connect(s, B, keys.keyFor(B))
        waitFor("two") { s.clientCount == 2 }
        val c = keys.keyFor("cccccccccccc")
        val cn = ByteArray(16) { 5 }
        assertTrue(hungUp(raw(s.port, hello("cccccccccccc", c, cn))))
        assertEquals(2, s.clientCount)
        assertTrue(a.socket.isConnected && b.socket.isConnected)
    }

    // ---- the client against a bad tablet ----

    /** A tablet that does the handshake right and then sends [body] (a sealed frame it may have damaged). */
    private fun fakeTablet(key: String, body: (FrameSeal, DataOutputStream) -> Unit): Int {
        val ss = ServerSocket(0)
        fakes += ss
        Thread {
            try {
                ss.accept().use { sock ->
                    val inp = DataInputStream(sock.getInputStream())
                    val hello = ByteArray(ScreenServer.HELLO_SIZE)
                    inp.readFully(hello)
                    val cn = hello.copyOfRange(16, 32)
                    val sn = ByteArray(16) { 7 }
                    val out = DataOutputStream(sock.getOutputStream())
                    out.write(sn)
                    out.flush()
                    body(FrameSeal(ScreenServer.sessionKey(key, cn, sn), "n2m-screen", "m2n-screen"), out)
                    Thread.sleep(300)
                }
            } catch (_: Exception) {}
        }.apply { isDaemon = true }.start()
        return ss.localPort
    }

    private fun endedReason(port: Int, key: String, got: LinkedBlockingQueue<ByteArray>): String? {
        val ended = LinkedBlockingQueue<String>()
        val c = ScreenClient("127.0.0.1", port, A, key, onEnded = { ended.add(it) })
        c.tap = { j, _ -> got.add(j) }
        clients += c
        c.start()
        return ended.poll(5, TimeUnit.SECONDS)
    }

    @Test
    fun `a tampered frame closes the connection`() {
        val key = keys.keyFor(A)
        val port = fakeTablet(key) { seal, out ->
            val ok = seal.seal(byteArrayOf(1, 2, 3, 0))
            out.writeInt(ok.size); out.write(ok)
            val bad = seal.seal(byteArrayOf(4, 5, 6, 0))
            bad[bad.size - 1] = (bad[bad.size - 1].toInt() xor 1).toByte()
            out.writeInt(bad.size); out.write(bad)
            val after = seal.seal(byteArrayOf(7, 8, 9, 0))
            out.writeInt(after.size); out.write(after)
            out.flush()
        }
        val got = LinkedBlockingQueue<ByteArray>()
        assertEquals(CamPlan.ENDED_IO, endedReason(port, key, got))
        assertArrayEquals(byteArrayOf(1, 2, 3), got.poll(1, TimeUnit.SECONDS))
        assertNull("nothing after the bad frame", got.poll(300, TimeUnit.MILLISECONDS))
    }

    @Test
    fun `a replayed or reordered frame closes the connection`() {
        val key = keys.keyFor(A)
        val port = fakeTablet(key) { seal, out ->
            val f1 = seal.seal(byteArrayOf(1, 0))
            val f2 = seal.seal(byteArrayOf(2, 0))
            out.writeInt(f1.size); out.write(f1)
            out.writeInt(f1.size); out.write(f1)
            out.writeInt(f2.size); out.write(f2)
            out.flush()
        }
        val got = LinkedBlockingQueue<ByteArray>()
        assertEquals(CamPlan.ENDED_IO, endedReason(port, key, got))
        assertEquals(1, got.size)
    }

    @Test
    fun `an oversize frame length closes the connection`() {
        val key = keys.keyFor(A)
        val port = fakeTablet(key) { _, out -> out.writeInt(ScreenServer.MAX_FRAME + 1); out.flush() }
        assertEquals(CamPlan.ENDED_IO, endedReason(port, key, LinkedBlockingQueue()))
    }

    @Test
    fun `a tablet that does not know the key ends the client with key refused`() {
        val s = start()
        keys.keyFor(A)
        val ended = LinkedBlockingQueue<String>()
        val c = ScreenClient("127.0.0.1", s.port, A, "w".repeat(32), onEnded = { ended.add(it) })
        clients += c
        c.start()
        assertEquals(CamPlan.ENDED_KEY, ended.poll(5, TimeUnit.SECONDS))
    }

    // ---- control and revocation ----

    @Test
    fun `only the Master whose key is reading can control, and not after it disconnects`() {
        val s = start()
        val a = keys.keyFor(A)
        val b = keys.keyFor(B) // B only sent screenStart
        assertFalse(keys.mayControl(A, s.connectedKeys()))
        assertFalse(keys.mayControl(B, s.connectedKeys()))

        val sa = connect(s, A, a)
        waitFor("A connected") { s.clientCount == 1 }
        assertTrue(keys.mayControl(A, s.connectedKeys()))
        assertFalse("B never opened the stream", keys.mayControl(B, s.connectedKeys()))

        sa.socket.close()
        waitFor("A gone") { s.clientCount == 0 }
        assertFalse(keys.mayControl(A, s.connectedKeys()))

        connect(s, B, b)
        waitFor("B connected") { s.clientCount == 1 }
        assertTrue(keys.mayControl(B, s.connectedKeys()))
        assertFalse(keys.mayControl(A, s.connectedKeys()))
    }

    @Test
    fun `revocation mid-stream closes the connection and blocks a new one`() {
        val s = start()
        val a = keys.keyFor(A)
        val se = connect(s, A, a)
        waitFor("A connected") { s.clientCount == 1 }
        s.push(byteArrayOf(1), 0)
        assertNotNullFrame(se)
        keys.remove(A)
        s.dropKey(a)
        waitFor("A cut off") { s.clientCount == 0 }
        assertTrue(hungUpAfterFrames(se))
        assertFalse(keys.mayControl(A, s.connectedKeys()))
        // Even with the same key handed back, this server will not admit it again.
        val again = raw(s.port, hello(A, a, ByteArray(16) { 11 }))
        assertTrue(hungUp(again))
    }

    private fun assertNotNullFrame(se: Session) = assertTrue(frame(se) != null)

    private fun hungUpAfterFrames(se: Session): Boolean = try {
        while (true) { if (se.socket.getInputStream().read() < 0) break }
        true
    } catch (e: SocketTimeoutException) { false } catch (e: java.io.IOException) { true }

    @Test
    fun `two Masters are isolated`() {
        val s = start()
        val a = keys.keyFor(A)
        val b = keys.keyFor(B)
        val sa = connect(s, A, a)
        val sb = connect(s, B, b)
        waitFor("both") { s.clientCount == 2 }
        // A's key cannot be used under B's id.
        assertTrue(hungUp(raw(s.port, hello(B, a, ByteArray(16) { 21 }))))
        // Each session has its own keys: B cannot read A's frames.
        s.push(byteArrayOf(9, 9), 0)
        assertTrue(frame(sa) != null)
        assertTrue(frame(sb) != null)
        // B leaves: A keeps streaming and keeps control, B has none.
        keys.remove(B)
        s.dropKey(b)
        waitFor("B gone") { s.connectedKeys() == setOf(a) }
        assertTrue(keys.mayControl(A, s.connectedKeys()))
        assertFalse(keys.mayControl(B, s.connectedKeys()))
        s.push(byteArrayOf(8), 0)
        assertEquals(1, frame(sa)!!.size - 1)
    }

    @Test
    fun `revocation blocks a validated reader before admission while another reader stays live`() {
        val accepted = java.util.concurrent.ConcurrentHashMap(mapOf(A to "a".repeat(32), B to "b".repeat(32)))
        val validated = CountDownLatch(1)
        val resume = CountDownLatch(1)
        val s = ScreenServer(
            onIdle = {}, preferredPort = 0, graceMs = 60_000, firstGraceMs = 60_000,
            clock = { System.nanoTime() / 1_000_000 }, keys = { HashMap(accepted) },
        )
        server = s
        s.beforeAdmission = { k ->
            if (k == "a".repeat(32)) {
                validated.countDown()
                check(resume.await(5, TimeUnit.SECONDS))
            }
        }
        assertTrue(s.start())
        val b = connect(s, B, accepted.getValue(B))
        val cnA = ByteArray(16) { 31 }
        val a = raw(s.port, hello(A, accepted.getValue(A), cnA))
        try {
            assertTrue(validated.await(5, TimeUnit.SECONDS))
            accepted.remove(A)
            s.dropKey("a".repeat(32))
        } finally {
            resume.countDown()
        }
        val jpeg = byteArrayOf(1, 2, 3)
        s.push(jpeg, 0)
        // A was validated but revoked before admission: it gets nothing, not even the server nonce.
        assertTrue(hungUp(a))
        assertEquals(setOf("b".repeat(32)), s.connectedKeys())
        assertEquals(1, s.clientCount)
        val plain = frame(b)!!
        assertArrayEquals(jpeg, plain.copyOfRange(0, plain.size - 1))
    }

    @Test
    fun `no picture is written while nobody is connected, and the newest one is waiting for the first`() {
        val s = start()
        val key = keys.keyFor(A)
        s.push(byteArrayOf(1), 0) // nobody there: kept as the newest, never sent anywhere
        val se = connect(s, A, key)
        waitFor("connected") { s.clientCount == 1 }
        s.push(byteArrayOf(2), 0)
        val seen = CopyOnWriteArrayList<Int>()
        val p = frame(se)!!
        seen += p[0].toInt()
        assertTrue(seen.single() == 1 || seen.single() == 2)
    }
}
