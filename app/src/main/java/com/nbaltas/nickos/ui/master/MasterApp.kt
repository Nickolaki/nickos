package com.nbaltas.nickos.ui.master

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
    data class Settings(val section: Int = 0) : MScreen
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

    Box(Modifier.fillMaxSize().background(C.bg)) {
        when (val s = screen) {
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
        toast?.let { Toast(it, Modifier.align(Alignment.BottomCenter)) }
    }
}

fun launchLocal(ctx: android.content.Context, pkg: String) {
    ctx.packageManager.getLaunchIntentForPackage(pkg)?.let { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
private fun Toast(text: String, modifier: Modifier) {
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
    val pad = if (portrait) 32.dp else 40.dp
    Column(Modifier.fillMaxSize()) {
        TopBar(pad, onSettings, onDrawer, onUpdate, onCameras, onTogether)
        CarNetBanner(pad, onSettings)
        if (views.isEmpty()) {
            EmptyHome(onAdd)
            return@Column
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = pad, end = pad, top = 12.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (portrait) {
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
private fun TopBar(pad: Dp, onSettings: () -> Unit, onDrawer: () -> Unit, onUpdate: () -> Unit, onCameras: () -> Unit, onTogether: () -> Unit) {
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

@Composable
private fun EmptyHome(onAdd: () -> Unit) {
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
        Text(text, style = body(size.sp, 600), color = C.muted, maxLines = 1)
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
                com.nbaltas.nickos.ui.theme.Artwork(null, together.title, Modifier.size(124.dp, 186.dp), radius = 14.dp)
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

/** App icon on a tile: the real icon when the Node sent it, else a Lucide glyph on the kid's colour. */
@Composable
fun AppTileIcon(app: RemoteApp?, fallback: ImageVector, size: Dp, radius: Dp, colorIndex: Int) {
    val icon = app?.icon
    Box(
        Modifier.size(size).clip(RoundedCornerShape(radius)).background(if (icon != null) C.surface2 else avatarColor(colorIndex)),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) Image(icon.asImageBitmap(), null, Modifier.size(size * 0.7f))
        else Icon(fallback, null, tint = OnAvatar, modifier = Modifier.size(size * 0.38f))
    }
}

@Composable
private fun AddCard(row: Boolean, modifier: Modifier, onAdd: () -> Unit) {
    val plus: @Composable () -> Unit = {
        Box(Modifier.size(if (row) 64.dp else 88.dp).clip(CircleShape).background(C.accent), contentAlignment = Alignment.Center) {
            Icon(Lucide.Plus, null, tint = C.onAccent, modifier = Modifier.size(if (row) 30.dp else 40.dp))
        }
    }
    if (row) {
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
