package com.nbaltas.nickos

import android.app.Application
import android.content.Context
import com.nbaltas.nickos.data.Store
import com.nbaltas.nickos.master.MasterRuntime
import com.nbaltas.nickos.node.NodeRuntime

class App : Application() {
    val store by lazy { Store(this) }
    val node by lazy { NodeRuntime(this, store) }
    val master by lazy { MasterRuntime(this, store) }

    companion object {
        fun get(ctx: Context) = ctx.applicationContext as App
    }
}
