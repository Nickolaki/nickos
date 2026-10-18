package com.nbaltas.nickos.ui.master

import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Camera
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Contrast
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.KeyRound
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Server
import com.composables.icons.lucide.Tablet
import com.composables.icons.lucide.Unlink
import com.composables.icons.lucide.Wifi
import com.composables.icons.lucide.WifiOff
import com.nbaltas.nickos.App
import com.nbaltas.nickos.BuildConfig
import com.nbaltas.nickos.EntrySwitch
import com.nbaltas.nickos.homeRequestIntent
import com.nbaltas.nickos.homeStateLine
import com.nbaltas.nickos.isDefaultHome
import com.nbaltas.nickos.cam.CamPrefs
import com.nbaltas.nickos.data.Hotspot
import com.nbaltas.nickos.data.LinkedNode
import com.nbaltas.nickos.master.CarNetState
import com.nbaltas.nickos.master.NodeNet
import com.nbaltas.nickos.master.effectiveCarNetState
import com.nbaltas.nickos.master.OnlinePolicy
import com.nbaltas.nickos.master.Reason
import com.nbaltas.nickos.master.Why
import com.nbaltas.nickos.jellyfin.JfUser
import com.nbaltas.nickos.power.PowerPolicy
import com.nbaltas.nickos.update.UpdateState
import com.nbaltas.nickos.update.Updater
import kotlinx.coroutines.launch
import com.nbaltas.nickos.ui.setup.JellyfinForm
import com.nbaltas.nickos.ui.setup.rotate90
import com.nbaltas.nickos.ui.theme.Avatar
import com.nbaltas.nickos.ui.theme.selfNoun
import com.nbaltas.nickos.ui.theme.selfWords
import com.nbaltas.nickos.ui.theme.BackButton
import com.nbaltas.nickos.ui.theme.BtnStyle
import com.nbaltas.nickos.ui.theme.C
import com.nbaltas.nickos.ui.theme.Chip
import com.nbaltas.nickos.ui.theme.Dot
import com.nbaltas.nickos.ui.theme.DarkColors
import com.nbaltas.nickos.ui.theme.EyebrowText
import com.nbaltas.nickos.ui.theme.FieldLabel
import com.nbaltas.nickos.ui.theme.NickField
import com.nbaltas.nickos.ui.theme.Keypad
import com.nbaltas.nickos.ui.theme.LightColors
import com.nbaltas.nickos.ui.theme.Muted
import com.nbaltas.nickos.ui.theme.IconCircle
import com.nbaltas.nickos.ui.theme.LocalPhone
import com.nbaltas.nickos.ui.theme.PhoneHeader
import com.nbaltas.nickos.ui.theme.PhoneMetrics
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalDensity
import com.nbaltas.nickos.ui.theme.LocalParentTablet
import com.nbaltas.nickos.ui.theme.TabletParentMetrics
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.nbaltas.nickos.ui.theme.NickButton
import com.nbaltas.nickos.ui.theme.NickLogo
import com.nbaltas.nickos.ui.theme.NickSwitch
import com.nbaltas.nickos.ui.theme.PinDots
import com.nbaltas.nickos.ui.theme.Segmented
import com.nbaltas.nickos.ui.theme.Title
import com.nbaltas.nickos.ui.theme.body
import com.nbaltas.nickos.ui.theme.dashed
import com.nbaltas.nickos.ui.theme.disp
import com.nbaltas.nickos.ui.theme.tap

private val sections = listOf<Pair<String, ImageVector>>(
    "Linked tablets" to Lucide.Tablet, "Jellyfin" to Lucide.Server, "Cameras" to Lucide.Camera,
    "Parent PIN" to Lucide.KeyRound, "Theme" to Lucide.Contrast, "About" to Lucide.Info,
)

/** Phone: what Back in a section page does (return to the list). Null on tablets, where the sidebar is always there. */
private val LocalSectionBack = staticCompositionLocalOf<(() -> Unit)?> { null }

private fun sectionPage(i: Int, onAdd: () -> Unit): @Composable () -> Unit = {
    when (i) {
        0 -> TabletsSection(onAdd)
        1 -> JellyfinSection()
        2 -> CamerasSection()
        3 -> PinSection()
        4 -> ThemeSection()
        else -> AboutSection()
    }
}

/**
 * [section] is the section to open. On a tablet null means the first one. On a phone null means the settings list,
 * and a number opens that page directly; Back then returns to the list, and Back from the list calls [onBack].
 */
@Composable
fun SettingsScreen(section: Int?, onSection: (Int) -> Unit, onBack: () -> Unit, onAdd: () -> Unit) {
    if (LocalPhone.current) {
        PhoneSettings(section, onBack, onAdd)
        return
    }
    val current = section ?: 0
    if (LocalParentTablet.current) {
        TabletSettings(current, onSection, onBack, onAdd)
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val panelW = if (maxWidth < 1000.dp) 250.dp else 320.dp
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.width(panelW).fillMaxHeight().background(C.panel).padding(horizontal = 20.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.padding(bottom = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    BackButton(onBack)
                    Title("Settings", 34, maxLines = 1)
                }
                sections.forEachIndexed { i, (label, icon) ->
                    val on = i == current
                    Row(
                        Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(18.dp)).background(if (on) C.surface2 else Color.Transparent)
                            .clickable { onSection(i) }.padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Icon(icon, null, tint = if (on) C.accent else C.muted, modifier = Modifier.size(22.dp).then(if (i == 0) Modifier.rotate90() else Modifier))
                        Text(label, style = body(19.sp, 700), color = if (on) C.text else C.muted, maxLines = 1)
                    }
                }
                Spacer(Modifier.weight(1f))
                Text("NickOS ${BuildConfig.VERSION_NAME}", style = body(15.sp), color = C.muted, modifier = Modifier.padding(horizontal = 18.dp))
            }
            Box(Modifier.weight(1f).fillMaxHeight()) { sectionPage(current, onAdd)() }
        }
    }
}

/** The category strip replaces the rail below 840 of width and whenever the font is 1.5 or larger. */
internal fun settingsUsesStrip(widthDp: Float, fontScale: Float): Boolean = widthDp < 840f || fontScale >= 1.5f

private val SETTINGS_MAIN_MAX = 864.dp

/** The parent tablet shell (design §2.6): a 208 / 176 rail, or a category strip below 840 and at font 1.5+. The section is the caller's, so it survives the switch. */
@Composable
private fun TabletSettings(current: Int, onSection: (Int) -> Unit, onBack: () -> Unit, onAdd: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val strip = settingsUsesStrip(maxWidth.value, LocalDensity.current.fontScale)
        // The page puts itself in the 24 inset and the 864 cap, so a scrim a page draws still covers the whole pane.
        val main: @Composable () -> Unit = { Box(Modifier.fillMaxSize()) { sectionPage(current, onAdd)() } }
        if (strip) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BackButton(onBack)
                    Title("Settings", 34, maxLines = 1)
                }
                val requesters = remember { List(sections.size) { BringIntoViewRequester() } }
                LaunchedEffect(current) { requesters[current].bringIntoView() }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    sections.forEachIndexed { i, (label, icon) ->
                        val on = i == current
                        Row(
                            Modifier.bringIntoViewRequester(requesters[i]).heightIn(min = TabletParentMetrics.minTarget).clip(RoundedCornerShape(TabletParentMetrics.buttonRadius))
                                .background(if (on) C.surface2 else Color.Transparent)
                                .selectable(selected = on, role = androidx.compose.ui.semantics.Role.Tab) { onSection(i) }.padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(icon, null, tint = if (on) C.accent else C.muted, modifier = Modifier.size(TabletParentMetrics.iconSize).then(if (i == 0) Modifier.rotate90() else Modifier))
                            Text(label, style = body(16.sp, 700, 1.25f), color = if (on) C.text else C.muted, maxLines = 1, softWrap = false)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Box(Modifier.weight(1f).fillMaxWidth()) { main() }
            }
        } else {
            val wide = maxWidth.value >= 960f
            val labelSp = if (wide) 16.sp else 15.sp
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.width(if (wide) 208.dp else 176.dp).fillMaxHeight().background(C.panel).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        BackButton(onBack)
                        Title("Settings", 34, maxLines = 1)
                    }
                    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        sections.forEachIndexed { i, (label, icon) ->
                            val on = i == current
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = TabletParentMetrics.minTarget).clip(RoundedCornerShape(TabletParentMetrics.buttonRadius))
                                    .background(if (on) C.surface2 else Color.Transparent)
                                    .selectable(selected = on, role = androidx.compose.ui.semantics.Role.Tab) { onSection(i) }.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(icon, null, tint = if (on) C.accent else C.muted, modifier = Modifier.size(TabletParentMetrics.iconSize).then(if (i == 0) Modifier.rotate90() else Modifier))
                                Text(label, style = body(labelSp, 700, 1.25f), color = if (on) C.text else C.muted, maxLines = 2)
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text("NickOS ${BuildConfig.VERSION_NAME}", style = body(14.sp, 500, 20f / 14), color = C.muted, modifier = Modifier.padding(horizontal = 10.dp))
                }
                Box(Modifier.weight(1f).fillMaxHeight()) { main() }
            }
        }
    }
}

