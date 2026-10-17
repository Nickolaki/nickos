package com.nbaltas.nickos.ui.master

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Mic
import com.nbaltas.nickos.talk.TalkBar
import com.nbaltas.nickos.ui.theme.C
import com.nbaltas.nickos.ui.theme.Muted
import com.nbaltas.nickos.ui.theme.body

/** What a tablet tile's small microphone needs. */
class TileTalk(
    val enabled: Boolean,
    val talking: Boolean,
    val onPress: () -> Unit,
    /** True when the press was cancelled (the finger slid off, a dialog came up) rather than let go. */
    val onRelease: (cancelled: Boolean) -> Unit,
    /** Why this tablet cannot hear right now ("Can't hear you: ..."), or null. */
    val note: String? = null,
)

/**
 * Press and hold. [onPress] runs when the finger goes down and [onRelease] when it comes up, on every way the press
 * can end, including this button leaving the screen mid press.
 */
@Composable
private fun Modifier.holdToTalk(onPress: () -> Unit, onRelease: (Boolean) -> Unit): Modifier {
    val down by rememberUpdatedState(onPress)
    val up by rememberUpdatedState(onRelease)
    return pointerInput(Unit) {
        detectTapGestures(onPress = {
            down()
            var cancelled = true
            try { cancelled = !tryAwaitRelease() } finally { up(cancelled) }
        })
    }
}

/** The big Hold to talk button and the line beside it that says who hears. */
@Composable
fun TalkBarRow(bar: TalkBar, onPress: () -> Unit, onRelease: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            Modifier.height(52.dp).clip(RoundedCornerShape(26.dp))
                .background(if (bar.talking) C.accent else C.surface2)
                .alpha(if (bar.enabled || bar.talking) 1f else 0.5f)
                .holdToTalk(onPress, onRelease)
                .padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Lucide.Mic, null, tint = if (bar.talking) C.onAccent else C.text, modifier = Modifier.size(22.dp))
            Text(bar.button, style = body(17.sp, 700), color = if (bar.talking) C.onAccent else C.text, maxLines = 1)
        }
        Muted(bar.line, 16, Modifier, maxLines = 1)
    }
}

/** A tablet tile's own small microphone, 48dp like its neighbours. Hold it to talk to this tablet only. */
@Composable
fun TileMic(t: TileTalk) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(if (t.talking) C.accent else C.surface)
            .alpha(if (t.enabled || t.talking) 1f else 0.4f)
            .holdToTalk(t.onPress, t.onRelease),
        contentAlignment = Alignment.Center,
    ) { Icon(Lucide.Mic, null, tint = if (t.talking) C.onAccent else C.text, modifier = Modifier.size(20.dp)) }
}
