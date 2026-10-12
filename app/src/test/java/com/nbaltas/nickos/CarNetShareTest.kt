package com.nbaltas.nickos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.nbaltas.nickos.master.CarNetState
import com.nbaltas.nickos.ui.master.CARNET_HIDE
import com.nbaltas.nickos.ui.master.CARNET_SHARE_HELP
import com.nbaltas.nickos.ui.master.CARNET_SHOW
import com.nbaltas.nickos.ui.master.SettingsScreen
import com.nbaltas.nickos.ui.master.wifiQrPayload
import com.nbaltas.nickos.ui.theme.C
import com.nbaltas.nickos.ui.theme.NickTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1024dp-h768dp-land-mdpi")
class CarNetShareTest {
    @get:Rule val rule = createComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<App>()

    @Test fun payloadIsTheStandardWifiString() {
        assertEquals("WIFI:T:WPA;S:DIRECT-NK-NickOS-a1b2;P:zz9plural;;", wifiQrPayload("DIRECT-NK-NickOS-a1b2", "zz9plural"))
    }

    @Test fun payloadEscapesTheSpecialCharacters() {
        assertEquals("""WIFI:T:WPA;S:a\;b\,c\:d\"e\\f;P:p\;\,\:\"\\q;;""", wifiQrPayload("""a;b,c:d"e\f""", """p;,:"\q"""))
    }

    @Test fun hexOnlyValuesAreQuoted() {
        assertEquals("""WIFI:T:WPA;S:"abcdef12";P:"0123456789abcdef";;""", wifiQrPayload("abcdef12", "0123456789abcdef"))
    }

    @Test fun nameAndPasswordAreHiddenUntilAskedFor() {
        val pass = app.store.carNetPass()
        @Suppress("UNCHECKED_CAST")
        (app.master.carNet.javaClass.getDeclaredField("_state").apply { isAccessible = true }.get(app.master.carNet) as MutableStateFlow<Any>)
            .value = CarNetState.On("DIRECT-NK-NickOS-a1b2", "192.168.49.1")
        rule.setContent { NickTheme { Box(Modifier.fillMaxSize().background(C.bg)) { SettingsScreen(0, {}, {}, {}) } } }
        rule.onNodeWithText(CARNET_SHOW).performScrollTo().assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithTextCount(pass))
        assertEquals(0, rule.onAllNodesWithTextCount(CARNET_SHARE_HELP))
        rule.onNodeWithText(CARNET_SHOW).performClick()
        rule.onNodeWithText(pass).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(CARNET_HIDE).performScrollTo().performClick()
        assertEquals(0, rule.onAllNodesWithTextCount(pass))
        assertTrue(rule.onAllNodesWithTextCount(CARNET_SHOW) == 1)
        assertFalse(CarNetState.On("x", null, "secretpass").toString().contains("secretpass"))
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(t: String) =
        onAllNodes(androidx.compose.ui.test.hasText(t)).fetchSemanticsNodes().size
}
