package com.nbaltas.nickos

import com.nbaltas.nickos.node.NodeSupport
import com.nbaltas.nickos.ui.setup.nodeCardEnabled
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleResetTest {
    @Test fun resetClearsWifiAndStopsMasterBeforeTheWipe() {
        val order = mutableListOf<String>()
        RoleReset.run({ order += "wifi" }, { order += "master" }, { order += "wipe" })
        assertEquals(listOf("wifi", "master", "wipe"), order)
    }

    @Test fun nodeCardIsOffBelowAndroid8() {
        assertFalse(nodeCardEnabled(24))
        assertFalse(nodeCardEnabled(25))
        assertTrue(nodeCardEnabled(26))
        assertTrue(nodeCardEnabled(34))
    }

    @Test fun refusalCopyNamesAndroid7() = assertTrue(NodeSupport.REFUSAL_COPY.contains("only be the Master"))
}
