package com.nbaltas.nickos.ui.master

import android.os.SystemClock
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import com.composables.icons.lucide.Camera as CameraIcon
import com.composables.icons.lucide.Cctv
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.RotateCwSquare
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.VideoOff
import com.nbaltas.nickos.App
import com.nbaltas.nickos.cam.CamLayout
import com.nbaltas.nickos.cam.CamPlan
import com.nbaltas.nickos.cam.CamPrefs
import com.nbaltas.nickos.cam.CamText
import com.nbaltas.nickos.cam.NodeCam
import com.nbaltas.nickos.cam.TileDisplay
import com.nbaltas.nickos.data.Camera
import com.nbaltas.nickos.data.LinkedNode
import com.nbaltas.nickos.data.Store
import com.nbaltas.nickos.master.CamController
import com.nbaltas.nickos.ui.node.kidName
import com.nbaltas.nickos.ui.theme.Avatar
import com.nbaltas.nickos.ui.theme.BackButton
import com.nbaltas.nickos.ui.theme.BtnStyle
import com.nbaltas.nickos.ui.theme.C
import com.nbaltas.nickos.ui.theme.Chip
import com.nbaltas.nickos.ui.theme.FieldLabel
import com.nbaltas.nickos.ui.theme.IconCircle
import com.nbaltas.nickos.ui.theme.Muted
import com.nbaltas.nickos.ui.theme.NickButton
import com.nbaltas.nickos.ui.theme.NickField
import com.nbaltas.nickos.ui.theme.Segmented
import com.nbaltas.nickos.ui.theme.Title
import com.nbaltas.nickos.ui.theme.body
import com.nbaltas.nickos.ui.theme.dashed
import com.nbaltas.nickos.ui.theme.tap
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

/** Everything the screen knows about one linked tablet's camera, worked out once per recomposition. */
private class TabletCam(
    val node: LinkedNode,
    val armed: Boolean,
    val online: Boolean,
    val supportsCam: Boolean,
    val cam: NodeCam?,
    val display: TileDisplay,
)

/** Which tiles are on the stage, in the order they first appeared, including ones still fading out. */
private class Roster {
    val order = ArrayList<String>()
    val sources = HashMap<String, CamSource>()
    /** Where each tile last sat, so one that is leaving fades out in place. */
    val frames = HashMap<String, CamLayout.Frame>()
    /** Tiles whose fade out has finished. */
    val done = HashSet<String>()
}

/**
 * Cameras on the Master: the kid tablets' front cameras and any RTSP cameras, as tiles on one
 * stage. A chip per source turns it on or off. The layout follows how many are showing (one,
 * a pair, a grid, a spotlight with a strip) and each tile glides to its new place when that
 * changes. Tablet cameras run only while this screen is open and in front.
 *
 * [live] false is for screenshot tests: no players, no streams, nothing that moves.
 * [startSpotlight] is a source id ("t:" plus a node id, "c:" plus a camera id) to open with in the
 * spotlight, as if its tile had been tapped.
 */
