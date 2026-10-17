package com.nbaltas.nickos

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.nbaltas.nickos.data.Role
import com.nbaltas.nickos.data.Store
import com.nbaltas.nickos.node.AdminReceiver
import com.nbaltas.nickos.node.KioskPolicy
import com.nbaltas.nickos.node.KioskState
import com.nbaltas.nickos.node.NodeDevice
import com.nbaltas.nickos.node.Provisioning
import com.nbaltas.nickos.node.StartScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

private class FakeEntryPort(main: Boolean = true, parent: Boolean = false, val failOn: Set<String> = emptySet()) : EntryPort {
    val state = mutableMapOf(Entry.MAIN to main, Entry.PARENT to parent)
    val writes = mutableListOf<String>()
    override fun enabled(e: Entry) = state.getValue(e)
    override fun setEnabled(e: Entry, on: Boolean): Boolean {
        val tag = "${e.name}:${if (on) "on" else "off"}"
        writes += tag
        if (tag in failOn) return false
        state[e] = on
        return true
    }
}

/** Which door the app uses: only a parent tablet that did not opt in to the home screen uses ParentActivity. */
class EntryPolicyTest {
    @Test fun `every role and opt in and provisioned case`() {
        for (role in Role.values()) for (opt in listOf(false, true)) for (prov in listOf(false, true)) {
            val want = if (role == Role.MASTER && !opt && !prov) Entry.PARENT else Entry.MAIN
            assertEquals("$role $opt $prov", want, EntryPolicy.wanted(role, opt, prov))
        }
    }

    @Test fun `kid tablets never change component state`() {
        for (role in listOf(Role.NODE, Role.NONE)) for (opt in listOf(false, true)) for (prov in listOf(false, true)) {
            val p = FakeEntryPort()
            assertTrue(EntryFlow.converge(p, EntryPolicy.wanted(role, opt, prov)))
            assertEquals("$role $opt $prov", emptyList<String>(), p.writes)
        }
    }

    @Test fun `a fresh or provisioned tablet is on the plain door and a parent without opt in is not`() {
        assertEquals(Entry.MAIN, EntryPolicy.wanted(Role.NONE, false))
        assertEquals(Entry.MAIN, EntryPolicy.wanted(Role.NONE, false, true))
        assertEquals(Entry.PARENT, EntryPolicy.wanted(Role.MASTER, false))
        assertEquals(Entry.MAIN, EntryPolicy.wanted(Role.MASTER, true))
    }

    @Test fun `a launcher mode kid tablet after the update sees the kid lock setup`() {
        val s = KioskState(deviceOwner = false, home = false, overlay = true, usage = true, admin = true)
        assertTrue(KioskPolicy.setupNeeded(s))
        assertFalse(KioskPolicy.setupNeeded(s.copy(home = true)))
    }
}

/** The door switching order, with a fake package manager. */
class EntryFlowTest {
    @Test fun `entry activity is whichever is on, the plain one first`() {
        assertEquals(Entry.MAIN, EntryFlow.current(FakeEntryPort(main = true, parent = false)))
        assertEquals(Entry.PARENT, EntryFlow.current(FakeEntryPort(main = false, parent = true)))
        assertEquals(Entry.MAIN, EntryFlow.current(FakeEntryPort(main = true, parent = true)))
        assertEquals(Entry.MAIN, EntryFlow.current(FakeEntryPort(main = false, parent = false)))
    }

    @Test fun `saving the parent role enables, starts, finishes, then disables`() {
        val p = FakeEntryPort()
        val log = mutableListOf<String>()
        val ok = EntryFlow.reconcile(
            p, Entry.PARENT, Entry.MAIN, Role.MASTER,
            start = { log += "start ${it.name} after ${p.writes}"; true },
            finish = { log += "finish after ${p.writes}" },
        )
        assertTrue(ok)
        assertEquals(listOf("PARENT:on", "MAIN:off"), p.writes)
        assertEquals(listOf("start PARENT after [PARENT:on]", "finish after [PARENT:on]"), log)
        assertEquals(Entry.PARENT, EntryFlow.current(p))
    }

    @Test fun `if the new door cannot start both stay on and nothing is finished`() {
        val p = FakeEntryPort()
        var finished = false
        assertFalse(EntryFlow.reconcile(p, Entry.PARENT, Entry.MAIN, Role.MASTER, start = { false }, finish = { finished = true }))
        assertFalse(finished)
        assertEquals(listOf("PARENT:on"), p.writes)
        assertTrue(p.enabled(Entry.MAIN))
    }

