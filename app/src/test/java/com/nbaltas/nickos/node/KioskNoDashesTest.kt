package com.nbaltas.nickos.node

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** No em dash (U+2014) or en dash (U+2013) in the kiosk, QR and PIN files, the tests for them, or SETUP.md. */
class KioskNoDashesTest {
    private val main = File("src/main/java/com/nbaltas/nickos")
    private val test = File("src/test/java/com/nbaltas/nickos")

    private val files = listOf(
        File(main, "node/KioskPolicy.kt"), File(main, "node/PinPolicy.kt"), File(main, "node/NodeDevice.kt"),
        File(main, "node/NodeRuntime.kt"), File(main, "node/Provisioning.kt"),
        File(main, "ui/node/KioskSetup.kt"), File(main, "ui/node/NodeApp.kt"), File(main, "ui/node/NodeUi.kt"),
        File(main, "ui/master/QrSetup.kt"), File(main, "ui/master/Settings.kt"), File(main, "update/Updater.kt"),
        File("../SETUP.md"),
        File(test, "node/KioskPolicyTest.kt"), File(test, "node/KioskWatchTest.kt"), File(test, "node/PinGateTest.kt"),
        File(test, "node/KioskNoDashesTest.kt"), File(test, "node/ProvisioningTest.kt"),
        File(test, "KioskScreensTest.kt"), File(test, "QrScreensTest.kt"),
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
