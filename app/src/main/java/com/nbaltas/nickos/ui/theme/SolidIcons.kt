package com.nbaltas.nickos.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Filled transport glyphs: the design draws these solid, Lucide only has outlines. */
object Solid {
    private fun icon(name: String, block: ImageVector.Builder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(block).build()

    private fun ImageVector.Builder.shape(d: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) =
        path(fill = SolidColor(Color.Black), stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineJoin = StrokeJoin.Round, pathBuilder = d)

    val Play = icon("SolidPlay") { shape { moveTo(7f, 4.5f); lineTo(19.5f, 12f); lineTo(7f, 19.5f); close() } }

    val Pause = icon("SolidPause") {
        shape { moveTo(6.5f, 5f); lineTo(9.5f, 5f); lineTo(9.5f, 19f); lineTo(6.5f, 19f); close() }
        shape { moveTo(14.5f, 5f); lineTo(17.5f, 5f); lineTo(17.5f, 19f); lineTo(14.5f, 19f); close() }
    }

    val Stop = icon("SolidStop") { shape { moveTo(6.5f, 6.5f); lineTo(17.5f, 6.5f); lineTo(17.5f, 17.5f); lineTo(6.5f, 17.5f); close() } }

    val Next = icon("SolidNext") {
        shape { moveTo(5.5f, 5.5f); lineTo(15f, 12f); lineTo(5.5f, 18.5f); close() }
        shape { moveTo(17.5f, 5.5f); lineTo(18.5f, 5.5f); lineTo(18.5f, 18.5f); lineTo(17.5f, 18.5f); close() }
    }
}
