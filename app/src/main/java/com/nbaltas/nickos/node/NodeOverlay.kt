package com.nbaltas.nickos.node

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.nbaltas.nickos.data.Store
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.unit.dp
import com.nbaltas.nickos.ui.node.BedtimeToast
import com.nbaltas.nickos.ui.node.LockedScreen

/**
 * Draws the "Screen locked" wall and the bedtime warning on top of whatever app the kid is in.
 * Needs the "display over other apps" permission (granted by the SETUP.md adb step). Without it,
 * locking brings NickOS to the front instead and the Node activity draws the same screens.
 */
class NodeOverlay(private val ctx: Context, private val store: Store, private val rt: NodeRuntime) {
    private val main = Handler(Looper.getMainLooper())
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private var lockView: View? = null
    private var toastView: View? = null
    private var guardView: View? = null
    private var talkView: View? = null

    fun refresh() = main.post { update() }

    private fun update() {
        val locked = store.lockedBy.value != null
        val warn = rt.bedtimeWarning.value != null
        val canOverlay = rt.device.canOverlay()
        if (!canOverlay) {
            hideAll()
            if (locked && !rt.homeVisible) rt.device.goHome()
            return
        }
        if (locked && lockView == null) {
            lockView = add(fullScreen = true) { LockedOverlayContent(store) }
            // The talking banner is added after the wall so it stays on top of it.
            if (talkView != null) { remove(talkView); talkView = null }
        }
        if (!locked && lockView != null) { remove(lockView); lockView = null }
        // Over other apps only: the home screen draws its own opaque toast (see NodeApp).
        val toast = warn && !locked && !rt.homeVisible
        if (toast && toastView == null) toastView = add(fullScreen = false) { ToastOverlayContent(rt) }
        if (!toast && toastView != null) { remove(toastView); toastView = null }
        // Same rule for "Dad is talking": over other apps here, on the home screen it is drawn by NodeApp. Also over the
        // locked wall, because the speech is still audible.
        val talking = com.nbaltas.nickos.talk.TalkBannerPolicy.overlayShows(rt.talkBanner.value != null, locked, rt.homeVisible)
        if (talking && talkView == null) talkView = add(fullScreen = false) { TalkOverlayContent(rt) }
        if (!talking && talkView != null) { remove(talkView); talkView = null }
    }

    /**
     * The landscape lock for other apps: a 1 pixel window nobody can see or touch that asks Android for sensor
     * landscape (both directions). The foreground app inherits the request. Safe to call again with the same value.
     * The window is added on the main thread and this waits (a short while) for the answer: true only when the window
     * is really there (on) or gone (off).
     */
    fun landscapeGuard(on: Boolean): Boolean {
        if (Looper.myLooper() == Looper.getMainLooper()) return guard(on)
        val done = java.util.concurrent.CountDownLatch(1)
        var ok = false
        main.post { try { ok = guard(on) } finally { done.countDown() } }
        return try { done.await(GUARD_WAIT_MS, java.util.concurrent.TimeUnit.MILLISECONDS) && ok } catch (e: InterruptedException) { false }
    }

    private fun guard(on: Boolean): Boolean {
        if (on && guardView == null) {
            val v = View(ctx)
            val lp = WindowManager.LayoutParams(
                1, 1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                screenOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
            try { wm.addView(v, lp); guardView = v } catch (e: Exception) { android.util.Log.w("NickOS.Rotation", "landscape guard", e) }
        }
        if (!on && guardView != null) { remove(guardView); guardView = null }
        return (guardView != null) == on
    }

    fun hideAll() = main.post {
        remove(lockView); lockView = null
        remove(toastView); toastView = null
        remove(talkView); talkView = null
    }

    private fun add(fullScreen: Boolean, content: @androidx.compose.runtime.Composable () -> Unit): View {
        val owner = OverlayOwner()
        val view = ComposeView(ctx).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent(content)
        }
        val flags = if (fullScreen) {
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            if (fullScreen) WindowManager.LayoutParams.MATCH_PARENT else WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = if (fullScreen) Gravity.TOP else Gravity.BOTTOM }
        wm.addView(view, lp)
        owner.resume()
        return view
    }

    private companion object { const val GUARD_WAIT_MS = 1500L }

    private fun remove(v: View?) {
        if (v != null) try { wm.removeView(v) } catch (_: Exception) {}
    }
}

@androidx.compose.runtime.Composable
private fun LockedOverlayContent(store: Store) {
    val by by store.lockedBy.collectAsStateWithLifecycle()
    LockedScreen(store, by)
}

@androidx.compose.runtime.Composable
private fun ToastOverlayContent(rt: NodeRuntime) {
    val left by rt.bedtimeWarning.collectAsStateWithLifecycle()
    left?.let {
        androidx.compose.foundation.layout.Box(
            androidx.compose.ui.Modifier
                .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.navigationBars)
                .padding(bottom = 28.dp),
        ) { BedtimeToast(it) }
    }
}

@androidx.compose.runtime.Composable
private fun TalkOverlayContent(rt: NodeRuntime) {
    val text by rt.talkBanner.collectAsStateWithLifecycle()
    text?.let {
        androidx.compose.foundation.layout.Box(
            androidx.compose.ui.Modifier
                .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.navigationBars)
                .padding(bottom = 28.dp),
        ) { com.nbaltas.nickos.ui.node.TalkBanner(it) }
    }
}

private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry

    init { saved.performRestore(null) }

    fun resume() { registry.currentState = Lifecycle.State.RESUMED }
}
