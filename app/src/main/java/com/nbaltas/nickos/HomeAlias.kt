package com.nbaltas.nickos

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.nbaltas.nickos.data.Role

/**
 * Whether NickOS offers itself to Android as a home screen. Only the ".Home" alias in the manifest carries the
 * HOME filter, so switching the alias off means Android never lists NickOS (and never asks) as a launcher.
 */
object HomeAliasPolicy {
    /**
     * Kid tablets always. A parent tablet only when the parent switched it on in Settings. A tablet with no role yet
     * stays off, except one that was set up from the QR code or is Device Owner: that is a kid tablet in the making.
     */
    fun wanted(role: Role, parentOptIn: Boolean, provisionedOrOwner: Boolean = false): Boolean = when (role) {
        Role.NODE -> true
        Role.MASTER -> parentOptIn
        Role.NONE -> provisionedOrOwner
    }
}

/** The ".Home" alias switch. Changes the state only when it differs, and never kills the app. */
object HomeAlias {
    private const val TAG = "NickOS.Home"

    fun component(ctx: Context) = ComponentName(ctx, "com.nbaltas.nickos.Home")

    fun isEnabled(ctx: Context): Boolean = try {
        ctx.packageManager.getComponentEnabledSetting(component(ctx)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    } catch (e: Exception) { false }

    /** Returns true when it ended up as asked. */
    fun set(ctx: Context, on: Boolean): Boolean = try {
        if (isEnabled(ctx) != on) {
            ctx.packageManager.setComponentEnabledSetting(
                component(ctx),
                if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
            Log.i(TAG, "home alias ${if (on) "on" else "off"}")
        }
        isEnabled(ctx) == on
    } catch (e: Exception) { Log.w(TAG, "home alias", e); false }

    /** Puts the alias where the policy says, from what this tablet knows right now. */
    fun apply(ctx: Context, role: Role, parentOptIn: Boolean): Boolean =
        set(ctx, HomeAliasPolicy.wanted(role, parentOptIn, com.nbaltas.nickos.node.Provisioning.provisionedAsNode(ctx) || isOwner(ctx)))

    private fun isOwner(ctx: Context): Boolean = try {
        ctx.getSystemService(android.app.admin.DevicePolicyManager::class.java).isDeviceOwnerApp(ctx.packageName)
    } catch (e: Exception) { false }
}
