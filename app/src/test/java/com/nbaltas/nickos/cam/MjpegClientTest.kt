package com.nbaltas.nickos.cam

import android.graphics.Bitmap
import android.graphics.Color
import com.nbaltas.nickos.master.MjpegClient
import com.nbaltas.nickos.node.CamServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** The Master's stream reader against the Node's real server, over a real socket, with real JPEGs. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class MjpegClientTest {
    private val realClock = { System.nanoTime() / 1_000_000 }
    private val server = CamServer(key = "abc", onIdle = {}, preferredPort = 0, clock = realClock)
    private val frames = LinkedBlockingQueue<MjpegClient.Frame>()
    private val ended = LinkedBlockingQueue<String>()
    private var client: MjpegClient? = null

    @After
    fun tearDown() {
        client?.close()
        server.stop()
    }

    private fun jpeg(w: Int, h: Int): ByteArray {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(200, 80, 40)) }
        return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 60, it) }.toByteArray()
    }

    private fun connect(key: String = "abc"): MjpegClient {
        assertTrue(server.start())
        return MjpegClient("http://127.0.0.1:${server.port}/cam?k=$key", { frames += it }, { ended += it }, clock = realClock)
            .start().also { client = it }
    }

    private fun awaitClients(n: Int) {
        val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (server.clientCount != n && System.nanoTime() < until) Thread.sleep(20)
        assertEquals(n, server.clientCount)
    }

    @Test
    fun `pictures arrive decoded with the tablet's rotation`() {
        connect()
        awaitClients(1)
        server.push(jpeg(64, 48), 90)
        val f = frames.poll(5, TimeUnit.SECONDS)
        assertNotNull(f)
        assertEquals(64, f!!.bitmap.width)
        assertEquals(48, f.bitmap.height)
        assertEquals(90, f.rotation)
        Thread.sleep(150)
        server.push(jpeg(32, 24), 270)
        val g = frames.poll(5, TimeUnit.SECONDS)
        assertEquals(32, g!!.bitmap.width)
        assertEquals(270, g.rotation)
        assertTrue(g.at >= f.at)
        assertTrue(ended.isEmpty())
    }

    @Test
    fun `a corrupt picture is skipped and the stream carries on`() {
        connect()
        awaitClients(1)
        server.push(ByteArray(300) { 7 }, 0)
        assertNull(frames.poll(400, TimeUnit.MILLISECONDS))
        server.push(jpeg(64, 48), 0)
        assertNotNull(frames.poll(5, TimeUnit.SECONDS))
        assertTrue(ended.isEmpty())
    }

    @Test
    fun `a picture far bigger than a tablet camera sends is never decoded`() {
        connect()
        awaitClients(1)
        server.push(jpeg(2400, 16), 0)
        assertNull(frames.poll(500, TimeUnit.MILLISECONDS))
        server.push(jpeg(64, 48), 0)
        assertEquals(64, frames.poll(5, TimeUnit.SECONDS)?.bitmap?.width)
        assertTrue(ended.isEmpty())
    }

    @Test
    fun `a refused key ends the stream as a key problem`() {
        connect(key = "stale")
        assertEquals(CamPlan.ENDED_KEY, ended.poll(5, TimeUnit.SECONDS))
        assertNull(ended.poll(200, TimeUnit.MILLISECONDS))
    }

    @Test
    fun `the tablet stopping ends the stream once`() {
        connect()
        awaitClients(1)
        server.push(jpeg(64, 48), 0)
        assertNotNull(frames.poll(5, TimeUnit.SECONDS))
        server.stop()
        assertEquals(CamPlan.ENDED_IO, ended.poll(5, TimeUnit.SECONDS))
        assertNull(ended.poll(200, TimeUnit.MILLISECONDS))
    }

    @Test
    fun `nothing listening ends the stream`() {
        assertTrue(server.start())
        val port = server.port
        server.stop()
        client = MjpegClient("http://127.0.0.1:$port/cam?k=abc", { frames += it }, { ended += it }, clock = realClock).start()
        assertEquals(CamPlan.ENDED_IO, ended.poll(10, TimeUnit.SECONDS))
    }

    @Test
    fun `closing is silent and the tablet sees the Master leave`() {
        val c = connect()
        awaitClients(1)
        c.close()
        c.close()
        awaitClients(0)
        assertNull(ended.poll(300, TimeUnit.MILLISECONDS))
    }
}
