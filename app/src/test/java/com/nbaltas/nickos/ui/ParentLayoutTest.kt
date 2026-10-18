package com.nbaltas.nickos.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.nbaltas.nickos.ui.theme.LocalParent
import com.nbaltas.nickos.ui.theme.LocalParentTablet
import com.nbaltas.nickos.ui.theme.LocalPhone
import com.nbaltas.nickos.ui.theme.ParentLayoutProvider
import com.nbaltas.nickos.ui.theme.homeCardMinHeight
import com.nbaltas.nickos.ui.theme.homeColumns
import com.nbaltas.nickos.ui.theme.isPhoneWidth
import com.nbaltas.nickos.ui.theme.strictSizes
import com.nbaltas.nickos.ui.theme.tabletBodySize
import com.nbaltas.nickos.ui.theme.tabletContentWidth
import com.nbaltas.nickos.ui.theme.tabletDispSize
import com.nbaltas.nickos.ui.theme.tabletGutter
import com.nbaltas.nickos.ui.theme.tabletMutedSize
import com.nbaltas.nickos.ui.theme.tabletTitleSize
import com.nbaltas.nickos.ui.theme.phoneMutedSize
import com.nbaltas.nickos.ui.theme.phoneTitleSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
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

    private fun localsAt(widthDp: Int?): Triple<Boolean, Boolean, Boolean> {
        var out: Triple<Boolean, Boolean, Boolean>? = null
        rule.setContent {
            @androidx.compose.runtime.Composable fun read() { out = Triple(LocalParent.current, LocalPhone.current, LocalParentTablet.current) }
            if (widthDp == null) read()
            else Box(Modifier.width(widthDp.dp).fillMaxHeight()) { ParentLayoutProvider { read() } }
        }
        rule.waitForIdle()
        return out!!
    }

    @Test fun parentTabletAt600() = assertEquals(Triple(true, false, true), localsAt(600))
    @Test fun notParentTabletAt599() = assertEquals(Triple(true, true, false), localsAt(599))
    @Test fun kidOutsideTheProviderIsNeitherParentNorTablet() = assertEquals(Triple(false, false, false), localsAt(null))

    @Test fun gutterIs24Under840AndThen32() {
        assertEquals(24f, tabletGutter(600f), 0f)
        assertEquals(24f, tabletGutter(839f), 0f)
        assertEquals(32f, tabletGutter(840f), 0f)
    }

    @Test fun contentWidthIsCappedAt1152() {
        assertEquals(552f, tabletContentWidth(600f), 0f)
        assertEquals(896f, tabletContentWidth(960f), 0f)
        assertEquals(960f, tabletContentWidth(1024f), 0f)
        assertEquals(1152f, tabletContentWidth(1280f), 0f)
        assertEquals(1152f, tabletContentWidth(2000f), 0f)
    }

    @Test fun homeColumnsFollowTheWidthAndFontRules() {
        assertEquals(1, homeColumns(552f, 1f))
        assertEquals(1, homeColumns(799f, 1f))
        assertEquals(2, homeColumns(800f, 1f))
        assertEquals(2, homeColumns(1152f, 1f))
        assertEquals(1, homeColumns(960f, 1.5f))
        assertEquals(2, homeColumns(1000f, 1.5f))
        assertEquals(2, homeColumns(1152f, 1.5f))
        assertEquals(248, homeCardMinHeight(2))
        assertEquals("one column is content-driven", 0, homeCardMinHeight(1))
    }

    @Test fun tabletTitleTableMatchesTheSpec() {
        val spec = mapOf(
            18 to 18, 20 to 20, 24 to 22, 25 to 22, 26 to 20, 28 to 22, 30 to 22, 32 to 24, 34 to 24, 36 to 26, 38 to 28,
            40 to 28, 44 to 30, 46 to 30, 48 to 32, 52 to 32, 56 to 34, 64 to 36, 72 to 36,
        )
        spec.forEach { (requested, out) -> assertEquals("Title $requested", out, tabletTitleSize(requested)) }
        val was = strictSizes
        strictSizes = true
        try {
            assertThrows(IllegalStateException::class.java) { tabletTitleSize(29) }
        } finally { strictSizes = was }
    }

    @Test fun unsupportedSizeFallsBackToTheNearestLowerRowOutsideStrictMode() {
        val was = strictSizes
        strictSizes = false
        try {
            assertEquals(tabletTitleSize(28), tabletTitleSize(29))
            assertEquals(tabletTitleSize(18), tabletTitleSize(10))
        } finally { strictSizes = was }
    }

    @Test fun tabletMutedAndBodyTablesMatchTheSpec() {
        // requested -> (Muted, direct body)
        val spec = mapOf(
            10 to (12 to 12), 11 to (12 to 12), 12 to (12 to 12), 13 to (13 to 13), 14 to (14 to 14), 15 to (14 to 14),
            16 to (15 to 15), 17 to (15 to 15), 18 to (16 to 16), 19 to (16 to 16), 20 to (16 to 16), 21 to (16 to 16),
            22 to (16 to 16), 23 to (16 to 16), 24 to (18 to 18), 26 to (18 to 20), 28 to (18 to 20), 30 to (18 to 22),
            32 to (18 to 22), 34 to (18 to 24),
        )
        spec.forEach { (requested, out) ->
            assertEquals("Muted $requested", out.first, tabletMutedSize(requested))
            assertEquals("body $requested", out.second, tabletBodySize(requested))
        }
        assertThrows(IllegalStateException::class.java) { tabletMutedSize(9) }
    }

    @Test fun tabletDispTableMatchesTheSpec() {
        val spec = mapOf(
            18 to 18, 20 to 18, 22 to 20, 24 to 22, 28 to 24, 30 to 24, 34 to 28, 36 to 28, 40 to 32, 48 to 32, 56 to 40,
            72 to 48, 88 to 48, 104 to 48,
        )
        spec.forEach { (requested, out) -> assertEquals("disp $requested", out, tabletDispSize(requested)) }
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
