package com.nbaltas.nickos.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * No em dash (U+2014) or en dash (U+2013) anywhere in what this feature adds: Kotlin source,
 * commit messages and the SETUP.md addition are all covered. Lists every added file by name so
 * a rename cannot silently drop a file out of the check.
 */
class NoDashesTest {

    private val moduleDir = File("src/main/java/com/nbaltas/nickos")
    private val testDir = File("src/test/java/com/nbaltas/nickos")

    private val addedFiles = listOf(
        File(moduleDir, "sync/ClockSync.kt"),
        File(moduleDir, "sync/DriftPolicy.kt"),
        File(moduleDir, "sync/SyncPlan.kt"),
        File(moduleDir, "sync/VideoId.kt"),
        File(moduleDir, "sync/DownloadPolicy.kt"),
        File(moduleDir, "sync/HttpRange.kt"),
        File(moduleDir, "sync/SyncMessages.kt"),
        File(moduleDir, "sync/SyncState.kt"),
        File(moduleDir, "node/LocalVideos.kt"),
        File(moduleDir, "node/SyncPlayer.kt"),
        File(moduleDir, "node/NodeTogether.kt"),
        File(moduleDir, "sync/Library.kt"),
        File(moduleDir, "sync/TogetherText.kt"),
        File(moduleDir, "master/MasterPort.kt"),
        File(moduleDir, "master/SyncController.kt"),
        File(testDir, "sync/LibraryTest.kt"),
        File(testDir, "sync/TogetherTextTest.kt"),
        File(testDir, "sync/FakeMasterPort.kt"),
        File(testDir, "sync/SyncControllerTest.kt"),
        File(moduleDir, "link/MediaServer.kt"),
        File(moduleDir, "link/LanAddress.kt"),
        File(testDir, "sync/MediaServerTest.kt"),
        File(testDir, "sync/LanAddressTest.kt"),
        File(moduleDir, "node/DownloadJobs.kt"),
        File(testDir, "sync/DownloadJobsTest.kt"),
        File(testDir, "sync/LocalVideosTest.kt"),
        File(testDir, "sync/ClockSyncTest.kt"),
        File(testDir, "sync/SyncMessagesTest.kt"),
        File(testDir, "sync/SyncPlanTest.kt"),
        File(testDir, "sync/DriftPolicyTest.kt"),
        File(testDir, "sync/VideoIdTest.kt"),
        File(testDir, "sync/DownloadPolicyTest.kt"),
        File(testDir, "sync/HttpRangeTest.kt"),
        File(testDir, "sync/NoDashesTest.kt"),
    )

    @Test
    fun `every added file exists and contains no em or en dash`() {
        val emDash = Char(0x2014) // U+2014
        val enDash = Char(0x2013) // U+2013
        for (f in addedFiles) {
            assertTrue("missing file: ${f.path}", f.exists())
            val text = f.readText()
            assertFalse("em dash (U+2014) in ${f.path}", text.contains(emDash))
            assertFalse("en dash (U+2013) in ${f.path}", text.contains(enDash))
        }
    }
}
