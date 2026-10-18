package com.nbaltas.nickos.ui.master

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import com.nbaltas.nickos.ui.theme.LocalParentTablet
import com.nbaltas.nickos.ui.theme.LocalPhone
import com.nbaltas.nickos.ui.theme.ParentLayoutProvider
import com.nbaltas.nickos.ui.theme.PhoneMetrics
import com.nbaltas.nickos.ui.theme.TabletParentMetrics
import com.nbaltas.nickos.ui.theme.homeCardMinHeight
import com.nbaltas.nickos.ui.theme.homeColumns
import com.nbaltas.nickos.ui.theme.tabletContentWidth
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Cctv
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.House
import com.composables.icons.lucide.LayoutGrid
import com.composables.icons.lucide.Link
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Tablet
import com.composables.icons.lucide.Tv
import com.composables.icons.lucide.WifiOff
import com.nbaltas.nickos.App
import com.nbaltas.nickos.data.LinkedNode
import com.nbaltas.nickos.jellyfin.JfItem
import com.nbaltas.nickos.master.MasterRuntime
import com.nbaltas.nickos.master.NodeStatus
import com.nbaltas.nickos.master.Playback
import com.nbaltas.nickos.master.RemoteApp
import com.nbaltas.nickos.ui.common.AppDrawer
import com.nbaltas.nickos.ui.node.rememberNow
import com.nbaltas.nickos.ui.node.screenNow
import com.nbaltas.nickos.ui.setup.AddTabletScreen
import com.nbaltas.nickos.ui.setup.LinkedScreen
import com.nbaltas.nickos.ui.setup.rotate90
import com.nbaltas.nickos.ui.theme.Artwork
import com.nbaltas.nickos.ui.theme.Avatar
import com.nbaltas.nickos.ui.theme.Battery
import com.nbaltas.nickos.ui.theme.BtnStyle
import com.nbaltas.nickos.ui.theme.C
import com.nbaltas.nickos.ui.theme.Solid
import com.nbaltas.nickos.ui.theme.Chip
import com.nbaltas.nickos.ui.theme.Dot
import com.nbaltas.nickos.ui.theme.EyebrowText
import com.nbaltas.nickos.ui.theme.IconCircle
import com.nbaltas.nickos.ui.theme.Muted
import com.nbaltas.nickos.ui.theme.NickButton
import com.nbaltas.nickos.ui.theme.OnAvatar
import com.nbaltas.nickos.ui.theme.ProgressBar
import com.nbaltas.nickos.ui.theme.Title
import com.nbaltas.nickos.ui.theme.avatarColor
import com.nbaltas.nickos.ui.theme.body
import com.nbaltas.nickos.ui.theme.dashed
import com.nbaltas.nickos.ui.theme.disp
import com.nbaltas.nickos.ui.theme.toSmallBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface MScreen {
    data object Home : MScreen
    data object AddTablet : MScreen
    data class Linked(val r: MasterRuntime.PairResult) : MScreen
    data class Control(val nodeId: String, val tab: Int = 0) : MScreen
    /** [section] null: the first section on a tablet, the settings list on a phone. */
    data class Settings(val section: Int? = null) : MScreen
    data object Drawer : MScreen
    data object Cameras : MScreen
    data object QrSetup : MScreen
    data object Together : MScreen
}

/** Everything a node card / control header needs, gathered once. */
data class NodeView(val node: LinkedNode, val online: Boolean, val status: NodeStatus?, val playback: Playback?, val apps: List<RemoteApp>?) {
    val battery get() = status?.battery ?: node.lastBattery
    val fgApp get() = status?.fg?.let { pkg -> apps?.firstOrNull { it.pkg == pkg } }
}

@Composable
fun rememberNodeViews(): List<NodeView> {
    val app = App.get(LocalContext.current)
    val nodes by app.store.nodes.collectAsState()
    val online by app.master.online.collectAsState()
    val status by app.master.status.collectAsState()
    val playback by app.master.playback.collectAsState()
    val apps by app.master.apps.collectAsState()
    return nodes.map { NodeView(it, it.id in online, status[it.id], playback[it.id], apps[it.id]) }
}

@Composable
fun MasterApp() {
    val app = App.get(LocalContext.current)
    var screen by remember { mutableStateOf<MScreen>(MScreen.Home) }
    BackHandler(enabled = screen != MScreen.Home) { screen = MScreen.Home }
    val toast by app.master.toast.collectAsState()
    LaunchedEffect(toast) { if (toast != null) { delay(5000); app.master.clearToast() } }

    ParentLayoutProvider {
    val phone = LocalPhone.current
    Box(Modifier.fillMaxSize().background(C.bg)) {
        // Phone: pages without players or polling fade in over 150ms. Control, Cameras and Together are never wrapped (no fade over live feeds).
        val s = screen
        val key = if (s is MScreen.Home || s is MScreen.AddTablet || s is MScreen.Linked || s is MScreen.Settings || s is MScreen.QrSetup || s is MScreen.Drawer) s::class else null
        val fade = remember(key) { Animatable(if (phone && key != null) 0f else 1f) }
        LaunchedEffect(key) { if (fade.value < 1f) fade.animateTo(1f, tween(150)) }
        Box(Modifier.fillMaxSize().then(if (phone) Modifier.graphicsLayer { alpha = fade.value } else Modifier)) {
        when (s) {
            MScreen.Home -> MasterHome(
                onOpen = { screen = MScreen.Control(it) },
                onAdd = { screen = MScreen.AddTablet },
                onSettings = { screen = MScreen.Settings() },
                onDrawer = { screen = MScreen.Drawer },
                onUpdate = { screen = MScreen.Settings(5) },
                onCameras = { screen = MScreen.Cameras },
                onTogether = { screen = MScreen.Together },
            )
            MScreen.AddTablet -> AddTabletScreen(back = { screen = MScreen.Home }, step = "Master · Add tablet", onQr = { screen = MScreen.QrSetup }) { screen = MScreen.Linked(it) }
            MScreen.QrSetup -> QrSetupScreen(onBack = { screen = MScreen.AddTablet })
            is MScreen.Linked -> LinkedScreen(s.r, onAnother = { screen = MScreen.AddTablet }, onContinue = { screen = MScreen.Home }, continueLabel = "Done")
            is MScreen.Control -> ControlScreen(
                s.nodeId, s.tab,
                onTab = { screen = s.copy(tab = it) },
                onBack = { screen = MScreen.Home },
                onSettings = { screen = MScreen.Settings(it) },
            )
            is MScreen.Settings -> SettingsScreen(s.section, onSection = { screen = MScreen.Settings(it) }, onBack = { screen = MScreen.Home }, onAdd = { screen = MScreen.AddTablet })
            MScreen.Cameras -> CamerasScreen(onBack = { screen = MScreen.Home }, onSettings = { screen = MScreen.Settings(it) })
            MScreen.Together -> TogetherScreen(onBack = { screen = MScreen.Home }, onSettings = { screen = MScreen.Settings(it) })
            MScreen.Drawer -> {
                val ctx = LocalContext.current
                val pinned by app.store.quickApps.collectAsState()
                AppDrawer(
                    onBack = { screen = MScreen.Home },
                    onOpen = { pkg -> launchLocal(ctx, pkg) },
                    pinned = pinned,
                    onLongPress = { pkg -> app.store.setQuickApps(if (pkg in pinned) pinned - pkg else (pinned + pkg).takeLast(6)) },
                )
            }
        }
        }
        if (phone) {
            // Keep the last text while the toast fades out.
            var lastToast by remember { mutableStateOf("") }
            toast?.let { lastToast = it }
            AnimatedVisibility(toast != null, Modifier.align(Alignment.BottomCenter), enter = fadeIn(tween(120)), exit = fadeOut(tween(120))) {
                Toast(lastToast, Modifier)
            }
        } else toast?.let { Toast(it, Modifier.align(Alignment.BottomCenter)) }
    }
    }
}

