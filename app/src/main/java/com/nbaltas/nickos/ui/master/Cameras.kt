package com.nbaltas.nickos.ui.master

import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import com.composables.icons.lucide.Cctv
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Maximize
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.RotateCwSquare
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.VideoOff
import com.nbaltas.nickos.App
import com.nbaltas.nickos.data.Camera
import com.nbaltas.nickos.data.Store
import com.nbaltas.nickos.ui.theme.BackButton
import com.nbaltas.nickos.ui.theme.BtnStyle
import com.nbaltas.nickos.ui.theme.C
import com.nbaltas.nickos.ui.theme.FieldLabel
import com.nbaltas.nickos.ui.theme.Muted
import com.nbaltas.nickos.ui.theme.NickButton
import com.nbaltas.nickos.ui.theme.NickField
import com.nbaltas.nickos.ui.theme.Title
import com.nbaltas.nickos.ui.theme.body
import com.nbaltas.nickos.ui.theme.dashed
import com.nbaltas.nickos.ui.theme.tap

/**
 * Cameras on the Master (not in the design handoff; built from the Master home's card language).
 * Each tile plays its feed live and muted; tap for full screen.
 */
@Composable
fun CamerasScreen(onBack: () -> Unit) {
    val app = App.get(LocalContext.current)
    val cams by app.store.cameras.collectAsState()
    var full by remember { mutableStateOf<Camera?>(null) }
    var editing by remember { mutableStateOf<Camera?>(null) }
    BackHandler(enabled = full != null) { full = null }

    full?.let { c ->
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            CameraView(c, Modifier.fillMaxSize(), muted = false)
            Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BackButton { full = null }
                Text(c.name, style = body(22.sp, 800), color = Color.White)
            }
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().height(104.dp).padding(horizontal = 40.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            BackButton(onBack)
            Title("Cameras", 40, Modifier.weight(1f), maxLines = 1)
            // Phone width: "Add camera" leaves the title no room.
            NickButton(if (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 600) "Add" else "Add camera", { editing = Camera(Store.randomHex(6), "", "rtsp://") }, style = BtnStyle.PRIMARY, leading = Lucide.Plus)
        }
        if (cams.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(start = 40.dp, end = 40.dp, bottom = 80.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
            ) {
                Box(Modifier.size(150.dp, 110.dp).dashed(C.line, 22.dp), contentAlignment = Alignment.Center) {
                    Icon(Lucide.Cctv, null, tint = C.muted, modifier = Modifier.size(38.dp))
                }
                Title("No cameras yet", 44)
                Muted("Add a camera's stream address (for a Tapo cam: rtsp://user:password@camera-ip:554/stream2), then watch it here.", 20, Modifier.widthIn(max = 640.dp))
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(420.dp),
                contentPadding = PaddingValues(start = 40.dp, end = 40.dp, bottom = 40.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                items(cams, key = { it.id }) { c ->
                    Column(Modifier.clip(RoundedCornerShape(30.dp)).background(C.surface).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(18.dp)).background(Color.Black).clickable { full = c }) {
                            CameraView(c, Modifier.fillMaxSize(), muted = true)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(c.name, style = body(22.sp, 800), color = C.text, modifier = Modifier.weight(1f), maxLines = 1)
                            SmallIcon(Lucide.Maximize) { full = c }
                            SmallIcon(Lucide.Pencil) { editing = c }
                        }
                    }
                }
            }
        }
    }
    editing?.let { c -> CameraEditor(c, isNew = cams.none { it.id == c.id }) { editing = null } }
}

@Composable
private fun SmallIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clip(CircleShape).background(C.surface2).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
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

/** One live feed. RTSP goes over TCP (steadier on WiFi than UDP); anything else via ExoPlayer's defaults. */
@OptIn(UnstableApi::class)
@Composable
fun CameraView(c: Camera, modifier: Modifier, muted: Boolean) {
    val ctx = LocalContext.current
    var error by remember(c.url) { mutableStateOf<String?>(null) }
    val player = remember(c.url) {
        ExoPlayer.Builder(ctx).build().apply {
            if (c.url.startsWith("rtsp", ignoreCase = true)) {
                setMediaSource(RtspMediaSource.Factory().setForceUseRtpTcp(true).createMediaSource(MediaItem.fromUri(c.url)))
            } else setMediaItem(MediaItem.fromUri(c.url))
            volume = if (muted) 0f else 1f
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onPlayerError(e: PlaybackException) { error = "Can't connect to this camera" }
                override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_READY) error = null }
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
                .graphicsLayer { rotationZ = c.rotation.toFloat() },
        )
        error?.let {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Lucide.VideoOff, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(36.dp))
                Text(it, style = body(16.sp, 600), color = Color.White.copy(alpha = 0.8f))
            }
        }
    }
}
