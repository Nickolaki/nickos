package com.nbaltas.nickos.ui.theme

import android.graphics.drawable.Drawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ripple
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.layout.onSizeChanged
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

/** A click with no press feedback on a tablet; on a phone it gets a bounded ripple so a tap is felt. */
@Composable
fun Modifier.tap(onClick: () -> Unit): Modifier =
    clickable(interactionSource = remember { MutableInteractionSource() }, indication = if (LocalPhone.current) ripple() else null, onClick = onClick)

/** Phone type mapping for [Title] (spec §1): the tablet size in, the phone size out. Sizes under 24 are already small. */
fun phoneTitleSize(requested: Int): Int = when {
    requested < 24 -> requested
    requested <= 26 -> 18
    requested <= 32 -> 20
    requested <= 36 -> 22
    requested <= 48 -> 24
    else -> 28
}

/** Phone type mapping for [Muted]. */
fun phoneMutedSize(requested: Int): Int = when {
    requested <= 15 -> if (requested == 15) 14 else requested
    else -> 16
}

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
    val phone = LocalPhone.current
    val shape = RoundedCornerShape(if (phone) 16.dp else if (big) 22.dp else 18.dp)
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
    if (phone) {
        // Min heights, not fixed: a long label or device name wraps to two lines and the button grows.
        val minH = when {
            sub != null -> PhoneMetrics.subButtonHeight
            big -> PhoneMetrics.bigButtonHeight
            style == BtnStyle.PRIMARY -> PhoneMetrics.buttonHeight
            else -> PhoneMetrics.secondaryButtonHeight
        }
        val alpha = if (enabled) 1f else 0.45f
        Row(
            modifier
                .heightIn(min = minH)
                .clip(shape)
                .background(bg)
                .then(if (border != null) Modifier.border(border, shape) else Modifier)
                .then(if (enabled) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) Icon(leading, null, tint = fg.copy(alpha = alpha), modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f, fill = false), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text, style = body(16.sp, 700, 1.25f), color = fg.copy(alpha = alpha), maxLines = 2, textAlign = TextAlign.Center)
                if (sub != null) Text(sub, style = body(13.sp, 700, 18f / 13f), color = fg.copy(alpha = 0.75f * alpha), maxLines = 2, textAlign = TextAlign.Center)
            }
            if (trailing != null) Icon(trailing, null, tint = fg.copy(alpha = alpha), modifier = Modifier.size(20.dp))
        }
        return
    }
    if (LocalParentTablet.current) {
        val tShape = RoundedCornerShape(TabletParentMetrics.buttonRadius)
        val minH = when {
            sub != null -> TabletParentMetrics.subButtonHeight
            big -> TabletParentMetrics.bigButtonHeight
            style == BtnStyle.PRIMARY -> TabletParentMetrics.primaryButtonHeight
            else -> TabletParentMetrics.buttonHeight
        }
        val alpha = if (enabled) 1f else 0.45f
        Row(
            modifier
                .heightIn(min = minH)
                .clip(tShape)
                .background(bg)
                .then(if (border != null) Modifier.border(1.dp, if (style == BtnStyle.GHOST) c.line else c.dangerLine, tShape) else Modifier)
                .then(if (enabled) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) Icon(leading, null, tint = fg.copy(alpha = alpha), modifier = Modifier.size(TabletParentMetrics.iconSize))
            Column(Modifier.weight(1f, fill = false), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text, style = body(16.sp, 700, 1.25f), color = fg.copy(alpha = alpha), maxLines = 2, textAlign = TextAlign.Center)
                if (sub != null) Text(sub, style = body(14.sp, 700, 20f / 14f), color = fg.copy(alpha = 0.75f * alpha), maxLines = 2, textAlign = TextAlign.Center)
            }
            if (trailing != null) Icon(trailing, null, tint = fg.copy(alpha = alpha), modifier = Modifier.size(TabletParentMetrics.iconSize))
        }
        return
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

