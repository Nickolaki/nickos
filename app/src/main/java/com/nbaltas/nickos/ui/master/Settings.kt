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
import com.nbaltas.nickos.update.UpdateState
import com.nbaltas.nickos.update.Updater
import kotlinx.coroutines.launch
import com.nbaltas.nickos.ui.setup.JellyfinForm
import com.nbaltas.nickos.ui.setup.rotate90
import com.nbaltas.nickos.ui.theme.Avatar
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

@Composable
fun SettingsScreen(section: Int, onSection: (Int) -> Unit, onBack: () -> Unit, onAdd: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val panelW = if (maxWidth < 1000.dp) 250.dp else 320.dp
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.width(panelW).fillMaxHeight().background(C.panel).padding(horizontal = 20.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.padding(bottom = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    BackButton(onBack)
                    Title("Settings", 34, maxLines = 1)
                }
                sections.forEachIndexed { i, (label, icon) ->
                    val on = i == section
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
            Box(Modifier.weight(1f).fillMaxHeight()) {
                when (section) {
                    0 -> TabletsSection(onAdd)
                    1 -> JellyfinSection()
                    2 -> CamerasSection()
                    3 -> PinSection()
                    4 -> ThemeSection()
                    else -> AboutSection()
                }
            }
        }
    }
}

