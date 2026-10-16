package com.nbaltas.nickos.ui.node

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.nbaltas.nickos.App
import com.nbaltas.nickos.data.Store
import com.nbaltas.nickos.node.KioskPolicy
import com.nbaltas.nickos.node.LocalVideos
import com.nbaltas.nickos.node.NodeService
import com.nbaltas.nickos.node.PermStep
import com.nbaltas.nickos.node.PermissionAsk
import com.nbaltas.nickos.node.parentName
import com.nbaltas.nickos.ui.common.AppDrawer
import com.nbaltas.nickos.ui.setup.PairingCodeBody
import com.nbaltas.nickos.ui.theme.C
import com.nbaltas.nickos.ui.theme.SetupHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class NodeScreen { HOME, PIN, MENU, DRAWER, PAIR, KIOSK }

/** Everything the Node activity shows. Kiosk/lock-task itself is handled by MainActivity. */
@Composable
fun NodeApp(onKidMode: () -> Unit, onParentMode: () -> Unit) {
    val ctx = LocalContext.current
    val app = App.get(ctx)
    val store = app.store
    val rt = app.node
    val allowed by store.allowed.collectAsState()
    val masters by store.masters.collectAsState()
    val connected by rt.connected.collectAsState()
    val lockedBy by store.lockedBy.collectAsState()
    val warn by rt.bedtimeWarning.collectAsState()
    val talkBanner by rt.talkBanner.collectAsState()
    val pin by store.pin.collectAsState()
    val playerUi by rt.syncPlayer.ui.collectAsState()
    val player by rt.syncPlayer.player.collectAsState()
    var videosAllowed by remember { mutableStateOf(rt.videos.hasPermission()) }
    val videosLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        videosAllowed = rt.videos.hasPermission()
    }
    // Leaving lock task makes Android start Home again in a new task, so this screen is rebuilt
    // right after the PIN: reopen the parent menu while parent mode is on.
    // A tablet that is not Device Owner and not yet the home screen opens the Kid lock setup first.
    var screen by remember {
        mutableStateOf(
            when {
                rt.parentMode -> NodeScreen.MENU
                KioskPolicy.setupNeeded(rt.device.kioskState()) -> NodeScreen.KIOSK
                else -> NodeScreen.HOME
            },
        )
    }
    val scope = rememberCoroutineScope()
    val kioskStatus = remember(screen) { KioskPolicy.status(rt.device.kioskState()) }

    BackHandler { if (screen != NodeScreen.HOME && screen != NodeScreen.MENU && screen != NodeScreen.KIOSK) screen = NodeScreen.HOME }

    // Re-checked when the allowlist changes: Jellyfin may be installed after NickOS started.
    val jfPkg = remember(allowed) { rt.device.jellyfinPackage() }
    val apps by produceState(emptyList<HomeApp>(), allowed) {
        value = withContext(Dispatchers.IO) { allowed.mapNotNull { loadHomeApp(ctx, it) } }
    }
    val jellyfin = apps.firstOrNull { it.pkg == jfPkg }
    val others = apps.filterNot { it.pkg == jfPkg }

    val canOverlay = remember(lockedBy, warn) { rt.device.canOverlay() }

    // One-time OKs the Master's brightness slider and Off button need on this tablet. Re-read after each page returns.
    var permTick by remember { mutableIntStateOf(0) }
    var onPermResult by remember { mutableStateOf({}) }
    val askPerm = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { permTick++; onPermResult() }
    val brightnessOk = remember(screen, permTick) { rt.device.brightnessOk }
    val screenOffOk = remember(screen, permTick) { rt.device.screenOffOk }
    fun ask(i: android.content.Intent) { try { askPerm.launch(i) } catch (e: Exception) { permTick++ } }

    // The friendly request card. Each system screen is opened at most once per Allow, so declining never loops.
    val masterAsked by rt.permAsk.masterAsked.collectAsState()
    val tried = remember { mutableSetOf<PermStep>() }
    var walking by remember { mutableStateOf(false) }
    val playing = screen == NodeScreen.HOME && playerUi.fid != null
    fun nextPermScreen() {
        val d = rt.device
        when (PermissionAsk.nextStep(d.isDeviceOwner, d.brightnessOk, d.screenOffOk, tried)) {
            PermStep.BRIGHTNESS -> { tried += PermStep.BRIGHTNESS; try { askPerm.launch(d.writeSettingsIntent()) } catch (e: Exception) { nextPermScreen() } }
            PermStep.SCREEN_OFF -> { tried += PermStep.SCREEN_OFF; try { askPerm.launch(d.deviceAdminIntent()) } catch (e: Exception) { nextPermScreen() } }
            null -> { walking = false; permTick++ }
        }
    }
    onPermResult = { if (walking) nextPermScreen() }
    val permWanted = remember(screen, permTick, masterAsked, walking, playing) {
        PermissionAsk.showCard(rt.device.isDeviceOwner, rt.device.brightnessOk, rt.device.screenOffOk, rt.permAsk.declined, playing, masterAsked)
    }
    val showPermCard = !walking && permWanted && (screen == NodeScreen.HOME || screen == NodeScreen.MENU)

    // Tablet cameras: the grown-up menu offers "Allow camera" until the permission is there.
    var cameraOk by remember(screen) { mutableStateOf(rt.device.hasCameraPermission()) }
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { cameraOk = it || rt.device.hasCameraPermission() }

    Box(Modifier.fillMaxSize().background(C.bg)) {
        when (screen) {
            NodeScreen.KIOSK -> KioskSetup(onStart = { screen = NodeScreen.HOME; onKidMode() })
            NodeScreen.DRAWER -> AppDrawer(onBack = { screen = NodeScreen.MENU }, onOpen = { rt.device.launch(it) })
            NodeScreen.PAIR -> {
                val code by rt.pairingCode.collectAsState()
                val name by store.name.collectAsState()
                val color by store.color.collectAsState()
                val avatar by store.avatar.collectAsState()
                LaunchedEffect(Unit) { rt.openPairing(); rt.paired.collect { screen = NodeScreen.HOME; onKidMode() } }
                DisposableEffect(Unit) { onDispose { rt.closePairing() } } // Back or any exit must not leave the code live
                Column(Modifier.fillMaxSize().background(C.bg)) {
                    SetupHeader("Add a parent tablet", { rt.closePairing(); screen = NodeScreen.MENU })
                    PairingCodeBody(name, color, avatar, code)
                }
            }
            else -> if (screen == NodeScreen.HOME && playerUi.fid != null) {
                TogetherScreen(
                    ui = playerUi,
                    player = player,
                    parent = parentName(masters.firstOrNull { it.id in connected }?.name ?: masters.firstOrNull()?.name),
                    onHold = { screen = NodeScreen.PIN },
                )
            } else NodeHome(
                store = store,
                apps = others,
                jellyfin = jellyfin,
                linkedTo = masters.firstOrNull { it.id in connected }?.name ?: masters.firstOrNull()?.name,
                linkedOnline = connected.isNotEmpty(),
                onOpen = { rt.device.launch(it) },
                onWant = { rt.want(it.itemId) },
                onHold = { screen = NodeScreen.PIN },
            )
        }

        if (screen == NodeScreen.PIN) PinPad(pin, onOk = { screen = NodeScreen.MENU; onParentMode() }, onCancel = { screen = NodeScreen.HOME })
        if (screen == NodeScreen.MENU) ParentMenu(
            kiosk = kioskStatus,
            pinIsDefault = pin == Store.DEFAULT_PIN,
            onFinishKiosk = { screen = NodeScreen.KIOSK },
            onKidMode = { screen = NodeScreen.HOME; onKidMode() },
            onAllApps = { screen = NodeScreen.DRAWER },
            onSettings = { ctx.startActivity(androidSettingsIntent()) },
            onPairAnother = { screen = NodeScreen.PAIR },
            onUpdate = { scope.launch { com.nbaltas.nickos.update.Updater.updateIfNewer(ctx) } },
            videosAllowed = videosAllowed,
            onAllowVideos = { videosLauncher.launch(LocalVideos.permissions().toTypedArray()) },
            onReset = {
                scope.launch {
                    // Read before releasing: Device Owner and the home role are gone after.
                    val handBackHome = KioskPolicy.afterReset(rt.device.isDeviceOwner, rt.device.isHome())
                    rt.sayGoodbye()
                    rt.releaseLandscape() // give the tablet's own rotation settings back before the stored copy is wiped
                    rt.device.releaseLockdown()
                    (ctx as? Activity)?.let { try { it.stopLockTask() } catch (_: Exception) {} }
                    NodeService.stop(ctx)
                    store.resetAll()
                    // Android has no API to hand the home role back: without this the Home button opens NickOS Setup forever.
                    if (handBackHome) try {
                        ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_HOME_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: Exception) {}
                }
            },
            onAllowCamera = if (cameraOk) null else ({ askCamera.launch(android.Manifest.permission.CAMERA) }),
            onAllowBrightness = if (brightnessOk) null else ({ ask(rt.device.writeSettingsIntent()) }),
            onAllowScreenOff = if (screenOffOk) null else ({ ask(rt.device.deviceAdminIntent()) }),
        )

        if (showPermCard) PermissionCard(
            onAllow = { rt.permAsk.begin(); tried.clear(); walking = true; nextPermScreen() },
            onNotNow = { rt.permAsk.decline(); permTick++ },
        )

        // The overlay only covers other apps (Android forces pass-through overlays to 80% opacity),
        // so the toast always draws here on the home screen. The wall too without the overlay permission.
        warn?.let { if (lockedBy == null) BedtimeToastAtBottom(it) }
        talkBanner?.let { if (lockedBy == null) TalkBannerAtBottom(it) }
        if (!canOverlay && lockedBy != null) LockedScreen(store, lockedBy)
    }
}
