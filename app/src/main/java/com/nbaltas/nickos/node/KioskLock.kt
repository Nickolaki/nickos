package com.nbaltas.nickos.node

/** Pure decisions behind the kid lock holes and the remote control recovery. Nothing here blocks while parent mode is on. */
object KioskLock {
    const val SETTINGS_PKG = "com.android.settings"
    const val SYSTEMUI_PKG = "com.android.systemui"

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

    /** Silent re-enable only on a tablet where a grown-up turned remote control on before, and only if it is off now. */
    fun mayReenable(everConnected: Boolean, running: Boolean) = everConnected && !running

    enum class Bounce { NONE, HOME, COLLAPSE }

    /** What the accessibility service does when a window of [pkg] comes up. */
    fun bounce(pkg: String?, parentMode: Boolean, sdk: Int): Bounce = when {
        parentMode -> Bounce.NONE
        pkg == SETTINGS_PKG -> Bounce.HOME
        pkg == SYSTEMUI_PKG && sdk >= 31 -> Bounce.COLLAPSE
        else -> Bounce.NONE
    }

    /** True exactly once when parent mode goes from on to off, so the lock is put back once. */
    class ParentExpiry {
        private var was = false
        fun tick(parentMode: Boolean): Boolean {
            val fire = was && !parentMode
            was = parentMode
            return fire
        }
    }
}
