package com.nbaltas.nickos.cam

/**
 * A tablet's last `camState` as the Master holds it. [now], [onSince] and [retryAt] are on the
 * tablet's clock; [receivedAt] is the Master's clock when the message arrived, which is what
 * lets a tablet time be read on the Master.
 */
data class NodeCam(
    val state: String,
    val reason: String? = null,
    val level: String = LEVEL_NORMAL,
    val port: Int = 0,
    val key: String? = null,
    val kind: String = KIND_MJPEG,
    val profile: String = "normal",
    val onSince: Long? = null,
    val maxMs: Long? = null,
    val retryAt: Long? = null,
    val now: Long = 0,
    val receivedAt: Long = 0,
) {
    val on: Boolean get() = state != OFF

    /** [retryAt] on the Master's clock. */
    val retryAtLocal: Long? get() = retryAt?.let { receivedAt + (it - now) }

    /** The auto-off this run was started with, in whole minutes. */
    val minutes: Int? get() = maxMs?.let { (it / 60_000L).toInt() }

    companion object {
        const val OFF = "off"
        const val WARMING = "warming"
        const val LIVE = "live"
        val STATES = setOf(OFF, WARMING, LIVE)

        const val LEVEL_NORMAL = "normal"
        const val LEVEL_HOT = "hot"
        const val LEVEL_TOO_HOT = "tooHot"
        val LEVELS = setOf(LEVEL_NORMAL, LEVEL_HOT, LEVEL_TOO_HOT)

        const val KIND_MJPEG = "mjpeg"

        const val NO_CAMERA = "noCamera"
        const val PERMISSION = "permission"
        const val BACKGROUND = "background"
        const val BUSY = "busy"
        const val CAMERA = "camera"
        const val AUTO_OFF = "autoOff"
        const val TOO_HOT = "tooHot"
        const val LOW_BATTERY = "lowBattery"
        const val IDLE = "idle"
        const val LINK = "link"
        const val STOPPED = "stopped"

        /** The tablet turned the camera off to protect itself: the parent's toggle flips off too. */
        val SELF_OFF = setOf(AUTO_OFF, TOO_HOT, LOW_BATTERY)
    }
}

/** What a camera tile shows. Only LIVE and HOT show the picture. */
enum class TileDisplay(val showsVideo: Boolean = false) {
    OFFLINE, UPDATE_NEEDED, PERMISSION, NO_CAMERA, BUSY, BACKGROUND, CAMERA_ERROR, LOW_BATTERY,
    TOO_HOT, AUTO_OFF, WARMING, LIVE(true), HOT(true),
}

/** The Master's two pure decisions about one tablet camera: what to do next, and what to show. */
object CamPlan {
    enum class Want { START, STOP, CONNECT, DISCONNECT, NONE }

    /** The stream ended because the tablet refused the key. */
    const val ENDED_KEY = "403"
    /** The stream ended any other way: the WiFi dropped, the tablet stopped. */
    const val ENDED_IO = "io"

    const val RECONNECT_AFTER_MS = 2_000L
    const val MAX_RECONNECTS = 3
    const val RETRY_ERROR_MS = 15_000L

