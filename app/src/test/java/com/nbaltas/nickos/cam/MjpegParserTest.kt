package com.nbaltas.nickos.cam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MjpegParserTest {
    private fun parse(vararg lines: String) = MjpegParser.parsePartHeaders(lines.toList())

    @Test
    fun `length and rotation parse`() {
        assertEquals(PartHeader(1234, 90, 7), parse("Content-Type: image/jpeg", "Content-Length: 1234", "X-Rotation: 90", "X-Seq: 7"))
    }

    @Test
    fun `a missing length is not a part`() {
        assertNull(parse("Content-Type: image/jpeg", "X-Rotation: 90"))
        assertNull(parse())
    }

    @Test
    fun `a zero or negative length is not a part`() {
        assertNull(parse("Content-Length: 0"))
        assertNull(parse("Content-Length: -5"))
    }

    @Test
    fun `a length that is not a number is not a part`() {
        assertNull(parse("Content-Length: lots"))
        assertNull(parse("Content-Length: 12.5"))
        assertNull(parse("Content-Length:"))
    }

    @Test
    fun `an absurd length is not a part`() {
        assertNull(parse("Content-Length: ${MjpegParser.MAX_PART + 1}"))
        assertNull(parse("Content-Length: 99999999999999"))
        assertEquals(MjpegParser.MAX_PART, parse("Content-Length: ${MjpegParser.MAX_PART}")?.length)
    }

    @Test
    fun `a missing rotation is 0`() {
        assertEquals(PartHeader(10, 0, 0), parse("Content-Length: 10"))
    }

    @Test
    fun `a nonsense rotation is 0`() {
        assertEquals(0, parse("Content-Length: 10", "X-Rotation: sideways")?.rotation)
        assertEquals(0, parse("Content-Length: 10", "X-Rotation: 720")?.rotation)
    }

    @Test
    fun `header names are case insensitive`() {
        assertEquals(PartHeader(55, 270, 0), parse("content-length: 55", "x-ROTATION:270"))
    }

    @Test
    fun `a boundary line before the headers is tolerated`() {
        assertEquals(PartHeader(1234, 180, 0), parse("", "--nickos", "Content-Type: image/jpeg", "Content-Length: 1234", "X-Rotation: 180"))
    }
}
