package com.nbaltas.nickos.node

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The two one-time system screens a normally installed Node needs before the Master can change brightness or turn the screen off. */
enum class PermStep { BRIGHTNESS, SCREEN_OFF, NOTIFICATIONS }

/**
 * When the Node pops up its own "let the parent tablet change brightness?" card, and which system screens Allow walks
 * through. Pure rules, so they can be tested without a tablet. A Device Owner is never asked: it can do both already.
 */
object PermissionAsk {
    const val TITLE = "Let the parent tablet change brightness and turn the screen off?"
    const val BODY = "These permissions also let NickOS keep apps in landscape and show when a parent is talking. Android will ask for permission on this tablet. Switch NickOS on in each settings page, then come back here. Talk notifications can be changed in the grown up menu."
    const val ALLOW = "Allow"
    const val NOT_NOW = "Not now"

    /** Something is still missing on a tablet that is not Device Owner. */
    fun needed(deviceOwner: Boolean, brightnessOk: Boolean, screenOffOk: Boolean, notificationsOk: Boolean = true) =
        !deviceOwner && !(brightnessOk && screenOffOk && notificationsOk)

    /**
     * Show the card? Not when nothing is needed, not while a video is playing in kid mode, and not after "Not now"
     * or after Allow was tapped this session, unless the Master asked since.
     */
    fun showCard(
        deviceOwner: Boolean, brightnessOk: Boolean, screenOffOk: Boolean,
        declined: Boolean, playing: Boolean, masterAsked: Boolean, notificationsOk: Boolean = true,
    ) = needed(deviceOwner, brightnessOk, screenOffOk, notificationsOk) && !playing && (masterAsked || !declined)

    /**
     * The next system screen to open, skipping what is already allowed and what was already shown (so a "no" never loops).
     * Notifications come last (Android 13 and up): without them the parent cannot talk to this tablet.
     */
    fun nextStep(deviceOwner: Boolean, brightnessOk: Boolean, screenOffOk: Boolean, tried: Set<PermStep>, notificationsOk: Boolean = true): PermStep? {
        if (deviceOwner) return null
        if (!brightnessOk && PermStep.BRIGHTNESS !in tried) return PermStep.BRIGHTNESS
        if (!screenOffOk && PermStep.SCREEN_OFF !in tried) return PermStep.SCREEN_OFF
        if (!notificationsOk && PermStep.NOTIFICATIONS !in tried) return PermStep.NOTIFICATIONS
        return null
    }
}

/** Memory-only state for the card. A new app start (or an update) starts fresh, which is what "ask again" means. */
class PermissionAskState {
    @Volatile var declined = false
        private set
    private val _masterAsked = MutableStateFlow(false)
    /** The Master tapped a greyed control and wants the card now. */
    val masterAsked: StateFlow<Boolean> = _masterAsked

    fun masterAsks() { _masterAsked.value = true }
    /** "Not now". */
    fun decline() { declined = true; _masterAsked.value = false }
    /** "Allow" was tapped: the walk has begun, so the card must not come back by itself if a screen is dismissed. */
    fun begin() = decline()
}
