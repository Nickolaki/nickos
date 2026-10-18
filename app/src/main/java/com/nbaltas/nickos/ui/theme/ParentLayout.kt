package com.nbaltas.nickos.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** True when the parent UI has less than [PHONE_MAX_WIDTH_DP] of width. Shared components branch on it; tablets keep their values. */
val LocalPhone = staticCompositionLocalOf { false }

/** True inside the parent UI (Master and the parent steps of setup). Kid (Node) screens never see it. */
val LocalParent = staticCompositionLocalOf { false }

/** True for the parent UI at 600dp of outer width or more. Measured before any content cap or sidebar. */
val LocalParentTablet = staticCompositionLocalOf { false }

const val PHONE_MAX_WIDTH_DP = 600

/** The width rule on its own, so it can be tested without composing anything. */
fun isPhoneWidth(widthDp: Float): Boolean = widthDp < PHONE_MAX_WIDTH_DP

/** Phone spacing and sizes from the design spec (JOB-0060 §1). */
object PhoneMetrics {
    val gutter = 16.dp
    val cardGap = 12.dp
    val sectionGap = 24.dp
    val relatedGap = 8.dp
    val labelGap = 4.dp
    val cardRadius = 20.dp
    val cardPadding = 16.dp
    val mediaRadius = 16.dp
    val nestedRadius = 12.dp
    val fieldRadius = 14.dp
    val minTarget = 48.dp
    val buttonHeight = 52.dp
    val secondaryButtonHeight = 48.dp
    val bigButtonHeight = 56.dp
    val subButtonHeight = 64.dp
    val fieldHeight = 56.dp
    val headerHeight = 64.dp
    val iconCircle = 48.dp
}

/** Parent tablet spacing, sizes and width rules (JOB-0061 design §1.3, §1.4). */
object TabletParentMetrics {
    val cardPadding = 20.dp
    val cardGap = 16.dp
    val sectionGap = 24.dp
    val relatedGap = 12.dp
    val labelGap = 6.dp
    val headerHeight = 80.dp
    val contentTop = 16.dp
    val contentBottom = 24.dp
    val cardRadius = 20.dp
    val dialogRadius = 24.dp
    val mediaRadius = 16.dp
    val buttonRadius = 14.dp
    val fieldRadius = 14.dp
    val nestedRadius = 12.dp
    val segmentedRadius = 16.dp
    val segmentedSelectedRadius = 12.dp
    val minTarget = 48.dp
    val buttonHeight = 48.dp
    val primaryButtonHeight = 52.dp
    val bigButtonHeight = 56.dp
    val subButtonHeight = 64.dp
    val fieldHeight = 52.dp
    val sliderTrack = 6.dp
    val sliderThumb = 24.dp
    val iconSize = 20.dp
    const val CONTENT_MAX_DP = 1152f
}

/** Outer gutter: 24 at 600-839 of width, 32 from 840. */
fun tabletGutter(widthDp: Float): Float = if (widthDp < 840f) 24f else 32f

/** I = min(W - 2 x gutter, 1152): the width of the centred content. */
fun tabletContentWidth(widthDp: Float): Float = minOf(widthDp - 2 * tabletGutter(widthDp), TabletParentMetrics.CONTENT_MAX_DP)

/** Home: two columns when the content is at least 800 wide (and, at font scale 1.5 or more, at least 1000); else one. */
fun homeColumns(contentWidthDp: Float, fontScale: Float): Int =
    if (contentWidthDp >= (if (fontScale >= 1.5f) 1000f else 800f)) 2 else 1

/** Home card minimum height: 248 in two columns, 216 in one. */
fun homeCardMinHeight(columns: Int): Int = if (columns == 2) 248 else 216

private val titleTable = sortedMapOf(
    18 to 18, 20 to 20, 24 to 22, 25 to 22, 26 to 20, 28 to 22, 30 to 22, 32 to 24, 34 to 24, 36 to 26, 38 to 28,
    40 to 28, 44 to 30, 46 to 30, 48 to 32, 52 to 32, 56 to 34, 64 to 36, 72 to 36,
)

