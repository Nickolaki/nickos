package com.nbaltas.nickos

/**
 * Giving up a role. The saved car WiFi (and its suggestion) goes first, then the Master side stops, then the
 * stored role is wiped, so the next role starts from nothing the old one left behind.
 */
object RoleReset {
    fun run(clearWifi: () -> Unit, stopMaster: () -> Unit, wipe: () -> Unit) {
        clearWifi()
        stopMaster()
        wipe()
    }

    /** The real parts: WifiJoin.save with an empty name forgets the saved network and removes its suggestion. */
    fun run(ctx: android.content.Context, app: App) = run(
        clearWifi = { app.node.wifi.save("", "") },
        stopMaster = { app.master.stop(); com.nbaltas.nickos.master.MasterService.stop(ctx) },
        wipe = { app.store.resetAll() },
    )
}