fun launchLocal(ctx: android.content.Context, pkg: String) {
    ctx.packageManager.getLaunchIntentForPackage(pkg)?.let { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
private fun Toast(text: String, modifier: Modifier) {
    if (LocalPhone.current) {
        Box(
            modifier.padding(bottom = 24.dp, start = 16.dp, end = 16.dp).shadow(12.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp)).background(C.surface2).padding(horizontal = 16.dp, vertical = 12.dp)
        ) { Text(text, style = body(16.sp, 700), color = C.text) }
        return
    }
    Box(
        modifier.padding(bottom = 32.dp, start = 24.dp, end = 24.dp).shadow(20.dp, RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp)).background(C.surface2).padding(horizontal = 26.dp, vertical = 18.dp)
    ) { Text(text, style = body(19.sp, 700), color = C.text) }
}

// ---------------- Home ----------------

@Composable
internal fun MasterHome(onOpen: (String) -> Unit, onAdd: () -> Unit, onSettings: () -> Unit, onDrawer: () -> Unit, onUpdate: () -> Unit, onCameras: () -> Unit, onTogether: () -> Unit) {
    val app = App.get(LocalContext.current)
    val views = rememberNodeViews()
    val account by app.store.jellyfin.collectAsState()
    val jf = remember(account) { app.master.jellyfin() }
    val poster: ((JfItem) -> String?)? = jf?.let { j -> { i: JfItem -> j.poster(i, 400) } }
    val portrait = LocalConfiguration.current.let { it.screenHeightDp > it.screenWidthDp }
    val phone = LocalPhone.current
    val pad = if (phone) PhoneMetrics.gutter else if (portrait) 32.dp else 40.dp
    var pauseSheet by remember { mutableStateOf(false) }
    val pausedMsg by app.store.pausedMsg.collectAsState()
    val tablet = LocalParentTablet.current
    Box(Modifier.fillMaxSize()) {
    if (tablet) TabletHome(views, poster, pausedMsg != null, onOpen, onAdd, onSettings, onDrawer, onUpdate, onCameras, onTogether) { pauseSheet = true }
    else Column(Modifier.fillMaxSize()) {
        if (phone) PhoneTopBar(onSettings, onDrawer, onUpdate, onCameras, onTogether, paused = pausedMsg != null, onPause = { pauseSheet = true })
        else TopBar(pad, onSettings, onDrawer, onUpdate, onCameras, onTogether, paused = pausedMsg != null, onPause = { pauseSheet = true })
        CarNetBanner(pad, onSettings)
        if (views.isEmpty()) {
            EmptyHome(onAdd)
            return@Column
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = pad, end = pad, top = if (phone) 0.dp else 12.dp, bottom = if (phone) 24.dp else 40.dp),
            verticalArrangement = Arrangement.spacedBy(if (phone) PhoneMetrics.cardGap else 24.dp),
        ) {
            if (phone) {
                views.forEach { v -> PhoneNodeCard(v, poster, Modifier.fillMaxWidth()) { onOpen(v.node.id) } }
                AddCard(true, Modifier.fillMaxWidth(), onAdd)
            } else if (portrait) {
                views.forEach { v -> NodeCard(v, poster, Modifier.fillMaxWidth().heightIn(min = 250.dp)) { onOpen(v.node.id) } }
                AddCard(true, Modifier.fillMaxWidth().height(136.dp), onAdd)
            } else {
                // 2-column grid of 312dp cards; the Add card always takes the next free cell.
                val cells = views.map { it as Any } + "add"
                cells.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        row.forEach { cell ->
                            val m = Modifier.weight(1f).height(312.dp)
                            if (cell is NodeView) NodeCard(cell, poster, m) { onOpen(cell.node.id) } else AddCard(false, m, onAdd)
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
    if (pauseSheet) PauseAllSheet(
        // Also when another parent tablet paused: this one can then lift it with the explicit button.
        paused = pausedMsg != null || views.any { it.status?.paused == true },
        onSubmit = { on, msg -> app.master.pauseAll(on, msg, force = !on); pauseSheet = false },
        onClose = { pauseSheet = false },
    )
    }
}

/** One warning row, only when the car network itself is down (the one offline reason fixed from this tablet) and no typed hotspot is in use. */
@Composable
private fun CarNetBanner(pad: Dp, onSettings: () -> Unit) {
    val app = App.get(LocalContext.current)
    // Read so the banner recomposes when any input of offlineReasons() changes.
    app.master.carNet.state.collectAsState().value
    app.store.hotspot.collectAsState().value
    app.store.carNetOn.collectAsState().value
    app.master.online.collectAsState().value
    val now = rememberNow(1000)
    val why = carNetBannerWhy(app.master.offlineReasons(now).values) ?: return
    val shape = RoundedCornerShape(20.dp)
    if (LocalPhone.current) {
        // Compact: 20dp icon, 14sp text, 12dp padding; the whole reason stays on screen.
        Row(
            Modifier.fillMaxWidth().padding(start = pad, end = pad, bottom = 12.dp).clip(shape).background(C.surface)
                .border(2.dp, C.dangerLine, shape).clickable(role = Role.Button, onClick = onSettings).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Lucide.WifiOff, null, tint = C.danger, modifier = Modifier.size(20.dp))
            Text(carNetBanner(why), style = body(14.sp, 700, 1.3f), color = C.text, modifier = Modifier.weight(1f))
        }
        return
    }
    if (LocalParentTablet.current) {
        // Compact warning: at least 48 high, radius 12, padding 12, 20dp glyph, 14/20 text. The grid below adds the 16dp gap.
        val tShape = RoundedCornerShape(TabletParentMetrics.nestedRadius)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(tShape).background(C.surface)
                .border(1.dp, C.dangerLine, tShape).clickable(role = Role.Button, onClick = onSettings).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Lucide.WifiOff, null, tint = C.danger, modifier = Modifier.size(TabletParentMetrics.iconSize))
            Text(carNetBanner(why), style = body(14.sp, 700, 20f / 14f), color = C.text, modifier = Modifier.weight(1f))
        }
        return
    }
    Row(
        Modifier.fillMaxWidth().padding(start = pad, end = pad, bottom = 12.dp).clip(shape).background(C.surface)
            .border(2.dp, C.dangerLine, shape).clickable(onClick = onSettings).padding(horizontal = 22.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(Lucide.WifiOff, null, tint = C.danger, modifier = Modifier.size(24.dp))
        Text(carNetBanner(why), style = body(18.sp, 700), color = C.text, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun TopBar(pad: Dp, onSettings: () -> Unit, onDrawer: () -> Unit, onUpdate: () -> Unit, onCameras: () -> Unit, onTogether: () -> Unit, paused: Boolean, onPause: () -> Unit) {
    val ctx = LocalContext.current
    val app = App.get(ctx)
    val session by app.master.sync.session.collectAsState()
    val now = rememberNow(1000)
    val name by app.store.name.collectAsState()
    val quick by app.store.quickApps.collectAsState()
    val icons by produceState(emptyList<Pair<String, android.graphics.Bitmap>>(), quick) {
        value = withContext(Dispatchers.IO) {
            quick.mapNotNull { pkg -> try { pkg to ctx.packageManager.getApplicationIcon(pkg).toSmallBitmap(120) } catch (e: Exception) { null } }
        }
    }
    // Phone-width Master: drop the name chip so Cameras and Settings stay on screen.
    val phone = LocalConfiguration.current.screenWidthDp < 600
    Row(
        Modifier.fillMaxWidth().height(104.dp).padding(horizontal = pad),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (phone) 12.dp else 28.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(SimpleDateFormat("h:mm", Locale.getDefault()).format(Date(now)), style = disp(48.sp, 800, 1f), color = C.text)
            Text(SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(now)), style = body(16.sp, 600), color = C.muted)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            icons.take(if (phone) 0 else icons.size).forEach { (pkg, bmp) ->
                Box(
                    Modifier.size(60.dp).clip(RoundedCornerShape(16.dp)).background(C.surface2).clickable { launchLocal(ctx, pkg) },
                    contentAlignment = Alignment.Center,
                ) { Image(bmp.asImageBitmap(), null, Modifier.size(46.dp)) }
            }
            Box(Modifier.size(60.dp).clip(RoundedCornerShape(17.dp)).background(C.surface2).clickable(onClick = onDrawer), contentAlignment = Alignment.Center) {
                Icon(Lucide.LayoutGrid, "All apps", tint = C.text, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.weight(1f))
        val update by com.nbaltas.nickos.update.Updater.state.collectAsState()
        if (update is com.nbaltas.nickos.update.UpdateState.Available) {
            Chip(Modifier.clip(RoundedCornerShape(24.dp)).clickable { onUpdate() }, height = 48.dp, bg = C.accent) {
                Icon(Lucide.Download, null, tint = C.onAccent, modifier = Modifier.size(20.dp))
                Text("Update", style = body(16.sp, 700), color = C.onAccent)
            }
        }
        if (!phone) Chip(Modifier.clip(RoundedCornerShape(24.dp)).clickable(onClick = onSettings), height = 48.dp) {
            Icon(Lucide.Tablet, null, tint = C.text, modifier = Modifier.size(20.dp).rotate90())
            Text("${name.ifBlank { "Parent's Tablet" }} · Master", style = body(16.sp, 700), color = C.text, maxLines = 1)
        }
        Chip(Modifier.clip(RoundedCornerShape(24.dp)).clickable(onClick = onPause), height = 48.dp, bg = if (paused) C.accent else C.surface2) {
            Icon(if (paused) Solid.Play else Solid.Pause, null, tint = if (paused) C.onAccent else C.text, modifier = Modifier.size(20.dp))
            if (!phone) Text(if (paused) "Resume everyone" else "Pause all", style = body(16.sp, 700), color = if (paused) C.onAccent else C.text, maxLines = 1)
        }
        IconCircle(Lucide.Cctv, onCameras)
        if (session != null) {
            Chip(Modifier.clip(RoundedCornerShape(24.dp)).clickable(onClick = onTogether), height = 48.dp, bg = C.accent) {
                Icon(Lucide.Tv, null, tint = C.onAccent, modifier = Modifier.size(20.dp))
                Text("Watching together", style = body(16.sp, 700), color = C.onAccent)
            }
        } else {
            IconCircle(Lucide.Tv, onTogether)
        }
        IconCircle(Lucide.Settings, onSettings)
    }
}

/**
 * Parent tablet Home (design §2.1): the header and the cards share one content width (min(W - 2 x gutter, 1152), centred); two columns
 * when that is at least 800 wide (one below, and one below 1000 at font scale 1.5). Cards are content-high with a 248/216 minimum.
 */
@Composable
private fun TabletHome(
    views: List<NodeView>, poster: ((JfItem) -> String?)?, paused: Boolean,
    onOpen: (String) -> Unit, onAdd: () -> Unit, onSettings: () -> Unit, onDrawer: () -> Unit, onUpdate: () -> Unit,
    onCameras: () -> Unit, onTogether: () -> Unit, onPause: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth.value
        val i = tabletContentWidth(w)
        val columns = homeColumns(i, LocalDensity.current.fontScale)
        val minH = homeCardMinHeight(columns).dp
        Column(Modifier.align(Alignment.TopCenter).width(i.dp).fillMaxHeight()) {
            TabletTopBar(w, onSettings, onDrawer, onUpdate, onCameras, onTogether, paused, onPause)
            CarNetBanner(0.dp, onSettings)
            if (views.isEmpty()) {
                EmptyHome(onAdd)
                return@Column
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = TabletParentMetrics.contentTop, bottom = TabletParentMetrics.contentBottom),
                verticalArrangement = Arrangement.spacedBy(TabletParentMetrics.cardGap),
            ) {
                if (columns == 2) {
                    // The Add card always takes the next free cell; cards in a row are as high as the tallest.
                    val cells = views.map { it as Any } + "add"
                    cells.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(TabletParentMetrics.cardGap)) {
                            row.forEach { cell ->
                                val m = Modifier.weight(1f).fillMaxHeight().heightIn(min = minH)
                                if (cell is NodeView) TabletNodeCard(cell, poster, m) { onOpen(cell.node.id) } else AddCard(false, m, onAdd)
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                } else {
                    views.forEach { v -> TabletNodeCard(v, poster, Modifier.fillMaxWidth().heightIn(min = minH)) { onOpen(v.node.id) } }
                    AddCard(true, Modifier.fillMaxWidth().heightIn(min = 104.dp), onAdd)
                }
            }
        }
    }
}

/**
 * Parent tablet top bar. At 960 or more of width: one 80dp row (clock and date, the apps button, pinned apps, the self-device badge,
 * then Update / Pause / Cameras / Together / Settings). If that does not fit, the badge (and then the pinned apps) drop to a second
 * row and the actions scroll sideways; no action is ever hidden. Between 600 and 959: a 72dp row with the clock, the apps button and
 * Settings, and a row of at least 56dp under it with Pause, Cameras, Together, Update and the pinned apps in a scrolling strip.
 */
@Composable
private fun TabletTopBar(w: Float, onSettings: () -> Unit, onDrawer: () -> Unit, onUpdate: () -> Unit, onCameras: () -> Unit, onTogether: () -> Unit, paused: Boolean, onPause: () -> Unit) {
    val ctx = LocalContext.current
    val app = App.get(ctx)
    val session by app.master.sync.session.collectAsState()
    val now = rememberNow(1000)
    val name by app.store.name.collectAsState()
    val quick by app.store.quickApps.collectAsState()
    val update by com.nbaltas.nickos.update.Updater.state.collectAsState()
    val hasUpdate = update is com.nbaltas.nickos.update.UpdateState.Available
    val icons by produceState(emptyList<Triple<String, String, android.graphics.Bitmap>>(), quick) {
        value = withContext(Dispatchers.IO) {
            quick.mapNotNull { pkg ->
                try {
                    val pm = ctx.packageManager
                    Triple(pkg, pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString(), pm.getApplicationIcon(pkg).toSmallBitmap(120))
                } catch (e: Exception) { null }
            }
        }
    }
    val pill = RoundedCornerShape(50)
    val clock: @Composable () -> Unit = {
        Column(Modifier.widthIn(min = 112.dp)) {
            Text(SimpleDateFormat("h:mm", Locale.getDefault()).format(Date(now)), style = disp(32.sp, 800, 1f).copy(fontFeatureSettings = "tnum"), color = C.text, maxLines = 1, softWrap = false)
            Text(SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(now)), style = body(14.sp, 600, 20f / 14f), color = C.muted, maxLines = 1, softWrap = false)
        }
    }
    val drawer: @Composable () -> Unit = { IconCircle(Lucide.LayoutGrid, onDrawer, label = "All apps") }
    val pinnedIcons: @Composable () -> Unit = {
        icons.forEach { (pkg, label, bmp) ->
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(TabletParentMetrics.buttonRadius)).background(C.surface2).clickable(role = Role.Button) { launchLocal(ctx, pkg) },
                contentAlignment = Alignment.Center,
            ) { Image(bmp.asImageBitmap(), label, Modifier.size(34.dp)) }
        }
    }
    val badgeText = "${name.ifBlank { "Parent's Tablet" }} · Master"
    val badge: @Composable () -> Unit = {
        Chip(Modifier.widthIn(max = 220.dp).clip(pill).clickable(role = Role.Button, onClick = onSettings), height = 48.dp) {
            Icon(Lucide.Tablet, null, tint = C.text, modifier = Modifier.size(18.dp).rotate90())
            Text(badgeText, style = body(15.sp, 700, 20f / 15f), color = C.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    val updateAction: @Composable () -> Unit = {
        if (hasUpdate) Chip(Modifier.clip(pill).clickable(role = Role.Button, onClick = onUpdate), height = 48.dp, bg = C.accent) {
            Icon(Lucide.Download, null, tint = C.onAccent, modifier = Modifier.size(20.dp))
            Text("Update", style = body(16.sp, 700, 1.25f), color = C.onAccent, maxLines = 1)
        }
    }
    val pauseAction: @Composable () -> Unit = {
        val fg = if (paused) C.onAccent else C.text
        Chip(Modifier.clip(pill).clickable(role = Role.Button, onClick = onPause), height = 48.dp, bg = if (paused) C.accent else C.surface2) {
            Icon(if (paused) Solid.Play else Solid.Pause, null, tint = fg, modifier = Modifier.size(20.dp))
            Text(if (paused) "Resume everyone" else "Pause all", style = body(16.sp, 700, 1.25f), color = fg, maxLines = 1)
        }
    }
    val camerasAction: @Composable () -> Unit = { IconCircle(Lucide.Cctv, onCameras, label = "Cameras") }
    val togetherAction: @Composable () -> Unit = {
        if (session != null) Chip(Modifier.clip(pill).clickable(role = Role.Button, onClick = onTogether), height = 48.dp, bg = C.accent) {
            Icon(Lucide.Tv, null, tint = C.onAccent, modifier = Modifier.size(20.dp))
            Text("Watching together", style = body(16.sp, 700, 1.25f), color = C.onAccent, maxLines = 1)
        } else IconCircle(Lucide.Tv, onTogether, label = "Together")
    }
    val settingsAction: @Composable () -> Unit = { IconCircle(Lucide.Settings, onSettings, label = "Settings") }

    if (w >= 960f) {
        TabletBarLayout(
            head = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { clock(); drawer() } },
            strip = if (icons.isEmpty()) null else ({ Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { pinnedIcons() } }),
            badge = badge,
            actions = {
                Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    updateAction(); pauseAction(); camerasAction(); togetherAction(); settingsAction()
                }
            },
        )
        return
    }
    val bigFont = LocalDensity.current.fontScale >= 1.3f
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().height(72.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            clock()
            drawer()
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { if (!bigFont) badge() }
            settingsAction()
        }
        if (bigFont) Text(badgeText, style = body(14.sp, 600, 20f / 14f), color = C.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            pauseAction(); camerasAction(); togetherAction(); updateAction(); pinnedIcons()
        }
    }
}

/** The wide top bar's own layout (see [TabletTopBar]): decides from measured widths what stays on the first row. */
@Composable
private fun TabletBarLayout(head: @Composable () -> Unit, strip: (@Composable () -> Unit)?, badge: @Composable () -> Unit, actions: @Composable () -> Unit) {
    Layout(
        content = {
            Box(Modifier.layoutId("head")) { head() }
            if (strip != null) Box(Modifier.layoutId("strip")) { strip() }
            Box(Modifier.layoutId("badge")) { badge() }
            Box(Modifier.layoutId("actions")) { actions() }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val maxW = constraints.maxWidth
        val g = 12.dp.roundToPx()
        val rowH = 80.dp.roundToPx()
        val row2H = 48.dp.roundToPx()
        val headM = measurables.first { it.layoutId == "head" }
        val stripM = measurables.firstOrNull { it.layoutId == "strip" }
        val badgeM = measurables.first { it.layoutId == "badge" }
        val actionsM = measurables.first { it.layoutId == "actions" }
        val headW = headM.maxIntrinsicWidth(rowH)
        val stripNat = stripM?.maxIntrinsicWidth(rowH) ?: 0
        val badgeW = minOf(badgeM.maxIntrinsicWidth(rowH), 220.dp.roundToPx(), maxW)
        val actW = minOf(actionsM.maxIntrinsicWidth(rowH), (maxW - headW - g).coerceAtLeast(0))
        val free = maxW - headW - actW - 2 * g
        val stripInRow1 = stripM != null && free >= 60.dp.roundToPx()
        val stripW1 = if (stripInRow1) minOf(stripNat, free) else 0
        val free2 = free - (if (stripInRow1) stripW1 + g else 0)
        val badgeInRow1 = free2 >= badgeW
        val stripInRow2 = stripM != null && !stripInRow1
        val badgeInRow2 = !badgeInRow1
        val twoRows = stripInRow2 || badgeInRow2
        val stripW2 = if (stripInRow2) minOf(stripNat, (maxW - (if (badgeInRow2) badgeW + g else 0)).coerceAtLeast(0)) else 0
        val pHead = headM.measure(Constraints(maxWidth = headW))
        val pActions = actionsM.measure(Constraints(maxWidth = actW))
        val pBadge = badgeM.measure(Constraints(maxWidth = badgeW))
        val pStrip = stripM?.measure(Constraints(maxWidth = if (stripInRow1) stripW1 else stripW2))
        val height = rowH + if (twoRows) 8.dp.roundToPx() + row2H else 0
        layout(maxW, height) {
            pHead.placeRelative(0, (rowH - pHead.height) / 2)
            pActions.placeRelative(maxW - actW, (rowH - pActions.height) / 2)
            if (stripInRow1 && pStrip != null) pStrip.placeRelative(headW + g, (rowH - pStrip.height) / 2)
            if (badgeInRow1) pBadge.placeRelative(maxW - actW - g - badgeW, (rowH - pBadge.height) / 2)
            val y2 = rowH + 8.dp.roundToPx()
            var x = 0
            if (badgeInRow2) { pBadge.placeRelative(x, y2 + (row2H - pBadge.height) / 2); x += badgeW + g }
            if (stripInRow2 && pStrip != null) pStrip.placeRelative(x, y2 + (row2H - pStrip.height) / 2)
        }
    }
}

/** Parent tablet tablet-card: padding 20, radius 20, avatar 44, name 20/24 (two lines), 14/20 status, battery 24 top-end; the body is as high as its content. */
@Composable
private fun TabletNodeCard(v: NodeView, poster: ((JfItem) -> String?)?, modifier: Modifier, onClick: () -> Unit) {
    val app = App.get(LocalContext.current)
    val session by app.master.sync.session.collectAsState()
    val receiving = app.master.transfer.receiving(v.node.id)
    val headline: @Composable (String) -> Unit = { t ->
        Text(t, style = disp(22.sp, 800, 26f / 22f), color = C.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    val small: @Composable (String) -> Unit = { t -> Text(t, style = body(14.sp, 500, 20f / 14f), color = C.muted) }
    Column(
        modifier.clip(RoundedCornerShape(TabletParentMetrics.cardRadius)).background(C.surface)
            .clickable(role = Role.Button, onClick = onClick).padding(TabletParentMetrics.cardPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(v.node.name, v.node.color, v.node.avatar, 44.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(v.node.name, style = disp(20.sp, 800, 24f / 20f), color = C.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                StatusLine(v, 14, 8.dp)
            }
            Battery(v.battery, v.status?.charging == true && v.online, 24, 20.dp)
        }
        val pb = v.playback
        val item = pb?.item
        when {
            !v.online -> Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(80.dp).dashed(C.line, 16.dp, 1.dp), contentAlignment = Alignment.Center) {
                    Icon(Lucide.WifiOff, null, tint = C.muted, modifier = Modifier.size(32.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    headline("Can't reach this tablet")
                    small("Check it's switched on and on the same WiFi. Reset it? Unlink it from the Device tab.")
                }
            }
            pb != null && item != null -> Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Artwork(poster?.invoke(item), item.displayTitle, Modifier.size(80.dp, 120.dp), radius = 12.dp, showTitle = false)
                Column(Modifier.weight(1f).heightIn(min = 120.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Solid.Play, null, tint = C.accent, modifier = Modifier.size(12.dp))
                        EyebrowText(if (pb.session.paused) "Paused" else "Now playing", C.accent)
                    }
                    headline(item.displayTitle)
                    Text(episodeLine(item), style = body(14.sp, 500, 20f / 14f), color = C.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.height(10.dp))
                    val now = rememberNow(1000)
                    val pos = pb.positionTicks(now)
                    ProgressBar(if (item.runTicks > 0) pos.toFloat() / item.runTicks else 0f, height = 6.dp)
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(fmtTicks(pos), style = body(14.sp, 600, 20f / 14f).copy(fontFeatureSettings = "tnum"), color = C.muted)
                        Text("${(item.runTicks - pos).coerceAtLeast(0) / JfItem.TICKS_PER_MIN} min left", style = body(14.sp, 600, 20f / 14f), color = C.muted)
                    }
                }
            }
            session != null && v.node.id in session!!.nodes -> Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                val together = session!!
                val frac = if (together.durMs > 0) app.master.sync.expectedPos().toFloat() / together.durMs else 0f
                Artwork(together.art, together.title, Modifier.size(80.dp, 120.dp), radius = 12.dp, showTitle = false)
                Column(Modifier.weight(1f).heightIn(min = 120.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    EyebrowText("Watching together", C.accent)
                    headline(together.title)
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.height(10.dp))
                    ProgressBar(frac, height = 6.dp)
                }
            }
            receiving != null -> Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                val locked = v.status?.lockedBy != null
                AppTileIcon(if (locked) null else v.fgApp, Lucide.Download, 80.dp, 16.dp, v.node.color, glyph = 32.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    EyebrowText("Receiving")
                    val pct = com.nbaltas.nickos.sync.TogetherText.percent(receiving.done, receiving.size)
                    headline(receiving.title.ifEmpty { "a movie" })
                    small("Receiving ${receiving.title} · $pct%")
                }
            }
            else -> Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                val st = v.status
                val locked = st?.lockedBy != null
                val (eyebrow, title) = when {
                    locked -> "Locked" to "Screen locked"
                    st?.fg != null -> "In an app" to (st.fgLabel ?: "An app")
                    else -> "Home screen" to "Home"
                }
                AppTileIcon(if (locked) null else v.fgApp, if (locked) Lucide.Lock else if (st?.fg == null) Lucide.House else Lucide.LayoutGrid, 80.dp, 16.dp, v.node.color, glyph = 32.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    EyebrowText(eyebrow)
                    headline(title)
                    small("Nothing playing")
                }
            }
        }
    }
}

