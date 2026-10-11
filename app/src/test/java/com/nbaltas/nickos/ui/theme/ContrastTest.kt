package com.nbaltas.nickos.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WCAG 2.x contrast checks for text drawn on a card background. A ratio below 4.5:1 means the
 * text is not reliably readable, which is exactly the bug the Node player overlay had (light
 * text on `NodePalette.toastBg`, a cream card) before TogetherPlayer.kt switched to `C.onAccent`.
 * [DarkColors.onAccent] is used directly here because `C` needs a composition to resolve, and
 * `onAccent` is the same fixed colour in both [DarkColors] and [LightColors].
 */
class ContrastTest {
    /** WCAG relative luminance, 0..1. https://www.w3.org/TR/WCAG21/#dfn-relative-luminance */
    private fun relativeLuminance(c: Color): Double {
        fun channel(v: Float): Double {
            val s = v.toDouble()
            return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    }

    /** WCAG contrast ratio, always >= 1.0. */
    private fun contrastRatio(a: Color, b: Color): Double {
        val l1 = relativeLuminance(a)
        val l2 = relativeLuminance(b)
        val lighter = maxOf(l1, l2)
        val darker = minOf(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    @Test
    fun `node player overlay text is readable on its card background`() {
        val ratio = contrastRatio(DarkColors.onAccent, NodePalette.toastBg)
        assertTrue("contrast ratio was $ratio, need at least 4.5:1", ratio >= 4.5)
    }

    @Test
    fun `node player overlay secondary text is readable on its card background`() {
        val ratio = contrastRatio(NodePalette.toastSub, NodePalette.toastBg)
        assertTrue("contrast ratio was $ratio, need at least 4.5:1", ratio >= 4.5)
    }

    @Test
    fun `the old overlay colour would have failed this check`() {
        val oldOverlayText = Color(0xFFEEF1F6)
        val ratio = contrastRatio(oldOverlayText, NodePalette.toastBg)
        assertTrue("expected the old light-on-light colour to fail, ratio was $ratio", ratio < 4.5)
    }
}