/** [label] is what a screen reader says; the icon has no text. On a phone the default 60dp circle is the 48dp target with a 22dp icon. */
@Composable
fun IconCircle(icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 60.dp, bg: Color = C.surface2, tint: Color = C.text, label: String? = null) {
    val phone = LocalPhone.current && size == 60.dp
    val tablet = !phone && LocalParentTablet.current && size == 60.dp
    val s = if (phone) PhoneMetrics.iconCircle else if (tablet) TabletParentMetrics.minTarget else size
    // Tablet parent: a 48dp box, glyph 20 (the Back glyph 22).
    val glyph = if (phone) 22.dp else if (tablet) (if (label == "Back") 22.dp else TabletParentMetrics.iconSize) else size * 0.4f
    Box(
        modifier.size(s).clip(CircleShape).background(bg).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = tint, modifier = Modifier.size(glyph)) }
}

@Composable
fun BackButton(onClick: () -> Unit) = IconCircle(Lucide.ChevronLeft, onClick, label = "Back")

/**
 * The phone screen header: back (48), a weighted title that may take two lines, and at most two 48dp actions.
 * Minimum height 64dp, 16dp gutters. Tablets keep their own headers.
 */
@Composable
fun PhoneHeader(title: String, onBack: (() -> Unit)?, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().heightIn(min = PhoneMetrics.headerHeight).padding(horizontal = PhoneMetrics.gutter),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PhoneMetrics.relatedGap),
    ) {
        if (onBack != null) BackButton(onBack)
        Text(title, style = disp(24.sp, 800, 1.15f), color = C.text, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        actions()
    }
}

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
    if (LocalPhone.current) {
        // Phone: one 64dp row, back + a small mark and name, the step label small on the right. Nothing competes with the title below.
        // The step fills the rest of the row and is right-aligned to the gutter; it never wraps. At large font sizes it drops to its own line.
        val dropped = LocalDensity.current.fontScale >= 1.3f
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = PhoneMetrics.headerHeight).padding(horizontal = PhoneMetrics.gutter),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PhoneMetrics.relatedGap),
            ) {
                if (onBack != null) BackButton(onBack)
                NickLogo(28.dp)
                Text("NickOS", style = disp(18.sp), color = C.text)
                if (step != null && !dropped) Text(step, style = body(14.sp, 600), color = C.muted, textAlign = TextAlign.End, maxLines = 1, softWrap = false, modifier = Modifier.weight(1f))
            }
            if (step != null && dropped) Text(step, style = body(14.sp, 600), color = C.muted, textAlign = TextAlign.End, maxLines = 1, softWrap = false, modifier = Modifier.fillMaxWidth().padding(horizontal = PhoneMetrics.gutter))
        }
        return
    }
    if (LocalParentTablet.current) {
        // 72dp header whose inner bounds are the content cap: logo 36, wordmark 20, back 48, step 14.
        BoxWithConstraints(Modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.Center) {
            Row(
                Modifier.width(tabletContentWidth(maxWidth.value).dp).fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TabletParentMetrics.relatedGap),
            ) {
                if (onBack != null) BackButton(onBack) else Spacer(Modifier.width(TabletParentMetrics.minTarget))
                NickLogo(36.dp)
                Text("NickOS", style = disp(20.sp, 800, 1.15f), color = C.text)
                Spacer(Modifier.weight(1f))
                if (step != null) Text(step, style = body(14.sp, 600, 20f / 14f), color = C.muted)
            }
        }
        return
    }
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
        if (pct != null) Text("$pct%", style = (if (LocalParentTablet.current) disp(textSize.sp, 800, 1.15f).copy(fontFeatureSettings = "tnum") else disp(textSize.sp, 800, 1f)), color = col, maxLines = 1, softWrap = false)
    }
}

/** Whole minutes left, rounded up: the Master's bedtime countdown and the kid's toast both use it. */
fun minutesLeft(ms: Long): Int = ((ms.coerceAtLeast(0) + 59_999) / 60_000).toInt()

// ---- Eyebrow / labels ----

