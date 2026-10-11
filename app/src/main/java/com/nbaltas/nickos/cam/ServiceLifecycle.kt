package com.nbaltas.nickos.cam

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry

/**
 * A lifecycle for CameraX inside the Node's service, which has none of its own: resumed while
 * the camera streams, back to created when it stops. Main thread only, like any lifecycle.
 */
class ServiceLifecycle : LifecycleOwner {
    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry

    val destroyed: Boolean get() = registry.currentState == Lifecycle.State.DESTROYED

    fun start() {
        if (!destroyed) registry.currentState = Lifecycle.State.RESUMED
    }

    fun stop() {
        if (registry.currentState.isAtLeast(Lifecycle.State.STARTED)) registry.currentState = Lifecycle.State.CREATED
    }

    /** The end. A destroyed lifecycle is never used again; make a new one. */
    fun destroy() {
        if (destroyed) return
        // A lifecycle that never started cannot be destroyed directly.
        if (registry.currentState == Lifecycle.State.INITIALIZED) registry.currentState = Lifecycle.State.CREATED
        registry.currentState = Lifecycle.State.DESTROYED
    }
}
