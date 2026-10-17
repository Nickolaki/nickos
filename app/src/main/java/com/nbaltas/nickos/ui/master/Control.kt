package com.nbaltas.nickos.ui.master

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.composables.icons.lucide.Film
import com.composables.icons.lucide.House
import com.composables.icons.lucide.LayoutGrid
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.LockOpen
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Moon
import com.composables.icons.lucide.Monitor
import com.composables.icons.lucide.MonitorOff
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Power
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.RotateCcw
import com.composables.icons.lucide.RotateCw
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Sun
import com.composables.icons.lucide.Tablet
import com.composables.icons.lucide.Unlink
import com.composables.icons.lucide.Volume2
import com.composables.icons.lucide.Wifi
import com.composables.icons.lucide.WifiOff
import com.nbaltas.nickos.App
import com.nbaltas.nickos.jellyfin.JfItem
import kotlinx.coroutines.launch
import com.nbaltas.nickos.ui.node.kidName
import com.nbaltas.nickos.ui.node.rememberNow
import com.nbaltas.nickos.ui.setup.rotate90
import com.nbaltas.nickos.ui.theme.Artwork
import com.nbaltas.nickos.ui.theme.Avatar
import com.nbaltas.nickos.ui.theme.BackButton
import com.nbaltas.nickos.ui.theme.Battery
import com.nbaltas.nickos.ui.theme.BtnStyle
import com.nbaltas.nickos.ui.theme.C
import com.nbaltas.nickos.ui.theme.Solid
import com.nbaltas.nickos.ui.theme.Chip
import com.nbaltas.nickos.ui.theme.EyebrowText
import com.nbaltas.nickos.ui.theme.Muted
import com.nbaltas.nickos.ui.theme.NickButton
import com.nbaltas.nickos.ui.theme.NickField
import com.nbaltas.nickos.node.ControlAccess
import com.nbaltas.nickos.node.LandscapePolicy
import com.nbaltas.nickos.link.NetMessages
import com.nbaltas.nickos.remote.RemoteMessages
import com.nbaltas.nickos.power.PowerPolicy
import com.nbaltas.nickos.ui.theme.NickSlider
import com.nbaltas.nickos.ui.theme.NickSwitch
import com.nbaltas.nickos.ui.theme.ProgressBar
import com.nbaltas.nickos.ui.theme.Segmented
import com.nbaltas.nickos.ui.theme.Title
import com.nbaltas.nickos.ui.theme.body
import com.nbaltas.nickos.ui.theme.dashed
import com.nbaltas.nickos.ui.theme.disp
import com.nbaltas.nickos.ui.theme.tap
import com.nbaltas.nickos.ui.theme.Dot
import com.nbaltas.nickos.ui.theme.LocalPhone
import com.nbaltas.nickos.ui.theme.PhoneMetrics
import com.composables.icons.lucide.Clock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import com.nbaltas.nickos.sync.TogetherText
import kotlin.math.roundToInt

/** Bedtime lengths and what tapping one sends. Kept apart from the UI so the command is testable. */
internal object BedtimeCommands {
    val choices = listOf(15, 30, 45, 60)
    /** Tapping a length sends it at once; returns the length to show as chosen. */
    fun pick(min: Int, send: (Int) -> Unit): Int { send(min); return min }
    /** Cancel sends 0 minutes. */
    fun cancel(send: (Int) -> Unit) = send(0)
}

const val TAB_NOW = 0
const val TAB_WATCH = 1
const val TAB_APPS = 2
const val TAB_DEVICE = 3
const val TAB_SCREEN = 4

/** Tablet Control: header + Now Playing / Watch / Apps / Device. [onSettings] takes a Settings section. */
@Composable
fun ControlScreen(nodeId: String, tab: Int, onTab: (Int) -> Unit, onBack: () -> Unit, onSettings: (Int) -> Unit) {
    val app = App.get(LocalContext.current)
    val v = rememberNodeViews().firstOrNull { it.node.id == nodeId }
    if (v == null) { LaunchedEffect(Unit) { onBack() }; return }

    DisposableEffect(tab) {
        app.master.fastPoll = tab == TAB_NOW
        onDispose { app.master.fastPoll = false }
    }

    // Leaving the Screen tab, or this screen, tells the tablet to stop sharing.
    DisposableEffect(tab == TAB_SCREEN) {
        val onScreen = tab == TAB_SCREEN
        onDispose { if (onScreen) app.master.screen.unwatch() }
    }
    val watchingScreen by app.master.screen.watching.collectAsState()

    val session by app.master.sync.session.collectAsState()
    val inTogether = session?.nodes?.contains(nodeId) == true

    val phone = LocalPhone.current
    // Phone: one scroll position per tab, kept while the parent moves between tabs.
    val nowScroll = rememberScrollState()
    val deviceScroll = rememberScrollState()
    val offlineScroll = rememberScrollState()
    val appsGrid = rememberLazyGridState()
    // Phone: only the static bodies (Apps, Device, offline) fade in over 150ms. Players, the library and the Screen viewer never do.
    // Only the very first entry skips the fade; coming back to the tab the screen opened on fades like any other.
    val rendered = remember { booleanArrayOf(false) }
    val staticBody = phone && (!v.online && tab != TAB_WATCH && tab != TAB_SCREEN && !(tab == TAB_NOW && inTogether) || v.online && (tab == TAB_APPS || tab == TAB_DEVICE))
    val fade = remember(tab, staticBody) { Animatable(if (staticBody && rendered[0]) 0f else 1f) }
    SideEffect { rendered[0] = true }
    LaunchedEffect(tab, staticBody) { if (fade.value < 1f) fade.animateTo(1f, tween(150)) }
    // Phone: the Watch tab's saveable state (library segment, open movie or show, scroll) survives a visit to another tab.
    val saveable = rememberSaveableStateHolder()
    // "Save to tablets instead" on a stream failure: the same flow as the Together screen, for the one tablet that failed.
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var saveInstead by remember { mutableStateOf<Pair<JfItem, String>?>(null) }
    var looking by remember { mutableStateOf(false) }
    val onSaveInstead: (com.nbaltas.nickos.master.StreamFail) -> Unit = { f ->
        val jfc = app.master.jellyfin()
        val account = app.store.jellyfin.value
        if (jfc != null && account != null && !looking) {
            looking = true
            scope.launch {
                // Look the item up first: the failure row (and with it this action) goes only once the picker can open.
                try {
                    val item = jfc.item(account.userId, f.fid.substringAfterLast(':'))
                    app.master.sync.dismissFailure(f.id)
                    saveInstead = item to f.id
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    app.master.say("Couldn't reach Jellyfin to save this. Try again.")
                } finally { looking = false }
            }
        } else if (jfc == null || account == null) app.master.say("Connect Jellyfin in Settings to save this.")
    }
    // System Back leaves the save picker for this screen, like its own Back button.
    androidx.activity.compose.BackHandler(enabled = saveInstead != null) { saveInstead = null }
    saveInstead?.let { (item, id) ->
        JfPickPanel(item, rememberNodeViews(), remember { app.master.sync.library() }, app.master, onBack = { saveInstead = null }, saveFor = id)
        return
    }

    Column(Modifier.fillMaxSize()) {
        // The screen viewer takes the whole window.
        if (!(tab == TAB_SCREEN && watchingScreen == nodeId && v.online)) ControlHeader(v, tab, onTab, onBack)
        Box(Modifier.fillMaxSize().then(if (staticBody) Modifier.graphicsLayer { alpha = fade.value } else Modifier)) {
            when {
                tab == TAB_WATCH ->
                    if (phone) saveable.SaveableStateProvider("watch") { TabletLibrary(nodeId, v, onPlayed = { onTab(TAB_NOW) }, onSettings = { onSettings(1) }) }
                    else TabletLibrary(nodeId, v, onPlayed = { onTab(TAB_NOW) }, onSettings = { onSettings(1) })
                tab == TAB_NOW && inTogether -> if (phone) PhoneGroupRemote(nodeId, nowScroll, onSaveInstead = onSaveInstead) else TabletRemote(nodeId)
                !v.online -> OfflineBody(v, onBack, offlineScroll)
                tab == TAB_NOW -> if (v.playback?.item != null) NowPlaying(v, nowScroll) else NothingPlaying(v, nowScroll) { onTab(TAB_WATCH) }
                tab == TAB_APPS -> AppsTab(v, appsGrid)
                tab == TAB_DEVICE -> DeviceTab(v, onUnlinked = onBack, scroll = deviceScroll)
                tab == TAB_SCREEN -> ScreenTab(v)
            }
        }
    }
}

