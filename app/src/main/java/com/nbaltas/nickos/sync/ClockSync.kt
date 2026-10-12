package com.nbaltas.nickos.sync

/**
 * Master-side NTP-style clock offset estimation between the Master's and a Node's
 * elapsedRealtime clocks. offset = nodeClock - masterClock, so nodeTime = masterTime + offset.
 *
 * Pure: this class owns no real clock and no cadence. The caller sends a "ping" with the id
 * from [nextPingId] and the Master's own t0, then feeds the Node's "pong" (t1, t2) plus the
 * Master's own receipt time t3 into [onPong]. One instance per Node.
 */
enum class SyncQuality { GOOD, OK, POOR }

data class ClockSample(val offset: Long, val rtt: Long)

class ClockSync {
    private var nextId = 0
    private val sentIds = ArrayDeque<Int>()
    private val samples = ArrayDeque<ClockSample>()

    /** Call when sending a ping; put the returned id on the wire as "id". */
    fun nextPingId(): Int {
        val id = nextId++
        sentIds.addLast(id)
        while (sentIds.size > WINDOW) sentIds.removeFirst()
        return id
    }

    /**
     * Feed a pong. [id] must be among the last [WINDOW] ids this instance handed out via
     * [nextPingId], else the sample is ignored (a stray reply after this Node's offset was
     * already re-estimated many times over, or a reply for a ping this instance never sent).
     */
    fun onPong(id: Int, t0: Long, t1: Long, t2: Long, t3: Long) {
        if (id !in sentIds) return
        val rtt = (t3 - t0) - (t2 - t1)
        if (rtt < 0 || rtt > 1000) return
        val offset = ((t1 - t0) + (t2 - t3)) / 2
        samples.addLast(ClockSample(offset, rtt))
        while (samples.size > WINDOW) samples.removeFirst()
    }

    private fun best(): ClockSample? = samples.minByOrNull { it.rtt }

    /** Null until at least one accepted sample. */
    val offset: Long? get() = best()?.offset
    val rtt: Long? get() = best()?.rtt
    val quality: SyncQuality?
        get() = best()?.let {
            when {
                it.rtt <= 60 -> SyncQuality.GOOD
                it.rtt <= 200 -> SyncQuality.OK
                else -> SyncQuality.POOR
            }
        }

    companion object {
        const val WINDOW = 8
    }
}
