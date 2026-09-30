package com.nbaltas.nickos.ui.theme

import android.graphics.drawable.Drawable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import coil.compose.AsyncImage
import com.composables.icons.lucide.BatteryCharging
import com.composables.icons.lucide.BatteryFull
import com.composables.icons.lucide.BatteryLow
import com.composables.icons.lucide.BatteryMedium
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.Cloud
import com.composables.icons.lucide.Delete
import com.composables.icons.lucide.Heart
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Moon
import com.composables.icons.lucide.Music
import com.composables.icons.lucide.Star
import com.composables.icons.lucide.Zap
import kotlin.math.roundToInt

// Sizes below are the design's px values at 1280×800, used 1:1 as dp (a 10" tablet is ~1280×800dp).

@Composable
fun Modifier.tap(onClick: () -> Unit): Modifier =
    clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)

enum class BtnStyle { PRIMARY, SECONDARY, GHOST, DANGER }

@Composable
fun NickButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: BtnStyle = BtnStyle.SECONDARY,
    big: Boolean = false,
    leading: ImageVector? = null,
    trailing: ImageVector? = null,
    sub: String? = null,
    enabled: Boolean = true,
) {
    val c = C
    val shape = RoundedCornerShape(if (big) 22.dp else 18.dp)
    val bg = when (style) {
        BtnStyle.PRIMARY -> c.accent
        BtnStyle.SECONDARY -> c.surface2
        else -> Color.Transparent
    }
    val fg = when (style) {
        BtnStyle.PRIMARY -> c.onAccent
        BtnStyle.DANGER -> c.danger
        else -> c.text
    }
    val border = when (style) {
        BtnStyle.GHOST -> BorderStroke(2.dp, c.line)
        BtnStyle.DANGER -> BorderStroke(2.dp, c.dangerLine)
        else -> null
    }
    Row(
        modifier
            .height(if (sub != null) 80.dp else if (big) 72.dp else 60.dp)
            .clip(shape)
            .background(bg)
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = if (big) 36.dp else 28.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val alpha = if (enabled) 1f else 0.45f
        if (leading != null) Icon(leading, null, tint = fg.copy(alpha = alpha), modifier = Modifier.size(22.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text, style = body(if (big) 21.sp else 19.sp, 700), color = fg.copy(alpha = alpha), maxLines = 1)
            if (sub != null) Text(sub, style = body(15.sp, 700), color = fg.copy(alpha = 0.75f * alpha), maxLines = 1)
        }
        if (trailing != null) Icon(trailing, null, tint = fg.copy(alpha = alpha), modifier = Modifier.size(22.dp))
    }
}

@Composable
fun IconCircle(icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 60.dp, bg: Color = C.surface2, tint: Color = C.text) {
    Box(
        modifier.size(size).clip(CircleShape).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.4f)) }
}

@Composable
fun BackButton(onClick: () -> Unit) = IconCircle(Lucide.ChevronLeft, onClick)

/** The NickOS mark: amber rounded square with a 2×2 grid, first cell solid. */
@Composable
fun NickLogo(size: Dp) {
    val c = C
    val pad = size * (25f / 128f)
    val gap = size * (10f / 128f)
    val cell = size * (38f / 128f)
    Box(Modifier.size(size).clip(RoundedCornerShape(size * (38f / 128f))).background(c.accent).padding(pad)) {
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            repeat(2) { r ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    repeat(2) { col ->
                        Box(
                            Modifier.size(cell).clip(RoundedCornerShape(cell * 0.29f))
                                .background(if (r == 0 && col == 0) c.onAccent else c.onAccent.copy(alpha = 0.35f))
                        )
                    }
                }
            }
        }
    }
}

