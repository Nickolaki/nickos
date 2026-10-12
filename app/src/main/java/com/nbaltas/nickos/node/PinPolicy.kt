package com.nbaltas.nickos.node

import com.nbaltas.nickos.data.Store

object PinPolicy {
    /** The starting PIN, four of one digit, or anything that is not 4 digits long. */
    fun isWeak(pin: String) = pin == Store.DEFAULT_PIN || pin.toSet().size == 1 || pin.length != 4
}

/** What a PIN entry came to. */
sealed interface PinResult {
    data object Ok : PinResult
    /** [left] wrong entries remain before the pad locks. */
    data class Wrong(val left: Int) : PinResult
    data class Locked(val untilMs: Long) : PinResult
}

/**
 * Lockout for the Node's PIN pad: the [MAX_WRONG]th consecutive wrong entry locks for [base] ms, and each wrong
 * entry after a lock doubles it up to [cap]. While locked every call answers Locked without counting. A correct
 * entry while not locked resets everything.
 *
 * Memory only, by design: a process restart resets it. It lives on NodeRuntime, not in the composable, so
 * Cancel and holding the avatar again cannot reset the counter.
 */
class PinGate(private val base: Long = 30_000, private val cap: Long = 300_000) {
    private var wrong = 0
    private var lockMs = 0L
    private var lockedUntil = 0L

    /** When the pad unlocks, or null when it is not locked at [now]. Lets the pad open already locked. */
    @Synchronized
    fun lockedUntil(now: Long): Long? = if (now < lockedUntil) lockedUntil else null

    @Synchronized
    fun tryPin(entered: String, actual: String, now: Long): PinResult {
        if (now < lockedUntil) return PinResult.Locked(lockedUntil)
        if (entered == actual) {
            wrong = 0; lockMs = 0; lockedUntil = 0
            return PinResult.Ok
        }
        wrong++
        if (lockMs > 0) {
            // A wrong entry right after a lock: lock again, twice as long.
            lockMs = (lockMs * 2).coerceAtMost(cap)
            lockedUntil = now + lockMs
            return PinResult.Locked(lockedUntil)
        }
        if (wrong >= MAX_WRONG) {
            lockMs = base
            lockedUntil = now + lockMs
            return PinResult.Locked(lockedUntil)
        }
        return PinResult.Wrong(MAX_WRONG - wrong)
    }

    companion object { const val MAX_WRONG = 5 }
}
