package com.nbaltas.nickos.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HttpRangeTest {

    @Test
    fun `bytes start dash to end of file`() {
        assertEquals(100L..999L, HttpRange.parse("bytes=100-", 1000))
    }

    @Test
    fun `bytes start dash end`() {
        assertEquals(0L..99L, HttpRange.parse("bytes=0-99", 1000))
    }

    @Test
    fun `start at the last byte`() {
        assertEquals(999L..999L, HttpRange.parse("bytes=999-", 1000))
    }

    @Test
    fun `start at or beyond size is unsatisfiable`() {
        assertNull(HttpRange.parse("bytes=1000-", 1000))
    }

    @Test
    fun `absent or garbage header returns null`() {
        assertNull(HttpRange.parse(null, 1000))
        assertNull(HttpRange.parse("not a range", 1000))
        assertNull(HttpRange.parse("bytes=", 1000))
    }

    @Test
    fun `suffix range is the last N bytes`() {
        assertEquals(500L..999L, HttpRange.parse("bytes=-500", 1000))
    }
}