/** Phone Control header: back 48, avatar 32, name 18 (two lines), status under it, battery 14; then five labelled tabs. */
@Composable
private fun PhoneControlHeader(v: NodeView, tab: Int, onTab: (Int) -> Unit, onBack: () -> Unit) {
    val scaled = LocalDensity.current.fontScale >= 1.5f
    Column(Modifier.fillMaxWidth().background(C.bg)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = PhoneMetrics.gutter, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PhoneMetrics.relatedGap),
        ) {
            BackButton(onBack)
            Avatar(v.node.name, v.node.color, v.node.avatar, 32.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Title(v.node.name, 25, maxLines = 2)
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.padding(top = 6.dp)) { Dot(if (v.online) C.online else C.offline, 8.dp) }
                    Text(statusText(v), style = body(14.sp, 600, 1.3f), color = C.muted)
                }
            }
            Battery(v.battery, v.status?.charging == true && v.online, 14, 20.dp)
        }
        // Short label on the tab, full name for a screen reader.
        val tabs = listOf(
            Triple("Playing", "Now Playing", Solid.Play), Triple("Watch", "Watch", Lucide.Film), Triple("Apps", "Apps", Lucide.LayoutGrid),
            Triple("Device", "Device", Lucide.Tablet), Triple("Screen", "Screen", Lucide.Monitor),
        )
        // Large font: all five tabs stay in view. Only the selected tab keeps its label (and gets the wider share); the others are icons.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = PhoneMetrics.gutter).clip(RoundedCornerShape(PhoneMetrics.fieldRadius)).background(C.surface).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            tabs.forEachIndexed { i, (label, full, icon) ->
                val on = i == tab
                val bg by animateColorAsState(if (on) C.accent else Color.Transparent, tween(120), label = "tabBg")
                val fg by animateColorAsState(if (on) C.onAccent else C.muted, tween(120), label = "tabFg")
                Column(
                    Modifier.weight(if (scaled && on) 2.4f else 1f).heightIn(min = 64.dp).clip(RoundedCornerShape(10.dp)).background(bg)
                        .selectable(selected = on, role = Role.Tab) { onTab(i) }
                        .semantics { contentDescription = full }
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                ) {
                    Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
                    if (!scaled || on) Text(label, style = body(12.sp, 700, 16f / 12f), color = fg, maxLines = 1, softWrap = false, textAlign = TextAlign.Center)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.surface2))
    }
}

@Composable
private fun ControlHeader(v: NodeView, tab: Int, onTab: (Int) -> Unit, onBack: () -> Unit) {
    if (LocalPhone.current) { PhoneControlHeader(v, tab, onTab, onBack); return }
    val phone = LocalConfiguration.current.screenWidthDp < 600
    val tabs = listOf<Pair<String, ImageVector?>>("Now Playing" to Solid.Play, "Watch" to Lucide.Film, "Apps" to Lucide.LayoutGrid, "Device" to Lucide.Tablet, "Screen" to Lucide.Monitor)
    BoxWithConstraints(Modifier.fillMaxWidth().background(C.bg)) {
        val narrow = maxWidth < 1100.dp
        val who: @Composable (Modifier) -> Unit = { modifier ->
            Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (phone) 8.dp else 16.dp)) {
                BackButton(onBack)
                Avatar(v.node.name, v.node.color, v.node.avatar, 52.dp)
                Column(if (phone) Modifier.weight(1f) else Modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Title(v.node.name, 25, maxLines = 1)
                    StatusLine(v, 15, 10.dp)
                }
            }
        }
        val battery: @Composable () -> Unit = {
            Battery(v.battery, v.status?.charging == true && v.online, if (phone) 24 else 38, if (phone) 24.dp else 30.dp)
        }
        Column {
            if (narrow) {
                Row(Modifier.fillMaxWidth().height(96.dp).padding(horizontal = 32.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (phone) { who(Modifier.weight(1f)); Spacer(Modifier.width(8.dp)) }
                    else { who(Modifier); Spacer(Modifier.weight(1f)) }
                    battery()
                }
                Row(Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, bottom = 16.dp)) {
                    Segmented(tabs, tab, onTab, modifier = if (phone) Modifier.fillMaxWidth() else Modifier, compact = phone)
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().height(104.dp).padding(horizontal = 32.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    who(Modifier)
                    Segmented(tabs, tab, onTab)
                    Spacer(Modifier.weight(1f))
                    battery()
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(C.surface2))
        }
    }
}

// ---------------- Now Playing ----------------

@Composable
private fun NowPlaying(v: NodeView, phoneScroll: ScrollState) {
    val app = App.get(LocalContext.current)
    val m = app.master
    val pb = v.playback ?: return
    val item = pb.item ?: return
    val account by app.store.jellyfin.collectAsState()
    val jf = remember(account) { m.jellyfin() }
    val now = rememberNow(500)
    val pos = pb.positionTicks(now)
    var scrub by remember { mutableStateOf<Float?>(null) }
    val next by produceState<JfItem?>(null, item.id) {
        val uid = v.node.jfUserId
        value = if (item.isEpisode && uid != null) try { jf?.nextEpisode(uid, item) } catch (e: Exception) { null } else null
    }

    if (LocalPhone.current) {
        PhoneNowPlaying(v, pb, item, jf, pos, next, scrub, { scrub = it }, phoneScroll)
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Backdrop at 35%, fading into the page towards the right-hand column.
        jf?.backdrop(item)?.let { AsyncImage(it, null, Modifier.fillMaxSize().alpha(0.35f), contentScale = ContentScale.Crop) }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to C.bg.copy(alpha = 0.55f), 0.62f to C.bg.copy(alpha = 0.92f), 0.78f to C.bg)))
        val narrow = maxWidth < 1000.dp
        val side: @Composable (Modifier) -> Unit = { mod ->
            Column(mod, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                EyebrowText("On ${v.node.name}")
                DeviceSliders(v)
                next?.let { n ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(C.surface)
                            .clickable { m.playOn(v.node.id, n.id, 0, n.displayTitle) }.padding(18.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Artwork(jf?.landscape(n, 300), "", Modifier.size(128.dp, 72.dp), radius = 12.dp, showTitle = false)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            EyebrowText("Up next")
                            Text("E${n.index ?: ""} · ${n.name}", style = body(18.sp, 700), color = C.text, maxLines = 2)
                        }
                    }
                }
            }
        }
        val nextAndStop: @Composable () -> Unit = {
            next?.let { n -> NickButton("Next episode", { m.playOn(v.node.id, n.id, 0, n.displayTitle) }, Modifier.height(68.dp), leading = Solid.Next) }
            NickButton("Stop", { m.control(v.node.id, "Stop") }, Modifier.height(68.dp), leading = Solid.Stop)
        }
        val main: @Composable (Modifier) -> Unit = { mod ->
            Column(mod, verticalArrangement = Arrangement.spacedBy(28.dp)) {
                // No weight when narrow: inside the scrolling column it would collapse the poster and title to nothing.
                Row(if (narrow) Modifier else Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(if (narrow) 20.dp else 32.dp)) {
                    val h = if (narrow) 210.dp else 375.dp
                    Artwork(jf?.poster(item, 600), item.displayTitle, Modifier.size(h * 2 / 3, h), radius = 20.dp, titleSize = 35)
                    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Solid.Play, null, tint = C.accent, modifier = Modifier.size(12.dp))
                            EyebrowText(if (pb.session.paused) "Paused" else "Playing now", C.accent)
                        }
                        Title(item.displayTitle, if (narrow) 40 else 64, lineHeight = 0.98f, maxLines = 3)
                        if (item.isEpisode) {
                            if (item.season != null && item.index != null) Text("Season ${item.season} · Episode ${item.index}", style = body(24.sp, 700), color = C.text)
                            Muted(item.name, 22, maxLines = 2)
                        } else Muted(episodeLine(item), 22)
                    }
                }
                // Jellyfin's integrated player only takes media keys (back 10 s, forward 30 s), so it
                // can't be scrubbed: show progress only. The Web player setting gives a real scrub bar.
                val integrated = !pb.session.remote
                val fwdSecs = if (integrated) 30 else 10
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val frac = scrub ?: if (item.runTicks > 0) pos.toFloat() / item.runTicks else 0f
                    if (integrated) ProgressBar(frac, Modifier.padding(vertical = 12.dp), height = 14.dp, track = C.line)
                    else NickSlider(
                        value = frac,
                        onChange = { scrub = it },
                        onCommit = { f -> scrub = null; m.control(v.node.id, "Seek", (f * item.runTicks).toLong()) },
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(fmtTicks((frac * item.runTicks).toLong()), style = body(18.sp, 700).copy(fontFeatureSettings = "tnum"), color = C.text)
                        Text(fmtTicks(item.runTicks), style = body(18.sp, 700).copy(fontFeatureSettings = "tnum"), color = C.muted)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    SkipButton(back = true) { m.control(v.node.id, "Seek", (pos - 10 * JfItem.TICKS_PER_SEC).coerceAtLeast(0)) }
                    Box(
                        Modifier.size(104.dp).clip(CircleShape).background(C.accent)
                            .clickable { m.control(v.node.id, if (pb.session.paused) "Unpause" else "Pause") },
                        contentAlignment = Alignment.Center,
                    ) { Icon(if (pb.session.paused) Solid.Play else Solid.Pause, if (pb.session.paused) "Play" else "Pause", tint = C.onAccent, modifier = Modifier.size(44.dp)) }
                    SkipButton(back = false, secs = fwdSecs) { m.control(v.node.id, "Seek", (pos + fwdSecs * JfItem.TICKS_PER_SEC).coerceAtMost(item.runTicks)) }
                    if (!narrow) { Spacer(Modifier.weight(1f)); nextAndStop() }
                }
                if (narrow) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { nextAndStop() }
            }
        }
        if (narrow) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp), verticalArrangement = Arrangement.spacedBy(32.dp)) {
                main(Modifier.fillMaxWidth()); side(Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.fillMaxSize().padding(start = 40.dp, end = 32.dp, top = 32.dp, bottom = 32.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                main(Modifier.weight(1f).fillMaxHeight()); side(Modifier.width(360.dp).verticalScroll(rememberScrollState()))
            }
        }
    }
}

