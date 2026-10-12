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
        File(moduleDir, "master/TransferController.kt"),
        File(testDir, "sync/TransferControllerTest.kt"),
        File(testDir, "sync/LocalVideosTest.kt"),
        File(testDir, "sync/ClockSyncTest.kt"),
        File(testDir, "sync/SyncMessagesTest.kt"),
        File(testDir, "sync/SyncPlanTest.kt"),
        File(testDir, "sync/DriftPolicyTest.kt"),
        File(testDir, "sync/VideoIdTest.kt"),
        File(testDir, "sync/DownloadPolicyTest.kt"),
        File(testDir, "sync/HttpRangeTest.kt"),
        File(testDir, "sync/NoDashesTest.kt"),
        File(testDir, "sync/LocalVideosDeleteTest.kt"),
        // Tablet cameras.
        File(moduleDir, "cam/CamProfile.kt"),
        File(moduleDir, "cam/ThermalPolicy.kt"),
        File(moduleDir, "cam/CamLayout.kt"),
        File(moduleDir, "cam/CamPlan.kt"),
        File(moduleDir, "cam/MjpegParser.kt"),
        File(testDir, "cam/CamLayoutTest.kt"),
        File(testDir, "cam/ThermalPolicyTest.kt"),
        File(testDir, "cam/CamPlanTest.kt"),
        File(testDir, "cam/MjpegParserTest.kt"),
        File(moduleDir, "cam/CamMessages.kt"),
        File(testDir, "cam/CamMessagesTest.kt"),
        File(moduleDir, "cam/ServiceLifecycle.kt"),
        File(moduleDir, "node/CamServer.kt"),
        File(moduleDir, "node/CamStreamer.kt"),
        File(testDir, "cam/CamServerTest.kt"),
        File(moduleDir, "master/CamController.kt"),
        File(moduleDir, "master/MjpegClient.kt"),
        File(testDir, "cam/CamControllerTest.kt"),
        File(testDir, "cam/MjpegClientTest.kt"),
        File(moduleDir, "ui/master/CamTile.kt"),
        File(moduleDir, "ui/master/Cameras.kt"),
        File(testDir, "cam/StoreCamPrefsTest.kt"),
        File(moduleDir, "ui/master/Together.kt"),
        File(moduleDir, "ui/master/TabletLibrary.kt"),
        File(moduleDir, "ui/node/TogetherPlayer.kt"),
        // UI copy (brief A).
        File(moduleDir, "ui/master/MasterApp.kt"),
        File(moduleDir, "ui/master/Control.kt"),
        File(moduleDir, "ui/master/Watch.kt"),
        File(moduleDir, "ui/theme/Components.kt"),
        File(moduleDir, "ui/node/NodeUi.kt"),
        File(moduleDir, "ui/master/Settings.kt"),
        File(moduleDir, "ui/setup/Setup.kt"),
        File("../SETUP.md"),
        // Android 7 Master.
        File(moduleDir, "node/NodeSupport.kt"),
        File(testDir, "node/NodeSupportTest.kt"),
        File("src/main/res/xml/network_security_config.xml"),
        File("src/main/res/mipmap-anydpi/ic_launcher.xml"),
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
