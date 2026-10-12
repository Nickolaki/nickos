package com.nbaltas.nickos.ui.setup

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Server
import com.composables.icons.lucide.Smartphone
import com.composables.icons.lucide.Tablet
import com.composables.icons.lucide.Wifi
import com.nbaltas.nickos.App
import com.nbaltas.nickos.data.JellyfinAccount
import com.nbaltas.nickos.data.Role
import com.nbaltas.nickos.jellyfin.Jellyfin
import com.nbaltas.nickos.master.MasterRuntime
import com.nbaltas.nickos.master.NodeStatus
import com.nbaltas.nickos.node.NodeService
import com.nbaltas.nickos.ui.theme.AvatarColors
import com.nbaltas.nickos.ui.theme.AvatarIcons
import com.nbaltas.nickos.ui.theme.Avatar
import com.nbaltas.nickos.ui.theme.BtnStyle
import com.nbaltas.nickos.ui.theme.C
import com.nbaltas.nickos.ui.theme.Chip
import com.nbaltas.nickos.ui.theme.Dot
import com.nbaltas.nickos.ui.theme.FieldLabel
import com.nbaltas.nickos.ui.theme.Keypad
import com.nbaltas.nickos.ui.theme.Muted
import com.nbaltas.nickos.ui.theme.NickButton
import com.nbaltas.nickos.ui.theme.NickField
import com.nbaltas.nickos.ui.theme.NickLogo
import com.nbaltas.nickos.ui.theme.OnAvatar
import com.nbaltas.nickos.ui.theme.SetupHeader
import com.nbaltas.nickos.ui.theme.Title
import com.nbaltas.nickos.ui.theme.body
import com.nbaltas.nickos.ui.theme.disp
import com.nbaltas.nickos.ui.theme.tap
import kotlinx.coroutines.launch

private enum class Step { WELCOME, ROLE, NODE_NAME, NODE_CODE, MASTER_NET, MASTER_ADD, MASTER_LINKED, MASTER_JELLYFIN }

/** First run: Welcome → Role → (Node: name → code) | (Master: add tablet → linked → Jellyfin). */
@Composable
fun SetupFlow() {
    val app = App.get(LocalContext.current)
    // A tablet set up from the Master's QR code is already known to be a kid tablet.
    val provisioned = remember { com.nbaltas.nickos.node.Provisioning.provisionedAsNode(app) }
    // A Master that already linked a tablet but was closed before finishing picks up at Jellyfin.
    val resumeMaster = remember { app.store.nodes.value.isNotEmpty() }
    var step by rememberSaveable {
        mutableStateOf(if (provisioned) Step.NODE_NAME else if (resumeMaster) Step.MASTER_JELLYFIN else Step.WELCOME)
    }
    var linked by remember { mutableStateOf<MasterRuntime.PairResult?>(null) }

    val back: (() -> Unit)? = when (step) {
        Step.WELCOME -> null
        Step.ROLE -> { { step = Step.WELCOME } }
        Step.NODE_NAME -> if (provisioned) null else { { step = Step.ROLE } }
        Step.MASTER_NET -> { { step = Step.ROLE } }
        Step.MASTER_ADD -> { { step = Step.ROLE } }
        Step.NODE_CODE -> { { step = Step.NODE_NAME } }
        Step.MASTER_LINKED -> null
        Step.MASTER_JELLYFIN -> { { step = Step.MASTER_LINKED } }
    }
    BackHandler(enabled = back != null) { back?.invoke() }

    Box(Modifier.fillMaxSize().background(C.bg)) {
        when (step) {
            Step.WELCOME -> Welcome { step = Step.ROLE }
            Step.ROLE -> RoleScreen(back!!, onMaster = { app.master.start(); step = Step.MASTER_NET }, onNode = { step = Step.NODE_NAME })
            Step.MASTER_NET -> MasterNetScreen(back!!) { step = Step.MASTER_ADD }
            Step.NODE_NAME -> NodeNameScreen(back) { step = Step.NODE_CODE }
            Step.NODE_CODE -> NodeCodeScreen(back!!) { app.store.setRole(Role.NODE) }
            Step.MASTER_ADD -> AddTabletScreen(back = back!!, step = "Master · Add tablet") { linked = it; step = Step.MASTER_LINKED }
            Step.MASTER_LINKED -> {
                val r = linked
                if (r != null) LinkedScreen(r, onAnother = { step = Step.MASTER_ADD }, onContinue = { step = Step.MASTER_JELLYFIN })
                else LaunchedEffect(Unit) { step = Step.MASTER_ADD }
            }
            Step.MASTER_JELLYFIN -> JellyfinSetupScreen(back!!) { app.store.setRole(Role.MASTER) }
        }
    }
}

