package com.nbaltas.nickos.link

import kotlinx.coroutines.cancel
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
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConnTest {
    private fun writeRaw(l: Link, bytes: Int, newline: Boolean = true) {
        val t = Thread {
            try {
                val out = l.master.socket.getOutputStream()
                val chunk = ByteArray(65536) { 'a'.code.toByte() }
                var left = bytes
                while (left > 0) { val n = minOf(left, chunk.size); out.write(chunk, 0, n); left -= n }
                if (newline) out.write('\n'.code)
                out.flush()
            } catch (_: Exception) {}
        }
        t.isDaemon = true; t.start()
    }

    @Test
    fun `a line of LINE_MAX plus one returns null and closes the socket`() {
        Link().use { l ->
            writeRaw(l, Conn.LINE_MAX + 1)
            assertNull(l.node.read())
            assertFalse(l.node.isOpen)
        }
    }

    @Test
    fun `a first line over FIRST_LINE_MAX before auth returns null and closes`() {
        Link().use { l ->
            writeRaw(l, Conn.FIRST_LINE_MAX + 1)
            assertNull(l.node.read(Conn.FIRST_LINE_MAX))
            assertFalse(l.node.isOpen)
        }
    }

    @Test
    fun `a first line with no newline at all is cut off at the cap`() {
        Link().use { l ->
            writeRaw(l, Conn.FIRST_LINE_MAX * 4, newline = false)
            assertNull(l.node.read(Conn.FIRST_LINE_MAX))
            assertFalse(l.node.isOpen)
        }
    }

    @Test
    fun `a line exactly at the cap still reads`() {
        Link().use { l ->
            val pad = "x".repeat(Conn.FIRST_LINE_MAX - """{"a":""}""".length)
            val json = """{"a":"$pad"}"""
            assertEquals(Conn.FIRST_LINE_MAX, json.length)
            assertTrue(l.master.send(JSONObject(json)))
            assertEquals(pad, l.node.read(Conn.FIRST_LINE_MAX)!!.optString("a"))
        }
    }

    @Test
    fun `a big post auth line under LINE_MAX reads`() {
        Link().use { l ->
            val big = "y".repeat(3 * 1024 * 1024)
            val t = Thread { l.master.send(JSONObject().put("t", "apps").put("icon", big)) }
            t.isDaemon = true; t.start()
            assertEquals(big.length, l.node.read()!!.optString("icon").length)
        }
    }

    @Test
    fun `a deadline closes a peer that drips bytes`() {
        Link().use { l ->
            val t = Thread {
                try {
                    val out = l.master.socket.getOutputStream()
                    repeat(20) { out.write('a'.code); out.flush(); Thread.sleep(50) }
                } catch (_: Exception) {}
            }
            t.isDaemon = true; t.start()
            assertNull(l.node.read(Conn.FIRST_LINE_MAX, deadlineMs = 200))
            assertFalse(l.node.isOpen)
        }
    }

    @Test
    fun `a sealed connection closes on a line that does not open`() {
        Link().use { l ->
            val key = ByteArray(32) { 9 }
            l.master.seal(Seal(key, "m2n", "n2m"))
            l.node.seal(Seal(key, "n2m", "m2n"))
            l.master.send(JSONObject().put("t", "cmd").put("c", "a"))
            assertEquals("a", l.node.read()!!.optString("c"))
            l.master.socket.getOutputStream().write("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\n".toByteArray())
            assertNull(l.node.read())
            assertFalse(l.node.isOpen)
        }
    }

    @Test
    fun `nine idle connections leave the ninth closed at once and the first eight held`() {
        val ss = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val gate = AcceptGate(max = 8, soTimeoutMs = 10_000)
        val started = AtomicInteger()
        val hold = CountDownLatch(1)
        val loop = Thread {
            gate.run(ss, { true }) { _, _ ->
                started.incrementAndGet()
                hold.await(20, TimeUnit.SECONDS) // an unauthenticated handshake that has not finished
            }
        }
        loop.isDaemon = true; loop.start()
        val clients = ArrayList<Socket>()
        try {
            repeat(8) {
                clients += Socket(InetAddress.getByName("127.0.0.1"), ss.localPort)
                val until = System.currentTimeMillis() + 5000
                while (started.get() < it + 1 && System.currentTimeMillis() < until) Thread.sleep(5)
            }
            assertEquals(8, started.get())
            val ninth = Socket(InetAddress.getByName("127.0.0.1"), ss.localPort).also { clients += it }
            ninth.soTimeout = 3000
            assertEquals("the ninth is closed immediately", -1, ninth.getInputStream().read())
            for (c in clients.take(8)) {
                c.soTimeout = 150
                try { c.getInputStream().read(); throw AssertionError("a held connection was closed") } catch (_: SocketTimeoutException) {}
            }
            assertEquals(8, started.get())
            // A slot frees when a handshake ends.
            hold.countDown()
            val until = System.currentTimeMillis() + 5000
            var tenth: Socket? = null
            while (System.currentTimeMillis() < until) {
                val s = Socket(InetAddress.getByName("127.0.0.1"), ss.localPort).also { clients += it }
                s.soTimeout = 300
                try { if (s.getInputStream().read() == -1) { Thread.sleep(20); continue } } catch (_: SocketTimeoutException) { tenth = s; break } catch (_: java.io.IOException) { continue }
                // a handler that returned immediately closes its socket: that is also an admitted connection
                tenth = s; break
            }
            assertNotNull("a freed slot admits a new connection", tenth)
        } finally {
            hold.countDown()
            clients.forEach { try { it.close() } catch (_: Exception) {} }
            ss.close()
        }
    }

    @Test
    fun `a lossy send gives up at once while another line is being written`() {
        // The peer never reads, so a big send fills the buffers and stays blocked holding the write lock.
        val ss = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val client = Socket(InetAddress.getByName("127.0.0.1"), ss.localPort)
        val peer = ss.accept()
        val conn = Conn(client)
        try {
            assertEquals("free link: written", Conn.Lossy.WRITTEN, conn.sendLossy(JSONObject().put("t", "talk")))
            val big = JSONObject().put("pad", "x".repeat(24 * 1024 * 1024))
            val blocked = Thread { conn.send(big) }.also { it.isDaemon = true; it.start() }
            Thread.sleep(300)
            assertTrue(blocked.isAlive)
            val t0 = System.nanoTime()
            assertEquals("busy link: dropped", Conn.Lossy.BUSY, conn.sendLossy(JSONObject().put("t", "talk")))
            assertTrue("gave up in ${(System.nanoTime() - t0) / 1e6} ms", (System.nanoTime() - t0) / 1e6 < 50)
            assertTrue("the link was not closed by the drop", conn.isOpen)
        } finally {
            conn.close(); peer.close(); ss.close()
        }
    }

    @Test
    fun `a lossy line arrives in the same order and opens like any other`() {
        Link().use { l ->
            val key = ByteArray(32) { it.toByte() }
            l.master.seal(Seal(key, "m2n", "n2m")); l.node.seal(Seal(key, "n2m", "m2n"))
            assertTrue(l.master.send(JSONObject().put("n", 1)))
            assertEquals(Conn.Lossy.WRITTEN, l.master.sendLossy(JSONObject().put("n", 2)))
            assertTrue(l.master.send(JSONObject().put("n", 3)))
            assertEquals(listOf(1, 2, 3), (1..3).map { l.node.read()!!.getInt("n") })
        }
    }

    /** A link whose peer never reads: the first big audio write stays blocked until the deadline closes the link. */
    private class Stuck {
        val ss = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val conn = Conn(Socket(InetAddress.getByName("127.0.0.1"), ss.localPort))
        val peer: Socket = ss.accept()
        // Bigger than the loopback buffers on both ends together, so the write cannot finish.
        val big = JSONObject().put("pad", "x".repeat(24 * 1024 * 1024))
        fun close() { conn.close(); peer.close(); ss.close() }
    }

    @Test
    fun `a stalled audio write is cut at its deadline, closes the link and says stalled`() {
        val s = Stuck()
        try {
            val t0 = System.nanoTime()
            val r = s.conn.sendLossy(s.big, deadlineMs = 150)
            val ms = (System.nanoTime() - t0) / 1e6
            assertEquals(Conn.Lossy.STALLED, r)
            assertTrue("took $ms ms", ms < 150 + 250)
            assertFalse(s.conn.isOpen)
        } finally { s.close() }
    }

    @Test
    fun `a finished audio write cancels its deadline and leaves the link open`() {
        Link().use { l ->
            assertEquals(Conn.Lossy.WRITTEN, l.master.sendLossy(JSONObject().put("t", "talk"), deadlineMs = 100))
            Thread.sleep(250)
            assertTrue(l.master.isOpen)
        }
    }

    @Test
    fun `while one tablet's audio write is stalled a command to the stalled tablet and to a healthy one both run within the budget`() {
        val stuck = Stuck()
        val healthy = Link()
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
        val lanes = SendLanes(scope)
        try {
            // The stalled audio write is in flight, holding that tablet's write lock.
            val audio = Thread { stuck.conn.sendLossy(stuck.big, deadlineMs = 2000) }.also { it.isDaemon = true; it.start() }
            Thread.sleep(300)
            assertTrue("the audio write is stuck", audio.isAlive)
            val toStuck = CountDownLatch(1); val toHealthy = CountDownLatch(1)
            val t0 = System.nanoTime()
            lanes.launch("a") { stuck.conn.send(JSONObject().put("c", "lock")); toStuck.countDown() }
            lanes.launch("b") { healthy.master.send(JSONObject().put("c", "lock")); toHealthy.countDown() }
            // The healthy tablet's command does not wait for the stalled one at all.
            assertTrue("healthy tablet's command ran", toHealthy.await(250, TimeUnit.MILLISECONDS))
            val healthyMs = (System.nanoTime() - t0) / 1e6
            assertEquals("lock", healthy.node.read()!!.getString("c"))
            assertTrue("healthy took $healthyMs ms", healthyMs < 250)
            // The stalled tablet's own command is not allowed to hang the caller: it fails once its link is cut.
            assertFalse("stalled tablet's command is still waiting on its stuck audio write", toStuck.await(100, TimeUnit.MILLISECONDS))
        } finally {
            stuck.close(); healthy.close(); scope.cancel()
        }
    }

    @Test
    fun `with the real deadline the stalled tablet's command finishes within the budget too`() {
        val stuck = Stuck()
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
        val lanes = SendLanes(scope)
        try {
            val audio = Thread { stuck.conn.sendLossy(stuck.big) }.also { it.isDaemon = true; it.start() }
            Thread.sleep(50)
            val done = CountDownLatch(1)
            val t0 = System.nanoTime()
            lanes.launch("a") { stuck.conn.send(JSONObject().put("c", "lock")); done.countDown() }
            assertTrue("command to the stalled tablet finished", done.await(600, TimeUnit.MILLISECONDS))
            val ms = (System.nanoTime() - t0) / 1e6
            assertTrue("budget is the audio deadline plus slack: $ms ms", ms < Conn.AUDIO_WRITE_MS + 300)
            audio.join(1000)
            assertFalse(stuck.conn.isOpen)
        } finally {
            stuck.close(); scope.cancel()
        }
    }

    @Test
    fun `commands to one tablet keep their order and different tablets use different lanes`() {
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
        val lanes = SendLanes(scope)
        try {
            val seen = java.util.concurrent.CopyOnWriteArrayList<Int>()
            val done = CountDownLatch(50)
            for (i in 1..50) lanes.launch("a") { seen += i; done.countDown() }
            assertTrue(done.await(3, TimeUnit.SECONDS))
            assertEquals((1..50).toList(), seen.toList())
        } finally { scope.cancel() }
    }

    @Test
    fun `a legacy clear text link is not sealed and a sealed one is`() {
        Link().use { l ->
            assertFalse(l.master.isSealed)
            l.master.seal(Seal(ByteArray(32) { it.toByte() }, "m2n", "n2m"))
            assertTrue(l.master.isSealed)
        }
    }

    @Test
    fun `a ticket releases its slot once only`() {
        val gate = AcceptGate(max = 1)
        val t = gate.enter()!!
        assertNull(gate.enter())
        t.release(); t.release()
        assertNotNull(gate.enter())
        assertNull("double release did not mint a second slot", gate.enter())
    }
}
