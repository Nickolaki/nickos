package com.nbaltas.nickos.ui.master

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.nbaltas.nickos.node.Provisioning
import com.nbaltas.nickos.ui.theme.C
import com.nbaltas.nickos.ui.theme.FieldLabel
import com.nbaltas.nickos.ui.theme.Muted
import com.nbaltas.nickos.ui.theme.NickField
import com.nbaltas.nickos.ui.theme.SetupHeader
import com.nbaltas.nickos.ui.theme.Title
import com.nbaltas.nickos.ui.theme.body
import com.nbaltas.nickos.ui.theme.disp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Zero-adb kid tablet setup. Not in the design handoff; follows the Add tablet screen's layout.
 * Only works on a new or factory-reset tablet: that's Android's rule for Device Owner.
 */
@Composable
fun QrSetupScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var ssid by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    val qr by produceState<Bitmap?>(null, ssid, pass) {
        value = withContext(Dispatchers.Default) { qrBitmap(Provisioning.qrPayload(ctx, ssid.trim(), pass), 900) }
    }
    Column(Modifier.fillMaxSize()) {
        SetupHeader("Master · New kid tablet", onBack)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val narrow = maxWidth < 1000.dp
            val steps: @Composable (Modifier) -> Unit = { mod ->
                Column(mod, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Title("Set up a new kid tablet", 48, lineHeight = 1.02f)
                    Muted("No cables or computer needed. The kid lock is switched on from the start.", 20)
                    Step(1, "Start from a new or factory-reset tablet (Settings → System → Reset).")
                    Step(2, "On its first \"Welcome\" screen, tap the same empty spot 6 times.")
                    Step(3, "Connect to WiFi if it asks, then point it at this code.")
                    Step(4, "It installs NickOS by itself. Then name the tablet and pair it with its 6-digit code.")
                    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Muted("Optional: put your WiFi in the code so the tablet joins it by itself.", 16)
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column(Modifier.weight(1f)) { FieldLabel("WiFi name"); NickField(ssid, { ssid = it }) }
                            Column(Modifier.weight(1f)) { FieldLabel("WiFi password"); NickField(pass, { pass = it }, password = true) }
                        }
                    }
                }
            }
            val code: @Composable () -> Unit = {
                Box(Modifier.size(460.dp).clip(RoundedCornerShape(36.dp)).background(Color.White).padding(24.dp), contentAlignment = Alignment.Center) {
                    qr?.let { Image(it.asImageBitmap(), "Setup QR code", Modifier.fillMaxSize(), filterQuality = FilterQuality.None) }
                }
            }
            if (narrow) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(32.dp)) {
                    code(); steps(Modifier.fillMaxWidth())
                }
            } else {
                Row(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 80.dp, end = 80.dp, bottom = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(64.dp), verticalAlignment = Alignment.CenterVertically,
                ) { steps(Modifier.weight(1f)); code() }
            }
        }
    }
}

@Composable
private fun Step(n: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(C.accent), contentAlignment = Alignment.Center) {
            Text("$n", style = disp(20.sp), color = C.onAccent)
        }
        Text(text, style = body(19.sp, 600, 1.35f), color = C.text, modifier = Modifier.width(560.dp))
    }
}

private fun qrBitmap(text: String, size: Int): Bitmap {
    val m = QRCodeWriter().encode(
        text, BarcodeFormat.QR_CODE, size, size,
        mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.CHARACTER_SET to "UTF-8"),
    )
    val px = IntArray(size * size) { i -> if (m.get(i % size, i / size)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
    return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
}
