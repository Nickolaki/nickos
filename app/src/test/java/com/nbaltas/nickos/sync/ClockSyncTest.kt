package com.nbaltas.nickos.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClockSyncTest {

    @Test
    fun `symmetric samples give the exact offset`() {
        val sync = ClockSync()
        val id = sync.nextPingId()
        // true offset 50 ms, 10 ms each way: t1=1060, t2=1060 (no node-side processing), t3=1020
        sync.onPong(id, t0 = 1000, t1 = 1060, t2 = 1060, t3 = 1020)
        assertEquals(50L, sync.offset)
        assertEquals(20L, sync.rtt)
    }

    @Test
    fun `lowest rtt sample wins even when it is not the latest`() {
        val sync = ClockSync()
        val a = sync.nextPingId()
        sync.onPong(a, t0 = 1000, t1 = 1060, t2 = 1060, t3 = 1020) // rtt 20, offset 50
        val b = sync.nextPingId()
        sync.onPong(b, t0 = 2000, t1 = 2200, t2 = 2300, t3 = 2500) // rtt (500)-(100)=400, offset ((200)+(-200))/2=0
        assertEquals(50L, sync.offset)
        assertEquals(20L, sync.rtt)
    }

    @Test
    fun `rtt over 1000ms is discarded`() {
        val sync = ClockSync()
        val id = sync.nextPingId()
        sync.onPong(id, t0 = 0, t1 = 2000, t2 = 2000, t3 = 2000) // rtt = 2000
        assertNull(sync.offset)
    }

    @Test
    fun `negative rtt is discarded`() {
        val sync = ClockSync()
        val id = sync.nextPingId()
        // rtt = (t3-t0)-(t2-t1) = (10-0)-(100-0) = -90
        sync.onPong(id, t0 = 0, t1 = 0, t2 = 100, t3 = 10)
        assertNull(sync.offset)
    }

    @Test
    fun `window keeps only the last 8 samples`() {
        val sync = ClockSync()
        // Sample 0 has the lowest rtt of all, but it must fall out of an 8-deep window.
        val firstId = sync.nextPingId()
        sync.onPong(firstId, t0 = 1000, t1 = 1005, t2 = 1005, t3 = 1001) // rtt ~0-ish, offset ~4-5
        repeat(8) {
            val id = sync.nextPingId()
            sync.onPong(id, t0 = 5000, t1 = 5250, t2 = 5250, t3 = 5100) // rtt 100, offset 200
        }
        // 9 pongs accepted total, window keeps the last 8: sample 0 is evicted.
        assertEquals(200L, sync.offset)
        assertEquals(100L, sync.rtt)
    }

    @Test
    fun `quality thresholds`() {
        val good = ClockSync().apply { val id = nextPingId(); onPong(id, 0, 60, 60, 60) }
        val ok = ClockSync().apply { val id = nextPingId(); onPong(id, 0, 150, 150, 150) }
        val poor = ClockSync().apply { val id = nextPingId(); onPong(id, 0, 500, 500, 500) }
        assertEquals(SyncQuality.GOOD, good.quality)
        assertEquals(SyncQuality.OK, ok.quality)
        assertEquals(SyncQuality.POOR, poor.quality)
    }

    @Test
    fun `offset is null before any sample`() {
        val sync = ClockSync()
        assertNull(sync.offset)
        assertNull(sync.rtt)
        assertNull(sync.quality)
    }

    @Test
    fun `a pong with an unknown id is ignored`() {
        val sync = ClockSync()
        sync.nextPingId() // id 0 sent
        sync.onPong(99, t0 = 1000, t1 = 1060, t2 = 1060, t3 = 1020)
        assertNull(sync.offset)
    }
}