    /**
     * What the Master should do about one tablet's camera right now.
     *
     * [hasClient]: a stream connection to it is open or opening. [ended], [endedAt] and
     * [retries] describe the last stream that ended since the camera came on: why, when, and how
     * many in a row ended without a single picture. [mine]: this Master asked for the camera or
     * watched it, so it is this Master's to turn off (another parent tablet may be watching).
     */
    fun want(
        armed: Boolean,
        online: Boolean,
        supportsCam: Boolean,
        viewing: Boolean,
        cam: NodeCam?,
        hasClient: Boolean,
        now: Long,
        ended: String? = null,
        endedAt: Long = 0,
        retries: Int = 0,
        mine: Boolean = true,
    ): Want {
        // Nothing can be sent to a tablet that is not there; it turns its own camera off.
        if (!online) return if (hasClient) Want.DISCONNECT else Want.NONE
        val on = cam != null && cam.on
        if (!viewing || !armed) {
            return when {
                on && mine -> Want.STOP
                hasClient -> Want.DISCONNECT
                else -> Want.NONE
            }
        }
        if (!supportsCam) return if (hasClient) Want.DISCONNECT else Want.NONE
        if (cam == null || !cam.on) {
            if (hasClient) return Want.DISCONNECT
            return when (cam?.reason) {
                // Only the parent can fix these; asking again changes nothing.
                NodeCam.PERMISSION, NodeCam.NO_CAMERA, NodeCam.LOW_BATTERY -> Want.NONE
                NodeCam.TOO_HOT -> {
                    val at = cam.retryAtLocal
                    if (at != null && now < at) Want.NONE else Want.START
                }
                NodeCam.BUSY, NodeCam.BACKGROUND, NodeCam.CAMERA ->
                    if (now - cam.receivedAt >= RETRY_ERROR_MS) Want.START else Want.NONE
                else -> Want.START
            }
        }
        if (hasClient) return Want.NONE
        // A refused key is a stale one: ask the tablet again instead of trying the same address.
        if (ended == ENDED_KEY) return Want.START
        if (ended == ENDED_IO) {
            if (retries >= MAX_RECONNECTS) return Want.STOP
            if (now - endedAt < RECONNECT_AFTER_MS) return Want.NONE
        }
        // A kind this build cannot decode (a later encoder) is left alone.
        return if (cam.kind == NodeCam.KIND_MJPEG && cam.port > 0 && !cam.key.isNullOrEmpty()) Want.CONNECT else Want.NONE
    }

    /** What the tile for one tablet shows. [hasFrame]: a picture from the current stream has arrived. */
    @Suppress("UNUSED_PARAMETER")
    fun display(armed: Boolean, online: Boolean, supportsCam: Boolean, cam: NodeCam?, hasFrame: Boolean, now: Long): TileDisplay {
        if (!online) return TileDisplay.OFFLINE
        if (!supportsCam) return TileDisplay.UPDATE_NEEDED
        if (cam == null) return TileDisplay.WARMING
        if (!cam.on) return when (cam.reason) {
            NodeCam.PERMISSION -> TileDisplay.PERMISSION
            NodeCam.NO_CAMERA -> TileDisplay.NO_CAMERA
            NodeCam.BUSY -> TileDisplay.BUSY
            NodeCam.BACKGROUND -> TileDisplay.BACKGROUND
            NodeCam.CAMERA -> TileDisplay.CAMERA_ERROR
            NodeCam.LOW_BATTERY -> TileDisplay.LOW_BATTERY
            NodeCam.TOO_HOT -> TileDisplay.TOO_HOT
            NodeCam.AUTO_OFF -> TileDisplay.AUTO_OFF
            // Stopped, idle or a lost link while the parent still wants it: it is about to start again.
            else -> TileDisplay.WARMING
        }
        if (cam.state != NodeCam.LIVE || !hasFrame) return TileDisplay.WARMING
        return if (cam.level == NodeCam.LEVEL_NORMAL) TileDisplay.LIVE else TileDisplay.HOT
    }

    /**
     * The tablet turned its own camera off (auto-off, too hot, low battery). Its tile stays on
     * the stage, with the parent's toggle already off, so the reason is seen.
     */
    fun lingers(cam: NodeCam?): Boolean = cam != null && !cam.on && cam.reason in NodeCam.SELF_OFF

    /**
     * Where the Master reads a tablet's stream: the address it already reaches the tablet at,
     * with the port and key the tablet sent. Null when any part is not plainly what it should be.
     */
    fun streamUrl(host: String?, port: Int, key: String?): String? {
        if (host.isNullOrEmpty() || key.isNullOrEmpty() || port !in 1..65535) return null
        if (!key.all { it in '0'..'9' || it in 'a'..'z' || it in 'A'..'Z' }) return null
        if (!host.all { it in '0'..'9' || it in 'a'..'z' || it in 'A'..'Z' || it == '.' || it == '-' || it == ':' }) return null
        return "http://${if (':' in host) "[$host]" else host}:$port/cam?k=$key"
    }

