package com.nbaltas.nickos.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import com.nbaltas.nickos.ui.setup.AddTabletScreen
import com.nbaltas.nickos.ui.setup.PairingCodeBody
import com.nbaltas.nickos.ui.theme.NickTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Behaviour checks for the pairing screens that a picture comparison would not name. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h600dp-land-mdpi")
class SetupScreensTest {
    @get:Rule val rule = createComposeRule()

    private val grouped = "482 916"

    /** The Node's code is 6 digits, on ONE line, not cut off, at every size a tablet really has. */
    private fun assertCodeIntact() {
        rule.setContent { NickTheme { Box(Modifier.fillMaxSize()) { PairingCodeBody("Hailey's Tablet", 0, "initial", "482916") } } }
        rule.waitForIdle()
        val node = rule.onNodeWithText(grouped).fetchSemanticsNode()
        val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)!!.action!!.invoke(results)
        val layout = results.single()
        assertEquals("the 6-digit code wrapped onto more than one line", 1, layout.lineCount)
        assertTrue("the 6-digit code is wider than the room it has (${layout.size.width} > ${layout.layoutInput.constraints.maxWidth})", layout.size.width <= layout.layoutInput.constraints.maxWidth)
        assertFalse("part of the code was cut", layout.isLineEllipsized(0))
        assertEquals("only part of the code is visible (the rest was cut off) size=${layout.size} fs=${layout.layoutInput.style.fontSize} cons=${layout.layoutInput.constraints} sw=${layout.layoutInput.softWrap} lc=${layout.lineCount} text=${layout.layoutInput.text.length}", grouped.length, layout.getLineEnd(0, visibleEnd = true))
    }

    @Test fun codeFitsLandscape960() = assertCodeIntact()
    @Config(qualifiers = "w1024dp-h768dp-land-mdpi") @Test fun codeFitsLandscape1024() = assertCodeIntact()
    @Config(qualifiers = "w1100dp-h680dp-land-mdpi") @Test fun codeFitsLandscape1100() = assertCodeIntact()
    @Config(qualifiers = "w600dp-h960dp-port-mdpi") @Test fun codeFitsPortrait600() = assertCodeIntact()
    @Config(qualifiers = "w400dp-h800dp-port-mdpi") @Test fun codeFitsPhone() = assertCodeIntact()

    /** Robolectric installs the app with no signing certificate; the QR payload needs one. */
    private fun giveAppASignature() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        org.robolectric.Shadows.shadowOf(ctx.packageManager).getInternalMutablePackageInfo(ctx.packageName).signatures = arrayOf(android.content.pm.Signature("0102030405"))
    }

    private fun assertQrWholeAndBigEnough(minDp: Int) {
        giveAppASignature()
        rule.setContent { NickTheme { Box(Modifier.fillMaxSize()) { com.nbaltas.nickos.ui.master.QrSetupScreen({}, kotlinx.coroutines.Dispatchers.Unconfined) } } }
        rule.waitForIdle()
        val n = rule.onNodeWithContentDescription("Setup QR code").assertExists().fetchSemanticsNode()
        val b = n.boundsInRoot
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val dp = rule.density.run { b.width.toDp().value }
        assertTrue("the QR code is clipped by the screen: $b in $root", b.left >= root.left && b.top >= root.top && b.right <= root.right && b.bottom <= root.bottom)
        assertTrue("the QR code is too small to scan reliably ($dp dp)", dp >= minDp)
    }

    @Config(sdk = [27]) @Test fun qrSetupWholeAtLandscape960() = assertQrWholeAndBigEnough(380)
    @Config(sdk = [27], qualifiers = "w1024dp-h768dp-land-mdpi") @Test fun qrSetupWholeAtLandscape1024() = assertQrWholeAndBigEnough(420)

    @Test fun addTabletStillOffersQrSetupWhenWired() {
        rule.setContent { NickTheme { Box(Modifier.fillMaxSize()) { AddTabletScreen({}, "Master · Add tablet", onQr = {}) {} } } }
        rule.waitForIdle()
        rule.onNodeWithText("Brand-new", substring = true).assertExists()
    }
}
