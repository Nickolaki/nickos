package com.nbaltas.nickos.link

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** No em dash (U+2014) or en dash (U+2013) in the security wave 1 files, source or tests. */
class DashTest {
    private val main = File("src/main/java/com/nbaltas/nickos")
    private val test = File("src/test/java/com/nbaltas/nickos/link")

    private val files = listOf(
        File(main, "link/Seal.kt"), File(main, "link/Handshake.kt"), File(main, "link/LinkFlags.kt"), File(main, "link/Wire.kt"),
        File(main, "node/NodeRuntime.kt"), File(main, "master/MasterRuntime.kt"), File(main, "ui/node/NodeApp.kt"),
        File(test, "SealTest.kt"), File(test, "HandshakeTest.kt"), File(test, "PairTest.kt"), File(test, "ConnTest.kt"),
        File(test, "DashTest.kt"), File(test, "TestNet.kt"),
    )

    @Test
    fun `no em or en dashes`() {
        val em = 0x2014.toChar()
        val en = 0x2013.toChar()
        for (f in files) {
            assertTrue("missing ${f.path}", f.exists())
            val t = f.readText()
            assertTrue("em dash in ${f.path}", em !in t)
            assertTrue("en dash in ${f.path}", en !in t)
        }
    }
}
