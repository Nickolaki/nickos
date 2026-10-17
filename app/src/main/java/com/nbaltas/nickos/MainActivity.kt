package com.nbaltas.nickos

import android.app.ActivityManager
import android.app.KeyguardManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.nbaltas.nickos.data.Role
import com.nbaltas.nickos.master.MasterService
import com.nbaltas.nickos.node.NodeService
import com.nbaltas.nickos.ui.master.MasterApp
import com.nbaltas.nickos.ui.node.NodeApp
import com.nbaltas.nickos.ui.setup.SetupFlow
import com.nbaltas.nickos.ui.theme.C
import com.nbaltas.nickos.ui.theme.NickTheme
import com.nbaltas.nickos.ui.theme.ThemeMode

open class MainActivity : ComponentActivity() {
    private val app get() = App.get(this)
    private val main = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val role by app.store.role.collectAsState()
            val theme by app.store.theme.collectAsState()
            LaunchedEffect(role) { onRoleChanged(role) }
            // Which door this tablet uses follows the role: see EntryFlow. A kid tablet never changes anything here.
            val homeOptIn by app.store.masterHomeOptIn.collectAsState()
            LaunchedEffect(role, homeOptIn) { EntrySwitch.reconcile(this@MainActivity, role, homeOptIn) }
            // The parent's "lock to landscape" for a kid tablet. Both landscape directions; other apps are handled by NodeRuntime.
            val landscape by app.store.landscapeLock.collectAsState()
            LaunchedEffect(landscape, role) {
                requestedOrientation = if (landscape && role == Role.NODE) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            val mode = if (role == Role.MASTER) ThemeMode.valueOf(theme) else ThemeMode.DARK
            val dark = mode == ThemeMode.DARK || (mode == ThemeMode.SYSTEM && isSystemInDarkTheme())
            // Dark status/nav bar icons on the light theme, or they vanish into the background.
            SideEffect {
                WindowInsetsControllerCompat(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark
                }
            }
            NickTheme(mode) {
                // Android 15 always draws edge to edge: keep setup and Master screens clear of the
                // status/navigation bars. The kid home hides the bars instead.
                Box(Modifier.fillMaxSize().background(C.bg).then(if (role == Role.NODE) Modifier else Modifier.safeDrawingPadding())) {
                    when (role) {
                        Role.NONE -> SetupFlow()
                        Role.MASTER -> MasterApp()
                        Role.NODE -> if (!com.nbaltas.nickos.node.NodeSupport.ok()) androidx.compose.material3.Text(com.nbaltas.nickos.node.NodeSupport.REFUSAL_COPY, color = C.text) else NodeApp(
                            onKidMode = { app.node.parentUntil = 0; enterKidMode() },
                            onParentMode = {
                                app.node.parentUntil = System.currentTimeMillis() + PARENT_MINUTES * 60_000L
                                app.node.device.pauseLockdown()
                                try { stopLockTask() } catch (_: Exception) {}
                                // A screen-off (lockNow) leaves the keyguard pending behind lock task;
                                // it would pop up as soon as the parent leaves kid mode.
                                if (android.os.Build.VERSION.SDK_INT >= 26) {
                                    getSystemService(KeyguardManager::class.java).requestDismissKeyguard(this@MainActivity, null)
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    private fun onRoleChanged(role: Role) {
        when (role) {
            Role.MASTER -> { app.master.start(); MasterService.start(this); askForCarNetPermission(); showSystemBars(true) }
            Role.NODE -> {
                if (!com.nbaltas.nickos.node.NodeSupport.ok()) { android.util.Log.w("NickOS", "this Android is too old to be a Node"); return }
                NodeService.start(this); enterKidMode()
            }
            Role.NONE -> { MasterService.stop(this); showSystemBars(true) }
        }
    }

    /** The Wi-Fi Direct group needs one permission (nearby devices, or location on older Android). Asked once per start. */
    private fun askForCarNetPermission() {
        val perms = app.master.carNet.permissionToRequest()
        if (perms.isEmpty() || askedCarNet || !app.store.carNetOn.value) return
        askedCarNet = true
        try { requestPermissions(perms.toTypedArray(), REQ_CARNET) } catch (_: Exception) {}
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_CARNET) app.master.carNet.recheck()
    }

    private var askedCarNet = false

    override fun onResume() {
        super.onResume()
        if (app.store.role.value == Role.MASTER && app.store.masterHomeOptIn.value && isDefaultHome(this) != true) {
            app.store.setMasterHomeOptIn(false)
            EntrySwitch.applyStored(this)
        }
        com.nbaltas.nickos.node.RotationRecovery.retry(this, app.store)
        if (app.store.role.value == Role.NODE && com.nbaltas.nickos.node.NodeSupport.ok()) {
            app.node.homeVisible = true
            app.node.device.lastLaunched = null
            app.node.pushStatus()
            enterKidMode()
        }
    }

    override fun onPause() {
        super.onPause()
        if (app.store.role.value == Role.NODE && com.nbaltas.nickos.node.NodeSupport.ok()) {
            app.node.homeVisible = false
            app.node.pushStatus()
        }
    }

    /**
     * Kiosk: lock task when this app is Device Owner, full screen, and straight back into the
     * "locked to" app if the parent set one. Paused while the parent PIN window is open.
     */
    private fun enterKidMode() {
        if (app.store.role.value != Role.NODE || !com.nbaltas.nickos.node.NodeSupport.ok() || app.node.parentMode) return
        showSystemBars(false)
        app.node.applyLockdown()
        val am = getSystemService(ActivityManager::class.java)
        if (app.node.device.isDeviceOwner && am.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE) {
            try { startLockTask() } catch (_: Exception) {}
        }
        // Lock task start resets the real status bar flags: switch the shade off again afterwards.
        app.node.device.disableStatusBar()
        val locked = app.store.lockedApp.value
        if (locked != null && app.store.lockedBy.value == null) {
            main.postDelayed({ if (app.node.homeVisible && !app.node.parentMode) app.node.device.launch(locked) }, 400)
        }
    }

    private fun showSystemBars(show: Boolean) {
        WindowCompat.setDecorFitsSystemWindows(window, show)
        val c = WindowInsetsControllerCompat(window, window.decorView)
        if (show) c.show(WindowInsetsCompat.Type.systemBars())
        else {
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Home screen: Back never leaves it. Compose BackHandlers still get it first.
        if (onBackPressedDispatcher.hasEnabledCallbacks()) onBackPressedDispatcher.onBackPressed()
        // The parent tablet is a normal app unless it is set as the home screen: Back goes to its own home.
        else if (masterBackLeaves(app.store.role.value, isDefaultHome(this))) moveTaskToBack(true)
    }

    companion object { const val PARENT_MINUTES = 10; private const val REQ_CARNET = 41 }
}