private val phoneSummaries = listOf(
    "Add, rename, unlink; the car network", "Optional movie library", "Picture quality and automatic off",
    "Change the PIN that leaves kid mode", "Dark, light or match the system", "Version, updates and reset",
)

/** The phone settings: a single list of sections, each opening a full page with its own header. */
@Composable
private fun PhoneSettings(direct: Int?, onBack: () -> Unit, onAdd: () -> Unit) {
    val app = App.get(LocalContext.current)
    // Survives rotation. The direct argument is only the starting point.
    var open by rememberSaveable(direct) { mutableStateOf(direct) }
    BackHandler { if (open != null) open = null else onBack() }
    val sel = open
    if (sel != null) {
        CompositionLocalProvider(LocalSectionBack provides { open = null }) { sectionPage(sel, onAdd)() }
        return
    }
    val nodes by app.store.nodes.collectAsState()
    val jf by app.store.jellyfin.collectAsState()
    val theme by app.store.theme.collectAsState()
    val update by Updater.state.collectAsState()
    Column(Modifier.fillMaxSize()) {
        PhoneHeader("Settings", onBack)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = PhoneMetrics.gutter),
            verticalArrangement = Arrangement.spacedBy(PhoneMetrics.relatedGap),
        ) {
            sections.forEachIndexed { i, (label, icon) ->
                val summary = when (i) {
                    0 -> if (nodes.isEmpty()) "No tablets linked yet" else "${nodes.size} linked · ${phoneSummaries[0]}"
                    1 -> if (jf != null) "Connected" else "Not connected · ${phoneSummaries[1]}"
                    4 -> when (theme) { "LIGHT" -> "Light"; "SYSTEM" -> "Match system"; else -> "Dark" }
                    5 -> if (update is UpdateState.Available) "Update ready · version ${BuildConfig.VERSION_NAME}" else "Version ${BuildConfig.VERSION_NAME} · ${phoneSummaries[5]}"
                    else -> phoneSummaries[i]
                }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(PhoneMetrics.cardRadius)).background(C.surface)
                        .clickable(role = androidx.compose.ui.semantics.Role.Button) { open = i }.padding(horizontal = PhoneMetrics.cardPadding, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(icon, null, tint = C.accent, modifier = Modifier.size(24.dp).then(if (i == 0) Modifier.rotate90() else Modifier))
                    Column(Modifier.weight(1f)) {
                        Text(label, style = body(18.sp, 700, 1.25f), color = C.text)
                        Text(summary, style = body(14.sp, 500, 1.3f), color = C.muted)
                    }
                    Icon(Lucide.ChevronRight, null, tint = C.muted, modifier = Modifier.size(20.dp))
                }
            }
            Text("NickOS ${BuildConfig.VERSION_NAME}", style = body(14.sp), color = C.muted, modifier = Modifier.padding(vertical = PhoneMetrics.cardPadding))
        }
    }
}

/** A card body. Tablets keep their own radius and padding; phones get 20dp radius and 16dp padding. */
@Composable
private fun Modifier.panel(radius: Dp = 28.dp, h: Dp = 28.dp, v: Dp = 28.dp): Modifier =
    if (LocalPhone.current) clip(RoundedCornerShape(PhoneMetrics.cardRadius)).background(C.surface).padding(PhoneMetrics.cardPadding)
    else if (LocalParentTablet.current) clip(RoundedCornerShape(TabletParentMetrics.cardRadius)).background(C.surface).padding(TabletParentMetrics.cardPadding)
    else clip(RoundedCornerShape(radius)).background(C.surface).padding(horizontal = h, vertical = v)

/** Phone buttons are full width and grow with the font; tablet buttons keep their fixed height. */
@Composable
private fun btnMod(): Modifier = if (LocalPhone.current) Modifier.fillMaxWidth() else if (LocalParentTablet.current) Modifier else Modifier.height(52.dp)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SectionPage(title: String, action: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
    if (LocalPhone.current) {
        Column(Modifier.fillMaxSize()) {
            PhoneHeader(title, LocalSectionBack.current)
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(horizontal = PhoneMetrics.gutter, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(PhoneMetrics.cardPadding),
            ) {
                action()
                content()
            }
        }
        return
    }
    if (LocalParentTablet.current) {
        // Heading 30 and the action (52) in one wrapping row, 16 between panels; the whole page scrolls above the keyboard.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.fillMaxHeight().widthIn(max = SETTINGS_MAIN_MAX + 48.dp).fillMaxWidth().verticalScroll(rememberScrollState()).imePadding()
                    .padding(start = 24.dp, end = 24.dp, top = TabletParentMetrics.contentTop, bottom = TabletParentMetrics.contentBottom),
                verticalArrangement = Arrangement.spacedBy(TabletParentMetrics.cardGap),
            ) {
                FlowRow(
                    Modifier.fillMaxWidth().heightIn(min = TabletParentMetrics.primaryButtonHeight),
                    horizontalArrangement = Arrangement.spacedBy(TabletParentMetrics.cardGap), verticalArrangement = Arrangement.spacedBy(TabletParentMetrics.relatedGap),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Title(title, 46, Modifier.weight(1f, fill = false), maxLines = 2)
                    action()
                }
                content()
            }
        }
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 48.dp, vertical = 40.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Title(title, 46, Modifier.weight(1f), maxLines = 1)
            action()
        }
        content()
    }
}

// ---------------- Linked tablets ----------------

