package com.nbaltas.nickos.ui.theme

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** True when the parent UI has less than [PHONE_MAX_WIDTH_DP] of width. Shared components branch on it; tablets keep their values. */
val LocalPhone = staticCompositionLocalOf { false }

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
        CompositionLocalProvider(LocalPhone provides isPhoneWidth(maxWidth.value), content = content)
    }
}
