package com.nbaltas.nickos.master

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.ServerSocket
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RtspKnockTest {
    private val servers = mutableListOf<ServerSocket>()
    @After fun tearDown() { servers.forEach { try { it.close() } catch (e: Exception) { } } }

    /** A server that reads the request, then writes [reply] (nothing when null) and stays quiet. */
    private fun server(reply: String?): Int {
        val ss = ServerSocket(0).also { servers += it }
        thread(isDaemon = true) {
            try {
                while (true) {
                    val c = ss.accept()
                    thread(isDaemon = true) {
                        try {
                            c.getInputStream().read()
                            if (reply != null) c.getOutputStream().apply { write(reply.toByteArray()); flush() }
                            Thread.sleep(60_000)
                        } catch (e: Exception) { }
                    }
                }
            } catch (e: Exception) { }
        }
        return ss.localPort
    }

    @Test
    fun `a server that speaks rtsp is a camera`() = runBlocking {
        val port = server("RTSP/1.0 200 OK\r\nCSeq: 1\r\n\r\n")
        assertTrue(RtspKnock().rtsp("127.0.0.1", port))
    }

    @Test
    fun `a web server is not a camera`() = runBlocking {
        val port = server("HTTP/1.1 400 Bad Request\r\n")
        assertFalse(RtspKnock().rtsp("127.0.0.1", port))
    }

    @Test
    fun `a port nobody listens on is false and quick`() = runBlocking {
        val closed = ServerSocket(0).use { it.localPort }
        val t0 = System.nanoTime()
        assertFalse(RtspKnock().rtsp("127.0.0.1", closed))
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertTrue("took $ms ms", ms < 1_000)
    }

    @Test
    fun `a server that never writes is cut at the overall deadline`() = runBlocking {
        val port = server(null)
        val t0 = System.nanoTime()
        assertFalse(RtspKnock(timeoutMs = 5_000, totalMs = 300).rtsp("127.0.0.1", port))
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertTrue("took $ms ms", ms < 2_000)
    }
}
