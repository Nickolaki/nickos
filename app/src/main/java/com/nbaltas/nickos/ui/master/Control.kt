package com.nbaltas.nickos.ui.master

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.nbaltas.nickos.master.Reason
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
import com.nbaltas.nickos.ui.theme.LocalParentTablet
import com.nbaltas.nickos.ui.theme.ParentContentCap
import com.nbaltas.nickos.ui.theme.LocalPhone
import com.nbaltas.nickos.ui.theme.PhoneMetrics
import com.nbaltas.nickos.ui.theme.TabletParentMetrics
import com.nbaltas.nickos.ui.theme.tabletContentWidth
import com.nbaltas.nickos.ui.theme.tabletGutter
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
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

/** Device grid columns for content width I (design §1.4): 3 from 1080, 2 from 720, else 1; at font scale 1.5 at most 2 (1 under 960); at 2.0 one. */
internal fun deviceColumns(contentWidthDp: Float, fontScale: Float): Int = when {
    fontScale >= 2f -> 1
    fontScale >= 1.5f -> if (contentWidthDp < 960f) 1 else 2
    contentWidthDp >= 1080f -> 3
    contentWidthDp >= 720f -> 2
    else -> 1
}

/** A Device card's inner width: equal columns with a 16 gap, minus the 20 padding on both sides. */
internal fun deviceCardInnerWidth(contentWidthDp: Float, columns: Int): Float = (contentWidthDp - 16f * (columns - 1)) / columns - 40f

/** Bedtime: one row of four blocks only when the inner width is at least 280 and the font scale under 1.5; else a 2x2 grid. */
internal fun bedtimeOneRow(innerWidthDp: Float, fontScale: Float): Boolean = innerWidthDp >= 280f && fontScale < 1.5f

/** The active bedtime timer's Cancel goes full width below the remaining time when the inner width is under 320. */
internal fun bedtimeCancelBelow(innerWidthDp: Float): Boolean = innerWidthDp < 320f

/** The Control header may be one row at 1180 wide or more and font scale under 1.5 (it still needs to measurably fit). */
internal fun controlHeaderOneRowAllowed(widthDp: Float, fontScale: Float): Boolean = widthDp >= 1180f && fontScale < 1.5f

/** Now Playing and the remote: two panes (right one 288) from I = 960 at normal font scale; else they stack. */
internal fun controlTwoPane(contentWidthDp: Float, fontScale: Float): Boolean = contentWidthDp >= 960f && fontScale < 1.5f

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
    TabletControlHeader(v, tab, onTab, onBack)
}

private val controlTabs = listOf<Pair<String, ImageVector?>>(
    "Now Playing" to Solid.Play, "Watch" to Lucide.Film, "Apps" to Lucide.LayoutGrid, "Device" to Lucide.Tablet, "Screen" to Lucide.Monitor,
)

/**
 * The five Control tabs: radius 16 group with 4 padding and gap, items at least 48 high with 12 side padding, 15/20 labels and 18 glyphs.
 * [scrolls] puts the group in a horizontal scroll (second header row) and brings the selected tab into view.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ControlTabs(tab: Int, onTab: (Int) -> Unit, scrolls: Boolean, modifier: Modifier = Modifier) {
    val requesters = remember { List(controlTabs.size) { BringIntoViewRequester() } }
    if (scrolls) LaunchedEffect(tab) { requesters[tab].bringIntoView() }
    Row(
        modifier.clip(RoundedCornerShape(TabletParentMetrics.segmentedRadius)).background(C.surface).padding(4.dp)
            .then(if (scrolls) Modifier.horizontalScroll(rememberScrollState()) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        controlTabs.forEachIndexed { i, (label, icon) ->
            val on = i == tab
            val bg by animateColorAsState(if (on) C.accent else Color.Transparent, tween(120), label = "ctrlTabBg")
            val fg by animateColorAsState(if (on) C.onAccent else C.muted, tween(120), label = "ctrlTabFg")
            Row(
                Modifier.heightIn(min = TabletParentMetrics.minTarget).clip(RoundedCornerShape(TabletParentMetrics.segmentedSelectedRadius)).background(bg)
                    .then(if (scrolls) Modifier.bringIntoViewRequester(requesters[i]) else Modifier)
                    .selectable(selected = on, role = Role.Tab) { onTab(i) }.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
                Text(label, style = body(15.sp, 700, 20f / 15), color = fg, maxLines = 1, softWrap = false)
            }
        }
    }
}

/**
 * Tablet Control header: one row (back, avatar, name and status, tabs, battery) when the width allows AND the row measurably fits the
 * content width; else the identity row (72) over the scrolling tab row, so nothing is ever clipped. The divider is full width.
 */
