package com.nbaltas.nickos

import android.app.Application
import android.content.Context
import com.nbaltas.nickos.data.Role
import com.nbaltas.nickos.data.Store
import com.nbaltas.nickos.node.NodeDevice
import com.nbaltas.nickos.master.MasterRuntime
import com.nbaltas.nickos.node.NodeRuntime

class App : Application() {
    val store by lazy { Store(this) }
    val node by lazy { NodeRuntime(this, store) }
    val master by lazy { MasterRuntime(this, store) }

    /**
     * Puts the entry activities where the policy says at every start, so an updated parent tablet converges on its first
     * start. A kid tablet is already there and nothing changes. Later role changes are switched by the running activity
     * in the right order: see [EntryFlow].
     */
    override fun onCreate() {
        super.onCreate()
        store.migrateKidLock() // before any screen decides whether this Node is an existing one
        // A parent tablet that already is the home screen (chosen before this version) keeps being offered.
        if (store.role.value == Role.MASTER && !store.masterHomeOptIn.value && isDefaultHome(this) == true) store.setMasterHomeOptIn(true)
        EntrySwitch.apply(this, store.role.value, store.masterHomeOptIn.value)
        // An update does not run the provisioning callbacks: a Device Owner kid tablet puts its HOME choice back here, parent mode or not.
        if (store.role.value == Role.NODE) NodeDevice.makeHome(this)
        com.nbaltas.nickos.node.RotationRecovery.retry(this, store)
    }

    companion object {
        fun get(ctx: Context) = ctx.applicationContext as App
    }
}