/** Phone Home header: NickOS + Settings on top (a badge when an update is waiting), four equal labelled quick actions below. */
@Composable
private fun PhoneTopBar(onSettings: () -> Unit, onDrawer: () -> Unit, onUpdate: () -> Unit, onCameras: () -> Unit, onTogether: () -> Unit, paused: Boolean, onPause: () -> Unit) {
    val app = App.get(LocalContext.current)
    val session by app.master.sync.session.collectAsState()
    val now = rememberNow(1000)
    val update by com.nbaltas.nickos.update.Updater.state.collectAsState()
    val hasUpdate = update is com.nbaltas.nickos.update.UpdateState.Available
    Column(Modifier.fillMaxWidth().padding(start = PhoneMetrics.gutter, end = PhoneMetrics.gutter, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = PhoneMetrics.headerHeight), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("NickOS", style = disp(24.sp, 800, 1.15f), color = C.text, maxLines = 1)
                Text(SimpleDateFormat("h:mm · EEE d MMM", Locale.getDefault()).format(Date(now)), style = body(14.sp, 600, 1.3f), color = C.muted, maxLines = 1)
            }
            Box {
                IconCircle(Lucide.Settings, if (hasUpdate) onUpdate else onSettings, label = if (hasUpdate) "Settings, update available" else "Settings")
                if (hasUpdate) Box(Modifier.align(Alignment.TopEnd).size(14.dp).clip(CircleShape).background(C.accent).border(2.dp, C.bg, CircleShape))
            }
        }
        val together = session != null
        val actions: List<@Composable (Modifier) -> Unit> = listOf(
            { m -> QuickAction(Lucide.LayoutGrid, "Apps", false, onDrawer, m) },
            { m -> QuickAction(if (paused) Solid.Play else Solid.Pause, if (paused) "Resume" else "Pause all", paused, onPause, m) },
            { m -> QuickAction(Lucide.Cctv, "Cameras", false, onCameras, m) },
            { m -> QuickAction(Lucide.Tv, "Together", together, onTogether, m, dot = together) },
        )
        // Large system fonts would break the labels mid-word in four columns: two rows of two instead.
        val perRow = if (LocalDensity.current.fontScale > 1.3f) 2 else 4
        Column(verticalArrangement = Arrangement.spacedBy(PhoneMetrics.relatedGap)) {
            actions.chunked(perRow).forEach { rowActions ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(PhoneMetrics.relatedGap)) {
                    rowActions.forEach { it(Modifier.weight(1f).fillMaxHeight()) }
                }
            }
        }
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit, modifier: Modifier, dot: Boolean = false) {
    val fg = if (active) C.onAccent else C.text
    Column(
        modifier.heightIn(min = 64.dp).clip(RoundedCornerShape(16.dp)).background(if (active) C.accent else C.surface2)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Box {
            Icon(icon, null, tint = fg, modifier = Modifier.size(22.dp))
            if (dot) Box(Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-2).dp).size(8.dp).clip(CircleShape).background(C.onAccent))
        }
        Text(label, style = body(12.sp, 700, 16f / 12f), color = fg, maxLines = 2, textAlign = TextAlign.Center)
    }
}

