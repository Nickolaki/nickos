package com.nbaltas.nickos.master

/**
 * How long [linkLoop] waits before the next dial. A link that had been up and dropped (a WiFi band switch) is dialed
 * again at once, then after 500 ms, 1 s, 2 s, 3 s; a Node that was never reachable keeps the plain 3 s.
 */
class RetryDelay {
    private var step = -1

    /** A session that had been up just ended: retry now. */
    fun dropped(): Long { step = 0; return 0 }

    /** A dial failed (or the Node never answered): how long to wait. */
    fun failed(): Long {
        if (step < 0) return SLOW_MS
        return FAST[minOf(step++, FAST.size - 1)]
    }

    companion object {
        const val SLOW_MS = 3_000L
        val FAST = longArrayOf(500, 1_000, 2_000, 3_000)
    }
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

object AdoptPolicy {
    /**
     * A Node's address arrived (mDNS or sweep). Dial it now when the link is down and the loop is not already dialing
     * exactly that address; leave a live link, and a dial already under way to the same address, alone.
     */
    fun redialNow(linked: Boolean, dialing: Pair<String, Int>?, addr: Pair<String, Int>): Boolean = !linked && dialing != addr
}
