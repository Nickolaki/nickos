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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.nbaltas.nickos.update.Updater
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
import com.nbaltas.nickos.ui.theme.LocalPhone
import com.nbaltas.nickos.ui.theme.PhoneMetrics
import androidx.compose.foundation.layout.imePadding
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
fun QrSetupScreen(
    onBack: () -> Unit,
    qrDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Default,
    /** The release certificate checksum the update server publishes, or null. Fetched once; tests pass a fake. */
    serverSignature: suspend () -> String? = { Updater.check()?.signature },
) {
    val ctx = LocalContext.current
    var ssid by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var fromServer by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val s = try { serverSignature() } catch (e: Exception) { null }
        if (Provisioning.validChecksum(s)) fromServer = s
    }
    val qr by produceState<Bitmap?>(null, ssid, pass, fromServer) {
        value = withContext(qrDispatcher) { qrBitmap(Provisioning.qrPayload(ctx, ssid.trim(), pass, fromServer), 900) }
    }
    Column(Modifier.fillMaxSize()) {
        SetupHeader("Master · New kid tablet", onBack)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Landscape tablets (Orbic 960x600, Galaxy Tab S2 1024x768): code beside the steps, code sized to the height.
            val landscape = maxWidth > maxHeight && maxWidth >= 700.dp
            val narrow = maxWidth < 1000.dp && !landscape
            val compact = landscape && (maxWidth < 1100.dp || maxHeight < 800.dp)
            val phone = LocalPhone.current
            val qrSize = if (phone) (maxWidth - PhoneMetrics.gutter * 2).coerceIn(200.dp, 460.dp) else if (compact) (maxHeight - 40.dp - 30.dp).coerceIn(260.dp, 460.dp) else 460.dp
            val steps: @Composable (Modifier) -> Unit = { mod ->
                Column(mod, verticalArrangement = Arrangement.spacedBy(if (phone) 16.dp else if (compact) 12.dp else 20.dp)) {
                    Title("Set up a new kid tablet", if (compact) 38 else 48, lineHeight = 1.02f)
                    Muted("No cables or computer needed. The kid lock is switched on from the start.", if (compact) 17 else 20)
                    Step(1, "Start from a new or factory reset tablet. Settings, System, Reset options, Erase all data.")
                    Step(2, "On its first Welcome screen, tap the same empty spot 6 times. A QR scanner opens.")
                    Step(3, "Point it at this code. It joins the WiFi below, or asks you to pick one.")
                    Step(4, "It downloads NickOS and switches the kid lock on. When it finishes, NickOS opens. Name the tablet and enter its 6 digit code on this tablet.")
                    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Muted("Put your home WiFi here and the tablet joins it by itself during setup.", 16)
                        if (phone) {
                            Column { FieldLabel("WiFi name"); NickField(ssid, { ssid = it }) }
                            Column { FieldLabel("WiFi password"); NickField(pass, { pass = it }, password = true) }
                        } else Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column(Modifier.weight(1f)) { FieldLabel("WiFi name"); NickField(ssid, { ssid = it }) }
                            Column(Modifier.weight(1f)) { FieldLabel("WiFi password"); NickField(pass, { pass = it }, password = true) }
                        }
                    }
                }
            }
            val code: @Composable () -> Unit = {
                Column(Modifier.width(qrSize), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(qrSize).clip(RoundedCornerShape(if (phone) 20.dp else 36.dp)).background(Color.White).padding(if (phone) 12.dp else if (compact) 16.dp else 24.dp), contentAlignment = Alignment.Center) {
                        qr?.let { Image(it.asImageBitmap(), "Setup QR code", Modifier.fillMaxSize(), filterQuality = FilterQuality.None) }
                    }
                    Muted(checksumLine(fromServer != null), 14, maxLines = 3)
                }
            }
            if (narrow) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(if (phone) PhoneMetrics.gutter else 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(if (phone) 16.dp else 32.dp)) {
                    code(); steps(Modifier.fillMaxWidth())
                }
            } else {
                Row(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = if (compact) 32.dp else 80.dp, end = if (compact) 32.dp else 80.dp, bottom = if (compact) 16.dp else 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 32.dp else 64.dp), verticalAlignment = Alignment.CenterVertically,
                ) { steps(Modifier.weight(1f)); code() }
            }
        }
    }
}

/** Which checksum the code carries, in words a parent can act on. */
internal fun checksumLine(fromServer: Boolean) =
    if (fromServer) "Code matches the NickOS on the update server."
    else "Code matches this tablet's NickOS. If the kid tablet says it can't set up, check for updates here first."

@Composable
private fun Step(n: Int, text: String) {
    val phone = LocalPhone.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (phone) 12.dp else 16.dp)) {
        Box(Modifier.size(if (phone) 32.dp else 40.dp).clip(CircleShape).background(C.accent), contentAlignment = Alignment.Center) {
            Text("$n", style = disp(20.sp), color = C.onAccent)
        }
        Text(text, style = body(if (phone) 16.sp else 19.sp, 600, 1.35f), color = C.text, modifier = Modifier.widthIn(max = 560.dp))
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