@Composable
internal fun Welcome(onStart: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
    // A short landscape tablet (~600dp tall) must show "Get started" without scrolling.
    val short = maxHeight < 700.dp
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(if (short) 24.dp else 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (short) 14.dp else 28.dp, Alignment.CenterVertically),
    ) {
        NickLogo(if (short) 80.dp else 128.dp)
        Text("NickOS", style = disp(if (short) 72.sp else 104.sp, 800, 1f).copy(letterSpacing = (-0.03).em), color = C.text)
        Text(
            "One home screen for every tablet in the family.", style = body(30.sp, 600, 1.25f), color = C.text,
            textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 520.dp),
        )
        Muted("Tablets talk to each other over your home or car WiFi. No internet needed.", 20, Modifier.widthIn(max = 620.dp))
        NickButton("Get started", onStart, Modifier.padding(top = if (short) 6.dp else 20.dp).widthIn(min = 320.dp), BtnStyle.PRIMARY, big = true, trailing = Lucide.ChevronRight)
    }
    }
}

@Composable
internal fun RoleScreen(onBack: () -> Unit, onMaster: () -> Unit, onNode: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        SetupHeader("Step 1 of 3", onBack)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val narrow = maxWidth < 800.dp
            Column(
                Modifier.fillMaxSize().then(if (narrow) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .padding(start = if (narrow) 32.dp else 80.dp, end = if (narrow) 32.dp else 80.dp, top = 8.dp, bottom = 64.dp),
                verticalArrangement = Arrangement.spacedBy(36.dp),
            ) {
                Title("What is this tablet?", 56)
                val cards: @Composable (Modifier) -> Unit = { m ->
                    RoleCard(m, Lucide.Tablet, true, C.accent, "Master", "I control", C.accent,
                        "For the parent. Sees and controls every linked tablet. Great mounted at the front of the car.", "Choose Master", onMaster)
                    RoleCard(m, Lucide.Smartphone, false, Color(0xFFFF7A59), "Node", "Kid's tablet", Color(0xFFFF9B80),
                        "Locked down. Shows only the apps you allow. Controlled from the Master tablet.", "Choose Node", onNode)
                }
                if (narrow) Column(verticalArrangement = Arrangement.spacedBy(24.dp)) { cards(Modifier.fillMaxWidth()) }
                else Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(32.dp)) { cards(Modifier.weight(1f).fillMaxHeight()) }
            }
        }
    }
}

@Composable
private fun RoleCard(
    modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, rotate: Boolean, tile: Color,
    title: String, sub: String, subColor: Color, desc: String, cta: String, onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(32.dp)
    Column(
        modifier.heightIn(min = 440.dp).clip(shape).background(C.surface).border(2.dp, C.line, shape).clickable(onClick = onClick).padding(40.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Box(Modifier.size(112.dp).clip(RoundedCornerShape(32.dp)).background(tile), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = OnAvatar, modifier = Modifier.size(52.dp).then(if (rotate) Modifier.rotate90() else Modifier))
        }
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Title(title, 52)
            Text(sub, style = body(24.sp, 700), color = subColor)
        }
        Muted(desc, 20)
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(cta, style = body(19.sp, 700), color = C.text)
            Icon(Lucide.ChevronRight, null, tint = C.text, modifier = Modifier.size(20.dp))
        }
    }
}

fun Modifier.rotate90() = rotate(90f)

// ---------------- Node ----------------