/** Setup/secondary header: back button, small mark + "NickOS", right-aligned step label. */
@Composable
fun SetupHeader(step: String?, onBack: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().height(96.dp).padding(horizontal = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (onBack != null) BackButton(onBack) else Spacer(Modifier.width(60.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NickLogo(36.dp)
            Text("NickOS", style = disp(22.sp), color = C.text)
        }
        Spacer(Modifier.weight(1f))
        if (step != null) Text(step, style = body(17.sp, 600), color = C.muted)
    }
}

// ---- Avatars ----

val AvatarIcons: Map<String, ImageVector> = linkedMapOf(
    "star" to Lucide.Star, "heart" to Lucide.Heart, "zap" to Lucide.Zap,
    "moon" to Lucide.Moon, "cloud" to Lucide.Cloud, "music" to Lucide.Music,
)

fun avatarColor(index: Int) = AvatarColors[index.coerceIn(0, AvatarColors.lastIndex)]

@Composable
fun Avatar(name: String, colorIndex: Int, avatar: String, size: Dp, shape: Shape = CircleShape) {
    Box(Modifier.size(size).clip(shape).background(avatarColor(colorIndex)), contentAlignment = Alignment.Center) {
        val icon = AvatarIcons[avatar]
        if (icon != null) {
            Icon(icon, null, tint = OnAvatar, modifier = Modifier.size(size * 0.46f))
        } else {
            val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            Text(initial, style = disp((size.value * 0.45f).sp, 800, 1f), color = OnAvatar)
        }
    }
}

// ---- Status bits ----

@Composable
fun Dot(color: Color, size: Dp = 12.dp) = Box(Modifier.size(size).clip(CircleShape).background(color))

fun batteryIcon(pct: Int, charging: Boolean): ImageVector = when {
    charging -> Lucide.BatteryCharging
    pct < 25 -> Lucide.BatteryLow
    pct < 70 -> Lucide.BatteryMedium
    else -> Lucide.BatteryFull
}

@Composable
fun Battery(pct: Int?, charging: Boolean, textSize: Int, iconSize: Dp = 28.dp, dim: Boolean = false, color: Color? = null) {
    val c = C
    val col = color ?: when {
        pct == null -> c.muted
        pct < 25 -> c.danger
        dim -> c.muted
        else -> c.text
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(batteryIcon(pct ?: 100, charging), null, tint = col, modifier = Modifier.size(iconSize))
        Text(if (pct == null) "–" else "$pct%", style = disp(textSize.sp, 800, 1f), color = col)
    }
}

// ---- Eyebrow / labels ----

@Composable
fun EyebrowText(text: String, color: Color = C.muted, modifier: Modifier = Modifier) =
    Text(text.uppercase(), style = Eyebrow, color = color, modifier = modifier)

@Composable
fun FieldLabel(text: String) = Text(text, style = body(15.sp, 700), color = C.muted, modifier = Modifier.padding(bottom = 10.dp))

@Composable
fun NickField(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
    placeholder: String = "",
) {
    val c = C
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = body(21.sp, 500).copy(color = c.text),
        cursorBrush = SolidColor(c.accent),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard),
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxWidth().height(68.dp).clip(shape).background(c.surface2)
                    .border(2.dp, if (focused) c.accent else c.line, shape).padding(horizontal = 22.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty() && placeholder.isNotEmpty()) Text(placeholder, style = body(21.sp), color = c.muted)
                inner()
            }
        },
    )
}

// ---- Keypad ----

@Composable
fun Keypad(
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    keyHeight: Dp = 84.dp,
    gap: Dp = 14.dp,
    keyColor: Color = C.surface2,
    leftLabel: String? = null,
    onLeft: () -> Unit = {},
) {
    val c = C
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        val rows = listOf("123", "456", "789")
        rows.forEach { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                r.forEach { d -> Key(d.toString(), keyColor, keyHeight, Modifier.weight(1f)) { onDigit(d) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            if (leftLabel != null) {
                Box(Modifier.weight(1f).height(keyHeight).clip(RoundedCornerShape(22.dp)).clickable(onClick = onLeft), contentAlignment = Alignment.Center) {
                    Text(leftLabel, style = body(20.sp, 700), color = c.text)
                }
            } else Spacer(Modifier.weight(1f))
            Key("0", keyColor, keyHeight, Modifier.weight(1f)) { onDigit('0') }
            Box(Modifier.weight(1f).height(keyHeight).clip(RoundedCornerShape(22.dp)).clickable(onClick = onDelete), contentAlignment = Alignment.Center) {
                Icon(Lucide.Delete, "Delete", tint = c.text, modifier = Modifier.size(32.dp))
            }
        }
    }
}

@Composable
private fun Key(label: String, bg: Color, h: Dp, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.height(h).clip(RoundedCornerShape(22.dp)).background(bg).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, style = body(34.sp, 700), color = C.text)
    }
}