    @Test fun `if the new door cannot be enabled nothing is started and the old one stays`() {
        val p = FakeEntryPort(failOn = setOf("PARENT:on"))
        var started = false
        assertFalse(EntryFlow.reconcile(p, Entry.PARENT, Entry.MAIN, Role.MASTER, start = { started = true; true }, finish = {}))
        assertFalse(started)
        assertTrue(p.enabled(Entry.MAIN))
    }

    @Test fun `a reset re enables the plain door first`() {
        val p = FakeEntryPort(main = false, parent = true)
        val ok = EntryFlow.reconcile(p, Entry.MAIN, Entry.PARENT, Role.NONE, start = { true }, finish = {})
        assertTrue(ok)
        assertEquals(listOf("MAIN:on", "PARENT:off"), p.writes)
    }

    @Test fun `opting in only turns the plain door on and leaves the running parent door`() {
        val p = FakeEntryPort(main = false, parent = true)
        var started = false
        assertTrue(EntryFlow.reconcile(p, Entry.MAIN, Entry.PARENT, Role.MASTER, start = { started = true; true }, finish = { started = true }))
        assertFalse(started)
        assertEquals(listOf("MAIN:on"), p.writes)
        assertTrue(p.enabled(Entry.PARENT))
        // Once the plain door runs, it turns the parent door off.
        assertTrue(EntryFlow.reconcile(p, Entry.MAIN, Entry.MAIN, Role.MASTER, start = { true }, finish = {}))
        assertFalse(p.enabled(Entry.PARENT))
        assertTrue(p.enabled(Entry.MAIN))
    }

    @Test fun `opting out switches back to the parent door in order`() {
        val p = FakeEntryPort(main = true, parent = false)
        assertTrue(EntryFlow.reconcile(p, Entry.PARENT, Entry.MAIN, Role.MASTER, start = { true }, finish = {}))
        assertEquals(listOf("PARENT:on", "MAIN:off"), p.writes)
    }

    @Test fun `converge is idempotent`() {
        val p = FakeEntryPort(main = false, parent = true)
        assertTrue(EntryFlow.converge(p, Entry.PARENT))
        assertTrue(EntryFlow.converge(p, Entry.PARENT))
        assertEquals(emptyList<String>(), p.writes)
        val q = FakeEntryPort()
        assertTrue(EntryFlow.converge(q, Entry.PARENT))
        assertTrue(EntryFlow.converge(q, Entry.PARENT))
        assertEquals(listOf("PARENT:on", "MAIN:off"), q.writes)
    }

    @Test fun `converge never leaves both doors off`() {
        val p = FakeEntryPort(failOn = setOf("PARENT:on"))
        assertFalse(EntryFlow.converge(p, Entry.PARENT))
        assertTrue(p.enabled(Entry.MAIN))
    }
}

/** MainActivity exactly as on main, ParentActivity launcher only. Read from the source manifest. */
class HomeManifestTest {
    private val xml = File("src/main/AndroidManifest.xml").readText()
    private fun activity(name: String) = Regex("<activity\\s[^>]*?android:name=\"\\.$name\"[\\s\\S]*?</activity>").find(xml)!!.value

    @Test fun `home is only on the main activity, as on main`() {
        val main = activity("MainActivity")
        assertTrue(main, "android.intent.category.HOME" in main && "android.intent.category.DEFAULT" in main)
        assertTrue(main, "android.intent.category.LAUNCHER" in main)
        assertFalse(main, "android:enabled" in main)
        assertEquals(2, Regex("<intent-filter>").findAll(main).count())
        // The only HOME filter in the app, apart from the <queries> entry that asks about launchers.
        val outsideQueries = xml.replace(Regex("<queries>[\\s\\S]*?</queries>"), "")
        assertEquals(1, Regex("category\\.HOME").findAll(outsideQueries).count())
    }

    @Test fun `the parent activity is launcher only, off and exported`() {
        val parent = activity("ParentActivity")
        assertTrue(parent, "android:enabled=\"false\"" in parent)
        assertTrue(parent, "android:exported=\"true\"" in parent)
        assertTrue(parent, "android:launchMode=\"singleTask\"" in parent)
        assertTrue(parent, "android.intent.category.LAUNCHER" in parent)
        assertFalse(parent, "category.HOME" in parent || "category.DEFAULT" in parent)
        assertEquals(1, Regex("<intent-filter>").findAll(parent).count())
    }

    @Test fun `no home alias remains`() {
        assertFalse(xml, "activity-alias" in xml)
        assertFalse(xml, "\".Home\"" in xml)
    }