@Composable
internal fun NodeNameScreen(onBack: (() -> Unit)?, onContinue: () -> Unit) {
    val store = App.get(LocalContext.current).store
    val name by store.name.collectAsState()
    val color by store.color.collectAsState()
    val avatar by store.avatar.collectAsState()
    // The battery exemption dialog has to come before NodeService starts lockdown: a system dialog under lock task is suppressed.
    val ctx = LocalContext.current
    // Asked one after another, each only if still missing: battery, then brightness, then screen off. Android 12+ grants
    // none of them silently, and under kiosk lockdown a system page is suppressed, so they all have to come now.
    var asked by rememberSaveable { mutableIntStateOf(0) }
    lateinit var proceed: () -> Unit
    val asks = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { proceed() }
    proceed = {
        val device = App.get(ctx).node.device
        val exempt = try { (ctx.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(ctx.packageName) } catch (e: Exception) { true }
        val intent = when {
            asked < 1 && !exempt -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).setData(Uri.parse("package:${ctx.packageName}"))
            asked < 2 && !device.brightnessOk -> device.writeSettingsIntent()
            asked < 3 && !device.screenOffOk -> device.deviceAdminIntent()
            else -> null
        }
        if (intent == null) onContinue()
        else {
            asked = when { intent.action == Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS -> 1; intent.action == Settings.ACTION_MANAGE_WRITE_SETTINGS -> 2; else -> 3 }
            try { asks.launch(intent) } catch (e: Exception) { proceed() }
        }
    }
    Column(Modifier.fillMaxSize()) {
        SetupHeader("Node · Step 2 of 3", onBack)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // On a landscape tablet narrower than ~1100dp the 380dp preview left the colour and avatar rows clipped.
            val landscape = maxWidth >= 700.dp && maxWidth > maxHeight
            val compact = landscape && maxWidth < 1100.dp
            val narrow = maxWidth < 900.dp && !landscape
            val preview: @Composable () -> Unit = {
                Column(Modifier.width(if (compact) 280.dp else 380.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    Avatar(name.ifBlank { "?" }, color, avatar, if (narrow) 180.dp else if (compact) 200.dp else 280.dp)
                    Title(name.ifBlank { "Your tablet" }, 34, maxLines = 1)
                    Muted("Preview", 17)
                }
            }
            val form: @Composable (Modifier) -> Unit = { m ->
                Column(m, verticalArrangement = Arrangement.spacedBy(32.dp)) {
                    Title("Name this tablet", 52)
                    Column {
                        FieldLabel("Tablet name")
                        NickField(name, { store.setName(it.take(28)) }, placeholder = "e.g. Hailey's Tablet")
                    }
                    Column {
                        FieldLabel("Colour")
                        Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            AvatarColors.forEachIndexed { i, col ->
                                val sel = i == color
                                Box(
                                    Modifier.size(64.dp).then(if (sel) Modifier.border(3.dp, Color.White, CircleShape).padding(7.dp) else Modifier)
                                        .clip(CircleShape).background(col).tap { store.setColor(i) }
                                )
                            }
                        }
                    }
                    Column {
                        FieldLabel("Avatar")
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            (listOf("initial") + AvatarIcons.keys).forEach { key ->
                                val sel = key == avatar
                                val shape = RoundedCornerShape(20.dp)
                                if (sel) Box(Modifier.tap { store.setAvatar(key) }) { Avatar(name.ifBlank { "?" }, color, key, 64.dp, shape) }
                                else Box(Modifier.size(64.dp).clip(shape).background(C.surface2).tap { store.setAvatar(key) }, contentAlignment = Alignment.Center) {
                                    val icon = AvatarIcons[key]
                                    if (icon != null) Icon(icon, null, tint = C.text, modifier = Modifier.size(28.dp))
                                    else Text(name.trim().firstOrNull()?.uppercase() ?: "A", style = disp(30.sp), color = C.text)
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        NickButton("Continue", proceed, Modifier.widthIn(min = 240.dp), BtnStyle.PRIMARY, big = true, trailing = Lucide.ChevronRight, enabled = name.isNotBlank())
                    }
                }
            }
            if (narrow) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(32.dp)) {
                    preview(); form(Modifier.fillMaxWidth())
                }
            } else {
                Row(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = if (compact) 40.dp else 80.dp, end = if (compact) 40.dp else 80.dp, bottom = if (compact) 24.dp else 56.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 40.dp else 72.dp), verticalAlignment = Alignment.CenterVertically,
                ) { preview(); form(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
internal fun NodeCodeScreen(onBack: () -> Unit, onPaired: () -> Unit) {
    val ctx = LocalContext.current
    val app = App.get(ctx)
    val name by app.store.name.collectAsState()
    val color by app.store.color.collectAsState()
    val avatar by app.store.avatar.collectAsState()
    val code by app.node.pairingCode.collectAsState()
    DisposableEffect(Unit) {
        NodeService.start(ctx)
        app.node.openPairing()
        onDispose { app.node.closePairing() }
    }
    LaunchedEffect(Unit) { app.node.paired.collect { onPaired() } }
    Column(Modifier.fillMaxSize()) {
        SetupHeader("Node · Step 3 of 3", onBack)
        PairingCodeBody(name, color, avatar, code)
    }
}

/** Also shown from the Node's parent menu when pairing a second Master. */
@Composable
fun PairingCodeBody(name: String, color: Int, avatar: String, code: String?) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
    // The code is always on ONE line, so its size comes from the width we have, never from a fixed 168sp.
    val sidePad = if (maxWidth < 900.dp) 20.dp else 40.dp
    val boxPad = if (maxWidth < 900.dp) 28.dp else 56.dp
    // On a short landscape screen the big code would push the instructions off the bottom.
    val codeMax = if (maxHeight < 700.dp) 132 else CODE_MAX_SP
    val availWidth = maxWidth - sidePad * 2 - boxPad * 2
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = sidePad, end = sidePad, bottom = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically),
    ) {
        Chip(height = 56.dp, padding = PaddingValues(start = 8.dp, end = 22.dp)) {
            Avatar(name, color, avatar, 42.dp)
            Text(name, style = body(19.sp, 600), color = C.text)
        }
        Title("Pair with the parent tablet", 48, lineHeight = 1.05f)
        val shape = RoundedCornerShape(36.dp)
        Box(Modifier.clip(shape).background(C.surface).border(2.dp, C.line, shape).padding(horizontal = boxPad, vertical = 28.dp)) {
            val c = code ?: "······"
            val grouped = c.take(3) + "\u200a" + c.drop(3)
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val style = disp(codeMax.sp, 800, 1f).copy(letterSpacing = 0.06.em, fontFeatureSettings = "tnum")
            val fullWidth = measurer.measure(grouped, style, softWrap = false, maxLines = 1).size.width
            val availPx = with(density) { availWidth.toPx() }
            val sp = if (fullWidth <= availPx * 0.96f || fullWidth == 0) codeMax else (codeMax * availPx * 0.96f / fullWidth).toInt().coerceAtLeast(40)
            Text(
                grouped,
                style = style.copy(fontSize = sp.sp, lineHeight = sp.sp),
                color = C.text, maxLines = 1, softWrap = false,
            )
        }
        Text(
            buildAnnotatedString {
                append("On the Master tablet, tap ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Add tablet") }
                append(" and enter this code.")
            },
            style = body(23.sp, 500, 1.4f), color = C.text, textAlign = TextAlign.Center,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Lucide.Wifi, null, tint = C.muted, modifier = Modifier.size(22.dp))
            Text("Waiting for the Master tablet on this WiFi…", style = body(18.sp), color = C.muted)
        }
        val address = remember { wifiAddress() }
        if (address != null) Text("Address: $address", style = body(16.sp, 600), color = C.muted)
    }
    }
}

