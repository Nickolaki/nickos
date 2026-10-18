package com.nbaltas.nickos.ui.master

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.nbaltas.nickos.ui.theme.LocalParentTablet
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Cctv
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Maximize
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.RotateCwSquare
import com.composables.icons.lucide.Thermometer
import com.composables.icons.lucide.VideoOff
import com.nbaltas.nickos.cam.CamPrefs
import com.nbaltas.nickos.cam.CamText
import com.nbaltas.nickos.cam.RtspNews
import com.nbaltas.nickos.cam.TileDisplay
import com.nbaltas.nickos.data.Camera
import com.nbaltas.nickos.data.LinkedNode
import com.nbaltas.nickos.master.MjpegClient
import com.nbaltas.nickos.ui.node.kidName
import com.nbaltas.nickos.ui.theme.Avatar
import com.nbaltas.nickos.ui.theme.BtnStyle
import com.nbaltas.nickos.ui.theme.C
import com.nbaltas.nickos.ui.theme.Chip
import com.nbaltas.nickos.ui.theme.Dot
import com.nbaltas.nickos.ui.theme.LocalPhone
import com.nbaltas.nickos.ui.theme.NickButton
import com.nbaltas.nickos.ui.theme.body
import com.nbaltas.nickos.ui.theme.tap

/** One thing the Cameras stage can show: a kid tablet's front camera, or an RTSP camera. */
sealed interface CamSource {
    val id: String
    val name: String
}

data class TabletSource(val node: LinkedNode) : CamSource {
    override val id: String get() = "t:${node.id}"
    /** "Hailey" for "Hailey's Tablet". */
    override val name: String get() = kidName(node.name)
}

data class RtspSource(val camera: Camera) : CamSource {
    override val id: String get() = "c:${camera.id}"
    override val name: String get() = camera.name
}

/**
 * One camera on the stage. It fills whatever rectangle it is given; the stage animates the
 * rectangle. The background is never black (black is what a flash looks like): a placeholder
 * with the reason always sits under the picture, and the picture fades in over it.
 *
 * [frame] is a tablet's newest picture; [rotation] the parent's extra quarter turns (a tablet)
 * or the camera's own setting (RTSP). [live] false draws an RTSP tile without a player, for
 * screenshot tests and while that camera is open full screen. [compact] is a small strip tile:
 * no buttons. [leaving]: the camera was just turned off, so the last picture stays under a scrim
 * for a moment. [retryInMs]: how long a too hot tablet still has to cool, null when unknown.
 * [onEdit] is the pencil on an RTSP camera and the rotate button on a tablet; [onAction] is the
 * button a state offers (Open Settings, Turn on again).
 */