@Composable
fun CamerasScreen(onBack: () -> Unit, onSettings: (Int) -> Unit = {}, live: Boolean = true, startSpotlight: String? = null) {
    val app = App.get(LocalContext.current)
    val store = app.store
    val rt = app.master
    val ctl = rt.cam
    val cams by store.cameras.collectAsState()
    val nodes by store.nodes.collectAsState()
    val armed by ctl.armed.collectAsState()
    val states by ctl.states.collectAsState()
    val online by rt.online.collectAsState()
    val status by rt.status.collectAsState()
    val hidden by store.camRtspHidden.collectAsState()
    val rotations by store.camRotation.collectAsState()
    val savedPrefs by store.camPrefs.collectAsState()
    // A screenshot has no time in it: no fades, no glides.
    val prefs = if (live) savedPrefs else savedPrefs.copy(layoutMs = 0, fadeMs = 0)
    val phone = LocalConfiguration.current.screenWidthDp < 600
    val roomy = LocalConfiguration.current.screenWidthDp >= 1000

    var override by rememberSaveable { mutableIntStateOf(0) }
    var spotlight by remember { mutableStateOf(startSpotlight) }
    var full by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Camera?>(null) }
    val now by produceState(SystemClock.elapsedRealtime(), live) {
        while (live) { delay(500); value = SystemClock.elapsedRealtime() }
    }

    // Tablet cameras are on only while this screen is in front. Pressing Home or the Master's
    // screen going off counts as leaving: a camera nobody is looking at is just a warm tablet.
    if (live) LifecycleStartEffect(ctl) {
        ctl.setViewing(true)
        onStopOrDispose { ctl.setViewing(false) }
    }
    BackHandler(enabled = full != null) { full = null }

    val tablets = nodes.map { n ->
        key(n.id) {
            val flow = remember(n.id) { ctl.frames(n.id).map { it != null }.distinctUntilChanged() }
            val hasFrame by flow.collectAsState(ctl.frames(n.id).value != null)
            val cam = states[n.id]
            val on = n.id in online
            val capable = status[n.id]?.supportsCam == true
            TabletCam(n, n.id in armed, on, capable, cam, CamPlan.display(n.id in armed, on, capable, cam, hasFrame, now))
        }
    }
    val byNode = tablets.associateBy { it.node.id }
    // On the stage: tablets the parent switched on, tablets that just turned themselves off (so the
    // reason is seen), then the RTSP cameras that are not hidden.
    val visible: List<CamSource> =
        tablets.filter { it.armed || CamPlan.lingers(it.cam) }.map { TabletSource(it.node) } +
            cams.filterNot { it.id in hidden }.map { RtspSource(it) }
    val visibleIds = visible.map { it.id }

    val roster = remember { Roster() }
    var purge by remember { mutableIntStateOf(0) }
    purge.let { } // read, so the roster is worked out again when a fade out ends
    visible.forEach { s ->
        roster.sources[s.id] = s
        roster.done -= s.id
        if (s.id !in roster.order) roster.order += s.id
    }
    roster.order.removeAll { it !in visibleIds && it in roster.done }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().height(104.dp).padding(horizontal = 40.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (phone) 10.dp else 20.dp),
            ) {
                BackButton(onBack)
                Title("Cameras", 40, Modifier.weight(1f), maxLines = 1)
                IconCircle(Lucide.Settings, { onSettings(2) }, size = if (phone) 48.dp else 60.dp)
                if (roomy) Segmented(listOf("Auto" to null, "Grid" to null, "Spotlight" to null), override, { override = it })
                // Phone width: "Add camera" leaves the title no room.
                NickButton(if (phone) "Add" else "Add camera", { editing = Camera(Store.randomHex(6), "", "rtsp://") }, style = BtnStyle.PRIMARY, leading = Lucide.Plus)
            }

            if (nodes.isEmpty() && cams.isEmpty()) {
                CenterMessage(
                    Lucide.Cctv, "No cameras yet",
                    "Add a camera's stream address (for a Tapo cam: rtsp://user:password@camera-ip:554/stream2), then watch it here. " +
                        "Linked kid tablets show up above as soon as they are updated; tap one to turn its front camera on.",
                )
                return@Column
            }

            LazyRow(
                Modifier.fillMaxWidth().padding(bottom = 20.dp),
                contentPadding = PaddingValues(horizontal = 40.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(tablets, key = { "t:${it.node.id}" }) { t ->
                    TabletChip(t) {
                        when {
                            !t.online -> {}
                            !t.supportsCam -> rt.say(CamText.updateHint(t.node.name))
                            else -> ctl.toggle(t.node.id)
                        }
                    }
                }
                items(cams, key = { "c:${it.id}" }) { c ->
                    RtspChip(c, shown = c.id !in hidden) {
                        store.setCamRtspHidden(if (c.id in hidden) hidden - c.id else hidden + c.id)
                    }
                }
            }

            if (roster.order.isEmpty()) {
                CenterMessage(Lucide.CameraIcon, "Nothing on", "Tap a tablet above to turn its camera on, or a camera to show it.")
                return@Column
            }

            BoxWithConstraints(Modifier.fillMaxSize().padding(start = 40.dp, end = 40.dp, bottom = 40.dp)) {
                val density = LocalDensity.current
                val w = constraints.maxWidth.toFloat()
                val h = constraints.maxHeight.toFloat()
                // A phone always stacks a pair and never goes past two columns.
                val wide = w > h && !phone
                val n = visible.size
                val picked = visibleIds.indexOf(spotlight)
                val mode = CamLayout.modeFor(n, CamLayout.Override.values()[override.coerceIn(0, 2)], picked >= 0)
                val big = if (mode == CamLayout.Mode.SPOTLIGHT) picked.coerceAtLeast(0) else -1
                val gap = with(density) { 20.dp.toPx() }
                val stripH = with(density) { CamLayout.stripHeight(wide, phone).dp.toPx() }
                val rects = CamLayout.rects(mode, n, w, h, gap, wide, picked.coerceAtLeast(0), stripH, if (phone) 2 else Int.MAX_VALUE)
                // Six or more sources: the strip runs off to the right and is dragged sideways. The spotlight never shrinks.
                val overflow = if (mode == CamLayout.Mode.SPOTLIGHT) CamLayout.stripOverflow(n, w, gap, stripH) else 0f
                var scrolled by remember { mutableFloatStateOf(0f) }
                val scroll = scrolled.coerceIn(0f, overflow)
                val drag = rememberDraggableState { d -> scrolled = (scrolled.coerceIn(0f, overflow) - d).coerceIn(0f, overflow) }
                // A strip tile is short and a phone's grid tile is narrow: neither has room for buttons.
                val shortBelow = with(density) { 220.dp.toPx() }
                val narrowBelow = with(density) { 300.dp.toPx() }

                Box(Modifier.fillMaxSize().clipToBounds().draggable(drag, Orientation.Horizontal, enabled = overflow > 0f)) {
                    // Never a Lazy container, and always keyed by id: a tile that left composition would
                    // lose its player (RTSP) and its place in the animation.
                    roster.order.toList().forEach { id ->
                        key(id) {
                            val src = roster.sources.getValue(id)
                            val i = visibleIds.indexOf(id)
                            val leaving = i < 0
                            val target = if (leaving) roster.frames[id] ?: CamLayout.Frame(0f, 0f, w, h) else {
                                val f = rects[i]
                                (if (big >= 0 && i != big) f.copy(x = f.x - scroll) else f).also { roster.frames[id] = it }
                            }
                            val compact = target.h < shortBelow || target.w < narrowBelow
                            StageTile(target, prefs, leaving, onGone = { roster.done += id; purge++ }) {
                                val onTap = { if (!leaving) spotlight = if (i == big) null else id }
                                when (src) {
                                    is TabletSource -> {
                                        val t = byNode[src.node.id]
                                        TabletTile(
                                            ctl, src, t?.display ?: TileDisplay.OFFLINE, t?.cam, rotations[src.node.id] ?: 0, prefs, live, compact, leaving, now,
                                            onTap = onTap,
                                            onMaximize = { full = id },
                                            onRotate = { store.setCamRotation(src.node.id, (rotations[src.node.id] ?: 0) + 90) },
                                            onAction = { if (t?.display == TileDisplay.UPDATE_NEEDED) onSettings(5) else ctl.arm(src.node.id) },
                                        )
                                    }
                                    // While this camera is open full screen its tile gives up its player: one
                                    // connection per camera, as before. The other tiles stay connected behind the
                                    // full screen view (simpler, and the small extra cost is theirs to keep).
                                    is RtspSource -> CamTile(
                                        src, TileDisplay.LIVE, null, src.camera.rotation, prefs, live = live && full != id, compact = compact, leaving = leaving,
                                        onTap = onTap, onMaximize = { full = id }, onEdit = { editing = src.camera },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        full?.let { id ->
            when (val src = roster.sources[id]) {
                is RtspSource -> FullScreen(src.name, false, { full = null }) { CameraView(src.camera, Modifier.fillMaxSize(), muted = false) }
                is TabletSource -> {
                    val t = byNode[src.node.id]
                    val frame by ctl.frames(src.node.id).collectAsState()
                    FullScreen(src.name, t?.display == TileDisplay.HOT, { full = null }) {
                        val f = frame
                        if (f != null) TabletPicture(f, rotations[src.node.id] ?: 0, ContentScale.Fit, Modifier.fillMaxSize())
                        else Text(
                            CamText.tile(t?.display ?: TileDisplay.OFFLINE, src.node.name, t?.cam?.minutes ?: prefs.maxMin),
                            style = body(20.sp, 600), color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center,
                            modifier = Modifier.align(Alignment.Center).padding(40.dp),
                        )
                    }
                }
                null -> LaunchedEffect(id) { full = null }
            }
        }
        editing?.let { c -> CameraEditor(c, isNew = cams.none { it.id == c.id }) { editing = null } }
    }
}

/** The dashed box, a title and a line, centred in what is left of the screen. */
@Composable
private fun CenterMessage(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, text: String) {
    Column(
        Modifier.fillMaxSize().padding(start = 40.dp, end = 40.dp, bottom = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        Box(Modifier.size(150.dp, 110.dp).dashed(C.line, 22.dp), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = C.muted, modifier = Modifier.size(38.dp))
        }
        Title(title, 44)
        Muted(text, 20, Modifier.widthIn(max = 640.dp), maxLines = 6)
    }
}

/** A linked tablet's chip: tap to turn its camera on or off. Lit while it is on. */
@Composable
private fun TabletChip(t: TabletCam, onTap: () -> Unit) {
    val lit = CamText.chipLit(t.armed, t.display)
    val word = CamText.chip(t.armed, t.online, t.supportsCam, t.cam, t.display)
    Chip(
        Modifier.alpha(if (t.online) 1f else 0.5f).clip(RoundedCornerShape(22.dp)).tap(onTap),
        bg = if (lit) C.accent else C.surface2, padding = PaddingValues(start = 8.dp, end = 18.dp),
    ) {
        Avatar(t.node.name, t.node.color, t.node.avatar, 28.dp)
        Text(kidName(t.node.name), style = body(17.sp, 700), color = if (lit) C.onAccent else C.text, maxLines = 1)
        if (word.isNotEmpty()) {
            if (lit) Text(word, style = body(14.sp, 500), color = C.onAccent.copy(alpha = 0.8f), maxLines = 1)
            else Muted(word, 14, maxLines = 1)
        }
    }
}

/** An RTSP camera's chip: tap to show or hide it. Lit while shown. */
@Composable
private fun RtspChip(c: Camera, shown: Boolean, onTap: () -> Unit) {
    Chip(Modifier.clip(RoundedCornerShape(22.dp)).tap(onTap), bg = if (shown) C.accent else C.surface2) {
        Icon(Lucide.Cctv, null, tint = if (shown) C.onAccent else C.muted, modifier = Modifier.size(22.dp))
        Text(c.name, style = body(17.sp, 700), color = if (shown) C.onAccent else C.text, maxLines = 1)
    }
}

/**
 * One tile's place on the stage. It glides to [target] whenever that changes, fades in when it
 * arrives, and when it is [leaving] holds for one fade (the tile shows its scrim), fades out,
 * and then says it is gone.
 */
@Composable
private fun StageTile(target: CamLayout.Frame, prefs: CamPrefs, leaving: Boolean, onGone: () -> Unit, content: @Composable () -> Unit) {
    val rect by animateRectAsState(
        Rect(target.x, target.y, target.right, target.bottom),
        // Off means off: snap leaves no frame in between.
        if (prefs.layoutMs == 0) snap() else tween(prefs.layoutMs, easing = FastOutSlowInEasing),
        label = "tile",
    )
    val shown = remember { MutableTransitionState(prefs.fadeMs == 0) }
    var faded by remember { mutableStateOf(false) }
    val gone by rememberUpdatedState(onGone)
    LaunchedEffect(leaving) {
        if (leaving) {
            delay(prefs.fadeMs.toLong())
            faded = true
            delay(prefs.fadeMs.toLong() + 50)
            gone()
        } else faded = false
    }
    shown.targetState = !faded
    val density = LocalDensity.current
    Box(
        Modifier.offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
            .size(with(density) { rect.width.coerceAtLeast(0f).toDp() }, with(density) { rect.height.coerceAtLeast(0f).toDp() })
    ) {
        AnimatedVisibility(visibleState = shown, enter = fadeIn(tween(prefs.fadeMs)), exit = fadeOut(tween(prefs.fadeMs))) { content() }
    }
}

/** A tablet's tile. The stream is read here and nowhere above, so a new picture redraws this tile and not the whole screen. */
@Composable
private fun TabletTile(
    ctl: CamController,
    src: TabletSource,
    display: TileDisplay,
    cam: NodeCam?,
    rotation: Int,
    prefs: CamPrefs,
    live: Boolean,
    compact: Boolean,
    leaving: Boolean,
    now: Long,
    onTap: () -> Unit,
    onMaximize: () -> Unit,
    onRotate: () -> Unit,
    onAction: () -> Unit,
) {
    val frame by ctl.frames(src.node.id).collectAsState()
    // The tablet's "try again at" is on its own clock; retryAtLocal has already moved it onto this one.
    val retryIn = if (display == TileDisplay.TOO_HOT) cam?.retryAtLocal?.let { it - now } else null
    CamTile(
        src, display, frame, rotation, prefs, live, compact, leaving, retryIn, cam?.minutes ?: prefs.maxMin,
        onTap = onTap, onMaximize = onMaximize, onEdit = onRotate, onAction = onAction,
    )
}

/** One source over the whole screen, on black, with a way back. */
@Composable
private fun FullScreen(name: String, warm: Boolean, onBack: () -> Unit, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black).tap {}) {
        content()
        Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BackButton(onBack)
            Text(name, style = body(22.sp, 800), color = Color.White)
            if (warm) WarmBadge(compact = false)
        }
    }
}

@Composable
internal fun SmallIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clip(CircleShape).background(C.surface).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = C.text, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun CameraEditor(c: Camera, isNew: Boolean, onDone: () -> Unit) {
    val store = App.get(LocalContext.current).store
    var name by remember { mutableStateOf(c.name) }
    var url by remember { mutableStateOf(c.url) }
    var rotation by remember { mutableIntStateOf(c.rotation) }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).tap(onDone), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(24.dp).clip(RoundedCornerShape(30.dp)).background(C.surface).tap {}
                .verticalScroll(rememberScrollState()).padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Title(if (isNew) "Add camera" else "Edit camera", 36)
            Column { FieldLabel("Name"); NickField(name, { name = it.take(30) }, placeholder = "e.g. Hailey's cam") }
            Column { FieldLabel("Stream address"); NickField(url, { url = it.trim() }, keyboard = androidx.compose.ui.text.input.KeyboardType.Uri) }
            Muted("RTSP (Tapo, go2rtc) or an HLS/HTTP video link. Use a camera's low-res sub-stream for smoother tiles.", 16)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                NickButton("Rotate: ${rotation}°", { rotation = (rotation + 90) % 360 }, leading = Lucide.RotateCwSquare)
                Spacer(Modifier.weight(1f))
                if (!isNew) NickButton("Remove", { store.removeCamera(c.id); onDone() }, style = BtnStyle.DANGER, leading = Lucide.Trash2)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.End)) {
                NickButton("Cancel", onDone, style = BtnStyle.GHOST)
                NickButton("Save", {
                    store.saveCamera(c.copy(name = name.trim(), url = url, rotation = rotation)); onDone()
                }, style = BtnStyle.PRIMARY, enabled = name.isNotBlank() && url.length > 8)
            }
        }
    }
}