/** A phone title: wraps at words (never mid-word), at most three lines, then an ellipsis. */
@Composable
internal fun PhoneMediaTitle(text: String, modifier: Modifier = Modifier) =
    Text(
        text, style = disp(24.sp, 800, 1.15f).copy(lineBreak = LineBreak.Heading, hyphens = Hyphens.None), color = C.text,
        modifier = modifier, maxLines = 3, overflow = TextOverflow.Ellipsis,
    )

/** Skip 56 / play 72 / skip 56, true circles. */
@Composable
internal fun PhoneTransport(playing: Boolean, backSecs: Int, fwdSecs: Int, onBack: () -> Unit, onToggle: () -> Unit, onForward: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        PhoneSkip(true, backSecs, onBack)
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(C.accent).clickable(role = Role.Button, onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) { Icon(if (playing) Solid.Pause else Solid.Play, if (playing) "Pause" else "Play", tint = C.onAccent, modifier = Modifier.size(32.dp)) }
        PhoneSkip(false, fwdSecs, onForward)
    }
}

@Composable
private fun PhoneSkip(back: Boolean, secs: Int, onClick: () -> Unit) {
    Box(Modifier.size(56.dp).clip(CircleShape).background(C.surface2).clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(if (back) Lucide.RotateCcw else Lucide.RotateCw, if (back) "Back $secs seconds" else "Forward $secs seconds", tint = C.text, modifier = Modifier.size(28.dp))
        // The number is a glyph inside a fixed circle: it keeps its size when the system font grows.
        Text("$secs", style = body((11f / LocalDensity.current.fontScale).sp, 800), color = C.text, modifier = Modifier.padding(top = 2.dp))
    }
}

/** Next and Stop as an equal-weight row of 48dp buttons. Next is left out when there is nothing to go to. */
@Composable
internal fun PhoneNextStop(showNext: Boolean, nextEnabled: Boolean, onNext: () -> Unit, stopLabel: String, onStop: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showNext) NickButton("Next episode", onNext, Modifier.weight(1f).fillMaxHeight(), leading = Solid.Next, enabled = nextEnabled)
        NickButton(stopLabel, onStop, Modifier.weight(1f).fillMaxHeight(), leading = Solid.Stop)
    }
}

/** The seek bar's time row: 14sp, tabular digits. */
@Composable
internal fun PhoneTimes(left: String, right: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(left, style = body(14.sp, 700).copy(fontFeatureSettings = "tnum"), color = C.text)
        Text(right, style = body(14.sp, 700).copy(fontFeatureSettings = "tnum"), color = C.muted)
    }
}

@Composable
private fun PhoneNowPlaying(
    v: NodeView, pb: com.nbaltas.nickos.master.Playback, item: JfItem, jf: com.nbaltas.nickos.jellyfin.Jellyfin?, pos: Long, next: JfItem?,
    scrub: Float?, onScrub: (Float?) -> Unit, scroll: ScrollState,
) {
    val m = App.get(LocalContext.current).master
    Box(Modifier.fillMaxSize()) {
        jf?.backdrop(item)?.let { AsyncImage(it, null, Modifier.fillMaxSize().alpha(0.3f), contentScale = ContentScale.Crop) }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to C.bg.copy(alpha = 0.6f), 0.5f to C.bg.copy(alpha = 0.92f), 1f to C.bg)))
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = PhoneMetrics.gutter, vertical = PhoneMetrics.gutter),
            verticalArrangement = Arrangement.spacedBy(PhoneMetrics.sectionGap),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(PhoneMetrics.gutter)) {
                Artwork(jf?.poster(item, 400), item.displayTitle, Modifier.size(80.dp, 120.dp), radius = PhoneMetrics.nestedRadius, titleSize = 14, showTitle = LocalDensity.current.fontScale < 1.5f)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Solid.Play, null, tint = C.accent, modifier = Modifier.size(10.dp))
                        EyebrowText(if (pb.session.paused) "Paused" else "Playing now", C.accent)
                    }
                    PhoneMediaTitle(item.displayTitle)
                    if (item.isEpisode) {
                        if (item.season != null && item.index != null) Text("Season ${item.season} · Episode ${item.index}", style = body(14.sp, 700, 1.3f), color = C.text)
                        Text(item.name, style = body(14.sp, 500, 1.3f), color = C.muted)
                    } else Text(episodeLine(item), style = body(14.sp, 500, 1.3f), color = C.muted)
                }
            }
            // Jellyfin's integrated player only takes media keys (back 10 s, forward 30 s), so it can't be scrubbed: progress only.
            val integrated = !pb.session.remote
            val fwdSecs = if (integrated) 30 else 10
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val frac = scrub ?: if (item.runTicks > 0) pos.toFloat() / item.runTicks else 0f
                if (integrated) Box(Modifier.fillMaxWidth().height(PhoneMetrics.minTarget), contentAlignment = Alignment.Center) { ProgressBar(frac, height = 8.dp, track = C.line) }
                else NickSlider(
                    value = frac,
                    onChange = { onScrub(it) },
                    onCommit = { f -> onScrub(null); m.control(v.node.id, "Seek", (f * item.runTicks).toLong()) },
                )
                PhoneTimes(fmtTicks((frac * item.runTicks).toLong()), fmtTicks(item.runTicks))
            }
            PhoneTransport(
                playing = !pb.session.paused, backSecs = 10, fwdSecs = fwdSecs,
                onBack = { m.control(v.node.id, "Seek", (pos - 10 * JfItem.TICKS_PER_SEC).coerceAtLeast(0)) },
                onToggle = { m.control(v.node.id, if (pb.session.paused) "Unpause" else "Pause") },
                onForward = { m.control(v.node.id, "Seek", (pos + fwdSecs * JfItem.TICKS_PER_SEC).coerceAtMost(item.runTicks)) },
            )
            PhoneNextStop(
                showNext = next != null, nextEnabled = true,
                onNext = { next?.let { n -> m.playOn(v.node.id, n.id, 0, n.displayTitle) } },
                stopLabel = "Stop", onStop = { m.control(v.node.id, "Stop") },
            )
            Column(verticalArrangement = Arrangement.spacedBy(PhoneMetrics.cardGap)) {
                EyebrowText("On ${v.node.name}")
                DeviceSliders(v)
            }
        }
    }
}

