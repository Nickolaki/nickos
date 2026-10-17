package com.nbaltas.nickos.node

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.nbaltas.nickos.App
import com.nbaltas.nickos.remote.TouchMap

/**
 * Taps, swipes, Back and Home for a parent who is looking at this tablet's screen. A grown-up switches it on once in
 * Android's accessibility settings; after that it stays on across reboots. It reads no screen content and no events:
 * [ScreenStreamer.touch] decides whether a touch may come in at all.
 */
class RemoteInputService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        tellParents()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) instance = null
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