@Composable
fun PinDots(filled: Int, total: Int = 4, color: Color = C.text) {
    val c = C
    Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
        repeat(total) { i ->
            if (i < filled) Box(Modifier.size(22.dp).clip(CircleShape).background(color))
            else Box(Modifier.size(22.dp).clip(CircleShape).border(3.dp, c.muted, CircleShape))
        }
    }
}

// ---- Slider / switch / progress ----

/**
 * Track 14dp, amber fill, 38dp white thumb. [onChange] fires while dragging (for the label);
 * [onCommit] fires once on release, so a drag sends one command, not dozens (the HA seek bug).
 */
@Composable
fun NickSlider(
    value: Float,
    onChange: (Float) -> Unit,
    onCommit: (Float) -> Unit,
    modifier: Modifier = Modifier,
    trackHeight: Dp = 14.dp,
    thumb: Dp = 38.dp,
    trackColor: Color = C.line,
) {
    val c = C
    var dragging by remember { mutableStateOf(false) }
    var local by remember { mutableFloatStateOf(value) }
    val shown = if (dragging) local else value
    BoxWithConstraints(modifier.fillMaxWidth().height(thumb)) {
        val w = constraints.maxWidth.toFloat()
        val thumbPx = with(LocalDensity.current) { thumb.toPx() }
        fun frac(x: Float) = ((x - thumbPx / 2) / (w - thumbPx)).coerceIn(0f, 1f)
        Box(
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { o -> val f = frac(o.x); local = f; onChange(f); onCommit(f) }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { o -> dragging = true; local = frac(o.x); onChange(local) },
                        onDragEnd = { dragging = false; onCommit(local) },
                        onDragCancel = { dragging = false; onCommit(local) },
                    ) { change, _ -> local = frac(change.position.x); onChange(local) }
                }
        ) {
            Box(Modifier.align(Alignment.CenterStart).fillMaxWidth().height(trackHeight).clip(RoundedCornerShape(trackHeight / 2)).background(trackColor))
            Box(
                Modifier.align(Alignment.CenterStart).fillMaxWidth(shown.coerceIn(0.001f, 1f)).height(trackHeight)
                    .clip(RoundedCornerShape(trackHeight / 2)).background(c.accent)
            )
            Box(
                Modifier.offset { IntOffset(((w - thumbPx) * shown).roundToInt(), 0) }
                    .size(thumb).shadow(6.dp, CircleShape).clip(CircleShape).background(Color.White)
            )
        }
    }
}

@Composable
fun NickSwitch(on: Boolean, onToggle: (Boolean) -> Unit) {
    val c = C
    Box(
        Modifier.size(60.dp, 34.dp).clip(RoundedCornerShape(17.dp)).background(if (on) c.accent else c.line)
            .tap { onToggle(!on) }
    ) {
        Box(
            Modifier.padding(4.dp).offset(x = if (on) 26.dp else 0.dp).size(26.dp).clip(CircleShape)
                .background(if (on) c.onAccent else Color(0xFFC5CBD6))
        )
    }
}

@Composable
fun ProgressBar(frac: Float, modifier: Modifier = Modifier, height: Dp = 10.dp, track: Color = Color.White.copy(alpha = 0.14f), fill: Color = C.accent) {
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height)).background(track)) {
        Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).height(height).clip(RoundedCornerShape(height)).background(fill))
    }
}

// ---- Segmented tabs / chips ----

@Composable
fun Segmented(
    items: List<Pair<String, ImageVector?>>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 56.dp,
) {
    val c = C
    Row(
        modifier.clip(RoundedCornerShape(20.dp)).background(c.surface).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEachIndexed { i, (label, icon) ->
            val on = i == selected
            Row(
                Modifier.height(height).clip(RoundedCornerShape(15.dp)).background(if (on) c.accent else Color.Transparent)
                    .clickable { onSelect(i) }.padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val col = if (on) c.onAccent else c.muted
                if (icon != null) Icon(icon, null, tint = col, modifier = Modifier.size(18.dp))
                Text(label, style = body(18.sp, 700), color = col, maxLines = 1)
            }
        }
    }
}