    @Test fun `the parent activity is a plain subclass`() {
        val src = File("src/main/java/com/nbaltas/nickos/ParentActivity.kt").readText()
        assertTrue(src, "class ParentActivity : MainActivity()" in src)
        assertFalse(src, "{" in src)
    }
}

/** The PIN gate for the kid lock repair screen. */
class KioskGateTest {
    @Test fun `an existing node means a linked parent or a finished kid lock`() {
        assertFalse(KioskPolicy.existingNode(0, false))
        assertTrue(KioskPolicy.existingNode(1, false))
        assertTrue(KioskPolicy.existingNode(0, true))
        assertTrue(KioskPolicy.existingNode(2, true))
    }

    @Test fun `policy table`() {
        // existing, setupNeeded, pinWindow
        val t = mapOf(
            Triple(true, true, true) to StartScreen.MENU,
            Triple(true, true, false) to StartScreen.ASK,
            Triple(true, false, true) to StartScreen.MENU,
            Triple(true, false, false) to StartScreen.HOME,
            Triple(false, true, true) to StartScreen.KIOSK,
            Triple(false, true, false) to StartScreen.KIOSK,
            Triple(false, false, true) to StartScreen.MENU,
            Triple(false, false, false) to StartScreen.HOME,
        )
        for ((k, want) in t) assertEquals("$k", want, KioskPolicy.startScreen(k.first, k.second, k.third))
    }

    @Test fun `recreating the activity without a PIN window never shows the menu or the setup for an existing node`() {
        for (setup in listOf(false, true)) {
            val s = KioskPolicy.startScreen(existing = true, setupNeeded = setup, pinWindow = false)
            assertTrue("$s", s == StartScreen.HOME || s == StartScreen.ASK)
        }
    }

    @Test fun `the setup may stay open only on a new tablet or inside a PIN window`() {
        assertTrue(KioskPolicy.setupMayStay(existing = false, pinWindow = false))
        assertTrue(KioskPolicy.setupMayStay(existing = true, pinWindow = true))
        assertFalse(KioskPolicy.setupMayStay(existing = true, pinWindow = false))
    }

