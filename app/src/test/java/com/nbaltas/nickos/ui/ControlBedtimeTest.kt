package com.nbaltas.nickos.ui

import com.nbaltas.nickos.ui.master.BedtimeCommands
import org.junit.Assert.assertEquals
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
}