@Composable
private fun EmptyHome(onAdd: () -> Unit) {
    if (LocalPhone.current) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = PhoneMetrics.gutter, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Box(Modifier.size(96.dp, 64.dp).dashed(C.line, 16.dp), contentAlignment = Alignment.Center) {
                Icon(Lucide.Link, null, tint = C.muted, modifier = Modifier.size(28.dp))
            }
            Text("No tablets linked yet", style = disp(28.sp, 800, 1.15f), color = C.text, textAlign = TextAlign.Center)
            Text("Set up the kid's tablet as a Node. It will show a 6-digit code. Enter it here.", style = body(16.sp, 500, 1.4f), color = C.muted, textAlign = TextAlign.Center)
            NickButton("Add tablet", onAdd, Modifier.fillMaxWidth(), BtnStyle.PRIMARY, leading = Lucide.Plus)
        }
        return
    }
    if (LocalParentTablet.current) {
        // One 72dp-high icon group, a 30/35 title, 16/23 prose (at most 560 wide) and the 52dp button, centred and scrollable on short screens.
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = TabletParentMetrics.contentBottom),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Box(Modifier.size(108.dp, 72.dp).dashed(C.line, 16.dp, 1.dp), contentAlignment = Alignment.Center) {
                Icon(Lucide.Link, null, tint = C.muted, modifier = Modifier.size(28.dp))
            }
            Text("No tablets linked yet", style = disp(30.sp, 800, 35f / 30f), color = C.text, textAlign = TextAlign.Center)
            Text(
                "Set up the kid's tablet as a Node. It will show a 6-digit code. Enter it here.",
                style = body(16.sp, 500, 23f / 16f), color = C.muted, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 560.dp),
            )
            NickButton("Add tablet", onAdd, style = BtnStyle.PRIMARY, leading = Lucide.Plus)
        }
        return
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 40.dp, end = 40.dp, bottom = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            repeat(3) { i ->
                Box(Modifier.size(150.dp, 110.dp).dashed(C.line, 22.dp), contentAlignment = Alignment.Center) {
                    if (i == 1) Icon(Lucide.Link, null, tint = C.muted, modifier = Modifier.size(34.dp))
                }
            }
        }
        Title("No tablets linked yet", 48, Modifier.padding(top = 12.dp), lineHeight = 1.05f)
        Muted("Set up the kid's tablet as a Node. It will show a 6-digit code. Enter it here.", 21, Modifier.widthIn(max = 600.dp))
        NickButton("Add tablet", onAdd, Modifier.padding(top = 8.dp), BtnStyle.PRIMARY, big = true, leading = Lucide.Plus)
    }
}

