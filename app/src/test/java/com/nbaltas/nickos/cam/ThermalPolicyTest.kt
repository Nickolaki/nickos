package com.nbaltas.nickos.cam

import com.nbaltas.nickos.cam.ThermalPolicy.Level
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThermalPolicyTest {
    @Test
    fun `normal at thermal status 0 to 2 and 35 degrees`() {
        for (status in 0..2) assertEquals(Level.NORMAL, ThermalPolicy().update(status, 350, 0))
    }

    @Test
    fun `hot at thermal status 3`() {
        assertEquals(Level.HOT, ThermalPolicy().update(3, 350, 0))
    }

    @Test
    fun `hot at 41 degrees with thermal status 0`() {
        assertEquals(Level.HOT, ThermalPolicy().update(0, 410, 0))
        assertEquals(Level.NORMAL, ThermalPolicy().update(0, 409, 0))
    }

    @Test
    fun `too hot at thermal status 4 and at 45 degrees`() {
        assertEquals(Level.TOO_HOT, ThermalPolicy().update(4, 350, 0))
        assertEquals(Level.TOO_HOT, ThermalPolicy().update(0, 450, 0))
        assertEquals(Level.HOT, ThermalPolicy().update(0, 449, 0))
    }

    @Test
    fun `hot goes back to normal only after 60 seconds below the thresholds`() {
        val p = ThermalPolicy()
        p.update(3, 350, 0)
        assertEquals(Level.HOT, p.update(1, 380, 1_000))
        assertEquals(Level.HOT, p.update(1, 380, 60_000))
        assertEquals(Level.NORMAL, p.update(1, 380, 61_000))
    }

    @Test
    fun `a spike in the middle starts the 60 seconds again`() {
        val p = ThermalPolicy()
        p.update(3, 350, 0)
        p.update(0, 380, 1_000)
        // Not hot, but not cool enough either: 39.5 degrees.
        assertEquals(Level.HOT, p.update(0, 395, 30_000))
        assertEquals(Level.HOT, p.update(0, 380, 31_000))
        assertEquals(Level.HOT, p.update(0, 380, 90_000))
        assertEquals(Level.NORMAL, p.update(0, 380, 91_000))
    }

    @Test
    fun `thermal status 2 is not cool enough to leave hot`() {
        val p = ThermalPolicy()
        p.update(3, 350, 0)
        assertEquals(Level.HOT, p.update(2, 350, 1_000))
        assertEquals(Level.HOT, p.update(2, 350, 200_000))
    }

    @Test
    fun `too hot starts a two minute cooldown`() {
        val p = ThermalPolicy()
        assertTrue(p.canStart(0))
        p.update(4, 350, 5_000)
        assertEquals(125_000L, p.cooldownUntil)
        assertFalse(p.canStart(5_000))
        assertFalse(p.canStart(124_999))
        assertTrue(p.canStart(125_000))
    }

    @Test
    fun `too hot ends the run and a new run starts normal with the cooldown kept`() {
        val p = ThermalPolicy()
        p.update(4, 350, 0)
        // Cooling readings do not bring the same run back, and do not move the cooldown.
        assertEquals(Level.TOO_HOT, p.update(0, 300, 10_000))
        assertEquals(Level.TOO_HOT, p.update(4, 300, 20_000))
        assertEquals(120_000L, p.cooldownUntil)
        p.reset()
        assertEquals(Level.NORMAL, p.level)
        assertEquals(120_000L, p.cooldownUntil)
        assertEquals(Level.NORMAL, p.update(0, 300, 130_000))
    }

    @Test
    fun `a warm tablet gets the cool profile whatever was chosen`() {
        assertEquals(CamProfile.COOL, ThermalPolicy.profileFor(CamProfile.HIGH, Level.HOT))
        assertEquals(CamProfile.COOL, ThermalPolicy.profileFor(CamProfile.LOW, Level.HOT))
        assertEquals(CamProfile.LOW, ThermalPolicy.profileFor(CamProfile.LOW, Level.NORMAL))
        assertEquals(CamProfile.HIGH, ThermalPolicy.profileFor(CamProfile.HIGH, Level.NORMAL))
    }

    @Test
    fun `before Android 10 only the battery temperature counts`() {
        assertEquals(Level.NORMAL, ThermalPolicy().update(-1, 350, 0))
        assertEquals(Level.HOT, ThermalPolicy().update(-1, 415, 0))
        assertEquals(Level.TOO_HOT, ThermalPolicy().update(-1, 455, 0))
        val p = ThermalPolicy()
        p.update(-1, 415, 0)
        p.update(-1, 380, 1_000)
        assertEquals(Level.NORMAL, p.update(-1, 380, 61_000))
    }

    @Test
    fun `an unknown battery temperature leaves the thermal status in charge`() {
        assertEquals(Level.NORMAL, ThermalPolicy().update(0, -1, 0))
        assertEquals(Level.HOT, ThermalPolicy().update(3, -1, 0))
    }

    @Test
    fun `low battery only counts off the charger`() {
        assertTrue(ThermalPolicy.lowBattery(14, charging = false))
        assertFalse(ThermalPolicy.lowBattery(15, charging = false))
        assertFalse(ThermalPolicy.lowBattery(5, charging = true))
    }

    @Test
    fun `profiles are the ones in the brief`() {
        assertEquals(CamProfile("normal", 640, 480, 10, 55), CamProfile.NORMAL)
        assertEquals(CamProfile.LOW, CamProfile.of("low"))
        assertEquals(CamProfile.HIGH, CamProfile.of("high"))
        assertEquals(CamProfile.NORMAL, CamProfile.of("nonsense"))
        assertEquals(CamProfile.NORMAL, CamProfile.of(null))
        assertEquals(600_000L, CamPrefs().maxMs)
    }
}
