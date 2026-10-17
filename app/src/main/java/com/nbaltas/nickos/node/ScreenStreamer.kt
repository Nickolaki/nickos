package com.nbaltas.nickos.node

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.nbaltas.nickos.R
import com.nbaltas.nickos.cam.CamMessages
import com.nbaltas.nickos.data.Store
import com.nbaltas.nickos.remote.FrameGate
import com.nbaltas.nickos.remote.RemoteMessages
import com.nbaltas.nickos.remote.ScreenConsent
import com.nbaltas.nickos.remote.ScreenSize
import com.nbaltas.nickos.remote.TouchLimiter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap

/** The real size of the screen in its current rotation, system bars included. */
fun realDisplaySize(ctx: Context): Pair<Int, Int> {
    val wm = ctx.getSystemService(WindowManager::class.java)
    if (Build.VERSION.SDK_INT >= 30) {
        val b = wm.maximumWindowMetrics.bounds
        return b.width() to b.height()
    }
    val m = DisplayMetrics()
    @Suppress("DEPRECATION")
    wm.defaultDisplay.getRealMetrics(m)
    return m.widthPixels to m.heightPixels
}

/**
 * The kid tablet's screen, streamed to a parent as JPEGs through a [CamServer] of its own. The grown-up allows screen
 * capture once on this tablet (the grown-up menu). The grant is kept until a reboot, unlink, reset, or the system or the
 * grown-up ends it, because Android 14 and up ask again for every new grant.
 *
 * Pictures are made only while a parent is reading, at most 8 a second. The stream turns itself off when nobody reads
 * for a few seconds, when the last parent's link ends, and after an hour; the grant stays.
 *
 * Everything but [touch] and [stateJson] happens on the main thread, where the capture is set up.
 */
