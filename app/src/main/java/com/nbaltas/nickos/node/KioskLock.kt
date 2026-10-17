package com.nbaltas.nickos.node

/** Pure decisions behind the kid lock holes and the remote control recovery. Nothing here blocks while parent mode is on. */
object KioskLock {
    const val SETTINGS_PKG = "com.android.settings"
    const val SYSTEMUI_PKG = "com.android.systemui"

    /** The longest the lock stays paused for "Open setting" on a parent's request. */
    const val MAINTENANCE_MS = 90_000L
    /** NickOS coming to the front this soon after the window opened is the screen waking up, not the end of Settings. */
    const val MAINTENANCE_GRACE_MS = 3_000L
    /** The notification shade is pushed back at most this often. */
    const val SHADE_GAP_MS = 1_000L

    /** The accessibility service flat name, as Android stores it in the enabled list. */
    fun serviceName(pkg: String, cls: String) = "$pkg/$cls"

    /** BootReceiver: put the kid home back after a reboot or an update, on kid (Node) tablets in kid mode only. */
    fun bootLaunch(action: String?, isNode: Boolean, parentMode: Boolean) =
        isNode && !parentMode && (action == "android.intent.action.BOOT_COMPLETED" || action == "android.intent.action.MY_PACKAGE_REPLACED")

    /** The enabled accessibility list with [flat] added: others kept, no duplicates, blank list gives just [flat]. */
    fun mergeAccessibility(cur: String?, flat: String): String {
        val parts = cur.orEmpty().split(':').filter { it.isNotBlank() }
        return if (flat in parts) parts.joinToString(":") else (parts + flat).joinToString(":")
    }

    // ---- remote control consent ----

    /** The service connected: that is a grown-up's consent only while a parent window or the settings window is open. */
    fun consentOnConnect(parentMode: Boolean, maintenance: Boolean) = parentMode || maintenance

    /**
     * The service unbound while the app lives on: the grown-up turned it off, unless an update is replacing the app
     * (the installed version is no longer the running one).
     */
    fun consentClearedOnUnbind(runningVersion: Long, installedVersion: Long) = installedVersion == runningVersion

    /**
     * The silent re-enable: once per app update ([lastTriedVersion] is the version already tried), only with consent,
     * only if the service is off now, and never again after Android refused it ([unsupported]).
     */
    fun mayReenable(consent: Boolean, running: Boolean, unsupported: Boolean, lastTriedVersion: Long, version: Long) =
        consent && !running && !unsupported && lastTriedVersion != version

    // ---- the settings (maintenance) window ----

    /** After applyLockdown: bring the kid home to the front only on a Device Owner tablet that is still not in lock task. */
    fun needsFront(deviceOwner: Boolean, lockTaskNone: Boolean) = deviceOwner && lockTaskNone

    /**
     * At relock, whether NickOS is brought forward. Only a reliably identified allowed kid app stays (a movie keeps playing);
     * Settings, any other app, system windows and an unknown foreground (null) go home.
     */
    fun relockGoesHome(fg: String?, allowed: Collection<String>): Boolean = fg == null || fg == SETTINGS_PKG || fg !in allowed

    // ---- accessibility backstop ----

    enum class Bounce { NONE, HOME, COLLAPSE }

    /**
     * What the accessibility service does when a window of [pkg] comes up: kid (Node) tablets in kid mode only, never in
     * a parent window or the settings window. systemui only for the shade on Android 12+, at most once per [SHADE_GAP_MS].
     */
    fun bounce(pkg: String?, isNode: Boolean, parentMode: Boolean, maintenance: Boolean, sdk: Int, now: Long, lastShade: Long): Bounce = when {
        !isNode || parentMode || maintenance -> Bounce.NONE
        pkg == SETTINGS_PKG -> Bounce.HOME
        pkg == SYSTEMUI_PKG && sdk >= 31 && now - lastShade >= SHADE_GAP_MS -> Bounce.COLLAPSE
        else -> Bounce.NONE
    }
}
