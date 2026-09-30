package com.nbaltas.nickos.ui.node

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.nbaltas.nickos.App
import com.nbaltas.nickos.node.NodeService
import com.nbaltas.nickos.ui.common.AppDrawer
import com.nbaltas.nickos.ui.setup.PairingCodeBody
import com.nbaltas.nickos.ui.theme.C
import com.nbaltas.nickos.ui.theme.SetupHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class NodeScreen { HOME, PIN, MENU, DRAWER, PAIR }

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
    val pin by store.pin.collectAsState()
    var screen by remember { mutableStateOf(NodeScreen.HOME) }
    val scope = rememberCoroutineScope()

    BackHandler { if (screen != NodeScreen.HOME && screen != NodeScreen.MENU) screen = NodeScreen.HOME }

    val jfPkg = remember { rt.device.jellyfinPackage() }
    val apps by produceState(emptyList<HomeApp>(), allowed) {
        value = withContext(Dispatchers.IO) { allowed.mapNotNull { loadHomeApp(ctx, it) } }
    }
    val jellyfin = apps.firstOrNull { it.pkg == jfPkg }
    val others = apps.filterNot { it.pkg == jfPkg }

    val canOverlay = remember(lockedBy, warn) { rt.device.canOverlay() }

    Box(Modifier.fillMaxSize().background(C.bg)) {
        when (screen) {
            NodeScreen.DRAWER -> AppDrawer(onBack = { screen = NodeScreen.MENU }, onOpen = { rt.device.launch(it) })
            NodeScreen.PAIR -> {
                val code by rt.pairingCode.collectAsState()
                val name by store.name.collectAsState()
                val color by store.color.collectAsState()
                val avatar by store.avatar.collectAsState()
                LaunchedEffect(Unit) { rt.openPairing(); rt.paired.collect { screen = NodeScreen.HOME; onKidMode() } }
                Column(Modifier.fillMaxSize().background(C.bg)) {
                    SetupHeader("Add a parent tablet", { rt.closePairing(); screen = NodeScreen.MENU })
                    PairingCodeBody(name, color, avatar, code)
                }
            }
            else -> NodeHome(
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
            lockdownOn = rt.device.isDeviceOwner,
            onKidMode = { screen = NodeScreen.HOME; onKidMode() },
            onAllApps = { screen = NodeScreen.DRAWER },
            onSettings = { ctx.startActivity(androidSettingsIntent()) },
            onPairAnother = { screen = NodeScreen.PAIR },
            onUpdate = { scope.launch { com.nbaltas.nickos.update.Updater.updateIfNewer(ctx) } },
            onReset = {
                rt.device.releaseLockdown()
                (ctx as? Activity)?.let { try { it.stopLockTask() } catch (_: Exception) {} }
                NodeService.stop(ctx)
                store.resetAll()
            },
        )

        // Without the overlay permission these draw here instead of over other apps.
        if (!canOverlay) {
            warn?.let { if (lockedBy == null) BedtimeToast(it) }
            if (lockedBy != null) LockedScreen(store, lockedBy)
        }
    }
}
