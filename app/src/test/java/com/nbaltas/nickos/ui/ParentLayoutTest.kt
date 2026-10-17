package com.nbaltas.nickos.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.nbaltas.nickos.ui.theme.LocalPhone
import com.nbaltas.nickos.ui.theme.ParentLayoutProvider
import com.nbaltas.nickos.ui.theme.isPhoneWidth
import com.nbaltas.nickos.ui.theme.phoneMutedSize
import com.nbaltas.nickos.ui.theme.phoneTitleSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The phone rule: under 600dp of available width is a phone, 600dp and up keeps the tablet layout. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-mdpi")
class ParentLayoutTest {
    @get:Rule val rule = createComposeRule()

    private fun phoneAt(widthDp: Int): Boolean {
        var phone: Boolean? = null
        rule.setContent {
            Box(Modifier.width(widthDp.dp).fillMaxHeight()) {
                ParentLayoutProvider { phone = LocalPhone.current }
            }
        }
        rule.waitForIdle()
        return phone!!
    }

    @Test fun phoneBelow600() = assertTrue(phoneAt(599))
    @Test fun tabletAt600() = assertFalse(phoneAt(600))
    @Test fun notPhoneWithoutProvider() {
        var phone: Boolean? = null
        rule.setContent { phone = LocalPhone.current }
        rule.waitForIdle()
        assertEquals(false, phone)
    }

    @Test fun widthRule() {
        assertTrue(isPhoneWidth(599.9f))
        assertFalse(isPhoneWidth(600f))
    }

    @Test fun titleSizesFollowTheSpec() {
        val spec = mapOf(
            24 to 18, 25 to 18, 26 to 18, 28 to 20, 30 to 20, 32 to 20, 34 to 22, 36 to 22,
            40 to 24, 44 to 24, 46 to 24, 48 to 24, 52 to 28, 56 to 28, 64 to 28, 72 to 28,
        )
        spec.forEach { (tablet, phone) -> assertEquals("Title $tablet", phone, phoneTitleSize(tablet)) }
    }

    @Test fun mutedSizesFollowTheSpec() {
        listOf(14 to 14, 15 to 14, 16 to 16, 17 to 16, 18 to 16, 19 to 16, 20 to 16, 21 to 16, 22 to 16)
            .forEach { (tablet, phone) -> assertEquals("Muted $tablet", phone, phoneMutedSize(tablet)) }
    }
}
