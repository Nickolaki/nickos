package com.nbaltas.nickos.talk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The manifest side of push to talk, and no em or en dashes in any talk file. Read from the source tree. */
class TalkFilesTest {
    private val main = File("src/main/java/com/nbaltas/nickos")
    private val test = File("src/test/java/com/nbaltas/nickos")
    private val manifest = File("src/main/AndroidManifest.xml").readText()

    @Test fun `the microphone permission is declared once and a microphone is not required`() {
        assertEquals(1, Regex("android\\.permission\\.RECORD_AUDIO").findAll(manifest).count())
        assertTrue(Regex("<uses-feature[^>]*android.hardware.microphone[^>]*android:required=\"false\"").containsMatchIn(manifest))
    }

    @Test fun `only the parent screen asks for the microphone`() {
        val askers = main.walkTopDown().filter { it.extension == "kt" && "RECORD_AUDIO" in it.readText() }.map { it.name }.toSet()
        // The Master's runtime checks it, the Cameras screen asks for it. Nothing in setup or the kid side does.
        assertEquals(setOf("MasterRuntime.kt", "Cameras.kt"), askers)
    }

    @Test fun `no dashes in the talk files`() {
        val files = listOf(
            File(main, "talk/TalkMessages.kt"), File(main, "talk/TalkPlan.kt"), File(main, "talk/TalkReceiver.kt"),
            File(main, "master/TalkController.kt"), File(main, "node/TalkPlayback.kt"), File(main, "ui/master/TalkButton.kt"),
            File(test, "talk/TalkMessagesTest.kt"), File(test, "talk/TalkReceiverTest.kt"), File(test, "talk/TalkPlanTest.kt"),
            File(test, "talk/TalkFilesTest.kt"), File(test, "master/TalkControllerTest.kt"),
        )
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
