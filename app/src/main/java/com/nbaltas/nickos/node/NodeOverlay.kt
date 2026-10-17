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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.unit.dp
import com.nbaltas.nickos.ui.node.BedtimeToast
import com.nbaltas.nickos.ui.node.FoundMeScreen
import com.nbaltas.nickos.ui.node.LockedScreen
import com.nbaltas.nickos.ui.node.PausedScreen

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
    private var pauseView: View? = null
    private var ringView: View? = null
    @Volatile private var talkView: View? = null
    private var watchView: View? = null

    fun refresh() = main.post { update() }

    private fun update() {
        val locked = store.lockedBy.value != null
        val warn = rt.bedtimeWarning.value != null
        val paused = store.pausedMsg.value != null
        val ringing = rt.ringing.value
        val canOverlay = rt.device.canOverlay()
        if (!canOverlay) {
            hideAll()
            // No window to draw in: bring NickOS to the front, its activity draws the same walls (see NodeApp).
            if ((locked || paused || ringing) && !rt.homeVisible) rt.device.goHome()
            return
        }
        val toast = warn && !locked && !rt.homeVisible // Over other apps only: the home screen draws its own opaque toast (see NodeApp).
        // Talk always uses an attached window, including on home, so playback can confirm presentation.
        val talking = com.nbaltas.nickos.talk.TalkBannerPolicy.overlayShows(rt.talkBanner.value != null, locked, rt.homeVisible)
        // "Parent is watching" sits at the top of the screen and above every other window, so nothing ever hides it.
        val watching = rt.screen.watching.value
        // Stacking, bottom to top: lock, pause, bedtime toast, "Found me!", the talking banner, "Parent is watching". A new
        // window is always added last, so when a lower one has to appear, every one above it is taken down and put back after it.
        val wanted = booleanArrayOf(locked, paused, toast, ringing, talking, watching)
        val views = arrayOf(lockView, pauseView, toastView, ringView, talkView, watchView)
        val lowestNew = wanted.indices.firstOrNull { wanted[it] && views[it] == null }
        for (i in wanted.indices) {
            if (views[i] != null && (!wanted[i] || (lowestNew != null && i > lowestNew))) { remove(views[i]); views[i] = null }
        }
        for (i in wanted.indices) {
            if (wanted[i] && views[i] == null) views[i] = when (i) {
                0 -> add(fullScreen = true) { LockedOverlayContent(store) }
                1 -> add(fullScreen = true) { PausedOverlayContent(store) }
                2 -> add(fullScreen = false) { ToastOverlayContent(rt) }
                3 -> add(fullScreen = true) { FoundMeScreen { rt.stopRing(RingMachine.Stop.TAP) } }
                // Only a banner that was added without an exception counts as shown (see talkOnScreen / watchOnScreen).
                4 -> try { add(fullScreen = false) { TalkOverlayContent(rt) } } catch (e: Exception) { android.util.Log.w("NickOS.Talk", "talk banner", e); null }
                else -> try { add(fullScreen = false, atTop = true) { WatchOverlayContent() } } catch (e: Exception) { android.util.Log.w("NickOS.Screen", "watch pill", e); null }
            }
        }
        lockView = views[0]; pauseView = views[1]; toastView = views[2]; ringView = views[3]; talkView = views[4]; watchView = views[5]
    }

    /**
     * Brings the overlay up to date on the main thread and waits (a short while) for it. True only when "Parent is
     * talking" really is on screen afterwards: the banner window is attached.
     */
    fun refreshTalkAndWait(): Boolean {
        if (Looper.myLooper() == Looper.getMainLooper()) { update(); return talkOnScreen() }
        val done = java.util.concurrent.CountDownLatch(1)
        main.post { try { update() } catch (e: Exception) { android.util.Log.w("NickOS.Talk", "overlay", e) } finally { done.countDown() } }
        return try { done.await(TALK_WAIT_MS, java.util.concurrent.TimeUnit.MILLISECONDS) && talkOnScreen() } catch (e: InterruptedException) { false }
    }

    /** Brings the overlay up to date on the main thread (and waits for it from another). True when "Parent is watching" really is on screen. */
    fun refreshWatchAndWait(): Boolean {
        if (Looper.myLooper() == Looper.getMainLooper()) { update(); return watchOnScreen() }
        val done = java.util.concurrent.CountDownLatch(1)
        main.post { try { update() } catch (e: Exception) { android.util.Log.w("NickOS.Screen", "overlay", e) } finally { done.countDown() } }
        return try { done.await(TALK_WAIT_MS, java.util.concurrent.TimeUnit.MILLISECONDS) && watchOnScreen() } catch (e: InterruptedException) { false }
    }

    /** The pill was added without an exception and its window is attached (or at least has its parent). */
    fun watchOnScreen(): Boolean {
        val v = watchView ?: return false
        return v.isAttachedToWindow || v.parent != null
    }

    fun talkOnScreen(): Boolean {
        val v = talkView
        return talkBannerShown(v != null, v?.isAttachedToWindow == true, v?.parent != null)
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
        remove(pauseView); pauseView = null
        remove(ringView); ringView = null
        remove(toastView); toastView = null
        remove(talkView); talkView = null
        remove(watchView); watchView = null
    }

    private fun add(fullScreen: Boolean, atTop: Boolean = false, content: @androidx.compose.runtime.Composable () -> Unit): View {
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
        ).apply { gravity = if (fullScreen || atTop) Gravity.TOP else Gravity.BOTTOM }
        wm.addView(view, lp)
        owner.resume()
        return view
    }

    private companion object { const val GUARD_WAIT_MS = 1500L; const val TALK_WAIT_MS = 500L }

    private fun remove(v: View?) {
        if (v != null) try { wm.removeView(v) } catch (_: Exception) {}
    }
}

/**
 * The talking banner counts as shown once its window was added and is still added. Attachment happens on a later
 * traversal, but WindowManager.addView assigns the parent synchronously.
 */
internal fun talkBannerShown(added: Boolean, attached: Boolean, hasParent: Boolean): Boolean = added && (attached || hasParent)

@androidx.compose.runtime.Composable
private fun LockedOverlayContent(store: Store) {
    val by by store.lockedBy.collectAsStateWithLifecycle()
    LockedScreen(store, by)
}

@androidx.compose.runtime.Composable
private fun PausedOverlayContent(store: Store) {
    val msg by store.pausedMsg.collectAsStateWithLifecycle()
    val by by store.pausedBy.collectAsStateWithLifecycle()
    msg?.let { PausedScreen(it, by) }
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

@androidx.compose.runtime.Composable
private fun WatchOverlayContent() {
    androidx.compose.foundation.layout.Box(
        androidx.compose.ui.Modifier
            .fillMaxWidth()
            .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.statusBars)
            .padding(top = 12.dp),
        contentAlignment = androidx.compose.ui.Alignment.TopCenter,
    ) { com.nbaltas.nickos.ui.node.WatchPill() }
}

private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry

    init { saved.performRestore(null) }

    fun resume() { registry.currentState = Lifecycle.State.RESUMED }
}
