package com.nbaltas.nickos.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nbaltas.nickos.R

/** Design tokens straight from design/HANDOFF.md ("Colour (dark theme)"). */
@Immutable
data class NickColors(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val line: Color,
    val text: Color,
    val muted: Color,
    val accent: Color = Color(0xFFFFB547),
    val onAccent: Color = Color(0xFF1C1305),
    val online: Color = Color(0xFF3DDC84),
    val offline: Color = Color(0xFF5A6273),
    val danger: Color = Color(0xFFFF8A7A),
    val dangerLine: Color = Color(0xFF4A2A2A),
    val isDark: Boolean,
)

val DarkColors = NickColors(
    bg = Color(0xFF0B0E13),
    surface = Color(0xFF151A22),
    surface2 = Color(0xFF1E2430),
    line = Color(0xFF2A3140),
    text = Color(0xFFEEF1F6),
    muted = Color(0xFFA0A8B6),
    isDark = true,
)

// The handoff only specifies dark values; this is the light swatch drawn in Settings-Theme
// (off-white ground #F3F1EC, white cards) extended to the same token set.
val LightColors = NickColors(
    bg = Color(0xFFF3F1EC),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFE6E3DC),
    line = Color(0xFFD6D2C8),
    text = Color(0xFF151A22),
    muted = Color(0xFF5E6573),
    dangerLine = Color(0xFFE9C4BE),
    danger = Color(0xFFD2503C),
    isDark = false,
)

/** Node (kid) palette: its own navy, independent of the Master theme setting. */
object NodePalette {
    val bg = Color(0xFF121433)
    val surface = Color(0xFF1D2150)
    val pinCard = Color(0xFF1A1D3F)
    val key = Color(0xFF272B57)
    val lockedBg = Color(0xFF0C0D24)
    val soft = Color(0xFFC9CCF0)
    val toastBg = Color(0xFFFFF4E0)
    val toastSub = Color(0xFF5A4520)
    /** App tile colours on the kid home screen, used in order. */
    val tiles = listOf(
        Color(0xFF2EC4B6), Color(0xFFFFB547), Color(0xFFB89CFF), Color(0xFF6FB8FF),
        Color(0xFFFF9B80), Color(0xFFB5E550), Color(0xFFFF6FB5),
    )
}

/** Kid avatar colours, chosen at setup. Text/icons on them use [OnAvatar]. */
val AvatarColors = listOf(
    Color(0xFFFF7A59), Color(0xFFFFB547), Color(0xFFB5E550), Color(0xFF2EC4B6),
    Color(0xFF4DA8FF), Color(0xFF9B7BFF), Color(0xFFFF6FB5),
)
val OnAvatar = Color(0xFF10131F)

val LocalNickColors = staticCompositionLocalOf { DarkColors }

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, weight: Int) = Font(
    res, FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Display = FontFamily(
    variable(R.font.bricolage, 500),
    variable(R.font.bricolage, 700),
    variable(R.font.bricolage, 800),
)

val Body = FontFamily(
    variable(R.font.figtree, 400),
    variable(R.font.figtree, 500),
    variable(R.font.figtree, 600),
    variable(R.font.figtree, 700),
    variable(R.font.figtree, 800),
)

fun disp(size: TextUnit, weight: Int = 800, lineHeight: Float = 1.05f) = TextStyle(
    fontFamily = Display,
    fontWeight = FontWeight(weight),
    fontSize = size,
    lineHeight = size * lineHeight,
    letterSpacing = (-0.01).em,
)

fun body(size: TextUnit, weight: Int = 500, lineHeight: Float = 1.3f) = TextStyle(
    fontFamily = Body,
    fontWeight = FontWeight(weight),
    fontSize = size,
    lineHeight = size * lineHeight,
)

val Eyebrow = TextStyle(
    fontFamily = Body,
    fontWeight = FontWeight(700),
    fontSize = 13.sp,
    letterSpacing = 0.12.em,
)

enum class ThemeMode { DARK, LIGHT, SYSTEM }

@Composable
fun NickTheme(mode: ThemeMode = ThemeMode.DARK, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    CompositionLocalProvider(LocalNickColors provides if (dark) DarkColors else LightColors, content = content)
}

val C: NickColors @Composable get() = LocalNickColors.current
