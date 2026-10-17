package com.nbaltas.nickos.node

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.nbaltas.nickos.App
import com.nbaltas.nickos.BuildConfig
import com.nbaltas.nickos.data.Role
import com.nbaltas.nickos.remote.TouchMap

/**
 * Taps, swipes, Back and Home for a parent who is looking at this tablet's screen. A grown-up switches it on once in
 * Android's accessibility settings; after that it stays on across reboots. It reads no screen content; the one event it hears is a window change, to keep the kid out of Settings:
 * [ScreenStreamer.touch] decides whether a touch may come in at all.
 */
class RemoteInputService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        try {
            val app = App.get(this)
            // Connecting in a parent window or the settings window is a grown-up turning remote control on: remember that.
            if (KioskLock.consentOnConnect(app.node.parentMode, app.node.maintenance)) app.store.setRemoteConsent(true)
            app.node.endMaintenance() // the service is on: the settings window has done its job
        } catch (e: Exception) { Log.d(TAG, "consent not saved", e) }
        tellParents()
    }

    /**
     * Backstop for the kid lock: Settings goes straight home, and on Android 12+ the notification shade is pulled up again
     * (once a second at most). Only window changes are heard, and only the package name is read. Kid tablets in kid mode
     * only, never in a parent window or the settings window.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        try {
            val app = App.get(this)
            val now = System.currentTimeMillis()
            when (KioskLock.bounce(event.packageName?.toString(), app.store.role.value == Role.NODE, app.node.parentMode, app.node.maintenance, Build.VERSION.SDK_INT, now, lastShade)) {
                KioskLock.Bounce.HOME -> app.node.device.goHome()
                KioskLock.Bounce.COLLAPSE -> if (Build.VERSION.SDK_INT >= 31) { lastShade = now; performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE) }
                KioskLock.Bounce.NONE -> {}
            }
        } catch (e: Exception) { Log.d(TAG, "bounce failed", e) }
    }

    private var lastShade = 0L

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) instance = null
        // Switched off while the app lives on and no update is replacing it: the grown-up's choice, so the silent re-enable stops.
        try {
            val installed = if (Build.VERSION.SDK_INT >= 28) packageManager.getPackageInfo(packageName, 0).longVersionCode
            else @Suppress("DEPRECATION") packageManager.getPackageInfo(packageName, 0).versionCode.toLong()
            if (KioskLock.consentClearedOnUnbind(BuildConfig.VERSION_CODE.toLong(), installed)) App.get(this).store.setRemoteConsent(false)
        } catch (e: Exception) { Log.d(TAG, "consent not cleared", e) }
        tellParents()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    /** The parents' screens show "view only" or the Back and Home buttons by this. */
    private fun tellParents() { try { App.get(this).node.broadcastScreenState() } catch (e: Exception) { Log.d(TAG, "no parents to tell", e) } }

    /** [xN] and [yN] are 0..1 of the screen. False when Android did not take the gesture. */
    fun tap(xN: Double, yN: Double): Boolean {
        val (x, y) = pixel(xN, yN)
        return gesture(Path().apply { moveTo(x, y) }, TAP_MS)
    }

    fun swipe(x1N: Double, y1N: Double, x2N: Double, y2N: Double, ms: Long): Boolean {
        val (x1, y1) = pixel(x1N, y1N)
        val (x2, y2) = pixel(x2N, y2N)
        if (x1 == x2 && y1 == y2) return gesture(Path().apply { moveTo(x1, y1) }, TAP_MS)
        return gesture(Path().apply { moveTo(x1, y1); lineTo(x2, y2) }, ms)
    }

    fun back(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)

    fun home(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)

    /** The screen's size right now, so a turned tablet scales the same 0..1 to its new shape. */
    private fun pixel(xN: Double, yN: Double): Pair<Float, Float> {
        val (w, h) = realDisplaySize(this)
        return TouchMap.toPixels(xN, yN, w, h)
    }

    private fun gesture(path: Path, ms: Long): Boolean = try {
        dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, ms.coerceAtLeast(1))).build(), null, null)
    } catch (e: Exception) { Log.w(TAG, "gesture refused", e); false }

    companion object {
        private const val TAG = "NickOS.Remote"
        private const val TAP_MS = 50L

        /** The running service, or null while the grown-up has not switched it on. */
        @Volatile var instance: RemoteInputService? = null
            private set

        val running: Boolean get() = instance != null
    }
}
