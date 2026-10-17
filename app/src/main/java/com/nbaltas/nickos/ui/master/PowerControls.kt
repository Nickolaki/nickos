package com.nbaltas.nickos.ui.master

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import com.nbaltas.nickos.ui.theme.LocalPhone
import com.nbaltas.nickos.ui.theme.PhoneMetrics
import com.nbaltas.nickos.power.PowerPolicy
import com.nbaltas.nickos.ui.theme.C
import com.nbaltas.nickos.ui.theme.NickSwitch
import com.nbaltas.nickos.ui.theme.body

/**
 * The two power settings, shared by the Master's own card and each kid tablet's card: a switch to wake the screen when
 * plugged in, and a row of idle times for the screen to go off on battery. [problem] is the red line under them, if any.
 */
@Composable
fun PowerControls(wake: Boolean, offMin: Int, problem: String?, onChange: (wake: Boolean, offMin: Int) -> Unit, modifier: Modifier = Modifier) {
    if (LocalPhone.current) {
        PhonePowerControls(wake, offMin, problem, onChange, modifier)
        return
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().toggleable(wake, role = androidx.compose.ui.semantics.Role.Switch) { onChange(it, offMin) },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Wake the screen when plugged in", style = body(17.sp, 700), color = C.text, modifier = Modifier.weight(1f))
            NickSwitch(wake) { onChange(it, offMin) }
        }
        Text("Screen off on battery", style = body(15.sp, 700), color = C.muted)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PowerControlsText.choices.forEach { min ->
                val sel = min == offMin
                Box(
                    Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(22.dp)).background(if (sel) C.accent else C.surface2)
                        .clickable { onChange(wake, min) },
                    contentAlignment = Alignment.Center,
                ) { Text(PowerControlsText.label(min), style = body(15.sp, 600), color = if (sel) C.onAccent else C.text, maxLines = 1) }
            }
        }
        if (problem != null) Text(problem, style = body(14.sp, 700), color = C.danger)
    }
}

/** Phone: a full-width wrapping switch row, then idle times as equal pills (two per row at large font sizes), each at least 48dp high. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhonePowerControls(wake: Boolean, offMin: Int, problem: String?, onChange: (wake: Boolean, offMin: Int) -> Unit, modifier: Modifier) {
    val scaled = LocalDensity.current.fontScale >= 1.5f
    Column(modifier, verticalArrangement = Arrangement.spacedBy(PhoneMetrics.relatedGap)) {
        SwitchRow("Wake the screen when plugged in", wake) { onChange(it, offMin) }
        Text("Screen off on battery", style = body(14.sp, 700, 1.3f), color = C.muted)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = if (scaled) 2 else PowerControlsText.choices.size,
        ) {
            PowerControlsText.choices.forEach { min ->
                val sel = min == offMin
                val bg by animateColorAsState(if (sel) C.accent else C.surface2, tween(120), label = "powerBg")
                Box(
                    Modifier.weight(1f).heightIn(min = PhoneMetrics.minTarget).clip(RoundedCornerShape(PhoneMetrics.nestedRadius)).background(bg)
                        .selectable(selected = sel, role = Role.RadioButton) { onChange(wake, min) }.padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(PowerControlsText.label(min), style = body(14.sp, 700, 1.25f), color = if (sel) C.onAccent else C.text, textAlign = TextAlign.Center) }
            }
        }
        if (problem != null) Text(problem, style = body(14.sp, 700, 1.4f), color = C.danger)
    }
}

object PowerControlsText {
    val choices get() = PowerPolicy.CHOICES
    fun label(min: Int) = if (min == 0) "Off" else "$min min"
}