@Composable
private fun TabletsSection(onAdd: () -> Unit) {
    val app = App.get(LocalContext.current)
    val views = rememberNodeViews()
    val name by app.store.name.collectAsState()
    val net by app.master.net.collectAsState()
    val hotspot by app.store.hotspot.collectAsState()
    var renaming by remember { mutableStateOf<String?>(null) } // node id, or "" for this tablet
    var confirmUnlink by remember { mutableStateOf<String?>(null) }
    val phone = LocalPhone.current
    val tab = !phone && LocalParentTablet.current
    SectionPage("Linked tablets", { NickButton("Add tablet", onAdd, if (phone) Modifier.fillMaxWidth() else Modifier, style = BtnStyle.PRIMARY, leading = Lucide.Plus) }) {
        Column(
            if (phone) Modifier.fillMaxWidth().border(2.dp, C.line, RoundedCornerShape(PhoneMetrics.cardRadius)).padding(PhoneMetrics.cardPadding)
            else if (tab) Modifier.fillMaxWidth().border(1.dp, C.line, RoundedCornerShape(TabletParentMetrics.cardRadius)).padding(TabletParentMetrics.cardPadding)
            else Modifier.fillMaxWidth().border(2.dp, C.line, RoundedCornerShape(24.dp)).padding(horizontal = 22.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(if (phone) 12.dp else 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (phone) 12.dp else 16.dp)) {
                Box(Modifier.size(if (phone) 32.dp else if (tab) 44.dp else 60.dp).clip(CircleShape).background(C.accent), contentAlignment = Alignment.Center) {
                    Icon(Lucide.Tablet, null, tint = C.onAccent, modifier = Modifier.size(if (phone) 18.dp else if (tab) 22.dp else 26.dp).rotate90())
                }
                Column(Modifier.weight(1f)) {
                    Text(name.ifBlank { "Parent's Tablet" }, style = body(if (phone || tab) 18.sp else 20.sp, 800, if (phone) 1.2f else 1.3f), color = C.text)
                    Muted(if (phone) "This ${selfNoun(phone)} · Parent" else "This tablet · Master", if (tab) 14 else 16)
                }
                if (phone) IconCircle(Lucide.Pencil, { renaming = "" }, label = "Rename this ${selfNoun(phone)}")
                else NickButton("Rename", { renaming = "" }, if (tab) Modifier else Modifier.height(52.dp), leading = Lucide.Pencil)
            }
            val ctx = LocalContext.current
            val wake by app.store.powerWakeOnCharge.collectAsState()
            val offMin by app.store.powerBatteryOffMin.collectAsState()
            // Same check as the tablet's own port, so a Device Owner counts as able. Read again on every resume: coming back from
            // the permission page applies the parent's choice, retries a pending restore and clears the red line.
            var canWrite by remember { mutableStateOf(com.nbaltas.nickos.power.AndroidScreenTimeoutPort.canChange(ctx)) }
            val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            androidx.compose.runtime.DisposableEffect(owner) {
                val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
                    if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        canWrite = com.nbaltas.nickos.power.AndroidScreenTimeoutPort.canChange(ctx)
                        app.master.refreshOwnPower()
                    }
                }
                owner.lifecycle.addObserver(obs)
                onDispose { owner.lifecycle.removeObserver(obs) }
            }
            val power: @Composable (Float) -> Unit = { innerWidth ->
                PowerControls(wake, offMin, if (offMin > 0 && !canWrite) PowerPolicy.NEEDS_PERMISSION else null, { w, m ->
                    // Only picking a time opens the permission page, never the wake switch.
                    if (m > 0 && m != offMin && !canWrite) {
                        try {
                            ctx.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS, android.net.Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (_: Exception) {}
                    }
                    app.master.setOwnPower(w, m)
                }, innerWidthDp = innerWidth)
            }
            // The tablet card measures its inner width (after the card padding) so the time choices can go six across; the phone keeps its own layout.
            if (phone) power(0f) else BoxWithConstraints(Modifier.fillMaxWidth()) { power(maxWidth.value) }
        }
        CarNetCard()
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            views.forEach { v ->
                val buttons: @Composable RowScope.() -> Unit = {
                    val bm = if (phone) Modifier.weight(1f) else if (tab) Modifier else Modifier.height(52.dp)
                    NickButton("Rename", { renaming = v.node.id }, bm, leading = Lucide.Pencil)
                    NickButton(if (confirmUnlink == v.node.id) "Sure?" else "Unlink", {
                        if (confirmUnlink == v.node.id) { app.master.unlink(v.node.id); confirmUnlink = null } else confirmUnlink = v.node.id
                    }, bm, BtnStyle.DANGER, leading = Lucide.Unlink)
                }
                // Portrait tablets/phones: the buttons go on their own line, or they squeeze the name to nothing.
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    // Parent tablet: the actions drop under the identity when the row is under 640 inside its padding, or at a large font.
                    val narrow = if (tab) maxWidth < 680.dp || LocalDensity.current.fontScale >= 1.5f else maxWidth < 680.dp
                    Column(
                        // Design: min-height 96 on the content box, plus 16 padding top and bottom.
                        if (phone) Modifier.fillMaxWidth().clip(RoundedCornerShape(PhoneMetrics.cardRadius)).background(C.surface).padding(PhoneMetrics.cardPadding)
                        else if (tab) Modifier.fillMaxWidth().heightIn(min = 104.dp).clip(RoundedCornerShape(TabletParentMetrics.cardRadius)).background(C.surface).padding(TabletParentMetrics.cardPadding)
                        else Modifier.fillMaxWidth().heightIn(min = 128.dp).clip(RoundedCornerShape(24.dp)).background(C.surface).padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(if (phone) 12.dp else if (tab) TabletParentMetrics.relatedGap else 14.dp, Alignment.CenterVertically),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (phone || tab) 12.dp else 18.dp)) {
                            Avatar(v.node.name, v.node.color, v.node.avatar, if (phone || tab) 40.dp else 60.dp)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(v.node.name, style = body(if (phone || tab) 18.sp else 22.sp, 800, if (phone) 1.2f else 1.3f), color = C.text, maxLines = if (phone || tab) 2 else 1)
                                StatusLine(v, if (phone) 14 else if (tab) 14 else 16, 10.dp, if (v.online) "Online" else statusText(v))
                                Muted(tabletNetLine(v.online, net[v.node.id], v.node.wifiSaved, hotspot), 15)
                            }
                            if (phone) Text(v.battery?.let { "$it%" } ?: "", style = body(14.sp, 700), color = if (v.online) C.text else C.muted, maxLines = 1, softWrap = false)
                            else if (tab) Text(v.battery?.let { "$it%" } ?: "", style = disp(24.sp), color = if (v.online) C.text else C.muted, maxLines = 1, softWrap = false)
                            else Text(v.battery?.let { "$it%" } ?: "", style = disp(28.sp), color = if (v.online) C.text else C.muted, modifier = Modifier.width(90.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                            if (!narrow) buttons()
                        }
                        if (narrow) Row(if (phone) Modifier.fillMaxWidth() else Modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) { buttons() }
                    }
                }
            }
            if (views.isEmpty()) Muted("No tablets linked yet.", 18)
        }
    }
    renaming?.let { id ->
        if (id.isEmpty()) RenameDialog(name.ifBlank { "Parent's Tablet" }, "Rename this ${selfNoun()}") { n -> renaming = null; if (n != null) app.store.setName(n) }
        else views.firstOrNull { it.node.id == id }?.let { v -> RenameDialog(v.node.name) { n -> renaming = null; if (n != null) app.master.rename(id, n) } }
    }
}

// ---------------- Car network ----------------

/** What a button on the car network card does. */
internal enum class CarAction { ALLOW, OPEN_LOCATION, DIFFERENT, USE_CAR_NET }

internal data class CarButton(val label: String, val action: CarAction)

/** Everything the card says for one state: title, one line, an optional primary and ghost button, and whether the manual fields show. */
internal data class CardCopy(val title: String, val line: String?, val primary: CarButton? = null, val ghost: CarButton? = null, val fields: Boolean = false)

/** The card's words by state. [manual] is the hotspot the parent typed in, if any; [sdk] is this tablet's Android version. */
internal fun carNetCopy(state: CarNetState, manual: Hotspot?, sdk: Int): CardCopy = when (state) {
    is CarNetState.Starting -> CardCopy("Starting the car network", null)
    is CarNetState.On -> CardCopy(
        state.ssid, "On. The tablets join it on their own. There is nothing to switch on in the car.",
        ghost = CarButton("Use a different hotspot", CarAction.DIFFERENT),
    )
    is CarNetState.Off -> when (state.why) {
        Why.NO_PERMISSION -> CardCopy(
            "Car network is off", "NickOS needs permission to run it from this tablet. It never uses your location.",
            primary = CarButton("Allow", CarAction.ALLOW),
        )
        Why.NO_LOCATION -> CardCopy(
            "Car network is off", "Android needs Location switched on for this tablet to run the network. NickOS does not read where you are.",
            primary = CarButton("Open location settings", CarAction.OPEN_LOCATION),
        )
        Why.WIFI_OFF -> CardCopy("Car network is off", "WiFi is switched off on this tablet. Turn it on. It does not need to join anything.")
        Why.TETHERING_ON -> CardCopy("Car network is paused", "This tablet's own hotspot is on. The car network comes back when the hotspot is off.")
        Why.FAILED, Why.UNSUPPORTED -> CardCopy(
            "Car network can't run on this tablet",
            "Use a phone or tablet hotspot instead. Enter its name and password here and the tablets will join that.",
            fields = true,
        )
        Why.TURNED_OFF -> CardCopy(
            "Car network is off", "Turn it on above when you want it back. Until then the tablets use your home WiFi or a hotspot.",
            ghost = CarButton("Use a different hotspot", CarAction.DIFFERENT), fields = manual != null,
        )
        Why.MANUAL -> CardCopy(
            manual?.ssid ?: "Hotspot", "The tablets join this hotspot when it is on. Switch it on each trip.",
            ghost = if (sdk >= 29) CarButton("Use the car network instead", CarAction.USE_CAR_NET) else null,
        )
    }
}

