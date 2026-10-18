package com.nbaltas.nickos.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.nbaltas.nickos.BuildConfig
import com.nbaltas.nickos.ui.master.SettingsScreen
import com.nbaltas.nickos.ui.master.settingsUsesStrip
import com.nbaltas.nickos.ui.theme.NickTheme
import com.nbaltas.nickos.ui.theme.ParentLayoutProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The parent tablet Settings shell (JOB-0061 §2.6): the rail at 840 and up, the category strip below it and at large fonts. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-mdpi")
class SettingsTabletTest {
    @get:Rule val rule = createComposeRule()

    @Test fun stripBelow840AndAtLargeFont() {
        assertTrue(settingsUsesStrip(839f, 1f))
        assertTrue(settingsUsesStrip(700f, 1f))
        assertFalse(settingsUsesStrip(840f, 1f))
        assertFalse(settingsUsesStrip(1280f, 1.3f))
        assertTrue(settingsUsesStrip(1280f, 1.5f))
        assertTrue(settingsUsesStrip(1280f, 2f))
    }

    /** The section is the caller's, so crossing 840 in either direction keeps it; picking one in the strip survives widening to the rail. */
    @Test fun selectedSectionSurvivesWidthChangeAcrossTheRailBreak() {
        var section by mutableIntStateOf(3) // Parent PIN
        var width by mutableStateOf(900.dp)
        val version = "NickOS ${BuildConfig.VERSION_NAME}"
        rule.setContent {
            NickTheme {
                Box(Modifier.width(width).height(800.dp)) {
                    ParentLayoutProvider { SettingsScreen(section, { section = it }, {}, {}) }
                }
            }
        }
        rule.waitForIdle()
        // Rail: the version line is only there, and the PIN page is open with its category selected.
        rule.onNodeWithText(version).assertExists()
        rule.onNode(hasText("Parent PIN") and isSelected()).assertExists()
        rule.onNodeWithText("Enter a new 4-digit PIN").assertExists()

        // Strip: the same section is selected and shown.
        width = 700.dp
        rule.waitForIdle()
        rule.onNodeWithText(version).assertDoesNotExist()
        rule.onNode(hasText("Parent PIN") and isSelected()).assertExists()
        rule.onNodeWithText("Enter a new 4-digit PIN").assertExists()

        // Pick Theme in the strip, then widen to the rail: Theme is still the open section.
        rule.onNode(hasText("Theme") and hasClickAction()).performClick()
        rule.waitForIdle()
        assertEquals(4, section)
        width = 900.dp
        rule.waitForIdle()
        rule.onNodeWithText(version).assertExists()
        rule.onNode(hasText("Theme") and isSelected()).assertExists()
        rule.onNodeWithText("Match system").assertExists()
        rule.onNode(hasText("Parent PIN") and isSelected()).assertDoesNotExist()
    }
}