@Composable
private fun SkipButton(back: Boolean, secs: Int = 10, onClick: () -> Unit) {
    Box(Modifier.size(76.dp).clip(CircleShape).background(C.surface2).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(if (back) Lucide.RotateCcw else Lucide.RotateCw, if (back) "Back $secs seconds" else "Forward $secs seconds", tint = C.text, modifier = Modifier.size(34.dp))
        Text("$secs", style = body(10.sp, 800), color = C.text, modifier = Modifier.padding(top = 2.dp))
    }
}

/** Volume + Brightness cards. They act on the tablet itself, one command per release. */
@Composable
private fun DeviceSliders(v: NodeView) {
    val m = App.get(LocalContext.current).master
    SliderCard(Lucide.Volume2, "Volume", v.status?.volume ?: 0) { m.setVolume(v.node.id, it) }
    SliderCard(
        Lucide.Sun, "Brightness", v.status?.brightness ?: 0, unavailable = brightnessReason(v),
        askedLine = askedLine(v), onAsk = { m.askPermission(v.node.id) },
    ) { m.setBrightness(v.node.id, it) }
}

/** "Asked on Hailey's Tablet. Tap Allow on that tablet." for two minutes after a greyed control was tapped, else null. */
@Composable
private fun askedLine(v: NodeView): String? {
    val at = App.get(LocalContext.current).master.asked.collectAsState().value[v.node.id]
    val now = rememberNow(1000)
    return if (ControlAccess.askedActive(at, now)) ControlAccess.askedLine(v.node.name) else null
}

/** Why Brightness cannot work on this tablet yet, or null when it can. */
private fun brightnessReason(v: NodeView): String? =
    if (v.status?.brightnessOk == false) ControlAccess.brightnessReason(v.node.name) else null

@Composable
private fun SliderCard(icon: ImageVector, label: String, value: Int, big: Boolean = false, modifier: Modifier = Modifier, unavailable: String? = null, askedLine: String? = null, onAsk: (() -> Unit)? = null, onCommit: (Int) -> Unit) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = dragging ?: (value / 100f)
    if (LocalPhone.current) {
        Column(
            modifier.fillMaxWidth().clip(RoundedCornerShape(PhoneMetrics.cardRadius)).background(C.surface)
                .then(if (unavailable != null && onAsk != null) Modifier.clickable(onClick = onAsk) else Modifier)
                // A working slider keeps its 48dp hit box but loses the extra air under the thumb.
                .padding(start = PhoneMetrics.cardPadding, end = PhoneMetrics.cardPadding, top = PhoneMetrics.cardPadding, bottom = if (unavailable == null) 8.dp else PhoneMetrics.cardPadding),
            verticalArrangement = Arrangement.spacedBy(if (unavailable == null) 4.dp else PhoneMetrics.relatedGap),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(icon, null, tint = C.text, modifier = Modifier.size(20.dp))
                Text(label, style = body(16.sp, 700), color = C.text, modifier = Modifier.weight(1f))
                if (unavailable == null) Text("${(shown * 100).roundToInt()}%", style = body(16.sp, 700).copy(fontFeatureSettings = "tnum"), color = C.text)
            }
            if (unavailable != null) {
                Muted(unavailable, 14)
                if (askedLine != null) Text(askedLine, style = body(14.sp, 700), color = C.accent)
            } else NickSlider(shown, onChange = { dragging = it }, onCommit = { dragging = null; onCommit((it * 100).roundToInt()) })
        }
        return
    }
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(if (big) 28.dp else 26.dp)).background(C.surface)
            .then(if (unavailable != null && onAsk != null) Modifier.clickable(onClick = onAsk) else Modifier)
            .padding(horizontal = if (big) 24.dp else 24.dp, vertical = if (big) 24.dp else 22.dp),
        verticalArrangement = Arrangement.spacedBy(if (big) 18.dp else 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = C.text, modifier = Modifier.size(22.dp))
            Text(label, style = body(if (big) 20.sp else 19.sp, if (big) 800 else 700), color = C.text, modifier = Modifier.weight(1f))
            if (!big && unavailable == null) Text("${(shown * 100).roundToInt()}%", style = disp(34.sp), color = C.text)
        }
        if (unavailable != null) {
            Muted(unavailable, if (big) 17 else 16)
            if (askedLine != null) Text(askedLine, style = body(if (big) 17.sp else 16.sp, 700), color = C.accent)
        }
        else {
            if (big) Text("${(shown * 100).roundToInt()}%", style = disp(56.sp, 800, 1f), color = C.text)
            Box(Modifier.padding(vertical = 12.dp)) {
                NickSlider(shown, onChange = { dragging = it }, onCommit = { dragging = null; onCommit((it * 100).roundToInt()) })
            }
        }
    }
}

@Composable
private fun NothingPlaying(v: NodeView, phoneScroll: ScrollState, onFind: () -> Unit) {
    val m = App.get(LocalContext.current).master
    if (LocalPhone.current) {
        val st = v.status
        Column(
            Modifier.fillMaxSize().verticalScroll(phoneScroll).padding(PhoneMetrics.gutter),
            verticalArrangement = Arrangement.spacedBy(PhoneMetrics.sectionGap),
        ) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(PhoneMetrics.cardRadius)).background(C.surface).padding(PhoneMetrics.cardPadding),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppTileIcon(v.fgApp, if (st?.fg == null) Lucide.House else Lucide.LayoutGrid, 48.dp, 14.dp, v.node.color)
                Text("Nothing playing", style = disp(24.sp, 800, 1.15f), color = C.text, textAlign = TextAlign.Center)
                Text(
                    buildAnnotatedString {
                        append(kidName(v.node.name))
                        if (st?.fg != null) {
                            append(" is using ")
                            withStyle(SpanStyle(color = C.text, fontWeight = FontWeight.Bold)) { append(st.fgLabel ?: "an app") }
                            append(".")
                        } else append(" is on the home screen.")
                    },
                    style = body(16.sp, 500, 1.4f), color = C.muted, textAlign = TextAlign.Center,
                )
                if (st?.fg != null) NickButton("Bring back to home", { m.home(v.node.id) }, Modifier.fillMaxWidth(), style = BtnStyle.GHOST, leading = Lucide.House)
                NickButton("Find something to watch", onFind, Modifier.fillMaxWidth(), style = BtnStyle.PRIMARY, leading = Lucide.Film)
            }
            Column(verticalArrangement = Arrangement.spacedBy(PhoneMetrics.cardGap)) {
                EyebrowText("On ${v.node.name}")
                DeviceSliders(v)
            }
        }
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val narrow = maxWidth < 1000.dp
        val box: @Composable (Modifier) -> Unit = { mod ->
            Column(
                mod.dashed(C.line, 30.dp).padding(40.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterVertically),
            ) {
                val st = v.status
                AppTileIcon(v.fgApp, if (st?.fg == null) Lucide.House else Lucide.LayoutGrid, 112.dp, 31.dp, v.node.color)
                Title("Nothing playing", 44, lineHeight = 1.05f)
                Text(
                    buildAnnotatedString {
                        append(kidName(v.node.name))
                        if (st?.fg != null) {
                            append(" is using ")
                            withStyle(SpanStyle(color = C.text, fontWeight = FontWeight.Bold)) { append(st.fgLabel ?: "an app") }
                            append(".")
                        } else append(" is on the home screen.")
                    },
                    style = body(21.sp), color = C.muted,
                )
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (st?.fg != null) NickButton("Bring back to home", { m.home(v.node.id) }, style = BtnStyle.GHOST, big = true, leading = Lucide.House)
                    NickButton("Find something to watch", onFind, style = BtnStyle.PRIMARY, big = true, leading = Lucide.Film)
                }
            }
        }
        if (narrow) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                box(Modifier.fillMaxWidth().heightIn(min = 420.dp))
                EyebrowText("On ${v.node.name}")
                DeviceSliders(v)
            }
        } else {
            Row(Modifier.fillMaxSize().padding(start = 40.dp, end = 32.dp, top = 32.dp, bottom = 32.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                box(Modifier.weight(1f).fillMaxHeight())
                Column(Modifier.width(360.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    EyebrowText("On ${v.node.name}")
                    DeviceSliders(v)
                }
            }
        }
    }
}