/** True when the typed hotspot can be sent: a name of at most 32 bytes and a password that is empty or 8 to 63 long. */
internal fun hotspotFieldsOk(name: String, pass: String): Boolean =
    name.isNotBlank() && name.toByteArray().size <= 32 && (pass.isEmpty() || pass.length in 8..63)

internal const val HOTSPOT_SAVE_NOTE = "Saved to every tablet the next time it is online."

/** The line under a tablet's status on Settings, Tablets. [net] is its last live report, used only while it is [online]. */
internal fun tabletNetLine(online: Boolean, net: NodeNet?, persistedSaved: Boolean?, hotspot: Hotspot?): String {
    val live = if (online) net else null
    val saved = OnlinePolicy.wifiSaved(live, persistedSaved) == true
    val manual = hotspot?.direct == false
    val base = when {
        live != null && live.supportsWifi && live.wifi.approve && !live.wifi.deviceOwner -> "Needs one tap on the tablet: Allow in the NickOS notification"
        saved -> if (manual) "Hotspot saved" else "Car network saved"
        hotspot != null -> "Hasn't got the car network yet. Link it on the home WiFi once."
        else -> "Nothing to send yet."
    }
    return if (live != null && live.supportsWifi && !live.wifi.doze) "$base · Battery saver can delay rejoining" else base
}

/** The reason as it reads in the banner and on the offline page. */
internal fun carNetWhy(why: Why): String = when (why) {
    Why.NO_PERMISSION -> "it needs permission"
    Why.NO_LOCATION -> "Location is off on this tablet"
    Why.WIFI_OFF -> "WiFi is off on this tablet"
    Why.TETHERING_ON -> "your hotspot is on"
    Why.FAILED, Why.UNSUPPORTED -> "it can't run on this tablet"
    Why.MANUAL -> "you are using your own hotspot"
    Why.TURNED_OFF -> "you switched it off"
}

/** The banner never nags about a switch the parent chose. */
internal fun carNetBannerWhy(reasons: Collection<Reason>): Why? = carNetDownWhy(reasons)?.takeIf { it != Why.TURNED_OFF }

internal fun carNetBanner(why: Why): String = "Car network is off: ${carNetWhy(why)}. Tap to fix."

/** The car network reason among the offline tablets' reasons, or null. The home banner shows only when there is one. */
internal fun carNetDownWhy(reasons: Collection<Reason>): Why? = reasons.filterIsInstance<Reason.CarNetDown>().firstOrNull()?.why

internal enum class TipIcon { WIFI_OFF, WIFI, POWER }

/** The tips on a tablet's offline page. [manualSsid] is set when the parent typed a hotspot in. */
internal fun offlineTips(reason: Reason?, manualSsid: String?): List<Pair<TipIcon, String>> = when (reason) {
    is Reason.CarNetDown -> if (reason.why == Why.TURNED_OFF) listOf(TipIcon.WIFI_OFF to "The car network is switched off. Turn it on in Settings, Tablets.") else listOf(TipIcon.WIFI_OFF to "The car network is off: ${carNetWhy(reason.why)}. Fix it in Settings, Tablets.")
    is Reason.MasterNotOnWifi -> listOf(TipIcon.WIFI to "Switch on the hotspot ${manualSsid ?: ""} and it will join.")
    is Reason.NoHotspotDetails -> listOf(TipIcon.WIFI to "It hasn't got the car network yet. Link it on the home WiFi once.")
    else -> listOf(
        TipIcon.POWER to "Make sure it's switched on and charged.",
        TipIcon.WIFI to if (manualSsid != null) "It joins $manualSsid on its own once the hotspot is on." else "It joins the car network on its own when it's in range.",
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CarNetCard() {
    val ctx = LocalContext.current
    val app = App.get(ctx)
    val runtime by app.master.carNet.state.collectAsState()
    val hotspot by app.store.hotspot.collectAsState()
    val switchedOn by app.store.carNetOn.collectAsState()
    // Off means off: the card follows the preference, not the async runtime state that may lag behind it.
    val state = effectiveCarNetState(runtime, switchedOn)
    val manual = hotspot?.takeIf { !it.direct }
    val onPhone = LocalPhone.current
    val raw = carNetCopy(state, manual, Build.VERSION.SDK_INT)
    val copy = raw.copy(title = raw.title.selfWords(onPhone), line = raw.line?.selfWords(onPhone))
    var reveal by rememberSaveable { mutableStateOf(false) }
    // Not saved: the secret is hidden again whenever this card leaves the screen.
    var showSecret by remember { mutableStateOf(carNetStartRevealed) }
    var name by rememberSaveable(manual?.ssid) { mutableStateOf(manual?.ssid ?: "") }
    var pass by rememberSaveable(manual?.pass) { mutableStateOf(manual?.pass ?: "") }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { app.master.carNet.recheck() }
    fun act(a: CarAction) {
        when (a) {
            CarAction.ALLOW -> {
                val p = app.master.carNet.permissionToRequest()
                if (p.isEmpty()) app.master.carNet.recheck() else ask.launch(p.toTypedArray())
            }
            CarAction.OPEN_LOCATION -> ctx.startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            CarAction.DIFFERENT -> reveal = true
            CarAction.USE_CAR_NET -> { reveal = false; app.store.setHotspot(null); app.master.carNet.poke() }
        }
    }
    val up = state is CarNetState.On
    val phone = LocalPhone.current
    val tab = !phone && LocalParentTablet.current
    Column(
        Modifier.fillMaxWidth().panel(24.dp, 22.dp, 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.heightIn(min = if (phone) 52.dp else if (tab) TabletParentMetrics.primaryButtonHeight else 0.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Car network", style = body(if (phone || tab) 18.sp else 20.sp, 800, if (phone) 1.25f else 1.3f), color = C.text)
                Muted(CARNET_SWITCH_HELP, 15)
            }
            NickSwitch(switchedOn) { app.store.setCarNetOn(it) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (phone || tab) 12.dp else 14.dp)) {
            Icon(if (up) Lucide.Wifi else Lucide.WifiOff, null, tint = if (up) C.online else C.muted, modifier = Modifier.size(if (phone || tab) 22.dp else 26.dp))
            // The network name is one long token: on a phone it stays on one line and loses its middle rather than splitting mid-token (the full name is under Show).
            if (phone && state is CarNetState.On) Text(copy.title, style = body(18.sp, 800, 1.25f), color = C.text, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.MiddleEllipsis, modifier = Modifier.weight(1f))
            else Text(copy.title, style = body(if (phone || tab) 18.sp else 22.sp, 800, if (phone || tab) 1.25f else 1.3f), color = C.text, maxLines = if (phone) Int.MAX_VALUE else 2, modifier = Modifier.weight(1f))
            if (!phone && !tab && showSecret && state is CarNetState.On) NickButton(CARNET_HIDE, { showSecret = false }, Modifier.height(52.dp))
        }
        if ((phone || tab) && showSecret && state is CarNetState.On) NickButton(CARNET_HIDE, { showSecret = false }, if (phone) Modifier.fillMaxWidth() else Modifier)
        (state as? CarNetState.On)?.let { on ->
            // The password: this tablet's own record of it, never logged and never part of the state's toString.
            if (showSecret) CarNetSecret(on.ssid, on.pass ?: hotspot?.takeIf { it.direct && it.ssid == on.ssid }?.pass ?: app.store.carNetPass())
        }
        copy.line?.let { if (tab) Text(it, style = body(14.sp, 500, 20f / 14), color = C.muted) else Muted(it, 16) }
        val buttons: @Composable () -> Unit = {
            copy.primary?.let { b -> NickButton(b.label, { act(b.action) }, btnMod(), BtnStyle.PRIMARY) }
            if (up && !showSecret) NickButton(CARNET_SHOW, { showSecret = true }, btnMod(), BtnStyle.PRIMARY)
            copy.ghost?.let { b -> NickButton(b.label, { act(b.action) }, btnMod(), BtnStyle.GHOST) }
        }
        if (copy.primary != null || copy.ghost != null || (up && !showSecret)) {
            // Phone: the actions stack, each the full width and at least 48dp; tablets keep one row.
            if (phone) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { buttons() }
            else if (tab) FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { buttons() }
            else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { buttons() }
        }
        if (copy.fields || reveal) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val nameField: @Composable (Modifier) -> Unit = { m -> Column(m) { FieldLabel("Hotspot name"); NickField(name, { name = it }, Modifier.widthIn(max = 460.dp), placeholder = "e.g. Dad's phone") } }
            val passField: @Composable (Modifier) -> Unit = { m -> Column(m) { FieldLabel("Password"); NickField(pass, { pass = it }, Modifier.widthIn(max = 460.dp), placeholder = "8 to 63 characters") } }
            // Parent tablet: side by side from 600 of inner width, stacked below it.
            if (tab) BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth >= 600.dp) Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { nameField(Modifier.weight(1f)); passField(Modifier.weight(1f)) }
                else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { nameField(Modifier); passField(Modifier) }
            } else { nameField(Modifier); passField(Modifier) }
            NickButton("Save", {
                app.store.setHotspot(Hotspot(name.trim(), pass, direct = false)); reveal = false
            }, btnMod(), BtnStyle.PRIMARY, enabled = hotspotFieldsOk(name.trim(), pass))
            Muted(HOTSPOT_SAVE_NOTE, 15)
        }
    }
}