@Composable
fun EyebrowText(text: String, color: Color = C.muted, modifier: Modifier = Modifier) =
    Text(text.uppercase(), style = if (LocalPhone.current || LocalParentTablet.current) PhoneEyebrow else Eyebrow, color = color, modifier = modifier)

@Composable
fun FieldLabel(text: String) =
    if (LocalPhone.current || LocalParentTablet.current) Text(text, style = body(14.sp, 700), color = C.muted, modifier = Modifier.padding(bottom = 6.dp))
    else Text(text, style = body(15.sp, 700), color = C.muted, modifier = Modifier.padding(bottom = 10.dp))

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
    val phone = LocalPhone.current
    val tablet = !phone && LocalParentTablet.current
    val shape = RoundedCornerShape(if (phone) PhoneMetrics.fieldRadius else if (tablet) TabletParentMetrics.fieldRadius else 18.dp)
    val bringIntoView = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    // Tablet parent: 52 minimum, 14 side padding, 16/22 text, 1dp border (2 when focused).
    val fieldText = if (tablet) body(16.sp, 500, 22f / 16f) else body(if (phone) 16.sp else 21.sp, 500)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = fieldText.copy(color = c.text),
        cursorBrush = SolidColor(c.accent),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard),
        // Phone: when the field gains focus (and the keyboard resizes the page) it scrolls itself into view above the keyboard.
        modifier = modifier.fillMaxWidth().bringIntoViewRequester(bringIntoView).onFocusChanged {
            focused = it.isFocused
            if (it.isFocused && phone) scope.launch { delay(250); bringIntoView.bringIntoView() }
        },
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxWidth().then(if (phone) Modifier.heightIn(min = PhoneMetrics.fieldHeight) else if (tablet) Modifier.heightIn(min = TabletParentMetrics.fieldHeight) else Modifier.height(68.dp)).clip(shape).background(c.surface2)
                    .border(if (tablet && !focused) 1.dp else 2.dp, if (focused) c.accent else c.line, shape).padding(horizontal = if (phone) 16.dp else if (tablet) 14.dp else 22.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty() && placeholder.isNotEmpty()) Text(placeholder, style = if (tablet) fieldText else body(if (phone) 16.sp else 21.sp), color = c.muted)
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
    val phone = LocalPhone.current
    val gap = if (phone) 8.dp else gap
    val keyHeight = if (phone) 56.dp else keyHeight
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
        val tablet = !LocalPhone.current && LocalParentTablet.current
        Text(label, style = if (tablet) body(24.sp, 700, 28f / 24f) else body(if (LocalPhone.current) 26.sp else 34.sp, 700), color = C.text)
    }
}

@Composable
fun PinDots(filled: Int, total: Int = 4, color: Color = C.text) {
    val c = C
    val phone = LocalPhone.current
    val dot = if (phone) 16.dp else 22.dp
    Row(horizontalArrangement = Arrangement.spacedBy(if (phone) 12.dp else 22.dp)) {
        repeat(total) { i ->
            if (i < filled) Box(Modifier.size(dot).clip(CircleShape).background(color))
            else Box(Modifier.size(dot).clip(CircleShape).border(if (phone) 2.dp else 3.dp, c.muted, CircleShape))
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
    // Phone: the default slider becomes a 6dp track and 24dp thumb inside a 48dp touch box.
    // Tablet parent: the same geometry (6 / 24 / 48), and the fraction maths below uses the new thumb.
    val phone = (LocalPhone.current || LocalParentTablet.current) && thumb == 38.dp
    val trackHeight = if (phone) 6.dp else trackHeight
    val thumb = if (phone) 24.dp else thumb
    val boxHeight = if (phone) PhoneMetrics.minTarget else thumb
    var dragging by remember { mutableStateOf(false) }
    var local by remember { mutableFloatStateOf(value) }
    val shown = if (dragging) local else value
    BoxWithConstraints(modifier.fillMaxWidth().height(boxHeight)) {
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
                Modifier.align(Alignment.CenterStart).offset { IntOffset(((w - thumbPx) * shown).roundToInt(), 0) }
                    .size(thumb).shadow(6.dp, CircleShape).clip(CircleShape).background(Color.White)
            )
        }
    }
}