@Composable
private fun OfflineBody(v: NodeView, onAll: () -> Unit, phoneScroll: ScrollState) {
    val app = App.get(LocalContext.current)
    val m = app.master
    // Read so the page recomposes when the car network or the saved hotspot changes.
    m.carNet.state.collectAsState().value
    app.store.carNetOn.collectAsState().value
    val hotspot = app.store.hotspot.collectAsState().value
    val reason = m.offlineReasons(rememberNow(1000))[v.node.id]
    val tips = offlineTips(reason, hotspot?.takeIf { !it.direct }?.ssid)
    if (LocalPhone.current) {
        var confirmUnlink by remember { mutableStateOf(false) }
        val seen = seenAgo(v.node.lastSeen).replaceFirstChar { it.uppercase() }.replace("Seen", "Last seen")
        Column(
            Modifier.fillMaxSize().verticalScroll(phoneScroll).padding(PhoneMetrics.gutter),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(PhoneMetrics.cardGap),
        ) {
            Box(Modifier.size(72.dp).clip(CircleShape).background(C.surface), contentAlignment = Alignment.Center) {
                Icon(Lucide.WifiOff, null, tint = C.muted, modifier = Modifier.size(32.dp))
            }
            Text("${v.node.name} is offline", style = disp(28.sp, 800, 1.15f), color = C.text, textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(seen.ifEmpty { null }, v.node.lastBattery?.let { "battery was $it%" }).joinToString(" · "),
                style = body(16.sp, 500, 1.4f), color = C.muted, textAlign = TextAlign.Center,
            )
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                tips.forEach { (icon, text) ->
                    Tip(when (icon) { TipIcon.WIFI_OFF -> Lucide.WifiOff; TipIcon.WIFI -> Lucide.Wifi; TipIcon.POWER -> Lucide.Power }, text)
                }
            }
            NickButton("Try again", { m.reconnect(v.node.id) }, Modifier.fillMaxWidth(), style = BtnStyle.PRIMARY, leading = Lucide.RefreshCw)
            NickButton("All tablets", onAll, Modifier.fillMaxWidth(), style = BtnStyle.GHOST)
            // A tablet reset while this one was away never said goodbye: let the parent forget it here.
            Spacer(Modifier.height(PhoneMetrics.relatedGap))
            Muted("Reset or gave it away? Unlink it so it stops showing here.", 17, Modifier.fillMaxWidth())
            NickButton(if (confirmUnlink) "Tap again to unlink" else "Unlink tablet", {
                if (confirmUnlink) { m.unlink(v.node.id); onAll() } else confirmUnlink = true
            }, Modifier.fillMaxWidth(), style = BtnStyle.DANGER, leading = Lucide.Unlink)
        }
        return
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 80.dp, end = 80.dp, top = 40.dp, bottom = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterVertically),
    ) {
        Box(Modifier.size(136.dp).clip(CircleShape).background(C.surface), contentAlignment = Alignment.Center) {
            Icon(Lucide.WifiOff, null, tint = C.muted, modifier = Modifier.size(56.dp))
        }
        Title("${v.node.name} is offline", 52, lineHeight = 1.05f)
        val seen = seenAgo(v.node.lastSeen).replaceFirstChar { it.uppercase() }.replace("Seen", "Last seen")
        Muted(listOfNotNull(seen.ifEmpty { null }, v.node.lastBattery?.let { "battery was $it%" }).joinToString(" · "), 20)
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            tips.forEach { (icon, text) ->
                Tip(when (icon) { TipIcon.WIFI_OFF -> Lucide.WifiOff; TipIcon.WIFI -> Lucide.Wifi; TipIcon.POWER -> Lucide.Power }, text)
            }
        }
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            NickButton("Try again", { m.reconnect(v.node.id) }, style = BtnStyle.PRIMARY, big = true, leading = Lucide.RefreshCw)
            NickButton("All tablets", onAll, style = BtnStyle.GHOST, big = true)
        }
        // A tablet reset while this one was away never said goodbye: let the parent forget it here.
        var confirmUnlink by remember { mutableStateOf(false) }
        Muted("Reset or gave it away? Unlink it so it stops showing here.", 17, Modifier.padding(top = 10.dp))
        NickButton(if (confirmUnlink) "Tap again to unlink" else "Unlink tablet", {
            if (confirmUnlink) { m.unlink(v.node.id); onAll() } else confirmUnlink = true
        }, style = BtnStyle.DANGER, leading = Lucide.Unlink)
    }
}

@Composable
private fun Tip(icon: ImageVector, text: String) {
    if (LocalPhone.current) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = C.text, modifier = Modifier.size(20.dp))
            Text(text, style = body(16.sp, 600, 1.4f), color = C.text, modifier = Modifier.weight(1f))
        }
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, null, tint = C.text, modifier = Modifier.size(22.dp))
        Text(text, style = body(20.sp, 600), color = C.text)
    }
}

// ---------------- Apps ----------------

@Composable
private fun AppsTab(v: NodeView, phoneGrid: LazyGridState) {
    val m = App.get(LocalContext.current).master
    LaunchedEffect(v.node.id) { m.requestApps(v.node.id) }
    val apps = v.apps
    val st = v.status
    val allowed = st?.allowed.orEmpty()
    val open = st?.fg
    if (LocalPhone.current) {
        PhoneAppsTab(v, phoneGrid)
        return
    }
    Column(Modifier.fillMaxSize().padding(start = 40.dp, end = 40.dp, top = 26.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        val intro: @Composable (Modifier) -> Unit = { mod ->
            Column(mod, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Tap an app to open it on ${v.node.name} now.", style = body(20.sp, 700), color = C.text)
                Text(
                    buildAnnotatedString {
                        append("Switch on ")
                        withStyle(SpanStyle(color = C.text, fontWeight = FontWeight.Bold)) { append("Home") }
                        append(" to show it on the home screen")
                        if (apps != null) append(" · ${allowed.count { a -> apps.any { it.pkg == a } }} of ${apps.size} shown")
                    },
                    style = body(17.sp), color = C.muted,
                )
            }
        }
        val buttons: @Composable () -> Unit = {
            NickButton("Bring back to home", { m.home(v.node.id) }, leading = Lucide.House)
            val locked = st?.lockedApp
            when {
                locked != null -> NickButton("Unlock ${apps?.firstOrNull { it.pkg == locked }?.label ?: "app"}", { m.lockToApp(v.node.id, null) }, style = BtnStyle.PRIMARY, leading = Lucide.LockOpen)
                open != null -> NickButton("Lock to ${st.fgLabel ?: "this app"}", { m.lockToApp(v.node.id, open) }, style = BtnStyle.PRIMARY, leading = Lucide.Lock)
            }
        }
        // Phone-width Master: text above the buttons instead of squeezed beside them.
        if (LocalConfiguration.current.screenWidthDp < 600) {
            intro(Modifier.fillMaxWidth())
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { buttons() }
        } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            intro(Modifier.weight(1f))
            buttons()
        }
        if (apps == null) {
            Muted("Getting the app list from ${v.node.name}…", 19)
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp),
        ) {
            items(apps, key = { it.pkg }) { a ->
                val isOpen = a.pkg == open
                val shape = RoundedCornerShape(24.dp)
                Box(Modifier.clip(shape).background(C.surface).border(3.dp, if (isOpen) C.accent else Color.Transparent, shape)) {
                    Column {
                        Column(
                            Modifier.fillMaxWidth().clickable { m.open(v.node.id, a.pkg) }.padding(top = 20.dp, bottom = 10.dp, start = 8.dp, end = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            AppTileIcon(a, Lucide.LayoutGrid, 76.dp, 21.dp, v.node.color)
                            Text(a.label, style = body(17.sp, 700), color = C.text, maxLines = 1)
                        }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(C.surface2))
                        Row(
                            // The whole row is the target, not just the switch: a reach from the driver's seat must not open the app by mistake.
                            Modifier.fillMaxWidth().heightIn(min = 52.dp)
                                .toggleable(a.pkg in allowed, role = androidx.compose.ui.semantics.Role.Switch) { m.allow(v.node.id, a.pkg, it) }
                                .padding(start = 14.dp, end = 12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Home", style = body(14.sp, 600), color = C.muted)
                            NickSwitch(a.pkg in allowed) { m.allow(v.node.id, a.pkg, it) }
                        }
                    }
                    if (isOpen) Box(
                        Modifier.align(Alignment.TopEnd).padding(10.dp).height(24.dp).clip(RoundedCornerShape(12.dp)).background(C.accent).padding(horizontal = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("OPEN", style = body(12.sp, 800), color = C.onAccent) }
                }
            }
        }
    }
}

/**
 * A label with a switch on the right, full width, wrapping, at least 48dp high. The whole row is the one toggle target; the
 * switch inside is only drawn (its own semantics are cleared) so a screen reader finds one switch, not two.
 */
@Composable
internal fun SwitchRow(label: String, on: Boolean, modifier: Modifier = Modifier, textStyle: androidx.compose.ui.text.TextStyle = body(16.sp, 700, 1.3f), color: Color = C.text, onChange: (Boolean) -> Unit) {
    Row(
        modifier.fillMaxWidth().heightIn(min = PhoneMetrics.minTarget).toggleable(on, role = Role.Switch) { onChange(it) },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, style = textStyle, color = color, modifier = Modifier.weight(1f))
        Box(Modifier.clearAndSetSemantics {}) { NickSwitch(on) { onChange(it) } }
    }
}