internal const val CARNET_SWITCH_HELP = "Turning this off can save battery. Kid tablets then need your home WiFi or a hotspot."

/** Test hook: the car network card opens with the name and password already shown. */
internal var carNetStartRevealed = false

@Composable
private fun CarNetSecret(ssid: String, pass: String) {
    val qr = remember(ssid, pass) { wifiQrBitmap(wifiQrPayload(ssid, pass), 500) }
    if (LocalPhone.current) {
        // Phone: name, password and QR code each get the full width, one under the other. Selectable, never squeezed.
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Long names and passwords have no spaces to break at: at large font sizes they get a smaller size on a line of their own.
            val token = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.5f) 14.sp else 20.sp
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column { FieldLabel("Network name"); Text(ssid, style = body(token, 800, 1.25f), color = C.text) }
                    Column { FieldLabel("Password"); Text(pass, style = body(token, 800, 1.25f), color = C.text) }
                }
            }
            Box(Modifier.align(Alignment.CenterHorizontally).size(200.dp).clip(RoundedCornerShape(PhoneMetrics.mediaRadius)).background(Color.White).padding(10.dp), contentAlignment = Alignment.Center) {
                Image(qr.asImageBitmap(), "Car network QR code", Modifier.fillMaxSize(), filterQuality = FilterQuality.None)
            }
            Muted(CARNET_SHARE_HELP, 15)
        }
        return
    }
    if (LocalParentTablet.current) {
        // Name / password at 18 on 25, labels 14, the 144 code on white with 12 quiet padding. Side by side only from 600 of inner width.
        val text: @Composable (Modifier) -> Unit = { m ->
            Column(m, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column { FieldLabel("Network name"); Text(ssid, style = body(18.sp, 800, 25f / 18), color = C.text) }
                        Column { FieldLabel("Password"); Text(pass, style = body(18.sp, 800, 25f / 18), color = C.text) }
                    }
                }
                Text(CARNET_SHARE_HELP, style = body(14.sp, 500, 20f / 14), color = C.muted)
            }
        }
        val code: @Composable () -> Unit = {
            Box(Modifier.size(168.dp).clip(RoundedCornerShape(TabletParentMetrics.cardRadius)).background(Color.White).padding(12.dp), contentAlignment = Alignment.Center) {
                Image(qr.asImageBitmap(), "Car network QR code", Modifier.fillMaxSize(), filterQuality = FilterQuality.None)
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth >= 600.dp) Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) { text(Modifier.weight(1f)); code() }
            else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { text(Modifier.fillMaxWidth()); code() }
        }
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column { FieldLabel("Network name"); Text(ssid, style = body(28.sp, 800), color = C.text) }
                    Column { FieldLabel("Password"); Text(pass, style = body(28.sp, 800), color = C.text) }
                }
            }
            Muted(CARNET_SHARE_HELP, 15)
        }
        Box(Modifier.size(170.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(10.dp), contentAlignment = Alignment.Center) {
            Image(qr.asImageBitmap(), "Car network QR code", Modifier.fillMaxSize(), filterQuality = FilterQuality.None)
        }
    }
}

// ---------------- Jellyfin ----------------

