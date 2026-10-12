package com.nbaltas.nickos.node

import com.nbaltas.nickos.master.NodeStatus
import com.nbaltas.nickos.ui.master.tabletCompact
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The Master must be told which controls a Node can really carry out, instead of silently doing nothing. */
@RunWith(RobolectricTestRunner::class)
class ControlAccessTest {
    @Test fun brightnessNeedsDeviceOwnerOnNineOrWriteSettings() {
        assertFalse("plain install, nothing granted", ControlAccess.brightness(deviceOwner = false, sdk = 31, canWriteSettings = false))
        assertTrue("write settings granted", ControlAccess.brightness(deviceOwner = false, sdk = 24, canWriteSettings = true))
        assertTrue("device owner on 9+", ControlAccess.brightness(deviceOwner = true, sdk = 28, canWriteSettings = false))
        assertFalse("device owner before 9 cannot write it", ControlAccess.brightness(deviceOwner = true, sdk = 24, canWriteSettings = false))
    }

    @Test fun screenOffNeedsAnActiveAdmin() {
        assertFalse(ControlAccess.screenOff(false))
        assertTrue(ControlAccess.screenOff(true))
    }

    @Test fun reasonsNameTheTabletAndTheMenuRow() {
        assertTrue(ControlAccess.brightnessReason("Hailey's Tablet").contains("Hailey's Tablet"))
        assertTrue(ControlAccess.brightnessReason("x").contains("Allow brightness"))
        assertTrue(ControlAccess.screenOffReason("x").contains("Allow screen off"))
    }

    private fun json(extra: String) = JSONObject("{\"battery\":50,\"charging\":false,\"screenOn\":true,\"volume\":10,\"brightness\":40$extra}")

    @Test fun statusCarriesTheFlags() {
        val s = NodeStatus.from(json(",\"brightnessOk\":false,\"screenOffOk\":false"))
        assertFalse(s.brightnessOk)
        assertFalse(s.screenOffOk)
    }

    @Test fun oldNodesWithoutTheFlagsAreTreatedAsAble() {
        val s = NodeStatus.from(json(""))
        assertTrue(s.brightnessOk)
        assertTrue(s.screenOffOk)
    }

    @Test fun shortTabletCardsUseTheCompactButtons() {
        assertTrue("217dp card on a 1024x768 or 960x600 tablet", tabletCompact(217.dp))
        assertFalse(tabletCompact(420.dp))
        assertEquals(true, tabletCompact(299.dp))
    }
}
