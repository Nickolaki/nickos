package com.nbaltas.nickos.master

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.ServerSocket
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SocketProberTest {
    private val servers = mutableListOf<ServerSocket>()
    @After fun tearDown() { servers.forEach { try { it.close() } catch (e: Exception) { } } }

    /** A server that answers the knock with [reply] bytes, one every [gapMs], never sending a newline when [newline] is false. */
    private fun server(reply: String, gapMs: Long, newline: Boolean): Int {
        val ss = ServerSocket(0).also { servers += it }
        thread(isDaemon = true) {
            try {
                while (true) {
                    val c = ss.accept()
                    thread(isDaemon = true) {
                        try {
                            c.getInputStream().read()
                            val out = c.getOutputStream()
                            for (ch in reply) { out.write(ch.code); out.flush(); Thread.sleep(gapMs) }
                            if (newline) { out.write('\n'.code); out.flush() }
                            while (true) { out.write('x'.code); out.flush(); Thread.sleep(gapMs) }
                        } catch (e: Exception) { }
                    }
                }
            } catch (e: Exception) { }
        }
        return ss.localPort
    }

    @Test
    fun `a host trickling bytes is cut off at the overall deadline`() = runBlocking {
        val port = server("x".repeat(400), gapMs = 100, newline = false)
        val t0 = System.nanoTime()
        val id = SocketProber(timeoutMs = 400, totalMs = 300).probe("127.0.0.1", port)
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertNull(id)
        assertTrue("took $ms ms", ms < 1500)
    }

    @Test
    fun `cancelling a probe closes the socket instead of waiting for the read`() = runBlocking {
        val port = server("x".repeat(400), gapMs = 100, newline = false)
        val t0 = System.nanoTime()
        val r = withTimeoutOrNull(150) { SocketProber(timeoutMs = 400, totalMs = 60_000).probe("127.0.0.1", port) }
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertNull(r)
        assertTrue("took $ms ms", ms < 1000)
    }

    @Test
    fun `a real iam reply is understood`() = runBlocking {
        val line = """{"t":"iam","nodeId":"0123456789ab","name":"N"}"""
        val port = server(line, gapMs = 0, newline = true)
        assertEquals("0123456789ab", SocketProber().probe("127.0.0.1", port))
    }
}
