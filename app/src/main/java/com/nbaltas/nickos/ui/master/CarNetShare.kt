package com.nbaltas.nickos.ui.master

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Words on the car network card's "show name and password" reveal. */
const val CARNET_SHOW = "Show name and password"
const val CARNET_HIDE = "Hide"
const val CARNET_SHARE_HELP = "Use these to join a camera or another device to the car network. In the Tapo app, pick this network when it asks for WiFi."

/**
 * The standard Wi-Fi QR payload a phone camera understands: `WIFI:T:WPA;S:<name>;P:<password>;;`.
 * The characters `\ ; , : "` are escaped with a backslash. A value made only of hex digits would be read as raw
 * hex, so (as the format says) it is wrapped in double quotes. The result holds the password: never log it.
 */
fun wifiQrPayload(ssid: String, pass: String): String = "WIFI:T:WPA;S:${wifiField(ssid)};P:${wifiField(pass)};;"

private fun wifiField(v: String): String {
    val esc = buildString { v.forEach { c -> if (c in "\\;,:\"") append('\\'); append(c) } }
    val hex = v.isNotEmpty() && v.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
    return if (hex) "\"$esc\"" else esc
}

internal fun wifiQrBitmap(text: String, size: Int): Bitmap {
    val m = QRCodeWriter().encode(
        text, BarcodeFormat.QR_CODE, size, size,
        mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.CHARACTER_SET to "UTF-8"),
    )
    val px = IntArray(size * size) { i -> if (m.get(i % size, i / size)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
    return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
}
