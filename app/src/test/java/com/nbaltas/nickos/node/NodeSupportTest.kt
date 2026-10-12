package com.nbaltas.nickos.node

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NodeSupportTest {
    @Test
    fun `a tablet below android 8 refuses to be a node`() {
        assertFalse(NodeSupport.ok(24))
        assertFalse(NodeSupport.ok(25))
        assertTrue(NodeSupport.ok(26))
        assertTrue(NodeSupport.ok(31))
    }

    @Test
    fun `the android 7 copy has no em or en dash`() {
        val em = 0x2014.toChar()
        val en = 0x2013.toChar()
        for (s in listOf(NodeSupport.REFUSAL_COPY, NodeSupport.LEGACY_NETWORK_NOTE)) {
            assertFalse(s, em in s || en in s)
        }
        for (f in listOf("src/main/java/com/nbaltas/nickos/node/NodeSupport.kt", "src/main/res/xml/network_security_config.xml",
            "src/main/res/mipmap-anydpi/ic_launcher.xml", "src/main/java/com/nbaltas/nickos/master/MasterService.kt")) {
            val t = File(f).readText()
            assertFalse(f, em in t || en in t)
        }
    }
}
