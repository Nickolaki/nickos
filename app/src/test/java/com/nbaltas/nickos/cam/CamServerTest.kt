package com.nbaltas.nickos.cam

import com.nbaltas.nickos.node.CamServer
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.BufferedSource
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CamServerTest {
    private val http = OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build()
    private val idles = AtomicInteger(0)
    private val idle = CountDownLatch(1)
    private val open = ArrayList<Response>()
    private var server: CamServer? = null

    /** A real clock: Robolectric's SystemClock stands still on a background thread. */
    private fun start(graceMs: Long = 300, firstGraceMs: Long = 60_000): CamServer {
        val s = CamServer(
            key = "abc", onIdle = { idles.incrementAndGet(); idle.countDown() }, preferredPort = 0,
            graceMs = graceMs, firstGraceMs = firstGraceMs, clock = { System.nanoTime() / 1_000_000 },
        )
        assertTrue(s.start())
        server = s
        return s
    }

    @After
    fun tearDown() {
        open.forEach { it.close() }
        server?.stop()
    }

    private fun get(s: CamServer, path: String): Response =
        http.newCall(Request.Builder().url("http://127.0.0.1:${s.port}$path").build()).execute().also { open += it }

    /** Reads one part the way the Master does: header lines up to the blank one, then exactly Content-Length bytes. */
    private fun part(src: BufferedSource): Pair<PartHeader, ByteArray> {
        val lines = ArrayList<String>()
        while (true) {
            val line = src.readUtf8LineStrict()
            if (line.isEmpty() && lines.any { it.contains(':') }) break
            lines += line
        }
        val h = MjpegParser.parsePartHeaders(lines)!!
        val bytes = src.readByteArray(h.length.toLong())
        assertEquals("", src.readUtf8LineStrict())
        return h to bytes
    }

    @Test
    fun `revocation blocks a validated reader before admission while another reader stays live`() {
        val accepted = java.util.concurrent.CopyOnWriteArrayList(listOf("A", "B"))
        val validated = CountDownLatch(1)
        val resume = CountDownLatch(1)
        val s = CamServer(
            key = "", onIdle = {}, preferredPort = 0, firstGraceMs = 60_000,
            clock = { System.nanoTime() / 1_000_000 }, keys = { accepted.toList() },
        )
        server = s
        s.beforeAdmission = { k ->
            if (k == "A") {
                validated.countDown()
                check(resume.await(5, TimeUnit.SECONDS))
            }
        }
        assertTrue(s.start())
        val b = get(s, "/cam?k=B")
        java.net.Socket("127.0.0.1", s.port).use { a ->
            a.soTimeout = 5_000
            a.getOutputStream().apply {
                write("GET /cam?k=A HTTP/1.1\r\nHost: localhost\r\n\r\n".toByteArray())
                flush()
            }
            try {
                assertTrue(validated.await(5, TimeUnit.SECONDS))
                accepted.remove("A")
                s.dropKey("A")
            } finally {
                resume.countDown()
            }
            val jpeg = byteArrayOf(1, 2, 3)
            s.push(jpeg, 90)
            val reply = a.getInputStream().bufferedReader().readText()
            assertTrue(reply.startsWith("HTTP/1.1 403 Forbidden\r\n"))
            assertFalse(reply.contains("image/jpeg"))
            assertEquals("", reply.substringAfter("\r\n\r\n"))
            assertEquals(setOf("B"), s.connectedKeys())
            assertEquals(1, s.clientCount)
            assertArrayEquals(jpeg, part(b.body!!.source()).second)
        }
    }

    @Test
    fun `fixed camera key still admits after dropKey during validation`() {
        val s = start()
        val validated = CountDownLatch(1)
        val resume = CountDownLatch(1)
        s.beforeAdmission = {
            validated.countDown()
            check(resume.await(5, TimeUnit.SECONDS))
        }
        java.net.Socket("127.0.0.1", s.port).use { camera ->
            camera.soTimeout = 5_000
            camera.getOutputStream().apply {
                write("GET /cam?k=abc HTTP/1.1\r\nHost: localhost\r\n\r\n".toByteArray())
                flush()
            }
            try {
                assertTrue(validated.await(5, TimeUnit.SECONDS))
                s.dropKey("abc")
            } finally {
                resume.countDown()
            }
            val input = camera.getInputStream().bufferedReader()
            assertEquals("HTTP/1.1 200 OK", input.readLine())
            assertEquals(setOf("abc"), s.connectedKeys())
            val jpeg = byteArrayOf(4, 5, 6)
            s.push(jpeg, 0)
            var foundPart = false
            for (i in 0 until 20) {
                if (input.readLine() == "Content-Length: 3") { foundPart = true; break }
            }
            assertTrue("camera frame header", foundPart)
            assertEquals("X-Rotation: 0", input.readLine())
            assertEquals("X-Seq: 1", input.readLine())
            assertEquals("", input.readLine())
            assertEquals(4, input.read())
            assertEquals(5, input.read())
            assertEquals(6, input.read())
        }
        assertEquals(403, get(s, "/cam?k=wrong").code)
    }

    @Test
    fun `a real port is reported`() {
        val s = start()
        assertTrue(s.port > 0)
    }

    @Test
    fun `the right key gets a multipart stream of the pictures pushed`() {
        val s = start()
        val one = ByteArray(100) { 1 }
        val two = ByteArray(200) { (it % 7).toByte() }
        val r = get(s, "/cam?k=abc")
        assertEquals(200, r.code)
        assertEquals("multipart/x-mixed-replace; boundary=nickos", r.header("Content-Type"))
        assertEquals("no-store", r.header("Cache-Control"))
        val src = r.body!!.source()

        s.push(one, 90)
        val (h1, b1) = part(src)
        assertEquals(100, h1.length)
        assertEquals(90, h1.rotation)
        assertArrayEquals(one, b1)

        s.push(two, 90)
        val (h2, b2) = part(src)
        assertEquals(200, h2.length)
        assertEquals(90, h2.rotation)
        assertArrayEquals(two, b2)
        assertTrue(h2.seq > h1.seq)
    }

    @Test
    fun `a Master that connects late gets the newest picture straight away`() {
        val s = start()
        s.push(ByteArray(10) { 1 }, 0)
        s.push(ByteArray(20) { 2 }, 270)
        val (h, b) = part(get(s, "/cam?k=abc").body!!.source())
        assertEquals(20, h.length)
        assertEquals(270, h.rotation)
        assertEquals(2.toByte(), b[0])
    }

    @Test
    fun `the wrong key is refused`() {
        val s = start()
        assertEquals(403, get(s, "/cam?k=nope").code)
        assertEquals(403, get(s, "/cam").code)
        assertEquals(403, get(s, "/cam?k=").code)
        assertEquals(403, get(s, "/cam?k=abcd").code)
        assertEquals(0, s.clientCount)
    }

    @Test
    fun `any other path or method is not found`() {
        val s = start()
        assertEquals(404, get(s, "/x").code)
        assertEquals(404, get(s, "/").code)
        assertEquals(404, get(s, "/camera?k=abc").code)
        val head = Request.Builder().url("http://127.0.0.1:${s.port}/cam?k=abc").head().build()
        http.newCall(head).execute().use { assertEquals(404, it.code) }
    }

    @Test
    fun `a peer from outside the home network is refused before its key is read`() {
        val s = CamServer(key = "abc", onIdle = {}, preferredPort = 0, allowPeer = { false }, clock = { System.nanoTime() / 1_000_000 })
        server = s
        assertTrue(s.start())
        assertEquals(403, get(s, "/cam?k=abc").code)
    }

    @Test
    fun `only home network and local addresses pass by default`() {
        assertTrue(CamServer.lanOrLocal("192.168.1.20"))
        assertTrue(CamServer.lanOrLocal("10.0.2.2"))
        assertTrue(CamServer.lanOrLocal("127.0.0.1"))
        assertFalse(CamServer.lanOrLocal("8.8.8.8"))
        assertFalse(CamServer.lanOrLocal(""))
        assertFalse(CamServer.lanOrLocal("example.com"))
    }

    @Test
    fun `a third Master at once is turned away`() {
        val s = start()
        assertEquals(200, get(s, "/cam?k=abc").code)
        assertEquals(200, get(s, "/cam?k=abc").code)
        assertEquals(503, get(s, "/cam?k=abc").code)
        assertEquals(2, s.clientCount)
    }

    @Test
    fun `when the last Master goes away the camera is told to stop after the grace time`() {
        val s = start(graceMs = 300)
        val r = get(s, "/cam?k=abc")
        s.push(ByteArray(50), 0)
        part(r.body!!.source())
        assertFalse(idle.await(600, TimeUnit.MILLISECONDS))
        r.close()
        assertTrue(idle.await(5, TimeUnit.SECONDS))
        assertEquals(1, idles.get())
    }

    @Test
    fun `a Master that comes back inside the grace time keeps the camera on`() {
        val s = start(graceMs = 1_500)
        get(s, "/cam?k=abc").close()
        Thread.sleep(300)
        get(s, "/cam?k=abc")
        assertFalse(idle.await(2_500, TimeUnit.MILLISECONDS))
    }

    @Test
    fun `no Master at all stops the camera after the first grace time`() {
        start(graceMs = 60_000, firstGraceMs = 300)
        assertTrue(idle.await(5, TimeUnit.SECONDS))
        assertEquals(1, idles.get())
    }

    @Test
    fun `a request that trickles in is cut off at the deadline`() {
        val s = CamServer(key = "abc", onIdle = {}, preferredPort = 0, headMs = 300, clock = { System.nanoTime() / 1_000_000 })
        server = s
        assertTrue(s.start())
        java.net.Socket("127.0.0.1", s.port).use { slow ->
            slow.soTimeout = 5_000
            slow.getOutputStream().apply { write("GET /cam?k=a".toByteArray()); flush() }
            // The server gives up on it: the connection is closed with nothing sent back.
            val closed = try { slow.getInputStream().read() == -1 } catch (e: java.net.SocketTimeoutException) { false } catch (e: java.io.IOException) { true }
            assertTrue(closed)
        }
        // And the port still answers a proper request.
        assertEquals(200, get(s, "/cam?k=abc").code)
    }

    @Test
    fun `stop closes the stream and never reports idle`() {
        val s = start(graceMs = 100)
        val r = get(s, "/cam?k=abc")
        s.stop()
        s.stop()
        assertTrue(r.body!!.source().exhausted())
        assertFalse(idle.await(500, TimeUnit.MILLISECONDS))
        assertEquals(0, s.clientCount)
    }
}
