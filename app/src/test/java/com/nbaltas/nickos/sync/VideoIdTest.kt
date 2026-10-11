package com.nbaltas.nickos.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoIdTest {

    @Test
    fun `fingerprint is stable and 16 lowercase hex chars`() {
        val head = ByteArray(1024) { it.toByte() }
        val tail = ByteArray(1024) { (it * 2).toByte() }
        val a = VideoId.fingerprint(5_000_000L, head, tail)
        val b = VideoId.fingerprint(5_000_000L, head, tail)
        assertEquals(a, b)
        assertEquals(16, a.length)
        assertTrue(a.all { it in "0123456789abcdef" })
    }

    @Test
    fun `fingerprint changes with size`() {
        val head = ByteArray(10) { it.toByte() }
        val tail = ByteArray(10) { it.toByte() }
        assertNotEquals(VideoId.fingerprint(100L, head, tail), VideoId.fingerprint(200L, head, tail))
    }

    @Test
    fun `fingerprint changes with the tail`() {
        val head = ByteArray(10) { it.toByte() }
        val tailA = ByteArray(10) { it.toByte() }
        val tailB = ByteArray(10) { (it + 1).toByte() }
        assertNotEquals(VideoId.fingerprint(100L, head, tailA), VideoId.fingerprint(100L, head, tailB))
    }

    @Test
    fun `a small file hashes its whole content once`() {
        val whole = ByteArray(500) { it.toByte() }
        // Caller passes the whole file as head and nothing as tail for files under 2 MiB.
        val fp = VideoId.fingerprint(500L, whole, ByteArray(0))
        assertEquals(16, fp.length)
    }

    @Test
    fun `title strips dots and underscores and only the last extension`() {
        assertEquals("Big Buck Bunny 1080p", VideoId.title("Big.Buck.Bunny_1080p.mp4"))
    }

    @Test
    fun `title keeps unicode`() {
        assertEquals("Fête du cinema", VideoId.title("Fête_du_cinema.mkv"))
    }

    @Test
    fun `title collapses repeated whitespace and trims`() {
        assertEquals("A B C", VideoId.title("  A..B__C  .mp4"))
    }

    @Test
    fun `episode parses show season number and label`() {
        val ep = VideoId.episode("Star Harbour S02E05 The Lighthouse")
        assertEquals(Episode("Star Harbour", 2, 5, "The Lighthouse"), ep)
    }

    @Test
    fun `episode is case insensitive and works with no surrounding text`() {
        val ep = VideoId.episode("s1e2")
        assertEquals(1, ep?.season)
        assertEquals(2, ep?.number)
    }

    @Test
    fun `episode returns null for a plain movie name`() {
        assertNull(VideoId.episode("Big Buck Bunny"))
    }

    @Test
    fun `safeName strips forbidden characters and never returns empty`() {
        assertEquals("a_b_c_d_e_f_g_h_", VideoId.safeName("a\\b/c:d*e?f\"g<h>"))
        assertEquals("___", VideoId.safeName("///"))
        assertEquals("video", VideoId.safeName("   "))
    }
}
