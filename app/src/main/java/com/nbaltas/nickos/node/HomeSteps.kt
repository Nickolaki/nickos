package com.nbaltas.nickos.node

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.nbaltas.nickos.HomeAlias

/** What [HomeSteps] needs from the tablet. Each call says whether it worked and never throws. */
interface HomePort {
    fun enableAlias(): Boolean
    /** Does the HOME intent now resolve to the alias? */
    fun aliasResolvesHome(): Boolean
    fun addPreferredAlias(): Boolean
    fun clearPackagePreferred(): Boolean
}

/**
 * Making NickOS the persistent home screen on a Device Owner tablet, in an order that can be repeated after an
 * interruption: the old preference is only cleared once the new one is in, so there is never a moment with none.
 */
object HomeSteps {
    fun run(p: HomePort, log: (String) -> Unit): Boolean {
        fun step(name: String, ok: Boolean): Boolean { log("home step $name: ${if (ok) "ok" else "failed"}"); return ok }
        if (!step("enable alias", p.enableAlias())) return false
        if (!step("alias resolves HOME", p.aliasResolvesHome())) return false
        if (!step("add alias preference", p.addPreferredAlias())) return false
        // Drops the old MainActivity preference (and anything else for this package), then puts the alias one back.
        if (!step("clear old preference", p.clearPackagePreferred())) return true
        return step("re-add alias preference", p.addPreferredAlias())
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

    override fun addPreferredAlias() = safe { dpm.addPersistentPreferredActivity(admin, NodeDevice.homeFilter(), HomeAlias.component(ctx)); true }

    override fun clearPackagePreferred() = safe { dpm.clearPackagePersistentPreferredActivities(admin, ctx.packageName); true }
}
