package com.nbaltas.nickos.ui.master

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import com.nbaltas.nickos.ui.theme.LocalParentTablet
import com.nbaltas.nickos.ui.theme.LocalPhone
import com.nbaltas.nickos.ui.theme.TabletParentMetrics
import com.nbaltas.nickos.ui.theme.disp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nbaltas.nickos.link.NetMessages
import com.nbaltas.nickos.node.PauseAll
import com.nbaltas.nickos.ui.theme.BtnStyle
import com.nbaltas.nickos.ui.theme.C
import com.nbaltas.nickos.ui.theme.NickButton
import com.nbaltas.nickos.ui.theme.NickField
import com.nbaltas.nickos.ui.theme.Solid
import com.nbaltas.nickos.ui.theme.Title
import com.nbaltas.nickos.ui.theme.body
import com.nbaltas.nickos.ui.theme.tap

/** The messages a parent can send with one tap. */
val PAUSE_PRESETS = listOf("Look out the window!", "Toilet stop", "Time to eat", "Listen to Mum and Dad")

/**
 * "Pause all" sheet on the Master home. While everyone is [paused] the big button reads "Resume everyone".
 * [onSubmit] gets the new state and the message; [onClose] closes the sheet without doing anything.
 */
@Composable
fun PauseAllSheet(paused: Boolean, startMsg: String = "", onSubmit: (Boolean, String) -> Unit, onClose: () -> Unit) {
    var msg by remember { mutableStateOf(startMsg) }
    if (LocalPhone.current) {
        // Bottom sheet: 16dp padding, full-width wrapping presets (no ellipsis), equal 52dp actions. Parent insets come from MainActivity.
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).tap(onClose), contentAlignment = Alignment.BottomCenter) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(C.surface).tap {}
                    .verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(if (paused) "Everyone is paused" else "Pause all tablets", style = disp(24.sp, 800, 1.15f), color = C.text)
                if (!paused) {
                    PAUSE_PRESETS.forEach { p ->
                        val sel = msg == p
                        Box(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp)).background(if (sel) C.accent else C.surface2)
                                .selectable(selected = sel, role = Role.RadioButton) { msg = p }.padding(horizontal = 16.dp, vertical = 10.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) { Text(p, style = body(16.sp, 700, 1.25f), color = if (sel) C.onAccent else C.text) }
                    }
                    NickField(msg, { msg = it.take(NetMessages.MAX_PAUSE_MSG) }, placeholder = "Or type a message")
                }
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NickButton("Cancel", onClose, Modifier.weight(1f).fillMaxHeight().heightIn(min = 52.dp), style = BtnStyle.GHOST)
                    NickButton(
                        if (paused) "Resume everyone" else "Pause everyone", { onSubmit(!paused, msg) }, Modifier.weight(1f).fillMaxHeight().heightIn(min = 52.dp),
                        style = BtnStyle.PRIMARY, leading = if (paused) Solid.Play else Solid.Pause,
                        enabled = PauseAll.canSubmit(paused, msg),
                    )
                }
            }
        }
        return
    }
    if (LocalParentTablet.current) {
        TabletPauseSheet(paused, msg, { msg = it }, onSubmit, onClose)
        return
    }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).tap(onClose), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(24.dp).clip(RoundedCornerShape(30.dp)).background(C.surface).tap {}.padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Title(if (paused) "Everyone is paused" else "Pause all tablets", 36)
            if (!paused) {
                PAUSE_PRESETS.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { p ->
                            val sel = msg == p
                            Box(
                                Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).background(if (sel) C.accent else C.surface2)
                                    .clickable { msg = p }.padding(horizontal = 18.dp, vertical = 18.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text(p, style = body(18.sp, 700), color = if (sel) C.onAccent else C.text, maxLines = 2) }
                        }
                    }
                }
                NickField(msg, { msg = it.take(NetMessages.MAX_PAUSE_MSG) }, placeholder = "Or type a message")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.End)) {
                NickButton("Cancel", onClose, style = BtnStyle.GHOST)
                NickButton(
                    if (paused) "Resume everyone" else "Pause everyone", { onSubmit(!paused, msg) },
                    style = BtnStyle.PRIMARY, big = true, leading = if (paused) Solid.Play else Solid.Pause,
                    enabled = PauseAll.canSubmit(paused, msg),
                )
            }
        }
    }
}

/**
 * Tablet parent pause panel: centred, at most 560 wide with 24 clearance, padding 24, radius 24, gaps 16. Two equal preset
 * columns (48 high, wrapping 16sp labels, 12 gap); the actions sit side by side and stack at large font scales. The panel
 * scrolls inside the usable height and above the keyboard. Tapping outside closes it, as before.
 */
@Composable
private fun TabletPauseSheet(paused: Boolean, msg: String, setMsg: (String) -> Unit, onSubmit: (Boolean, String) -> Unit, onClose: () -> Unit) {
    val stacked = LocalDensity.current.fontScale >= 1.5f
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).tap(onClose).imePadding(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(24.dp).widthIn(max = 560.dp).fillMaxWidth().clip(RoundedCornerShape(TabletParentMetrics.dialogRadius)).background(C.surface).tap {}
                .verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(if (paused) "Everyone is paused" else "Pause all tablets", style = disp(26.sp, 800, 29.9f / 26f), color = C.text)
            if (!paused) {
                PAUSE_PRESETS.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { p ->
                            val sel = msg == p
                            Box(
                                Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp).clip(RoundedCornerShape(TabletParentMetrics.buttonRadius))
                                    .background(if (sel) C.accent else C.surface2)
                                    .selectable(selected = sel, role = Role.RadioButton) { setMsg(p) }.padding(horizontal = 16.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text(p, style = body(16.sp, 700, 1.25f), color = if (sel) C.onAccent else C.text, textAlign = TextAlign.Center) }
                        }
                    }
                }
                NickField(msg, { setMsg(it.take(NetMessages.MAX_PAUSE_MSG)) }, placeholder = "Or type a message")
            }
            val cancel: @Composable (Modifier) -> Unit = { m -> NickButton("Cancel", onClose, m, style = BtnStyle.GHOST) }
            val submit: @Composable (Modifier) -> Unit = { m ->
                NickButton(
                    if (paused) "Resume everyone" else "Pause everyone", { onSubmit(!paused, msg) }, m.heightIn(min = 52.dp),
                    style = BtnStyle.PRIMARY, leading = if (paused) Solid.Play else Solid.Pause,
                    enabled = PauseAll.canSubmit(paused, msg),
                )
            }
            if (stacked) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    submit(Modifier.fillMaxWidth())
                    cancel(Modifier.fillMaxWidth())
                }
            } else {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    cancel(Modifier.weight(1f).fillMaxHeight().heightIn(min = 52.dp))
                    submit(Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}
