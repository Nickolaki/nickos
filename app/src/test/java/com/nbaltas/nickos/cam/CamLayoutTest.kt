package com.nbaltas.nickos.cam

import com.nbaltas.nickos.cam.CamLayout.Frame
import com.nbaltas.nickos.cam.CamLayout.Mode
import com.nbaltas.nickos.cam.CamLayout.Override
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CamLayoutTest {
    private val w = 1200f
    private val h = 600f
    private val eps = 0.01f

    private fun inside(f: Frame) = f.x >= -eps && f.y >= -eps && f.right <= w + eps && f.bottom <= h + eps
    private fun overlap(a: Frame, b: Frame) = a.x < b.right - eps && b.x < a.right - eps && a.y < b.bottom - eps && b.y < a.bottom - eps

    private fun assertTidy(frames: List<Frame>) {
        frames.forEach { assertTrue("outside the stage: $it", inside(it)) }
        for (i in frames.indices) for (j in i + 1 until frames.size) {
            assertFalse("frames $i and $j overlap", overlap(frames[i], frames[j]))
        }
    }

    @Test
    fun `auto picks the mode from how many cameras are showing`() {
        assertEquals(Mode.SINGLE, CamLayout.modeFor(1, Override.AUTO, false))
        assertEquals(Mode.PAIR, CamLayout.modeFor(2, Override.AUTO, false))
        assertEquals(Mode.GRID, CamLayout.modeFor(3, Override.AUTO, false))
        assertEquals(Mode.GRID, CamLayout.modeFor(4, Override.AUTO, false))
        assertEquals(Mode.SPOTLIGHT, CamLayout.modeFor(5, Override.AUTO, false))
        assertEquals(Mode.SPOTLIGHT, CamLayout.modeFor(8, Override.AUTO, false))
    }

    @Test
    fun `the parent's override wins over auto`() {
        assertEquals(Mode.GRID, CamLayout.modeFor(2, Override.GRID, false))
        assertEquals(Mode.GRID, CamLayout.modeFor(8, Override.GRID, false))
        assertEquals(Mode.GRID, CamLayout.modeFor(8, Override.GRID, true))
        assertEquals(Mode.SPOTLIGHT, CamLayout.modeFor(2, Override.SPOTLIGHT, false))
        assertEquals(Mode.SPOTLIGHT, CamLayout.modeFor(4, Override.SPOTLIGHT, false))
    }

    @Test
    fun `tapping a tile in auto makes it the spotlight`() {
        assertEquals(Mode.SPOTLIGHT, CamLayout.modeFor(2, Override.AUTO, true))
        assertEquals(Mode.SPOTLIGHT, CamLayout.modeFor(4, Override.AUTO, true))
    }

    @Test
    fun `one camera is always single, whatever is chosen`() {
        assertEquals(Mode.SINGLE, CamLayout.modeFor(1, Override.SPOTLIGHT, false))
        assertEquals(Mode.SINGLE, CamLayout.modeFor(1, Override.GRID, false))
        assertEquals(Mode.SINGLE, CamLayout.modeFor(1, Override.AUTO, true))
    }

    @Test
    fun `single fills the stage`() {
        assertEquals(listOf(Frame(0f, 0f, w, h)), CamLayout.rects(Mode.SINGLE, 1, w, h))
    }

    @Test
    fun `no cameras is no frames`() {
        assertTrue(CamLayout.rects(Mode.GRID, 0, w, h).isEmpty())
    }

    @Test
    fun `a pair is side by side when wide`() {
        val f = CamLayout.rects(Mode.PAIR, 2, w, h, gap = 20f, wide = true)
        assertTidy(f)
        assertEquals(f[0].y, f[1].y, eps)
        assertEquals(590f, f[0].w, eps)
        assertEquals(f[0].w, f[1].w, eps)
        assertEquals(h, f[0].h, eps)
        assertEquals(20f, f[1].x - f[0].right, eps)
    }

    @Test
    fun `a pair is stacked when tall`() {
        val f = CamLayout.rects(Mode.PAIR, 2, w, h, gap = 20f, wide = false)
        assertTidy(f)
        assertEquals(f[0].x, f[1].x, eps)
        assertEquals(w, f[0].w, eps)
        assertEquals(290f, f[0].h, eps)
        assertEquals(f[0].h, f[1].h, eps)
        assertEquals(20f, f[1].y - f[0].bottom, eps)
    }

    @Test
    fun `a grid of three keeps three equal cells and leaves the fourth empty`() {
        val three = CamLayout.rects(Mode.GRID, 3, w, h, gap = 20f)
        val four = CamLayout.rects(Mode.GRID, 4, w, h, gap = 20f)
        assertTidy(three)
        assertTidy(four)
        assertEquals(3, three.size)
        three.forEach { assertEquals(590f, it.w, eps); assertEquals(290f, it.h, eps) }
        // The three tiles do not move when the fourth camera arrives.
        assertEquals(three, four.take(3))
        assertEquals(Frame(610f, 310f, 590f, 290f), four[3])
    }

    @Test
    fun `a grid goes row by row with the gap between cells`() {
        val f = CamLayout.rects(Mode.GRID, 6, w, h, gap = 30f)
        assertTidy(f)
        // 3 columns, 2 rows.
        assertEquals(380f, f[0].w, eps)
        assertEquals(285f, f[0].h, eps)
        assertEquals(30f, f[1].x - f[0].right, eps)
        assertEquals(30f, f[3].y - f[0].bottom, eps)
        assertEquals(f[0].x, f[3].x, eps)
    }

    @Test
    fun `a phone grid never goes past two columns`() {
        val f = CamLayout.rects(Mode.GRID, 5, 400f, 900f, gap = 10f, wide = false, maxCols = 2)
        assertEquals(195f, f[0].w, eps)
        assertEquals(f[0].x, f[2].x, eps)
        assertEquals(f[0].x, f[4].x, eps)
    }

    @Test
    fun `spotlight puts the first camera on top and four in the strip`() {
        val f = CamLayout.rects(Mode.SPOTLIGHT, 5, w, h, gap = 20f, wide = true)
        assertTidy(f)
        assertEquals(Frame(0f, 0f, w, h - 150f - 20f), f[0])
        val stripW = 150f * 16f / 9f
        for (i in 1..4) {
            assertEquals(stripW, f[i].w, eps)
            assertEquals(150f, f[i].h, eps)
            assertEquals(h - 150f, f[i].y, eps)
        }
        assertEquals(0f, f[1].x, eps)
        assertEquals(20f, f[2].x - f[1].right, eps)
    }

    @Test
    fun `spotlight index swaps which camera is big`() {
        val f = CamLayout.rects(Mode.SPOTLIGHT, 5, w, h, gap = 20f, wide = true, spotlightIndex = 2)
        assertTidy(f)
        assertEquals(w, f[2].w, eps)
        assertEquals(0f, f[2].y, eps)
        // The others keep their order along the strip.
        assertEquals(0f, f[0].x, eps)
        assertTrue(f[1].x > f[0].x)
        assertTrue(f[3].x > f[1].x)
        assertTrue(f[4].x > f[3].x)
        listOf(0, 1, 3, 4).forEach { assertEquals(150f, f[it].h, eps) }
    }

    @Test
    fun `a tall stage gets a shorter strip`() {
        val f = CamLayout.rects(Mode.SPOTLIGHT, 3, 600f, 1000f, gap = 20f, wide = false)
        assertEquals(120f, f[1].h, eps)
        assertEquals(1000f - 120f - 20f, f[0].h, eps)
        assertEquals(100f, CamLayout.stripHeight(wide = false, phone = true), eps)
    }

    @Test
    fun `a long strip runs off to the right and the spotlight does not shrink`() {
        val five = CamLayout.rects(Mode.SPOTLIGHT, 5, w, h, gap = 20f)
        val nine = CamLayout.rects(Mode.SPOTLIGHT, 9, w, h, gap = 20f)
        assertEquals(five[0], nine[0])
        assertTrue(nine[8].right > w)
        val stripW = 150f * 16f / 9f
        assertEquals(8 * stripW + 7 * 20f - w, CamLayout.stripOverflow(9, w, 20f, 150f), eps)
        assertEquals(0f, CamLayout.stripOverflow(5, w, 20f, 150f), eps)
        assertEquals(0f, CamLayout.stripOverflow(1, w, 20f, 150f), eps)
    }
}