/**
 * One live feed. RTSP goes over TCP (steadier on WiFi than UDP); anything else via ExoPlayer's defaults.
 *
 * A stage tile passes [bare] (it draws its own "can't connect" under the video), fades the video
 * in with [alpha], and hears through [onPicture] when the first frame is drawn (true) or the
 * camera cannot be reached (false).
 */
@OptIn(UnstableApi::class)
@Composable
fun CameraView(c: Camera, modifier: Modifier, muted: Boolean, alpha: Float = 1f, bare: Boolean = false, onPicture: (Boolean) -> Unit = {}) {
    val ctx = LocalContext.current
    var error by remember(c.url) { mutableStateOf<String?>(null) }
    val picture by rememberUpdatedState(onPicture)
    val player = remember(c.url) {
        ExoPlayer.Builder(ctx).build().apply {
            if (c.url.startsWith("rtsp", ignoreCase = true)) {
                setMediaSource(RtspMediaSource.Factory().setForceUseRtpTcp(true).createMediaSource(MediaItem.fromUri(c.url)))
            } else setMediaItem(MediaItem.fromUri(c.url))
            volume = if (muted) 0f else 1f
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onPlayerError(e: PlaybackException) { error = CamText.RTSP_ERROR; picture(false) }
                override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_READY) error = null }
                override fun onRenderedFirstFrame() { picture(true) }
            })
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    // Cameras drop out; keep retrying quietly while the tile is on screen.
    LaunchedEffect(error) { if (error != null) { delay(5000); player.prepare(); player.play() } }
    player.volume = if (muted) 0f else 1f

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val sideways = c.rotation % 180 != 0
        AndroidView(
            factory = { TextureView(it).also { tv -> player.setVideoTextureView(tv) } },
            modifier = Modifier
                // Portrait cams: lay the view out with width/height swapped, then rotate it into place.
                .layout { m, cons ->
                    val w = cons.maxWidth; val h = cons.maxHeight
                    val p = m.measure(if (sideways) Constraints.fixed(h, w) else Constraints.fixed(w, h))
                    layout(w, h) { p.place((w - p.width) / 2, (h - p.height) / 2) }
                }
                .graphicsLayer { rotationZ = c.rotation.toFloat(); this.alpha = alpha },
        )
        if (!bare) error?.let {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Lucide.VideoOff, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(36.dp))
                Text(it, style = body(16.sp, 600), color = Color.White.copy(alpha = 0.8f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
