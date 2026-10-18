package com.nbaltas.nickos.ui.master

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextOverflow
import com.nbaltas.nickos.ui.theme.LocalPhone
import com.nbaltas.nickos.ui.theme.LocalParentTablet
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
private fun Modifier.holdToTalk(label: String, talking: Boolean, onPress: () -> Unit, onRelease: (Boolean) -> Unit): Modifier {
    val down by rememberUpdatedState(onPress)
    val up by rememberUpdatedState(onRelease)
    val isTalking by rememberUpdatedState(talking)
    // Screen readers cannot hold a finger down: activating the control starts talking and activating it again stops.
    // Touch timing is the raw press below, untouched.
    val toggle = { if (isTalking) up(false) else down() }
    return semantics {
        role = Role.Button
        contentDescription = label
        stateDescription = if (talking) "Talking" else "Ready"
        onClick(if (talking) "Stop talking" else "Start talking") { toggle(); true }
        customActions = listOf(CustomAccessibilityAction(if (talking) "Stop talking" else "Start talking") { toggle(); true })
    }.pointerInput(Unit) {
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
    if (LocalPhone.current) { PhoneTalkBar(bar, onPress, onRelease, modifier); return }
    if (LocalParentTablet.current) { TabletTalkBar(bar, onPress, onRelease, modifier); return }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            Modifier.height(52.dp).clip(RoundedCornerShape(26.dp))
                .background(if (bar.talking) C.accent else C.surface2)
                .alpha(if (bar.enabled || bar.talking) 1f else 0.5f)
                .holdToTalk(bar.button, bar.talking, onPress, onRelease)
                .padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Lucide.Mic, null, tint = if (bar.talking) C.onAccent else C.text, modifier = Modifier.size(22.dp))
            Text(bar.button, style = body(17.sp, 700), color = if (bar.talking) C.onAccent else C.text, maxLines = 1)
        }
        Muted(bar.line, 16, Modifier, maxLines = 2)
    }
}

/**
 * Tablet parent: the bar is at least 64 high; the hold button is 52 (radius 14, 20 glyph, 16 label) and only its colours animate (120ms).
 * The helper is 14/20 beside the button. When the stage is under 720 wide or the font is at 1.5 or more the helper goes under the button
 * with 8 between, in a slot that is at least 40 high in every state, so the camera feeds below never move.
 */
@Composable
private fun TabletTalkBar(bar: TalkBar, onPress: () -> Unit, onRelease: (Boolean) -> Unit, modifier: Modifier) {
    val bg by animateColorAsState(if (bar.talking) C.accent else C.surface2, tween(120), label = "talkBg")
    val fg by animateColorAsState(if (bar.talking) C.onAccent else C.text, tween(120), label = "talkFg")
    val stackedByFont = LocalDensity.current.fontScale >= 1.5f
    BoxWithConstraints(modifier.fillMaxWidth().heightIn(min = 64.dp)) {
        val stacked = maxWidth < 720.dp || stackedByFont
        val button: @Composable (Modifier) -> Unit = { m ->
            Row(
                m.heightIn(min = 52.dp).clip(RoundedCornerShape(14.dp))
                    .background(bg)
                    .alpha(if (bar.enabled || bar.talking) 1f else 0.5f)
                    .holdToTalk(bar.button, bar.talking, onPress, onRelease)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                Icon(Lucide.Mic, null, tint = fg, modifier = Modifier.size(20.dp))
                Text(bar.button, style = body(16.sp, 700, 1.25f), color = fg, maxLines = 1)
            }
        }
        if (stacked) Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            button(Modifier.fillMaxWidth())
            Text(bar.line, style = body(14.sp, 500, 20f / 14), color = C.muted, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp))
        } else Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            button(Modifier)
            Text(bar.line, style = body(14.sp, 500, 20f / 14), color = C.muted, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
    }
}

/**
 * Phone: a full-width 52dp hold button whose geometry never changes (only the colours animate, 120ms) and a helper slot
 * of two 14sp lines under it that is reserved in every state, so the camera feeds above never move.
 */
@Composable
private fun PhoneTalkBar(bar: TalkBar, onPress: () -> Unit, onRelease: (Boolean) -> Unit, modifier: Modifier) {
    val bg by animateColorAsState(if (bar.talking) C.accent else C.surface2, tween(120), label = "talkBg")
    val fg by animateColorAsState(if (bar.talking) C.onAccent else C.text, tween(120), label = "talkFg")
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp))
                .background(bg)
                .alpha(if (bar.enabled || bar.talking) 1f else 0.5f)
                .holdToTalk(bar.button, bar.talking, onPress, onRelease)
                .padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
        ) {
            Icon(Lucide.Mic, null, tint = fg, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(bar.button, style = body(17.sp, 700), color = fg, maxLines = 1)
        }
        Text(
            bar.line, style = body(14.sp, 500, 1.4f), color = C.muted, minLines = 2, maxLines = 2,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** A tablet tile's own small microphone, 48dp like its neighbours. Hold it to talk to this tablet only. */
@Composable
fun TileMic(t: TileTalk) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(if (t.talking) C.accent else C.surface)
            .alpha(if (t.enabled || t.talking) 1f else 0.4f)
            .holdToTalk("Talk", t.talking, t.onPress, t.onRelease),
        contentAlignment = Alignment.Center,
    ) { Icon(Lucide.Mic, null, tint = if (t.talking) C.onAccent else C.text, modifier = Modifier.size(20.dp)) }
}
