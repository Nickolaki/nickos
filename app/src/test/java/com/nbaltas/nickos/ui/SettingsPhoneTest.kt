package com.nbaltas.nickos.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.nbaltas.nickos.ui.master.SettingsScreen
import com.nbaltas.nickos.ui.theme.C
import com.nbaltas.nickos.ui.theme.NickTheme
import com.nbaltas.nickos.ui.theme.ParentLayoutProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Phone Settings navigation: the list, a section page, and what Back does at each level. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
class SettingsPhoneTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun systemBack() = rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

    private fun show(section: Int?, onBack: () -> Unit) {
        rule.setContent { NickTheme { Box(Modifier.fillMaxSize().background(C.bg)) { ParentLayoutProvider { SettingsScreen(section, {}, onBack, {}) } } } }
        rule.waitForIdle()
    }

    /** List -> section -> Back -> list -> Back calls onBack, once. */
    @Test fun backWalksSectionToListToOutside() {
        var backs = 0
        show(null, { backs++ })
        // The list: every section is a row, none of the pages is open.
        rule.onNodeWithText("Theme").assertExists()
        rule.onNodeWithText("About").assertExists()
        rule.onNodeWithText("Parent PIN").assertExists()
        rule.onNodeWithText("Theme").performClick()
        rule.waitForIdle()
        // The section page has its own header with a Back button, and the list's other rows are gone.
        rule.onNodeWithContentDescription("Back").assertExists()
        rule.onNodeWithText("Parent PIN").assertDoesNotExist()
        rule.onNodeWithContentDescription("Back").performClick()
        rule.waitForIdle()
        assertEquals("Back from a section must stay in Settings", 0, backs)
        rule.onNodeWithText("Parent PIN").assertExists()
        rule.onNodeWithContentDescription("Back").performClick()
        rule.waitForIdle()
        assertEquals("Back from the list leaves Settings", 1, backs)
    }

    /** The picture of a section page reached by tapping its row in the list. */
    @Test fun sectionPageFromTheListPicture() {
        show(null, {})
        rule.onNodeWithText("Theme").performClick()
        rule.waitForIdle()
        rule.onRoot().captureRoboImage(
            "../design/screens/phone/Settings-Phone-Section-Theme.png",
            roborazziOptions = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.0005f)),
        )
    }

    /** The system Back gesture does the same as the on-screen Back. */
    @Test fun systemBackWalksSectionToListToOutside() {
        var backs = 0
        show(null, { backs++ })
        rule.onNodeWithText("Cameras").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Parent PIN").assertDoesNotExist()
        systemBack()
        rule.waitForIdle()
        assertEquals(0, backs)
        rule.onNodeWithText("Parent PIN").assertExists()
        systemBack()
        rule.waitForIdle()
        assertEquals(1, backs)
    }

    /** A direct section (the update badge opens About) starts on that page; Back then shows the list, not the Home. */
    @Test fun directSectionOpensThenBackShowsTheList() {
        var backs = 0
        show(4, { backs++ })
        rule.onNodeWithText("Parent PIN").assertDoesNotExist()
        rule.onNodeWithContentDescription("Back").performClick()
        rule.waitForIdle()
        assertEquals(0, backs)
        rule.onNodeWithText("Parent PIN").assertExists()
    }

    /** The open section survives the activity being recreated (a rotation). */
    @Test fun openSectionSurvivesRecreation() {
        val restoration = StateRestorationTester(rule)
        restoration.setContent { NickTheme { Box(Modifier.fillMaxSize().background(C.bg)) { ParentLayoutProvider { SettingsScreen(null, {}, {}, {}) } } } }
        rule.onNodeWithText("Cameras").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Parent PIN").assertDoesNotExist()
        restoration.emulateSavedInstanceStateRestore()
        rule.waitForIdle()
        rule.onNodeWithText("Parent PIN").assertDoesNotExist()
        rule.onNodeWithContentDescription("Back").assertExists()
    }
}
