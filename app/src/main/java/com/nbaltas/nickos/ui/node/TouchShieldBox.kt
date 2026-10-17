package com.nbaltas.nickos.ui.node

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.nbaltas.nickos.node.TouchShield
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The touch lock inside NickOS, for a tablet without the "display over other apps" permission. It takes every touch and
 * shows nothing. A finger held for [TouchShield.HOLD_MS] in the top left corner calls [onHold] (the emergency PIN).
 * [onActive] says whether the Box is composed while NickOS is resumed, which is the only time it really blocks anything.
 */
@Composable
fun TouchShieldBox(onActive: (Boolean) -> Unit, onHold: () -> Unit) {
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val watch = LifecycleEventObserver { _, _ -> onActive(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
        owner.lifecycle.addObserver(watch)
        onDispose { owner.lifecycle.removeObserver(watch); onActive(false) }
    }
    val zone = with(LocalDensity.current) { TouchShield.CORNER_DP.dp.toPx() }
    Box(
        Modifier.fillMaxSize().pointerInput(zone) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                down.consume()
                if (down.position.x <= zone && down.position.y <= zone) {
                    // Still inside the corner and still down after the hold time.
                    val stayed = withTimeoutOrNull(TouchShield.HOLD_MS) {
                        while (true) {
                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                            ev.changes.forEach { it.consume() }
                            val c = ev.changes.firstOrNull() ?: break
                            if (!c.pressed || c.position.x > zone || c.position.y > zone) break
                        }
                    }
                    if (stayed == null) onHold()
                }
                // Swallow the rest of this touch.
                do {
                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                    ev.changes.forEach { it.consume() }
                } while (ev.changes.any { it.pressed })
            }
        },
    )
}
