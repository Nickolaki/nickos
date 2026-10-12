package com.nbaltas.nickos.node

/**
 * Which remote controls a Node can really carry out. On a normally installed tablet (not Device Owner) Android
 * refuses two of them until a grown-up grants a one-time permission ON that tablet. Before this, the Node just
 * ignored the command, so the Master's slider and Off button looked like they worked and did nothing.
 */
object ControlAccess {
    /** Brightness: Device Owner on Android 9+, or the "modify system settings" permission. */
    fun brightness(deviceOwner: Boolean, sdk: Int, canWriteSettings: Boolean) =
        (deviceOwner && sdk >= 28) || canWriteSettings

    /** Screen off: only a device administrator may lock the screen. */
    fun screenOff(isAdmin: Boolean) = isAdmin

    /** Master side: the short reason shown under a control the Node cannot do, or null when it works. */
    fun brightnessReason(tablet: String) = "Needs a one-time OK on $tablet. Tap here to ask for it."
    /** Master side: shown after a greyed control was tapped and the Node was asked to show its card. */
    fun askedLine(tablet: String) = "Asked on $tablet. Tap Allow on that tablet."
    const val ASKED_SHOW_MS = 120_000L
    fun askedActive(askedAt: Long?, now: Long) = askedAt != null && now - askedAt in 0..ASKED_SHOW_MS
    const val SCREEN_OFF_SHORT = "Off needs a one-time OK. Tap Off to ask."
    fun screenOffReason(tablet: String) = "Needs a one-time OK on $tablet. Tap Off to ask for it."
}
