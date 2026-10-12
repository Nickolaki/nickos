package com.nbaltas.nickos.node

import android.os.Build

/**
 * A tablet below Android 8 can be the Master but never a Node: the Node needs the overlay window type, the
 * wifi suggestion API and the camera service rules that arrived later. It refuses instead of half working.
 */
object NodeSupport {
    const val MIN_SDK = 26

    fun ok(sdk: Int = Build.VERSION.SDK_INT): Boolean = sdk >= MIN_SDK

    /** Shown under the disabled Node card in the role picker (Setup.kt, added once the pair-code branch has merged). */
    const val REFUSAL_COPY = "This tablet runs Android 7. It can only be the Master. A kids' tablet needs Android 8 or newer."

    /** Muted line under the "car network is on" text in Settings when the system, not NickOS, picked the name. */
    const val LEGACY_NETWORK_NOTE = "Android 7 picks the network name. If it ever changes, each tablet needs one link on the home WiFi."
}