@Composable
fun CamTile(
    source: CamSource,
    display: TileDisplay,
    frame: MjpegClient.Frame?,
    rotation: Int,
    prefs: CamPrefs,
    live: Boolean = true,
    compact: Boolean = false,
    leaving: Boolean = false,
    retryInMs: Long? = null,
    minutes: Int = prefs.maxMin,
    onTap: () -> Unit = {},
    onMaximize: () -> Unit = {},
    onEdit: () -> Unit = {},
    onAction: () -> Unit = {},
    preset: RtspNews? = null,
    onNews: (RtspNews) -> Unit = {},
    /** A tablet tile's own hold to talk microphone; null for an RTSP camera, which cannot hear anyone. */
    talk: TileTalk? = null,
) {
    val tablet = source as? TabletSource
    // The last picture outlives its stream, so it can fade out instead of vanishing.
    val last = remember(source.id) { arrayOfNulls<MjpegClient.Frame>(1) }
    if (frame != null) last[0] = frame
    // An RTSP player's news. A tile with no player ([live] false) starts from [preset] (screenshot tests) or just "no picture".
    var news by remember(source.id, live) { mutableStateOf(if (live) RtspNews.CONNECTING else preset ?: RtspNews.CONNECTING) }
    // Looking and Moved are the screen's own words about this camera and win over what the player last said.
    val shown = if (preset == RtspNews.LOOKING || preset == RtspNews.MOVED) preset else news
    val picture = if (tablet != null) (frame != null && display.showsVideo) || (leaving && last[0] != null) else shown == RtspNews.LIVE

    val phone = LocalPhone.current
    // Tablet parent tiles: radius 16, overlay controls 48 inset 8 with 4 between, passive labels 28 high at 14sp.
    val tab = LocalParentTablet.current
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(if (phone) 20.dp else if (tab) 16.dp else 22.dp)).background(C.surface2).tap(onTap).clipToBounds()) {
        Placeholder(source, display, shown, preset != null || live, compact, live, retryInMs, minutes, onAction, Modifier.align(Alignment.Center))

        if (tablet != null) {
            AnimatedVisibility(visible = picture, enter = fadeIn(tween(prefs.fadeMs)), exit = fadeOut(tween(prefs.fadeMs))) {
                last[0]?.let { TabletPicture(it, rotation, ContentScale.Crop, Modifier.fillMaxSize()) }
            }
        } else if (live) {
            // A TextureView is drawn through the view tree, so it can be faded like anything else.
            val a by animateFloatAsState(if (news == RtspNews.LIVE) 1f else 0f, tween(prefs.fadeMs), label = "rtsp")
            CameraView(
                (source as RtspSource).camera, Modifier.fillMaxSize(), muted = true, alpha = a, bare = true,
                onNews = { news = it; onNews(it) },
            )
        }

        if (leaving) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                Text(CamText.TURNING_OFF, style = body(if (compact) 14.sp else 18.sp, 700), color = Color.White)
            }
        }

        val inset = if (tab) 8.dp else 12.dp
        val badgeH = if (tab) 28.dp else 36.dp
        Chip(Modifier.align(Alignment.BottomStart).padding(inset), height = badgeH, bg = Color.Black.copy(alpha = 0.45f), padding = PaddingValues(horizontal = if (tab) 10.dp else 12.dp)) {
            Dot(if (picture && !leaving) C.online else C.offline, 10.dp)
            Text(source.name, style = body(if (tab) 14.sp else 15.sp, 700), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        // This tablet refuses talk audio until notifications are allowed on it: say so where the parent is looking.
        talk?.note?.let { note ->
            if (!compact && !leaving) Chip(Modifier.align(Alignment.BottomEnd).padding(inset), height = badgeH, bg = Color.Black.copy(alpha = 0.45f), padding = PaddingValues(horizontal = if (tab) 10.dp else 12.dp)) {
                Text(note, style = body(14.sp, 700), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        if (!compact && !leaving) {
            if (tab) {
                // One row of 48 targets with 4 between; it wraps to a second row only if the tile is too narrow, never smaller targets.
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(Modifier.align(Alignment.TopEnd).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (talk != null) TileMic(talk)
                    SmallIcon(Lucide.Maximize, onMaximize)
                    SmallIcon(if (tablet != null) Lucide.RotateCwSquare else Lucide.Pencil, onEdit)
                }
            } else Row(Modifier.align(Alignment.TopEnd).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (talk != null) TileMic(talk)
                SmallIcon(Lucide.Maximize, onMaximize)
                SmallIcon(if (tablet != null) Lucide.RotateCwSquare else Lucide.Pencil, onEdit)
            }
        }

        if (tablet != null && display == TileDisplay.HOT && !leaving) WarmBadge(
            compact,
            // Below the corner buttons (48dp tall, inset from the top) so the chip never covers them; a compact tile has none.
            if (tab) Modifier.align(Alignment.TopStart).padding(start = 8.dp, end = 8.dp, top = if (compact) 8.dp else 64.dp, bottom = 8.dp)
            else Modifier.align(Alignment.TopStart).padding(start = 12.dp, end = 12.dp, top = if (compact) 12.dp else 68.dp, bottom = 12.dp),
        )
    }
}

/** What sits under the picture: who it is and, when there is no picture, why. */
@Composable
private fun Placeholder(
    source: CamSource,
    display: TileDisplay,
    news: RtspNews,
    words: Boolean,
    compact: Boolean,
    live: Boolean,
    retryInMs: Long?,
    minutes: Int,
    onAction: () -> Unit,
    modifier: Modifier,
) {
    val tablet = (source as? TabletSource)?.node
    val tab = LocalParentTablet.current
    // A tablet tile with a button (update, turn on again) may be taller than a small tile: only that placeholder scrolls inside itself.
    val actionable = tablet != null && !compact && display in setOf(TileDisplay.UPDATE_NEEDED, TileDisplay.AUTO_OFF, TileDisplay.TOO_HOT)
    Column(
        // A strip tile is short: keep clear of the name label along its bottom edge.
        modifier.padding(start = 16.dp, end = 16.dp, top = if (compact) 8.dp else 16.dp, bottom = if (compact) 44.dp else 16.dp).widthIn(max = 360.dp)
            .then(if (tab && actionable) Modifier.verticalScroll(rememberScrollState()) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 12.dp),
    ) {
        // Tablet parent: a 40 avatar or glyph in every size.
        if (tablet != null) Avatar(tablet.name, tablet.color, tablet.avatar, if (compact || tab) 40.dp else 72.dp)
        else Icon(if (news.isFailure()) Lucide.VideoOff else Lucide.Cctv, null, tint = C.muted, modifier = Modifier.size(if (tab) 40.dp else if (compact) 28.dp else 44.dp))

        val text = when {
            tablet != null -> CamText.tile(display, tablet.name, minutes)
            // A tile with no player and nothing to say: the chip along the bottom already names the camera.
            !words -> ""
            else -> CamText.rtsp(news)
        }
        if (text.isNotEmpty()) {
            val phone = LocalPhone.current
            // Phone: the dot sits on the first line of the words (a wrapped sentence must not leave it floating mid-height).
            Row(verticalAlignment = if (phone) Alignment.Top else Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if ((tablet != null && display == TileDisplay.WARMING) || (tablet == null && news == RtspNews.LOOKING)) {
                    if (phone) Box(Modifier.padding(top = 4.dp)) { PulseDot(live) } else PulseDot(live)
                }
                if (phone) Box(Modifier.weight(1f, fill = false)) { Words(text, 14, 4) }
                else Words(text, if (compact) 14 else 18, if (compact) 2 else 3)
            }
        }
        if (tablet == null || compact) return@Column
        // Tablet parent: the helper is 14/20 and may take as many lines as it needs (the placeholder scrolls if it must).
        CamText.hint(display, tablet.name)?.let { if (LocalPhone.current) Words(it, 14, 4) else if (tab) Words(it, 14, Int.MAX_VALUE, 20f / 14) else Words(it, 15, 3) }
        when (display) {
            TileDisplay.UPDATE_NEEDED -> NickButton(CamText.OPEN_SETTINGS, onAction, style = BtnStyle.GHOST)
            // Outlined, not the filled secondary style: that fill is this tile's own background colour.
            TileDisplay.AUTO_OFF -> NickButton(CamText.TURN_ON_AGAIN, onAction, style = BtnStyle.GHOST)
            TileDisplay.TOO_HOT ->
                if (retryInMs != null && retryInMs > 0) Words(CamText.retryIn(retryInMs), if (tab) 14 else 15, 1, if (tab) 20f / 14 else 1.4f)
                else NickButton(CamText.TURN_ON_AGAIN, onAction, style = BtnStyle.GHOST)
            else -> {}
        }
    }
}

@Composable
private fun Words(text: String, size: Int, maxLines: Int, lineHeight: Float = 1.4f) =
    Text(text, style = body(size.sp, 500, lineHeight), color = C.muted, textAlign = TextAlign.Center, maxLines = maxLines, overflow = TextOverflow.Ellipsis)

/** The news states that mean "no picture and a reason the parent can act on". */
internal fun RtspNews.isFailure(): Boolean = when (this) {
    RtspNews.UNREACHABLE, RtspNews.WRONG_PASSWORD, RtspNews.CANT_PLAY, RtspNews.ERROR, RtspNews.MOVED -> true
    else -> false
}

/** The "something is happening" dot. Still when [live] is false, so a screenshot is the same every time. */
@Composable
internal fun PulseDot(live: Boolean) {
    if (!live) { Dot(C.accent, 12.dp); return }
    val a by rememberInfiniteTransition(label = "pulse").animateFloat(0.4f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse")
    Box(Modifier.alpha(a)) { Dot(C.accent, 12.dp) }
}

@Composable
internal fun WarmBadge(compact: Boolean, modifier: Modifier = Modifier) {
    if (LocalPhone.current) {
        // Phone: a fixed 36dp circle that never stretches into an oval; the tile's own words say why it is warm.
        Box(modifier.size(36.dp).clip(CircleShape).background(C.danger), contentAlignment = Alignment.Center) {
            Icon(Lucide.Thermometer, CamText.WARM_BADGE, tint = C.onAccent, modifier = Modifier.size(18.dp))
        }
        return
    }
    if (LocalParentTablet.current) {
        // Tablet parent: a passive 28 high badge with a 12sp label.
        Chip(modifier, height = 28.dp, bg = C.danger, padding = PaddingValues(horizontal = 10.dp)) {
            Icon(Lucide.Thermometer, null, tint = C.onAccent, modifier = Modifier.size(16.dp))
            if (!compact) Text(CamText.WARM_BADGE, style = body(12.sp, 700), color = C.onAccent, maxLines = 1)
        }
        return
    }
    Chip(modifier, height = 36.dp, bg = C.danger, padding = PaddingValues(horizontal = 12.dp)) {
        Icon(Lucide.Thermometer, null, tint = C.onAccent, modifier = Modifier.size(18.dp))
        if (!compact) Text(CamText.WARM_BADGE, style = body(14.sp, 700), color = C.onAccent, maxLines = 1)
    }
}

/**
 * A tablet's picture. The stream says how far the sensor's picture must be turned to stand
 * upright and the parent's rotate button adds to that. For a quarter turn the picture is laid
 * out with width and height swapped and then turned into place (the same trick as [CameraView]),
 * or a portrait picture would be squashed.
 */
@Composable
internal fun TabletPicture(frame: MjpegClient.Frame, rotation: Int, scale: ContentScale, modifier: Modifier) {
    val turn = (((frame.rotation + rotation) % 360) + 360) % 360
    val sideways = turn % 180 != 0
    val image = remember(frame.bitmap) { frame.bitmap.asImageBitmap() }
    Box(modifier.clipToBounds(), contentAlignment = Alignment.Center) {
        Image(
            image, null, contentScale = scale,
            modifier = Modifier
                .layout { m, cons ->
                    val w = cons.maxWidth; val h = cons.maxHeight
                    val p = m.measure(if (sideways) Constraints.fixed(h, w) else Constraints.fixed(w, h))
                    layout(w, h) { p.place((w - p.width) / 2, (h - p.height) / 2) }
                }
                .graphicsLayer { rotationZ = turn.toFloat() },
        )
    }
}
