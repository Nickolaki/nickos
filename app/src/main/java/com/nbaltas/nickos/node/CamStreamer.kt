package com.nbaltas.nickos.node

import android.content.Context
import android.graphics.Bitmap
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.CameraUnavailableException
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import com.nbaltas.nickos.cam.CamMessages
import com.nbaltas.nickos.cam.CamProfile
import com.nbaltas.nickos.cam.NodeCam
import com.nbaltas.nickos.cam.ServiceLifecycle
import com.nbaltas.nickos.cam.ThermalPolicy
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * The kid tablet's front camera, streamed to a Master as JPEGs through a [CamServer]. Video
 * only: no microphone is ever opened. Nothing is drawn on this tablet while it runs.
 *
 * The camera is on only while a Master is reading the stream, and it turns itself off without
 * being told: when nobody reads for a few seconds, when the last Master's link ends, at the
 * auto-off time, when the tablet gets too hot, and when the battery runs low. None of those
 * needs a message from a Master to arrive.
 *
 * [start] and [stop] are safe on any thread; everything else here happens on the main thread,
 * which is where CameraX wants to be bound and unbound.
 */
class CamStreamer(
    private val ctx: Context,
    private val rt: NodeRuntime,
    private val device: NodeDevice,
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
) {
    private val main = Handler(Looper.getMainLooper())
    private val power = ctx.getSystemService(PowerManager::class.java)
    private val thermal = ThermalPolicy()
    private var life = ServiceLifecycle()

    /** Goes up on every start and stop, so a late callback from an earlier run can tell it is late. */
    private var gen = 0
    private var chosen = CamProfile.NORMAL
    private var active = CamProfile.NORMAL
    private var provider: ProcessCameraProvider? = null
    private var analysis: ImageAnalysis? = null
    private var camera: Camera? = null
    private var executor: ExecutorService? = null
    private var server: CamServer? = null
    private var wake: PowerManager.WakeLock? = null
    private var wifi: WifiManager.WifiLock? = null
    /** A PowerManager.OnThermalStatusChangedListener, held untyped because the class is Android 10+. */
    private var thermalListener: Any? = null

    /** The last state told to the Masters. Read from socket threads. */
    @Volatile private var snap = NodeCam(NodeCam.OFF)

    /** `camState` as it stands now, for a Master that has just connected and for every change. */
    fun stateJson(): JSONObject = CamMessages.encodeState(snap.copy(now = clock()))

    /** A Master asked for the camera. If it is already on, the current state (same key) is sent again. */
    fun start(profile: CamProfile, maxMs: Long, key: String) {
        main.post { startNow(profile, maxMs.coerceIn(1_000L, CamMessages.MAX_MS), key) }
    }

    /** Turns the camera off and says why. Does nothing if it is off. */
    fun stop(reason: String) {
        if (Looper.myLooper() == main.looper) stopNow(reason) else main.post { stopNow(reason) }
    }

    /** The runtime is stopping: camera off and the lifecycle ended. */
    fun destroy() {
        stop(NodeCam.STOPPED)
        main.post { life.destroy() }
    }

    private fun announce() { rt.broadcastCamState() }

    /** Says why the camera did not start. [retryAt] is when asking again can work. */
    private fun refuse(reason: String, retryAt: Long? = null) {
        snap = NodeCam(NodeCam.OFF, reason = reason, level = thermal.level.wire, retryAt = retryAt)
        announce()
    }

    private fun startNow(profile: CamProfile, maxMs: Long, key: String) {
        if (snap.on) { announce(); return }
        val now = clock()
        if (!device.hasCameraPermission()) return refuse(NodeCam.PERMISSION)
        if (!thermal.canStart(now)) return refuse(NodeCam.TOO_HOT, thermal.cooldownUntil)
        val (percent, charging) = device.battery()
        if (ThermalPolicy.lowBattery(percent, charging)) return refuse(NodeCam.LOW_BATTERY)
        thermal.reset()
        if (thermal.update(thermalStatus(), device.batteryTemperatureTenths(), now) == ThermalPolicy.Level.TOO_HOT) {
            return refuse(NodeCam.TOO_HOT, thermal.cooldownUntil)
        }
        // Android 14 will not open a camera from a service that has not declared the camera type,
        // and will not grant the type to an app in the background with no exemption.
        if (!NodeService.camera(true)) {
            NodeService.camera(false)
            return refuse(NodeCam.BACKGROUND)
        }
        val run = ++gen
        chosen = profile
        active = ThermalPolicy.profileFor(profile, thermal.level)
        val s = CamServer(key, onIdle = { main.post { if (gen == run) stopNow(NodeCam.IDLE) } }, clock = clock)
        if (!s.start()) {
            NodeService.camera(false)
            return refuse(NodeCam.CAMERA)
        }
        server = s
        holdAwake(maxMs)
        executor = Executors.newSingleThreadExecutor()
        if (life.destroyed) life = ServiceLifecycle()
        life.start()
        snap = NodeCam(
            NodeCam.WARMING, level = thermal.level.wire, port = s.port, key = key,
            profile = active.name, onSince = now, maxMs = maxMs,
        )
        announce()
        // The tablet enforces the auto-off itself; a Master's timer is never relied on.
        main.postDelayed(autoOff, maxMs)
        main.postDelayed(tick, TICK_MS)
        listenThermal()
        try {
            val future = ProcessCameraProvider.getInstance(ctx)
            future.addListener({
                if (gen != run) return@addListener
                try {
                    val p = future.get()
                    provider = p
                    if (!p.hasCamera(SELECTOR)) stopNow(NodeCam.NO_CAMERA) else bind()
                } catch (e: Exception) {
                    Log.w(TAG, "camera did not start", e)
                    stopNow(reasonFor(e))
                }
            }, ContextCompat.getMainExecutor(ctx))
        } catch (e: Exception) {
            Log.w(TAG, "no camera provider", e)
            stopNow(reasonFor(e))
        }
    }

    /** Binds a fresh analysis use case at [active]. Throws what CameraX throws; callers turn that into a state. */
    private fun bind() {
        val p = provider ?: return
        val ex = executor ?: return
        val out = server ?: return
        val a = ImageAnalysis.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(ResolutionStrategy(Size(active.w, active.h), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                    .build()
            )
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
        a.setAnalyzer(ex, Frames(active, out, gen))
        val cam = p.bindToLifecycle(life, SELECTOR, a)
        analysis = a
        camera = cam
        cam.cameraInfo.cameraState.removeObserver(cameraWatch)
        cam.cameraInfo.cameraState.observe(life, cameraWatch)
    }

    /** Turns each camera frame into a JPEG for the server, at the profile's pace. Runs on the analysis thread. */
    private inner class Frames(private val profile: CamProfile, private val out: CamServer, private val run: Int) : ImageAnalysis.Analyzer {
        private val buf = ByteArrayOutputStream(64 * 1024)
        private val gapMs = 1000L / profile.fps
        private var last = 0L
        private var first = true

        override fun analyze(image: ImageProxy) {
            // Every path must close the image, or the camera silently stops sending frames.
            try {
                val t = clock()
                if (t - last < gapMs - 5) return
                last = t
                val bmp = image.toBitmap()
                buf.reset()
                bmp.compress(Bitmap.CompressFormat.JPEG, profile.q, buf)
                bmp.recycle()
                out.push(buf.toByteArray(), image.imageInfo.rotationDegrees)
                if (first) {
                    first = false
                    main.post { onFirstFrame(run) }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "frame dropped", e)
            } finally {
                image.close()
            }
        }
    }

    private fun onFirstFrame(run: Int) {
        if (gen != run || snap.state != NodeCam.WARMING) return
        snap = snap.copy(state = NodeCam.LIVE)
        announce()
    }

    /** A camera that fails after it was bound: another app took it, or the hardware gave up. */
    private val cameraWatch = Observer<CameraState> { st ->
        when (st.error?.code) {
            CameraState.ERROR_CAMERA_IN_USE, CameraState.ERROR_MAX_CAMERAS_IN_USE, CameraState.ERROR_CAMERA_DISABLED -> stopNow(NodeCam.BUSY)
            CameraState.ERROR_CAMERA_FATAL_ERROR, CameraState.ERROR_DO_NOT_DISTURB_MODE_ENABLED -> stopNow(NodeCam.CAMERA)
            else -> {}
        }
    }

    private val autoOff = Runnable { stopNow(NodeCam.AUTO_OFF) }

    /** Every 10 seconds while on: check the heat and the battery, and tell the Masters the camera is still on. */
    private val tick = object : Runnable {
        override fun run() {
            if (!snap.on) return
            checkHeat()
            if (!snap.on) return
            announce()
            main.postDelayed(this, TICK_MS)
        }
    }

    private fun checkHeat() {
        if (!snap.on) return
        val (percent, charging) = device.battery()
        if (ThermalPolicy.lowBattery(percent, charging)) return stopNow(NodeCam.LOW_BATTERY)
        val before = thermal.level
        val level = thermal.update(thermalStatus(), device.batteryTemperatureTenths(), clock())
        if (level == ThermalPolicy.Level.TOO_HOT) return stopNow(NodeCam.TOO_HOT)
        if (level == before) return
        // Warm: a smaller, slower picture without stopping. Cool again: back to what the parent chose.
        active = ThermalPolicy.profileFor(chosen, level)
        try {
            val p = provider
            val old = analysis
            if (p != null && old != null) {
                old.clearAnalyzer()
                p.unbind(old)
                bind()
            }
        } catch (e: Exception) {
            Log.w(TAG, "could not change the picture size", e)
            return stopNow(reasonFor(e))
        }
        snap = snap.copy(level = level.wire, profile = active.name)
        announce()
    }

    private fun thermalStatus(): Int = if (Build.VERSION.SDK_INT >= 29) power.currentThermalStatus else -1

    private fun listenThermal() {
        if (Build.VERSION.SDK_INT < 29 || thermalListener != null) return
        val l = PowerManager.OnThermalStatusChangedListener { main.post { checkHeat() } }
        try {
            power.addThermalStatusListener(ContextCompat.getMainExecutor(ctx), l)
            thermalListener = l
        } catch (e: Exception) { Log.w(TAG, "no thermal listener", e) }
    }

    private fun unlistenThermal() {
        val l = thermalListener ?: return
        thermalListener = null
        if (Build.VERSION.SDK_INT >= 29) try {
            power.removeThermalStatusListener(l as PowerManager.OnThermalStatusChangedListener)
        } catch (e: Exception) { Log.w(TAG, "thermal listener", e) }
    }

    /** The kid's screen may be off while the parent looks: keep the processor and the WiFi up, never without a time limit. */
    private fun holdAwake(maxMs: Long) {
        try {
            wake = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "nickos:cam").apply {
                setReferenceCounted(false)
                acquire(maxMs + 60_000L)
            }
        } catch (e: Exception) { Log.w(TAG, "no wake lock", e) }
        try {
            @Suppress("DEPRECATION")
            wifi = (ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
                .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "nickos:cam").apply {
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

    private fun stopNow(reason: String) {
        if (!snap.on) return
        gen++
        main.removeCallbacks(autoOff)
        main.removeCallbacks(tick)
        unlistenThermal()
        try {
            camera?.cameraInfo?.cameraState?.removeObserver(cameraWatch)
            analysis?.clearAnalyzer()
            provider?.unbindAll()
        } catch (e: Exception) { Log.w(TAG, "unbind", e) }
        camera = null
        analysis = null
        server?.stop()
        server = null
        executor?.shutdown()
        executor = null
        letSleep()
        life.stop()
        NodeService.camera(false)
        snap = NodeCam(
            NodeCam.OFF, reason = reason, level = thermal.level.wire, maxMs = snap.maxMs,
            retryAt = if (reason == NodeCam.TOO_HOT) thermal.cooldownUntil else null,
        )
        Log.i(TAG, "camera off: $reason")
        announce()
    }

    /** No camera at all, or one that would not start. */
    private fun reasonFor(e: Throwable): String {
        var c: Throwable? = e
        while (c != null) {
            if (c is CameraUnavailableException) return NodeCam.NO_CAMERA
            if (c is IllegalArgumentException && c.message?.contains("camera", ignoreCase = true) == true) return NodeCam.NO_CAMERA
            c = c.cause?.takeIf { it !== c }
        }
        return NodeCam.CAMERA
    }

    companion object {
        private const val TAG = "NickOS.Cam"
        private const val TICK_MS = 10_000L
        private val SELECTOR = CameraSelector.DEFAULT_FRONT_CAMERA
    }
}