@Composable
private fun TabletControlHeader(v: NodeView, tab: Int, onTab: (Int) -> Unit, onBack: () -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    val identity: @Composable RowScope.(Boolean) -> Unit = { weighted ->
        BackButton(onBack)
        Avatar(v.node.name, v.node.color, v.node.avatar, 40.dp)
        Column(if (weighted) Modifier.weight(1f) else Modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Title(v.node.name, 25, maxLines = if (weighted) 2 else 1)
            StatusLine(v, 14, 8.dp)
        }
    }
    val battery: @Composable () -> Unit = { Battery(v.battery, v.status?.charging == true && v.online, 24, 24.dp) }
    val oneRow: @Composable (Modifier) -> Unit = { mod ->
        Row(mod, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            identity(false)
            Spacer(Modifier.width(12.dp))
            ControlTabs(tab, onTab, scrolls = false, modifier = Modifier.widthIn(max = 600.dp))
            Spacer(Modifier.weight(1f))
            battery()
        }
    }
    SubcomposeLayout(Modifier.fillMaxWidth().background(C.bg)) { c ->
        val widthDp = c.maxWidth / density
        val contentPx = (tabletContentWidth(widthDp) * density).roundToInt().coerceAtLeast(0)
        // The probe is the bare row: its natural width is asked for, it is never placed.
        val fits = controlHeaderOneRowAllowed(widthDp, fontScale) &&
            subcompose("probe") { oneRow(Modifier) }.firstOrNull()?.maxIntrinsicWidth(80.dp.roundToPx())?.let { it <= contentPx } == true
        val shown = subcompose(if (fits) "one" else "two") {
            Column(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    if (fits) oneRow(Modifier.width(contentPx.toDp()).heightIn(min = TabletParentMetrics.headerHeight))
                    else Column(Modifier.width(contentPx.toDp())) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 72.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) { identity(true); battery() }
                        Spacer(Modifier.height(8.dp))
                        ControlTabs(tab, onTab, scrolls = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(C.surface2))
            }
        }.first().measure(Constraints(minWidth = c.maxWidth, maxWidth = c.maxWidth))
        layout(shown.width, shown.height) { shown.place(0, 0) }
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
    TabletNowPlaying(v, pb, item, jf, pos, next, scrub) { scrub = it }
}

/**
 * Tablet Now Playing (design §2.3): content capped at 1152; the primary pane (art, title, seek, transport, Next/Stop) and the
 * 288 side pane (volume, brightness, up next) sit side by side from I = 960, else they stack. Both stay reachable by scrolling.
 */
