package com.nbaltas.nickos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.nbaltas.nickos.link.NetMessages
import com.nbaltas.nickos.ui.master.BatteryBody
import com.nbaltas.nickos.ui.master.SettingsScreen
import com.nbaltas.nickos.ui.theme.C
import com.nbaltas.nickos.ui.theme.NickTheme
import com.nbaltas.nickos.ui.theme.ParentLayoutProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** One picture each for the power controls: the Master's own Settings card and a kid tablet's Battery card. Baselines are recorded by the foreman. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1024dp-h768dp-land-mdpi")
class PowerScreensTest {
    @get:Rule val rule = createComposeRule()

    init { com.nbaltas.nickos.ui.theme.strictSizes = true }

    private val options = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.0005f))

    @Test fun settingsCard() {
        rule.setContent { NickTheme { ParentLayoutProvider { Box(Modifier.fillMaxSize().background(C.bg)) { SettingsScreen(0, {}, {}, {}) } } } }
        rule.waitForIdle()
        rule.onNodeWithText("Wake the screen when plugged in").assertExists()
        rule.onRoot().captureRoboImage("../design/screens/Master-Settings-Power.png", roborazziOptions = options)
    }

    @Test fun controlCard() {
        rule.setContent {
            NickTheme {
                ParentLayoutProvider {
                    Box(Modifier.fillMaxSize().background(C.bg).padding(24.dp)) {
                        // A content-height card: it grows with the choices and the permission line.
                        Box(Modifier.width(400.dp).background(C.surface).padding(20.dp)) {
                            BatteryBody(62, false, NetMessages.PowerStatus(true, 5, NetMessages.STATE_NO_PERMISSION)) { _, _ -> }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("Needs permission on the tablet").assertExists()
        rule.onRoot().captureRoboImage("../design/screens/Control-Battery-Power.png", roborazziOptions = options)
    }

    @Test fun oldNodeHidesTheControls() {
        rule.setContent { NickTheme { ParentLayoutProvider { BatteryBody(62, false, null) { _, _ -> } } } }
        rule.waitForIdle()
        rule.onNodeWithText("Wake the screen when plugged in").assertDoesNotExist()
    }
}
