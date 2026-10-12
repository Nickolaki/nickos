package com.nbaltas.nickos

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.SystemClock
import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.nbaltas.nickos.cam.NodeCam
import com.nbaltas.nickos.data.Camera
import com.nbaltas.nickos.data.JellyfinAccount
import com.nbaltas.nickos.data.LinkedMaster
import com.nbaltas.nickos.data.LinkedNode
import com.nbaltas.nickos.data.ResumeCard
import com.nbaltas.nickos.data.Role
import com.nbaltas.nickos.jellyfin.JfItem
import com.nbaltas.nickos.jellyfin.JfSession
import com.nbaltas.nickos.master.CamController
import com.nbaltas.nickos.master.ClockInfo
import com.nbaltas.nickos.master.DeviceLibrary
import com.nbaltas.nickos.master.MasterRuntime
import com.nbaltas.nickos.master.MjpegClient
import com.nbaltas.nickos.master.NodeStatus
import com.nbaltas.nickos.master.NodeSyncState
import com.nbaltas.nickos.master.Playback
import com.nbaltas.nickos.master.RemoteApp
import com.nbaltas.nickos.ui.master.CamerasScreen
import com.nbaltas.nickos.node.PlayerUi
import com.nbaltas.nickos.sync.Library
import com.nbaltas.nickos.sync.SyncMessages
import com.nbaltas.nickos.sync.SyncQuality
import com.nbaltas.nickos.sync.SyncSession
import com.nbaltas.nickos.sync.SyncState
import com.nbaltas.nickos.ui.master.ControlScreen
import com.nbaltas.nickos.ui.master.MasterHome
import com.nbaltas.nickos.ui.master.PickPanel
import com.nbaltas.nickos.ui.master.SettingsScreen
import com.nbaltas.nickos.ui.master.TAB_APPS
import com.nbaltas.nickos.ui.master.TAB_DEVICE
import com.nbaltas.nickos.ui.master.TAB_NOW
import com.nbaltas.nickos.ui.master.TAB_WATCH
import com.nbaltas.nickos.ui.master.TogetherScreen
import com.nbaltas.nickos.ui.master.rememberNodeViews
import com.nbaltas.nickos.ui.node.BedtimeToast
import com.nbaltas.nickos.ui.node.HomeApp
import com.nbaltas.nickos.ui.node.LockedScreen
import com.nbaltas.nickos.ui.node.NodeHome
import com.nbaltas.nickos.ui.node.PinPad
import com.nbaltas.nickos.ui.node.TogetherScreen as NodeTogetherScreen
import com.nbaltas.nickos.ui.setup.AddTabletScreen
import com.nbaltas.nickos.ui.setup.NodeNameScreen
import com.nbaltas.nickos.ui.setup.RoleScreen
import com.nbaltas.nickos.ui.setup.Welcome
import com.nbaltas.nickos.ui.theme.C
import com.nbaltas.nickos.ui.theme.NickTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders NickOS screens with the design's sample data at 1280×800 so they can be compared
 * side by side with the same-named PNG in design/screens. Run: ./gradlew :app:recordRoborazziDebug
 * Output goes to app/build/design-check.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-mdpi")
class DesignScreensTest {
    private val app get() = ApplicationProvider.getApplicationContext<App>()

    private fun shot(name: String, content: @Composable () -> Unit) =
        captureRoboImage("build/design-check/$name.png") {
            NickTheme { Box(Modifier.fillMaxSize().background(C.bg)) { content() } }
        }