@Composable
private fun JellyfinSection() {
    val app = App.get(LocalContext.current)
    val account by app.store.jellyfin.collectAsState()
    val nodes by app.store.nodes.collectAsState()
    var picking by remember { mutableStateOf<LinkedNode?>(null) }
    var formKey by remember { mutableStateOf(0) }
    val phone = LocalPhone.current
    val tab = !phone && LocalParentTablet.current
    SectionPage("Jellyfin", {
        if (account != null) NickButton("Disconnect", { app.store.setJellyfin(null); formKey++ }, if (phone) Modifier.fillMaxWidth() else Modifier, style = BtnStyle.DANGER)
    }) {
        Muted("Optional. The NickOS library works without it.", 15)
        Column(Modifier.fillMaxWidth().panel(28.dp, 28.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(if (phone || tab) 16.dp else 22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Dot(if (account != null) C.online else C.offline)
                Text(if (account != null) "Connected" else "Not connected", style = body(18.sp, 700), color = if (account != null) C.online else C.muted)
            }
            androidx.compose.runtime.key(formKey, account?.token) {
                JellyfinForm(onSaved = {}, initial = account, footer = { save, saving ->
                    val fm = if (phone) Modifier.fillMaxWidth() else Modifier
                    if (account == null) NickButton(if (saving) "Saving…" else "Save", save, fm, style = BtnStyle.PRIMARY, enabled = !saving)
                    else NickButton(if (saving) "Saving…" else "Save changes", save, fm, enabled = !saving)
                })
            }
        }
        if (account != null && nodes.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().panel(28.dp, 28.dp, 28.dp)) {
                Text("Whose library each tablet shows", style = body(if (phone || tab) 18.sp else 20.sp, 800, if (phone) 1.25f else 1.3f), color = C.text)
                Muted("Watch browses this Jellyfin user's library on each tablet.", 16)
                Spacer(Modifier.height(10.dp))
                nodes.forEach { n ->
                    Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))
                    if (phone) Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Avatar(n.name, n.color, n.avatar, 40.dp)
                            Text(n.name, style = body(16.sp, 700, 1.25f), color = C.text, modifier = Modifier.weight(1f))
                        }
                        // The choice is its own 48dp action under the name.
                        Chip(Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).clickable { picking = n }, height = 48.dp) {
                            Text("Jellyfin user: ${n.jfUserName ?: "Choose"}", style = body(14.sp, 700), color = C.text, modifier = Modifier.weight(1f))
                            Icon(Lucide.ChevronRight, null, tint = C.text, modifier = Modifier.size(18.dp))
                        }
                    } else if (tab) Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Avatar(n.name, n.color, n.avatar, 40.dp)
                        Text(n.name, style = body(18.sp, 700, 1.25f), color = C.text, modifier = Modifier.weight(1f), maxLines = 2)
                        Chip(Modifier.clip(RoundedCornerShape(26.dp)).clickable(role = androidx.compose.ui.semantics.Role.Button) { picking = n }, height = 48.dp) {
                            Text("Jellyfin user: ${n.jfUserName ?: "Choose"}", style = body(15.sp, 700, 1.3f), color = C.text, maxLines = 2)
                            Icon(Lucide.ChevronRight, null, tint = C.text, modifier = Modifier.size(18.dp))
                        }
                    } else Row(Modifier.fillMaxWidth().height(76.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Avatar(n.name, n.color, n.avatar, 44.dp)
                        Text(n.name, style = body(20.sp, 700), color = C.text, modifier = Modifier.weight(1f), maxLines = 1)
                        Chip(Modifier.clip(RoundedCornerShape(26.dp)).clickable { picking = n }, height = 52.dp) {
                            Text("Jellyfin user: ${n.jfUserName ?: "Choose"}", style = body(17.sp, 700), color = C.text)
                            Icon(Lucide.ChevronRight, null, tint = C.text, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
    picking?.let { n ->
        val jf = remember(account) { app.master.jellyfin() }
        val users by produceState<List<JfUser>?>(null, jf) { value = try { jf?.users() } catch (e: Exception) { emptyList() } }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).tap { picking = null }, contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(if (phone) 16.dp else 24.dp).clip(RoundedCornerShape(if (phone) 20.dp else if (tab) TabletParentMetrics.dialogRadius else 30.dp)).background(C.surface).tap {}.padding(if (phone) 16.dp else if (tab) TabletParentMetrics.cardPadding else 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Title("${n.name} shows…", 32)
                Spacer(Modifier.height(6.dp))
                val list = users
                if (list == null) Muted("Loading users…", 18)
                list?.forEach { u ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (u.id == n.jfUserId) C.surface2 else Color.Transparent)
                            .clickable {
                                app.store.updateNode(n.id) { it.copy(jfUserId = u.id, jfUserName = u.name) }
                                app.master.refreshResume(n.id)
                                picking = null
                            }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Avatar(u.name, 4, "initial", 40.dp)
                        Text(u.name, style = body(19.sp, 700), color = C.text, modifier = Modifier.weight(1f))
                        if (u.id == n.jfUserId) Icon(Lucide.Check, null, tint = C.accent, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

// ---------------- PIN ----------------

@Composable
private fun PinSection() {
    val app = App.get(LocalContext.current)
    var first by remember { mutableStateOf<String?>(null) }
    var entered by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    val currentPin by app.store.pin.collectAsState()
    SectionPage("Parent PIN") {
        BoxWithConstraints {
            val phone = LocalPhone.current
            val tab = !phone && LocalParentTablet.current
            // Parent tablet: the 400 PIN panel sits beside the words from 740 of inner width, under them below it.
            val narrow = if (tab) maxWidth < 740.dp else maxWidth < 800.dp
            val text: @Composable (Modifier) -> Unit = { mod ->
                Column(mod, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (tab) {
                        Text("Used to leave kid mode on every Node tablet.", style = body(16.sp, 500, 22f / 16), color = C.text)
                        Text("Changing it here updates all linked tablets.", style = body(14.sp, 500, 20f / 14), color = C.muted)
                        if (com.nbaltas.nickos.node.PinPolicy.isWeak(currentPin)) Text("This is still the starting PIN. Anyone who knows NickOS knows it.", style = body(14.sp, 700, 20f / 14), color = C.danger)
                        message?.let { (ok, m) -> Text(m, style = body(14.sp, 700, 20f / 14), color = if (ok) C.online else C.danger) }
                        return@Column
                    }
                    Text("Used to leave kid mode on every Node tablet.", style = body(if (phone) 16.sp else 21.sp, 500, 1.45f), color = C.text)
                    Muted("Changing it here updates all linked tablets.", 17)
                    if (com.nbaltas.nickos.node.PinPolicy.isWeak(currentPin)) Text("This is still the starting PIN. Anyone who knows NickOS knows it.", style = body(if (phone) 16.sp else 18.sp, 700, if (phone) 1.35f else 1.3f), color = C.danger)
                    message?.let { (ok, m) -> Text(m, style = body(if (phone) 16.sp else 18.sp, 700, if (phone) 1.35f else 1.3f), color = if (ok) C.online else C.danger) }
                }
            }
            val pad: @Composable () -> Unit = {
                Column(
                    (if (phone) Modifier.fillMaxWidth().clip(RoundedCornerShape(PhoneMetrics.cardRadius)).background(C.surface).padding(PhoneMetrics.cardPadding)
                    else if (tab) Modifier.widthIn(max = 400.dp).fillMaxWidth().clip(RoundedCornerShape(TabletParentMetrics.cardRadius)).background(C.surface).padding(TabletParentMetrics.cardPadding)
                    else Modifier.widthIn(max = 484.dp).fillMaxWidth().clip(RoundedCornerShape(36.dp)).background(C.surface).padding(horizontal = 32.dp, vertical = 30.dp)),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(if (phone || tab) 16.dp else 22.dp),
                ) {
                    if (tab) Title(if (first == null) "Enter a new 4-digit PIN" else "Enter it again", 36, maxLines = 2)
                    else Text(if (first == null) "Enter a new 4-digit PIN" else "Enter it again", style = body(if (phone) 18.sp else 20.sp, 700), color = C.text)
                    PinDots(entered.length)
                    Keypad(
                        onDigit = { d ->
                            if (entered.length < 4) entered += d
                            if (entered.length == 4) {
                                val f = first
                                if (f == null && com.nbaltas.nickos.node.PinPolicy.isWeak(entered)) { entered = ""; message = false to "Pick a PIN that is not 0000 and not four of the same digit." }
                                else if (f == null) { first = entered; entered = ""; message = null }
                                else if (f == entered) { app.store.setPin(entered); first = null; entered = ""; message = true to "PIN changed on this tablet and every linked tablet." }
                                else { first = null; entered = ""; message = false to "Those didn't match. Start again." }
                            }
                        },
                        onDelete = { entered = entered.dropLast(1) },
                        modifier = if (phone || tab) Modifier.fillMaxWidth() else Modifier.width(356.dp),
                        keyHeight = if (tab) 64.dp else 72.dp,
                        gap = if (tab) 10.dp else 14.dp,
                    )
                }
            }
            if (narrow) Column(verticalArrangement = Arrangement.spacedBy(if (tab) 16.dp else 28.dp)) { text(Modifier.fillMaxWidth()); pad() }
            else Row(horizontalArrangement = Arrangement.spacedBy(if (tab) 32.dp else 48.dp)) { text(Modifier.weight(1f)); pad() }
        }
    }
}

// ---------------- Theme ----------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemeSection() {
    val app = App.get(LocalContext.current)
    val theme by app.store.theme.collectAsState()
    SectionPage("Theme") {
        if (LocalPhone.current) {
            // One row per theme: a 120x80 preview, the name, and a selected mark. The whole row is the 48dp+ target.
            Column(verticalArrangement = Arrangement.spacedBy(PhoneMetrics.cardGap)) {
                listOf("DARK" to "Dark", "LIGHT" to "Light", "SYSTEM" to "Match system").forEach { (key, label) ->
                    val on = theme == key
                    val shape = RoundedCornerShape(PhoneMetrics.cardRadius)
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 96.dp).clip(shape).background(C.surface).border(3.dp, if (on) C.accent else C.line, shape)
                            .selectable(selected = on, role = androidx.compose.ui.semantics.Role.RadioButton) { app.store.setTheme(key) }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        ThemePreview(key, Modifier.size(120.dp, 80.dp))
                        Text(label, style = body(16.sp, 800, 1.25f), color = C.text, modifier = Modifier.weight(1f))
                        if (on) Box(Modifier.size(28.dp).clip(CircleShape).background(C.accent), contentAlignment = Alignment.Center) {
                            Icon(Lucide.Check, null, tint = C.onAccent, modifier = Modifier.size(16.dp))
                        } else Box(Modifier.size(28.dp).border(3.dp, C.line, CircleShape))
                    }
                }
            }
        } else if (LocalParentTablet.current) BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Previews are 160x120 at least and 220x150 at most; the cards wrap onto a second row below a 480-wide pane.
            val w = ((maxWidth - 32.dp) / 3 - 24.dp).coerceIn(160.dp, 220.dp)
            val h = (w * 150f / 220f).coerceIn(120.dp, 150.dp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                listOf("DARK" to "Dark", "LIGHT" to "Light", "SYSTEM" to "Match system").forEach { (key, label) ->
                    val on = theme == key
                    val shape = RoundedCornerShape(TabletParentMetrics.cardRadius)
                    Column(
                        Modifier.clip(shape).background(C.surface).border(if (on) 3.dp else 1.dp, if (on) C.accent else C.line, shape)
                            .selectable(selected = on, role = androidx.compose.ui.semantics.Role.RadioButton) { app.store.setTheme(key) }.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ThemePreview(key, Modifier.size(w, h))
                        Row(Modifier.width(w).heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(label, style = body(16.sp, 800, 1.25f), color = C.text, modifier = Modifier.weight(1f), maxLines = 2)
                            if (on) Box(Modifier.size(24.dp).clip(CircleShape).background(C.accent), contentAlignment = Alignment.Center) {
                                Icon(Lucide.Check, null, tint = C.onAccent, modifier = Modifier.size(14.dp))
                            } else Box(Modifier.size(24.dp).border(2.dp, C.line, CircleShape))
                        }
                    }
                }
            }
        } else Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            listOf("DARK" to "Dark", "LIGHT" to "Light", "SYSTEM" to "Match system").forEach { (key, label) ->
                val on = theme == key
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(28.dp)).background(C.surface).border(3.dp, if (on) C.accent else C.line, RoundedCornerShape(28.dp))
                        .clickable { app.store.setTheme(key) }.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ThemePreview(key)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, style = body(21.sp, 800), color = C.text, modifier = Modifier.weight(1f), maxLines = 1)
                        if (on) Box(Modifier.size(32.dp).clip(CircleShape).background(C.accent), contentAlignment = Alignment.Center) {
                            Icon(Lucide.Check, null, tint = C.onAccent, modifier = Modifier.size(18.dp))
                        } else Box(Modifier.size(32.dp).border(3.dp, C.line, CircleShape))
                    }
                }
            }
        }
        Muted("Kid tablets keep their own colour. Theme applies to this ${selfNoun()}.", 17)
    }
}