// ---------------- Cards ----------------

fun seenAgo(ms: Long): String {
    if (ms <= 0) return ""
    val min = ((screenNow() - ms) / 60_000).toInt()
    return when {
        min < 1 -> "seen just now"
        min < 60 -> "seen $min min ago"
        min < 60 * 24 -> "seen ${min / 60} h ago"
        else -> "seen ${min / (60 * 24)} days ago"
    }
}

fun statusText(v: NodeView): String = when {
    !v.online -> listOf("Offline", seenAgo(v.node.lastSeen)).filter { it.isNotEmpty() }.joinToString(" · ")
    v.status?.lockedBy != null -> "Online · Locked"
    else -> "Online · Screen ${if (v.status?.screenOn == false) "off" else "on"}"
}

@Composable
fun StatusLine(v: NodeView, size: Int, dot: Dp, text: String = statusText(v)) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Dot(if (v.online) C.online else C.offline, dot)
        // Tablet parent: 14/20, and a long status may take a second line instead of being cut.
        if (LocalParentTablet.current) Text(text, style = body(size.sp, 600, 20f / size), color = C.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        else Text(text, style = body(size.sp, 600), color = C.muted, maxLines = 1)
    }
}

@Composable
private fun NodeCard(v: NodeView, poster: ((JfItem) -> String?)?, modifier: Modifier, onClick: () -> Unit) {
    val app = App.get(LocalContext.current)
    val session by app.master.sync.session.collectAsState()
    val receiving = app.master.transfer.receiving(v.node.id)
    Column(
        modifier.clip(RoundedCornerShape(30.dp)).background(C.surface).clickable(onClick = onClick).padding(horizontal = 26.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Avatar(v.node.name, v.node.color, v.node.avatar, 56.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Title(v.node.name, 28, maxLines = 1, lineHeight = 1.05f)
                StatusLine(v, 16, 12.dp)
            }
            // Phone width: a smaller battery leaves room for the kid's name.
            val phone = LocalConfiguration.current.screenWidthDp < 600
            Battery(v.battery, v.status?.charging == true && v.online, if (phone) 24 else 40, if (phone) 22.dp else 30.dp)
        }
        val pb = v.playback
        val item = pb?.item
        when {
            !v.online -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                Box(Modifier.size(124.dp).dashed(C.line, 30.dp), contentAlignment = Alignment.Center) {
                    Icon(Lucide.WifiOff, null, tint = C.muted, modifier = Modifier.size(44.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Title("Can't reach this tablet", 28, lineHeight = 1.1f)
                    Muted("Check it's switched on and on the same WiFi. Reset it? Unlink it from the Device tab.", 18)
                }
            }
            pb != null && item != null -> Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                Artwork(poster?.invoke(item), item.displayTitle, Modifier.size(124.dp, 186.dp), radius = 14.dp)
                Column(Modifier.weight(1f).height(186.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Solid.Play, null, tint = C.accent, modifier = Modifier.size(12.dp))
                        EyebrowText(if (pb.session.paused) "Paused" else "Now playing", C.accent)
                    }
                    Title(item.displayTitle, 32, lineHeight = 1.05f, maxLines = 1)
                    Muted(episodeLine(item), 18, maxLines = 1)
                    Spacer(Modifier.weight(1f))
                    val now = rememberNow(1000)
                    val pos = pb.positionTicks(now)
                    ProgressBar(if (item.runTicks > 0) pos.toFloat() / item.runTicks else 0f)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(fmtTicks(pos), style = body(16.sp, 600).copy(fontFeatureSettings = "tnum"), color = C.muted)
                        Text("${(item.runTicks - pos).coerceAtLeast(0) / JfItem.TICKS_PER_MIN} min left", style = body(16.sp, 600), color = C.muted)
                    }
                }
            }
            session != null && v.node.id in session!!.nodes -> Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                val together = session!!
                val frac = if (together.durMs > 0) app.master.sync.expectedPos().toFloat() / together.durMs else 0f
                com.nbaltas.nickos.ui.theme.Artwork(together.art, together.title, Modifier.size(124.dp, 186.dp), radius = 14.dp)
                Column(Modifier.weight(1f).height(186.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    EyebrowText("Watching together", C.accent)
                    Title(together.title, 32, lineHeight = 1.05f, maxLines = 1)
                    Spacer(Modifier.weight(1f))
                    ProgressBar(frac)
                }
            }
            receiving != null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                val devStatus = v.status
                val locked = devStatus?.lockedBy != null
                AppTileIcon(if (locked) null else v.fgApp, Lucide.Download, 124.dp, 34.dp, v.node.color)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    EyebrowText("Receiving")
                    val pct = com.nbaltas.nickos.sync.TogetherText.percent(receiving.done, receiving.size)
                    Title(receiving.title.ifEmpty { "a movie" }, 28, lineHeight = 1.05f, maxLines = 1)
                    Muted("Receiving ${receiving.title} · $pct%", 18)
                }
            }
            else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                val st = v.status
                val locked = st?.lockedBy != null
                val (eyebrow, title) = when {
                    locked -> "Locked" to "Screen locked"
                    st?.fg != null -> "In an app" to (st.fgLabel ?: "An app")
                    else -> "Home screen" to "Home"
                }
                AppTileIcon(if (locked) null else v.fgApp, if (locked) Lucide.Lock else if (st?.fg == null) Lucide.House else Lucide.LayoutGrid, 124.dp, 34.dp, v.node.color)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    EyebrowText(eyebrow)
                    Title(title, 32, lineHeight = 1.05f, maxLines = 1)
                    Muted("Nothing playing", 18)
                }
            }
        }
    }
}

