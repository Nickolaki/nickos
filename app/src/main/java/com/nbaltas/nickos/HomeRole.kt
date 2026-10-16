package com.nbaltas.nickos

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.nbaltas.nickos.data.Role

/** Whether NickOS is this tablet's home screen right now. Null when the system would not say. */
fun isDefaultHome(ctx: Context): Boolean? = try {
    val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    ctx.packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName?.let { it == ctx.packageName }
} catch (e: Exception) { null }

/** Asks for the home role: the system dialog on Android 10+, else the Home app settings page. Null when this tablet has neither. */
fun homeRequestIntent(ctx: Context): Intent? = try {
    val rm = if (android.os.Build.VERSION.SDK_INT >= 29) ctx.getSystemService(android.app.role.RoleManager::class.java) else null
    if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)) rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME)
    else Intent(android.provider.Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        .takeIf { it.resolveActivity(ctx.packageManager) != null }
} catch (e: Exception) { null }

/** What the Home screen row says under the switch. [isHome] is null when the system would not say, which reads as another app. */
fun homeStateLine(isHome: Boolean?): String =
    if (isHome == true) "NickOS is the home screen now" else "Another app is the home screen"

/**
 * Back with nothing left to close: the parent tablet goes back to its own home screen, like any app, unless NickOS
 * is that home screen (then there is nowhere to go). Anything unknown keeps Back doing nothing. Tablets for kids and
 * tablets with no role never leave.
 */
fun masterBackLeaves(role: Role, isHome: Boolean?): Boolean = role == Role.MASTER && isHome == false
