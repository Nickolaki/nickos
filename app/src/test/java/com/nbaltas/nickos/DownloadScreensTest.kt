package com.nbaltas.nickos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.nbaltas.nickos.master.DlState
import com.nbaltas.nickos.master.Progress
import com.nbaltas.nickos.ui.master.SaveActions
import com.nbaltas.nickos.ui.theme.C
import com.nbaltas.nickos.ui.theme.NickTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The save buttons on a Jellyfin movie: idle, downloading, and saved. New picture names; none overwrite an existing picture. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1024dp-h768dp-land-mdpi")
class DownloadScreensTest {
    @get:Rule val rule = createComposeRule()

    private fun shot(name: String, progress: Progress?, onTablet: Boolean = false) {
        rule.setContent {
            NickTheme {
                Box(Modifier.fillMaxSize().background(C.bg).padding(40.dp)) {
                    Box(Modifier.widthIn(max = 440.dp)) { SaveActions("Hailey's Tablet", progress, onTablet, {}, {}, {}, {}) }
                }
            }
        }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage(
            "../design/screens/$name.png",
            roborazziOptions = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.0005f)),
        )
    }

    @Test fun movieDetailSaveButtons() {
        shot("Master-Watch-Movie-Save", null)
        rule.onNodeWithText("Save to this tablet").assertExists()
        rule.onNodeWithText("Save and send to Hailey's Tablet").assertExists()
    }

    @Test fun movieDetailDownloadProgress() {
        shot("Master-Watch-Movie-Saving", Progress(420_000_000, 1_000_000_000, DlState.DOWNLOADING))
        rule.onNodeWithText("Saving to this tablet", substring = true).assertExists()
        rule.onNodeWithText("Cancel").assertExists()
    }

    @Test fun movieDetailSaved() {
        shot("Master-Watch-Movie-Saved", Progress(1, 1, DlState.DONE), onTablet = true)
        rule.onNodeWithText("On this tablet").assertExists()
        rule.onNodeWithText("Send to Hailey's Tablet").assertExists()
    }
}