private const val CODE_MAX_SP = 168

/** This tablet's LAN IPv4, for typing on the Master when the tablets can't find each other. */
private fun wifiAddress(): String? = try {
    java.net.NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.toList() }.firstOrNull { it is java.net.Inet4Address && it.isSiteLocalAddress }?.hostAddress
} catch (e: Exception) { null }

// ---------------- Master ----------------

/** The muted line on the car network page; Android 10 to 12 also needs Location switched on. */
internal fun masterNetMuted(sdk: Int): String =
    "Android asks for one permission to do this. NickOS never uses your location." + if (sdk in 29..32) " Location also has to be switched on." else ""

internal const val MASTER_NET_TITLE = "The car network"
internal const val MASTER_NET_BODY =
    "NickOS runs a small WiFi network from this tablet. In the car, the kids' tablets join it on their own. There is nothing to switch on."

/** Setup, Master: ask for the one permission the car network needs. Skipped when there is nothing to ask. */
@Composable
internal fun MasterNetScreen(onBack: () -> Unit, onNext: () -> Unit) {
    val app = App.get(LocalContext.current)
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        app.master.carNet.recheck(); onNext()
    }
    LaunchedEffect(Unit) { if (app.master.carNet.permissionToRequest().isEmpty()) onNext() }
    Column(Modifier.fillMaxSize()) {
        SetupHeader("Master · Car network", onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 40.dp, end = 40.dp, bottom = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        ) {
            Box(Modifier.size(112.dp).clip(RoundedCornerShape(32.dp)).background(C.accent), contentAlignment = Alignment.Center) {
                Icon(Lucide.Wifi, null, tint = OnAvatar, modifier = Modifier.size(52.dp))
            }
            Title(MASTER_NET_TITLE, 52, lineHeight = 1.05f)
            Text(MASTER_NET_BODY, style = body(23.sp, 500, 1.4f), color = C.text, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 620.dp))
            Text(masterNetMuted(Build.VERSION.SDK_INT), style = body(19.sp, 500, 1.4f), color = C.muted, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 620.dp))
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                NickButton("Allow", {
                    val p = app.master.carNet.permissionToRequest()
                    if (p.isEmpty()) { app.master.carNet.recheck(); onNext() } else ask.launch(p.toTypedArray())
                }, style = BtnStyle.PRIMARY, big = true)
                NickButton("Not now", onNext, style = BtnStyle.GHOST, big = true)
            }
        }
    }
}

