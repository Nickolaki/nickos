package com.nbaltas.nickos.master

import com.nbaltas.nickos.link.NetMessages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * How long [linkLoop] waits before the next dial. A link that had been up and dropped (a WiFi band switch) is dialed
 * again at once, then after 500 ms, 1 s, 2 s, 3 s; a Node that was never reachable keeps the plain 3 s.
 */
class RetryDelay {
    private var step = -1

    /** A session that had been up just ended: retry now. */
    fun dropped(): Long { step = 0; return 0 }

    /**
     * A welcomed session ended after [lastedMs]. A stable one (at least [STABLE_MS]) resets as [dropped] does; a short one
     * is a failure for the schedule (0.5 / 1 / 2 / 3 s, then the plain 3 s), so a Node that welcomes and closes at once
     * cannot be redialed in a tight loop.
     */
    fun ended(lastedMs: Long): Long {
        if (lastedMs >= STABLE_MS) return dropped()
        if (step < 0) step = 0
        return failed()
    }

    /** A dial failed (or the Node never answered): how long to wait. */
    fun failed(): Long {
        if (step < 0) return SLOW_MS
        return FAST[minOf(step++, FAST.size - 1)]
    }

    companion object {
        const val SLOW_MS = 3_000L
        const val STABLE_MS = 5_000L
        val FAST = longArrayOf(500, 1_000, 2_000, 3_000)
    }
}

/**
 * The read timeout of a Node link. Only a Node that says "hb3" in its caps sends something at least every 3 s; an older
 * Node sends an unchanged status every 10 to 11.5 s, so it keeps the long timeout.
 */
object LinkTimeout {
    const val LEGACY_MS = 25_000
    const val HEARTBEAT_MS = 8_000

    fun forCaps(caps: Collection<String>?): Int = if (caps != null && NetMessages.HEARTBEAT_CAP in caps) HEARTBEAT_MS else LEGACY_MS
}

/**
 * Keeps a tablet shown as online for [graceMs] after its link dropped, while the Master reconnects. Pure: the caller
 * passes the clock. The real link state (and the Sync, Transfer and Talk teardown) is not touched by this.
 */
const val GRACE_MS = 10_000L

class OnlineGrace(private val graceMs: Long = GRACE_MS) {
    private val droppedAt = HashMap<String, Long>()

    @Synchronized fun dropped(id: String, now: Long) { droppedAt[id] = now }
    @Synchronized fun reconnected(id: String) { droppedAt.remove(id) }

    /** True while [id] dropped less than [graceMs] ago. */
    @Synchronized fun holding(id: String, now: Long): Boolean = droppedAt[id]?.let { now - it < graceMs } == true

    /** Ids whose grace ran out by [now]; they are forgotten here and must be shown offline. */
    @Synchronized fun expire(now: Long): Set<String> {
        val out = droppedAt.filterValues { now - it >= graceMs }.keys.toSet()
        out.forEach { droppedAt.remove(it) }
        return out
    }
}

/**
 * The one owner of the online set and its grace: a reconnect is published and a grace expiry is applied under the same
 * lock, so the expiry timer can never remove a Node that reconnected between its expiry and its removal.
 * [betweenExpireAndRemove] is a test seam, called inside the lock.
 */
class OnlineTracker(
    private val online: MutableStateFlow<Set<String>>,
    private val grace: OnlineGrace = OnlineGrace(),
    private val betweenExpireAndRemove: () -> Unit = {},
) {
    private val lock = Any()

    /** The link to [id] is up. */
    fun connected(id: String) = synchronized(lock) { grace.reconnected(id); online.update { it + id } }

    /** The link to [id] dropped at [now]; it stays shown as online for the grace window. */
    fun dropped(id: String, now: Long) = synchronized(lock) { grace.dropped(id, now) }

    /** [id] is gone for good (unlinked). */
    fun forget(id: String) = synchronized(lock) { grace.reconnected(id); online.update { it - id } }

    /** Shows offline every Node whose grace ran out by [now]. */
    fun expire(now: Long) = synchronized(lock) {
        val gone = grace.expire(now)
        betweenExpireAndRemove()
        if (gone.isNotEmpty()) online.update { it - gone }
    }
}

object AdoptPolicy {
    /**
     * A Node's address arrived (mDNS or sweep). Dial it now when the link is down and the loop is not already dialing
     * exactly that address; leave a live link, and a dial already under way to the same address, alone.
     */
    fun redialNow(linked: Boolean, dialing: Pair<String, Int>?, addr: Pair<String, Int>): Boolean = !linked && dialing != addr
}
