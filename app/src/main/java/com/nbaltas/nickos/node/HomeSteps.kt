package com.nbaltas.nickos.node

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.nbaltas.nickos.HomeAlias

/** What [HomeSteps] needs from the tablet. Each call says whether it worked and never throws. */
interface HomePort {
    fun enableAlias(): Boolean
    /** Is the alias among the apps that answer the HOME intent? */
    fun aliasResolvesHome(): Boolean
    fun clearPackagePreferred(): Boolean
    fun addPreferredAlias(): Boolean
    /** After the preference is in: does HOME now open the alias? */
    fun homeOpensAlias(): Boolean
}

/**
 * Making NickOS the persistent home screen on a Device Owner tablet. Every Device Owner path goes through here
 * ([NodeDevice.makeHome]). The old preference (MainActivity from before the alias) is cleared and the alias one
 * added right after, then HOME is checked. Any failure returns false and is simply tried again the next time
 * lockdown is applied or NickOS starts.
 */
object HomeSteps {
    fun run(p: HomePort, log: (String) -> Unit): Boolean {
        fun step(name: String, ok: Boolean): Boolean { log("home step $name: ${if (ok) "ok" else "failed"}"); return ok }
        if (!step("enable alias", p.enableAlias())) return false
        if (!step("alias resolves HOME", p.aliasResolvesHome())) return false
        // The HOME preference is the only persistent preferred activity this package sets (see NodeDevice), so
        // clearing for the whole package only drops the old MainActivity one.
        if (!step("clear old preference", p.clearPackagePreferred())) return false
        if (!step("add alias preference", p.addPreferredAlias())) return false
        return step("home opens alias", p.homeOpensAlias())
    }
}

internal class RealHomePort(private val ctx: Context, private val dpm: DevicePolicyManager) : HomePort {
    private val admin = ComponentName(ctx, AdminReceiver::class.java)

    private inline fun safe(f: () -> Boolean) = try { f() } catch (e: Throwable) { android.util.Log.w("NickOS.Device", "home port", e); false }

    override fun enableAlias() = HomeAlias.set(ctx, true)

    override fun aliasResolvesHome() = safe {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        ctx.packageManager.queryIntentActivities(i, 0).any { it.activityInfo.packageName == ctx.packageName && it.activityInfo.name == HomeAlias.component(ctx).className }
    }

    override fun clearPackagePreferred() = safe { dpm.clearPackagePersistentPreferredActivities(admin, ctx.packageName); true }

    override fun addPreferredAlias() = safe { dpm.addPersistentPreferredActivity(admin, NodeDevice.homeFilter(), HomeAlias.component(ctx)); true }

    override fun homeOpensAlias() = safe {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val a = ctx.packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
        a != null && a.packageName == ctx.packageName && a.name == HomeAlias.component(ctx).className
    }
}
