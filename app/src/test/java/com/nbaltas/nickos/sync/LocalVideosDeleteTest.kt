package com.nbaltas.nickos.sync

import android.Manifest
import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import com.nbaltas.nickos.node.LocalVideos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.android.controller.ContentProviderController
import org.robolectric.annotation.Config

/** `LocalVideos.delete` against an in-memory stand-in for MediaStore. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalVideosDeleteTest {

    /** One MediaStore video row. */
    private data class Row(val id: Long, val name: String, val folder: String, val size: Long = 50L * 1024 * 1024, val modified: Long = 1000)

    private class FakeMedia : ContentProvider() {
        val rows = ArrayList<Row>()
        /** Ids NickOS did not create: deleting them throws, as MediaStore does on Android 10+. */
        val foreign = HashSet<Long>()
        override fun onCreate() = true
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor {
            val cols = projection ?: arrayOf("_id")
            val c = MatrixCursor(cols)
            for (r in rows) {
                c.addRow(cols.map { col ->
                    when (col) {
                        MediaStore.Video.Media._ID -> r.id
                        MediaStore.Video.Media.DISPLAY_NAME -> r.name
                        MediaStore.Video.Media.SIZE -> r.size
                        MediaStore.Video.Media.DURATION -> 60_000L
                        MediaStore.Video.Media.DATE_MODIFIED -> r.modified
                        MediaStore.Video.Media.MIME_TYPE -> "video/mp4"
                        MediaStore.Video.Media.RELATIVE_PATH -> r.folder
                        MediaStore.Video.Media.IS_PENDING -> 0
                        else -> null
                    }
                })
            }
            return c
        }
        override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int {
            val id = ContentUris.parseId(uri)
            if (id in foreign) throw SecurityException("not ours")
            return if (rows.removeAll { it.id == id }) 1 else 0
        }
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
    }

    private val app get() = ApplicationProvider.getApplicationContext<android.app.Application>()
    private lateinit var media: FakeMedia
    private lateinit var videos: LocalVideos

    @Before
    fun setUp() {
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.READ_MEDIA_VIDEO)
        val ctl: ContentProviderController<FakeMedia> = org.robolectric.Robolectric.buildContentProvider(FakeMedia::class.java).create(MediaStore.AUTHORITY)
        media = ctl.get()
        videos = LocalVideos(app)
    }

    /** Adds a row and teaches the fid cache that it is movie [fid], so no file is ever opened. */
    private fun add(id: Long, fid: String, folder: String = "Movies/NickOS/", name: String = "movie$id.mp4") {
        val r = Row(id, name, folder)
        media.rows += r
        app.getSharedPreferences("nickos.fids", 0).edit().putString("$id:${r.size}:${r.modified}", "$fid:${r.size}").commit()
    }

    @Test
    fun `a row NickOS owns is deleted and leaves the scan`() {
        add(1, "aaaa")
        add(2, "bbbb")
        assertEquals(listOf("aaaa", "bbbb"), videos.scan().map { it.fid }.sorted())
        val o = videos.delete("aaaa")
        assertEquals(1, o.removed)
        assertTrue(o.ok)
        assertNull(o.err)
        assertEquals(listOf("bbbb"), videos.scan().map { it.fid })
        assertEquals(listOf(2L), media.rows.map { it.id })
    }

    @Test
    fun `two rows with one fid both go, not only the one the scan shows`() {
        add(1, "aaaa", folder = "Movies/")
        add(2, "aaaa", folder = "Download/")
        assertEquals(1, videos.scan().size) // the scan folds the pair into one entry
        val o = videos.delete("aaaa")
        assertEquals(2, o.removed)
        assertTrue(media.rows.isEmpty())
        assertTrue(videos.scan().isEmpty())
    }

    @Test
    fun `an unknown fid reports missing and removes nothing`() {
        add(1, "aaaa")
        val o = videos.delete("zzzz")
        assertEquals(0, o.removed)
        assertTrue(o.missing)
        assertEquals("missing", o.err)
        assertEquals(1, media.rows.size)
    }

    @Test
    fun `a hand-copied row that MediaStore refuses is reported as notOwned and stays`() {
        add(1, "aaaa", folder = "Download/")
        media.foreign += 1
        val o = videos.delete("aaaa")
        assertEquals(0, o.removed)
        assertEquals(1, o.notOwned)
        assertFalse(o.ok)
        assertEquals("notOwned", o.err)
        assertEquals(1, videos.scan().size)
    }

    @Test
    fun `find does not serve a deleted movie from the stale cache`() {
        add(1, "aaaa")
        assertEquals("aaaa", videos.find("aaaa")?.fid) // warms the cache
        videos.delete("aaaa")
        assertNull(videos.find("aaaa", rescan = false))
        assertNull(videos.find("aaaa"))
    }

    @Test
    fun `other folders are never touched even with a matching fid`() {
        add(1, "aaaa", folder = "DCIM/Camera/")
        val o = videos.delete("aaaa")
        assertEquals(0, o.removed)
        assertEquals(1, media.rows.size)
    }
}
