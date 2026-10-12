package com.nbaltas.nickos.link

import android.content.Context

/** Which peers have completed a sec2 handshake. Once set, the legacy (token in clear) hello is refused for that peer. */
interface SecFlags {
    fun sec2(id: String): Boolean
    fun setSec2(id: String, v: Boolean)
}

/**
 * The ratchet, in its own prefs file ("nickos_link") so Store stays untouched. The Master keys it by nodeId, the
 * Node by masterId.
 */
class LinkFlags(ctx: Context) : SecFlags {
    private val p = ctx.applicationContext.getSharedPreferences("nickos_link", Context.MODE_PRIVATE)

    override fun sec2(id: String): Boolean = p.getBoolean("sec2.$id", false)

    override fun setSec2(id: String, v: Boolean) { p.edit().putBoolean("sec2.$id", v).apply() }
}