/**
 * Phone tablet card: 40dp avatar, name up to two lines, battery, then a body sized to its content
 * (64×96 artwork + weighted text while playing). The same states as [NodeCard].
 */
@Composable
private fun PhoneNodeCard(v: NodeView, poster: ((JfItem) -> String?)?, modifier: Modifier, onClick: () -> Unit) {
    val app = App.get(LocalContext.current)
    val session by app.master.sync.session.collectAsState()
    val receiving = app.master.transfer.receiving(v.node.id)
    Column(
        modifier.clip(RoundedCornerShape(PhoneMetrics.cardRadius)).background(C.surface).clickable(role = Role.Button, onClick = onClick).padding(PhoneMetrics.cardPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(v.node.name, v.node.color, v.node.avatar, 40.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(v.node.name, style = disp(18.sp, 800, 1.15f), color = C.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                PhoneStatusLine(v)
            }
            Battery(v.battery, v.status?.charging == true && v.online, 14, 20.dp)
        }
        val pb = v.playback
        val item = pb?.item
        when {
            !v.online -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(56.dp).dashed(C.line, 16.dp), contentAlignment = Alignment.Center) {
                    Icon(Lucide.WifiOff, null, tint = C.muted, modifier = Modifier.size(24.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    CardTitle("Can't reach this tablet")
                    Text("Check it's switched on and on the same WiFi. Reset it? Unlink it from the Device tab.", style = body(14.sp, 500, 1.4f), color = C.muted)
                }
            }
            pb != null && item != null -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Artwork(poster?.invoke(item), item.displayTitle, Modifier.size(64.dp, 96.dp), radius = 12.dp, showTitle = false)
                Column(Modifier.weight(1f).heightIn(min = 96.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Solid.Play, null, tint = C.accent, modifier = Modifier.size(10.dp))
                        EyebrowText(if (pb.session.paused) "Paused" else "Now playing", C.accent)
                    }
                    CardTitle(item.displayTitle)
                    Text(episodeLine(item), style = body(14.sp, 500, 1.3f), color = C.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.weight(1f))
                    val now = rememberNow(1000)
                    val pos = pb.positionTicks(now)
                    ProgressBar(if (item.runTicks > 0) pos.toFloat() / item.runTicks else 0f, height = 6.dp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(fmtTicks(pos), style = body(14.sp, 600).copy(fontFeatureSettings = "tnum"), color = C.muted)
                        Text("${(item.runTicks - pos).coerceAtLeast(0) / JfItem.TICKS_PER_MIN} min left", style = body(14.sp, 600), color = C.muted)
                    }
                }
            }
            session != null && v.node.id in session!!.nodes -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val together = session!!
                val frac = if (together.durMs > 0) app.master.sync.expectedPos().toFloat() / together.durMs else 0f
                Artwork(together.art, together.title, Modifier.size(64.dp, 96.dp), radius = 12.dp, showTitle = false)
                Column(Modifier.weight(1f).heightIn(min = 96.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    EyebrowText("Watching together", C.accent)
                    CardTitle(together.title)
                    Spacer(Modifier.weight(1f))
                    ProgressBar(frac, height = 6.dp)
                }
            }
            receiving != null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val locked = v.status?.lockedBy != null
                AppTileIcon(if (locked) null else v.fgApp, Lucide.Download, 56.dp, 16.dp, v.node.color)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    EyebrowText("Receiving")
                    val pct = com.nbaltas.nickos.sync.TogetherText.percent(receiving.done, receiving.size)
                    CardTitle(receiving.title.ifEmpty { "a movie" })
                    Text("Receiving ${receiving.title} · $pct%", style = body(14.sp, 500, 1.3f), color = C.muted)
                }
            }
            else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val st = v.status
                val locked = st?.lockedBy != null
                val (eyebrow, title) = when {
                    locked -> "Locked" to "Screen locked"
                    st?.fg != null -> "In an app" to (st.fgLabel ?: "An app")
                    else -> "Home screen" to "Home"
                }
                AppTileIcon(if (locked) null else v.fgApp, if (locked) Lucide.Lock else if (st?.fg == null) Lucide.House else Lucide.LayoutGrid, 56.dp, 16.dp, v.node.color)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    EyebrowText(eyebrow)
                    CardTitle(title)
                    Text("Nothing playing", style = body(14.sp, 500, 1.3f), color = C.muted)
                }
            }
        }
    }
}