// requested -> (Muted output, direct body output)
private val bodyTable = sortedMapOf(
    10 to (12 to 12), 11 to (12 to 12), 12 to (12 to 12), 13 to (13 to 13), 14 to (14 to 14), 15 to (14 to 14),
    16 to (15 to 15), 17 to (15 to 15), 18 to (16 to 16), 19 to (16 to 16), 20 to (16 to 16), 21 to (16 to 16),
    22 to (16 to 16), 23 to (16 to 16), 24 to (18 to 18), 26 to (18 to 20), 28 to (18 to 20), 30 to (18 to 22),
    32 to (18 to 22), 34 to (18 to 24),
)

private val dispTable = sortedMapOf(
    18 to 18, 20 to 18, 22 to 20, 24 to 22, 28 to 24, 30 to 24, 34 to 28, 36 to 28, 40 to 32, 48 to 32, 56 to 40,
    72 to 48, 88 to 48, 104 to 48,
)

/** The value at [requested], else the row just below it (null when under the table). */
private fun <V> java.util.SortedMap<Int, V>.lookup(requested: Int): V? =
    this[requested] ?: headMap(requested).let { if (it.isEmpty()) null else it[it.lastKey()] }

/** Tablet parent type mapping for [Title] (spec §1.2): requested sp in, final sp out. A size between two rows takes the row below it; under the table it is kept. */
fun tabletTitleSize(requested: Int): Int = titleTable.lookup(requested) ?: requested

/** Tablet parent type mapping for [Muted]. Under the table: 12, the floor. */
fun tabletMutedSize(requested: Int): Int = bodyTable.lookup(requested)?.first ?: 12

/** Tablet parent mapping for a direct `body(...)` call. */
fun tabletBodySize(requested: Int): Int = bodyTable.lookup(requested)?.second ?: 12

/** Tablet parent mapping for a direct `disp(...)` call. */
fun tabletDispSize(requested: Int): Int = dispTable.lookup(requested) ?: requested

/** A direct body call at a tablet-parent call site: mapped once, Figtree, 1.35x line height. */
fun parentBody(requested: Int, weight: Int = 500): TextStyle = body(tabletBodySize(requested).sp, weight, 1.35f)

/** A direct disp call at a tablet-parent call site: mapped once, Bricolage, 1.15x line height. */
fun parentDisp(requested: Int, weight: Int = 800): TextStyle = disp(tabletDispSize(requested).sp, weight, 1.15f)

/**
 * Centres its content at [tabletContentWidth] (the content cap); the content gets that width. The cap belongs to
 * the content, not to a full-bleed background, so draw backgrounds outside of this.
 */
@Composable
fun ParentContentCap(modifier: Modifier = Modifier, content: @Composable BoxScope.(contentWidth: Dp) -> Unit) {
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val i = tabletContentWidth(maxWidth.value).coerceAtLeast(0f).dp
        Box(Modifier.width(i).fillMaxHeight()) { content(i) }
    }
}

/** What the parent calls its own device: "phone" on a phone, "tablet" otherwise. Kid devices stay "tablet". */
fun selfNoun(phone: Boolean): String = if (phone) "phone" else "tablet"

/** [selfNoun] for the current layout. */
@Composable
fun selfNoun(): String = selfNoun(LocalPhone.current)

/** Swaps "this tablet" (and "This tablet") in copy about the parent's own device when [phone]. */
fun String.selfWords(phone: Boolean): String =
    if (!phone) this else replace("this tablet", "this phone").replace("This tablet", "This phone")

/** Measures the width this content has (not the screen's orientation) and tells everything below whether it is a phone. */
@Composable
fun ParentLayoutProvider(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val phone = isPhoneWidth(maxWidth.value)
        CompositionLocalProvider(
            LocalParent provides true,
            LocalPhone provides phone,
            LocalParentTablet provides !phone,
            content = content,
        )
    }
}