@Composable
fun Chip(modifier: Modifier = Modifier, height: Dp = 44.dp, bg: Color = C.surface2, padding: PaddingValues = PaddingValues(horizontal = 18.dp), content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.height(height).clip(RoundedCornerShape(height / 2)).background(bg).padding(padding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

// ---- Dashed box ----

fun Modifier.dashed(color: Color, radius: Dp, width: Dp = 3.dp): Modifier = drawBehind {
    val sw = width.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(sw / 2, sw / 2),
        size = Size(size.width - sw, size.height - sw),
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(width = sw, pathEffect = PathEffect.dashPathEffect(floatArrayOf(sw * 3f, sw * 2.2f))),
    )
}

// ---- Artwork ----

/** The design's placeholder art: (top colour, bottom colour, sun colour). */
private val posterArt = listOf(
    Triple(Color(0xFF12305C), Color(0xFFE86F4A), Color(0xFFFFD27A)),
    Triple(Color(0xFF402A5E), Color(0xFFFF9F68), Color(0xFFFFE08A)),
    Triple(Color(0xFF1D4B3A), Color(0xFF8BC34A), Color(0xFFF7F3C6)),
    Triple(Color(0xFF0F3D4F), Color(0xFF2EC4B6), Color(0xFFFFE9A8)),
    Triple(Color(0xFF3A1C5C), Color(0xFFE0568F), Color(0xFFFFC1D8)),
    Triple(Color(0xFF4A2511), Color(0xFFF2A541), Color(0xFFFFF1D0)),
)

/**
 * Poster/thumbnail. Shows the real Jellyfin image when there is one; underneath (and as the
 * fallback) the design's placeholder: gradient, sun, hill and the title in white.
 */
@Composable
fun Artwork(
    url: String?,
    title: String,
    modifier: Modifier,
    radius: Dp = 14.dp,
    titleSize: Int = 17,
    showTitle: Boolean = true,
) {
    val (top, bottom, sun) = posterArt[(title.hashCode() and 0x7fffffff) % posterArt.size]
    Box(
        modifier.clip(RoundedCornerShape(radius))
            .background(Brush.verticalGradient(listOf(top, bottom)))
            .drawBehind {
                val w = size.width; val h = size.height
                val landscape = w > h
                val d = minOf(w, h) * 0.52f
                val cx = if (landscape) w / 2 else w * 0.36f + d / 2
                drawCircle(sun.copy(alpha = 0.92f), radius = d / 2, center = Offset(cx, h * 0.12f + d / 2))
                // Hill: a wide ellipse across the bottom, 130% of the width.
                drawOval(
                    top.copy(alpha = 0.88f),
                    topLeft = Offset(-0.15f * w, h - h * 0.62f + h * 0.296f),
                    size = Size(1.3f * w, h * 0.62f),
                )
            },
    ) {
        if (showTitle) {
            Text(
                title, style = disp(titleSize.sp, 800, 0.95f).copy(shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 2f), 14f)),
                color = Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
            )
        }
        if (url != null) AsyncImage(model = url, contentDescription = title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

/** A real app icon (from the package manager, or decoded from what a Node sent). */
@Composable
fun AppIconImage(bitmap: android.graphics.Bitmap?, modifier: Modifier) {
    if (bitmap != null) Image(bitmap.asImageBitmap(), null, modifier)
    else Box(modifier.clip(RoundedCornerShape(12.dp)).background(C.line))
}

fun Drawable.toSmallBitmap(px: Int = 144) = toBitmap(px, px)

@Composable
fun Title(text: String, size: Int, modifier: Modifier = Modifier, color: Color = C.text, lineHeight: Float = 1.0f, maxLines: Int = 2) =
    Text(text, style = disp(size.sp, 800, lineHeight), color = color, modifier = modifier, maxLines = maxLines, overflow = TextOverflow.Ellipsis)

@Composable
fun Muted(text: String, size: Int, modifier: Modifier = Modifier, weight: Int = 500, maxLines: Int = 3) =
    Text(text, style = body(size.sp, weight, 1.4f), color = C.muted, modifier = modifier, maxLines = maxLines, overflow = TextOverflow.Ellipsis)

@Composable
fun Card(modifier: Modifier = Modifier, radius: Dp = 28.dp, bg: Color = C.surface, padding: PaddingValues = PaddingValues(28.dp), content: @Composable () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(radius)).background(bg).padding(padding)) { content() }
}
