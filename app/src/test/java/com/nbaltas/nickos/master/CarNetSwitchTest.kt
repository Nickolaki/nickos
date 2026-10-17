package com.nbaltas.nickos.master

import androidx.test.core.app.ApplicationProvider
import com.nbaltas.nickos.App
import com.nbaltas.nickos.data.Hotspot
import com.nbaltas.nickos.data.Role
import com.nbaltas.nickos.data.Store
import com.nbaltas.nickos.masterBackLeaves
import com.nbaltas.nickos.ui.master.CARNET_SWITCH_HELP
import com.nbaltas.nickos.ui.master.carNetBannerWhy
import com.nbaltas.nickos.ui.master.carNetCopy
import com.nbaltas.nickos.ui.master.carNetWhy
import com.nbaltas.nickos.ui.master.offlineTips
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/** The parent's car network switch: policy, loop, words, and the Back rule for a Master that is not the home screen. */
class CarNetSwitchTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @After fun tearDown() { scope.cancel() }

    private val want = Hotspot("DIRECT-NK-NickOS-test", "testpassphrase01", direct = true)
    private val timing = CarNetPolicy.Timing(retryMs = 20, failedRetryMs = 200, maxFailures = 3, settleMs = 80)
    private val config = CarNet.Config(callTimeoutMs = 150, watchdogMs = 60, timing = timing)

    @Volatile private var off = false
    @Volatile private var manual = false

    private fun net(p: FakeP2p, sdk: Int = 34) =
        CarNet(p, scope, wanted = { want }, manual = { manual }, off = { off }, sdk = sdk, config = config)

    private fun waitFor(what: String, cond: () -> Boolean) {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!cond()) { check(System.nanoTime() < end) { "timed out: $what" }; Thread.sleep(2) }
    }

    private fun CarNet.isOn() = state.value is CarNetState.On
    private fun CarNet.why() = (state.value as? CarNetState.Off)?.why

    private val ours get() = Group(want.ssid, want.pass, "192.168.49.1", true)
    private val sysGroup = Group("DIRECT-ab-Android_1234", "legacypass1", "192.168.49.1", true)

    private fun inputs(sdk: Int = 34, permission: Boolean = true, manual: Boolean = false, live: Group? = null, turnedOff: Boolean = true,
                       wifi: Boolean = true, failures: Int = 0, location: Boolean = true) =
        CarNetPolicy.Inputs(sdk, permission, location, wifi, false, manual, live, want, failures, 0, 100_000, turnedOff = turnedOff)

    // ---- the policy ----

    @Test fun `off with no group is off and creates nothing`() =
        assertEquals(CarNetPolicy.Action.Nothing(CarNetState.Off(Why.TURNED_OFF)), CarNetPolicy.next(inputs()))

    @Test fun `off with our group removes it, new and legacy`() {
        assertEquals(CarNetPolicy.Action.Remove(CarNetState.Off(Why.TURNED_OFF)), CarNetPolicy.next(inputs(live = ours)))
        assertEquals(CarNetPolicy.Action.Remove(CarNetState.Off(Why.TURNED_OFF)), CarNetPolicy.next(inputs(sdk = 24, live = sysGroup)))
        assertEquals(CarNetPolicy.Action.Remove(CarNetState.Off(Why.TURNED_OFF)), CarNetPolicy.next(inputs(sdk = 28, live = sysGroup)))
    }

    @Test fun `off leaves a group that is not ours alone`() =
        assertEquals(CarNetPolicy.Action.Nothing(CarNetState.Off(Why.TURNED_OFF)), CarNetPolicy.next(inputs(live = ours.copy(name = "DIRECT-xy-Other"))))

    @Test fun `off beats a typed hotspot`() {
        assertEquals(CarNetPolicy.Action.Nothing(CarNetState.Off(Why.TURNED_OFF)), CarNetPolicy.next(inputs(manual = true)))
        assertEquals(CarNetPolicy.Action.Remove(CarNetState.Off(Why.TURNED_OFF)), CarNetPolicy.next(inputs(manual = true, live = ours)))
    }

    @Test fun `off never creates or waits whatever else is true`() {
        for (sdk in listOf(24, 28, 29, 30, 33, 34)) for (perm in listOf(true, false)) for (wifi in listOf(true, false))
            for (live in listOf(null, ours, sysGroup)) for (failures in listOf(0, 1, 3)) for (loc in listOf(true, false)) {
                val a = CarNetPolicy.next(inputs(sdk = sdk, permission = perm, wifi = wifi, live = live, failures = failures, location = loc))
                assertFalse("$a", a is CarNetPolicy.Action.Create || a is CarNetPolicy.Action.RemoveThenCreate || a is CarNetPolicy.Action.Wait)
            }
    }

    @Test fun `on again goes through the normal checks`() {
        assertEquals(CarNetPolicy.Action.Create, CarNetPolicy.next(inputs(turnedOff = false)))
        assertEquals(CarNetPolicy.Action.Nothing(CarNetState.Off(Why.NO_PERMISSION)), CarNetPolicy.next(inputs(turnedOff = false, permission = false)))
        assertEquals(CarNetPolicy.Action.Nothing(CarNetState.Off(Why.NO_LOCATION)), CarNetPolicy.next(inputs(sdk = 30, turnedOff = false, location = false)))
        assertEquals(CarNetPolicy.Action.Nothing(CarNetState.Off(Why.WIFI_OFF)), CarNetPolicy.next(inputs(turnedOff = false, wifi = false)))
        assertEquals(CarNetPolicy.Action.Nothing(CarNetState.Off(Why.MANUAL)), CarNetPolicy.next(inputs(turnedOff = false, manual = true)))
    }

    // ---- the loop ----

    @Test fun `starting while off never creates a group`() {
        val p = FakeP2p()
        off = true
        val n = net(p)
        n.start()
        waitFor("off") { n.why() == Why.TURNED_OFF }
        Thread.sleep(200)                          // several watchdog laps
        assertEquals(0, p.creates.get())
        assertEquals(0, p.removes.get())
    }

    @Test fun `switching off removes our group and nothing is made again`() {
        val p = FakeP2p()
        val n = net(p)
        n.start()
        waitFor("on") { n.isOn() }
        off = true; n.poke()
        waitFor("off and removed") { n.why() == Why.TURNED_OFF && p.group == null }
        Thread.sleep(150)
        assertEquals(1, p.creates.get())
    }

    @Test fun `switching off in the middle of a create removes the group that shows up`() {
        val p = FakeP2p()
        val gate = CompletableDeferred<Unit>()
        p.create = { s, pw -> gate.await(); p.bringUp(s, pw); Outcome.OK }
        val n = net(p)
        n.start()
        waitFor("create started") { p.creates.get() == 1 }
        off = true; n.poke()
        gate.complete(Unit)
        waitFor("off and removed") { n.why() == Why.TURNED_OFF && p.group == null }
        Thread.sleep(150)
        assertEquals(1, p.creates.get())
    }

    @Test fun `switching off while the removal before a create is running starts no create`() {
        val p = FakeP2p()
        p.group = Group("DIRECT-zz-other", "otherpass12", "192.168.49.1", true)   // something in the way: RemoveThenCreate
        val gate = CompletableDeferred<Unit>()
        p.removeGate = gate
        val n = net(p)
        n.start()
        waitFor("removal started") { p.removes.get() >= 1 }
        off = true; n.poke()
        gate.complete(Unit)
        waitFor("off") { n.why() == Why.TURNED_OFF }
        Thread.sleep(200)
        assertEquals(0, p.creates.get())
    }

    @Test fun `off beats unsupported, and says nothing to remove`() {
        assertEquals(CarNetPolicy.Action.Nothing(CarNetState.Off(Why.TURNED_OFF)),
            CarNetPolicy.next(inputs().copy(supported = false)))
        assertEquals(CarNetPolicy.Action.Nothing(CarNetState.Off(Why.TURNED_OFF)),
            CarNetPolicy.next(inputs(sdk = 23)))
        assertEquals(CarNetPolicy.Action.Nothing(CarNetState.Off(Why.UNSUPPORTED)),
            CarNetPolicy.next(inputs(turnedOff = false).copy(supported = false)))
    }

    @Test fun `with the switch off the card and the reasons ignore stale runtime state`() {
        val stale = listOf(CarNetState.On("DIRECT-NK-NickOS-test", "192.168.49.1", "testpassphrase01"), CarNetState.Off(Why.NO_PERMISSION), CarNetState.Starting)
        for (s in stale) {
            val eff = effectiveCarNetState(s, false)
            assertEquals(CarNetState.Off(Why.TURNED_OFF), eff)
            val copy = carNetCopy(eff, null, 34)
            assertEquals("Car network is off", copy.title)
            assertNull(copy.primary)
            val reason = OnlinePolicy.reason(eff, false, null, true, 0, 1_000)
            assertEquals(Reason.CarNetDown(Why.TURNED_OFF), reason)
            assertNull(carNetBannerWhy(listOf(reason)))
        }
        assertEquals(stale[0], effectiveCarNetState(stale[0], true))
    }

    @Test fun `a remove that fails is retried and nothing is created`() {
        val p = FakeP2p()
        val n = net(p)
        n.start()
        waitFor("on") { n.isOn() }
        p.removeFails.set(2)
        off = true; n.poke()
        waitFor("removed at last") { p.group == null && n.why() == Why.TURNED_OFF }
        assertTrue(p.removes.get() >= 3)
        Thread.sleep(150)
        assertEquals(1, p.creates.get())
    }

    @Test fun `switching back on makes the group again and clears old failures`() {
        val p = FakeP2p()
        off = true
        val n = net(p)
        n.start()
        waitFor("off") { n.why() == Why.TURNED_OFF }
        off = false; n.recheck()
        waitFor("on") { n.isOn() }
        assertEquals(1, p.creates.get())
    }

    @Test fun `switching back on without permission says so instead of making a group`() {
        val p = FakeP2p()
        p.permission = false
        off = true
        val n = net(p)
        n.start()
        waitFor("off") { n.why() == Why.TURNED_OFF }
        off = false; n.recheck()
        waitFor("needs permission") { n.why() == Why.NO_PERMISSION }
        assertEquals(0, p.creates.get())
    }

    @Test fun `off with a typed hotspot still says switched off`() {
        val p = FakeP2p()
        off = true; manual = true
        val n = net(p)
        n.start()
        waitFor("off") { n.why() == Why.TURNED_OFF }
    }

    // ---- who is told ----

    @Test fun `offline tablets get the switched off reason, except with a typed hotspot`() {
        val st = CarNetState.Off(Why.TURNED_OFF)
        assertEquals(Reason.CarNetDown(Why.TURNED_OFF), OnlinePolicy.reason(st, false, null, true, 0, 1_000))
        assertEquals(Reason.MasterNotOnWifi, OnlinePolicy.reason(st, true, null, true, 0, 1_000))
    }

    @Test fun `the home banner is never shown for the parent's own switch`() {
        assertNull(carNetBannerWhy(listOf(Reason.CarNetDown(Why.TURNED_OFF))))
        assertEquals(Why.WIFI_OFF, carNetBannerWhy(listOf(Reason.CarNetDown(Why.WIFI_OFF))))
    }

    @Test fun `the card and the tip say it is off and where to turn it on`() {
        val c = carNetCopy(CarNetState.Off(Why.TURNED_OFF), null, 34)
        assertEquals("Car network is off", c.title)
        assertNull(c.primary)
        assertTrue(c.ghost != null)
        assertFalse(c.fields)
        assertTrue(carNetCopy(CarNetState.Off(Why.TURNED_OFF), Hotspot("Dad's phone", "password1"), 34).fields)
        assertEquals("you switched it off", carNetWhy(Why.TURNED_OFF))
        val tip = offlineTips(Reason.CarNetDown(Why.TURNED_OFF), null).single().second
        assertTrue(tip, "switched off" in tip && "Settings, Tablets" in tip)
        assertEquals("Turning this off can save battery. Kid tablets then need your home WiFi or a hotspot.", CARNET_SWITCH_HELP)
    }

    // ---- Back ----

    @Test fun `back leaves only for a master that is not the home screen`() {
        assertTrue(masterBackLeaves(Role.MASTER, false))
        assertFalse(masterBackLeaves(Role.MASTER, true))
        assertFalse(masterBackLeaves(Role.MASTER, null))
        assertFalse(masterBackLeaves(Role.NODE, false))
        assertFalse(masterBackLeaves(Role.NONE, false))
        assertFalse(masterBackLeaves(Role.NODE, null))
    }
}

/** The switch in the Store: on by default, and kept. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StoreCarNetSwitchTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<App>()

    @Test fun `on by default, kept across a restart, and back on after a reset`() {
        val store = ctx.store
        assertTrue(store.carNetOn.value)
        store.setCarNetOn(false)
        assertFalse(store.carNetOn.value)
        // The value is in the preferences by the time the flow says so (the write is committed before it is published).
        assertFalse(ctx.getSharedPreferences("nickos", android.content.Context.MODE_PRIVATE).getBoolean("carNetOn", true))
        assertFalse(Store(ctx).carNetOn.value)     // a fresh Store (a restart) reads off before anything can create a group
        store.resetAll()
        assertTrue(store.carNetOn.value)
        assertTrue(Store(ctx).carNetOn.value)
    }
}
