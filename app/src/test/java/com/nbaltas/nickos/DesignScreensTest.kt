package com.nbaltas.nickos

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.nbaltas.nickos.data.JellyfinAccount
import com.nbaltas.nickos.data.LinkedMaster
import com.nbaltas.nickos.data.LinkedNode
import com.nbaltas.nickos.data.ResumeCard
import com.nbaltas.nickos.data.Role
import com.nbaltas.nickos.jellyfin.JfItem
import com.nbaltas.nickos.jellyfin.JfSession
import com.nbaltas.nickos.master.ClockInfo
import com.nbaltas.nickos.master.DeviceLibrary
import com.nbaltas.nickos.master.MasterRuntime
import com.nbaltas.nickos.master.NodeStatus
import com.nbaltas.nickos.master.NodeSyncState
import com.nbaltas.nickos.master.Playback
import com.nbaltas.nickos.master.RemoteApp
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
    private fun set(m: MasterRuntime, field: String, value: Any) {
        val f = MasterRuntime::class.java.getDeclaredField(field).apply { isAccessible = true }
        (f.get(m) as MutableStateFlow<Any>).value = value
    }

    /** Same as [set] but for any object (used to seed [SyncController]/[TransferController] private state). */
    @Suppress("UNCHECKED_CAST")
    private fun set(target: Any, field: String, value: Any) {
        val f = target::class.java.getDeclaredField(field).apply { isAccessible = true }
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