@Composable
private fun TabletNowPlaying(
    v: NodeView, pb: com.nbaltas.nickos.master.Playback, item: JfItem, jf: com.nbaltas.nickos.jellyfin.Jellyfin?, pos: Long, next: JfItem?,
    scrub: Float?, onScrub: (Float?) -> Unit,
) {
    val m = App.get(LocalContext.current).master
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Backdrop at 35%, fading into the page towards the right-hand column.
        jf?.backdrop(item)?.let { AsyncImage(it, null, Modifier.fillMaxSize().alpha(0.35f), contentScale = ContentScale.Crop) }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to C.bg.copy(alpha = 0.55f), 0.62f to C.bg.copy(alpha = 0.92f), 0.78f to C.bg)))
        val i = tabletContentWidth(maxWidth.value)
        val twoPane = controlTwoPane(i, fontScale)
        val side: @Composable (Modifier) -> Unit = { mod ->
            Column(mod, verticalArrangement = Arrangement.spacedBy(TabletParentMetrics.cardGap)) {
                EyebrowText("On ${v.node.name}")
                DeviceSliders(v)
                next?.let { n ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(TabletParentMetrics.cardRadius)).background(C.surface)
                            .clickable { m.playOn(v.node.id, n.id, 0, n.displayTitle) }.padding(TabletParentMetrics.cardPadding),
                        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Artwork(jf?.landscape(n, 300), "", Modifier.size(128.dp, 72.dp), radius = TabletParentMetrics.nestedRadius, showTitle = false)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            EyebrowText("Up next")
                            Text("E${n.index ?: ""} · ${n.name}", style = body(16.sp, 700, 22f / 16), color = C.text, maxLines = 2)
                        }
                    }
                }
            }
        }
        val main: @Composable (Modifier) -> Unit = { mod ->
            Column(mod, verticalArrangement = Arrangement.spacedBy(TabletParentMetrics.sectionGap)) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Artwork(jf?.poster(item, 400), item.displayTitle, Modifier.size(128.dp, 192.dp), radius = TabletParentMetrics.mediaRadius, titleSize = 18)
                    Column(Modifier.weight(1f).padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Solid.Play, null, tint = C.accent, modifier = Modifier.size(12.dp))
                            EyebrowText(if (pb.session.paused) "Paused" else "Playing now", C.accent)
                        }
                        Title(item.displayTitle, 64, maxLines = 3)
                        if (item.isEpisode) {
                            if (item.season != null && item.index != null) Text("Season ${item.season} · Episode ${item.index}", style = body(16.sp, 700, 22f / 16), color = C.text)
                            Text(item.name, style = body(16.sp, 500, 22f / 16), color = C.muted)
                        } else Text(episodeLine(item), style = body(16.sp, 500, 22f / 16), color = C.muted)
                    }
                }
                // Jellyfin's integrated player only takes media keys (back 10 s, forward 30 s), so it
                // can't be scrubbed: show progress only. The Web player setting gives a real scrub bar.
                val integrated = !pb.session.remote
                val fwdSecs = if (integrated) 30 else 10
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val frac = scrub ?: if (item.runTicks > 0) pos.toFloat() / item.runTicks else 0f
                    if (integrated) Box(Modifier.fillMaxWidth().height(TabletParentMetrics.minTarget), contentAlignment = Alignment.Center) { ProgressBar(frac, height = 6.dp, track = C.line) }
                    else NickSlider(
                        value = frac,
                        onChange = { onScrub(it) },
                        onCommit = { f -> onScrub(null); m.control(v.node.id, "Seek", (f * item.runTicks).toLong()) },
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(fmtTicks((frac * item.runTicks).toLong()), style = body(14.sp, 700, 20f / 14).copy(fontFeatureSettings = "tnum"), color = C.text)
                        Text(fmtTicks(item.runTicks), style = body(14.sp, 700, 20f / 14).copy(fontFeatureSettings = "tnum"), color = C.muted)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                    TabletSkip(true, 10) { m.control(v.node.id, "Seek", (pos - 10 * JfItem.TICKS_PER_SEC).coerceAtLeast(0)) }
                    Box(
                        Modifier.size(64.dp).clip(CircleShape).background(C.accent)
                            .clickable(role = Role.Button) { m.control(v.node.id, if (pb.session.paused) "Unpause" else "Pause") },
                        contentAlignment = Alignment.Center,
                    ) { Icon(if (pb.session.paused) Solid.Play else Solid.Pause, if (pb.session.paused) "Play" else "Pause", tint = C.onAccent, modifier = Modifier.size(28.dp)) }
                    TabletSkip(false, fwdSecs) { m.control(v.node.id, "Seek", (pos + fwdSecs * JfItem.TICKS_PER_SEC).coerceAtMost(item.runTicks)) }
                }
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    next?.let { n -> NickButton("Next episode", { m.playOn(v.node.id, n.id, 0, n.displayTitle) }, leading = Solid.Next) }
                    NickButton("Stop", { m.control(v.node.id, "Stop") }, leading = Solid.Stop)
                }
            }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.width(i.dp).fillMaxHeight()) {
                if (twoPane) {
                    Row(
                        Modifier.fillMaxSize().padding(top = TabletParentMetrics.contentTop, bottom = TabletParentMetrics.contentBottom),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        main(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()))
                        side(Modifier.width(288.dp).fillMaxHeight().verticalScroll(rememberScrollState()))
                    }
                } else {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = TabletParentMetrics.contentTop, bottom = TabletParentMetrics.contentBottom),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) { main(Modifier.fillMaxWidth()); side(Modifier.fillMaxWidth()) }
                }
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
private fun TabletSkip(back: Boolean, secs: Int, onClick: () -> Unit) {
    Box(Modifier.size(TabletParentMetrics.minTarget).clip(CircleShape).background(C.surface2).clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(if (back) Lucide.RotateCcw else Lucide.RotateCw, if (back) "Back $secs seconds" else "Forward $secs seconds", tint = C.text, modifier = Modifier.size(22.dp))
        Text("$secs", style = body(12.sp, 800, 14f / 12).copy(fontFeatureSettings = "tnum"), color = C.text, modifier = Modifier.padding(top = 2.dp))
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
    // Tablet: a Device card (big) shows a 40 hero under an 18 heading; the Now Playing side card shows a 28 value on its heading row.
    // The slider's own 48dp box is the only air under it. A greyed control keeps its tap-to-ask card.
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(TabletParentMetrics.cardRadius)).background(C.surface)
            .then(if (unavailable != null && onAsk != null) Modifier.clickable(onClick = onAsk) else Modifier)
            .padding(TabletParentMetrics.cardPadding),
        verticalArrangement = Arrangement.spacedBy(TabletParentMetrics.relatedGap),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, tint = C.text, modifier = Modifier.size(TabletParentMetrics.iconSize))
            Text(label, style = if (big) body(18.sp, 800, 24f / 18) else body(16.sp, 700, 22f / 16), color = C.text, modifier = Modifier.weight(1f))
            if (!big && unavailable == null) Text("${(shown * 100).roundToInt()}%", style = disp(28.sp, 800, 1.15f).copy(fontFeatureSettings = "tnum"), color = C.text)
        }
        if (unavailable != null) {
            Text(unavailable, style = body(14.sp, 500, 20f / 14), color = C.muted)
            if (askedLine != null) Text(askedLine, style = body(14.sp, 700, 20f / 14), color = C.accent)
            if (onAsk != null) StartButton(Lucide.Settings, "Ask for permission", height = TabletParentMetrics.minTarget, onClick = onAsk)
        } else {
            if (big) Text("${(shown * 100).roundToInt()}%", style = disp(40.sp, 800, 1f).copy(fontFeatureSettings = "tnum"), color = C.text)
            NickSlider(shown, onChange = { dragging = it }, onCommit = { dragging = null; onCommit((it * 100).roundToInt()) })
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
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val i = tabletContentWidth(maxWidth.value)
        val twoPane = controlTwoPane(i, fontScale)
        val st = v.status
        // The group is at most 560 wide and centred in its pane; the page scrolls when it is taller than the window.
        val group: @Composable (Modifier) -> Unit = { mod ->
            Box(mod, contentAlignment = Alignment.Center) {
                Column(
                    Modifier.widthIn(max = 560.dp).fillMaxWidth().clip(RoundedCornerShape(TabletParentMetrics.cardRadius)).background(C.surface).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(TabletParentMetrics.cardGap),
                ) {
                    AppTileIcon(v.fgApp, if (st?.fg == null) Lucide.House else Lucide.LayoutGrid, 64.dp, 18.dp, v.node.color)
                    Title("Nothing playing", 40, maxLines = 2)
                    Text(
                        buildAnnotatedString {
                            append(kidName(v.node.name))
                            if (st?.fg != null) {
                                append(" is using ")
                                withStyle(SpanStyle(color = C.text, fontWeight = FontWeight.Bold)) { append(st.fgLabel ?: "an app") }
                                append(".")
                            } else append(" is on the home screen.")
                        },
                        style = body(16.sp, 500, 23f / 16), color = C.muted, textAlign = TextAlign.Center,
                    )
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (st?.fg != null) NickButton("Bring back to home", { m.home(v.node.id) }, style = BtnStyle.GHOST, leading = Lucide.House)
                        NickButton("Find something to watch", onFind, style = BtnStyle.PRIMARY, leading = Lucide.Film)
                    }
                }
            }
        }
        val sliders: @Composable (Modifier) -> Unit = { mod ->
            Column(mod, verticalArrangement = Arrangement.spacedBy(TabletParentMetrics.cardGap)) {
                EyebrowText("On ${v.node.name}")
                DeviceSliders(v)
            }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.width(i.dp).fillMaxHeight()) {
                if (twoPane) {
                    Row(
                        Modifier.fillMaxSize().padding(top = TabletParentMetrics.contentTop, bottom = TabletParentMetrics.contentBottom),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        Box(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) { group(Modifier.fillMaxWidth()) }
                        sliders(Modifier.width(288.dp).fillMaxHeight().verticalScroll(rememberScrollState()))
                    }
                } else {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = TabletParentMetrics.contentTop, bottom = TabletParentMetrics.contentBottom),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) { group(Modifier.fillMaxWidth()); sliders(Modifier.fillMaxWidth()) }
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
    // Tablet: one centred group, at most 560 wide; it scrolls when the window is shorter than the group.
    var confirmUnlink by remember { mutableStateOf(false) }
    val seen = seenAgo(v.node.lastSeen).replaceFirstChar { it.uppercase() }.replace("Seen", "Last seen")
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = TabletParentMetrics.contentBottom, vertical = TabletParentMetrics.contentBottom), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(TabletParentMetrics.cardGap),
        ) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(C.surface), contentAlignment = Alignment.Center) {
                Icon(Lucide.WifiOff, null, tint = C.muted, modifier = Modifier.size(28.dp))
            }
            Title("${v.node.name} is offline", 44, maxLines = 4)
            Text(
                listOfNotNull(seen.ifEmpty { null }, v.node.lastBattery?.let { "battery was $it%" }).joinToString(" · "),
                style = body(14.sp, 500, 20f / 14), color = C.muted, textAlign = TextAlign.Center,
            )
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                tips.forEach { (icon, text) ->
                    val glyph = when (icon) { TipIcon.WIFI_OFF -> Lucide.WifiOff; TipIcon.WIFI -> Lucide.Wifi; TipIcon.POWER -> Lucide.Power }
                    if (reason is Reason.CarNetDown) {
                        // The same compact warning card as the Home banner: min 48, radius 12, padding 12, 14/20 text.
                        val warnShape = RoundedCornerShape(TabletParentMetrics.nestedRadius)
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(warnShape).background(C.surface).border(1.dp, C.dangerLine, warnShape).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(glyph, null, tint = C.danger, modifier = Modifier.size(TabletParentMetrics.iconSize))
                            Text(text, style = body(14.sp, 700, 20f / 14), color = C.text, modifier = Modifier.weight(1f))
                        }
                    } else Tip(glyph, text)
                }
            }
            NickButton("Try again", { m.reconnect(v.node.id) }, Modifier.fillMaxWidth(), style = BtnStyle.PRIMARY, leading = Lucide.RefreshCw)
            NickButton("All tablets", onAll, Modifier.fillMaxWidth(), style = BtnStyle.GHOST)
            // A tablet reset while this one was away never said goodbye: let the parent forget it here.
            Text("Reset or gave it away? Unlink it so it stops showing here.", style = body(14.sp, 500, 20f / 14), color = C.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            NickButton(if (confirmUnlink) "Tap again to unlink" else "Unlink tablet", {
                if (confirmUnlink) { m.unlink(v.node.id); onAll() } else confirmUnlink = true
            }, Modifier.fillMaxWidth(), style = BtnStyle.DANGER, leading = Lucide.Unlink)
        }
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
    // Tablet: 20 glyph, 15/22 text that wraps beside it.
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = C.text, modifier = Modifier.padding(top = 1.dp).size(TabletParentMetrics.iconSize))
        Text(text, style = body(15.sp, 600, 22f / 15), color = C.text, modifier = Modifier.weight(1f))
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
    // Tablet: the content cap, 16 gaps and adaptive cells of at least 128, all in one scrolling grid.
    val gridState = phoneGrid
    ParentContentCap {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(128.dp), state = gridState,
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(TabletParentMetrics.cardGap),
            verticalArrangement = Arrangement.spacedBy(TabletParentMetrics.cardGap),
            contentPadding = PaddingValues(top = TabletParentMetrics.contentTop, bottom = TabletParentMetrics.contentBottom),
        ) {
            item(key = "intro", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Tap an app to open it on ${v.node.name} now.", style = body(16.sp, 700, 22f / 16), color = C.text)
                    Text(
                        buildAnnotatedString {
                            append("Switch on ")
                            withStyle(SpanStyle(color = C.text, fontWeight = FontWeight.Bold)) { append("Home") }
                            append(" to show it on the home screen")
                            if (apps != null) append(" · ${allowed.count { a -> apps.any { it.pkg == a } }} of ${apps.size} shown")
                        },
                        style = body(14.sp, 500, 20f / 14), color = C.muted,
                    )
                }
            }
            item(key = "actions", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    NickButton("Bring back to home", { m.home(v.node.id) }, leading = Lucide.House)
                    val locked = st?.lockedApp
                    when {
                        locked != null -> NickButton("Unlock ${apps?.firstOrNull { it.pkg == locked }?.label ?: "app"}", { m.lockToApp(v.node.id, null) }, style = BtnStyle.PRIMARY, leading = Lucide.LockOpen)
                        open != null -> NickButton("Lock to ${st.fgLabel ?: "this app"}", { m.lockToApp(v.node.id, open) }, style = BtnStyle.PRIMARY, leading = Lucide.Lock)
                    }
                }
            }
            if (apps == null) {
                item(key = "loading", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { Muted("Getting the app list from ${v.node.name}…", 16) }
                return@LazyVerticalGrid
            }
            items(apps, key = { it.pkg }) { a ->
                val isOpen = a.pkg == open
                val shape = RoundedCornerShape(TabletParentMetrics.mediaRadius)
                Box(Modifier.clip(shape).background(C.surface).border(2.dp, if (isOpen) C.accent else Color.Transparent, shape)) {
                    Column {
                        Column(
                            Modifier.fillMaxWidth().heightIn(min = TabletParentMetrics.minTarget).clickable(role = Role.Button) { m.open(v.node.id, a.pkg) }
                                .padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AppTileIcon(a, Lucide.LayoutGrid, 56.dp, 16.dp, v.node.color)
                            Text(a.label, style = body(15.sp, 700, 20f / 15), color = C.text, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                        }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(C.surface2))
                        // The whole row is the target, not just the switch: a reach from the driver's seat must not open the app by mistake.
                        SwitchRow("Home", a.pkg in allowed, Modifier.padding(start = 12.dp, end = 8.dp), body(14.sp, 600, 20f / 14), C.muted) { m.allow(v.node.id, a.pkg, it) }
                    }
                    if (isOpen) Box(
                        Modifier.align(Alignment.TopEnd).padding(8.dp).height(28.dp).clip(RoundedCornerShape(8.dp)).background(C.accent).padding(horizontal = 9.dp),
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
    val fontScale = LocalDensity.current.fontScale
    // 14/20 notes on a tablet; the phone keeps its 1.3.
    val noteLine = if (phone) 1.3f else 20f / 14
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Tablet: the columns follow the capped content width; a card's inner width follows the columns (no measuring inside the rows).
        val contentI = tabletContentWidth(maxWidth.value)
        val cols = deviceColumns(contentI, fontScale)
        val innerWidth = deviceCardInnerWidth(contentI, cols)
        val cards: List<@Composable (Modifier) -> Unit> = listOf(
            { mod ->
                DeviceCard(mod, Lucide.Tablet, "Screen", rotateIcon = true) {
                    val segmented: @Composable () -> Unit = {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(if (phone) PhoneMetrics.fieldRadius else TabletParentMetrics.segmentedRadius)).background(C.surface2).padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            val on = st?.screenOn != false
                            val segH = PhoneMetrics.minTarget
                            SegBtn(Lucide.MonitorOff, "Off", !on, Modifier.weight(1f), dim = st?.screenOffOk == false, height = segH) {
                                if (st?.screenOffOk == false) m.askPermission(v.node.id) else m.screen(v.node.id, false)
                            }
                            SegBtn(Lucide.Sun, "On", on, Modifier.weight(1f), height = segH) { m.screen(v.node.id, true) }
                        }
                    }
                    val lockBtn: @Composable () -> Unit = {
                        if (st?.lockedBy != null) NickButton("Unlock tablet", { m.lock(v.node.id, false) }, Modifier.fillMaxWidth(), BtnStyle.PRIMARY, leading = Lucide.LockOpen)
                        else NickButton("Lock tablet now", { m.lock(v.node.id, true) }, Modifier.fillMaxWidth().heightIn(min = PhoneMetrics.buttonHeight), leading = Lucide.Lock)
                    }
                    // Hidden when the Node does not have the touch1 capability.
                    val touchRow: @Composable () -> Unit = { if (st?.supportsTouch == true) TouchSwitchRow(v, st.touch, st.overlay, m) }
                    // An old Node has no "ring1": no button.
                    val ringBtn: @Composable () -> Unit = { if (st?.supportsRing == true) RingButton(st.ringing) { m.ring(v.node.id, !st.ringing) } }
                    // One short line about the On/Off switch.
                    val offHint: @Composable () -> Unit = {
                        if (st?.screenOffOk == false) {
                            val asked = askedLine(v)
                            if (asked != null) Text(asked, style = body(14.sp, 700, noteLine), color = C.accent)
                            else Muted(ControlAccess.SCREEN_OFF_SHORT, 14)
                        }
                    }
                    // Two separate things. Screen view: the grown-up taps Allow on the tablet. Touch control: the accessibility switch,
                    // which some updates turn off; open that setting on the tablet in one tap (a 90 s window the parent can end).
                    val remote: @Composable () -> Unit = {
                        if (st?.supportsScreen == true) {
                            if (st.screen == RemoteMessages.NEEDS_CONSENT) Text("Screen: tap Allow on ${v.node.name}", style = body(14.sp, 700, noteLine), color = C.accent)
                            if (st.maintenance) {
                                Text("Settings open on ${v.node.name}", style = body(14.sp, 700, noteLine), color = C.danger)
                                NickButton("Lock now", { m.home(v.node.id) }, Modifier.fillMaxWidth(), BtnStyle.DANGER)
                            } else if (st.control != RemoteMessages.CONTROL_READY) {
                                Text("Touch control: off", style = body(14.sp, 700, noteLine), color = C.danger)
                                NickButton("Open setting on tablet", { m.askControl(v.node.id) }, Modifier.fillMaxWidth())
                            }
                        }
                    }
                    // The hint explains the On/Off switch, so it sits right under it. Touch is its own group below a divider
                    // (a phone and a tablet share this order; the tablet's gaps are the 12 of its card content).
                    Column(verticalArrangement = Arrangement.spacedBy(TabletParentMetrics.relatedGap)) { segmented(); offHint() }
                    lockBtn()
                    ringBtn()
                    // remote() draws nothing when the screen is allowed and touch control is ready, so match that here.
                    val remoteShows = st?.supportsScreen == true && (st.screen == RemoteMessages.NEEDS_CONSENT || st.maintenance || st.control != RemoteMessages.CONTROL_READY)
                    if (st?.supportsTouch == true || remoteShows) Box(Modifier.fillMaxWidth().height(1.dp).background(C.surface2))
                    touchRow()
                    remote()
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
                    // Four equal rounded blocks (radius 12, gap 8, at least 64 high): number over "min". One row at 280+ of inner
                    // width and font scale under 1.5, else 2x2. Tapping sends at once, as before.
                    val pick: @Composable RowScope.(Int) -> Unit = { min ->
                        val sel = at > 0 && chosen == min
                        val bg by animateColorAsState(if (sel) C.accent else C.surface2, tween(120), label = "bedtimeBg")
                        val fg = if (sel) C.onAccent else C.text
                        Column(
                            Modifier.weight(1f).fillMaxHeight().heightIn(min = TabletParentMetrics.subButtonHeight).clip(RoundedCornerShape(TabletParentMetrics.nestedRadius)).background(bg)
                                .selectable(selected = sel, role = Role.RadioButton) { chosen = BedtimeCommands.pick(min) { m.bedtime(v.node.id, it) } }
                                .semantics { contentDescription = "$min minutes" }.padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                        ) {
                            Text("$min", style = body(20.sp, 700, 22f / 20).copy(fontFeatureSettings = "tnum"), color = fg)
                            Text("min", style = body(12.sp, 600, 16f / 12), color = fg.copy(alpha = 0.8f))
                        }
                    }
                    if (bedtimeOneRow(innerWidth, fontScale)) {
                        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) { BedtimeCommands.choices.forEach { pick(it) } }
                    } else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        BedtimeCommands.choices.chunked(2).forEach { pair -> Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) { pair.forEach { pick(it) } } }
                    }
                    if (at > 0) {
                        val cancel: @Composable (Modifier) -> Unit = { mod -> NickButton("Cancel", { chosen = null; BedtimeCommands.cancel { m.bedtime(v.node.id, it) } }, mod, BtnStyle.GHOST) }
                        val remaining: @Composable (Modifier) -> Unit = { mod ->
                            Column(mod) {
                                Text("SCREEN TURNS OFF IN", style = body(12.sp, 700, 16f / 12), color = C.muted)
                                Text("$left min", style = body(24.sp, 800, 28f / 24).copy(fontFeatureSettings = "tnum"), color = C.text)
                            }
                        }
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(TabletParentMetrics.nestedRadius)).background(C.surface2).padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (bedtimeCancelBelow(innerWidth)) { remaining(Modifier.fillMaxWidth()); cancel(Modifier.fillMaxWidth()) }
                            else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { remaining(Modifier.weight(1f)); cancel(Modifier) }
                        }
                    } else Text("Pick a time. The tablet warns 5 minutes before, then turns its screen off.", style = body(14.sp, 500, 20f / 14), color = C.muted)
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
                    // A passive badge: 28 high, 12sp.
                    if (st != null && !st.owner) Chip(height = if (phone) 30.dp else 28.dp, bg = C.dangerLine) { Text("Kid lock off", style = body(if (phone) 13.sp else 12.sp, 700), color = C.danger) }
                }) {
                    val unlink: @Composable (Dp) -> Unit = { h ->
                        StartButton(Lucide.Unlink, if (confirmUnlink) "Tap again to unlink" else "Unlink tablet", danger = true, height = h) {
                            if (confirmUnlink) { m.unlink(v.node.id); onUnlinked() } else confirmUnlink = true
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(if (phone) 10.dp else 8.dp)) {
                        StartButton(Lucide.RefreshCw, "Restart Jellyfin") { m.restartJellyfin(v.node.id) }
                        StartButton(Lucide.Pencil, "Rename tablet") { renaming = true }
                        unlink(PhoneMetrics.minTarget)
                        // An old Node does not report it, so there is nothing to switch.
                        st?.landscape?.let { l ->
                            SwitchRow("Lock to landscape", l.lock) { m.setLandscape(v.node.id, it) }
                            if (l.lock && l.needsPermission) Text(LandscapePolicy.NEEDS_PERMISSION, style = body(14.sp, 700, noteLine), color = C.danger)
                            if (l.pending) Text(if (l.lock) LandscapePolicy.PENDING_ON else LandscapePolicy.PENDING_OFF, style = body(14.sp, 700, noteLine), color = C.danger)
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
        // Tablet: masonry. One scrolling page holding equal-width independent columns (16 apart, 16 between cards), each card as tall as
        // its own content: no row minimum, no stretching. Cards are Screen 0, Bedtime 1, Battery 2, Volume 3, Brightness 4, Tablet 5.
        val columns: List<List<Int>> = when (cols) {
            3 -> listOf(listOf(0, 3), listOf(1, 4), listOf(2, 5))
            2 -> listOf(listOf(0, 2, 4), listOf(1, 3, 5))
            else -> listOf(listOf(0, 1, 2, 3, 4, 5))
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll ?: ownScroll).padding(top = TabletParentMetrics.contentTop, bottom = TabletParentMetrics.contentBottom),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.width(contentI.dp), horizontalArrangement = Arrangement.spacedBy(TabletParentMetrics.cardGap)) {
                columns.forEach { idx ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TabletParentMetrics.cardGap)) {
                        idx.forEach { cards[it](Modifier.fillMaxWidth()) }
                    }
                }
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
    // Tablet: the row is at least 48 (label 16), its 14/20 helper sits right under it, the Allow action is 48.
    Column(verticalArrangement = Arrangement.spacedBy(TabletParentMetrics.labelGap)) {
        SwitchRow("Touch", on) { change(it) }
        val status = TouchText.status(touch, v.node.name)
        Text(status ?: TouchText.HINT, style = body(14.sp, 700, 20f / 14), color = if (status != null && status != TouchText.IS_OFF) C.danger else C.muted)
        if (TouchText.offerAllow(touch)) StartButton(Lucide.Settings, "Allow on ${v.node.name}", height = PhoneMetrics.minTarget) { m.askPermission(v.node.id) }
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
    if (!phone) {
        // Tablet: without power controls a 48 hero, a 6-high track 12 below and the 14 charging line 8 below; with them a 36 hero beside the
        // flexible progress and status column, then 16 before the wake row. Every line wraps, nothing is cut.
        val track = if (low) C.danger else C.online
        val status = if (charging) "Charging" else "Not charging"
        if (power == null) {
            Column {
                Text("$battery%", style = disp(48.sp, 800, 1f).copy(fontFeatureSettings = "tnum"), color = if (low) C.danger else C.text)
                Spacer(Modifier.height(12.dp))
                ProgressBar(battery / 100f, height = 6.dp, fill = track)
                Spacer(Modifier.height(8.dp))
                Text(status, style = body(14.sp, 600, 20f / 14), color = C.muted)
            }
        } else Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("$battery%", style = disp(36.sp, 800, 1f).copy(fontFeatureSettings = "tnum"), color = if (low) C.danger else C.text)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProgressBar(battery / 100f, height = 6.dp, fill = track)
                    Text(status, style = body(14.sp, 600, 20f / 14), color = C.muted)
                }
            }
            PowerControls(power.wake, power.offMin, if (power.needsPermission) PowerPolicy.NEEDS_PERMISSION_TABLET else null, onPower)
        }
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
    // Tablet: radius 20, padding 20, content 12 apart; the card grows with its content. The heading row wraps a trailing badge onto its own line.
    Column(
        modifier.clip(RoundedCornerShape(TabletParentMetrics.cardRadius)).background(C.surface)
            .then(if (animateSize) Modifier.animateContentSize(tween(180)) else Modifier).padding(TabletParentMetrics.cardPadding),
        verticalArrangement = Arrangement.spacedBy(TabletParentMetrics.relatedGap),
    ) {
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(
            Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(icon, null, tint = C.text, modifier = Modifier.size(TabletParentMetrics.iconSize).then(if (rotateIcon) Modifier.rotate90() else Modifier))
                Text(title, style = body(18.sp, 800, 24f / 18), color = C.text)
            }
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
    // Tablet: a segment is at least 48 high with radius 12, a 20 glyph and a 16 label; colours fade over 120ms.
    val bg by animateColorAsState(if (on) C.accent else Color.Transparent, tween(120), label = "segBtnBg")
    val col by animateColorAsState(if (on) C.onAccent else C.muted, tween(120), label = "segBtnFg")
    Row(
        modifier.heightIn(min = height).clip(RoundedCornerShape(TabletParentMetrics.segmentedSelectedRadius)).background(bg)
            .alpha(if (enabled && !dim) 1f else 0.4f).selectable(selected = on, enabled = enabled, role = Role.RadioButton, onClick = onClick).padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = col, modifier = Modifier.size(TabletParentMetrics.iconSize))
        Text(label, style = body(16.sp, 700, 1.25f), color = col)
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
    // Tablet: at least 48 high (grows for a long label), radius 14, a 20 glyph, a 16 label; danger is a 1dp outline.
    val tShape = RoundedCornerShape(TabletParentMetrics.buttonRadius)
    val col = if (danger) C.danger else C.text
    Row(
        modifier.fillMaxWidth().heightIn(min = if (height == 60.dp) TabletParentMetrics.minTarget else maxOf(height, TabletParentMetrics.minTarget)).clip(tShape)
            .then(if (danger) Modifier.border(1.dp, C.dangerLine, tShape) else Modifier.background(C.surface2))
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        Icon(icon, null, tint = col, modifier = Modifier.size(TabletParentMetrics.iconSize))
        Text(label, style = body(16.sp, 700, 1.25f), color = col, modifier = Modifier.weight(1f, fill = false), textAlign = TextAlign.Center)
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
        if (LocalParentTablet.current && !LocalPhone.current) {
            // Tablet: at most 480 wide with 24 clear all round, padding 24, radius 24; scrolls on a short window and wraps its actions.
            Column(
                Modifier.widthIn(max = 480.dp + 48.dp).fillMaxWidth().padding(24.dp).heightIn(max = 560.dp).clip(RoundedCornerShape(TabletParentMetrics.dialogRadius)).background(C.surface).tap {}
                    .verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(TabletParentMetrics.sectionGap),
            ) {
                Title(title, 36)
                NickField(name, { name = it.take(28) })
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    NickButton("Cancel", { onDone(null) }, style = BtnStyle.GHOST)
                    NickButton("Save", { onDone(name.trim()) }, style = BtnStyle.PRIMARY, enabled = name.isNotBlank())
                }
            }
            return@Box
        }
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
