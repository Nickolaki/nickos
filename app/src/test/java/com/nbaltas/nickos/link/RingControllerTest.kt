package com.nbaltas.nickos.link

import androidx.test.core.app.ApplicationProvider
import com.nbaltas.nickos.data.Store
import com.nbaltas.nickos.node.AlarmRecovery
import com.nbaltas.nickos.node.PinGate
import com.nbaltas.nickos.node.PinResult
import com.nbaltas.nickos.node.RingController
import com.nbaltas.nickos.node.RingMachine
import com.nbaltas.nickos.node.RingPort
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RingControllerTest {
    private class FakePort(var volume: Int = 3) : RingPort {
        val log = CopyOnWriteArrayList<String>()
        @Volatile var backup: Int? = null
        @Volatile var holdStop: CountDownLatch? = null
        @Volatile var stopEntered = CountDownLatch(0)
        override fun alarmVolume(): Int { log += "read $volume"; return volume }
        override fun setAlarmVolume(v: Int) { log += "set $v"; volume = v }
        override fun saveVolumeBackup(v: Int) { log += "save $v"; backup = v }
        override fun clearVolumeBackup() { log += "clear"; backup = null }
        override fun wake() { log += "wake" }
        override fun startAlarm() { log += "start"; volume = 15 }
        override fun stopAlarm() { log += "stop"; stopEntered.countDown(); holdStop?.await(5, TimeUnit.SECONDS) }
        override fun shown(ringing: Boolean) { log += "shown $ringing" }
    }

    @Test fun `the volume is saved to disk before it is raised`() {
        val port = FakePort(volume = 4)
        RingController(port, { 0L }).start()
        assertTrue(port.log.indexOf("save 4") in 0 until port.log.indexOf("start"))
        assertEquals(4, port.backup)
        assertEquals(15, port.volume)
    }

    @Test fun `stopping puts the volume back and drops the copy`() {
        val port = FakePort(volume = 4)
        val c = RingController(port, { 0L })
        c.start()
        assertTrue(c.stop(RingMachine.Stop.TAP))
        assertEquals(4, port.volume)
        assertNull(port.backup)
        assertFalse("a second stop does nothing", c.stop(RingMachine.Stop.TAP))
    }

    @Test fun `a volume left behind by a killed process is put back once`() {
        var set: Int? = null
        var cleared = false
        assertTrue(AlarmRecovery.recover(6, { set = it }, { cleared = true }))
        assertEquals(6, set); assertTrue(cleared)
        set = null; cleared = false
        assertFalse(AlarmRecovery.recover(null, { set = it }, { cleared = true }))
        assertNull(set); assertFalse(cleared)
    }

    @Test fun `the saved volume survives a reset and the pause owner does not`() {
        val store = Store(ApplicationProvider.getApplicationContext())
        assertTrue(store.setSavedAlarmVolume(7))
        store.setPausedOwner("A"); store.setPausedMsg("Time to eat")
        store.resetAll()
        assertEquals(7, store.savedAlarmVolume())
        assertNull(store.pausedOwner())
        assertNull(store.pausedMsg.value)
        assertTrue(store.setSavedAlarmVolume(null))
        assertNull(store.savedAlarmVolume())
    }

    @Test fun `the cap follows the clock it is given and a clock that steps back does not extend it`() {
        var now = 1_000_000L
        val port = FakePort()
        val c = RingController(port, { now })
        c.start()
        now += 59_000
        assertFalse(c.tick())
        now += 1_000
        assertTrue(c.tick())
        assertFalse(c.ringing)
        assertEquals(3, port.volume)
        // A wall clock stepping back is not part of this clock: the elapsed time still only goes forward.
        now = 2_000_000L
        c.start()
        now += 60_000
        assertTrue(c.tick())
    }

    @Test fun `a restart cannot slip in between a stop and its device effects`() {
        val port = FakePort(volume = 4)
        val c = RingController(port, { 0L })
        c.start()
        port.holdStop = CountDownLatch(1)
        port.stopEntered = CountDownLatch(1)
        port.log.clear()
        val stopper = Thread { c.stop(RingMachine.Stop.PARENT) }
        stopper.start()
        assertTrue(port.stopEntered.await(5, TimeUnit.SECONDS))
        val starter = Thread { c.start() }
        starter.start()
        Thread.sleep(300)
        assertEquals("the new ring waits for the old stop to finish", listOf("stop"), port.log.toList())
        port.holdStop!!.countDown()
        stopper.join(5000); starter.join(5000)
        assertEquals(
            listOf("stop", "set 4", "clear", "shown false", "read 4", "save 4", "wake", "start", "shown true"),
            port.log.toList(),
        )
        assertEquals("the second ring saved the real volume, not the raised one", 4, port.backup)
        assertTrue(c.ringing)
    }

    @Test fun `a ring id only stays current until it stops or another begins`() {
        val c = RingController(FakePort(), { 0L })
        val first = c.start()
        assertTrue(first > 0 && c.isCurrent(first))
        assertEquals("already ringing", 0L, c.start())
        c.stop(RingMachine.Stop.TAP)
        assertFalse(c.isCurrent(first))
        val second = c.start()
        assertTrue(second > first && c.isCurrent(second) && !c.isCurrent(first))
    }

    @Test fun `the right PIN passes the gate that clears the pause and a wrong one does not`() {
        val gate = PinGate()
        assertTrue(gate.tryPin("1111", "2468", 0) is PinResult.Wrong)
        assertTrue(gate.tryPin("2468", "2468", 1) is PinResult.Ok)
    }
}
