package com.nbaltas.nickos.ui

import com.nbaltas.nickos.ui.master.artGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The art-grid column rule from the tablet design (§2.4, §2.5): whole columns, 200-260 wide art, 16 gaps, no stretching. */
class ArtGridTest {
    @Test fun columnsAreWholeAndComeFromTheContentWidth() {
        // columns = floor((I + 16) / 216), at least 1
        assertEquals(5, artGrid(1152f).columns)
        assertEquals(4, artGrid(960f).columns)
        assertEquals(4, artGrid(896f).columns)
        assertEquals(2, artGrid(552f).columns)
        assertEquals(1, artGrid(215f).columns)
        assertEquals(1, artGrid(100f).columns)
    }

    @Test fun artIsTheWidthSharedBetweenTheColumnsButNeverOver260() {
        assertEquals(217.6f, artGrid(1152f).artWidth, 0.01f)
        assertEquals(228f, artGrid(960f).artWidth, 0.01f)
        assertEquals(212f, artGrid(896f).artWidth, 0.01f)
        // 552 wide: two columns would be 268 each, so the picture stops at 260 and the row ends short
        assertEquals(260f, artGrid(552f).artWidth, 0.01f)
        assertEquals(260f, artGrid(216f - 16f + 100f).artWidth, 0.01f)
    }

    @Test fun artIsAtLeast200WhereAColumnFits() {
        var w = 216f - 16f // 200: the narrowest content that takes one 200 column
        while (w <= 1152f) {
            val g = artGrid(w)
            assertTrue("art at I=$w is ${g.artWidth}", g.artWidth >= 200f - 0.01f)
            assertTrue("art at I=$w is ${g.artWidth}", g.artWidth <= 260f)
            assertTrue("the columns fit in I=$w", g.gridWidth <= w + 0.01f)
            w += 1f
        }
    }

    @Test fun aShortRowKeepsTheColumnWidthAndTheRestStaysEmpty() {
        // The grid is as wide as its whole columns; a row with fewer items is not stretched to fill it.
        val g = artGrid(552f)
        assertEquals(2 * 260f + 16f, g.gridWidth, 0.01f)
        assertTrue(g.gridWidth < 552f)
    }

    @Test fun posterGridUsesItsOwnMinimumAndMaximum() {
        // posters: at least 136, at most 176
        val g = artGrid(1152f, minArt = 136f, maxArt = 176f)
        assertEquals(7, g.columns)
        assertTrue(g.artWidth >= 136f && g.artWidth <= 176f)
        // 552 wide: three columns of (552 - 32) / 3
        val narrow = artGrid(552f, minArt = 136f, maxArt = 176f)
        assertEquals(3, narrow.columns)
        assertEquals(173.33f, narrow.artWidth, 0.01f)
    }
}
