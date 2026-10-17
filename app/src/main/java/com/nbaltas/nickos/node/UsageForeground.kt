package com.nbaltas.nickos.node

/**
 * Folds usage events into "which app is in front". Pure, so the fail closed rules can be tested.
 * Strict mode (used by the relock decision) answers null unless the foreground is verified.
 */
class UsageForeground {
    enum class Kind { RESUMED, PAUSED }
    class Ev(val kind: Kind, val pkg: String)

    private var cursor = 0L
    private var last: String? = null
    private var lastResumed = false
    // Set by a clock discontinuity; only a fresh RESUMED from an incremental query clears it.
    private var untrusted = false
    private var recovery = false

    /** Start of the next query. A reversed interval (clock went back) drops the cache. */
    fun from(now: Long): Long {
        if (cursor != 0L && now < cursor) reset(flag = true)
        recovery = cursor == 0L
        return if (cursor == 0L) now - 6 * 60 * 60 * 1000L else cursor - 2000
    }

    fun reset(flag: Boolean = false) {
        cursor = 0L
        last = null
        lastResumed = false
        if (flag) untrusted = true
    }

    /** [events] are those returned for the interval starting at [from]. Returns the package, or null. */
    fun fold(now: Long, access: Boolean, events: List<Ev>, strict: Boolean): String? {
        if (!access) { reset(); return null }
        for (e in events) {
            if (e.kind == Kind.RESUMED) {
                last = e.pkg; lastResumed = true
                // Historical recovery events refresh the cache only; they are not fresh evidence.
                if (!recovery) untrusted = false
            } else if (e.pkg == last) lastResumed = false
        }
        cursor = now
        if (strict && (untrusted || !lastResumed)) return null
        return last
    }
}
