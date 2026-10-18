package com.nbaltas.nickos.ui

import com.nbaltas.nickos.ui.master.BedtimeCommands
import com.nbaltas.nickos.ui.master.bedtimeCancelBelow
import com.nbaltas.nickos.ui.master.bedtimeOneRow
import com.nbaltas.nickos.ui.master.controlHeaderOneRowAllowed
import com.nbaltas.nickos.ui.master.controlTwoPane
import com.nbaltas.nickos.ui.master.deviceCardInnerWidth
import com.nbaltas.nickos.ui.master.deviceColumns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Picking a bedtime length sends that many minutes at once; Cancel sends 0. The phone time blocks use the same calls as the old circles. */
class ControlBedtimeTest {
    @Test fun lengthsAreTheFourFamiliarOnes() {
        assertEquals(listOf(15, 30, 45, 60), BedtimeCommands.choices)
    }

    @Test fun pickingSendsThatLengthOnceAndReportsItAsChosen() {
        BedtimeCommands.choices.forEach { min ->
            val sent = mutableListOf<Int>()
            val chosen = BedtimeCommands.pick(min) { sent += it }
            assertEquals(listOf(min), sent)
            assertEquals(min, chosen)
        }
    }

    @Test fun cancelSendsZeroMinutes() {
        val sent = mutableListOf<Int>()
        BedtimeCommands.cancel { sent += it }
        assertEquals(listOf(0), sent)
    }

    /** Design §2.2: one row only at an inner width of 280 or more AND font scale under 1.5; else 2x2. */
    @Test fun bedtimeIsOneRowOnlyWhenWideEnoughAtNormalFontScale() {
        assertTrue(bedtimeOneRow(333.33f, 1f))
        assertTrue(bedtimeOneRow(280f, 1.49f))
        assertFalse(bedtimeOneRow(279.9f, 1f))
        assertFalse(bedtimeOneRow(333.33f, 1.5f))
        assertFalse(bedtimeOneRow(600f, 2f))
    }

    @Test fun activeTimerCancelGoesBelowUnder320() {
        assertTrue(bedtimeCancelBelow(319.9f))
        assertFalse(bedtimeCancelBelow(320f))
    }

    /** Design §1.4: 3 columns from I = 1080, 2 from 720, else 1; font scale 1.5 caps at 2 (1 under 960); 2.0 is one column. */
    @Test fun deviceColumnsFollowTheContentWidth() {
        assertEquals(3, deviceColumns(1152f, 1f))
        assertEquals(3, deviceColumns(1080f, 1f))
        assertEquals(2, deviceColumns(1079.9f, 1f))
        assertEquals(2, deviceColumns(960f, 1f))
        assertEquals(2, deviceColumns(720f, 1f))
        assertEquals(1, deviceColumns(719.9f, 1f))
        assertEquals(1, deviceColumns(552f, 1f))
    }

    @Test fun deviceColumnsAreCappedAtLargeFontScale() {
        assertEquals(2, deviceColumns(1152f, 1.5f))
        assertEquals(2, deviceColumns(960f, 1.5f))
        assertEquals(1, deviceColumns(959f, 1.5f))
        assertEquals(1, deviceColumns(1152f, 2f))
        assertEquals(1, deviceColumns(1152f, 2.5f))
    }

    /** At the standard 1280 grid a card is 373.33 wide and its inner width 333.33: four blocks fit one row, as rectangles. */
    @Test fun standardGridCardInnerWidthIsOneRowForBedtime() {
        val inner = deviceCardInnerWidth(1152f, 3)
        assertEquals(333.33f, inner, 0.01f)
        assertTrue(bedtimeOneRow(inner, 1f))
        // The two-column grid at 1024x768 (I = 960) is wider still; a single 552 column is the widest.
        assertTrue(deviceCardInnerWidth(960f, 2) > inner)
    }

    @Test fun headerIsOneRowOnlyFromWidth1180AtNormalFontScale() {
        assertTrue(controlHeaderOneRowAllowed(1280f, 1f))
        assertTrue(controlHeaderOneRowAllowed(1180f, 1.49f))
        assertFalse(controlHeaderOneRowAllowed(1179f, 1f))
        assertFalse(controlHeaderOneRowAllowed(1280f, 1.5f))
    }

    @Test fun nowPlayingHasTwoPanesFrom960AtNormalFontScale() {
        assertTrue(controlTwoPane(960f, 1f))
        assertFalse(controlTwoPane(959f, 1f))
        assertFalse(controlTwoPane(1152f, 1.5f))
    }
}
