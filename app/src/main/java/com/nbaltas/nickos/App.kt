package com.nbaltas.nickos

import android.app.Application
import android.content.Context
import com.nbaltas.nickos.data.Role
import com.nbaltas.nickos.data.Store
import com.nbaltas.nickos.node.NodeDevice
import com.nbaltas.nickos.master.MasterRuntime
import com.nbaltas.nickos.node.NodeRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class App : Application() {
    val store by lazy { Store(this) }
    val node by lazy { NodeRuntime(this, store) }
    val master by lazy { MasterRuntime(this, store) }

    /** Keeps the home screen offer matching the role: at start (update, restart) and on every role or opt-in change. */
    override fun onCreate() {
        super.onCreate()
        HomeAlias.apply(this, store.role.value, store.masterHomeOptIn.value)
        // An update does not run the provisioning callbacks: a Device Owner kid tablet puts its HOME choice back here, parent mode or not.
        if (store.role.value == Role.NODE) NodeDevice.makeHome(this)
        com.nbaltas.nickos.node.RotationRecovery.retry(this, store)
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            combine(store.role, store.masterHomeOptIn) { r, o -> r to o }.collect { (r, o) -> HomeAlias.apply(this@App, r, o) }
        }
    }

    companion object {
        fun get(ctx: Context) = ctx.applicationContext as App
    }
}
