package com.nbaltas.nickos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.nbaltas.nickos.node.KioskState
import com.nbaltas.nickos.node.KioskStatus
import com.nbaltas.nickos.node.KioskStep
import com.nbaltas.nickos.ui.node.KioskSetupScreen
import com.nbaltas.nickos.ui.node.ParentMenu
import com.nbaltas.nickos.ui.node.PinPad
import com.nbaltas.nickos.ui.node.screenNow
import com.nbaltas.nickos.ui.theme.C
import com.nbaltas.nickos.ui.theme.NickTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Pictures and copy checks for the Kid lock setup, the grown-up menu without Device Owner, and the locked PIN pad. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h600dp-land-mdpi")
class KioskScreensTest {
    @get:Rule val rule = createComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<App>()

    @After fun unpin() { screenNow = { System.currentTimeMillis() } }

    private fun shot(name: String, content: @Composable () -> Unit) {
        rule.setContent { NickTheme { Box(Modifier.fillMaxSize().background(C.bg)) { content() } } }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage(
            "../design/screens/$name.png",
            roborazziOptions = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.0005f)),
        )
    }

    private val none = KioskState(deviceOwner = false, home = false, overlay = false, usage = false, admin = false)

    @Test fun kioskSetup960AllUndone() {
        shot("Node-KioskSetup-960") { KioskSetupScreen(none) }
        rule.onNodeWithText("Make this the kids' tablet").assertExists()
        rule.onNodeWithText("Set the home screen first.").assertExists()
    }

    @Test fun kioskSetup960HomeDone() {
        shot("Node-KioskSetup-960-home") { KioskSetupScreen(none.copy(home = true)) }
        rule.onNodeWithText("Start kid mode").assertExists()
    }

    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    @Test fun kioskSetup1280() = shot("Node-KioskSetup-1280") { KioskSetupScreen(none) }

    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    @Test fun menuNotOwner() {
        shot("Node-Menu-NotOwner") {
            ParentMenu(
                kiosk = KioskStatus.Incomplete(listOf(KioskStep.OVERLAY, KioskStep.USAGE)),
                pinIsDefault = true,
                onKidMode = {}, onAllApps = {}, onSettings = {}, onPairAnother = {}, onUpdate = {}, onReset = {},
            )
        }
        rule.onNodeWithText("Finish kid lock").assertExists()
    }

    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    @Test fun pinLocked() {
        screenNow = { 1_000_000L }
        repeat(5) { app.node.pinGate.tryPin("1111", "2222", screenNow()) }
        shot("Node-PIN-Locked") { PinPad("2222", {}, {}) }
        rule.onNodeWithText("Too many tries. Wait 30 seconds.").assertExists()
    }
}