@Composable
private fun SectionPage(title: String, action: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
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
    SectionPage("Linked tablets", { NickButton("Add tablet", onAdd, style = BtnStyle.PRIMARY, leading = Lucide.Plus) }) {
        Row(
            Modifier.fillMaxWidth().border(2.dp, C.line, RoundedCornerShape(24.dp)).padding(horizontal = 22.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.size(60.dp).clip(CircleShape).background(C.accent), contentAlignment = Alignment.Center) {
                Icon(Lucide.Tablet, null, tint = C.onAccent, modifier = Modifier.size(26.dp).rotate90())
            }
            Column(Modifier.weight(1f)) {
                Text(name.ifBlank { "Parent's Tablet" }, style = body(20.sp, 800), color = C.text)
                Muted("This tablet · Master", 16)
            }
            NickButton("Rename", { renaming = "" }, Modifier.height(52.dp), leading = Lucide.Pencil)
        }
        CarNetCard()
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            views.forEach { v ->
                val buttons: @Composable () -> Unit = {
                    NickButton("Rename", { renaming = v.node.id }, Modifier.height(52.dp), leading = Lucide.Pencil)
                    NickButton(if (confirmUnlink == v.node.id) "Sure?" else "Unlink", {
                        if (confirmUnlink == v.node.id) { app.master.unlink(v.node.id); confirmUnlink = null } else confirmUnlink = v.node.id
                    }, Modifier.height(52.dp), BtnStyle.DANGER, leading = Lucide.Unlink)
                }
                // Portrait tablets/phones: the buttons go on their own line, or they squeeze the name to nothing.
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val narrow = maxWidth < 680.dp
                    Column(
                        // Design: min-height 96 on the content box, plus 16 padding top and bottom.
                        Modifier.fillMaxWidth().heightIn(min = 128.dp).clip(RoundedCornerShape(24.dp)).background(C.surface).padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            Avatar(v.node.name, v.node.color, v.node.avatar, 60.dp)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(v.node.name, style = body(22.sp, 800), color = C.text, maxLines = 1)
                                StatusLine(v, 16, 10.dp, if (v.online) "Online" else statusText(v))
                                Muted(tabletNetLine(v.online, net[v.node.id], v.node.wifiSaved, hotspot), 15)
                            }
                            Text(v.battery?.let { "$it%" } ?: "", style = disp(28.sp), color = if (v.online) C.text else C.muted, modifier = Modifier.width(90.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                            if (!narrow) buttons()
                        }
                        if (narrow) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { buttons() }
                    }
                }
            }
            if (views.isEmpty()) Muted("No tablets linked yet.", 18)
        }
    }
    renaming?.let { id ->
        if (id.isEmpty()) RenameDialog(name.ifBlank { "Parent's Tablet" }, "Rename this tablet") { n -> renaming = null; if (n != null) app.store.setName(n) }
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
    val copy = carNetCopy(state, manual, Build.VERSION.SDK_INT)
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
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(C.surface).padding(horizontal = 22.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Car network", style = body(20.sp, 800), color = C.text)
                Muted(CARNET_SWITCH_HELP, 15)
            }
            NickSwitch(switchedOn) { app.store.setCarNetOn(it) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(if (up) Lucide.Wifi else Lucide.WifiOff, null, tint = if (up) C.online else C.muted, modifier = Modifier.size(26.dp))
            Text(copy.title, style = body(22.sp, 800), color = C.text, maxLines = 2, modifier = Modifier.weight(1f))
            if (showSecret && state is CarNetState.On) NickButton(CARNET_HIDE, { showSecret = false }, Modifier.height(52.dp))
        }
        (state as? CarNetState.On)?.let { on ->
            // The password: this tablet's own record of it, never logged and never part of the state's toString.
            if (showSecret) CarNetSecret(on.ssid, on.pass ?: hotspot?.takeIf { it.direct && it.ssid == on.ssid }?.pass ?: app.store.carNetPass())
        }
        copy.line?.let { Muted(it, 16) }
        if (copy.primary != null || copy.ghost != null || (up && !showSecret)) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            copy.primary?.let { b -> NickButton(b.label, { act(b.action) }, Modifier.height(52.dp), BtnStyle.PRIMARY) }
            if (up && !showSecret) NickButton(CARNET_SHOW, { showSecret = true }, Modifier.height(52.dp), BtnStyle.PRIMARY)
            copy.ghost?.let { b -> NickButton(b.label, { act(b.action) }, Modifier.height(52.dp), BtnStyle.GHOST) }
        }
        if (copy.fields || reveal) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column { FieldLabel("Hotspot name"); NickField(name, { name = it }, Modifier.widthIn(max = 460.dp), placeholder = "e.g. Dad's phone") }
            Column { FieldLabel("Password"); NickField(pass, { pass = it }, Modifier.widthIn(max = 460.dp), placeholder = "8 to 63 characters") }
            NickButton("Save", {
                app.store.setHotspot(Hotspot(name.trim(), pass, direct = false)); reveal = false
            }, Modifier.height(52.dp), BtnStyle.PRIMARY, enabled = hotspotFieldsOk(name.trim(), pass))
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
    SectionPage("Jellyfin", {
        if (account != null) NickButton("Disconnect", { app.store.setJellyfin(null); formKey++ }, style = BtnStyle.DANGER)
    }) {
        Muted("Optional. The NickOS library works without it.", 15)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(C.surface).padding(28.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Dot(if (account != null) C.online else C.offline)
                Text(if (account != null) "Connected" else "Not connected", style = body(18.sp, 700), color = if (account != null) C.online else C.muted)
            }
            androidx.compose.runtime.key(formKey, account?.token) {
                JellyfinForm(onSaved = {}, initial = account, footer = { save, saving ->
                    if (account == null) NickButton(if (saving) "Saving…" else "Save", save, style = BtnStyle.PRIMARY, enabled = !saving)
                    else NickButton(if (saving) "Saving…" else "Save changes", save, enabled = !saving)
                })
            }
        }
        if (account != null && nodes.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(C.surface).padding(28.dp)) {
                Text("Whose library each tablet shows", style = body(20.sp, 800), color = C.text)
                Muted("Watch browses this Jellyfin user's library on each tablet.", 16)
                Spacer(Modifier.height(10.dp))
                nodes.forEach { n ->
                    Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))
                    Row(Modifier.fillMaxWidth().height(76.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
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
                Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(24.dp).clip(RoundedCornerShape(30.dp)).background(C.surface).tap {}.padding(28.dp),
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
            val narrow = maxWidth < 800.dp
            val text: @Composable (Modifier) -> Unit = { mod ->
                Column(mod, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Used to leave kid mode on every Node tablet.", style = body(21.sp, 500, 1.45f), color = C.text)
                    Muted("Changing it here updates all linked tablets.", 17)
                    if (com.nbaltas.nickos.node.PinPolicy.isWeak(currentPin)) Text("This is still the starting PIN. Anyone who knows NickOS knows it.", style = body(18.sp, 700), color = C.danger)
                    message?.let { (ok, m) -> Text(m, style = body(18.sp, 700), color = if (ok) C.online else C.danger) }
                }
            }
            val pad: @Composable () -> Unit = {
                Column(
                    Modifier.widthIn(max = 484.dp).fillMaxWidth().clip(RoundedCornerShape(36.dp)).background(C.surface).padding(horizontal = 32.dp, vertical = 30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    Text(if (first == null) "Enter a new 4-digit PIN" else "Enter it again", style = body(20.sp, 700), color = C.text)
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
                        modifier = Modifier.width(356.dp),
                        keyHeight = 72.dp,
                    )
                }
            }
            if (narrow) Column(verticalArrangement = Arrangement.spacedBy(28.dp)) { text(Modifier.fillMaxWidth()); pad() }
            else Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) { text(Modifier.weight(1f)); pad() }
        }
    }
}

// ---------------- Theme ----------------

@Composable
private fun ThemeSection() {
    val app = App.get(LocalContext.current)
    val theme by app.store.theme.collectAsState()
    SectionPage("Theme") {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
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
        Muted("Kid tablets keep their own colour. Theme applies to this tablet.", 17)
    }
}

