package com.nbaltas.nickos.ui

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import com.nbaltas.nickos.remote.RemoteMessages
import com.nbaltas.nickos.remote.TouchMap
import com.nbaltas.nickos.ui.master.ScreenViewer
import com.nbaltas.nickos.ui.theme.NickTheme
import com.nbaltas.nickos.ui.theme.ParentLayoutProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Screen viewer's tablet chrome (JOB-0061) must not touch the picture: its fit rectangle and the pixel to normalised conversion are the
 * existing [TouchMap]. These pin the corners and the centre of that mapping, and check the viewer still reports exactly them.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-mdpi")
class ScreenViewerTouchTest {
    @get:Rule val rule = createComposeRule()

    private fun assertNorm(x: Double, y: Double, n: TouchMap.Norm?) {
        assertNotNull(n)
        assertEquals(x, n!!.x, 1e-9)
        assertEquals(y, n.y, 1e-9)
    }

    @Test fun cornersAndCentreOfAPortraitPictureInALandscapeView() {
        // A 300x500 picture in a 1000x500 view: scale 1, a 300 wide box with 350 of black bar each side.
        val box = TouchMap.content(1000f, 500f, 300, 500)!!
        assertEquals(350f, box.left, 0f); assertEquals(0f, box.top, 0f); assertEquals(300f, box.width, 0f); assertEquals(500f, box.height, 0f)
        assertNorm(0.0, 0.0, TouchMap.toNorm(350f, 0f, 1000f, 500f, 300, 500))
        assertNorm(1.0, 0.0, TouchMap.toNorm(650f, 0f, 1000f, 500f, 300, 500))
        assertNorm(0.0, 1.0, TouchMap.toNorm(350f, 500f, 1000f, 500f, 300, 500))
        assertNorm(1.0, 1.0, TouchMap.toNorm(650f, 500f, 1000f, 500f, 300, 500))
        assertNorm(0.5, 0.5, TouchMap.toNorm(500f, 250f, 1000f, 500f, 300, 500))
    }

    @Test fun cornersAndCentreOfALandscapePictureInAPortraitView() {
        // An 800x400 picture in a 400x1000 view: scale 0.5, a 400x200 box with 400 of black bar above and below.
        val box = TouchMap.content(400f, 1000f, 800, 400)!!
        assertEquals(0f, box.left, 0f); assertEquals(400f, box.top, 0f); assertEquals(400f, box.width, 0f); assertEquals(200f, box.height, 0f)
        assertNorm(0.0, 0.0, TouchMap.toNorm(0f, 400f, 400f, 1000f, 800, 400))
        assertNorm(1.0, 0.0, TouchMap.toNorm(400f, 400f, 400f, 1000f, 800, 400))
        assertNorm(0.0, 1.0, TouchMap.toNorm(0f, 600f, 400f, 1000f, 800, 400))
        assertNorm(1.0, 1.0, TouchMap.toNorm(400f, 600f, 400f, 1000f, 800, 400))
        assertNorm(0.5, 0.5, TouchMap.toNorm(200f, 500f, 400f, 1000f, 800, 400))
    }

    /** The viewer on a tablet: a tap at the picture's centre and near its corners arrives as the same 0..1 values the mapping gives. */
    @Test fun theViewerReportsTheSameNormalisedTouchAsTheMapping() {
        val taps = mutableListOf<Pair<Double, Double>>()
        val bmp = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)
        rule.setContent {
            NickTheme {
                ParentLayoutProvider {
                    ScreenViewer(
                        frame = bmp, state = RemoteMessages.LIVE, control = true, lost = false,
                        onStop = {}, onWatchAgain = {}, onBack = {}, onHome = {},
                        onTap = { x, y -> taps += x to y }, onSwipe = { _, _, _, _, _ -> },
                    )
                }
            }
        }
        rule.waitForIdle()
        val picture = rule.onNodeWithContentDescription("The tablet's screen")
        val size = picture.fetchSemanticsNode().size
        val vw = size.width.toFloat()
        val vh = size.height.toFloat()
        val fit = TouchMap.content(vw, vh, bmp.width, bmp.height)!!
        // Inside the corners by two pixels (a tap exactly on the edge is rounded by the pointer), then the centre.
        val points = listOf(
            Offset(fit.left + 2f, fit.top + 2f), Offset(fit.left + fit.width - 2f, fit.top + 2f),
            Offset(fit.left + 2f, fit.top + fit.height - 2f), Offset(fit.left + fit.width - 2f, fit.top + fit.height - 2f),
            Offset(fit.left + fit.width / 2f, fit.top + fit.height / 2f),
        )
        points.forEach { p -> picture.performTouchInput { click(p) } }
        rule.waitForIdle()
        assertEquals(points.size, taps.size)
        points.forEachIndexed { i, p ->
            val expected = TouchMap.toNorm(p.x, p.y, vw, vh, bmp.width, bmp.height)!!
            assertEquals("tap $i x", expected.x, taps[i].first, 1e-6)
            assertEquals("tap $i y", expected.y, taps[i].second, 1e-6)
        }
        assertEquals(0.5, taps.last().first, 0.01)
        assertEquals(0.5, taps.last().second, 0.01)
    }
}
