package com.nbaltas.nickos

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.nbaltas.nickos.data.Role

/**
 * The two doors into the app. [MAIN] is [MainActivity], exactly as it always was: launcher icon and home screen offer.
 * [PARENT] is [ParentActivity], the same app with the launcher icon only, so a parent tablet is never offered as a
 * home screen. Kid tablets never leave [MAIN].
 */
enum class Entry(val className: String, val manifestEnabled: Boolean) {
    MAIN("com.nbaltas.nickos.MainActivity", true),
    PARENT("com.nbaltas.nickos.ParentActivity", false);

    val other get() = if (this == MAIN) PARENT else MAIN
}

object EntryPolicy {
    /**
     * Only a parent tablet that did not opt in to being the home screen uses [Entry.PARENT]. Everything else, kid
     * tablets, a tablet with no role yet, a set up or Device Owner tablet, a parent who opted in, a reset tablet,
     * uses [Entry.MAIN].
     */
    fun wanted(role: Role, parentOptIn: Boolean, provisionedOrOwner: Boolean = false): Entry =
        if (role == Role.MASTER && !parentOptIn && !provisionedOrOwner) Entry.PARENT else Entry.MAIN
}

/** What [EntryFlow] needs from the package manager. Never throws. */
interface EntryPort {
    fun enabled(e: Entry): Boolean
    /** True when the component ended up as asked. */
    fun setEnabled(e: Entry, on: Boolean): Boolean
}

/** The ordered switching between the two doors. Always leaves at least one of them on. */
object EntryFlow {
    /** The door to use right now: [Entry.MAIN] when it is on (also when both are), else [Entry.PARENT]. */
    fun current(p: EntryPort): Entry = if (p.enabled(Entry.MAIN) || !p.enabled(Entry.PARENT)) Entry.MAIN else Entry.PARENT

    /** Enable the wanted one first, then disable the other. Changes only what differs, so a kid tablet is never touched. */
    fun converge(p: EntryPort, want: Entry): Boolean {
        if (!p.enabled(want) && !p.setEnabled(want, true)) return false
        if (p.enabled(want.other) && !p.setEnabled(want.other, false)) return false
        return true
    }

    /**
     * enable the new door, start it, finish the running activity, then disable the old door. If starting fails both
     * stay on, so there is always a way in.
     */
    fun switchTo(p: EntryPort, want: Entry, start: (Entry) -> Boolean, finish: () -> Unit): Boolean {
        if (!p.enabled(want) && !p.setEnabled(want, true)) return false
        if (!start(want)) return false
        finish()
        return !p.enabled(want.other) || p.setEnabled(want.other, false)
    }

    /**
     * What the running activity does about the wanted door. A parent who just opted in to the home screen keeps the
     * running [Entry.PARENT] (the home request dialog is open over it): [Entry.MAIN] is only turned on, and turns
     * [Entry.PARENT] off once it runs itself.
     */
    fun reconcile(p: EntryPort, want: Entry, running: Entry, role: Role, start: (Entry) -> Boolean, finish: () -> Unit): Boolean = when {
        running == want -> converge(p, want)
        want == Entry.MAIN && role == Role.MASTER -> p.enabled(Entry.MAIN) || p.setEnabled(Entry.MAIN, true)
        else -> switchTo(p, want, start, finish)
    }
}

internal class PackageEntryPort(private val ctx: Context) : EntryPort {
    private fun component(e: Entry) = ComponentName(ctx.packageName, e.className)

    override fun enabled(e: Entry): Boolean = try {
        when (ctx.packageManager.getComponentEnabledSetting(component(e))) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> e.manifestEnabled
            else -> false
        }
    } catch (ex: Exception) { e.manifestEnabled }

    override fun setEnabled(e: Entry, on: Boolean): Boolean = try {
        if (enabled(e) != on) {
            ctx.packageManager.setComponentEnabledSetting(
                component(e),
                if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
            Log.i(TAG, "${e.name} ${if (on) "on" else "off"}")
        }
        enabled(e) == on
    } catch (ex: Exception) { Log.w(TAG, "entry ${e.name}", ex); false }

    private companion object { const val TAG = "NickOS.Entry" }
}

/** Where the app opens: the one entry activity that is on. Every internal launch of the app goes through here. */
fun entryActivity(ctx: Context, port: EntryPort = PackageEntryPort(ctx)): ComponentName = ComponentName(ctx.packageName, EntryFlow.current(port).className)

/** A launch intent for the app's front door. */
fun entryIntent(ctx: Context): Intent = Intent().setComponent(entryActivity(ctx))

/** The door switching, for the real tablet. */
object EntrySwitch {
    private const val TAG = "NickOS.Entry"

    /** What the policy wants from what this tablet knows right now. */
    fun wanted(ctx: Context, role: Role, parentOptIn: Boolean): Entry =
        EntryPolicy.wanted(role, parentOptIn, com.nbaltas.nickos.node.Provisioning.provisionedAsNode(ctx) || isOwner(ctx))

    /** At app start: put the doors where the policy says, no activity involved. Idempotent. */
    fun apply(ctx: Context, role: Role, parentOptIn: Boolean): Boolean =
        try { EntryFlow.converge(PackageEntryPort(ctx), wanted(ctx, role, parentOptIn)) } catch (e: Throwable) { Log.w(TAG, "apply", e); false }

    /** Never throws: provisioning must not fail on it. */
    fun applyStored(ctx: Context) {
        try {
            val s = App.get(ctx).store
            apply(ctx, s.role.value, s.masterHomeOptIn.value)
        } catch (e: Throwable) { Log.w(TAG, "apply stored", e) }
    }

    /** From a running entry activity, when the role or the opt in changed. */
    fun reconcile(a: Activity, role: Role, parentOptIn: Boolean): Boolean = try {
        val running = if (a.componentName.className == Entry.PARENT.className) Entry.PARENT else Entry.MAIN
        EntryFlow.reconcile(
            PackageEntryPort(a), wanted(a, role, parentOptIn), running, role,
            start = { e ->
                try {
                    a.startActivity(Intent().setComponent(ComponentName(a.packageName, e.className)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                    true
                } catch (ex: Exception) { Log.w(TAG, "start ${e.name}", ex); false }
            },
            finish = { a.finish() },
        )
    } catch (e: Throwable) { Log.w(TAG, "reconcile", e); false }

    /** Opting in to the home screen: the HOME offer has to be on before the system is asked. */
    fun enableMain(ctx: Context): Boolean = try { PackageEntryPort(ctx).setEnabled(Entry.MAIN, true) } catch (e: Throwable) { false }

    private fun isOwner(ctx: Context): Boolean = try {
        ctx.getSystemService(android.app.admin.DevicePolicyManager::class.java).isDeviceOwnerApp(ctx.packageName)
    } catch (e: Exception) { false }
}