@Composable
fun NickSwitch(on: Boolean, enabled: Boolean = true, onToggle: (Boolean) -> Unit) {
    val c = C
    if (LocalPhone.current) {
        PhoneSwitch(on, enabled, onToggle)
        return
    }
    if (LocalParentTablet.current) {
        TabletSwitch(on, enabled, onToggle)
        return
    }
    Box(
        Modifier.size(60.dp, 34.dp).clip(RoundedCornerShape(17.dp))
            .background((if (on) c.accent else c.line).copy(alpha = if (enabled) 1f else 0.4f))
            .let { if (enabled) it.tap { onToggle(!on) } else it }
    ) {
        Box(
            Modifier.padding(4.dp).offset(x = if (on) 26.dp else 0.dp).size(26.dp).clip(CircleShape)
                .background((if (on) c.onAccent else Color(0xFFC5CBD6)).copy(alpha = if (enabled) 1f else 0.6f))
        )
    }
}

/** 52×32 track, 24dp thumb, in a 48dp-high toggleable box. The thumb and colour glide over 140ms; the state flips at once. */
@Composable
private fun PhoneSwitch(on: Boolean, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    val c = C
    val alpha = if (enabled) 1f else 0.4f
    val track by animateColorAsState(if (on) c.accent else c.line, tween(140), label = "switchTrack")
    val thumb by animateColorAsState(if (on) c.onAccent else Color(0xFFC5CBD6), tween(140), label = "switchThumb")
    val x by animateDpAsState(if (on) 20.dp else 0.dp, tween(140), label = "switchThumbX")
    Box(
        Modifier.size(52.dp, PhoneMetrics.minTarget)
            .toggleable(value = on, enabled = enabled, role = Role.Switch, onValueChange = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(52.dp, 32.dp).clip(CircleShape).background(track.copy(alpha = track.alpha * alpha))) {
            Box(
                Modifier.align(Alignment.CenterStart).padding(4.dp).offset(x = x).size(24.dp).clip(CircleShape)
                    .background(thumb.copy(alpha = thumb.alpha * (if (enabled) 1f else 0.6f)))
            )
        }
    }
}

/** Tablet parent: 48×28 track, 20dp thumb inset 4 (travel 20), in a 52×48 toggleable box. Colours fade over 120ms, the thumb moves over 140ms. */
@Composable
private fun TabletSwitch(on: Boolean, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    val c = C
    val alpha = if (enabled) 1f else 0.4f
    val track by animateColorAsState(if (on) c.accent else c.line, tween(120), label = "switchTrack")
    val thumb by animateColorAsState(if (on) c.onAccent else Color(0xFFC5CBD6), tween(120), label = "switchThumb")
    val x by animateDpAsState(if (on) 20.dp else 0.dp, tween(140), label = "switchThumbX")
    Box(
        Modifier.size(52.dp, TabletParentMetrics.minTarget)
            .toggleable(value = on, enabled = enabled, role = Role.Switch, onValueChange = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(48.dp, 28.dp).clip(CircleShape).background(track.copy(alpha = track.alpha * alpha))) {
            Box(
                Modifier.align(Alignment.CenterStart).padding(4.dp).offset(x = x).size(20.dp).clip(CircleShape)
                    .background(thumb.copy(alpha = thumb.alpha * (if (enabled) 1f else 0.6f)))
            )
        }
    }
}

