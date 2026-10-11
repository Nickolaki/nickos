package com.nbaltas.nickos.sync

import com.nbaltas.nickos.link.MediaServer
import com.nbaltas.nickos.link.Served
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileInputStream
import java.util.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaServerTest {
    private val mib = 1024 * 1024
    private val bytes = ByteArray(3 * mib).also { Random(7).nextBytes(it) }
    private lateinit var file: File
    private lateinit var server: MediaServer
    private val http = OkHttpClient()
    private var opened = 0
    private var now = 1000L
    private lateinit var key: String

    @Before
    fun setUp() {
        file = File.createTempFile("nickos", ".mp4").apply { writeBytes(bytes) }
        server = MediaServer(0, allowPeer = { true }, clock = { now }) { fid ->
            if (fid == "abc" || fid == "other") Served(file.length(), "video/mp4") { opened++; FileInputStream(file) } else null
        }
        assertTrue(server.start())
        key = server.grant("abc")
    }

    @After
    fun tearDown() {
        server.stop()
        file.delete()
    }

    private fun get(path: String, range: String? = null): Response {
        val b = Request.Builder().url("http://127.0.0.1:${server.port}$path")
        if (range != null) b.header("Range", range)
        return http.newCall(b.build()).execute()
    }

    private fun good(fid: String = "abc") = "/v/$fid?k=$key"

    @Test
    fun `a real port is reported and the url carries the movie's key`() {
        assertTrue(server.port > 0)
        assertEquals("http://192.168.1.20:${server.port}/v/abc?k=$key", server.url("192.168.1.20", "abc"))
        assertEquals(32, key.length)
        assertEquals("asking again keeps the key", key, server.grant("abc"))
    }

    @Test
    fun `a movie nobody asked for is not served, and one movie's key does not open another`() {
        get("/v/other?k=$key").use { assertEquals(403, it.code) }
        val other = server.grant("other")
        assertTrue(other != key)
        get("/v/abc?k=$other").use { assertEquals(403, it.code) }
        get("/v/other?k=$other").use { assertEquals(200, it.code) }
    }

    @Test
    fun `a key stops working six hours after it was last asked for`() {
        now += MediaServer.GRANT_MS - 1
        get(good()).use { assertEquals(200, it.code) }
        now += 2
        get(good()).use { assertEquals(403, it.code) }
        val again = server.grant("abc")
        assertTrue("an expired key is not handed out again", again != key)
        get("/v/abc?k=$again").use { assertEquals(200, it.code) }
    }

    @Test
    fun `a request from outside the home network is refused even with the key`() {
        val strict = MediaServer(0) { _ -> Served(file.length(), "video/mp4") { FileInputStream(file) } }
        try {
            assertTrue(strict.start())
            val k = strict.grant("abc")
            // The test client arrives from 127.0.0.1, which is not a private LAN address.
            http.newCall(Request.Builder().url("http://127.0.0.1:${strict.port}/v/abc?k=$k").build()).execute().use { assertEquals(403, it.code) }
        } finally { strict.stop() }
    }

    @Test
    fun `wrong or missing key is refused before the file is looked at`() {
        get("/v/abc?k=nope").use { assertEquals(403, it.code) }
        get("/v/abc").use { assertEquals(403, it.code) }
        assertEquals(0, opened)
    }

    @Test
    fun `unknown fid and every other path are not found`() {
        get("/v/zzz?k=${server.grant("zzz")}").use { assertEquals(404, it.code) }
        get("/?k=$key").use { assertEquals(404, it.code) }
        get("/v/?k=$key").use { assertEquals(404, it.code) }
        get("/v/../abc?k=$key").use { assertEquals(404, it.code) }
        get("/files/abc?k=$key").use { assertEquals(404, it.code) }
    }

    @Test
    fun `no range gives the whole file`() {
        get(good()).use { r ->
            assertEquals(200, r.code)
            assertEquals("video/mp4", r.header("Content-Type"))
            assertEquals("bytes", r.header("Accept-Ranges"))
            assertEquals(bytes.size.toString(), r.header("Content-Length"))
            assertArrayEquals(bytes, r.body!!.bytes())
        }
    }

    @Test
    fun `an open range resumes from that byte`() {
        get(good(), "bytes=$mib-").use { r ->
            assertEquals(206, r.code)
            assertEquals("bytes $mib-${bytes.size - 1}/${bytes.size}", r.header("Content-Range"))
            assertEquals((2 * mib).toString(), r.header("Content-Length"))
            assertArrayEquals(bytes.copyOfRange(mib, bytes.size), r.body!!.bytes())
        }
    }

    @Test
    fun `a closed range stops at its end`() {
        get(good(), "bytes=10-19").use { r ->
            assertEquals(206, r.code)
            assertEquals("bytes 10-19/${bytes.size}", r.header("Content-Range"))
            assertArrayEquals(bytes.copyOfRange(10, 20), r.body!!.bytes())
        }
    }

    @Test
    fun `a range past the end is 416`() {
        get(good(), "bytes=9999999-").use { r ->
            assertEquals(416, r.code)
            assertEquals("bytes */${bytes.size}", r.header("Content-Range"))
        }
    }

    @Test
    fun `starting twice keeps the same port and stop frees it`() {
        val p = server.port
        assertTrue(server.start())
        assertEquals(p, server.port)
        server.stop()
        assertEquals(0, server.port)
    }

    @Test
    fun `a taken port falls back to a free one`() {
        val second = MediaServer(server.port) { _ -> null }
        try {
            assertTrue(second.start())
            assertTrue(second.port > 0 && second.port != server.port)
        } finally { second.stop() }
    }
}
