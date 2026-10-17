package com.nbaltas.nickos.ui.master

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.semantics.Role
import com.nbaltas.nickos.ui.theme.LocalPhone
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NickButton("Cancel", onClose, Modifier.weight(1f), style = BtnStyle.GHOST)
                    NickButton(
                        if (paused) "Resume everyone" else "Pause everyone", { onSubmit(!paused, msg) }, Modifier.weight(1f),
                        style = BtnStyle.PRIMARY, leading = if (paused) Solid.Play else Solid.Pause,
                        enabled = PauseAll.canSubmit(paused, msg),
                    )
                }
            }
        }
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