@Composable
fun ProgressBar(frac: Float, modifier: Modifier = Modifier, height: Dp = 10.dp, track: Color = Color.White.copy(alpha = 0.14f), fill: Color = C.accent) {
    val height = if (height == 10.dp && LocalParentTablet.current) 6.dp else height
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
    compact: Boolean = false,
) {
    val c = C
    if (LocalPhone.current) {
        PhoneSegmented(items, selected, onSelect, modifier, compact)
        return
    }
    if (LocalParentTablet.current) {
        TabletSegmented(items, selected, onSelect, modifier, compact)
        return
    }
    Row(
        modifier.clip(RoundedCornerShape(20.dp)).background(c.surface).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEachIndexed { i, (label, icon) ->
            val on = i == selected
            Row(
                (if (compact) Modifier.weight(1f) else Modifier).height(height).clip(RoundedCornerShape(15.dp)).background(if (on) c.accent else Color.Transparent)
                    .clickable { onSelect(i) }.padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (compact) Arrangement.Center else Arrangement.spacedBy(10.dp),
            ) {
                val col = if (on) c.onAccent else c.muted
                if (icon != null) Icon(icon, if (compact) label else null, tint = col, modifier = Modifier.size(18.dp))
                if (!compact || icon == null) Text(label, style = body(18.sp, 700), color = col, maxLines = 1)
            }
        }
    }
}

/**
 * Phone segments: 2 to 4 options share the width equally (each at least 48dp high, labels may take two lines);
 * more than 4 scroll sideways instead of clipping. Selected colours fade over 120ms.
 */