@Composable
private fun CardTitle(text: String) =
    Text(text, style = disp(18.sp, 800, 1.15f), color = C.text, maxLines = 2, overflow = TextOverflow.Ellipsis)

/** 14sp status with an 8dp dot; may wrap rather than clip. */
@Composable
private fun PhoneStatusLine(v: NodeView) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Dot(if (v.online) C.online else C.offline, 8.dp)
        Text(statusText(v), style = body(14.sp, 600, 1.3f), color = C.muted)
    }
}

/** App icon on a tile: the real icon when the Node sent it, else a Lucide glyph on the kid's colour. */
@Composable
fun AppTileIcon(app: RemoteApp?, fallback: ImageVector, size: Dp, radius: Dp, colorIndex: Int, glyph: Dp = size * 0.38f) {
    val icon = app?.icon
    Box(
        Modifier.size(size).clip(RoundedCornerShape(radius)).background(if (icon != null) C.surface2 else avatarColor(colorIndex)),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) Image(icon.asImageBitmap(), null, Modifier.size(size * 0.7f))
        else Icon(fallback, null, tint = OnAvatar, modifier = Modifier.size(glyph))
    }
}

@Composable
private fun AddCard(row: Boolean, modifier: Modifier, onAdd: () -> Unit) {
    val phone = LocalPhone.current
    val plus: @Composable () -> Unit = {
        Box(Modifier.size(if (phone) 48.dp else if (row) 64.dp else 88.dp).clip(CircleShape).background(C.accent), contentAlignment = Alignment.Center) {
            Icon(Lucide.Plus, null, tint = C.onAccent, modifier = Modifier.size(if (phone) 22.dp else if (row) 30.dp else 40.dp))
        }
    }
    if (phone) {
        Row(
            modifier.heightIn(min = 80.dp).dashed(C.line, 20.dp, 2.dp).clip(RoundedCornerShape(20.dp)).clickable(role = Role.Button, onClick = onAdd).padding(PhoneMetrics.cardPadding),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            plus()
            Column(Modifier.weight(1f)) {
                Text("Add tablet", style = disp(18.sp, 800, 1.15f), color = C.text)
                Text("Pair a kid's tablet with its 6-digit code", style = body(14.sp, 500, 1.4f), color = C.muted)
            }
        }
    } else if (LocalParentTablet.current) {
        // Dashed 1dp border, radius 20, a 56dp plus disk, heading 22 and helper 14/20; icon and text side by side in one column.
        val disk: @Composable () -> Unit = {
            Box(Modifier.size(56.dp).clip(CircleShape).background(C.accent), contentAlignment = Alignment.Center) {
                Icon(Lucide.Plus, null, tint = C.onAccent, modifier = Modifier.size(24.dp))
            }
        }
        val heading: @Composable () -> Unit = { Text("Add tablet", style = disp(22.sp, 800, 26f / 22f), color = C.text) }
        val helper: @Composable (TextAlign) -> Unit = { a -> Text("Pair a kid's tablet with its 6-digit code", style = body(14.sp, 500, 20f / 14f), color = C.muted, textAlign = a) }
        if (row) {
            Row(
                modifier.dashed(C.line, 20.dp, 1.dp).clip(RoundedCornerShape(20.dp)).clickable(role = Role.Button, onClick = onAdd).padding(20.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                disk()
                Column(Modifier.weight(1f)) { heading(); helper(TextAlign.Start) }
            }
        } else {
            Column(
                modifier.dashed(C.line, 20.dp, 1.dp).clip(RoundedCornerShape(20.dp)).clickable(role = Role.Button, onClick = onAdd).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            ) {
                disk(); heading(); helper(TextAlign.Center)
            }
        }
    } else if (row) {
        Row(
            modifier.dashed(C.line, 30.dp).clip(RoundedCornerShape(30.dp)).clickable(onClick = onAdd),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
        ) {
            plus()
            Column { Title("Add tablet", 32); Muted("Pair a kid's tablet with its 6-digit code", 18) }
        }
    } else {
        Column(
            modifier.dashed(C.line, 30.dp).clip(RoundedCornerShape(30.dp)).clickable(onClick = onAdd),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            plus()
            Title("Add tablet", 32)
            Muted("Pair a kid's tablet with its 6-digit code", 18, Modifier.padding(horizontal = 16.dp))
        }
    }
}

// ---------------- formatting ----------------

fun fmtTicks(t: Long): String {
    val s = (t / JfItem.TICKS_PER_SEC).toInt()
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

/** "S2 · E5 · The Lighthouse" for episodes, year for movies. */
fun episodeLine(i: JfItem): String = when {
    i.isEpisode && i.season != null && i.index != null -> "S${i.season} · E${i.index} · ${i.name}"
    i.isEpisode -> i.name
    else -> listOfNotNull(i.year?.toString(), i.rating).joinToString(" · ").ifEmpty { "Movie" }
}

fun minutesText(min: Int): String = if (min >= 60) "${min / 60} h ${min % 60} min" else "$min min"