/** Phone Apps: the explanation and two 48dp+ actions, then two columns of app tiles in the one scrolling grid. */
@Composable
private fun PhoneAppsTab(v: NodeView, gridState: LazyGridState) {
    val m = App.get(LocalContext.current).master
    val apps = v.apps
    val st = v.status
    val allowed = st?.allowed.orEmpty()
    val open = st?.fg
    LazyVerticalGrid(
        columns = GridCells.Fixed(2), state = gridState,
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(PhoneMetrics.cardGap),
        verticalArrangement = Arrangement.spacedBy(PhoneMetrics.cardGap),
        contentPadding = PaddingValues(start = PhoneMetrics.gutter, end = PhoneMetrics.gutter, top = PhoneMetrics.gutter, bottom = 32.dp),
    ) {
        item(key = "intro", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Tap an app to open it on ${v.node.name} now.", style = body(16.sp, 700, 1.3f), color = C.text)
                Text(
                    buildAnnotatedString {
                        append("Switch on ")
                        withStyle(SpanStyle(color = C.text, fontWeight = FontWeight.Bold)) { append("Home") }
                        append(" to show it on the home screen")
                        if (apps != null) append(" · ${allowed.count { a -> apps.any { it.pkg == a } }} of ${apps.size} shown")
                    },
                    style = body(14.sp, 500, 1.4f), color = C.muted,
                )
            }
        }
        item(key = "actions", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(PhoneMetrics.relatedGap)) {
                NickButton("Bring back to home", { m.home(v.node.id) }, Modifier.fillMaxWidth(), leading = Lucide.House)
                val locked = st?.lockedApp
                when {
                    locked != null -> NickButton("Unlock ${apps?.firstOrNull { it.pkg == locked }?.label ?: "app"}", { m.lockToApp(v.node.id, null) }, Modifier.fillMaxWidth(), BtnStyle.PRIMARY, leading = Lucide.LockOpen)
                    open != null -> NickButton("Lock to ${st.fgLabel ?: "this app"}", { m.lockToApp(v.node.id, open) }, Modifier.fillMaxWidth(), BtnStyle.PRIMARY, leading = Lucide.Lock)
                }
            }
        }
        if (apps == null) {
            item(key = "loading", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { Muted("Getting the app list from ${v.node.name}…", 16) }
            return@LazyVerticalGrid
        }
        items(apps, key = { it.pkg }) { a ->
            val isOpen = a.pkg == open
            val shape = RoundedCornerShape(PhoneMetrics.mediaRadius)
            Box(Modifier.clip(shape).background(C.surface).border(2.dp, if (isOpen) C.accent else Color.Transparent, shape)) {
                Column {
                    Column(
                        Modifier.fillMaxWidth().clickable(role = Role.Button) { m.open(v.node.id, a.pkg) }.padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AppTileIcon(a, Lucide.LayoutGrid, 48.dp, 14.dp, v.node.color)
                        Text(a.label, style = body(16.sp, 700, 1.25f), color = C.text, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(C.surface2))
                    // The whole row is the target, not just the switch: a reach from the driver's seat must not open the app by mistake.
                    SwitchRow("Home", a.pkg in allowed, Modifier.padding(start = 12.dp, end = 8.dp), body(14.sp, 600), C.muted) { m.allow(v.node.id, a.pkg, it) }
                }
                if (isOpen) Box(
                    Modifier.align(Alignment.TopEnd).padding(8.dp).height(24.dp).clip(RoundedCornerShape(12.dp)).background(C.accent).padding(horizontal = 9.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("OPEN", style = body(12.sp, 800), color = C.onAccent) }
            }
        }
    }
}

// ---------------- Device ----------------

@Composable
private fun DeviceTab(v: NodeView, onUnlinked: () -> Unit, scroll: ScrollState? = null) {
    val m = App.get(LocalContext.current).master
    val st = v.status
    val phone = LocalPhone.current
    var renaming by remember { mutableStateOf(false) }
    var confirmUnlink by remember { mutableStateOf(false) }
    val now = rememberNow(1000)
    val ownScroll = rememberScrollState()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cols = if (maxWidth < 1000.dp) 1 else 3
        val gap = 20.dp
        val cardH: Dp = if (cols == 3) (maxHeight - 26.dp - 32.dp - gap) / 2 else 300.dp
        val cards: List<@Composable (Modifier) -> Unit> = listOf(
            { mod ->
                DeviceCard(mod, Lucide.Tablet, "Screen", rotateIcon = true) {
                    val segmented: @Composable () -> Unit = {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(if (phone) PhoneMetrics.fieldRadius else 22.dp)).background(C.surface2).padding(if (phone) 4.dp else 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(if (phone) 4.dp else 6.dp),
                        ) {
                            val on = st?.screenOn != false
                            val segH = if (phone) PhoneMetrics.minTarget else 68.dp
                            SegBtn(Lucide.MonitorOff, "Off", !on, Modifier.weight(1f), dim = st?.screenOffOk == false, height = segH) {
                                if (st?.screenOffOk == false) m.askPermission(v.node.id) else m.screen(v.node.id, false)
                            }
                            SegBtn(Lucide.Sun, "On", on, Modifier.weight(1f), height = segH) { m.screen(v.node.id, true) }
                        }
                    }
                    val lockBtn: @Composable () -> Unit = {
                        if (st?.lockedBy != null) NickButton("Unlock tablet", { m.lock(v.node.id, false) }, Modifier.fillMaxWidth(), BtnStyle.PRIMARY, big = !phone, leading = Lucide.LockOpen)
                        else NickButton("Lock tablet now", { m.lock(v.node.id, true) }, Modifier.fillMaxWidth().then(if (phone) Modifier.heightIn(min = PhoneMetrics.buttonHeight) else Modifier), big = !phone, leading = Lucide.Lock)
                    }
                    // Hidden when the Node does not have the touch1 capability.
                    val touchRow: @Composable () -> Unit = { if (st?.supportsTouch == true) TouchSwitchRow(v, st.touch, st.overlay, m) }
                    // An old Node has no "ring1": no button.
                    val ringBtn: @Composable () -> Unit = { if (st?.supportsRing == true) RingButton(st.ringing) { m.ring(v.node.id, !st.ringing) } }
                    // One short line about the On/Off switch.
                    val offHint: @Composable () -> Unit = {
                        if (st?.screenOffOk == false) {
                            val asked = askedLine(v)
                            if (asked != null) Text(asked, style = body(14.sp, 700), color = C.accent, maxLines = if (phone) Int.MAX_VALUE else 2)
                            else Muted(ControlAccess.SCREEN_OFF_SHORT, 14)
                        }
                    }
                    // Two separate things. Screen view: the grown-up taps Allow on the tablet. Touch control: the accessibility switch,
                    // which some updates turn off; open that setting on the tablet in one tap (a 90 s window the parent can end).
                    val remote: @Composable () -> Unit = {
                        if (st?.supportsScreen == true) {
                            if (st.screen == RemoteMessages.NEEDS_CONSENT) Text("Screen: tap Allow on ${v.node.name}", style = body(14.sp, 700), color = C.accent, maxLines = if (phone) Int.MAX_VALUE else 2)
                            if (st.maintenance) {
                                Text("Settings open on ${v.node.name}", style = body(14.sp, 700), color = C.danger, maxLines = if (phone) Int.MAX_VALUE else 2)
                                NickButton("Lock now", { m.home(v.node.id) }, Modifier.fillMaxWidth(), BtnStyle.DANGER)
                            } else if (st.control != RemoteMessages.CONTROL_READY) {
                                Text("Touch control: off", style = body(14.sp, 700), color = C.danger, maxLines = if (phone) Int.MAX_VALUE else 2)
                                NickButton("Open setting on tablet", { m.askControl(v.node.id) }, Modifier.fillMaxWidth())
                            }
                        }
                    }
                    if (phone) {
                        // The hint explains the On/Off switch, so it sits right under it. Touch is its own group below a divider.
                        Column(verticalArrangement = Arrangement.spacedBy(PhoneMetrics.cardGap)) { segmented(); offHint() }
                        lockBtn()
                        ringBtn()
                        Box(Modifier.fillMaxWidth().height(1.dp).background(C.surface2))
                        touchRow()
                        remote()
                    } else {
                        segmented(); lockBtn(); touchRow(); ringBtn(); offHint(); remote()
                    }
                }
            },
            { mod ->
                DeviceCard(mod, Lucide.Moon, "Bedtime", animateSize = true) {
                    val at = st?.bedtimeAt ?: 0
                    val left = if (at > 0) com.nbaltas.nickos.ui.theme.minutesLeft(at - now) else 0
                    var chosen by remember { mutableStateOf<Int?>(null) }
                    if (phone) {
                        // Four equal rounded rectangles (a 2x2 grid at large font sizes). Tapping sends at once, as before.
                        val pick: @Composable RowScope.(Int) -> Unit = { min ->
                            val sel = at > 0 && chosen == min
                            val bg by animateColorAsState(if (sel) C.accent else C.surface2, tween(120), label = "bedtimeBg")
                            val fg = if (sel) C.onAccent else C.text
                            Column(
                                Modifier.weight(1f).heightIn(min = 56.dp).clip(RoundedCornerShape(PhoneMetrics.nestedRadius)).background(bg)
                                    .selectable(selected = sel, role = Role.RadioButton) { chosen = BedtimeCommands.pick(min) { m.bedtime(v.node.id, it) } }
                                    .semantics { contentDescription = "$min minutes" }.padding(vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                            ) {
                                Text("$min", style = body(18.sp, 700, 1.1f).copy(fontFeatureSettings = "tnum"), color = fg)
                                Text("min", style = body(12.sp, 600, 16f / 12f), color = fg.copy(alpha = 0.8f))
                            }
                        }
                        val choices = BedtimeCommands.choices
                        if (LocalDensity.current.fontScale >= 1.5f) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                choices.chunked(2).forEach { pair -> Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) { pair.forEach { pick(it) } } }
                            }
                        } else Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) { choices.forEach { pick(it) } }
                        if (at > 0) {
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(PhoneMetrics.nestedRadius)).background(C.surface2).padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(Lucide.Clock, null, tint = C.muted, modifier = Modifier.size(20.dp))
                                Text("$left min left", style = body(18.sp, 700).copy(fontFeatureSettings = "tnum"), color = C.text, modifier = Modifier.weight(1f))
                                NickButton("Cancel", { chosen = null; BedtimeCommands.cancel { m.bedtime(v.node.id, it) } }, style = BtnStyle.GHOST)
                            }
                        } else Muted("Pick a time. The tablet warns 5 minutes before, then turns its screen off.", 14)
                        return@DeviceCard
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(15, 30, 45, 60).forEach { min ->
                            val sel = at > 0 && chosen == min
                            Box(
                                Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(28.dp)).background(if (sel) C.accent else C.surface2)
                                    .clickable { chosen = min; m.bedtime(v.node.id, min) },
                                contentAlignment = Alignment.Center,
                            ) { Text("$min min", style = body(18.sp, 600), color = if (sel) C.onAccent else C.text) }
                        }
                    }
                    if (at > 0) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.surface2).padding(horizontal = 18.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("SCREEN TURNS OFF IN", style = body(14.sp, 700), color = C.muted)
                                Text("$left min", style = disp(34.sp), color = C.text)
                            }
                            NickButton("Cancel", { chosen = null; m.bedtime(v.node.id, 0) }, Modifier.height(52.dp), BtnStyle.GHOST)
                        }
                    } else Muted("Pick a time. The tablet warns 5 minutes before, then turns its screen off.", 16)
                }
            },
            { mod ->
                DeviceCard(mod, com.nbaltas.nickos.ui.theme.batteryIcon(v.battery ?: 100, st?.charging == true), "Battery") {
                    BatteryBody(v.battery ?: 0, st?.charging == true, st?.power) { w, min -> m.setPower(v.node.id, w, min) }
                }
            },
            { mod -> SliderCard(Lucide.Volume2, "Volume", st?.volume ?: 0, big = true, modifier = mod) { m.setVolume(v.node.id, it) } },
            { mod -> SliderCard(Lucide.Sun, "Brightness", st?.brightness ?: 0, big = true, modifier = mod, unavailable = brightnessReason(v), askedLine = askedLine(v), onAsk = { m.askPermission(v.node.id) }) { m.setBrightness(v.node.id, it) } },
            { mod ->
                DeviceCard(mod, Lucide.Settings, "Tablet", trailing = {
                    if (st != null && !st.owner) Chip(height = 30.dp, bg = C.dangerLine) { Text("Kid lock off", style = body(13.sp, 700), color = C.danger) }
                }) {
                    val unlink: @Composable (Dp) -> Unit = { h ->
                        StartButton(Lucide.Unlink, if (confirmUnlink) "Tap again to unlink" else "Unlink tablet", danger = true, height = h) {
                            if (confirmUnlink) { m.unlink(v.node.id); onUnlinked() } else confirmUnlink = true
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        StartButton(Lucide.RefreshCw, "Restart Jellyfin") { m.restartJellyfin(v.node.id) }
                        StartButton(Lucide.Pencil, "Rename tablet") { renaming = true }
                        unlink(if (phone) PhoneMetrics.minTarget else 60.dp)
                        // An old Node does not report it, so there is nothing to switch.
                        st?.landscape?.let { l ->
                            if (phone) SwitchRow("Lock to landscape", l.lock) { m.setLandscape(v.node.id, it) }
                            else Row(
                                Modifier.fillMaxWidth().toggleable(l.lock, role = androidx.compose.ui.semantics.Role.Switch) { m.setLandscape(v.node.id, it) },
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Text("Lock to landscape", style = body(17.sp, 700), color = C.text, modifier = Modifier.weight(1f))
                                NickSwitch(l.lock) { m.setLandscape(v.node.id, it) }
                            }
                            if (l.lock && l.needsPermission) Text(LandscapePolicy.NEEDS_PERMISSION, style = body(14.sp, 700), color = C.danger)
                            if (l.pending) Text(if (l.lock) LandscapePolicy.PENDING_ON else LandscapePolicy.PENDING_OFF, style = body(14.sp, 700), color = C.danger)
                        }
                    }
                }
            },
        )
        if (phone) {
            // One column of cards at their own height; the page scrolls.
            Column(
                Modifier.fillMaxSize().verticalScroll(scroll ?: ownScroll).padding(PhoneMetrics.gutter),
                verticalArrangement = Arrangement.spacedBy(PhoneMetrics.cardGap),
            ) { cards.forEach { it(Modifier.fillMaxWidth()) } }
            return@BoxWithConstraints
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(ownScroll)
                .padding(start = 40.dp, end = 40.dp, top = 26.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            cards.chunked(cols).forEach { row ->
                // A row is as tall as its tallest card (never below cardH); a tall row just makes the page scroll.
                Row(Modifier.height(IntrinsicSize.Min).heightIn(min = cardH), horizontalArrangement = Arrangement.spacedBy(gap)) { row.forEach { it(Modifier.weight(1f).fillMaxHeight()) } }
            }
        }
    }
    if (renaming) RenameDialog(v.node.name, onDone = { name -> renaming = false; if (name != null) m.rename(v.node.id, name) })
}