@Composable
private fun PhoneSegmented(items: List<Pair<String, ImageVector?>>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier, compact: Boolean) {
    val c = C
    val scrolls = items.size > 4
    Row(
        modifier.selectableGroup().clip(RoundedCornerShape(PhoneMetrics.fieldRadius)).background(c.surface).padding(4.dp)
            .then(if (scrolls) Modifier.horizontalScroll(rememberScrollState()) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEachIndexed { i, (label, icon) ->
            val on = i == selected
            val bg by animateColorAsState(if (on) c.accent else Color.Transparent, tween(120), label = "segBg")
            val col by animateColorAsState(if (on) c.onAccent else c.muted, tween(120), label = "segText")
            Row(
                (if (scrolls) Modifier else Modifier.weight(1f)).heightIn(min = PhoneMetrics.minTarget).clip(RoundedCornerShape(10.dp)).background(bg)
                    .selectable(selected = on, role = Role.Tab) { onSelect(i) }.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            ) {
                if (icon != null) Icon(icon, if (compact) label else null, tint = col, modifier = Modifier.size(20.dp))
                if (!compact || icon == null) Text(label, style = body(14.sp, 700, 18f / 14f), color = col, maxLines = if (scrolls) 1 else 2, textAlign = TextAlign.Center)
            }
        }
    }
}

/**
 * Tablet parent segments: outer radius 16, padding 4, gap 4; items at least 48 high (56 in all), radius 12, 16/20 labels, 20dp glyphs.
 * More than 4 non-compact items scroll sideways instead of clipping. Colours fade over 120ms.
 */
@Composable
private fun TabletSegmented(items: List<Pair<String, ImageVector?>>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier, compact: Boolean) {
    val c = C
    val scrolls = !compact && items.size > 4
    Row(
        modifier.selectableGroup().clip(RoundedCornerShape(TabletParentMetrics.segmentedRadius)).background(c.surface).padding(4.dp)
            .then(if (scrolls) Modifier.horizontalScroll(rememberScrollState()) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEachIndexed { i, (label, icon) ->
            val on = i == selected
            val bg by animateColorAsState(if (on) c.accent else Color.Transparent, tween(120), label = "segBg")
            val col by animateColorAsState(if (on) c.onAccent else c.muted, tween(120), label = "segText")
            Row(
                (if (compact) Modifier.weight(1f) else Modifier).heightIn(min = TabletParentMetrics.minTarget)
                    .clip(RoundedCornerShape(TabletParentMetrics.segmentedSelectedRadius)).background(bg)
                    .selectable(selected = on, role = Role.Tab) { onSelect(i) }.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                if (icon != null) Icon(icon, if (compact) label else null, tint = col, modifier = Modifier.size(TabletParentMetrics.iconSize))
                if (!compact || icon == null) Text(label, style = body(16.sp, 700, 1.25f), color = col, maxLines = if (scrolls) 1 else 2, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
fun Chip(modifier: Modifier = Modifier, height: Dp = 44.dp, bg: Color = C.surface2, padding: PaddingValues = PaddingValues(horizontal = 18.dp), content: @Composable RowScope.() -> Unit) {
    if (LocalPhone.current) {
        // Phone pill: the height is a minimum so text can wrap; symmetric 12dp sides; 14sp text comes from the caller.
        Row(
            modifier.heightIn(min = height).clip(RoundedCornerShape(50)).background(bg)
                .padding(if (padding == PaddingValues(horizontal = 18.dp)) PaddingValues(horizontal = 12.dp) else padding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            content = content,
        )
        return
    }
    if (LocalParentTablet.current) {
        // Tablet parent: a clickable chip is at least 48 high with 14 side padding; a small passive badge (height under 40) keeps its own height.
        Row(
            modifier.heightIn(min = if (height >= 40.dp) TabletParentMetrics.minTarget else height).clip(RoundedCornerShape(50)).background(bg)
                .padding(if (padding == PaddingValues(horizontal = 18.dp)) PaddingValues(horizontal = 14.dp) else padding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
        return
    }
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
    // Tablet parent: the overlay title is a fixed 20/24 on landscape art and 18/22 on portrait art, whatever size the caller asked for.
    val tablet = LocalParentTablet.current
    var landscapeArt by remember { mutableStateOf(true) }
    Box(
        modifier.then(if (tablet) Modifier.onSizeChanged { landscapeArt = it.width > it.height } else Modifier)
            .clip(RoundedCornerShape(radius))
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
                title, style = (if (tablet) (if (landscapeArt) disp(20.sp, 800, 1.2f) else disp(18.sp, 800, 22f / 18f)) else disp(titleSize.sp, 800, 0.95f)).copy(shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 2f), 14f)),
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
    if (LocalPhone.current) Text(text, style = disp(phoneTitleSize(size).sp, 800, 1.15f), color = color, modifier = modifier, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    else if (LocalParentTablet.current) Text(text, style = disp(tabletTitleSize(size).sp, 800, 1.15f), color = color, modifier = modifier, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    else Text(text, style = disp(size.sp, 800, lineHeight), color = color, modifier = modifier, maxLines = maxLines, overflow = TextOverflow.Ellipsis)

/** On a phone the default three-line limit is lifted: essential instructions are never cut with "…". An explicit smaller limit still applies. */
@Composable
fun Muted(text: String, size: Int, modifier: Modifier = Modifier, weight: Int = 500, maxLines: Int = 3) =
    if (LocalPhone.current) Text(text, style = body(phoneMutedSize(size).sp, weight, 1.4f), color = C.muted, modifier = modifier, maxLines = if (maxLines == 3) Int.MAX_VALUE else maxLines, overflow = TextOverflow.Ellipsis)
    else if (LocalParentTablet.current) Text(text, style = body(tabletMutedSize(size).sp, weight, 1.4f), color = C.muted, modifier = modifier, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    else Text(text, style = body(size.sp, weight, 1.4f), color = C.muted, modifier = modifier, maxLines = maxLines, overflow = TextOverflow.Ellipsis)

@Composable
fun Card(modifier: Modifier = Modifier, radius: Dp = 28.dp, bg: Color = C.surface, padding: PaddingValues = PaddingValues(28.dp), content: @Composable () -> Unit) {
    val phone = LocalPhone.current
    val tablet = !phone && LocalParentTablet.current
    val r = if (phone && radius == 28.dp) PhoneMetrics.cardRadius else if (tablet && radius == 28.dp) TabletParentMetrics.cardRadius else radius
    val p = if (phone && padding == PaddingValues(28.dp)) PaddingValues(PhoneMetrics.cardPadding)
        else if (tablet && padding == PaddingValues(28.dp)) PaddingValues(TabletParentMetrics.cardPadding) else padding
    Box(modifier.clip(RoundedCornerShape(r)).background(bg).padding(p)) { content() }
}
