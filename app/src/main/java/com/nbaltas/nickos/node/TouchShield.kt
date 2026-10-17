package com.nbaltas.nickos.node

/**
 * The touch lock: when the parent turns touch off for a kid tablet, a see through window (and, without the overlay
 * permission, a box inside NickOS) swallows every touch while the video keeps playing underneath. The decisions live
 * here, without Android, so they can be tested.
 */
object TouchShield {
    /** The shield steps aside in parent mode and under the locked and paused walls, which already block touches. */
    fun wanted(touchOff: Boolean, parentMode: Boolean, locked: Boolean, paused: Boolean, suspended: Boolean = false): Boolean =
        touchOff && !parentMode && !locked && !paused && !suspended

    /** Top left square of the shield, in dp, where a long press asks for the parent PIN. */
    const val CORNER_DP = 80
    const val HOLD_MS = 3_000L

    /** The longest the shield stays away for the emergency PIN prompt. */
    const val SUSPEND_MAX_MS = 60_000L

    /** How long past a gesture's own length the shield waits for Android's answer before it takes touch back. */
    const val RESTORE_SLACK_MS = 300L
}

/**
 * A finger held in the top left corner of the shield. [start] when it goes down, [cancel] when it lifts, leaves the
 * corner or the shield goes away, [ready] when asked later: true once it stayed for [TouchShield.HOLD_MS].
 */
class CornerHold(private val zonePx: Float, private val holdMs: Long = TouchShield.HOLD_MS) {
    private var since = -1L

    fun inZone(x: Float, y: Float) = x >= 0f && y >= 0f && x <= zonePx && y <= zonePx

    /** Starts only inside the corner; a touch anywhere else is ignored. */
    fun start(x: Float, y: Float, now: Long) { since = if (inZone(x, y)) now else -1L }

    /** A finger that slides out of the corner starts over. */
    fun move(x: Float, y: Float) { if (!inZone(x, y)) since = -1L }

    fun cancel() { since = -1L }

    val holding get() = since >= 0L

    fun ready(now: Long) = since >= 0L && now - since >= holdMs

    /** Fires once: after it returns true the hold is over. */
    fun consume(now: Long): Boolean = ready(now).also { if (it) since = -1L }
}

/**
 * The emergency PIN is shown above the shield, so the shield is taken away while it is up. Cancel or a wrong PIN puts it
 * back at once; a right PIN enters parent mode, and the shield stays away while that lasts. A forgotten window ends by
 * itself after [TouchShield.SUSPEND_MAX_MS].
 */
class ShieldSuspension(private val maxMs: Long = TouchShield.SUSPEND_MAX_MS) {
    @Volatile private var until = 0L

    fun suspend(now: Long) { until = now + maxMs }

    /** Cancelled or failed PIN. */
    fun restore() { until = 0L }

    fun active(now: Long) = now < until

    /** When the shield has to be looked at again, or 0. */
    fun endsAt() = until
}

/**
 * One injected gesture at a time. While the shield is up, the parent's remote tap must pass through it, so the shield
 * becomes touchable only to Android's pass through for the gesture's few hundred milliseconds. Physical touches can get
 * through during that short window and only then.
 *
 * Every injection gets a token. Android's answer, the timeout and a refusal all call [finish] with it; only the token
 * that is still in flight restores the shield, so a late callback of an old gesture can never restore it early or late
 * for a newer one. Extra gestures while one is in flight are dropped ([begin] returns null).
 */
class InjectGate {
    private var next = 0L
    private var active = 0L

    @Synchronized fun begin(): Long? {
        if (active != 0L) return null
        next += 1
        active = next
        return active
    }

    /** True when [token] was the one in flight: the caller restores the shield. Stale tokens do nothing. */
    @Synchronized fun finish(token: Long): Boolean {
        if (token == 0L || token != active) return false
        active = 0L
        return true
    }

    @get:Synchronized val busy get() = active != 0L
}
