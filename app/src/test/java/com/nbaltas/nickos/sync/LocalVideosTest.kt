package com.nbaltas.nickos.sync

import com.nbaltas.nickos.node.LocalVideos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile

/** The file-reading half of the fingerprint, on real files. Plain JVM: nothing Android is touched. */
class LocalVideosTest {
    private val mib = 1024 * 1024

    private fun temp(bytes: ByteArray): File = File.createTempFile("nickos", ".bin").apply { deleteOnExit(); writeBytes(bytes) }

    private fun pattern(n: Int, seed: Int = 7) = ByteArray(n) { ((it * 31 + seed) % 251).toByte() }

    private fun fid(f: File): String = RandomAccessFile(f, "r").use { LocalVideos.fingerprint(it.channel, f.length()) }

    @Test
    fun `a big file is fingerprinted from its size, first MiB and last MiB`() {
        val bytes = pattern(3 * mib + 123)
        val expected = VideoId.fingerprint(bytes.size.toLong(), bytes.copyOfRange(0, mib), bytes.copyOfRange(bytes.size - mib, bytes.size))
        assertEquals(expected, fid(temp(bytes)))
    }

    @Test
    fun `a change in the middle is not seen but a change in the tail is`() {
        val bytes = pattern(3 * mib)
        val base = fid(temp(bytes))
        val middle = bytes.copyOf().also { it[mib + 500] = (it[mib + 500] + 1).toByte() }
        assertEquals(base, fid(temp(middle)))
        val tail = bytes.copyOf().also { it[bytes.size - 10] = (it[bytes.size - 10] + 1).toByte() }
        assertNotEquals(base, fid(temp(tail)))
    }

    @Test
    fun `a file of two MiB or less is hashed whole, once`() {
        val bytes = pattern(mib + 17)
        assertEquals(VideoId.fingerprint(bytes.size.toLong(), bytes, ByteArray(0)), fid(temp(bytes)))
        val exact = pattern(2 * mib)
        assertEquals(VideoId.fingerprint(exact.size.toLong(), exact, ByteArray(0)), fid(temp(exact)))
    }

    @Test
    fun `a copy of a file has the fingerprint of its source`() {
        val src = temp(pattern(4 * mib + 9, seed = 3))
        val copy = File.createTempFile("nickos", ".copy").apply { deleteOnExit() }
        src.copyTo(copy, overwrite = true)
        assertEquals(fid(src), fid(copy))
        assertTrue(fid(src).matches(Regex("[0-9a-f]{16}")))
    }

    @Test
    fun `only the Movies and Download folders are part of the library`() {
        assertTrue(LocalVideos.inLibrary("Movies/"))
        assertTrue(LocalVideos.inLibrary("Movies/NickOS/"))
        assertTrue(LocalVideos.inLibrary("Download/Kids/"))
        assertTrue(LocalVideos.inLibrary("movies/"))
        for (other in listOf("DCIM/Camera/", "Pictures/Screenshots/", "WhatsApp/Media/WhatsApp Video/", "MoviesPrivate/", "", null)) {
            assertEquals("folder $other", false, LocalVideos.inLibrary(other))
        }
    }

    @Test
    fun `an absolute path from an old Android gives the folder below the storage root`() {
        assertEquals("Movies/", LocalVideos.relativeOf("/storage/emulated/0/Movies/Star Harbour.mp4"))
        assertEquals("Movies/NickOS/", LocalVideos.relativeOf("/storage/emulated/0/Movies/NickOS/a.mkv"))
        assertEquals("Download/", LocalVideos.relativeOf("/storage/1A2B-3C4D/Download/a.mp4"))
        assertEquals("DCIM/Camera/", LocalVideos.relativeOf("/sdcard/DCIM/Camera/clip.mp4"))
        assertEquals(false, LocalVideos.inLibrary(LocalVideos.relativeOf("/storage/emulated/0/clip.mp4")))
        assertEquals(null, LocalVideos.relativeOf("/data/user/0/app/files/a.mp4"))
        assertEquals(null, LocalVideos.relativeOf(null))
    }

    @Test
    fun `the permissions asked for depend on the Android version`() {
        assertEquals(listOf("android.permission.READ_MEDIA_VIDEO"), LocalVideos.permissions(34))
        assertEquals(listOf("android.permission.READ_MEDIA_VIDEO"), LocalVideos.permissions(33))
        assertEquals(listOf("android.permission.READ_EXTERNAL_STORAGE"), LocalVideos.permissions(32))
        assertEquals(listOf("android.permission.READ_EXTERNAL_STORAGE"), LocalVideos.permissions(29))
        assertEquals(listOf("android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE"), LocalVideos.permissions(28))
    }
}
