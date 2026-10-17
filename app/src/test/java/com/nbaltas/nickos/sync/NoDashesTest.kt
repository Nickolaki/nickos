package com.nbaltas.nickos.sync

import com.nbaltas.nickos.data.Hotspot
import com.nbaltas.nickos.link.NetMessages
import com.nbaltas.nickos.master.CarNetState
import com.nbaltas.nickos.master.NodeNet
import com.nbaltas.nickos.master.Reason
import com.nbaltas.nickos.master.Why
import com.nbaltas.nickos.ui.master.CarAction
import com.nbaltas.nickos.ui.master.CarButton
import com.nbaltas.nickos.ui.master.HOTSPOT_SAVE_NOTE
import com.nbaltas.nickos.ui.master.TipIcon
import com.nbaltas.nickos.ui.master.carNetBanner
import com.nbaltas.nickos.ui.master.carNetCopy
import com.nbaltas.nickos.ui.master.carNetDownWhy
import com.nbaltas.nickos.ui.master.carNetWhy
import com.nbaltas.nickos.ui.master.hotspotFieldsOk
import com.nbaltas.nickos.ui.master.offlineTips
import com.nbaltas.nickos.ui.master.tabletNetLine
import com.nbaltas.nickos.ui.setup.MASTER_NET_BODY
import com.nbaltas.nickos.ui.setup.MASTER_NET_TITLE
import com.nbaltas.nickos.ui.setup.masterNetMuted
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        // Tapo camera on the car network.
        File(moduleDir, "cam/RtspCam.kt"),
        File(moduleDir, "master/CamFinder.kt"),
        File(testDir, "cam/RtspCamTest.kt"),
        File(testDir, "master/CamFinderTest.kt"),
        File(testDir, "master/RtspKnockTest.kt"),
        File(testDir, "master/RtspDescribeTest.kt"),
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
        // Watch together by streaming from Jellyfin.
        File(moduleDir, "sync/JfStream.kt"),
        File(moduleDir, "sync/GroupWait.kt"),
        File(moduleDir, "sync/StreamError.kt"),
        File(moduleDir, "sync/JfTogether.kt"),
        File(testDir, "sync/StreamTest.kt"),
        File(testDir, "sync/StreamControllerTest.kt"),
        File(testDir, "sync/GroupWaitTest.kt"),
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

    private fun allCarNetStrings(): List<String> {
        val out = mutableListOf<String>()
        val hot = Hotspot("Dad's phone", "password1", direct = false)
        val states = listOf<CarNetState>(CarNetState.Starting, CarNetState.On("DIRECT-NK-NickOS-a1b2", "192.168.49.1")) +
            Why.values().map { CarNetState.Off(it) }
        for (s in states) for (sdk in listOf(28, 29, 34)) {
            val c = carNetCopy(s, hot, sdk)
            out += listOfNotNull(c.title, c.line, c.primary?.label, c.ghost?.label)
        }
        out += HOTSPOT_SAVE_NOTE
        for (w in Why.values()) { out += carNetWhy(w); out += carNetBanner(w); out += offlineTips(Reason.CarNetDown(w), null).map { it.second } }
        for (r in listOf(Reason.MasterNotOnWifi, Reason.NoHotspotDetails, Reason.Looking(60_000), null)) for (ssid in listOf(null, "Dad's phone")) {
            out += offlineTips(r, ssid).map { it.second }
        }
        val statuses = listOf(NetMessages.WifiStatus(), NetMessages.WifiStatus(saved = true, doze = true), NetMessages.WifiStatus(saved = true, approve = true))
        for (st in statuses) for (online in listOf(true, false)) for (h in listOf(null, hot, Hotspot("DIRECT-NK-NickOS-a1b2", "x", true))) {
            out += tabletNetLine(online, NodeNet(true, st), true, h)
            out += tabletNetLine(online, null, null, h)
        }
        for (sdk in listOf(28, 29, 31, 33)) out += masterNetMuted(sdk)
        out += listOf(MASTER_NET_TITLE, MASTER_NET_BODY)
        return out
    }

    @Test
    fun `car network words contain no em or en dash`() {
        val all = allCarNetStrings()
        assertTrue(all.size > 40)
        for (t in all) {
            assertFalse("em dash in: $t", t.contains(Char(0x2014)))
            assertFalse("en dash in: $t", t.contains(Char(0x2013)))
        }
    }

    @Test
    fun `car network card words by state`() {
        val hot = Hotspot("Dad's phone", "password1", direct = false)
        val on = carNetCopy(CarNetState.On("DIRECT-NK-NickOS-a1b2", "192.168.49.1"), null, 34)
        assertEquals("DIRECT-NK-NickOS-a1b2", on.title)
        assertEquals("On. The tablets join it on their own. There is nothing to switch on in the car.", on.line)
        assertEquals("Use a different hotspot", on.ghost?.label)
        assertNull(on.primary); assertFalse(on.fields)

        val starting = carNetCopy(CarNetState.Starting, null, 34)
        assertEquals("Starting the car network", starting.title)
        assertNull(starting.line); assertNull(starting.primary); assertNull(starting.ghost)

        val perm = carNetCopy(CarNetState.Off(Why.NO_PERMISSION), null, 34)
        assertEquals("Car network is off", perm.title)
        assertEquals("NickOS needs permission to run it from this tablet. It never uses your location.", perm.line)
        assertEquals(CarButton("Allow", CarAction.ALLOW), perm.primary)

        val loc = carNetCopy(CarNetState.Off(Why.NO_LOCATION), null, 30)
        assertEquals("Android needs Location switched on for this tablet to run the network. NickOS does not read where you are.", loc.line)
        assertEquals(CarButton("Open location settings", CarAction.OPEN_LOCATION), loc.primary)

        val wifi = carNetCopy(CarNetState.Off(Why.WIFI_OFF), null, 34)
        assertEquals("Car network is off", wifi.title)
        assertEquals("WiFi is switched off on this tablet. Turn it on. It does not need to join anything.", wifi.line)
        assertNull(wifi.primary); assertNull(wifi.ghost)

        val tether = carNetCopy(CarNetState.Off(Why.TETHERING_ON), null, 34)
        assertEquals("Car network is paused", tether.title)
        assertEquals("This tablet's own hotspot is on. The car network comes back when the hotspot is off.", tether.line)

        for (w in listOf(Why.FAILED, Why.UNSUPPORTED)) {
            val c = carNetCopy(CarNetState.Off(w), null, 34)
            assertEquals("Car network can't run on this tablet", c.title)
            assertEquals("Use a phone or tablet hotspot instead. Enter its name and password here and the tablets will join that.", c.line)
            assertTrue(c.fields)
        }

        val man = carNetCopy(CarNetState.Off(Why.MANUAL), hot, 34)
        assertEquals("Dad's phone", man.title)
        assertEquals("The tablets join this hotspot when it is on. Switch it on each trip.", man.line)
        assertEquals(CarButton("Use the car network instead", CarAction.USE_CAR_NET), man.ghost)
        assertNull(carNetCopy(CarNetState.Off(Why.MANUAL), hot, 28).ghost)
    }

    @Test
    fun `tablet line banner and tips`() {
        val direct = Hotspot("DIRECT-NK-NickOS-a1b2", "x", true)
        val manual = Hotspot("Dad's phone", "password1", false)
        fun net(saved: Boolean, approve: Boolean = false, dev: Boolean = false, doze: Boolean = true) = NodeNet(true, NetMessages.WifiStatus(saved, approve, dev, doze))
        assertEquals("Car network saved", tabletNetLine(true, net(true), null, direct))
        assertEquals("Hotspot saved", tabletNetLine(true, net(true), null, manual))
        assertEquals("Needs one tap on the tablet: Allow in the NickOS notification", tabletNetLine(true, net(true, approve = true), null, direct))
        assertEquals("Car network saved", tabletNetLine(true, net(true, approve = true, dev = true), null, direct))
        assertEquals("Hasn't got the car network yet. Link it on the home WiFi once.", tabletNetLine(true, net(false), null, direct))
        assertEquals("Nothing to send yet.", tabletNetLine(true, net(false), null, null))
        assertEquals("Car network saved · Battery saver can delay rejoining", tabletNetLine(true, net(true, doze = false), null, direct))
        // Offline: the last live report is ignored and the persisted flag decides, with no battery note.
        assertEquals("Car network saved", tabletNetLine(false, net(false, approve = true, doze = false), true, direct))
        assertEquals("Hasn't got the car network yet. Link it on the home WiFi once.", tabletNetLine(false, null, null, direct))

        assertEquals("Car network is off: WiFi is off on this tablet. Tap to fix.", carNetBanner(Why.WIFI_OFF))
        assertEquals(Why.NO_LOCATION, carNetDownWhy(listOf(Reason.Looking(null), Reason.CarNetDown(Why.NO_LOCATION))))
        assertNull(carNetDownWhy(listOf(Reason.Looking(null), Reason.NoHotspotDetails)))
        assertEquals(listOf(TipIcon.WIFI_OFF to "The car network is off: it needs permission. Fix it in Settings, Tablets."), offlineTips(Reason.CarNetDown(Why.NO_PERMISSION), null))
        assertEquals(listOf(TipIcon.WIFI to "Switch on the hotspot Dad's phone and it will join."), offlineTips(Reason.MasterNotOnWifi, "Dad's phone"))
        assertEquals(listOf(TipIcon.WIFI to "It hasn't got the car network yet. Link it on the home WiFi once."), offlineTips(Reason.NoHotspotDetails, null))
        assertEquals(
            listOf(TipIcon.POWER to "Make sure it's switched on and charged.", TipIcon.WIFI to "It joins the car network on its own when it's in range."),
            offlineTips(Reason.Looking(null), null),
        )
        assertEquals("It joins Dad's phone on its own once the hotspot is on.", offlineTips(Reason.Looking(null), "Dad's phone")[1].second)
        assertEquals("Android asks for one permission to do this. NickOS never uses your location. Location also has to be switched on.", masterNetMuted(31))
        assertEquals("Android asks for one permission to do this. NickOS never uses your location.", masterNetMuted(34))
        assertTrue(hotspotFieldsOk("Dad's phone", "")); assertFalse(hotspotFieldsOk("", "")); assertFalse(hotspotFieldsOk("x", "short"))
    }
}
