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
            assertTrue("free link: written", conn.sendLossy(JSONObject().put("t", "talk")))
            val big = JSONObject().put("pad", "x".repeat(24 * 1024 * 1024))
            val blocked = Thread { conn.send(big) }.also { it.isDaemon = true; it.start() }
            Thread.sleep(300)
            assertTrue(blocked.isAlive)
            val t0 = System.nanoTime()
            assertFalse("busy link: dropped", conn.sendLossy(JSONObject().put("t", "talk")))
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
            assertTrue(l.master.sendLossy(JSONObject().put("n", 2)))
            assertTrue(l.master.send(JSONObject().put("n", 3)))
            assertEquals(listOf(1, 2, 3), (1..3).map { l.node.read()!!.getInt("n") })
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