    /** True while a too hot tablet is still inside its cooldown, on the Master's clock. */
    fun coolingDown(cam: NodeCam?, now: Long): Boolean {
        val at = cam?.takeIf { !it.on && it.reason == NodeCam.TOO_HOT }?.retryAtLocal ?: return false
        return now < at
    }
}

/** Every word the Cameras screen says about a tablet camera, kept here so it is tested as plain text. */
object CamText {
    const val TURNING_OFF = "Turning off..."
    const val WARM_BADGE = "Getting warm, lower picture"
    const val TURN_ON_AGAIN = "Turn on again"
    const val OPEN_SETTINGS = "Open Settings"
    const val RTSP_ERROR = "Can't connect to this camera"

    /** The line in the middle of a tile. [tablet] is the tablet's full name ("Hailey's Tablet"). Empty while the picture shows. */
    fun tile(d: TileDisplay, tablet: String, minutes: Int): String = when (d) {
        TileDisplay.OFFLINE -> "Offline"
        TileDisplay.UPDATE_NEEDED -> "Update needed"
        TileDisplay.PERMISSION -> "Camera permission needed"
        TileDisplay.NO_CAMERA -> "This tablet has no front camera"
        TileDisplay.BUSY -> "Another app is using the camera"
        TileDisplay.BACKGROUND -> "Can't start the camera right now. Open NickOS on $tablet and try again"
        TileDisplay.CAMERA_ERROR -> "The camera didn't start. Trying again..."
        TileDisplay.LOW_BATTERY -> "Battery too low for the camera"
        TileDisplay.TOO_HOT -> "Too hot. Camera off to cool down"
        TileDisplay.AUTO_OFF -> "Turned off after $minutes min to keep the tablet cool"
        TileDisplay.WARMING -> "Starting the camera..."
        TileDisplay.LIVE, TileDisplay.HOT -> ""
    }

    /** A smaller second line, or null. */
    fun hint(d: TileDisplay, tablet: String): String? = when (d) {
        TileDisplay.PERMISSION -> "On $tablet, hold the avatar, enter the PIN, then tap Allow camera"
        else -> null
    }

    /** "Try again in 1:43". */
    fun retryIn(msLeft: Long): String = "Try again in ${countdown(msLeft)}"

    /** Minutes and seconds left, rounded up to the next second: 103000 is "1:43". */
    fun countdown(msLeft: Long): String {
        val s = (msLeft.coerceAtLeast(0) + 999) / 1000
        return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
    }

    /** The word after a tablet's name on its chip. Empty when the chip needs none. */
    fun chip(armed: Boolean, online: Boolean, supportsCam: Boolean, cam: NodeCam?, d: TileDisplay): String = when {
        !online -> "Offline"
        !supportsCam -> "Update needed"
        cam != null && !cam.on && cam.reason == NodeCam.PERMISSION -> "No camera access"
        cam != null && !cam.on && cam.reason == NodeCam.AUTO_OFF -> "Turned off"
        cam != null && !cam.on && cam.reason == NodeCam.TOO_HOT -> "Too hot"
        !armed -> ""
        d == TileDisplay.WARMING -> "Starting"
        d == TileDisplay.HOT -> "Warm"
        else -> ""
    }

    /** A chip is lit while the parent has the camera on and it is running or about to. */
    fun chipLit(armed: Boolean, d: TileDisplay): Boolean =
        armed && (d == TileDisplay.LIVE || d == TileDisplay.WARMING || d == TileDisplay.HOT)

    /** The toast when a tablet turned its own camera off. [kid] is the short name ("Hailey"). Null for any other reason. */
    fun toast(reason: String?, kid: String, minutes: Int): String? = when (reason) {
        NodeCam.AUTO_OFF -> "$kid's camera turned off after $minutes min to keep it cool"
        NodeCam.TOO_HOT -> "$kid's tablet is too hot. Camera off for 2 minutes"
        NodeCam.LOW_BATTERY -> "$kid's battery is too low for the camera"
        else -> null
    }

    fun updateHint(tablet: String): String = "Update $tablet from Settings, About"
}
