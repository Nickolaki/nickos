package com.nbaltas.nickos.ui.master

import com.nbaltas.nickos.link.NetMessages

/** The words under the "Touch" switch on the Device tab. They tell what the kid tablet really does, not only what was asked. */
object TouchText {
    const val HINT = "Off: the screen ignores touches, video keeps playing"
    const val TURNING_OFF = "Turning off..."
    const val IS_OFF = "Touch is off"

    fun needsOverlay(name: String) = "Only blocks touches inside NickOS. Allow 'Display over other apps' on $name"

    /** The switch shows touch as on until the Node says it has been turned off. */
    fun touchOn(t: NetMessages.TouchStatus?) = t?.off != true

    /** Null when the plain hint is enough: touch is on. */
    fun status(t: NetMessages.TouchStatus?, name: String): String? = when {
        t == null || !t.off -> null
        t.shield == NetMessages.SHIELD_ON -> IS_OFF
        t.shield == NetMessages.SHIELD_INAPP -> needsOverlay(name)
        else -> TURNING_OFF
    }

    /** The "Allow" button is offered when the shield only covers NickOS. */
    fun offerAllow(t: NetMessages.TouchStatus?) = t?.off == true && t.shield == NetMessages.SHIELD_INAPP
}
