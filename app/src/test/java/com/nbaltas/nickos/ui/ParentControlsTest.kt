package com.nbaltas.nickos.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.center
import androidx.compose.ui.test.centerY
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveTo
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.Density
import com.nbaltas.nickos.ui.master.PauseAllSheet
import com.nbaltas.nickos.ui.theme.NickSlider
import com.nbaltas.nickos.ui.theme.NickSwitch
import com.nbaltas.nickos.ui.theme.NickTheme
import com.nbaltas.nickos.ui.theme.ParentLayoutProvider
import com.nbaltas.nickos.ui.theme.Segmented
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The stage 1 review's regression tests: slider commits, the switch inside a toggle row, segment selection and the pause sheet's buttons. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xhdpi")
class ParentControlsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun sliderCommitsOnlyAfterRelease() {
        val commits = mutableListOf<Float>()
        rule.setContent {
            NickTheme {
                ParentLayoutProvider {
                    NickSlider(0.25f, {}, { commits += it }, Modifier.testTag("seek"))
                }
            }
        }
        val slider = rule.onNodeWithTag("seek")
        slider.performTouchInput {
            down(center)
            moveTo(Offset(width * .8f, centerY), delayMillis = 100)
        }
        rule.runOnIdle { assertEquals(0, commits.size) }
        slider.performTouchInput { up() }
        rule.runOnIdle { assertEquals(1, commits.size) }
        slider.performTouchInput { click(center) }
        rule.runOnIdle { assertEquals(2, commits.size) }
    }

    @Test fun nestedSwitchSendsOnceAndDisabledSendsNothing() {
        val sent = mutableListOf<Boolean>()
        rule.setContent {
            NickTheme {
                ParentLayoutProvider {
                    Column {
                        Row(Modifier.toggleable(false) { sent += it }) {
                            Text("Touch")
                            NickSwitch(false) { sent += it }
                        }
                        NickSwitch(false, enabled = false) { sent += it }
                    }
                }
            }
        }
        val switches = rule.onAllNodes(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch),
            useUnmergedTree = true,
        )
        // The outer row deliberately has no Role; these are the two NickSwitch nodes.
        switches[0].performTouchInput { click(center) }
        rule.runOnIdle { assertEquals(listOf(true), sent) }
        switches[1].assertIsNotEnabled().performTouchInput { click(center) }
        rule.runOnIdle { assertEquals(listOf(true), sent) }
    }

    @Test fun phoneSegmentsExposeSelection() {
        rule.setContent {
            NickTheme {
                ParentLayoutProvider {
                    Segmented(listOf("One" to null, "Two" to null), 1, {})
                }
            }
        }
        rule.onNodeWithText("One").assertIsNotSelected()
        rule.onNodeWithText("Two").assertIsSelected()
    }

    @Test fun pauseActionsHaveEqualBoundsAtFont200() {
        rule.setContent {
            NickTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    ParentLayoutProvider {
                        PauseAllSheet(false, "Time to eat", { _, _ -> }, {})
                    }
                }
            }
        }
        rule.onNodeWithText("Pause everyone").performScrollTo()
        val cancel = rule.onNodeWithText("Cancel").fetchSemanticsNode().boundsInRoot
        val pause = rule.onNodeWithText("Pause everyone").fetchSemanticsNode().boundsInRoot
        assertEquals(cancel.top, pause.top, 1f)
        assertEquals(cancel.height, pause.height, 1f)
    }
}