@Composable
private fun ThemePreview(key: String) {
    Box(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(18.dp))) {
        Row(Modifier.fillMaxSize()) {
            if (key != "LIGHT") Box(Modifier.weight(1f).fillMaxHeight().background(DarkColors.bg))
            if (key != "DARK") Box(Modifier.weight(1f).fillMaxHeight().background(LightColors.bg))
        }
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(2) { r ->
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
        val narrow = maxWidth < 760.dp
        val text: @Composable (Modifier) -> Unit = { mod ->
            Column(mod, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, style = body(19.sp, 700), color = C.text)
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
    SectionPage("About") {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(C.surface).padding(32.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            NickLogo(96.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
            }
            val available = update as? UpdateState.Available
            if (available != null) NickButton("Install ${available.info.versionName}", { scope.launch { Updater.install(ctx, available.info) } }, style = BtnStyle.PRIMARY, leading = Lucide.Download)
            else NickButton("Check for updates", { scope.launch { Updater.check() } }, leading = Lucide.RefreshCw, enabled = update !is UpdateState.Checking && update !is UpdateState.Downloading)
        }
        if (views.isNotEmpty()) {
            val latest = (update as? UpdateState.Available)?.info?.versionName ?: if (update == UpdateState.UpToDate) BuildConfig.VERSION_NAME else null
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(C.surface).padding(horizontal = 28.dp, vertical = 12.dp)) {
                views.forEachIndexed { i, v ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))
                    Row(Modifier.fillMaxWidth().height(76.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Avatar(v.node.name, v.node.color, v.node.avatar, 44.dp)
                        Column(Modifier.weight(1f)) {
                            Text(v.node.name, style = body(19.sp, 700), color = C.text, maxLines = 1)
                            Muted(v.status?.version?.let { "Version $it" } ?: if (v.online) "Version unknown" else statusText(v), 15)
                        }
                        val behind = v.status?.version != null && latest != null && v.status.version != latest
                        if (v.online && behind) NickButton("Update", { app.master.updateNode(v.node.id); app.master.say("Updating ${v.node.name}…") }, Modifier.height(52.dp), leading = Lucide.Download)
                        else if (v.online && latest != null) Muted("Up to date", 15)
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(C.surface).padding(horizontal = 28.dp, vertical = 8.dp)) {
            InfoRow("This tablet", "${name.ifBlank { "Parent's Tablet" }} · Master")
            Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))
            InfoRow("Linked tablets", nodes.size.toString())
            Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))
            InfoRow("Connection", "Local WiFi only")
        }
        HomeScreenRow()
        NickButton(if (confirmReset) "Tap again: forget every tablet and start over" else "Reset this tablet", {
            if (confirmReset) app.store.resetAll() else confirmReset = true
        }, style = BtnStyle.DANGER)
    }
}

/** What the Home screen row says. [isHome] is null when the system would not say, which reads as a normal app. */
internal fun homeScreenLine(isHome: Boolean?): String =
    if (isHome == true) "NickOS is this tablet's home screen" else "NickOS opens like a normal app"

/** The intent for the system's home screen settings, or null when this tablet has no such page. */
private fun homeSettingsIntent(ctx: android.content.Context): Intent? {
    val i = Intent(android.provider.Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try { i.takeIf { it.resolveActivity(ctx.packageManager) != null } } catch (e: Exception) { null }
}

/** Where this tablet's home screen is set. NickOS works either way; the change happens in the system page, not here. */
@Composable
private fun HomeScreenRow() {
    val ctx = LocalContext.current
    var isHome by remember { mutableStateOf(isDefaultHome(ctx)) }
    // Look again when the parent comes back from the system page.
    val lifecycle = (ctx as? androidx.lifecycle.LifecycleOwner)?.lifecycle
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) isHome = isDefaultHome(ctx) }
        lifecycle?.addObserver(obs)
        onDispose { lifecycle?.removeObserver(obs) }
    }
    val intent = remember { homeSettingsIntent(ctx) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(C.surface).padding(horizontal = 28.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("Home screen", style = body(19.sp, 700), color = C.text)
            Muted(homeScreenLine(isHome), 15)
        }
        if (intent != null) NickButton("Open home screen settings", {
            try { ctx.startActivity(intent) } catch (_: android.content.ActivityNotFoundException) {}
        }, Modifier.height(52.dp))
    }
}

@Composable
private fun InfoRow(k: String, v: String, h: Dp = 64.dp) {
    Row(Modifier.fillMaxWidth().height(h), verticalAlignment = Alignment.CenterVertically) {
        Text(k, style = body(19.sp), color = C.muted, modifier = Modifier.weight(1f))
        Text(v, style = body(19.sp, 700), color = C.text)
    }
}
