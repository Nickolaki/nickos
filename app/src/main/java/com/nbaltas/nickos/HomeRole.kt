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

/**
 * Back with nothing left to close: the parent tablet goes back to its own home screen, like any app, unless NickOS
 * is that home screen (then there is nowhere to go). Anything unknown keeps Back doing nothing. Tablets for kids and
 * tablets with no role never leave.
 */
fun masterBackLeaves(role: Role, isHome: Boolean?): Boolean = role == Role.MASTER && isHome == false
