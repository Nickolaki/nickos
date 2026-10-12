package com.nbaltas.nickos.master

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** No em dash (U+2014) or en dash (U+2013) in the car network files, source or tests, and no passphrase in a log line. */
class CarNetNoDashesTest {
    private val main = File("src/main/java/com/nbaltas/nickos")
    private val test = File("src/test/java/com/nbaltas/nickos")

    private val files = listOf(
        File(main, "master/CarNet.kt"), File(main, "master/AndroidP2pPort.kt"), File(main, "master/MasterService.kt"),
        File(main, "master/MasterRuntime.kt"), File(main, "master/MasterPort.kt"), File(main, "master/TransferController.kt"),
        File(main, "link/LanAddress.kt"), File(main, "link/NetWatch.kt"), File(main, "MainActivity.kt"),
        File(main, "node/NodeService.kt"), File("src/main/AndroidManifest.xml"),
        File(test, "master/CarNetTest.kt"), File(test, "master/CarNetSupportTest.kt"), File(test, "master/CarNetNoDashesTest.kt"),
        File(test, "sync/FakeMasterPort.kt"), File(test, "sync/TransferControllerTest.kt"),
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

    @Test
    fun `the passphrase is never in a log call`() {
        for (f in listOf(File(main, "master/CarNet.kt"), File(main, "master/AndroidP2pPort.kt"), File(main, "master/MasterService.kt"))) {
            val bad = f.readLines().filter { "Log." in it || "log(" in it }.filter { Regex("pass|passphrase", RegexOption.IGNORE_CASE).containsMatchIn(it) }
            assertTrue("${f.name}: $bad", bad.isEmpty())
        }
    }
}
