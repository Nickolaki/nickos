package com.nbaltas.nickos.master

import com.nbaltas.nickos.data.Hotspot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** The Wi-Fi Direct calls, scripted. Nothing here touches Android. */
class FakeP2p : P2pPort {
    override var onEvent: (() -> Unit)? = null
    @Volatile var supported = true
    @Volatile var permission = true
    @Volatile var location = true
    @Volatile var wifi = true
    @Volatile var tethering = false
    @Volatile var group: Group? = null
    /** True: the system has the group but groupInfo does not show it yet (the broadcast beat the query). */
    @Volatile var hideGroup = false
    @Volatile var groupInfoHangs = false
    val creates = AtomicInteger()
    val removes = AtomicInteger()
    val events = CopyOnWriteArrayList<String>()
    @Volatile var create: suspend (String, String) -> Outcome = { s, p -> bringUp(s, p); Outcome.OK }

    fun bringUp(ssid: String, pass: String) { group = Group(ssid, pass, "192.168.49.1", true) }
    fun fire() { onEvent?.invoke() }

    override fun supported() = supported
    override fun hasPermission() = permission
    override fun locationOn() = location
    override fun wifiOn() = wifi
    override fun tetheringOn() = tethering
    override suspend fun groupInfo(): Group? {
        if (groupInfoHangs) awaitCancellation()
        return if (hideGroup) null else group
    }
    override suspend fun createGroup(ssid: String, pass: String): Outcome {
        creates.incrementAndGet(); events += "create"
        return create(ssid, pass)
    }
    /** The next this many removes fail and leave the group where it is. */
    val removeFails = AtomicInteger()
    /** When set, removeGroup waits for it after being counted (a removal that takes a while). */
    @Volatile var removeGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
    override suspend fun removeGroup(): Boolean {
        removes.incrementAndGet(); events += "remove"
        removeGate?.await()
        if (removeFails.getAndUpdate { if (it > 0) it - 1 else 0 } > 0) return false
        group = null; return true
    }
    override fun close() { events += "close" }
}

class CarNetTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @After fun tearDown() { scope.cancel() }

    private val want = Hotspot("DIRECT-NK-NickOS-test", "testpassphrase01", direct = true)
    private val timing = CarNetPolicy.Timing(retryMs = 20, failedRetryMs = 200, maxFailures = 3, settleMs = 80)
    private val config = CarNet.Config(callTimeoutMs = 150, watchdogMs = 60, timing = timing)

    private fun net(p: FakeP2p, manual: () -> Boolean = { false }, sdk: Int = 34, onUp: (Hotspot) -> Unit = {}) =
        CarNet(p, scope, wanted = { want }, manual = manual, onUp = onUp, sdk = sdk, config = config)

    private fun waitFor(what: String, cond: () -> Boolean) {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!cond()) { check(System.nanoTime() < end) { "timed out: $what" }; Thread.sleep(2) }
    }

    private fun CarNet.isOn() = state.value is CarNetState.On
    private fun CarNet.off() = (state.value as? CarNetState.Off)?.why

    // ---- the policy, as a table ----

    private fun inputs(
        sdk: Int = 34, permission: Boolean = true, location: Boolean = true, wifi: Boolean = true, tethering: Boolean = false,
        manual: Boolean = false, live: Group? = null, failures: Int = 0, lastTryAt: Long = 0, now: Long = 100_000,
        supported: Boolean = true, createdOkAt: Long = 0,
    ) = CarNetPolicy.Inputs(sdk, permission, location, wifi, tethering, manual, live, want, failures, lastTryAt, now, supported, createdOkAt)

    private val ours get() = Group(want.ssid, want.pass, "192.168.49.1", true)

    @Test fun `no group yet means create`() = assertEquals(CarNetPolicy.Action.Create, CarNetPolicy.next(inputs()))

    @Test fun `our own group left over from a crash is adopted not rebuilt`() =
        assertEquals(CarNetPolicy.Action.Nothing(CarNetState.On(want.ssid, "192.168.49.1")), CarNetPolicy.next(inputs(live = ours)))

    @Test fun `our name with an old passphrase is replaced because a node could not join it`() =
        assertEquals(CarNetPolicy.Action.RemoveThenCreate, CarNetPolicy.next(inputs(live = ours.copy(passphrase = "stale"))))

    @Test fun `a group that is not ours is replaced`() =
        assertEquals(CarNetPolicy.Action.RemoveThenCreate, CarNetPolicy.next(inputs(live = ours.copy(name = "DIRECT-xy-Other"))))

    @Test fun `a group we are only a client of is replaced`() =
        assertEquals(CarNetPolicy.Action.RemoveThenCreate, CarNetPolicy.next(inputs(live = ours.copy(isOwner = false))))

    @Test fun `old android and no wifi direct are unsupported not retried`() {
        assertEquals(CarNetPolicy.Action.Nothing(CarNetState.Off(Why.UNSUPPORTED)), CarNetPolicy.next(inputs(sdk = 28, supported = false)))
        assertEquals(CarNetPolicy.Action.Nothing(CarNetState.Off(Why.UNSUPPORTED)), CarNetPolicy.next(inputs(supported = false)))
        assertEquals(CarNetPolicy.Action.Nothing(CarNetState.Off(Why.UNSUPPORTED)), CarNetPolicy.next(inputs(sdk = 23)))
        assertEquals(CarNetPolicy.Action.Nothing(CarNetState.Off(Why.UNSUPPORTED)), CarNetPolicy.next(inputs(sdk = 24, supported = false)))
    }

    // ---- legacy mode (Android 7 to 9): the system picks the name and passphrase ----

    private val sysGroup = Group("DIRECT-ab-Android_1234", "legacypass1", "192.168.49.1", true)

    @Test fun `android 7 creates a group with no permission and no location`() {
        assertEquals(CarNetPolicy.Action.Create, CarNetPolicy.next(inputs(sdk = 24, live = null)))
        assertEquals(CarNetPolicy.Action.Create, CarNetPolicy.next(inputs(sdk = 24, permission = false, location = false, live = null)))
    }

    @Test fun `android 7 adopts the live group and learns its name and passphrase`() =
        assertEquals(CarNetPolicy.Action.Nothing(CarNetState.On("DIRECT-ab-Android_1234", "192.168.49.1", "legacypass1")),
            CarNetPolicy.next(inputs(sdk = 24, live = sysGroup)))

    @Test fun `android 7 replaces a group with no passphrase or one it only joined`() {
        assertEquals(CarNetPolicy.Action.RemoveThenCreate, CarNetPolicy.next(inputs(sdk = 24, live = sysGroup.copy(passphrase = null))))
        assertEquals(CarNetPolicy.Action.RemoveThenCreate, CarNetPolicy.next(inputs(sdk = 24, live = sysGroup.copy(isOwner = false))))
    }

    @Test fun `android 7 never adopts a group with an empty or short passphrase`() {
        for (weak in listOf("", "pw", "1234567")) {
            assertEquals(CarNetPolicy.Action.RemoveThenCreate, CarNetPolicy.next(inputs(sdk = 24, live = sysGroup.copy(passphrase = weak))))
        }
    }

    @Test fun `stopping on android 7 removes the group the system named`() {
        val p = FakeP2p()
        p.group = Group("DIRECT-ab-Android_1234", "legacypass1", "192.168.49.1", true)
        val n = net(p, sdk = 24)
        n.start()
        waitFor("on") { n.isOn() }
        n.stop()
        waitFor("removed") { p.group == null }
    }

    @Test fun `android 9 behaves as android 7 and 10 does not`() {
        assertEquals(CarNetPolicy.Action.Nothing(CarNetState.On("DIRECT-ab-Android_1234", "192.168.49.1", "legacypass1")),
            CarNetPolicy.next(inputs(sdk = 28, live = sysGroup)))
        assertEquals(CarNetPolicy.Action.Create, CarNetPolicy.next(inputs(sdk = 28, permission = false, location = false)))
        assertEquals(CarNetPolicy.Action.Nothing(CarNetState.Off(Why.NO_PERMISSION)), CarNetPolicy.next(inputs(sdk = 29, permission = false)))
    }

    @Test fun `android 7 never loops remove and create on a stable system group`() {
        // The wanted name never matches the system's: comparing it would rebuild the group every minute.
        repeat(3) { assertTrue(CarNetPolicy.next(inputs(sdk = 24, live = sysGroup)) is CarNetPolicy.Action.Nothing) }
    }

    @Test fun `location only matters on android 10 to 12`() {
        assertEquals(CarNetPolicy.Action.Nothing(CarNetState.Off(Why.NO_LOCATION)), CarNetPolicy.next(inputs(sdk = 30, location = false)))
        assertEquals(CarNetPolicy.Action.Create, CarNetPolicy.next(inputs(sdk = 33, location = false)))
    }

    @Test fun `no permission and wifi off are reported before anything is tried`() {
        assertEquals(CarNetPolicy.Action.Nothing(CarNetState.Off(Why.NO_PERMISSION)), CarNetPolicy.next(inputs(permission = false)))
        assertEquals(CarNetPolicy.Action.Nothing(CarNetState.Off(Why.WIFI_OFF)), CarNetPolicy.next(inputs(wifi = false)))
    }

    @Test fun `tethering pauses the group and removes ours`() {
        assertEquals(CarNetPolicy.Action.Nothing(CarNetState.Off(Why.TETHERING_ON)), CarNetPolicy.next(inputs(tethering = true)))
        assertEquals(CarNetPolicy.Action.Remove(CarNetState.Off(Why.TETHERING_ON)), CarNetPolicy.next(inputs(tethering = true, live = ours)))
    }

    @Test fun `a typed in hotspot switches ours off, and never removes someone else's group`() {
        assertEquals(CarNetPolicy.Action.Remove(CarNetState.Off(Why.MANUAL)), CarNetPolicy.next(inputs(manual = true, live = ours)))
        assertEquals(CarNetPolicy.Action.Nothing(CarNetState.Off(Why.MANUAL)),
            CarNetPolicy.next(inputs(manual = true, live = ours.copy(name = "DIRECT-xy-Other"))))
    }

    @Test fun `after a settled ok the missing group is waited for, once`() {
        val a = CarNetPolicy.next(inputs(failures = 1, lastTryAt = 99_990, now = 100_000, createdOkAt = 99_990))
        assertTrue(a is CarNetPolicy.Action.Wait && a.state == CarNetState.Starting)
        assertEquals(CarNetPolicy.Action.Create, CarNetPolicy.next(inputs(failures = 1, lastTryAt = 90_000, now = 100_000, createdOkAt = 90_000)))
    }

    @Test fun `three failures is failed, and it is tried again only after the long wait`() {
        val a = CarNetPolicy.next(inputs(failures = 3, lastTryAt = 99_000, now = 100_000))
        assertTrue(a is CarNetPolicy.Action.Wait && a.state == CarNetState.Off(Why.FAILED))
        assertEquals(CarNetPolicy.Action.Create, CarNetPolicy.next(inputs(failures = 3, lastTryAt = 30_000, now = 100_000)))
    }

    // ---- the loop, against the fake ----

    @Test fun `it comes up, tells the store, and reports the address it read`() {
        val p = FakeP2p()
        val got = CopyOnWriteArrayList<Hotspot>()
        val n = net(p, onUp = { got += it })
        n.start()
        waitFor("on") { n.isOn() }
        assertEquals(CarNetState.On(want.ssid, "192.168.49.1"), n.state.value)
        assertEquals(listOf(want), got.toList())
        assertEquals(1, p.creates.get())
    }

    @Test fun `stop in the middle of a create does not leak the group the system then delivers`() {
        val p = FakeP2p()
        val gate = CompletableDeferred<Unit>()
        // Like the real port: the system makes the group even though the caller was stopped while it waited.
        p.create = { s, pw -> withContext(NonCancellable) { gate.await(); p.bringUp(s, pw) }; Outcome.OK }
        val n = net(p)
        n.start()
        waitFor("create started") { p.creates.get() == 1 }
        n.stop()
        gate.complete(Unit)                      // the system finishes the create after the service is gone
        waitFor("group removed") { p.group == null && p.removes.get() >= 1 }
        waitFor("closed") { "close" in p.events }
        assertFalse(n.isOn())
        assertEquals(1, p.creates.get())         // and the cancelled loop did not go on to create again
    }

    @Test fun `stop then an immediate start removes the old group before making the new one`() {
        val p = FakeP2p()
        val n = net(p)
        n.start()
        waitFor("on") { n.isOn() }
        n.stop()
        n.start()
        waitFor("on again") { n.isOn() && p.creates.get() == 2 }
        val ev = p.events.toList()
        val firstRemove = ev.indexOf("remove")
        val secondCreate = ev.indexOfLast { it == "create" }
        assertTrue("events $ev", firstRemove in 0 until secondCreate)
        assertEquals(want.ssid, p.group?.name)
    }

    @Test fun `stop leaves a group that is not ours alone`() {
        val p = FakeP2p()
        p.group = Group("DIRECT-xy-Other", "x", "192.168.49.1", true)
        p.permission = false                       // so the loop itself never touches it
        val n = net(p)
        n.start()
        waitFor("reported") { n.off() == Why.NO_PERMISSION }
        n.stop()
        waitFor("closed") { "close" in p.events }
        assertEquals(0, p.removes.get())
        assertEquals("DIRECT-xy-Other", p.group?.name)
    }

    @Test fun `a failing create is retried, and three in a row show as failed instead of a loop`() {
        val p = FakeP2p()
        p.create = { _, _ -> Outcome.ERROR }
        val n = net(p)
        n.start()
        waitFor("failed") { n.off() == Why.FAILED }
        assertEquals(3, p.creates.get())
        Thread.sleep(100)                          // well inside the long retry: no more attempts
        assertEquals(3, p.creates.get())
    }

    @Test fun `failed is retried after the long wait and recovers`() {
        val p = FakeP2p()
        val tries = AtomicInteger()
        p.create = { s, pw -> if (tries.incrementAndGet() <= 3) Outcome.ERROR else { p.bringUp(s, pw); Outcome.OK } }
        val n = net(p)
        n.start()
        waitFor("failed") { n.off() == Why.FAILED }
        waitFor("on after the long retry") { n.isOn() }
        assertEquals(4, p.creates.get())
    }

    @Test fun `busy clears the radio before the next try`() {
        val p = FakeP2p()
        val tries = AtomicInteger()
        p.create = { s, pw -> if (tries.incrementAndGet() == 1) Outcome.BUSY else { p.bringUp(s, pw); Outcome.OK } }
        val n = net(p)
        n.start()
        waitFor("on") { n.isOn() }
        assertTrue(p.removes.get() >= 1)
    }

    @Test fun `a phone that ignores the requested name is failed after three, not flapping for ever`() {
        val p = FakeP2p()
        p.create = { _, pw -> p.group = Group("DIRECT-ab-Phone", pw, "192.168.49.1", true); Outcome.OK }
        val n = net(p)
        n.start()
        waitFor("failed") { n.off() == Why.FAILED }
        assertEquals(3, p.creates.get())
    }

    @Test fun `a broadcast that beats group info does not cause a second create`() {
        val p = FakeP2p()
        p.hideGroup = true
        p.create = { s, pw -> p.bringUp(s, pw); Outcome.OK }
        val n = net(p)
        n.start()
        waitFor("created") { p.creates.get() == 1 }
        p.fire(); p.fire()                         // the framework's own broadcasts land before the query can see the group
        Thread.sleep(40)
        assertEquals(1, p.creates.get())
        p.hideGroup = false
        p.fire()
        waitFor("on") { n.isOn() }
        assertEquals(1, p.creates.get())
    }

    @Test fun `wifi off takes the group and it comes back when wifi does`() {
        val p = FakeP2p()
        val n = net(p)
        n.start()
        waitFor("on") { n.isOn() }
        p.wifi = false; p.group = null; p.fire()   // the system removes the group with WiFi
        waitFor("wifi off shown") { n.off() == Why.WIFI_OFF }
        val before = p.creates.get()
        Thread.sleep(80)
        assertEquals(before, p.creates.get())      // nothing is tried while WiFi is off
        p.wifi = true; p.fire()
        waitFor("on again") { n.isOn() }
        assertEquals(before + 1, p.creates.get())
    }

    @Test fun `wifi turned off in the middle of a create ends in off, not a stuck starting`() {
        val p = FakeP2p()
        val gate = CompletableDeferred<Unit>()
        p.create = { _, _ -> gate.await(); Outcome.ERROR }
        val n = net(p)
        n.start()
        waitFor("create started") { p.creates.get() == 1 }
        p.wifi = false; p.fire()
        gate.complete(Unit)
        waitFor("wifi off shown") { n.off() == Why.WIFI_OFF }
    }

    @Test fun `tethering pauses the car network once, with no retry timer, and resumes by itself`() {
        val p = FakeP2p()
        val n = net(p)
        n.start()
        waitFor("on") { n.isOn() }
        p.tethering = true; p.fire()
        waitFor("paused") { n.off() == Why.TETHERING_ON }
        waitFor("group removed") { p.removes.get() >= 1 }
        val creates = p.creates.get()
        val removes = p.removes.get()
        Thread.sleep(200)                          // several watchdog laps
        assertEquals(creates, p.creates.get())
        assertEquals(removes, p.removes.get())
        p.tethering = false; p.fire()
        waitFor("back") { n.isOn() }
    }

    @Test fun `typing a hotspot in removes our group and clearing it brings it back`() {
        val p = FakeP2p()
        var manual = false
        val n = net(p, manual = { manual })
        n.start()
        waitFor("on") { n.isOn() }
        manual = true; n.poke()
        waitFor("manual") { n.off() == Why.MANUAL && p.group == null }
        manual = false; n.poke()
        waitFor("back") { n.isOn() }
    }

    @Test fun `a manual switch racing a group coming up leaves no group behind`() {
        val p = FakeP2p()
        val gate = CompletableDeferred<Unit>()
        var manual = false
        p.create = { s, pw -> gate.await(); p.bringUp(s, pw); Outcome.OK }
        val n = net(p, manual = { manual })
        n.start()
        waitFor("create started") { p.creates.get() == 1 }
        manual = true; n.poke()
        gate.complete(Unit)
        waitFor("manual and removed") { n.off() == Why.MANUAL && p.group == null }
    }

    @Test fun `a call that never answers does not hang the loop`() {
        val p = FakeP2p()
        p.groupInfoHangs = true
        val n = net(p)
        n.start()
        waitFor("it keeps trying past the hung query") { p.creates.get() >= 1 }
        p.groupInfoHangs = false
        waitFor("on") { n.isOn() }
    }

    @Test fun `android 7 stores the credentials the system chose and follows a rotation`() {
        val p = FakeP2p()
        p.permission = false; p.location = false
        val calls = CopyOnWriteArrayList<Pair<String, String>>()
        p.create = { s, pw -> calls += s to pw; p.group = Group("DIRECT-ab-Android_1234", "sysPass1", "192.168.49.1", true); Outcome.OK }
        val ups = CopyOnWriteArrayList<Hotspot>()
        val n = net(p, sdk = 24, onUp = { ups += it })
        n.start()
        waitFor("first up") { ups.size == 1 }
        assertEquals(1, calls.size)
        assertEquals(Hotspot("DIRECT-ab-Android_1234", "sysPass1", direct = true), ups[0])
        assertTrue(ups[0] != want)
        assertEquals(CarNetState.On("DIRECT-ab-Android_1234", "192.168.49.1", "sysPass1"), n.state.value)
        // The system hands back a different network (a Samsung reboot, say): the Store must learn it.
        p.group = Group("DIRECT-cd-Android_9999", "sysPass2", "192.168.49.1", true)
        n.poke()
        waitFor("second up") { ups.size == 2 }
        assertEquals(Hotspot("DIRECT-cd-Android_9999", "sysPass2", direct = true), ups[1])
        assertEquals(CarNetState.On("DIRECT-cd-Android_9999", "192.168.49.1", "sysPass2"), n.state.value)
        assertEquals(1, p.creates.get())
        assertTrue(n.permissionToRequest().isEmpty())
    }

    @Test fun `a create that never answers counts as a failed try`() {
        val p = FakeP2p()
        p.create = { _, _ -> awaitCancellation() }
        val n = net(p)
        n.start()
        waitFor("failed") { n.off() == Why.FAILED }
    }

    @Test fun `a permission answer clears past failures and tries at once`() {
        val p = FakeP2p()
        p.permission = false
        val n = net(p)
        n.start()
        waitFor("needs permission") { n.off() == Why.NO_PERMISSION }
        assertEquals(listOf(CarNet.NEARBY_WIFI_DEVICES), n.permissionToRequest())
        p.permission = true
        n.recheck()
        waitFor("on") { n.isOn() }
        assertTrue(n.permissionToRequest().isEmpty())
    }

    @Test fun `the permissions to ask for follow the android version`() {
        val p = FakeP2p(); p.permission = false
        val fine = CarNet.ACCESS_FINE_LOCATION
        val coarse = CarNet.ACCESS_COARSE_LOCATION
        assertEquals(listOf(fine), net(p, sdk = 29).permissionToRequest())
        assertEquals(listOf(fine), net(p, sdk = 30).permissionToRequest())
        // Android 12 ignores a fine-only request, so coarse goes in the same prompt.
        assertEquals(listOf(fine, coarse), net(p, sdk = 31).permissionToRequest())
        assertEquals(listOf(fine, coarse), net(p, sdk = 32).permissionToRequest())
        assertEquals(listOf(CarNet.NEARBY_WIFI_DEVICES), net(p, sdk = 33).permissionToRequest())
        assertEquals(listOf(CarNet.NEARBY_WIFI_DEVICES), net(p, sdk = 34).permissionToRequest())
        assertTrue(net(p, sdk = 28).permissionToRequest().isEmpty())
        p.permission = true
        assertTrue(net(p, sdk = 31).permissionToRequest().isEmpty())
    }

    @Test fun `stop removes the group even when the shutdown query times out`() {
        val p = FakeP2p()
        val n = net(p)
        n.start()
        waitFor("on") { n.isOn() }
        p.groupInfoHangs = true                   // the query at shutdown never answers
        n.stop()
        waitFor("removed anyway") { p.group == null }
        waitFor("closed") { "close" in p.events }
    }

    @Test fun `start twice is one loop`() {
        val p = FakeP2p()
        val n = net(p)
        n.start(); n.start(); n.start()
        waitFor("on") { n.isOn() }
        assertEquals(1, p.creates.get())
    }

    @Test fun `the state never carries the passphrase`() {
        val p = FakeP2p()
        val n = net(p)
        n.start()
        waitFor("on") { n.isOn() }
        assertFalse(n.state.value.toString().contains(want.pass))
    }

    @Test fun `a dropped group is noticed by the watchdog even with no broadcast`() {
        val p = FakeP2p()
        val n = net(p)
        n.start()
        waitFor("on") { n.isOn() }
        p.group = null                             // dropped quietly, no event
        waitFor("rebuilt") { p.creates.get() == 2 && n.isOn() }
    }
}