/**
 * "Touch" under "Lock tablet now" in the Screen card. On means the kid can touch the screen. Off sends the lock; the words below it say
 * what the tablet really does (see [TouchText]), and the Allow button asks the kid tablet for the overlay permission.
 */
@Composable
private fun TouchSwitchRow(v: NodeView, touch: NetMessages.TouchStatus?, overlayOk: Boolean, m: com.nbaltas.nickos.master.MasterRuntime, compact: Boolean = false) {
    val on = TouchText.touchOn(touch)
    val change = { nowOn: Boolean ->
        // Without the overlay permission the shield only covers NickOS: ask for the permission first.
        if (!nowOn && !overlayOk) m.askPermission(v.node.id)
        m.setTouchOff(v.node.id, !nowOn)
    }
    if (LocalPhone.current) {
        Column(verticalArrangement = Arrangement.spacedBy(PhoneMetrics.labelGap)) {
            SwitchRow("Touch", on) { change(it) }
            val status = TouchText.status(touch, v.node.name)
            Text(status ?: TouchText.HINT, style = body(14.sp, 700, 1.4f), color = if (status != null && status != TouchText.IS_OFF) C.danger else C.muted)
            if (TouchText.offerAllow(touch)) StartButton(Lucide.Settings, "Allow on ${v.node.name}", height = PhoneMetrics.minTarget) { m.askPermission(v.node.id) }
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 0.dp else 4.dp)) {
        Row(
            Modifier.fillMaxWidth().toggleable(on, role = androidx.compose.ui.semantics.Role.Switch) { change(it) },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Touch", style = body(if (compact) 16.sp else 17.sp, 700), color = C.text, modifier = Modifier.weight(1f))
            NickSwitch(on) { change(it) }
        }
        val status = TouchText.status(touch, v.node.name)
        Text(status ?: TouchText.HINT, style = body(if (compact) 13.sp else 14.sp, 700), color = if (status != null && status != TouchText.IS_OFF) C.danger else C.muted)
        if (TouchText.offerAllow(touch)) StartButton(Lucide.Settings, "Allow on ${v.node.name}", height = 40.dp) { m.askPermission(v.node.id) }
    }
}

