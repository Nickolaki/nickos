package com.nbaltas.nickos

import android.app.ActivityManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.nbaltas.nickos.data.Role
import com.nbaltas.nickos.node.NodeService
import com.nbaltas.nickos.ui.master.MasterApp
import com.nbaltas.nickos.ui.node.NodeApp
import com.nbaltas.nickos.ui.setup.SetupFlow
import com.nbaltas.nickos.ui.theme.C
import com.nbaltas.nickos.ui.theme.NickTheme
import com.nbaltas.nickos.ui.theme.ThemeMode

class MainActivity : ComponentActivity() {
    private val app get() = App.get(this)
    private val main = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val role by app.store.role.collectAsState()
            val theme by app.store.theme.collectAsState()
            LaunchedEffect(role) { onRoleChanged(role) }
            NickTheme(if (role == Role.MASTER) ThemeMode.valueOf(theme) else ThemeMode.DARK) {
                // Android 15 always draws edge to edge: keep setup and Master screens clear of the
                // status/navigation bars. The kid home hides the bars instead.
                Box(Modifier.fillMaxSize().background(C.bg).then(if (role == Role.NODE) Modifier else Modifier.safeDrawingPadding())) {
                    when (role) {
                        Role.NONE -> SetupFlow()
                        Role.MASTER -> MasterApp()
                        Role.NODE -> NodeApp(
                            onKidMode = { app.node.parentUntil = 0; enterKidMode() },
                            onParentMode = {
                                app.node.parentUntil = System.currentTimeMillis() + PARENT_MINUTES * 60_000L
                                try { stopLockTask() } catch (_: Exception) {}
                            },
                        )
                    }
                }
            }
        }
    }

    private fun onRoleChanged(role: Role) {
        when (role) {
            Role.MASTER -> { app.master.start(); showSystemBars(true) }
            Role.NODE -> { NodeService.start(this); enterKidMode() }
            Role.NONE -> showSystemBars(true)
        }
    }

    override fun onResume() {
        super.onResume()
        if (app.store.role.value == Role.NODE) {
            app.node.homeVisible = true
            app.node.device.lastLaunched = null
            app.node.pushStatus()
            enterKidMode()
        }
    }

    override fun onPause() {
        super.onPause()
        if (app.store.role.value == Role.NODE) {
            app.node.homeVisible = false
            app.node.pushStatus()
        }
    }

    /**
     * Kiosk: lock task when this app is Device Owner, full screen, and straight back into the
     * "locked to" app if the parent set one. Paused while the parent PIN window is open.
     */
    private fun enterKidMode() {
        if (app.store.role.value != Role.NODE || app.node.parentMode) return
        showSystemBars(false)
        app.node.applyLockdown()
        val am = getSystemService(ActivityManager::class.java)
        if (app.node.device.isDeviceOwner && am.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE) {
            try { startLockTask() } catch (_: Exception) {}
        }
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
    }

    companion object { const val PARENT_MINUTES = 10 }
}
