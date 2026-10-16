package com.nbaltas.nickos

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.nbaltas.nickos.data.Role
import com.nbaltas.nickos.data.Store
import com.nbaltas.nickos.node.AdminReceiver
import com.nbaltas.nickos.node.HomePort
import com.nbaltas.nickos.node.HomeSteps
import com.nbaltas.nickos.node.KioskPolicy
import com.nbaltas.nickos.node.KioskState
import com.nbaltas.nickos.node.NodeDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/** Who may be offered to Android as a home screen. */
class HomeAliasPolicyTest {
    @Test fun `every role and opt in and provisioned case`() {
        for (opt in listOf(false, true)) for (prov in listOf(false, true)) {
            assertTrue("node $opt $prov", HomeAliasPolicy.wanted(Role.NODE, opt, prov))
            assertEquals("master $opt $prov", opt, HomeAliasPolicy.wanted(Role.MASTER, opt, prov))
            assertEquals("none $opt $prov", prov, HomeAliasPolicy.wanted(Role.NONE, opt, prov))
        }
    }

    @Test fun `a fresh parent tablet is not offered`() {
        assertFalse(HomeAliasPolicy.wanted(Role.NONE, false))
        assertFalse(HomeAliasPolicy.wanted(Role.MASTER, false))
        assertTrue(HomeAliasPolicy.wanted(Role.MASTER, true))
    }

    @Test fun `a launcher mode kid tablet after the update sees the kid lock setup`() {
        val s = KioskState(deviceOwner = false, home = false, overlay = true, usage = true, admin = true)
        assertTrue(KioskPolicy.setupNeeded(s))
        assertFalse(KioskPolicy.setupNeeded(s.copy(home = true)))
    }
}

/** HOME lives only on the alias, and the alias starts off. Read from the source manifest. */
class HomeManifestTest {
    private val xml = File("src/main/AndroidManifest.xml").readText()

    @Test fun `home is only on the disabled alias`() {
        val alias = Regex("<activity-alias[^>]*?android:name=\"\\.Home\"[\\s\\S]*?</activity-alias>").find(xml)!!.value
        assertTrue(alias, "android:enabled=\"false\"" in alias)
        assertTrue(alias, "android:targetActivity=\".MainActivity\"" in alias)
        assertTrue(alias, "android:exported=\"true\"" in alias)
        assertTrue(alias, "android.intent.category.HOME" in alias && "android.intent.category.DEFAULT" in alias)
        val main = Regex("<activity\\s[^>]*?android:name=\"\\.MainActivity\"[\\s\\S]*?</activity>").find(xml)!!.value
        assertFalse(main, "category.HOME" in main)
        assertTrue(main, "category.LAUNCHER" in main)
        // The only HOME filter in the app, apart from the <queries> entry that asks about launchers.
        val outsideQueries = xml.replace(Regex("<queries>[\\s\\S]*?</queries>"), "")
        assertEquals(1, Regex("category\\.HOME").findAll(outsideQueries).count())
    }
}

/** The order [NodeDevice.makeHome] works in. */
class HomeStepsTest {
    private class Fake(val failAt: String? = null) : HomePort {
        val calls = mutableListOf<String>()
        private fun go(n: String): Boolean { calls += n; return n != failAt }
        override fun enableAlias() = go("enable")
        override fun aliasResolvesHome() = go("resolves")
        override fun addPreferredAlias() = go("add")
        override fun clearPackagePreferred() = go("clear")
    }

    @Test fun `enable, verify, add, clear, add again`() {
        val f = Fake()
        assertTrue(HomeSteps.run(f) {})
        assertEquals(listOf("enable", "resolves", "add", "clear", "add"), f.calls)
    }

    @Test fun `nothing is cleared until the new preference is in`() {
        for (bad in listOf("enable", "resolves", "add")) {
            val f = Fake(bad)
            assertFalse(bad, HomeSteps.run(f) {})
            assertFalse(bad, "clear" in f.calls)
        }
    }

    @Test fun `a failed clear keeps the new preference and still reports home`() {
        val f = Fake("clear")
        assertTrue(HomeSteps.run(f) {})
        assertEquals(listOf("enable", "resolves", "add", "clear"), f.calls)
    }

    @Test fun `every step is logged`() {
        val log = mutableListOf<String>()
        HomeSteps.run(Fake()) { log += it }
        assertEquals(5, log.size)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeAliasDeviceTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Test fun `the alias starts off and follows the policy`() {
        assertFalse(HomeAlias.isEnabled(ctx))
        assertTrue(HomeAlias.apply(ctx, Role.NODE, false))
        assertTrue(HomeAlias.isEnabled(ctx))
        assertTrue(HomeAlias.apply(ctx, Role.MASTER, false))
        assertFalse(HomeAlias.isEnabled(ctx))
        assertTrue(HomeAlias.apply(ctx, Role.MASTER, true))
        assertTrue(HomeAlias.isEnabled(ctx))
        assertTrue(HomeAlias.apply(ctx, Role.NONE, true))
        assertFalse(HomeAlias.isEnabled(ctx))
    }

    @Test fun `a provisioned or owner tablet with no role is offered`() {
        shadowOf(ctx.getSystemService(DevicePolicyManager::class.java)).setDeviceOwner(ComponentName(ctx, AdminReceiver::class.java))
        assertTrue(HomeAlias.apply(ctx, Role.NONE, false))
        assertTrue(HomeAlias.isEnabled(ctx))
    }

    @Test fun `make home on an owner uses the alias`() {
        shadowOf(ctx.getSystemService(DevicePolicyManager::class.java)).setDeviceOwner(ComponentName(ctx, AdminReceiver::class.java))
        assertTrue(NodeDevice.makeHome(ctx))
        assertTrue(HomeAlias.isEnabled(ctx))
        val acts = mutableListOf<ComponentName>()
        shadowOf(ctx.packageManager).getPersistentPreferredActivities(mutableListOf(), acts, null)
        assertTrue(acts.toString(), acts.contains(ComponentName(ctx, "com.nbaltas.nickos.Home")))
        assertFalse(acts.contains(ComponentName(ctx, MainActivity::class.java)))
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

    @Test fun `the state line tells the truth`() {
        assertEquals("NickOS is the home screen now", homeStateLine(true))
        assertEquals("Another app is the home screen", homeStateLine(false))
        assertEquals("Another app is the home screen", homeStateLine(null))
    }
}