    @Test fun `while waiting for the PIN the watchdog is not held back`() {
        assertTrue(KioskPolicy.returnHome(deviceOwner = false, parentMode = false, homeVisible = false, fg = "com.other.launcher", allowed = emptyList(), self = "x"))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeAliasDeviceTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val pm get() = ctx.packageManager
    private fun state(e: Entry) = pm.getComponentEnabledSetting(ComponentName(ctx.packageName, e.className))
    private fun makeOwner() = shadowOf(ctx.getSystemService(DevicePolicyManager::class.java)).setDeviceOwner(ComponentName(ctx, AdminReceiver::class.java))

    @Test fun `a kid tablet never has a component state written`() {
        assertTrue(EntrySwitch.apply(ctx, Role.NODE, false))
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, state(Entry.MAIN))
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, state(Entry.PARENT))
        assertEquals(ComponentName(ctx.packageName, Entry.MAIN.className), entryActivity(ctx))
    }

    @Test fun `a parent tablet moves to the parent door and the entry activity follows`() {
        assertTrue(EntrySwitch.apply(ctx, Role.MASTER, false))
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED, state(Entry.PARENT))
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, state(Entry.MAIN))
        assertEquals(ComponentName(ctx.packageName, Entry.PARENT.className), entryActivity(ctx))
        assertEquals(ComponentName(ctx.packageName, Entry.PARENT.className), entryIntent(ctx).component)
        // Again: nothing more to do.
        assertTrue(EntrySwitch.apply(ctx, Role.MASTER, false))
        // Opted in: back to the plain door.
        assertTrue(EntrySwitch.apply(ctx, Role.MASTER, true))
        assertEquals(ComponentName(ctx.packageName, Entry.MAIN.className), entryActivity(ctx))
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, state(Entry.PARENT))
        // And a reset.
        assertTrue(EntrySwitch.apply(ctx, Role.MASTER, false))
        assertTrue(EntrySwitch.apply(ctx, Role.NONE, false))
        assertEquals(ComponentName(ctx.packageName, Entry.MAIN.className), entryActivity(ctx))
    }

    @Test fun `a provisioned or owner tablet stays on the plain door whatever the role`() {
        makeOwner()
        assertTrue(EntrySwitch.apply(ctx, Role.NONE, false))
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, state(Entry.MAIN))
        assertTrue(EntrySwitch.apply(ctx, Role.MASTER, false))
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, state(Entry.MAIN))
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, state(Entry.PARENT))
    }

    @Test fun `a reset forgets the provisioned marker so a parent is offered the parent door again`() {
        Provisioning.remember(ctx, android.os.PersistableBundle().apply { putString(Provisioning.EXTRA_ROLE, "node") })
        assertTrue(EntrySwitch.apply(ctx, Role.MASTER, false))
        assertEquals(ComponentName(ctx.packageName, Entry.MAIN.className), entryActivity(ctx))
        Provisioning.forget(ctx)
        assertTrue(EntrySwitch.apply(ctx, Role.MASTER, false))
        assertEquals(ComponentName(ctx.packageName, Entry.PARENT.className), entryActivity(ctx))
    }

    @Test fun `make home on an owner is still the main activity`() {
        makeOwner()
        assertTrue(NodeDevice.makeHome(ctx))
        val acts = mutableListOf<ComponentName>()
        shadowOf(pm).getPersistentPreferredActivities(mutableListOf(), acts, null)
        assertTrue(acts.toString(), acts.contains(ComponentName(ctx, MainActivity::class.java)))
        assertFalse(acts.contains(ComponentName(ctx, ParentActivity::class.java)))
        // Nothing changed state on the way.
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, state(Entry.MAIN))
    }

    @Test fun `make home on a tablet that is not an owner does nothing`() {
        assertFalse(NodeDevice.makeHome(ctx))
    }

    @Test fun `the opt in is kept and cleared by a reset`() {
        val s = Store(ctx)
        assertFalse(s.masterHomeOptIn.value)
        s.setMasterHomeOptIn(true)
        assertTrue(Store(ctx).masterHomeOptIn.value)
        s.resetAll()
        assertFalse(s.masterHomeOptIn.value)
        assertFalse(Store(ctx).masterHomeOptIn.value)
    }

    @Test fun `the finished kid lock is kept across restarts and cleared by a reset`() {
        val s = Store(ctx)
        assertFalse(s.kidLockDone.value)
        s.setKidLockDone(true)
        assertTrue(Store(ctx).kidLockDone.value)
        s.resetAll()
        assertFalse(s.kidLockDone.value)
        assertFalse(Store(ctx).kidLockDone.value)
    }

    /** App.onCreate already ran the migration under Robolectric: put the tablet back to "never ran it", as an update finds it. */
    private fun beforeMigration(): Store {
        ctx.getSharedPreferences("nickos", Context.MODE_PRIVATE).edit().remove("migration").commit()
        return Store(ctx)
    }

    @Test fun `the first start of this version treats an existing Node as locked, once`() {
        val s = beforeMigration()
        s.setRole(com.nbaltas.nickos.data.Role.NODE)
        assertFalse(s.kidLockDone.value)
        assertTrue(s.migrateKidLock())
        assertTrue(s.kidLockDone.value)
        assertTrue(Store(ctx).kidLockDone.value)
        // Not again: a later start does not set the flag back after it was cleared on purpose.
        s.setKidLockDone(false)
        assertFalse(Store(ctx).migrateKidLock())
        assertFalse(Store(ctx).kidLockDone.value)
    }

    @Test fun `the first start on a tablet that is not a Node only records the version`() {
        val s = beforeMigration()
        assertTrue(s.migrateKidLock())
        assertFalse(s.kidLockDone.value)
        s.setRole(com.nbaltas.nickos.data.Role.NODE) // set up afterwards: a new tablet keeps today's no PIN flow
        assertFalse(Store(ctx).migrateKidLock())
        assertFalse(Store(ctx).kidLockDone.value)
    }

    @Test fun `a reset keeps the migration version`() {
        val s = Store(ctx)
        s.migrateKidLock()
        s.resetAll()
        assertFalse(Store(ctx).migrateKidLock())
    }

    @Test fun `a window that ran out is never extended on an existing Node`() {
        val now = 1_000_000L
        assertEquals(now + 600_000L, KioskPolicy.rearmedUntil(true, now + 1, now, 10))
        assertNull(KioskPolicy.rearmedUntil(true, now, now, 10))
        assertNull(KioskPolicy.rearmedUntil(true, 0L, now, 10))
        // A new, never locked tablet keeps today's flow without a PIN.
        assertEquals(now + 600_000L, KioskPolicy.rearmedUntil(false, 0L, now, 10))
    }

    @Test fun `the state line tells the truth`() {
        assertEquals("NickOS is the home screen now", homeStateLine(true))
        assertEquals("Another app is the home screen", homeStateLine(false))
        assertEquals("Another app is the home screen", homeStateLine(null))
    }
}