@Composable
private fun ThemePreview(key: String, modifier: Modifier = Modifier.fillMaxWidth().height(180.dp)) {
    val small = LocalPhone.current || LocalParentTablet.current
    Box(modifier.clip(RoundedCornerShape(if (small) 12.dp else 18.dp))) {
        Row(Modifier.fillMaxSize()) {
            if (key != "LIGHT") Box(Modifier.weight(1f).fillMaxHeight().background(DarkColors.bg))
            if (key != "DARK") Box(Modifier.weight(1f).fillMaxHeight().background(LightColors.bg))
        }
        Column(Modifier.fillMaxSize().padding(if (small) 8.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(if (small) 6.dp else 10.dp)) {
            repeat(2) { r ->
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(if (small) 6.dp else 10.dp)) {
                    repeat(2) { c ->
                        val light = key == "LIGHT" || (key == "SYSTEM" && c == 1)
                        val tile = when {
                            key == "SYSTEM" && !light -> Color(0xFF3A3D44)
                            key == "SYSTEM" -> Color(0xFFC4C1BA)
                            light -> Color.White
                            else -> DarkColors.surface2
                        }
                        val m = Modifier.weight(1f).fillMaxHeight()
                        if (r == 1 && c == 1) Box(m.dashed(if (light) Color(0xFFC07A00) else C.accent, 12.dp, 2.dp))
                        else Box(m.clip(RoundedCornerShape(12.dp)).background(tile))
                    }
                }
            }
        }
    }
}

// ---------------- Cameras ----------------

@Composable
private fun CamerasSection() {
    val app = App.get(LocalContext.current)
    val prefs by app.store.camPrefs.collectAsState()
    SectionPage("Cameras") {
        CamPrefRow(
            "Tablet picture", "Lower is cooler and smoother on a weak WiFi.",
            listOf("Low" to "low", "Normal" to "normal", "High" to "high"), prefs.profile,
        ) { app.store.setCamPrefs(prefs.copy(profile = it)) }
        CamPrefRow(
            "Turn tablet cameras off after",
            "A tablet camera that stays on warms the tablet up. Cameras also turn off by themselves when the tablet gets too warm.",
            listOf("5 min" to 5, "10 min" to 10, "30 min" to 30, "60 min" to 60), prefs.maxMin,
        ) { app.store.setCamPrefs(prefs.copy(maxMin = it)) }
        CamPrefRow(
            "Layout animation", "How tiles move when a camera comes or goes.",
            listOf("Off" to 0, "Quick" to 220, "Smooth" to 450), prefs.layoutMs,
        ) { app.store.setCamPrefs(prefs.copy(layoutMs = it)) }
        CamPrefRow(
            "Fade", "How a picture appears and disappears.",
            listOf("Off" to 0, "Quick" to 250, "Smooth" to 600), prefs.fadeMs,
        ) { app.store.setCamPrefs(prefs.copy(fadeMs = it)) }
        Muted("Kid tablets show Android's own camera dot while their camera is on. NickOS adds nothing else on the kid's screen.", 15)
    }
}

@Composable
private fun <T> CamPrefRow(label: String, help: String, options: List<Pair<String, T>>, current: T, onSelect: (T) -> Unit) {
    val values = options.map { it.second }
    val selected = values.indexOf(current).coerceAtLeast(0)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val tab = !LocalPhone.current && LocalParentTablet.current
        val narrow = maxWidth < (if (tab) 640.dp else 760.dp)
        val text: @Composable (Modifier) -> Unit = { mod ->
            Column(mod, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, style = body(if (LocalPhone.current || tab) 18.sp else 19.sp, 700, if (LocalPhone.current || tab) 1.25f else 1.3f), color = C.text)
                Muted(help, 15)
            }
        }
        val control: @Composable () -> Unit = {
            Segmented(options.map { it.first to null }, selected, { onSelect(values[it]) })
        }
        if (narrow) Column(verticalArrangement = Arrangement.spacedBy(14.dp)) { text(Modifier.fillMaxWidth()); control() }
        else Row(verticalAlignment = Alignment.CenterVertically) { text(Modifier.weight(1f)); control() }
    }
}

// ---------------- About ----------------