/** Enter a Node's 6-digit code. Used in setup, from the home "Add tablet" card and Settings. */
@Composable
fun AddTabletScreen(back: () -> Unit, step: String, onQr: (() -> Unit)? = null, onLinked: (MasterRuntime.PairResult) -> Unit) {
    val app = App.get(LocalContext.current)
    val scope = rememberCoroutineScope()
    var digits by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Typed address for networks where the tablets can't find each other by themselves.
    var typing by rememberSaveable { mutableStateOf(false) }
    var address by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { app.master.start() }

    fun submit(code: String) {
        busy = true; error = null
        scope.launch {
            val r = app.master.pair(code, address)
            busy = false
            if (r != null) onLinked(r) else { error = "No tablet on this WiFi is showing $code"; digits = "" }
        }
    }

    Column(Modifier.fillMaxSize()) {
        SetupHeader(step, back)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Landscape tablets (~960x600) are wide but short: put the pad beside the text and shrink it, instead of
            // stacking it below the fold.
            val landscape = maxWidth >= 700.dp && maxWidth > maxHeight
            val compact = landscape && (maxWidth < 1000.dp || maxHeight < 700.dp)
            val narrow = maxWidth < 1000.dp && !landscape
            val intro: @Composable (Modifier) -> Unit = { m ->
                Column(m, verticalArrangement = Arrangement.spacedBy(22.dp)) {
                    Title("Add tablet", 56)
                    Muted("Enter the 6-digit code showing on the kid's tablet. Both tablets need to be on the same WiFi.", 22, Modifier.widthIn(max = 460.dp))
                    if (onQr != null) Text(
                        "Brand-new or reset tablet? Set it up with a QR code",
                        style = body(19.sp, 700), color = C.accent, modifier = Modifier.clickable(onClick = onQr),
                    )
                    if (typing) Column {
                        FieldLabel("Kid's tablet address (shown under its code)")
                        NickField(address, { address = it.trim() }, Modifier.widthIn(max = 460.dp), keyboard = androidx.compose.ui.text.input.KeyboardType.Uri, placeholder = "192.168.1.23")
                    } else Text(
                        "Tablet not found? Type its address",
                        style = body(19.sp, 700), color = C.accent, modifier = Modifier.clickable { typing = true },
                    )
                    if (!narrow && !compact) {
                        Column(
                            Modifier.padding(top = 20.dp).size(300.dp, 200.dp).border(3.dp, C.line, RoundedCornerShape(28.dp)),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
                        ) {
                            Text("ON THE KID'S TABLET", style = body(14.sp, 700), color = C.muted)
                            Text("482 916", style = disp(48.sp, 800).copy(letterSpacing = 0.06.em), color = C.muted)
                        }
                    }
                }
            }
            // Phones: six 76dp boxes don't fit in ~410dp, so shrink them.
            val phone = maxWidth < 600.dp
            val pad: @Composable () -> Unit = {
                // 6 boxes of 76 + gaps = 526dp.
                Column(Modifier.widthIn(max = if (compact) 440.dp else 540.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(horizontalArrangement = Arrangement.spacedBy(if (phone || compact) 8.dp else 12.dp)) {
                        (0 until 6).forEach { i ->
                            if (i == 3) Spacer(Modifier.width(if (phone) 4.dp else 10.dp))
                            val active = i == digits.length && !busy
                            Box(
                                Modifier.size(if (phone) 48.dp else if (compact) 60.dp else 76.dp, if (phone) 68.dp else if (compact) 80.dp else 100.dp).clip(RoundedCornerShape(20.dp)).background(C.surface)
                                    .border(3.dp, if (active) C.accent else C.line, RoundedCornerShape(20.dp)),
                                contentAlignment = Alignment.Center,
                            ) { Text(digits.getOrNull(i)?.toString() ?: "", style = disp(if (phone) 36.sp else 56.sp), color = C.text) }
                        }
                    }
                    Box(Modifier.height(if (compact) 28.dp else 32.dp), contentAlignment = Alignment.Center) {
                        when {
                            busy -> Text("Looking for the tablet…", style = body(16.sp, 600), color = C.muted)
                            error != null -> Text(error!!, style = body(16.sp, 600), color = C.danger)
                        }
                    }
                    Keypad(
                        onDigit = { d -> if (!busy && digits.length < 6) { digits += d; error = null; if (digits.length == 6) submit(digits) } },
                        onDelete = { if (!busy) digits = digits.dropLast(1) },
                        modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                        keyHeight = if (compact) 58.dp else 76.dp,
                        gap = if (compact) 10.dp else 14.dp,
                    )
                }
            }
            if (narrow) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(32.dp)) {
                    intro(Modifier.fillMaxWidth()); pad()
                }
            } else {
                Row(
                    Modifier.fillMaxSize().padding(start = if (compact) 40.dp else 80.dp, end = if (compact) 40.dp else 80.dp, bottom = if (compact) 16.dp else 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 40.dp else 72.dp), verticalAlignment = Alignment.CenterVertically,
                ) { intro(Modifier.weight(1f).verticalScroll(rememberScrollState())); pad() }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LinkedScreen(r: MasterRuntime.PairResult, onAnother: () -> Unit, onContinue: () -> Unit, continueLabel: String = "Continue") {
    Column(Modifier.fillMaxSize()) {
        SetupHeader("Master · Add tablet", null)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 40.dp, end = 40.dp, bottom = 72.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(30.dp, Alignment.CenterVertically),
        ) {
            Box(Modifier.size(128.dp).clip(CircleShape).background(C.online), contentAlignment = Alignment.Center) {
                Icon(Lucide.Check, null, tint = Color(0xFF0B2616), modifier = Modifier.size(64.dp))
            }
            Title("${r.node.name} is linked", 56, lineHeight = 1f)
            TabletSummaryRow(r.node.name, r.node.color, r.node.avatar, r.status)
            // Wraps to two lines on a phone instead of squashing Continue.
            FlowRow(
                Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                NickButton("Add another tablet", onAnother, style = BtnStyle.GHOST, big = true, leading = Lucide.Plus)
                NickButton(continueLabel, onContinue, Modifier.widthIn(min = 220.dp), BtnStyle.PRIMARY, big = true, trailing = Lucide.ChevronRight)
            }
        }
    }
}

@Composable
private fun TabletSummaryRow(name: String, color: Int, avatar: String, st: NodeStatus) {
    Row(
        Modifier.widthIn(max = 520.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(C.surface).padding(24.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Avatar(name, color, avatar, 64.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Title(name, 28, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Dot(C.online)
                Text("Online · Screen ${if (st.screenOn) "on" else "off"}", style = body(17.sp), color = C.muted)
            }
        }
        Text("${st.battery}%", style = disp(36.sp), color = C.text)
    }
}

@Composable
internal fun JellyfinSetupScreen(onBack: () -> Unit, onDone: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        SetupHeader("Master · Step 3 of 3", onBack)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val narrow = maxWidth < 1000.dp
            val intro: @Composable (Modifier) -> Unit = { m ->
                Column(m, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Box(Modifier.size(96.dp).clip(RoundedCornerShape(28.dp)).background(C.surface2), contentAlignment = Alignment.Center) {
                        Icon(Lucide.Server, null, tint = C.accent, modifier = Modifier.size(44.dp))
                    }
                    Title("Connect Jellyfin (optional, legacy)", 52)
                    Chip(height = 36.dp) { Text("Optional", style = body(15.sp, 600), color = C.text) }
                    Muted("Browse your family's shows and movies from here, and play them on any tablet.", 21)
                }
            }
            val form: @Composable (Modifier) -> Unit = { m ->
                Column(m, verticalArrangement = Arrangement.spacedBy(36.dp)) {
                    JellyfinForm(onSaved = onDone, footer = { save, saving ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End)) {
                            NickButton("Skip for now", onDone, style = BtnStyle.GHOST, big = true)
                            NickButton(if (saving) "Saving…" else "Save & finish", save, style = BtnStyle.PRIMARY, big = true, enabled = !saving)
                        }
                    })
                }
            }
            if (narrow) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(32.dp)) {
                    intro(Modifier.fillMaxWidth()); form(Modifier.fillMaxWidth())
                }
            } else {
                Row(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 80.dp, end = 80.dp, bottom = 56.dp),
                    horizontalArrangement = Arrangement.spacedBy(72.dp), verticalAlignment = Alignment.CenterVertically,
                ) { intro(Modifier.width(420.dp)); form(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * Server address / username / password + Test connection. Shared by setup and Settings.
 * [footer] gets a save action (tests, then stores the account) and whether it's running.
 */
@Composable
fun JellyfinForm(
    onSaved: () -> Unit,
    footer: @Composable (save: () -> Unit, saving: Boolean) -> Unit,
    initial: JellyfinAccount? = null,
) {
    val app = App.get(LocalContext.current)
    val scope = rememberCoroutineScope()
    var url by rememberSaveable { mutableStateOf(initial?.url ?: "http://") }
    var user by rememberSaveable { mutableStateOf(initial?.username ?: "") }
    var pass by rememberSaveable { mutableStateOf("") }
    var result by remember { mutableStateOf<Pair<Boolean, String>?>(if (initial != null) true to "Connected" else null) }
    var busy by remember { mutableIntStateOf(0) } // 1 = testing, 2 = saving

    // Settings shows the saved account without its password; unchanged fields keep the saved token.
    suspend fun login(): JellyfinAccount? = try {
        val acc = if (initial != null && pass.isEmpty() && url == initial.url && user == initial.username) {
            initial.also { Jellyfin(it, app.master.displayName, app.store.deviceId).users() }
        } else Jellyfin.login(url, user, pass, app.master.displayName, app.store.deviceId)
        result = true to "Connected"
        acc
    } catch (e: Exception) { result = false to (e.message?.takeIf { it.length < 60 } ?: "Couldn't connect"); null }

    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Column { FieldLabel("Server address"); NickField(url, { url = it.trim() }, keyboard = androidx.compose.ui.text.input.KeyboardType.Uri) }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) { FieldLabel("Username"); NickField(user, { user = it }) }
            Column(Modifier.weight(1f)) { FieldLabel("Password"); NickField(pass, { pass = it }, password = true, placeholder = if (initial != null) "••••••••" else "") }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            NickButton(if (busy == 1) "Testing…" else "Test connection", { busy = 1; scope.launch { login(); busy = 0 } }, leading = Lucide.RefreshCw, enabled = busy == 0)
            result?.let { (ok, msg) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (ok) Icon(Lucide.Check, null, tint = C.online, modifier = Modifier.size(22.dp))
                    Text(msg, style = body(18.sp, 700), color = if (ok) C.online else C.danger)
                }
            }
        }
    }
    Spacer(Modifier.height(0.dp))
    footer({
        busy = 2
        scope.launch {
            val acc = login()
            busy = 0
            if (acc != null) { app.store.setJellyfin(acc); onSaved() }
        }
    }, busy == 2)
}

