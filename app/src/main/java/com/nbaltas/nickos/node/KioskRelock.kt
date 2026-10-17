package com.nbaltas.nickos.node

/**
 * The settings window ("Open setting" from a parent) and the central re-lock, with every clock and device effect injected.
 * The window is timed on [elapsed] (SystemClock.elapsedRealtime), which the child cannot move; the wall clock is never its
 * boundary. The durable marker only says "a window was open": a process that starts with it set closes the window at once.
 * The parent window ([parentUntil]) stays on the wall clock, as before.
 */
class KioskRelock(
    private val elapsed: () -> Long,
    private val wall: () -> Long,
    private val markerOpen: () -> Boolean,
    private val setMarker: (Boolean) -> Unit,
    private val parentUntil: () -> Long,
    private val clearParent: () -> Unit,
    private val homeVisible: () -> Boolean,
    /** The real foreground package (usage access), null when unknown. Never a stale "last launched". */
    private val foreground: () -> String?,
    private val allowed: () -> Collection<String>,
    private val needsFront: () -> Boolean,
    private val applyLockdown: () -> Unit,
    private val goHome: () -> Unit,
    private val maxTries: Int = 5,
) {
    private var open = false
    private var openedAt = 0L
    private var pending = false
    private var tries = 0

    val active: Boolean get() = open && elapsed() - openedAt in 0 until KioskLock.MAINTENANCE_MS
    private fun parentActive() = parentUntil().let { it > 0 && wall() < it }

    /** The parent asked for Settings: open the window now. */
    @Synchronized
    fun begin() {
        open = true; openedAt = elapsed(); pending = false
        setMarker(true)
    }

    /** The app process started: a marker left from before a restart or reboot closes the window at once. */
    @Synchronized
    fun onStart() { if (markerOpen()) end() }

    /** Ends the window now (Lock now, the service connected, a restart) and puts the lock back unless a parent window is open. */
    @Synchronized
    fun end() {
        if (!open && !markerOpen()) return
        close()
        relockIfExpired()
    }

    /** NickOS came to the front: that ends the window, once its first moments are over. */
    @Synchronized
    fun onForeground() = relockIfExpired()

    private fun close() { open = false; setMarker(false); pending = true; tries = 0 }

    /** Cheap and safe to call every second: does nothing while a window is open or when nothing ran out. */
    @Synchronized
    fun relockIfExpired() {
        if (open) {
            val age = elapsed() - openedAt
            // Timed out, or the monotonic clock is unreadable (age < 0): closed. Home in front after the grace period: closed.
            if (age !in 0 until KioskLock.MAINTENANCE_MS || (age >= KioskLock.MAINTENANCE_GRACE_MS && homeVisible())) close()
        }
        val p = parentUntil()
        if (open || (p > 0 && wall() < p)) { pending = false; return }
        if (p > 0) { clearParent(); pending = true; tries = 0 }
        if (!pending) return
        applyLockdown()
        if (!needsFront() || ++tries > maxTries) { pending = false; return }
        // An allowed kid app in front stays; lock task re-enters when NickOS next resumes (enterKidMode).
        if (!KioskLock.relockGoesHome(foreground(), allowed())) { pending = false; return }
        goHome()
    }
}
