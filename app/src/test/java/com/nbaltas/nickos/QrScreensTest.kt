package com.nbaltas.nickos

import android.content.pm.Signature
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.nbaltas.nickos.ui.master.QrSetupScreen
import com.nbaltas.nickos.ui.theme.C
import com.nbaltas.nickos.ui.theme.NickTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** (sdk 27, the Master is an Android 7 tablet and Robolectric only signs the app for the old API.) Pictures of the QR setup screen with the new copy and the checksum line (names are new; none overwrite an existing picture). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [27], qualifiers = "w1024dp-h768dp-land-mdpi")
class QrScreensTest {
    @get:Rule val rule = createComposeRule()

    @Before fun sign() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        shadowOf(ctx.packageManager).getInternalMutablePackageInfo(ctx.packageName).signatures = arrayOf(Signature("0102030405"))
    }

    private fun shot(name: String, sig: String?) {
        rule.setContent {
            NickTheme { Box(Modifier.fillMaxSize().background(C.bg)) { QrSetupScreen({}, kotlinx.coroutines.Dispatchers.Unconfined, { sig }) } }
        }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage(
            "../design/screens/$name.png",
            roborazziOptions = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.0005f)),
        )
    }

    @Test fun qr1024ServerChecksum() {
        shot("Master-QR-1024", "x".repeat(43))
        rule.onNodeWithText("Code matches the NickOS on the update server.").assertExists()
    }

    @Test fun qr1024LocalChecksumShowsFallbackLine() {
        shot("Master-QR-1024-local", null)
        rule.onNodeWithText("Code matches this tablet's NickOS", substring = true).assertExists()
    }

    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    @Test fun qr1280() = shot("Master-QR-1280", "x".repeat(43))

    @Config(qualifiers = "w960dp-h600dp-land-mdpi")
    @Test fun qr960() = shot("Master-QR-960", "x".repeat(43))

    @Test fun stepsUseTheNewCopy() {
        rule.setContent { NickTheme { Box(Modifier.fillMaxSize()) { QrSetupScreen({}, kotlinx.coroutines.Dispatchers.Unconfined, { null }) } } }
        rule.waitForIdle()
        rule.onNodeWithText("Start from a new or factory reset tablet", substring = true).assertExists()
        rule.onNodeWithText("A QR scanner opens.", substring = true).assertExists()
        rule.onNodeWithText("Put your home WiFi here", substring = true).assertExists()
    }
}
