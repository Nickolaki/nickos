package com.nbaltas.nickos.node

/** The one-time grants a tablet that is NOT Device Owner needs before NickOS can act as the kid lock. */
enum class KioskStep { HOME, OVERLAY, USAGE, ADMIN }

data class KioskState(
    val deviceOwner: Boolean,
    val home: Boolean,
    val overlay: Boolean,
    val usage: Boolean,
    val admin: Boolean,
)

sealed interface KioskStatus {
    /** Device Owner: the full lock. */
    data object Owner : KioskStatus
    /** Launcher mode: NickOS is the home screen. */
    data object Ready : KioskStatus
    /** Launcher mode, not finished: every undone step, in the order the setup screen lists them. */
    data class Incomplete(val missing: List<KioskStep>) : KioskStatus
}

/** Where the Node screen starts: see [KioskPolicy.startScreen]. ASK is the calm "ask a grown-up" card. */
enum class StartScreen { HOME, KIOSK, ASK, MENU }

/** Pure rules for launcher mode (the fallback for a tablet that was not factory reset). */
object KioskPolicy {
    fun status(s: KioskState): KioskStatus {
        if (s.deviceOwner) return KioskStatus.Owner
        if (s.home) return KioskStatus.Ready
        return KioskStatus.Incomplete(missing(s))
    }

    fun missing(s: KioskState): List<KioskStep> = buildList {
        if (!s.home) add(KioskStep.HOME)
        if (!s.overlay) add(KioskStep.OVERLAY)
        if (!s.usage) add(KioskStep.USAGE)
        if (!s.admin) add(KioskStep.ADMIN)
    }

    /** True when the kid lock screen should open instead of the kid home: not owner and not yet the home screen. */
    fun setupNeeded(s: KioskState) = status(s) is KioskStatus.Incomplete

    /** A Node that has been used: it has a parent linked, or it finished the kid lock once. Never a brand new tablet. */
    fun existingNode(masters: Int, kidLockDone: Boolean) = masters > 0 || kidLockDone

    /**
     * What the kid home opens on. An existing Node may only reach the parent menu or the kid lock setup with a parent
     * PIN window ([pinWindow]); without one and with the lock undone, it stays on the kid home with the watchdog on and
     * shows the "ask a grown-up" card. A brand new, never locked tablet keeps going straight into the setup.
     */
    fun startScreen(existing: Boolean, setupNeeded: Boolean, pinWindow: Boolean): StartScreen = when {
        existing && pinWindow -> StartScreen.MENU
        existing && setupNeeded -> StartScreen.ASK
        existing -> StartScreen.HOME
        setupNeeded -> StartScreen.KIOSK
        pinWindow -> StartScreen.MENU
        else -> StartScreen.HOME
    }

    /** Whether the kid lock setup may stay open: always on a new tablet, only inside a PIN window on an existing one. */
    fun setupMayStay(existing: Boolean, pinWindow: Boolean) = !existing || pinWindow

    /**
     * The parent window after a rearm, or null when it may not be extended. A new tablet always may; an existing Node
     * only while the window it got from the PIN is still running, so an expired window sends it back to the PIN card.
     */
    fun rearmedUntil(existing: Boolean, currentUntil: Long, now: Long, minutes: Long): Long? =
        if (!existing || now < currentUntil) now + minutes * 60_000L else null

    /**
     * Packages that must never be bounced: NickOS itself, the system, and the system dialogs a grant or an
     * update shows as activities (runtime permission, Home role, "Install?").
     */
    fun isSystem(pkg: String, self: String) =
        pkg == self || pkg == "android" || pkg == "com.android.systemui" ||
            pkg.contains("permissioncontroller") || pkg.contains("packageinstaller")

    /** Whether the watchdog should send the kid back to NickOS now. Never for Device Owner (lock task does it). */
    fun returnHome(deviceOwner: Boolean, parentMode: Boolean, homeVisible: Boolean, fg: String?, allowed: Collection<String>, self: String): Boolean =
        !deviceOwner && !parentMode && !homeVisible && fg != null && fg !in allowed && !isSystem(fg, self)

    /** After "Reset this tablet": hand the home role back (Android has no API for it) so the Home button is not NickOS Setup forever. */
    fun afterReset(deviceOwner: Boolean, home: Boolean) = !deviceOwner && home
}

/**
 * Drives [KioskPolicy.returnHome] once a second. At most one [goHome] per 1500 ms. Three bounces in 10 s with
 * the home screen still not visible means the start is failing silently (Android 12 drops background starts
 * without the display over other apps grant): log it once and pause 30 s instead of pretending forever.
 */
class KioskWatch(
    private val inputs: () -> Inputs,
    private val goHome: () -> Unit,
    private val log: (String) -> Unit,
) {
    data class Inputs(
        val deviceOwner: Boolean,
        val parentMode: Boolean,
        val homeVisible: Boolean,
        val fg: String?,
        val allowed: Collection<String>,
        val self: String,
    )

    private var lastBounce = Long.MIN_VALUE / 2
    private var pausedUntil = Long.MIN_VALUE / 2
    private val bounces = ArrayDeque<Long>()

    fun tick(now: Long) {
        if (now < pausedUntil) return
        val i = inputs()
        if (i.homeVisible) bounces.clear()
        if (!KioskPolicy.returnHome(i.deviceOwner, i.parentMode, i.homeVisible, i.fg, i.allowed, i.self)) return
        if (now - lastBounce < MIN_GAP_MS) return
        lastBounce = now
        goHome()
        bounces.addLast(now)
        while (bounces.isNotEmpty() && now - bounces.first() > WINDOW_MS) bounces.removeFirst()
        if (bounces.size >= MAX_BOUNCES) {
            log("cannot return home (display over other apps off?)")
            pausedUntil = now + PAUSE_MS
            bounces.clear()
        }
    }

    companion object {
        const val MIN_GAP_MS = 1500L
        const val WINDOW_MS = 10_000L
        const val MAX_BOUNCES = 3
        const val PAUSE_MS = 30_000L
    }
}