    @Before
    fun seed() {
        val s = app.store
        s.resetAll()
        s.setName("Dad's Tablet")
        s.upsertNode(LinkedNode("aaaaaaaaaaaa", "Hailey's Tablet", 0, "initial", "t", lastSeen = System.currentTimeMillis()))
        s.upsertNode(LinkedNode("bbbbbbbbbbbb", "Leo's Tablet", 3, "initial", "t", lastSeen = System.currentTimeMillis()))
        s.upsertNode(LinkedNode("cccccccccccc", "Mia's Tablet", 5, "initial", "t", lastSeen = System.currentTimeMillis() - 12 * 60_000, lastBattery = 23))
        val m = app.master
        set(m, "_online", setOf("aaaaaaaaaaaa", "bbbbbbbbbbbb"))
        set(m, "_status", mapOf(
            "aaaaaaaaaaaa" to status(82, fg = "org.jellyfin.mobile", fgLabel = "Jellyfin"),
            "bbbbbbbbbbbb" to status(41, fg = "com.example.drawing", fgLabel = "Drawing"),
        ))
        set(m, "_apps", mapOf("aaaaaaaaaaaa" to listOf("Jellyfin", "Drawing", "Reader", "Puzzles", "Music", "Maths Quest", "Spelling", "Camera", "Gallery", "Browser", "Calculator", "Clock", "App store", "Settings")
            .map { RemoteApp(if (it == "Jellyfin") "org.jellyfin.mobile" else "com.example.${it.lowercase().replace(" ", "")}", it, null) }))
        val ep = JfItem(
            "ep5", "The Lighthouse", "Episode", "series1", "Star Harbour", "s2", 5, 2,
            runTicks = (24 * 60 + 10) * JfItem.TICKS_PER_SEC, posTicks = (18 * 60 + 2) * JfItem.TICKS_PER_SEC, played = false,
            overview = null, year = null, rating = "G", childCount = null, hasPrimary = false, hasThumb = false, hasBackdrop = false,
            parentThumbId = null, parentBackdropId = null,
        )
        val sess = JfSession("sess", null, "Hailey's Tablet", "Jellyfin Android", true, ep, ep.posTicks, false, "")
        set(m, "_playback", mapOf("aaaaaaaaaaaa" to Playback(sess, System.currentTimeMillis())))
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.READ_MEDIA_VIDEO)
    }

    private fun status(b: Int, fg: String?, fgLabel: String?, caps: List<String> = listOf("sync1")) = NodeStatus(
        battery = b, charging = false, screenOn = true, volume = 60, brightness = 75, fg = fg, fgLabel = fgLabel,
        allowed = listOf("org.jellyfin.mobile", "com.example.drawing", "com.example.reader", "com.example.puzzles", "com.example.music"),
        lockedApp = null, lockedBy = null, bedtimeAt = 0, owner = true, usage = true, overlay = true,
        jf = "org.jellyfin.mobile", names = listOf("Hailey's Tablet"), version = BuildConfig.VERSION_NAME, caps = caps,
    )

    @Suppress("UNCHECKED_CAST")
    private fun set(target: Any, field: String, value: Any) {
        val f = target.javaClass.getDeclaredField(field).apply { isAccessible = true }
        (f.get(target) as MutableStateFlow<Any>).value = value
    }

    // ---- setup ----
    @Test fun setupWelcome() = shot("Setup-Welcome") { Welcome {} }
    @Test fun setupRole() = shot("Setup-Role") { RoleScreen({}, {}, {}) }
    @Test fun nodeName() {
        app.store.setName("Hailey's Tablet")
        shot("Node-Name") { NodeNameScreen({}) {} }
    }
    @Test fun masterAddCode() = shot("Master-AddCode") { AddTabletScreen({}, "Master · Add tablet") {} }

    // ---- master ----
    @Test fun masterHome() = shot("Master-Home") { MasterHome({}, {}, {}, {}, {}, {}, {}) }
    @Test fun masterHomeEmpty() {
        listOf("aaaaaaaaaaaa", "bbbbbbbbbbbb", "cccccccccccc").forEach { app.store.removeNode(it) }
        shot("Master-Home-Empty") { MasterHome({}, {}, {}, {}, {}, {}, {}) }
    }
    @Config(qualifiers = "w800dp-h1280dp-port-mdpi")
    @Test fun masterHomePortrait() = shot("Master-Home-Portrait") { MasterHome({}, {}, {}, {}, {}, {}, {}) }
    @Test fun controlNowPlaying() = shot("Control-NowPlaying") { ControlScreen("aaaaaaaaaaaa", TAB_NOW, {}, {}, {}) }
    @Test fun controlNothingPlaying() = shot("Control-NothingPlaying") { ControlScreen("bbbbbbbbbbbb", TAB_NOW, {}, {}, {}) }
    @Test fun controlOffline() = shot("Control-Offline") { ControlScreen("cccccccccccc", TAB_NOW, {}, {}, {}) }
    @Test fun controlApps() = shot("Control-Apps") { ControlScreen("aaaaaaaaaaaa", TAB_APPS, {}, {}, {}) }
    @Test fun controlDevice() = shot("Control-Device") { ControlScreen("aaaaaaaaaaaa", TAB_DEVICE, {}, {}, {}) }
    @Test fun settingsTablets() = shot("Settings-Tablets") { SettingsScreen(0, {}, {}, {}) }
    @Test fun settingsJellyfin() = shot("Settings-Jellyfin") { SettingsScreen(1, {}, {}, {}) }
    @Test fun settingsPin() = shot("Settings-PIN") { SettingsScreen(2, {}, {}, {}) }
    @Test fun settingsTheme() = shot("Settings-Theme") { SettingsScreen(3, {}, {}, {}) }
    @Test fun settingsAbout() = shot("Settings-About") { SettingsScreen(4, {}, {}, {}) }
    @Test fun settingsCameras() = shot("Settings-Cameras") { SettingsScreen(5, {}, {}, {}) }

    // ---- cameras (section 11 of the tablet-cameras brief) ----
    private val CH = "aaaaaaaaaaaa"
    private val CL = "bbbbbbbbbbbb"
    private val CM = "cccccccccccc"
    private val CZ = "dddddddddddd"

    private fun seedCams() {
        val s = app.store
        s.resetAll()
        s.setName("Dad's Tablet")
        s.upsertNode(LinkedNode(CH, "Hailey's Tablet", 0, "initial", "t"))
        s.upsertNode(LinkedNode(CL, "Leo's Tablet", 3, "initial", "t"))
        s.upsertNode(LinkedNode(CM, "Mia's Tablet", 5, "initial", "t"))
        set(app.master, "_online", setOf(CH, CL, CM))
        set(app.master, "_status", mapOf(CH to camStatus(true), CL to camStatus(true), CM to camStatus(true)))
    }

    private fun camStatus(cam: Boolean) = NodeStatus(
        battery = 80, charging = false, screenOn = true, volume = 60, brightness = 75, fg = null, fgLabel = null,
        allowed = emptyList(), lockedApp = null, lockedBy = null, bedtimeAt = 0, owner = true, usage = true, overlay = true,
        jf = null, names = emptyList(), version = BuildConfig.VERSION_NAME, caps = if (cam) listOf("sync1", "cam1") else listOf("sync1"),
    )

    private val camController: CamController get() = app.master.cam

    private fun cams(armed: Set<String>, states: Map<String, NodeCam>) {
        set(camController, "_armed", armed)
        set(camController, "_states", states)
    }

    /** A synthetic 640x480 picture, a different gradient per tablet, so a live tile shows something. */
    private fun camPicture(id: String, from: Int, to: Int) {
        val bmp = Bitmap.createBitmap(640, 480, Bitmap.Config.RGB_565)
        Canvas(bmp).drawRect(0f, 0f, 640f, 480f, Paint().apply { shader = LinearGradient(0f, 0f, 640f, 480f, from, to, Shader.TileMode.CLAMP) })
        camController.frameFlow(id).value = MjpegClient.Frame(bmp, 0, SystemClock.elapsedRealtime())
    }

    private fun camNow() = SystemClock.elapsedRealtime()
    private fun camLive(level: String = NodeCam.LEVEL_NORMAL) = NodeCam(NodeCam.LIVE, level = level, port = 47472, key = "k", maxMs = 600_000, receivedAt = camNow())
    private fun camOff(reason: String) = NodeCam(NodeCam.OFF, reason = reason, maxMs = 600_000, receivedAt = camNow())
    private fun camRtsp(id: String, name: String) = app.store.saveCamera(Camera(id, name, "rtsp://example.invalid/stream"))

    private val camBlue = 0xFF2B5B8A.toInt() to 0xFF9AD0E8.toInt()
    private val camGreen = 0xFF2E6B4F.toInt() to 0xFFB9E6A8.toInt()
    private val camPlum = 0xFF5B3A78.toInt() to 0xFFE8B4D8.toInt()

    @Test fun camerasSingle() {
        seedCams()
        cams(setOf(CH), mapOf(CH to camLive()))
        camPicture(CH, camBlue.first, camBlue.second)
        shot("Cameras-Single") { CamerasScreen({}, {}, live = false) }
    }

    @Test fun camerasPair() {
        seedCams()
        cams(setOf(CH), mapOf(CH to camLive()))
        camPicture(CH, camBlue.first, camBlue.second)
        camRtsp("front", "Front door")
        shot("Cameras-Pair") { CamerasScreen({}, {}, live = false) }
    }

    @Test fun camerasGrid() {
        seedCams()
        cams(setOf(CH, CL, CM), mapOf(CH to camLive(), CL to camLive(NodeCam.LEVEL_HOT), CM to NodeCam(NodeCam.WARMING, port = 47472, key = "k", receivedAt = camNow())))
        camPicture(CH, camBlue.first, camBlue.second)
        camPicture(CL, camGreen.first, camGreen.second)
        camRtsp("front", "Front door")
        shot("Cameras-Grid") { CamerasScreen({}, {}, live = false) }
    }

    @Test fun camerasSpotlight() {
        seedCams()
        cams(setOf(CH, CL, CM), mapOf(CH to camLive(), CL to camLive(), CM to camLive()))
        camPicture(CH, camBlue.first, camBlue.second)
        camPicture(CL, camGreen.first, camGreen.second)
        camPicture(CM, camPlum.first, camPlum.second)
        camRtsp("front", "Front door")
        camRtsp("yard", "Back yard")
        // Five sources is a spotlight by itself; startSpotlight puts Leo in it, as a tap on his tile would.
        shot("Cameras-Spotlight") { CamerasScreen({}, {}, live = false, startSpotlight = "t:$CL") }
    }

    @Test fun camerasStates() {
        seedCams()
        app.store.upsertNode(LinkedNode(CZ, "Zoe's Tablet", 2, "initial", "t"))
        set(app.master, "_online", setOf(CH, CL, CM, CZ))
        set(app.master, "_status", mapOf(CH to camStatus(true), CL to camStatus(true), CM to camStatus(true), CZ to camStatus(false)))
        cams(
            setOf(CH, CZ),
            mapOf(
                CH to camOff(NodeCam.PERMISSION),
                CL to NodeCam(NodeCam.OFF, reason = NodeCam.TOO_HOT, level = NodeCam.LEVEL_TOO_HOT, retryAt = 103_000, now = 0, receivedAt = camNow()),
                CM to camOff(NodeCam.AUTO_OFF),
            ),
        )
        shot("Cameras-States") { CamerasScreen({}, {}, live = false) }
    }

    @Config(qualifiers = "w400dp-h800dp-port-mdpi")
    @Test fun camerasPhone() {
        seedCams()
        cams(setOf(CH, CL, CM), mapOf(CH to camLive(), CL to camLive(NodeCam.LEVEL_HOT), CM to camOff(NodeCam.PERMISSION)))
        camPicture(CH, camBlue.first, camBlue.second)
        camPicture(CL, camGreen.first, camGreen.second)
        shot("Cameras-Phone") { CamerasScreen({}, {}, live = false) }
    }

    @Test fun camerasNothingOn() {
        seedCams()
        cams(emptySet(), emptyMap())
        shot("Cameras-Nothing-On") { CamerasScreen({}, {}, live = false) }
    }

    @Test fun camerasEmpty() {
        seedCams()
        listOf(CH, CL, CM).forEach { app.store.removeNode(it) }
        shot("Cameras-Empty") { CamerasScreen({}, {}, live = false) }
    }

    // ---- watch together ----
    private fun video(fid: String, title: String) = SyncMessages.VideoEntry(fid, title, "$fid.mp4", 400_000_000L, 90 * 60_000L)

    /** Lets a seeded tablet's status differ from the default (e.g. offline-but-capable Mia, so she shows "Offline" and not "Update needed"). */
    private fun addStatus(id: String, b: Int) {
        val m = app.master
        val f = MasterRuntime::class.java.getDeclaredField("_status").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST") val flow = f.get(m) as MutableStateFlow<Map<String, NodeStatus>>
        flow.value = flow.value + (id to status(b, fg = null, fgLabel = null))
    }

    @Test fun togetherLibrary() {
        val m = app.master
        addStatus("cccccccccccc", 23)
        set(m.sync, "_videos", mapOf(
            Library.SELF to DeviceLibrary(true, listOf(video("sh", "Star Harbour"), video("cc", "Captain Comet"))),
            "aaaaaaaaaaaa" to DeviceLibrary(true, listOf(video("sh", "Star Harbour"), video("cc", "Captain Comet"))),
            "bbbbbbbbbbbb" to DeviceLibrary(true, listOf(video("sh", "Star Harbour"), video("mp", "Moss and Pebble"))),
        ))
        set(m.transfer, "_jobs", mapOf(
            "bbbbbbbbbbbb" to listOf(SyncMessages.JobEntry("cc", "Captain Comet", "running", 420_000_000L, 1_000_000_000L)),
        ))
        shot("Together-Library") { TogetherScreen({}, {}) }
    }

    @Test fun togetherShows() {
        val m = app.master
        val starHarbour = (1..6).map { video("sh$it", "Star Harbour S01E0$it The One With Episode $it") }
        val captainComet = (1..2).map { video("cc$it", "Captain Comet S01E0$it Part $it") }
        set(m.sync, "_videos", mapOf(Library.SELF to DeviceLibrary(true, starHarbour + captainComet)))
        shot("Together-Shows") { TogetherScreen({}, {}, initialSegment = 1) }
    }

    @Test fun togetherPick() {
        val m = app.master
        addStatus("cccccccccccc", 23)
        set(m.sync, "_videos", mapOf(
            Library.SELF to DeviceLibrary(true, listOf(video("cc", "Captain Comet"))),
            "aaaaaaaaaaaa" to DeviceLibrary(true, listOf(video("cc", "Captain Comet"))),
        ))
        set(m.transfer, "_jobs", mapOf(
            "bbbbbbbbbbbb" to listOf(SyncMessages.JobEntry("cc", "Captain Comet", "running", 420_000_000L, 1_000_000_000L)),
        ))
        val item = Library.merge(m.sync.videos.value.mapValues { it.value.list }).first { it.fid == "cc" }
        shot("Together-Pick") {
            val nodes = rememberNodeViews()
            PickPanel(item, thumb = null, nodes = nodes, sync = m.sync, transfer = m.transfer, backLabel = "Movies", onBack = {})
        }
    }

    @Test fun togetherLibraryEmpty() = shot("Together-Library-Empty") { TogetherScreen({}, {}) }

    @Test fun togetherRemote() {
        val m = app.master
        val now = android.os.SystemClock.elapsedRealtime()
        set(m.sync, "_videos", mapOf(Library.SELF to DeviceLibrary(true, listOf(video("sh", "Star Harbour")))))
        set(m.sync, "_session", SyncSession("sh", "Star Harbour", 92 * 60_000L, setOf("aaaaaaaaaaaa", "bbbbbbbbbbbb"), 92 * 60_000L - 10 * 60_000L, now, true))
        set(m.sync, "_states", mapOf(
            "aaaaaaaaaaaa" to NodeSyncState(SyncState.PLAYING, 82 * 60_000L, now, 92 * 60_000L, 1f, null, now),
            "bbbbbbbbbbbb" to NodeSyncState(SyncState.BUFFERING, 82 * 60_000L, now, 92 * 60_000L, 1f, null, now),
        ))
        set(m.sync, "_offsets", mapOf(
            "aaaaaaaaaaaa" to ClockInfo(0L, 20L, SyncQuality.GOOD),
            "bbbbbbbbbbbb" to ClockInfo(0L, 20L, SyncQuality.GOOD),
        ))
        shot("Together-Remote") { TogetherScreen({}, {}) }
    }

    @Test fun controlWatchLibrary() {
        val m = app.master
        app.store.setJellyfin(JellyfinAccount("https://jf.local", "dad", "tok", "u1", true))
        set(m.sync, "_videos", mapOf("aaaaaaaaaaaa" to DeviceLibrary(true, listOf(video("sh", "Star Harbour")))))
        shot("Control-Watch-Library") { ControlScreen("aaaaaaaaaaaa", TAB_WATCH, {}, {}, {}) }
    }

    @Test fun nodeTogether() = shot("Node-Together") {
        NodeTogetherScreen(ui = PlayerUi(fid = "sh", title = "Star Harbour", state = SyncState.PAUSED), player = null, parent = "Dad", onHold = {})
    }

    // ---- node ----
    private fun nodeSeed() {
        val s = app.store
        s.setRole(Role.NODE); s.setName("Hailey's Tablet"); s.setColor(0)
        s.addMaster(LinkedMaster("m", "Dad's Tablet", "t"))
        s.setResume(listOf(ResumeCard("1", "Star Harbour", null), ResumeCard("2", "Captain Comet", null), ResumeCard("3", "Moss & Pebble", null)))
    }
    private val kidApps = listOf("Drawing", "Reader", "Puzzles", "Music").map { HomeApp("com.example.$it", it, null) }

    @Composable private fun kidHome() = NodeHome(app.store, kidApps, HomeApp("org.jellyfin.mobile", "Jellyfin", null), "Dad's Tablet", true, {}, {}, {})

    @Test fun nodeHome() { nodeSeed(); shot("Node-Home") { kidHome() } }
    @Config(qualifiers = "w800dp-h1280dp-port-mdpi")
    @Test fun nodeHomePortrait() { nodeSeed(); shot("Node-Home-Portrait") { kidHome() } }
    @Test fun nodePin() { nodeSeed(); shot("Node-PIN") { kidHome(); PinPad("1234", {}, {}) } }
    @Test fun nodeLocked() { nodeSeed(); shot("Node-Locked") { LockedScreen(app.store, "Dad's Tablet") } }
    @Test fun nodeBedtime() { nodeSeed(); shot("Node-Bedtime") { kidHome(); BedtimeToast(300) } }
}