/**
 * The Battery card's content. An old Node reports no [power], so the two power controls are hidden and the card keeps
 * its big number; with them the number shrinks and the card scrolls if the tablet is short.
 */
@Composable
internal fun BatteryBody(battery: Int, charging: Boolean, power: NetMessages.PowerStatus?, onPower: (Boolean, Int) -> Unit) {
    val low = battery < 25
    val phone = LocalPhone.current
    if (power == null && !phone) {
        Text("$battery%", style = disp(88.sp, 800, 1f), color = if (low) C.danger else C.text)
        ProgressBar(battery / 100f, height = 14.dp, fill = if (low) C.danger else C.online)
        Muted(if (charging) "Charging" else "Not charging", 17, weight = 600)
    } else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("$battery%", style = disp(40.sp, 800, 1f), color = if (low) C.danger else C.text)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ProgressBar(battery / 100f, height = 14.dp, fill = if (low) C.danger else C.online)
                Muted(if (charging) "Charging" else "Not charging", 15, weight = 600)
            }
        }
        if (power != null) PowerControls(power.wake, power.offMin, if (power.needsPermission) PowerPolicy.NEEDS_PERMISSION_TABLET else null, onPower)
    }
}

@Composable
private fun DeviceCard(
    modifier: Modifier, icon: ImageVector, title: String, rotateIcon: Boolean = false,
    trailing: @Composable () -> Unit = {}, animateSize: Boolean = false, content: @Composable () -> Unit,
) {
    if (LocalPhone.current) {
        // Intrinsic height, 16dp padding, 12dp between rows. The shell animates when a row appears (bedtime timer).
        Column(
            modifier.clip(RoundedCornerShape(PhoneMetrics.cardRadius)).background(C.surface)
                .then(if (animateSize) Modifier.animateContentSize(tween(180)) else Modifier).padding(PhoneMetrics.cardPadding),
            verticalArrangement = Arrangement.spacedBy(PhoneMetrics.cardGap),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(icon, null, tint = C.text, modifier = Modifier.size(20.dp).then(if (rotateIcon) Modifier.rotate90() else Modifier))
                Text(title, style = body(18.sp, 800, 1.2f), color = C.text, modifier = Modifier.weight(1f))
                trailing()
            }
            content()
        }
        return
    }
    Column(modifier.clip(RoundedCornerShape(28.dp)).background(C.surface).padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = C.text, modifier = Modifier.size(22.dp).then(if (rotateIcon) Modifier.rotate90() else Modifier))
            Text(title, style = body(20.sp, 800), color = C.text, modifier = Modifier.weight(1f))
            trailing()
        }
        content()
    }
}

@Composable
private fun SegBtn(icon: ImageVector, label: String, on: Boolean, modifier: Modifier, enabled: Boolean = true, dim: Boolean = false, height: Dp = 68.dp, onClick: () -> Unit) {
    if (LocalPhone.current) {
        val bg by animateColorAsState(if (on) C.accent else Color.Transparent, tween(120), label = "segBtnBg")
        val col by animateColorAsState(if (on) C.onAccent else C.muted, tween(120), label = "segBtnFg")
        Row(
            modifier.heightIn(min = height).clip(RoundedCornerShape(10.dp)).background(bg)
                .alpha(if (enabled && !dim) 1f else 0.4f).selectable(selected = on, enabled = enabled, role = Role.RadioButton, onClick = onClick).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = col, modifier = Modifier.size(20.dp))
            Text(label, style = body(16.sp, 700), color = col)
        }
        return
    }
    Row(
        modifier.height(height).clip(RoundedCornerShape(17.dp)).background(if (on) C.accent else Color.Transparent)
            .alpha(if (enabled && !dim) 1f else 0.4f).clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
    ) {
        val col = if (on) C.onAccent else C.muted
        Icon(icon, null, tint = col, modifier = Modifier.size(22.dp))
        Text(label, style = body(20.sp, 800), color = col)
    }
}

@Composable
private fun StartButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, danger: Boolean = false, height: Dp = 60.dp, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    if (LocalPhone.current) {
        // A minimum height, not a fixed one: a long label ("Tap again to unlink", a device name) wraps instead of clipping.
        val fg = if (danger) C.danger else C.text
        val phoneShape = RoundedCornerShape(16.dp)
        Row(
            modifier.fillMaxWidth().heightIn(min = maxOf(height, PhoneMetrics.minTarget)).clip(phoneShape)
                .then(if (danger) Modifier.border(2.dp, C.dangerLine, phoneShape) else Modifier.background(C.surface2))
                .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
            Text(label, style = body(16.sp, 700, 1.25f), color = fg, modifier = Modifier.weight(1f, fill = false), textAlign = TextAlign.Center)
        }
        return
    }
    Row(
        modifier.fillMaxWidth().height(height).clip(shape)
            .then(if (danger) Modifier.border(2.dp, C.dangerLine, shape) else Modifier.background(C.surface2))
            .clickable(onClick = onClick).padding(horizontal = if (height < 56.dp) 8.dp else 28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (height < 56.dp) Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally) else Arrangement.spacedBy(12.dp),
    ) {
        val col = if (danger) C.danger else C.text
        Icon(icon, null, tint = col, modifier = Modifier.size(if (height < 56.dp) 18.dp else 20.dp))
        Text(label, style = body(if (height < 56.dp) 16.sp else 19.sp, 700), color = col, maxLines = 1)
    }
}

/** "Ring" finds a lost tablet; while it sounds the same button reads "Stop ringing". */
@Composable
internal fun RingButton(ringing: Boolean, onClick: () -> Unit) =
    StartButton(Lucide.Volume2, if (ringing) "Stop ringing" else "Ring", danger = ringing, height = 48.dp, onClick = onClick)

/** True when a Device tab card is too short for three full-size buttons (every landscape tablet we support). */
internal fun tabletCompact(cardHeight: Dp) = cardHeight < 300.dp

/** Name editor used by the Device tab and Settings. [onDone] gets null on cancel. */
@Composable
fun RenameDialog(current: String, title: String = "Rename tablet", onDone: (String?) -> Unit) {
    var name by remember { mutableStateOf(current) }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).tap { onDone(null) }, contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(24.dp).clip(RoundedCornerShape(30.dp)).background(C.surface).tap {}.padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Title(title, 36)
            NickField(name, { name = it.take(28) })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.End)) {
                NickButton("Cancel", { onDone(null) }, style = BtnStyle.GHOST)
                NickButton("Save", { onDone(name.trim()) }, style = BtnStyle.PRIMARY, enabled = name.isNotBlank())
            }
        }
    }
}
