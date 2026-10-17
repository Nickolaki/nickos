package com.nbaltas.nickos.cam

import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * Where each camera tile sits on the Cameras stage. Plain floats in, plain floats out, so the
 * screen only has to animate each tile towards the rectangle it is given here.
 */
object CamLayout {
    enum class Mode { SINGLE, PAIR, GRID, SPOTLIGHT }

    /** The parent's choice in the header. AUTO picks by how many cameras are showing. */
    enum class Override { AUTO, GRID, SPOTLIGHT }

    data class Frame(val x: Float, val y: Float, val w: Float, val h: Float) {
        val right: Float get() = x + w
        val bottom: Float get() = y + h
    }

    /** [spotlit]: the parent tapped a tile to make it the big one. */
    fun modeFor(n: Int, override: Override, spotlit: Boolean): Mode = when {
        n <= 1 -> Mode.SINGLE
        override == Override.GRID -> Mode.GRID
        override == Override.SPOTLIGHT || spotlit -> Mode.SPOTLIGHT
        n == 2 -> Mode.PAIR
        n <= 4 -> Mode.GRID
        else -> Mode.SPOTLIGHT
    }

    /** Height of the spotlight strip, in the same unit as everything else here (the caller scales). */
    fun stripHeight(wide: Boolean, phone: Boolean = false): Float = if (phone) 100f else if (wide) 150f else 120f

    /** How far the spotlight strip runs past the right edge: what there is to scroll. */
    fun stripOverflow(n: Int, w: Float, gap: Float, stripH: Float): Float {
        val k = n - 1
        if (k <= 0) return 0f
        val tile = stripH * 16f / 9f
        return (k * tile + (k - 1) * gap - w).coerceAtLeast(0f)
    }

    /**
     * One frame per source, in source order, inside a stage of [w] by [h]. [spotlightIndex] is
     * the source that is big in SPOTLIGHT. [maxCols] caps the grid (2 on a phone).
     */
    fun rects(
        mode: Mode,
        n: Int,
        w: Float,
        h: Float,
        gap: Float = 20f,
        wide: Boolean = true,
        spotlightIndex: Int = 0,
        stripH: Float = stripHeight(wide),
        maxCols: Int = Int.MAX_VALUE,
        /** Phone: three feeds are one full-width tile over two half-width ones, not a 2x2 grid with a hole. */
        stackThree: Boolean = false,
    ): List<Frame> {
        if (n <= 0) return emptyList()
        if (n == 1) return listOf(Frame(0f, 0f, w, h))
        return when (mode) {
            Mode.SINGLE -> List(n) { Frame(0f, 0f, w, h) }
            Mode.PAIR -> if (n == 2) pair(w, h, gap, wide) else grid(n, w, h, gap, maxCols, stackThree)
            Mode.GRID -> grid(n, w, h, gap, maxCols, stackThree)
            Mode.SPOTLIGHT -> spotlight(n, w, h, gap, spotlightIndex.coerceIn(0, n - 1), stripH)
        }
    }

    private fun pair(w: Float, h: Float, gap: Float, wide: Boolean): List<Frame> =
        if (wide) {
            val cw = (w - gap) / 2f
            listOf(Frame(0f, 0f, cw, h), Frame(cw + gap, 0f, cw, h))
        } else {
            val ch = (h - gap) / 2f
            listOf(Frame(0f, 0f, w, ch), Frame(0f, ch + gap, w, ch))
        }

    /** Equal cells, row by row. A short last row leaves its cells empty: a stretched tile would jump when the next camera arrives. */
    private fun grid(n: Int, w: Float, h: Float, gap: Float, maxCols: Int, stackThree: Boolean): List<Frame> {
        val cols = ceil(sqrt(n.toDouble())).toInt().coerceIn(1, maxCols.coerceAtLeast(1))
        val rows = ceil(n / cols.toDouble()).toInt()
        // Three feeds on a phone: the first is one full-width tile, the other two share the row below, so no cell is left empty.
        if (cols == 2 && n == 3 && stackThree) {
            val rh = (h - gap) / 2f
            val half = (w - gap) / 2f
            return listOf(Frame(0f, 0f, w, rh), Frame(0f, rh + gap, half, rh), Frame(half + gap, rh + gap, half, rh))
        }
        val cw = (w - gap * (cols - 1)) / cols
        val ch = (h - gap * (rows - 1)) / rows
        return List(n) { i -> Frame((i % cols) * (cw + gap), (i / cols) * (ch + gap), cw, ch) }
    }

    /** The big one on top at full width, the rest in a strip along the bottom that may run off to the right. */
    private fun spotlight(n: Int, w: Float, h: Float, gap: Float, big: Int, stripH: Float): List<Frame> {
        val tile = stripH * 16f / 9f
        val y = h - stripH
        var k = 0
        return List(n) { i ->
            if (i == big) Frame(0f, 0f, w, h - stripH - gap)
            else Frame(k++ * (tile + gap), y, tile, stripH)
        }
    }
}