@Composable
private fun AboutSection() {
    val app = App.get(LocalContext.current)
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val name by app.store.name.collectAsState()
    val nodes by app.store.nodes.collectAsState()
    val update by Updater.state.collectAsState()
    val views = rememberNodeViews()
    var confirmReset by remember { mutableStateOf(false) }
    val phone = LocalPhone.current
    val tab = !phone && LocalParentTablet.current
    SectionPage("About") {
        // Phone: the app summary is a column (mark, name, version, status, then the action on its own row).
        val summary: @Composable (@Composable () -> Unit, @Composable () -> Unit) -> Unit = { info, act ->
            if (phone) Column(Modifier.fillMaxWidth().panel(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NickLogo(48.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { info() }
                }
                act()
            } else Row(
                Modifier.fillMaxWidth().panel(28.dp, 32.dp, 32.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (tab) 16.dp else 24.dp),
            ) {
                NickLogo(if (tab) 64.dp else 96.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { info() }
                act()
            }
        }
        summary({
                Title("NickOS", 40)
                Muted("Version ${BuildConfig.VERSION_NAME}", 18)
                when (val u = update) {
                    UpdateState.Checking -> Muted("Checking…", 16)
                    UpdateState.UpToDate -> Muted("You're up to date.", 16)
                    is UpdateState.Available -> Text("Version ${u.info.versionName} is ready" + (u.info.notes?.let { ": $it" } ?: ""), style = body(16.sp, 700), color = C.accent)
                    is UpdateState.Downloading -> Muted("Downloading ${u.pct}%…", 16)
                    is UpdateState.Installing -> Muted("Installing…", 16)
                    is UpdateState.Failed -> Text(u.message, style = body(16.sp, 700), color = C.danger)
                    UpdateState.Idle -> {}
                }
        }, {
            val available = update as? UpdateState.Available
            val am = if (phone) Modifier.fillMaxWidth() else Modifier
            if (available != null) NickButton("Install ${available.info.versionName}", { scope.launch { Updater.install(ctx, available.info) } }, am, style = BtnStyle.PRIMARY, leading = Lucide.Download)
            else NickButton("Check for updates", { scope.launch { Updater.check() } }, am, leading = Lucide.RefreshCw, enabled = update !is UpdateState.Checking && update !is UpdateState.Downloading)
        })
        if (views.isNotEmpty()) {
            val latest = (update as? UpdateState.Available)?.info?.versionName ?: if (update == UpdateState.UpToDate) BuildConfig.VERSION_NAME else null
            Column(Modifier.fillMaxWidth().then(if (phone) Modifier.panel() else if (tab) Modifier.clip(RoundedCornerShape(TabletParentMetrics.cardRadius)).background(C.surface).padding(horizontal = TabletParentMetrics.cardPadding, vertical = 8.dp) else Modifier.clip(RoundedCornerShape(28.dp)).background(C.surface).padding(horizontal = 28.dp, vertical = 12.dp))) {
                views.forEachIndexed { i, v ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))
                    val behind = v.status?.version != null && latest != null && v.status.version != latest
                    val updateButton: @Composable (Modifier) -> Unit = { m ->
                        if (v.online && behind) NickButton("Update", { app.master.updateNode(v.node.id); app.master.say("Updating ${v.node.name}…") }, m, leading = Lucide.Download)
                        else if (v.online && latest != null) Muted("Up to date", 15)
                    }
                    if (phone) Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Avatar(v.node.name, v.node.color, v.node.avatar, 32.dp)
                            Column(Modifier.weight(1f)) {
                                Text(v.node.name, style = body(18.sp, 700, 1.25f), color = C.text)
                                Muted(v.status?.version?.let { "Version $it" } ?: if (v.online) "Version unknown" else statusText(v), 15)
                            }
                        }
                        // The update action is its own row under the tablet.
                        updateButton(Modifier.fillMaxWidth())
                    } else if (tab) Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Avatar(v.node.name, v.node.color, v.node.avatar, 40.dp)
                        Column(Modifier.weight(1f)) {
                            Text(v.node.name, style = body(18.sp, 700, 1.25f), color = C.text, maxLines = 2)
                            Muted(v.status?.version?.let { "Version $it" } ?: if (v.online) "Version unknown" else statusText(v), 15)
                        }
                        updateButton(Modifier)
                    } else Row(Modifier.fillMaxWidth().height(76.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Avatar(v.node.name, v.node.color, v.node.avatar, 44.dp)
                        Column(Modifier.weight(1f)) {
                            Text(v.node.name, style = body(19.sp, 700), color = C.text, maxLines = 1)
                            Muted(v.status?.version?.let { "Version $it" } ?: if (v.online) "Version unknown" else statusText(v), 15)
                        }
                        updateButton(Modifier.height(52.dp))
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().then(if (phone) Modifier.panel() else if (tab) Modifier.clip(RoundedCornerShape(TabletParentMetrics.cardRadius)).background(C.surface).padding(horizontal = TabletParentMetrics.cardPadding, vertical = 4.dp) else Modifier.clip(RoundedCornerShape(28.dp)).background(C.surface).padding(horizontal = 28.dp, vertical = 8.dp))) {
            InfoRow("This ${selfNoun()}", "${name.ifBlank { "Parent's Tablet" }} · ${if (phone) "Parent" else "Master"}")
            Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))
            InfoRow("Linked tablets", nodes.size.toString())
            Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))
            InfoRow("Connection", "Local WiFi only")
        }
        HomeScreenRow()
        NickButton(if (confirmReset) "Tap again: forget every tablet and start over" else "Reset this ${selfNoun()}", {
            if (confirmReset) {
                if (EntrySwitch.enableMain(ctx)) com.nbaltas.nickos.RoleReset.run(ctx, app)
            } else confirmReset = true
        }, if (phone) Modifier.fillMaxWidth() else Modifier, style = BtnStyle.DANGER)
    }
}

/**
 * The parent's choice to use NickOS as this tablet's home screen. The switch is the choice; the line under it is what
 * Android really says, so a cancelled prompt leaves the switch on with the line telling the truth.
 */
@Composable
private fun HomeScreenRow() {
    val ctx = LocalContext.current
    val app = App.get(ctx)
    val optIn by app.store.masterHomeOptIn.collectAsState()
    var isHome by remember { mutableStateOf(isDefaultHome(ctx)) }
    val ask = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) {
        isHome = isDefaultHome(ctx)
        app.store.setMasterHomeOptIn(isHome == true)
        EntrySwitch.applyStored(ctx)
    }
    // Turning it off opens no page, so look again right after any switch change. Android takes a moment to settle.
    androidx.compose.runtime.LaunchedEffect(optIn) {
        for (wait in listOf(0L, 400L, 1200L)) {
            kotlinx.coroutines.delay(wait)
            isHome = isDefaultHome(ctx)
        }
    }
    // Look again when the parent comes back from the system page.
    val lifecycle = (ctx as? androidx.lifecycle.LifecycleOwner)?.lifecycle
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
            isHome = isDefaultHome(ctx)
            app.store.setMasterHomeOptIn(isHome == true)
            EntrySwitch.applyStored(ctx)
        } }
        lifecycle?.addObserver(obs)
        onDispose { lifecycle?.removeObserver(obs) }
    }
    Row(
        Modifier.fillMaxWidth().panel(28.dp, 28.dp, 16.dp).heightIn(min = if (LocalPhone.current) 52.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            val compact = LocalPhone.current || LocalParentTablet.current
            Text("Use NickOS as this ${selfNoun()}'s home screen", style = body(if (compact) 16.sp else 19.sp, 700, if (compact) 1.25f else 1.3f), color = C.text)
            Muted(homeStateLine(isHome), 15)
        }
        NickSwitch(optIn) { on ->
            // On: the HOME offer is switched on before the system is asked. Off: the running activity switches the door back.
            if (on) EntrySwitch.enableMain(ctx)
            if (!on) app.store.setMasterHomeOptIn(false)
            if (on) {
                val request = homeRequestIntent(ctx)
                if (request != null) {
                    try { ask.launch(request) } catch (_: android.content.ActivityNotFoundException) { EntrySwitch.applyStored(ctx) }
                } else {
                    app.store.setMasterHomeOptIn(isDefaultHome(ctx) == true)
                    EntrySwitch.applyStored(ctx)
                }
            }
        }
    }
}

@Composable
private fun InfoRow(k: String, v: String, h: Dp = 64.dp) {
    if (LocalPhone.current) {
        // Label over value, so a long value never squeezes the label.
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(k, style = body(14.sp, 500, 1.3f), color = C.muted)
            Text(v, style = body(16.sp, 700, 1.3f), color = C.text)
        }
        return
    }
    if (LocalParentTablet.current) {
        // At least 48 high: label 14, value 15, and a long value wraps instead of pushing the label out.
        Row(Modifier.fillMaxWidth().heightIn(min = TabletParentMetrics.minTarget).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(k, style = body(14.sp, 500, 20f / 14), color = C.muted, modifier = Modifier.weight(1f))
            Text(v, style = body(15.sp, 700, 22f / 15), color = C.text, modifier = Modifier.weight(1.4f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
        return
    }
    Row(Modifier.fillMaxWidth().height(h), verticalAlignment = Alignment.CenterVertically) {
        Text(k, style = body(19.sp), color = C.muted, modifier = Modifier.weight(1f))
        Text(v, style = body(19.sp, 700), color = C.text)
    }
}
