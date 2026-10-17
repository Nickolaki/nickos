package com.nbaltas.nickos.ui.node

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nbaltas.nickos.App
import com.nbaltas.nickos.MainActivity
import com.nbaltas.nickos.node.KioskState
import com.nbaltas.nickos.node.KioskStep
import com.nbaltas.nickos.ui.theme.BtnStyle
import com.nbaltas.nickos.ui.theme.C
import com.nbaltas.nickos.ui.theme.Muted
import com.nbaltas.nickos.ui.theme.NickButton
import com.nbaltas.nickos.ui.theme.SetupHeader
import com.nbaltas.nickos.ui.theme.Title
import com.nbaltas.nickos.ui.theme.body
import com.nbaltas.nickos.ui.theme.tap
import kotlinx.coroutines.delay

/**
 * Launcher mode setup, for a tablet that is not Device Owner: four system pages, each a one-time grant.
 * Entering this screen opens the parent window so the watchdog does not bounce the settings page the parent is on.
 */
@Composable
fun KioskSetup(onStart: () -> Unit) {
    val ctx = LocalContext.current
    val rt = App.get(ctx).node
    var state by remember { mutableStateOf(rt.device.kioskState()) }
    var unavailable by remember { mutableStateOf(emptySet<KioskStep>()) }
    fun rearm() { rt.parentUntil = System.currentTimeMillis() + MainActivity.PARENT_MINUTES * 60_000L }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        rearm(); state = rt.device.kioskState()
    }
    LaunchedEffect(Unit) {
        rearm()
        // The system pages return with Back, which does not always report a result: look again every second.
        while (true) { delay(1000); state = rt.device.kioskState() }
    }
    KioskSetupScreen(
        state = state,
        unavailable = unavailable,
        onStep = { step ->
            rearm()
            val intent: Intent = when (step) {
                KioskStep.HOME -> rt.device.homeRequestIntent()
                KioskStep.OVERLAY -> rt.device.overlayIntent()
                KioskStep.USAGE -> rt.device.usageIntent()
                KioskStep.ADMIN -> rt.device.adminIntent()
            }
            try { launcher.launch(intent) } catch (e: ActivityNotFoundException) { unavailable = unavailable + step }
        },
        onStart = onStart,
    )
}

/** The picture of it. [state] decides each row; [unavailable] rows say so instead of offering a button. */
@Composable
fun KioskSetupScreen(
    state: KioskState,
    unavailable: Set<KioskStep> = emptySet(),
    onStep: (KioskStep) -> Unit = {},
    onStart: () -> Unit = {},
) {
    Column(Modifier.fillMaxSize().background(C.bg)) {
        SetupHeader("Kid lock", null)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 40.dp).padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 760.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Title("Make this the kids' tablet", 40, lineHeight = 1.0f)
                Muted("Each one opens an Android settings page. Switch NickOS on, then come back.", 17, maxLines = 1)
                Spacer8()
                StepRow("Make NickOS the home screen", state.home, KioskStep.HOME in unavailable) { onStep(KioskStep.HOME) }
                StepRow("Let NickOS show over other apps", state.overlay, KioskStep.OVERLAY in unavailable) { onStep(KioskStep.OVERLAY) }
                StepRow("Let NickOS see which app is open", state.usage, KioskStep.USAGE in unavailable) { onStep(KioskStep.USAGE) }
                StepRow("Let NickOS turn the screen off at bedtime", state.admin, KioskStep.ADMIN in unavailable) { onStep(KioskStep.ADMIN) }
                if (state.home && !(state.overlay && state.usage && state.admin)) {
                    Muted("The home screen is set. The other steps keep the kids inside NickOS and let bedtime switch the screen off.", 15, maxLines = 2)
                }
                Spacer8()
                NickButton("Start kid mode", onStart, Modifier.fillMaxWidth(), style = BtnStyle.PRIMARY, enabled = state.home)
                if (!state.home) Muted("Set the home screen first.", 15, maxLines = 1)
            }
        }
    }
}

/**
 * The kid lock was undone (an update, say) on a tablet that has been used. The kid stays on the kid home with the
 * watchdog on; only the parent PIN opens the setup. This card is the whole screen and the PIN pad is its only action.
 */
@Composable
fun AskGrownUpScreen(onPin: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(C.bg).padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Title("Ask a grown-up to finish the kid lock", 36, lineHeight = 1.05f)
            Muted("This tablet needs a quick check from a grown-up before it is ready again.", 17, maxLines = 2)
            NickButton("Enter the PIN", onPin, Modifier.fillMaxWidth(), style = BtnStyle.PRIMARY)
        }
    }
}

@Composable
private fun Spacer8() = Box(Modifier.height(4.dp))

@Composable
private fun StepRow(label: String, done: Boolean, unavailable: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(18.dp)).background(C.surface).padding(start = 24.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, style = body(19.sp, 700), color = C.text, modifier = Modifier.weight(1f), maxLines = 1)
        when {
            done -> Text("Done", style = body(18.sp, 700), color = C.online, modifier = Modifier.padding(end = 12.dp))
            unavailable -> Text("Not available on this tablet", style = body(15.sp, 600), color = C.muted, modifier = Modifier.padding(end = 12.dp))
            else -> Box(
                Modifier.height(44.dp).clip(RoundedCornerShape(14.dp)).background(C.accent).tap(onClick).padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Set up", style = body(17.sp, 700), color = C.onAccent) }
        }
    }
}