class ScreenStreamer(
    private val ctx: Context,
    private val rt: NodeRuntime,
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
) {
    private val main = Handler(Looper.getMainLooper())
    private val power = ctx.getSystemService(PowerManager::class.java)
    private val consent = ScreenConsent()
    private val notice = ScreenNotice(ctx)

    /** Masters that asked to watch and have not stopped. A touch is only taken from one of these, and only while live. */
    private val viewers = ConcurrentHashMap.newKeySet<String>()

    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var thread: HandlerThread? = null
    private var worker: Handler? = null
    private var size = 0 to 0
    private var wake: PowerManager.WakeLock? = null
    private var wifi: WifiManager.WifiLock? = null

    @Volatile private var server: CamServer? = null
    @Volatile private var gate = FrameGate(clock = clock)
    @Volatile private var sawFrame = false
    private var gen = 0
    private var kickedFor = 0

    /** The state and the address told to the Masters. Read from socket threads. */
    @Volatile private var port = 0
    @Volatile private var key: String? = null
    @Volatile private var wire = RemoteMessages.OFF

    private val _holding = MutableStateFlow(false)
    /** True while a screen capture grant is held: the grown-up menu row offers to turn it off. */
    val holding: StateFlow<Boolean> = _holding

    private val _watching = MutableStateFlow(false)
    /** True while the screen goes to a parent: the "Parent is watching" pill. */
    val watching: StateFlow<Boolean> = _watching

    /** The `screen` field of the status. */
    fun statusWire(): String = wire

    /** The `control` field of the status. */
    fun controlWire(): String = if (RemoteInputService.running) RemoteMessages.CONTROL_READY else RemoteMessages.CONTROL_OFF

    fun stateJson(): JSONObject = RemoteMessages.encodeState(RemoteMessages.State(wire, controlWire(), port, key))

    private fun announce() { rt.broadcastScreenState() }

    // ---- the grown-up allows it on this tablet ----

    /** The result of the system's capture dialog. A "no" changes nothing. */
    fun grant(resultCode: Int, data: Intent?) { main.post { grantNow(resultCode, data) } }

    private fun grantNow(resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK || data == null || projection != null) return
        // The type must be on before the projection is taken, or Android 14 refuses it.
        if (!NodeService.projection(true)) { NodeService.projection(false); Log.w(TAG, "no service type for the capture"); return }
        val p = try {
            ctx.getSystemService(MediaProjectionManager::class.java).getMediaProjection(resultCode, data)
        } catch (e: Exception) { Log.w(TAG, "capture refused", e); null }
        if (p == null) { NodeService.projection(false); return }
        // Registered before the display exists: Android 14 throws otherwise.
        p.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { main.post { onSystemStop(p) } }
        }, main)
        projection = p
        size = ScreenSize.capture(realDisplaySize(ctx).first, realDisplaySize(ctx).second)
        val t = HandlerThread("nickos-screen").also { it.start() }
        thread = t
        worker = Handler(t.looper)
        try {
            val r = newReader(size)
            reader = r
            // No surface until a parent watches: the capture costs nothing while nobody does.
            display = p.createVirtualDisplay(
                "nickos-screen", size.first, size.second, ctx.resources.displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, null, null, null,
            )
        } catch (e: Exception) {
            Log.w(TAG, "no virtual display", e)
            drop(RemoteMessages.OFF)
            return
        }
        val now = consent.grant()
        _holding.value = true
        if (now == RemoteMessages.LIVE) beginLive() else setWire(now)
        announce()
    }

    /** The system or the user ended the capture. */
    private fun onSystemStop(p: MediaProjection) {
        if (projection !== p) return
        Log.i(TAG, "capture ended by the system")
        consent.revoke()
        drop(RemoteMessages.OFF)
    }

    /** Unlink, reset, or the grown-up menu: give the grant back. */
    fun release() { main.post { consent.release(); viewers.clear(); drop(RemoteMessages.OFF) } }

    // ---- a parent asks ----

    fun start(masterId: String) { main.post { viewers += masterId; startNow() } }

    fun stop(masterId: String) { main.post { viewers -= masterId; if (viewers.isEmpty()) stopNow() } }

    /** A Master's link ended. */
    fun masterGone(masterId: String) { main.post { viewers -= masterId; if (viewers.isEmpty()) stopNow() } }

    private fun startNow() {
        val before = consent.state
        val now = consent.start()
        if (now == RemoteMessages.LIVE && before == RemoteMessages.LIVE && server != null) { announce(); return }
        if (now == RemoteMessages.LIVE) beginLive() else setWire(now)
        announce()
    }

    private fun stopNow() {
        val was = consent.state
        val now = consent.stop()
        if (was == RemoteMessages.LIVE) endLive()
        setWire(now)
        announce()
    }

    // ---- streaming ----

    private fun beginLive() {
        val d = display
        val r = reader
        if (d == null || r == null) { consent.release(); setWire(RemoteMessages.OFF); return }
        val run = ++gen
        val k = Store.randomHex(16)
        val s = CamServer(k, onIdle = { main.post { if (gen == run) { viewers.clear(); stopNow() } } }, preferredPort = PORT, clock = clock)
        if (!s.start()) { consent.stop(); setWire(RemoteMessages.READY); return }
        server = s
        key = k
        port = s.port
        gate = FrameGate(clock = clock)
        sawFrame = false
        kickedFor = 0
        holdAwake()
        try { d.setSurface(r.surface) } catch (e: Exception) { Log.w(TAG, "no surface", e) }
        setWire(RemoteMessages.LIVE)
        setWatching(true)
        main.postDelayed(autoOff, CamMessages.MAX_MS)
        main.postDelayed(tick, TICK_MS)
    }

    /** Ends the stream and keeps the grant. */
    private fun endLive() {
        gen++
        main.removeCallbacks(autoOff)
        main.removeCallbacks(tick)
        try { display?.setSurface(null) } catch (_: Exception) {}
        server?.stop()
        server = null
        port = 0
        key = null
        letSleep()
        setWatching(false)
    }

    private val autoOff = Runnable { viewers.clear(); stopNow() }

    /** Every second while live: a screen that does not change makes no new picture, so a parent who has just joined gets one pushed. */
    private val tick = object : Runnable {
        override fun run() {
            val s = server ?: return
            val n = s.clientCount
            if (n > kickedFor) kick()
            kickedFor = n
            main.postDelayed(this, TICK_MS)
        }
    }

    /** Asks the capture for a fresh picture even though nothing on screen moved. */
    private fun kick() {
        val d = display ?: return
        val r = reader ?: return
        try { d.setSurface(null); d.setSurface(r.surface) } catch (e: Exception) { Log.w(TAG, "kick", e) }
    }

    private fun newReader(sz: Pair<Int, Int>): ImageReader {
        val r = ImageReader.newInstance(sz.first, sz.second, PixelFormat.RGBA_8888, 2)
        r.setOnImageAvailableListener({ onImage(it) }, worker)
        return r
    }

    private val jpeg = ByteArrayOutputStream(128 * 1024)
    private var lastCheck = 0L

    /** Runs on the capture thread. Every path closes the image, or the capture stops sending. */
    private fun onImage(r: ImageReader) {
        val img = try { r.acquireLatestImage() } catch (e: Exception) { null } ?: return
        try {
            val out = server ?: return
            if (!gate.shouldEncode(out.clientCount)) return
            val plane = img.planes[0]
            val px = plane.pixelStride
            val padded = Bitmap.createBitmap(plane.rowStride / px, img.height, Bitmap.Config.ARGB_8888)
            padded.copyPixelsFromBuffer(plane.buffer)
            val bmp = if (padded.width == img.width) padded else Bitmap.createBitmap(padded, 0, 0, img.width, img.height).also { padded.recycle() }
            jpeg.reset()
            bmp.compress(Bitmap.CompressFormat.JPEG, QUALITY, jpeg)
            bmp.recycle()
            out.push(jpeg.toByteArray(), 0)
            if (!sawFrame) sawFrame = true
            val now = clock()
            if (now - lastCheck > 1000) { lastCheck = now; main.post { checkRotation() } }
        } catch (e: Throwable) {
            Log.w(TAG, "frame dropped", e)
        } finally {
            img.close()
        }
    }

    /** The tablet turned: the picture must follow, with the same grant (a second display would need a new one). */
    private fun checkRotation() {
        val d = display ?: return
        val now = ScreenSize.capture(realDisplaySize(ctx).first, realDisplaySize(ctx).second)
        if (now == size || server == null) return
        try {
            val old = reader
            val r = newReader(now)
            reader = r
            size = now
            d.resize(now.first, now.second, ctx.resources.displayMetrics.densityDpi)
            d.setSurface(r.surface)
            old?.close()
        } catch (e: Exception) { Log.w(TAG, "resize", e) }
    }

    // ---- touches ----

    private val limiter = TouchLimiter(clock = clock)

    /**
     * A Master's touch. Taken only while this tablet is sharing with that Master, at most 20 a second, and (for taps and
     * swipes) only when the remote input service is switched on. Anything else is dropped without a word. A touch is an
     * ordinary touch: the kid lock and NickOS's own watch still apply to wherever it lands.
     */
    fun touch(masterId: String, t: RemoteMessages.Touch) {
        if (wire != RemoteMessages.LIVE || masterId !in viewers) return
        if (!limiter.allow()) return
        when (t) {
            RemoteMessages.Touch.Home -> rt.device.goHome() // NickOS's home, not whatever launcher the system would pick
            is RemoteMessages.Touch.Tap -> RemoteInputService.instance?.tap(t.x, t.y)
            is RemoteMessages.Touch.Swipe -> RemoteInputService.instance?.swipe(t.x1, t.y1, t.x2, t.y2, t.ms)
            RemoteMessages.Touch.Back -> RemoteInputService.instance?.back()
        }
    }

    // ---- the pill ----

    private fun setWatching(on: Boolean) {
        if (_watching.value == on) return
        _watching.value = on
        // The pill is a window over other apps. Without that permission a notification says the same.
        if (rt.device.canOverlay()) { notice.clear(); rt.overlay.refresh() }
        else if (on) notice.show("Parent is watching") else notice.clear()
        if (!on) rt.overlay.refresh()
    }

    // ---- bookkeeping ----

    private fun setWire(s: String) { wire = s }

    /** Everything down: the grant is gone. */
    private fun drop(to: String) {
        endLive()
        try { display?.release() } catch (_: Exception) {}
        display = null
        try { reader?.close() } catch (_: Exception) {}
        reader = null
        try { thread?.quitSafely() } catch (_: Exception) {}
        thread = null
        worker = null
        val p = projection
        projection = null
        try { p?.stop() } catch (_: Exception) {}
        NodeService.projection(false)
        _holding.value = false
        setWire(to)
        announce()
    }

    /** The kid's screen may be off while the parent looks: keep the processor and the WiFi up, never without a time limit. */
    private fun holdAwake() {
        try {
            wake = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "nickos:screen").apply {
                setReferenceCounted(false)
                acquire(CamMessages.MAX_MS + 60_000L)
            }
        } catch (e: Exception) { Log.w(TAG, "no wake lock", e) }
        try {
            @Suppress("DEPRECATION")
            wifi = (ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
                .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "nickos:screen").apply {
                    setReferenceCounted(false)
                    acquire()
                }
        } catch (e: Exception) { Log.w(TAG, "no wifi lock", e) }
    }

    private fun letSleep() {
        try { wake?.takeIf { it.isHeld }?.release() } catch (_: Exception) {}
        try { wifi?.takeIf { it.isHeld }?.release() } catch (_: Exception) {}
        wake = null
        wifi = null
    }

    companion object {
        private const val TAG = "NickOS.Screen"
        const val PORT = 47473
        private const val QUALITY = 70
        private const val TICK_MS = 1_000L

        /** The system dialog that asks a grown-up. From Android 14 it is limited to the whole screen, not one app. */
        fun consentIntent(ctx: Context): Intent {
            val mpm = ctx.getSystemService(MediaProjectionManager::class.java)
            return if (Build.VERSION.SDK_INT >= 34) {
                mpm.createScreenCaptureIntent(android.media.projection.MediaProjectionConfig.createConfigForDefaultDisplay())
            } else mpm.createScreenCaptureIntent()
        }
    }
}

/** The fallback for "Parent is watching" when the pill cannot be drawn over the kid's app: a notification for as long as it streams. */
class ScreenNotice(private val ctx: Context) {
    private val nm = ctx.getSystemService(NotificationManager::class.java)

    fun show(text: String) {
        try {
            if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, "Parent watching", NotificationManager.IMPORTANCE_HIGH))
            nm.notify(ID, NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build())
        } catch (e: Exception) { Log.w("NickOS.Screen", "notice", e) }
    }

    fun clear() { try { nm.cancel(ID) } catch (_: Exception) {} }

    private companion object {
        const val CHANNEL = "screenwatch"
        const val ID = 3
    }
}
