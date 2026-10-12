package com.nbaltas.nickos.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryTest {
    private fun v(fid: String, title: String, dur: Long = 60_000) = SyncMessages.VideoEntry(fid, title, "$title.mp4", 10_000_000, dur)

    private val videos = mapOf(
        Library.SELF to listOf(v("m1", "Star Harbour"), v("m2", "Captain Comet", dur = 0), v("e1", "Moss and Pebble S01E01 Rain"), v("e2", "Moss and Pebble S01E02")),
        "hailey" to listOf(v("m1", "Star Harbour"), v("m2", "Captain Comet", dur = 90_000), v("e1", "Moss and Pebble S01E01 Rain"), v("e2", "Moss and Pebble S01E02"), v("e3", "moss and pebble S02E01")),
        "leo" to listOf(v("m1", "Star Harbour"), v("m3", "Big Buck Bunny"), v("e1", "Moss and Pebble S01E01 Rain")),
    )
    private val items = Library.merge(videos)

    @Test
    fun `one entry per fid with every owner`() {
        assertEquals(6, items.size)
        assertEquals(setOf(Library.SELF, "hailey", "leo"), items.first { it.fid == "m1" }.owners)
        assertEquals(setOf("leo"), items.first { it.fid == "m3" }.owners)
    }

    @Test
    fun `most widely held first, then by title`() {
        assertEquals(listOf("e1", "m1", "m2", "e2", "m3", "e3"), items.map { it.fid })
    }

    @Test
    fun `the first known duration wins`() {
        assertEquals(90_000, items.first { it.fid == "m2" }.durMs)
    }

    @Test
    fun `movies and shows are split by the episode code`() {
        assertEquals(setOf("m1", "m2", "m3"), Library.movies(items).map { it.fid }.toSet())
        val shows = Library.shows(items)
        assertEquals(1, shows.size)
        assertEquals("Moss and Pebble", shows[0].name)
        assertEquals(listOf("e1", "e2", "e3"), shows[0].episodes.map { it.fid })
    }

    @Test
    fun `a bare episode code with no show name is a movie`() {
        val only = Library.merge(mapOf(Library.SELF to listOf(v("x", "S01E02"))))
        assertNull(only[0].episode)
    }

    @Test
    fun `owned by one tablet`() {
        assertEquals(setOf("m1", "m3", "e1"), Library.ownedBy(items, "leo").map { it.fid }.toSet())
    }

    @Test
    fun `next episode needs every tablet in the session to have it`() {
        assertEquals("e2", Library.next(items, "e1", setOf("hailey"))?.fid)
        assertEquals("e3", Library.next(items, "e2", setOf("hailey"))?.fid)
        assertNull("Leo lacks episode 2", Library.next(items, "e1", setOf("hailey", "leo")))
        assertNull("last episode", Library.next(items, "e3", setOf("hailey")))
        assertNull("a movie", Library.next(items, "m1", setOf("hailey")))
        assertTrue(Library.next(items, "nope", setOf("hailey")) == null)
    }
}
