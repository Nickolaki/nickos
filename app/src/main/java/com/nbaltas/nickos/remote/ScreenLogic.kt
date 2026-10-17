package com.nbaltas.nickos.remote

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Where a kid tablet's screen sharing stands. Android 14 and up give a screen capture grant once, so the grant
 * (a held MediaProjection) outlives one viewing: it is dropped only by [revoke] (the system took it back), [release]
 * (the parent menu, unlink, reset) or a reboot, which is a new object. Nothing here is saved to disk.
 *
 *  - off: no grant and nobody asked.
 *  - needs_consent: a parent asked, and a grown-up has to allow it on the kid tablet first.
 *  - ready: granted, not streaming.
 *  - live: streaming to a parent.
 */
class ScreenConsent {
    var state: String = RemoteMessages.OFF
        private set

    /** True once a grant is held. */
    val granted: Boolean get() = state == RemoteMessages.READY || state == RemoteMessages.LIVE

    /** A parent asked to watch. Returns the new state; [RemoteMessages.LIVE] means start streaming now. */
    fun start(): String {
        state = when (state) {
            RemoteMessages.OFF, RemoteMessages.NEEDS_CONSENT -> RemoteMessages.NEEDS_CONSENT
            else -> RemoteMessages.LIVE
        }
        return state
    }

    /** The parent stopped watching. The grant stays; a request still waiting for a grant is dropped. */
    fun stop(): String {
        state = when (state) {
            RemoteMessages.LIVE, RemoteMessages.READY -> RemoteMessages.READY
            else -> RemoteMessages.OFF
        }
        return state
    }

    /** A grown-up allowed screen capture. A parent already waiting gets the picture at once. */
    fun grant(): String {
        state = if (state == RemoteMessages.NEEDS_CONSENT) RemoteMessages.LIVE else if (state == RemoteMessages.OFF) RemoteMessages.READY else state
        return state
    }

    /** Android ended the capture (the user stopped it, or the system did). */
    fun revoke(): String { state = RemoteMessages.OFF; return state }

    /** Turned off on purpose: the parent menu, unlink or reset. */
    fun release(): String { state = RemoteMessages.OFF; return state }
}

/** Lets a picture be encoded only while a parent is reading, and at most [fps] times a second. */
class FrameGate(fps: Int = MAX_FPS, private val clock: () -> Long) {
    private val gapMs = 1000L / fps
    private var last = -1L

    @Synchronized
    fun shouldEncode(viewers: Int): Boolean {
        if (viewers <= 0) return false
        val now = clock()
        if (last >= 0 && now - last < gapMs) return false
        last = now
        return true
    }

    companion object { const val MAX_FPS = 8 }
}

/** At most [perSecond] touches a second. Anything past that is dropped, not queued. */
class TouchLimiter(private val perSecond: Int = PER_SECOND, private val clock: () -> Long) {
    private val times = ArrayDeque<Long>()

    @Synchronized
    fun allow(): Boolean {
        val now = clock()
        while (times.isNotEmpty() && now - times.first() >= 1000L) times.removeFirst()
        if (times.size >= perSecond) return false
        times.addLast(now)
        return true
    }

    companion object { const val PER_SECOND = 20 }
}

/** How big the shared picture is: the screen scaled so its longer side is at most [MAX_SIDE]. */
object ScreenSize {
    const val MAX_SIDE = 1280

    fun capture(w: Int, h: Int): Pair<Int, Int> {
        val long = max(w, h)
        if (long <= 0) return 1 to 1
        if (long <= MAX_SIDE) return w to h
        val k = MAX_SIDE.toDouble() / long
        return max(1, (w * k).roundToInt()) to max(1, (h * k).roundToInt())
    }
}

/** Turning a touch on the Master's picture into a place on the kid tablet's screen. */
object TouchMap {
    /** The part of a view that the picture covers, in view pixels. */
    data class Box(val left: Float, val top: Float, val width: Float, val height: Float)

    data class Norm(val x: Double, val y: Double)

    /** A picture [fw] by [fh] fitted whole and centred in a view [vw] by [vh]; the rest is the black bars. */
    fun content(vw: Float, vh: Float, fw: Int, fh: Int): Box? {
        if (vw <= 0f || vh <= 0f || fw <= 0 || fh <= 0) return null
        val k = minOf(vw / fw, vh / fh)
        val w = fw * k
        val h = fh * k
        return Box((vw - w) / 2f, (vh - h) / 2f, w, h)
    }

    /** 0..1 of the picture for a touch at ([px], [py]) in the view, or null on the black bars. */
    fun toNorm(px: Float, py: Float, vw: Float, vh: Float, fw: Int, fh: Int): Norm? {
        val b = content(vw, vh, fw, fh) ?: return null
        if (px < b.left || px > b.left + b.width || py < b.top || py > b.top + b.height) return null
        return Norm(((px - b.left) / b.width).toDouble().coerceIn(0.0, 1.0), ((py - b.top) / b.height).toDouble().coerceIn(0.0, 1.0))
    }

    /** The pixel on a screen [w] by [h] (its size in its current rotation) for 0..1 coordinates. */
    fun toPixels(nx: Double, ny: Double, w: Int, h: Int): Pair<Float, Float> =
        (nx.coerceIn(0.0, 1.0) * (w - 1).coerceAtLeast(0)).toFloat() to (ny.coerceIn(0.0, 1.0) * (h - 1).coerceAtLeast(0)).toFloat()
}
