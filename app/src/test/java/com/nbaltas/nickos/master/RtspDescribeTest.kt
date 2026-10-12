package com.nbaltas.nickos.master

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.ServerSocket
import java.security.MessageDigest
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RtspDescribeTest {
    private val servers = mutableListOf<ServerSocket>()
    @After fun tearDown() { servers.forEach { try { it.close() } catch (e: Exception) { } } }

    private fun md5(s: String) = MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    /** A camera that wants Digest for [user]/[pass] and serves only [path]. */
    private fun camera(user: String, pass: String, path: String): Int {
        val ss = ServerSocket(0).also { servers += it }
        thread(isDaemon = true) {
            try {
                while (true) {
                    val c = ss.accept()
                    thread(isDaemon = true) {
                        try {
                            val ins = c.getInputStream()
                            val head = StringBuilder()
                            while (!head.endsWith("\r\n\r\n")) { val b = ins.read(); if (b < 0) break; head.append(b.toChar()) }
                            val h = head.toString()
                            val uri = h.lineSequence().first().split(" ")[1]
                            val auth = h.lines().firstOrNull { it.startsWith("Authorization:") }
                            val realm = "TP-LINK IP-Camera"; val nonce = "abc123"
                            val ok = auth != null && uri.endsWith("/$path") && run {
                                val want = md5("${md5("$user:$realm:$pass")}:$nonce:${md5("DESCRIBE:$uri")}")
                                auth.contains("response=\"$want\"")
                            }
                            val reply = when {
                                ok -> "RTSP/1.0 200 OK\r\nCSeq: 2\r\n\r\n"
                                else -> "RTSP/1.0 401 Unauthorized\r\nCSeq: 1\r\nWWW-Authenticate: Digest realm=\"$realm\", nonce=\"$nonce\"\r\n\r\n"
                            }
                            c.getOutputStream().apply { write(reply.toByteArray()); flush() }
                            c.close()
                        } catch (e: Exception) { }
                    }
                }
            } catch (e: Exception) { }
        }
        return ss.localPort
    }

    @Test
    fun `digest response matches the RFC 2617 example`() {
        // RFC 2617 section 3.5 style vector without qop: computed independently here.
        val want = md5("${md5("Mufasa:testrealm@host.com:Circle Of Life")}:dcd98b7102dd2f0e8b11d0f600bfb0c093:${md5("GET:/dir/index.html")}")
        assertEquals(want, RtspDigest.response("Mufasa", "testrealm@host.com", "Circle Of Life", "dcd98b7102dd2f0e8b11d0f600bfb0c093", "GET", "/dir/index.html"))
    }

    @Test
    fun `status and challenge parsing`() {
        assertEquals(401, RtspDigest.status("RTSP/1.0 401 Unauthorized\r\n"))
        assertNull(RtspDigest.status("HTTP/1.1 200 OK"))
        val head = "RTSP/1.0 401 Unauthorized\r\nWWW-Authenticate: Digest realm=\"r\", nonce=\"n\"\r\n\r\n"
        assertTrue(RtspDigest.answer(head, "u", "p", "DESCRIBE", "rtsp://h/x")!!.startsWith("Digest username=\"u\""))
        assertNull(RtspDigest.answer("RTSP/1.0 401 Unauthorized\r\n\r\n", "u", "p", "DESCRIBE", "rtsp://h/x"))
        assertNotNull(RtspDigest.answer("RTSP/1.0 401 x\r\nWWW-Authenticate: Basic realm=\"r\"\r\n\r\n", "u", "p", "DESCRIBE", "rtsp://h/x"))
    }

    @Test
    fun `saved account on the saved path is accepted`() = runBlocking {
        val port = camera("nick", "s3cret!", "stream2")
        assertEquals(Described.OK, RtspDescribe(port = port).check("127.0.0.1", "nick", "s3cret!", "stream2"))
    }

    @Test
    fun `wrong password is unauthorized`() = runBlocking {
        val port = camera("nick", "s3cret!", "stream2")
        assertEquals(Described.UNAUTHORIZED, RtspDescribe(port = port).check("127.0.0.1", "nick", "nope", "stream2"))
    }

    @Test
    fun `right account on another path is not accepted`() = runBlocking {
        val port = camera("nick", "s3cret!", "stream2")
        assertEquals(Described.UNAUTHORIZED, RtspDescribe(port = port).check("127.0.0.1", "nick", "s3cret!", "stream1"))
    }

    @Test
    fun `nothing listening is other`() = runBlocking {
        val closed = ServerSocket(0).use { it.localPort }
        assertEquals(Described.OTHER, RtspDescribe(port = closed).check("127.0.0.1", "u", "p", "stream2"))
    }
}
