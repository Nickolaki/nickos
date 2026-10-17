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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.Modifier
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.nbaltas.nickos.ui.node.screenNow
import java.util.Locale
import java.util.TimeZone
import org.junit.After
import com.nbaltas.nickos.cam.NodeCam
import com.nbaltas.nickos.data.Camera
import com.nbaltas.nickos.data.JellyfinAccount
import com.nbaltas.nickos.data.Hotspot
import com.nbaltas.nickos.data.LinkedMaster
import com.nbaltas.nickos.data.LinkedNode
import com.nbaltas.nickos.data.ResumeCard
import com.nbaltas.nickos.data.Role
import com.nbaltas.nickos.jellyfin.JfItem
import com.nbaltas.nickos.jellyfin.JfSession
import com.nbaltas.nickos.link.NetMessages
import com.nbaltas.nickos.master.CarNetState
import com.nbaltas.nickos.master.NodeNet
import com.nbaltas.nickos.master.Why
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
import com.nbaltas.nickos.ui.master.JfPickContent
import com.nbaltas.nickos.ui.master.JfPrep
import com.nbaltas.nickos.ui.master.JfRow
import com.nbaltas.nickos.ui.master.JfStreamUi
import com.nbaltas.nickos.sync.JfTogether
import com.nbaltas.nickos.ui.master.PickPanel
import com.nbaltas.nickos.ui.master.SettingsScreen
import com.nbaltas.nickos.ui.master.TAB_APPS
import com.nbaltas.nickos.ui.master.TAB_DEVICE
import com.nbaltas.nickos.ui.master.TAB_NOW
import com.nbaltas.nickos.ui.master.TAB_WATCH
import com.nbaltas.nickos.ui.master.TogetherScreen
import com.nbaltas.nickos.ui.master.TabletLibrary
import com.nbaltas.nickos.ui.master.TabletPickPanel
import com.nbaltas.nickos.ui.master.rememberNodeViews
import com.nbaltas.nickos.ui.node.BedtimeToastAtBottom
import com.nbaltas.nickos.ui.node.HomeApp
import com.nbaltas.nickos.ui.node.LockedScreen
import com.nbaltas.nickos.ui.node.NodeHome
import com.nbaltas.nickos.ui.node.PinPad
import com.nbaltas.nickos.ui.node.TogetherScreen as NodeTogetherScreen
import com.nbaltas.nickos.ui.setup.AddTabletScreen
import com.nbaltas.nickos.ui.setup.MasterNetScreen
import com.nbaltas.nickos.ui.setup.NodeNameScreen
import com.nbaltas.nickos.ui.setup.PairingCodeBody
import com.nbaltas.nickos.ui.setup.RoleScreen
import com.nbaltas.nickos.ui.setup.Welcome
import com.nbaltas.nickos.ui.theme.C
import com.nbaltas.nickos.ui.theme.NickTheme
import com.nbaltas.nickos.ui.theme.ParentLayoutProvider
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders NickOS screens with the design's sample data (1280x800, or portrait / phone where named) and
 * COMPARES each render with the same-named PNG in design/screens. A screen that no longer matches its
 * picture fails the ordinary test run (`./gradlew :app:testDebugUnitTest`).
 *
 * When a screen changed on purpose, re-record the pictures and commit them:
 *     ./gradlew :app:recordRoborazziDebug      (writes straight into design/screens)
 *
 * Everything that depends on the time of day is pinned to one instant (Mon 12 Oct 2026, 12:00, Sydney
 * time zone, English locale) so a render comes out the same every run. See [screenNow] in NodeUi.kt.
 * A render may differ from its picture by up to [CHANGE_THRESHOLD] of its pixels, which absorbs font
 * anti-aliasing differences between machines but not a moved or changed element.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-mdpi")
class DesignScreensTest {
    private val app get() = ApplicationProvider.getApplicationContext<App>()

    companion object {
        /** Mon 12 Oct 2026, 12:00 in Sydney (AEDT, UTC+11). */
        val FIXED_NOW: Long = java.time.ZonedDateTime.of(2026, 10, 12, 12, 0, 0, 0, java.time.ZoneId.of("Australia/Sydney")).toInstant().toEpochMilli()

        /** Fraction of pixels allowed to differ from the saved picture. */
        const val CHANGE_THRESHOLD = 0.0005f

        const val CAR_SSID = "DIRECT-NK-NickOS-a1b2"

        /** A Galaxy S25 held upright. */
        const val PHONE_QUALIFIERS = "w411dp-h891dp-port-xhdpi"

        /** Every parent state that also gets a phone picture (design/screens/phone). Kid (Node-*) screens are not here. */
        val PHONE_SCREENS = setOf(
            "Cameras-Empty", "Cameras-Grid", "Cameras-Nothing-On", "Cameras-Pair", "Cameras-Single", "Cameras-Spotlight",
            "Cameras-States", "Cameras-Talk-Ready", "Cameras-Talk-Talking", "Cameras-Talk-Update",
            "Control-Apps", "Control-Device-Ring", "Control-Device", "Control-Movie", "Control-NothingPlaying",
            "Control-NowPlaying-JellyfinTogether", "Control-NowPlaying", "Control-Offline-CarNet-Off", "Control-Offline-NoDetails",
            "Control-Offline", "Control-Series", "Control-Watch-Library", "Control-Watch",
            "Master-AddCode", "Master-Home-CarNet-Off", "Master-Home-Empty", "Master-Home", "Master-Jellyfin", "Master-Linked",
            "Master-Pause-Sheet", "Master-Tablet-Library", "Master-Tablet-Pick-Delete",
            "Screen-Viewer-Control", "Screen-Viewer-Lost", "Screen-Viewer-NeedsConsent", "Screen-Viewer-ViewOnly",
            "Settings-About", "Settings-Cameras", "Settings-Jellyfin", "Settings-PIN", "Settings-Tablets-CarNet-Manual",
            "Settings-Tablets-CarNet-Off", "Settings-Tablets-CarNet-On", "Settings-Tablets-CarNet-SwitchedOff", "Settings-Tablets",
            "Settings-Theme", "Setup-Master-CarNet", "Setup-Role-Android7", "Setup-Role", "Setup-Welcome",
            "Together-Jellyfin-Browse", "Together-Jellyfin-GettingReady", "Together-Jellyfin-Pick", "Together-Jellyfin-Playing",
            "Together-Jellyfin-Stream-Converted", "Together-Jellyfin-Stream", "Together-Library-Empty", "Together-Library",
            "Together-Pick", "Together-Remote", "Together-Shows", "Together-Stream-Waiting",
        )
    }

    /** Set only by the landscape sweep below: renders go to design/screens/sweep/<name><suffix>.png. */
    private var sweepSuffix = ""

    private fun shot(name: String, content: @Composable () -> Unit) {
        try {
            captureRoboImage(
                if (sweepSuffix.isEmpty()) "../design/screens/$name.png" else "../design/screens/sweep/$name$sweepSuffix.png",
                roborazziOptions = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = CHANGE_THRESHOLD)),
            ) {
                NickTheme { Box(Modifier.fillMaxSize().background(C.bg)) { content() } }
            }
        } catch (e: AssertionError) {
            throw AssertionError(
                "Screen \"$name\" no longer matches design/screens/$name.png. If the change is on purpose, " +
                    "re-record with: .\\gradlew.bat :app:recordRoborazziDebug  and commit design/screens. " +
                    "A side-by-side of what changed is in app/build/outputs/roborazzi. (${e.message})",
                e,
            )
        }
        phoneShot(name, content)
    }

    /**
     * The phone copy of a parent screen: the same state, rendered at 411x891dp inside the real [ParentLayoutProvider],
     * into design/screens/phone/<name>.png. Runs after the tablet picture has been compared, so the tablet pictures and
     * their tests are exactly as before. Node (kid) screens are not in [PHONE_SCREENS] and have no phone copy.
     */
    private fun phoneShot(name: String, content: @Composable () -> Unit) {
        if (sweepSuffix.isNotEmpty() || name !in PHONE_SCREENS) return
        org.robolectric.RuntimeEnvironment.setQualifiers(PHONE_QUALIFIERS)
        try {
            captureRoboImage(
                "../design/screens/phone/$name.png",
                roborazziOptions = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = CHANGE_THRESHOLD)),
            ) {
                NickTheme { Box(Modifier.fillMaxSize().background(C.bg)) { ParentLayoutProvider { content() } } }
            }
        } catch (e: AssertionError) {
            throw AssertionError("Phone screen \"$name\" no longer matches design/screens/phone/$name.png. (${e.message})", e)
        }
    }

    private var oldZone: TimeZone? = null
    private var oldLocale: Locale? = null

    @After
    fun unpin() {
        screenNow = { System.currentTimeMillis() }
        oldZone?.let { TimeZone.setDefault(it) }
        oldLocale?.let { Locale.setDefault(it) }
    }

    @Before
    fun seed() {
        oldZone = TimeZone.getDefault(); oldLocale = Locale.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Australia/Sydney"))
        Locale.setDefault(Locale.ENGLISH)
        screenNow = { FIXED_NOW }
        // The update check runs on app start and its answer is global: pin it so About never depends on test order.
        set(com.nbaltas.nickos.update.Updater, "_state", com.nbaltas.nickos.update.UpdateState.Idle)
        val s = app.store
        s.resetAll()
        s.setName("Dad's Tablet")
        s.upsertNode(LinkedNode("aaaaaaaaaaaa", "Hailey's Tablet", 0, "initial", "t", lastSeen = FIXED_NOW))
        s.upsertNode(LinkedNode("bbbbbbbbbbbb", "Leo's Tablet", 3, "initial", "t", lastSeen = FIXED_NOW))
        s.upsertNode(LinkedNode("cccccccccccc", "Mia's Tablet", 5, "initial", "t", lastSeen = FIXED_NOW - 12 * 60_000, lastBattery = 23, wifiSaved = true))
        // The car network is up, as it is on a Master that has run for a while. Fixed here so no screen depends on a clock.
        s.setHotspot(Hotspot(CAR_SSID, "carnet-pass-1", direct = true))
        val m = app.master
        set(m.carNet, "_state", CarNetState.On(CAR_SSID, "192.168.49.1"))
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
        set(m, "_playback", mapOf("aaaaaaaaaaaa" to Playback(sess, FIXED_NOW)))
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
    @Test fun setupRoleAndroid7() = shot("Setup-Role-Android7") { RoleScreen({}, {}, {}, nodeAllowed = false) }
    @Test fun nodeName() {
        app.store.setName("Hailey's Tablet")
        shot("Node-Name") { NodeNameScreen({}) {} }
    }
    @Test fun masterAddCode() = shot("Master-AddCode") { AddTabletScreen({}, "Master · Add tablet") {} }

    // ---- master ----
    @Test fun masterHome() = shot("Master-Home") { MasterHome({}, {}, {}, {}, {}, {}, {}) }
    /** The phone Home with the system font at its largest (2.0): nothing may be cut off. Phone only, so no tablet picture. */
    @Config(qualifiers = PHONE_QUALIFIERS)
    @Test fun masterHomePhoneFont200() {
        captureRoboImage(
            "../design/screens/phone/Master-Home-font200.png",
            roborazziOptions = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = CHANGE_THRESHOLD)),
        ) {
            NickTheme {
                Box(Modifier.fillMaxSize().background(C.bg)) {
                    val d = androidx.compose.ui.platform.LocalDensity.current
                    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density, 2f)) {
                        ParentLayoutProvider { MasterHome({}, {}, {}, {}, {}, {}, {}) }
                    }
                }
            }
        }
    }
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
    @Test fun masterPauseSheet() = shot("Master-Pause-Sheet") { com.nbaltas.nickos.ui.master.PauseAllSheet(paused = false, startMsg = "Time to eat", onSubmit = { _, _ -> }, onClose = {}) }
    @Test fun controlDeviceRing() {
        set(app.master, "_status", mapOf(
            "aaaaaaaaaaaa" to status(82, fg = "org.jellyfin.mobile", fgLabel = "Jellyfin", caps = listOf("sync1", "ring1", "pause1")),
            "bbbbbbbbbbbb" to status(41, fg = "com.example.drawing", fgLabel = "Drawing"),
        ))
        shot("Control-Device-Ring") { ControlScreen("aaaaaaaaaaaa", TAB_DEVICE, {}, {}, {}) }
    }
    /** A 0.10 Node reports power: the real Device tab (not BatteryBody alone) must compose, with no scroll nested in a scrolling card. */
    private fun deviceTabWithPower() {
        set(app.master, "_status", mapOf(
            "aaaaaaaaaaaa" to status(82, fg = "org.jellyfin.mobile", fgLabel = "Jellyfin", caps = listOf("sync1", "ring1", "pause1", "screen1"))
                .copy(power = NetMessages.PowerStatus(true, 5, NetMessages.STATE_NO_PERMISSION)),
        ))
        shot("Control-Device-Power-${if (app.resources.configuration.screenWidthDp >= 1000) "1280x800" else "Portrait"}") {
            ControlScreen("aaaaaaaaaaaa", TAB_DEVICE, {}, {}, {})
        }
    }
    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    @Test fun controlDevicePowerGrid() = deviceTabWithPower()
    @Config(qualifiers = "w800dp-h1280dp-port-mdpi")
    @Test fun controlDevicePowerPortrait() = deviceTabWithPower()
    @Test fun settingsTablets() = shot("Settings-Tablets") { SettingsScreen(0, {}, {}, {}) }
    @Test fun settingsJellyfin() = shot("Settings-Jellyfin") { SettingsScreen(1, {}, {}, {}) }
    @Test fun settingsPin() = shot("Settings-PIN") { SettingsScreen(3, {}, {}, {}) }
    @Test fun settingsTheme() = shot("Settings-Theme") { SettingsScreen(4, {}, {}, {}) }
    @Test fun settingsAbout() = shot("Settings-About") { SettingsScreen(5, {}, {}, {}) }
    @Test fun settingsCameras() = shot("Settings-Cameras") { SettingsScreen(2, {}, {}, {}) }

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

    // ---- push to talk on the Cameras screen ----
    private fun talkCaps(vararg ids: String) {
        val all = listOf(CH, CL, CM).associateWith { id -> camStatus(true).let { if (id in ids) it.copy(caps = it.caps + "talk1") else it } }
        set(app.master, "_status", all)
    }

    /** Two tablets that can hear and one that is too old: the button is ready and names who hears. */
    @Test fun camerasTalkReady() {
        seedCams()
        talkCaps(CH, CL)
        cams(setOf(CH, CL, CM), mapOf(CH to camLive(), CL to camLive(), CM to camLive()))
        camPicture(CH, camBlue.first, camBlue.second)
        camPicture(CL, camGreen.first, camGreen.second)
        camPicture(CM, camPlum.first, camPlum.second)
        shot("Cameras-Talk-Ready") { CamerasScreen({}, {}, live = false) }
    }

    /** A press in progress: the button is lit and the line names the frozen recipients. */
    @Test fun camerasTalking() {
        seedCams()
        talkCaps(CH, CL)
        cams(setOf(CH, CL), mapOf(CH to camLive(), CL to camLive()))
        camPicture(CH, camBlue.first, camBlue.second)
        camPicture(CL, camGreen.first, camGreen.second)
        shot("Cameras-Talk-Talking") {
            CamerasScreen({}, {}, live = false, talkPreset = com.nbaltas.nickos.master.TalkState("0123456789abcdef", listOf(CH, CL), listOf("Hailey", "Leo")))
        }
    }

    /** The only tablet on the stage is an old version: the button is greyed and says to update it. */
    @Test fun camerasTalkOldTablet() {
        seedCams()
        talkCaps()
        cams(setOf(CH), mapOf(CH to camLive()))
        camPicture(CH, camBlue.first, camBlue.second)
        shot("Cameras-Talk-Update") { CamerasScreen({}, {}, live = false) }
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

    private fun jfMovie() = com.nbaltas.nickos.jellyfin.JfItem(
        "m1", "Captain Comet", "Movie", null, null, null, null, null, 90L * JfItem.TICKS_PER_MIN, 0, false, null, 2021, null, null, false, false, false, null, null,
        container = "mkv", size = 1_000_000_000L,
    )

    @Test fun togetherJellyfinBrowse() {
        addStatus("cccccccccccc", 23)
        set(app.master.sync, "_videos", mapOf(Library.SELF to DeviceLibrary(true, listOf(video("cc", "Captain Comet")))))
        jellyfinShot("Together-Jellyfin-Browse", null, "Continue Watching", together = true)
    }

    @Test fun togetherJellyfinPick() {
        addStatus("cccccccccccc", 23)
        shot("Together-Jellyfin-Pick") {
            val nodes = rememberNodeViews()
            JfPickContent(jfMovie(), nodes, nodes.filter { it.online }.map { it.node.id }.toSet(), { _, _ -> }, prep = null, note = null, onBack = {}, onPrimary = {})
        }
    }

    @Test fun togetherJellyfinGettingReady() {
        addStatus("cccccccccccc", 23)
        shot("Together-Jellyfin-GettingReady") {
            val nodes = rememberNodeViews()
            val on = nodes.filter { it.online }
            val rows = on.mapIndexed { i, n ->
                when (i) {
                    0 -> JfRow(n.node.id, "Ready", null, ready = true, failed = false)
                    1 -> JfRow(n.node.id, "42% · 420 MB of 1 GB", 0.42f, ready = false, failed = false)
                    else -> JfRow(n.node.id, "Failed", null, ready = false, failed = true, error = "Not enough space (needs 1 GB, has 300 MB)")
                }
            }
            JfPickContent(
                jfMovie(), nodes, on.map { it.node.id }.toSet(), { _, _ -> },
                prep = JfPrep(0.65f, null, rows), note = "Leo went offline, starting without them", onBack = {}, onPrimary = {},
            )
        }
    }

    @Test fun togetherJellyfinStream() {
        addStatus("cccccccccccc", 23)
        shot("Together-Jellyfin-Stream") {
            val nodes = rememberNodeViews()
            // Hailey can stream, Leo's tablet is too old; the count and label come from the same split the screen uses.
            val selected = nodes.filter { it.online }.map { it.node.id }.toSet()
            val (ok, no) = JfTogether.streamSplit(selected) { id -> nodes.first { it.node.id == id }.node.name.startsWith("Hailey") }
            val stream = JfStreamUi(ok.size, JfTogether.streamLabel(ok.size, no.map { id -> nodes.first { it.node.id == id }.node.name }), onStream = {})
            JfPickContent(
                jfMovie(), nodes, selected, { _, _ -> },
                prep = null, note = null, onBack = {}, onPrimary = {}, stream = stream,
            )
        }
    }

    @Test fun togetherJellyfinStreamConverted() {
        addStatus("cccccccccccc", 23)
        shot("Together-Jellyfin-Stream-Converted") {
            val nodes = rememberNodeViews()
            val selected = nodes.filter { it.online }.map { it.node.id }.toSet()
            val (ok, no) = JfTogether.streamSplit(selected) { id -> nodes.first { it.node.id == id }.node.name.startsWith("Hailey") }
            val stream = JfStreamUi(
                ok.size, JfTogether.streamLabel(ok.size, no.map { id -> nodes.first { it.node.id == id }.node.name }), onStream = {},
                convertedLine = com.nbaltas.nickos.sync.StreamCodec.convertedLine(listOf("Leo")),
            )
            JfPickContent(
                jfMovie(), nodes, selected, { _, _ -> },
                prep = null, note = null, onBack = {}, onPrimary = {}, stream = stream,
            )
        }
    }

    @Test fun togetherStreamWaiting() {
        val m = app.master
        val now = android.os.SystemClock.elapsedRealtime()
        val fid = "jf:ab12cd34:m1"
        set(m.sync, "_session", SyncSession(fid, "Captain Comet", 90 * 60_000L, setOf("aaaaaaaaaaaa", "bbbbbbbbbbbb"), 12 * 60_000L, now, false, "http://jf.local/x"))
        set(m.sync, "_states", mapOf(
            "aaaaaaaaaaaa" to NodeSyncState(SyncState.PAUSED, 12 * 60_000L, now, 90 * 60_000L, 1f, null, now),
            "bbbbbbbbbbbb" to NodeSyncState(SyncState.BUFFERING, 12 * 60_000L, now, 90 * 60_000L, 1f, null, now),
        ))
        set(m.sync, "_offsets", mapOf(
            "aaaaaaaaaaaa" to ClockInfo(0L, 20L, SyncQuality.GOOD),
            "bbbbbbbbbbbb" to ClockInfo(0L, 20L, SyncQuality.GOOD),
        ))
        set(m.sync, "_waiting", "Waiting for Leo")
        shot("Together-Stream-Waiting") { TogetherScreen({}, {}) }
    }

    @Test fun togetherJellyfinPlaying() {
        val m = app.master
        val now = android.os.SystemClock.elapsedRealtime()
        set(m.sync, "_session", SyncSession(
            "jf:ab12cd34:m1", "Captain Comet", 90 * 60_000L, setOf("aaaaaaaaaaaa", "bbbbbbbbbbbb"), 12 * 60_000L, now, true,
            "http://jf.local/x", "http://jf.local/Items/m1/Images/Primary",
        ))
        set(m.sync, "_states", mapOf(
            "aaaaaaaaaaaa" to NodeSyncState(SyncState.PLAYING, 12 * 60_000L, now, 90 * 60_000L, 1f, null, now),
            "bbbbbbbbbbbb" to NodeSyncState(SyncState.PLAYING, 12 * 60_000L, now, 90 * 60_000L, 1f, null, now),
        ))
        set(m.sync, "_offsets", mapOf(
            "aaaaaaaaaaaa" to ClockInfo(0L, 20L, SyncQuality.GOOD),
            "bbbbbbbbbbbb" to ClockInfo(0L, 20L, SyncQuality.GOOD),
        ))
        shot("Together-Jellyfin-Playing") { TogetherScreen({}, {}) }
    }

    /** The tablet's own Now Playing tab during a Jellyfin stream: poster and title from the Jellyfin artwork. */
    @Test fun controlNowPlayingJellyfinTogether() {
        val m = app.master
        val now = android.os.SystemClock.elapsedRealtime()
        set(m.sync, "_session", SyncSession(
            "jf:ab12cd34:m1", "Captain Comet", 90 * 60_000L, setOf("aaaaaaaaaaaa", "bbbbbbbbbbbb"), 12 * 60_000L, now, true,
            "http://jf.local/x", "http://jf.local/Items/m1/Images/Primary",
        ))
        shot("Control-NowPlaying-JellyfinTogether") { ControlScreen("aaaaaaaaaaaa", TAB_NOW, {}, {}, {}) }
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

    // ---- brief D: the six designed screens that had no picture (Control-Watch, Control-Movie, Control-Series,
    //      Master-Jellyfin, Master-Linked, Node-Code). Appended; the designer's originals are kept in design/handoff. ----

    @Test fun masterJellyfin() = shot("Master-Jellyfin") { com.nbaltas.nickos.ui.setup.JellyfinSetupScreen({}, {}) }

    @Test fun masterLinked() {
        val node = app.store.nodes.value.first { it.id == "aaaaaaaaaaaa" }
        val result = MasterRuntime.PairResult(node, status(82, fg = null, fgLabel = null))
        shot("Master-Linked") { com.nbaltas.nickos.ui.setup.LinkedScreen(result, {}, {}) }
    }

    /** NodeCodeScreen starts the node service and draws a random code, so this lays out its two parts with a fixed code. */
    @Test fun nodeCode() {
        app.store.setName("Hailey's Tablet"); app.store.setColor(0)
        shot("Node-Code") {
            androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
                com.nbaltas.nickos.ui.theme.SetupHeader("Node · Step 3 of 3", {})
                com.nbaltas.nickos.ui.setup.PairingCodeBody("Hailey's Tablet", 0, app.store.avatar.value, "482916", address = "192.168.1.20")
            }
        }
    }

    // A tiny Jellyfin on 127.0.0.1 so the three Jellyfin screens render real rows. No image URLs are offered, so
    // every poster is the app's own gradient artwork and nothing leaves the machine.
    private fun jfJson(
        id: String, name: String, type: String, run: Long = 0, pos: Long = 0, played: Boolean = false,
        series: String? = null, seriesName: String? = null, season: Int? = null, index: Int? = null,
        seasonId: String? = null, overview: String? = null, rating: String? = null, year: Int? = null,
    ): String {
        val o = org.json.JSONObject().put("Id", id).put("Name", name).put("Type", type)
        if (run > 0) o.put("RunTimeTicks", run * JfItem.TICKS_PER_SEC)
        o.put("UserData", org.json.JSONObject().put("PlaybackPositionTicks", pos * JfItem.TICKS_PER_SEC).put("Played", played))
        series?.let { o.put("SeriesId", it) }; seriesName?.let { o.put("SeriesName", it) }
        season?.let { o.put("ParentIndexNumber", it) }; index?.let { o.put("IndexNumber", it) }
        seasonId?.let { o.put("SeasonId", it) }; overview?.let { o.put("Overview", it) }
        rating?.let { o.put("OfficialRating", it) }; year?.let { o.put("ProductionYear", it) }
        return o.toString()
    }

    private fun fakeJellyfin(): java.net.ServerSocket {
        val min = 60L
        val resume = listOf(
            jfJson("e25", "The Lighthouse", "Episode", 24 * min + 10, 18 * min + 2, series = "sh", seriesName = "Star Harbour", season = 2, index = 5, seasonId = "s2"),
            jfJson("cc", "Captain Comet", "Movie", 92 * min, 51 * min + 10, overview = "A small-town kid finds a comet in the backyard and has one night to send it home.", rating = "G"),
            jfJson("mp3", "The Fog Bell", "Episode", 22 * min, 10 * min, series = "mp", seriesName = "Moss & Pebble", season = 1, index = 3, seasonId = "mps1"),
            jfJson("sf", "The Snow Fox", "Movie", 80 * min, 18 * min),
            jfJson("rp", "Robo Pals", "Episode", 22 * min, 3 * min, series = "rps", seriesName = "Robo Pals", season = 3, index = 1, seasonId = "rp3"),
        )
        val nextUp = listOf(
            jfJson("lv4", "Night Market", "Episode", 24 * min, series = "lv", seriesName = "Lantern Valley", season = 1, index = 4, seasonId = "lv1"),
            jfJson("dd1", "Bones!", "Episode", 24 * min, series = "dd", seriesName = "Dino Dig Crew", season = 2, index = 1, seasonId = "dd2"),
            jfJson("oe9", "The Reef", "Episode", 24 * min, series = "oe", seriesName = "Ocean Explorers", season = 1, index = 9, seasonId = "oe1"),
            jfJson("wk2", "Pancakes", "Episode", 24 * min, series = "wk", seriesName = "Wild Kitchen", season = 1, index = 2, seasonId = "wk1"),
            jfJson("tt7", "Big Surprise", "Episode", 24 * min, series = "tt", seriesName = "Tiny Tugboat", season = 1, index = 7, seasonId = "tt1"),
        )
        val movies = listOf(
            jfJson("cc", "Captain Comet", "Movie", 92 * min, 51 * min + 10, year = 2021),
            jfJson("sf", "The Snow Fox", "Movie", 80 * min, 18 * min, year = 2019),
            jfJson("mm", "Moon Mice", "Movie", 76 * min, year = 2022),
        )
        val shows = listOf(jfJson("sh", "Star Harbour", "Series", year = 2020), jfJson("mp", "Moss & Pebble", "Series", year = 2018))
        val episodes = listOf(
            jfJson("e21", "Welcome Aboard", "Episode", 23 * min, 23 * min, true, "sh", "Star Harbour", 2, 1, "s2"),
            jfJson("e22", "Seagull Trouble", "Episode", 24 * min, 24 * min, true, "sh", "Star Harbour", 2, 2, "s2"),
            jfJson("e23", "The Night Crossing", "Episode", 22 * min, 22 * min, true, "sh", "Star Harbour", 2, 3, "s2"),
            jfJson("e24", "Treasure Buoy", "Episode", 24 * min, 24 * min, true, "sh", "Star Harbour", 2, 4, "s2"),
            resume[0],
            jfJson("e26", "Fog Horn", "Episode", 23 * min, 0, false, "sh", "Star Harbour", 2, 6, "s2"),
        )
        val series = jfJson("sh", "Star Harbour", "Series", rating = "G", overview = "A little tugboat crew keeps the harbour lights on for every ship that comes home.")
        val seasons = listOf(jfJson("s1", "Season 1", "Season"), jfJson("s2", "Season 2", "Season"))
        fun arr(l: List<String>) = "{\"Items\":[" + l.joinToString(",") + "]}"
        val byId = (resume + nextUp + movies + episodes).associateBy { org.json.JSONObject(it).getString("Id") } + ("sh" to series)

        val server = java.net.ServerSocket(0, 50, java.net.InetAddress.getLoopbackAddress())
        Thread {
            while (!server.isClosed) {
                val sock = try { server.accept() } catch (e: Exception) { break }
                Thread {
                    sock.use {
                        val reader = it.getInputStream().bufferedReader()
                        val target = reader.readLine()?.split(" ")?.getOrNull(1) ?: return@use
                        while (reader.readLine()?.isNotEmpty() == true) { /* skip the headers */ }
                        val path = target.substringBefore('?'); val q = target.substringAfter('?', "")
                        val body = when {
                            path == "/Users/u1/Items/Resume" -> arr(if ("ParentId=" in q) listOf(resume[0]) else resume)
                            path == "/Shows/NextUp" -> arr(nextUp)
                            path == "/Users/u1/Items" -> arr(if ("IncludeItemTypes=Series" in q) shows else movies)
                            path.startsWith("/Users/u1/Items/") -> byId[path.substringAfterLast('/')]
                            path.endsWith("/Seasons") -> arr(seasons)
                            path.endsWith("/Episodes") -> arr(episodes)
                            else -> null
                        }
                        val bytes = (body ?: "").toByteArray()
                        val head = (if (body == null) "HTTP/1.1 404 Not Found" else "HTTP/1.1 200 OK") +
                            "\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
                        it.getOutputStream().apply { write(head.toByteArray()); write(bytes); flush() }
                    }
                }.start()
            }
        }.apply { isDaemon = true }.start()
        return server
    }

    /** Loads fixture rows in Control Watch or Together, optionally taps [open], and saves the loaded picture. */
    private fun jellyfinShot(name: String, open: String?, waitFor: String, together: Boolean = false) {
        jellyfinShotOnce(name, open, waitFor, together, phone = false)
        if (name in PHONE_SCREENS) {
            org.robolectric.RuntimeEnvironment.setQualifiers(PHONE_QUALIFIERS)
            jellyfinShotOnce(name, open, waitFor, together, phone = true)
        }
    }

    @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
    private fun jellyfinShotOnce(name: String, open: String?, waitFor: String, together: Boolean, phone: Boolean) {
        val server = fakeJellyfin()
        try {
            app.store.setJellyfin(JellyfinAccount("http://127.0.0.1:${server.localPort}", "dad", "tok", "u1", true))
            app.store.upsertNode(app.store.nodes.value.first { it.id == "aaaaaaaaaaaa" }.copy(jfUserId = "u1", jfUserName = "Hailey"))
            androidx.compose.ui.test.runAndroidComposeUiTest<androidx.activity.ComponentActivity> {
                setContent {
                    NickTheme {
                        Box(Modifier.fillMaxSize().background(C.bg)) {
                            val screen: @Composable () -> Unit = {
                                if (together) TogetherScreen({}, {}, initialSegment = 2)
                                else ControlScreen("aaaaaaaaaaaa", TAB_WATCH, {}, {}, {})
                            }
                            if (phone) ParentLayoutProvider(screen) else screen()
                        }
                    }
                }
                if (!together) onNodeWithTag("jellyfin-library").performClick()
                fun has(t: String) = onAllNodes(hasText(t, substring = true)).fetchSemanticsNodes().isNotEmpty()
                waitUntil(timeoutMillis = 20_000) { has("Continue Watching") }
                if (open != null) {
                    onAllNodes(hasText(open))[0].performClick()
                    waitUntil(timeoutMillis = 20_000) { has(waitFor) }
                }
                waitForIdle()
                onRoot().captureRoboImage(
                    if (phone) "../design/screens/phone/$name.png" else "../design/screens/$name.png",
                    roborazziOptions = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = CHANGE_THRESHOLD)),
                )
            }
        } finally { server.close() }
    }

    @Test fun controlWatch() = jellyfinShot("Control-Watch", null, "Continue Watching")
    @Test fun controlMovie() = jellyfinShot("Control-Movie", "Captain Comet", "Play from start")
    @Test fun controlSeries() = jellyfinShot("Control-Series", "Star Harbour", "Fog Horn")
    @Test fun nodeBedtime() { nodeSeed(); shot("Node-Bedtime") { kidHome(); BedtimeToastAtBottom(300) } }

    // ---- car network (F2B revised) ----
    private fun carState(s: CarNetState) = set(app.master.carNet, "_state", s)
    private fun wifiReports(vararg r: Pair<String, NetMessages.WifiStatus>) =
        set(app.master, "_net", r.associate { (id, st) -> id to NodeNet(true, st) })

    @Test fun settingsTabletsCarNetOn() {
        // Hailey has the network, Leo has not, Mia (offline) has it from her last report.
        wifiReports("aaaaaaaaaaaa" to NetMessages.WifiStatus(saved = true, deviceOwner = true, doze = true),
            "bbbbbbbbbbbb" to NetMessages.WifiStatus(saved = false, deviceOwner = true, doze = true))
        shot("Settings-Tablets-CarNet-On") { SettingsScreen(0, {}, {}, {}) }
    }

    @Test fun settingsTabletsCarNetOff() {
        carState(CarNetState.Off(Why.NO_PERMISSION))
        wifiReports("aaaaaaaaaaaa" to NetMessages.WifiStatus(saved = true, approve = true, deviceOwner = false, doze = true),
            "bbbbbbbbbbbb" to NetMessages.WifiStatus(saved = true, deviceOwner = true, doze = true))
        shot("Settings-Tablets-CarNet-Off") { SettingsScreen(0, {}, {}, {}) }
    }

    @Test fun settingsTabletsCarNetSwitchedOff() {
        app.store.setCarNetOn(false)          // switch off; the runtime state stays On (set in seed()) to prove the card follows the preference
        wifiReports("aaaaaaaaaaaa" to NetMessages.WifiStatus(saved = true, deviceOwner = true, doze = true),
            "bbbbbbbbbbbb" to NetMessages.WifiStatus(saved = true, deviceOwner = true, doze = true))
        shot("Settings-Tablets-CarNet-SwitchedOff") { SettingsScreen(0, {}, {}, {}) }
    }

    @Test fun settingsTabletsCarNetManual() {
        app.store.setHotspot(Hotspot("Dad's phone", "hotspot-pass-1", direct = false))
        carState(CarNetState.Off(Why.MANUAL))
        wifiReports("aaaaaaaaaaaa" to NetMessages.WifiStatus(saved = true, deviceOwner = true, doze = true),
            "bbbbbbbbbbbb" to NetMessages.WifiStatus(saved = true, deviceOwner = true, doze = false))
        shot("Settings-Tablets-CarNet-Manual") { SettingsScreen(0, {}, {}, {}) }
    }

    @Test fun masterHomeCarNetOff() {
        carState(CarNetState.Off(Why.WIFI_OFF))
        shot("Master-Home-CarNet-Off") { MasterHome({}, {}, {}, {}, {}, {}, {}) }
    }

    @Test fun controlOfflineNoDetails() {
        app.store.upsertNode(LinkedNode("cccccccccccc", "Mia's Tablet", 5, "initial", "t", lastSeen = FIXED_NOW - 12 * 60_000, lastBattery = 23, wifiSaved = false))
        shot("Control-Offline-NoDetails") { ControlScreen("cccccccccccc", TAB_NOW, {}, {}, {}) }
    }

    @Test fun controlOfflineCarNetOff() {
        carState(CarNetState.Off(Why.NO_LOCATION))
        shot("Control-Offline-CarNet-Off") { ControlScreen("cccccccccccc", TAB_NOW, {}, {}, {}) }
    }

    @Test fun setupMasterCarNet() = shot("Setup-Master-CarNet") { MasterNetScreen({}, {}) }

    // ---- delete a movie from a tablet (Master) ----
    private val deleteNode = "aaaaaaaaaaaa"

    private fun seedDeletable() {
        val m = app.master
        set(m, "_status", mapOf(deleteNode to status(82, fg = null, fgLabel = null, caps = listOf("sync1", "del1"))))
        set(m.sync, "_videos", mapOf(
            deleteNode to DeviceLibrary(true, listOf(video("sh", "Star Harbour"), video("cc", "Captain Comet"), video("mp", "Moss and Pebble")), 9_770_000_000L),
        ))
    }

    @Test fun masterTabletLibrary() {
        seedDeletable()
        shot("Master-Tablet-Library") {
            val v = rememberNodeViews().first { it.node.id == deleteNode }
            TabletLibrary(deleteNode, v, onPlayed = {}, onSettings = {})
        }
    }

    @Test fun masterTabletPickDelete() {
        seedDeletable()
        val m = app.master
        val item = Library.merge(m.sync.videos.value.mapValues { it.value.list }).first { it.fid == "cc" }
        shot("Master-Tablet-Pick-Delete") {
            TabletPickPanel(item, deleteNode, null, m.sync, m.transfer, canDelete = true, onBack = {})
        }
    }

    // ---- pairing at real tablet sizes (a 6-digit code must never be cut to 3) ----
    private fun sizeTag(): String {
        val c = app.resources.configuration
        return "${c.screenWidthDp}x${c.screenHeightDp}"
    }

    private fun nodeCodeAt() { nodeSeed(); shot("Node-Code-${sizeTag()}") { PairingCodeBody("Hailey's Tablet", 0, "initial", "482916") } }

    @Config(qualifiers = "w960dp-h600dp-land-mdpi")
    @Test fun nodeCode960() = nodeCodeAt()
    @Config(qualifiers = "w1024dp-h768dp-land-mdpi")
    @Test fun nodeCode1024() = nodeCodeAt()
    @Config(qualifiers = "w1100dp-h680dp-land-mdpi")
    @Test fun nodeCode1100() = nodeCodeAt()
    @Config(qualifiers = "w600dp-h960dp-port-mdpi")
    @Test fun nodeCodePortrait() = nodeCodeAt()

    private fun masterCode() = shot("Master-AddCode-${sizeTag()}") { AddTabletScreen({}, "Master · Add tablet") {} }

    @Config(qualifiers = "w960dp-h600dp-land-mdpi")
    @Test fun masterAddCode960() = masterCode()
    @Config(qualifiers = "w1024dp-h768dp-land-mdpi")
    @Test fun masterAddCode1024() = masterCode()
    @Config(qualifiers = "w1100dp-h680dp-land-mdpi")
    @Test fun masterAddCode1100() = masterCode()
    @Config(qualifiers = "w600dp-h960dp-port-mdpi")
    @Test fun masterAddCodePortrait() = masterCode()

    private fun qrSetup() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        org.robolectric.Shadows.shadowOf(ctx.packageManager).getInternalMutablePackageInfo(ctx.packageName).signatures = arrayOf(android.content.pm.Signature("0102030405"))
        qrShot()
    }
    private fun qrShot() = shot("Master-QrSetup-${sizeTag()}") { com.nbaltas.nickos.ui.master.QrSetupScreen({}, kotlinx.coroutines.Dispatchers.Unconfined) }

    @Config(sdk = [27], qualifiers = "w960dp-h600dp-land-mdpi")
    @Test fun masterQrSetup960() = qrSetup()
    @Config(sdk = [27], qualifiers = "w1024dp-h768dp-land-mdpi")
    @Test fun masterQrSetup1024() = qrSetup()

    // ---- landscape sweep: every tablet screen at the two real landscape sizes ----
    // Developer tool, off by default (it would write ~80 pictures). Turn it on by creating the file .pipeline/sweep,
    // run ./gradlew :app:recordRoborazziDebug --tests "*landscapeSweep*", then LOOK at design/screens/sweep.
    private fun sweepOn() = java.io.File("../.pipeline/sweep").exists()

    private fun sweepAt(qualifiers: String) {
        org.junit.Assume.assumeTrue(sweepOn())
        org.robolectric.RuntimeEnvironment.setQualifiers(qualifiers)
        sweepSuffix = "-" + sizeTag()
        val screens: List<() -> Unit> = listOf(
            ::setupWelcome, ::setupRole, ::nodeName, ::masterAddCode, ::masterHome, ::masterHomeEmpty,
            ::controlNowPlaying, ::controlNothingPlaying, ::controlOffline, ::controlApps, ::controlDevice, ::controlWatchLibrary,
            ::settingsTablets, ::settingsJellyfin, ::settingsCameras, ::settingsPin, ::settingsTheme, ::settingsAbout,
            ::camerasSingle, ::camerasGrid, ::camerasSpotlight, ::camerasStates, ::camerasNothingOn, ::camerasEmpty,
            ::togetherLibrary, ::togetherShows, ::togetherPick, ::togetherLibraryEmpty, ::togetherRemote,
            ::masterTabletLibrary, ::masterTabletPickDelete,
            ::nodeHome, ::nodePin, ::nodeLocked, ::nodeBedtime, ::nodeTogether, ::nodeCodeAt,
        )
        try {
            screens.forEach { render -> seed(); render() }
        } finally { sweepSuffix = "" }
    }

    @Test fun landscapeSweep960() = sweepAt("w960dp-h600dp-land-mdpi")
    @Test fun landscapeSweep1024() = sweepAt("w1024dp-h768dp-land-mdpi")

    // ---- big system font: the code must still be 6 digits on one line ----
    private fun nodeCodeBigFont(scale: Float) { nodeSeed(); shot("Node-Code-${sizeTag()}-font${(scale * 100).toInt()}") {
        val d = androidx.compose.ui.platform.LocalDensity.current
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density, scale)) {
            PairingCodeBody("Hailey's Tablet", 0, "initial", "482916")
        }
    } }

    @Config(qualifiers = "w960dp-h600dp-land-mdpi")
    @Test fun nodeCode960BigFont() = nodeCodeBigFont(1.5f)
    @Config(qualifiers = "w1024dp-h768dp-land-mdpi")
    @Test fun nodeCode1024BigFont() = nodeCodeBigFont(1.5f)

    // ---- controls and relink fix: the Device tab and the Node's Grown-up menu at both real tablet sizes ----
    private fun deviceTab() = shot("Control-Device-${sizeTag()}") { ControlScreen("aaaaaaaaaaaa", TAB_DEVICE, {}, {}, {}) }

    @Config(qualifiers = "w960dp-h600dp-land-mdpi")
    @Test fun controlDevice960() = deviceTab()
    @Config(qualifiers = "w1024dp-h768dp-land-mdpi")
    @Test fun controlDevice1024() = deviceTab()

    /** A Node that has not been given the brightness / screen off permission: the Master greys the controls and says why. */
    private fun deviceTabNoPerms() {
        set(app.master, "_status", mapOf("aaaaaaaaaaaa" to status(82, fg = "org.jellyfin.mobile", fgLabel = "Jellyfin").copy(brightnessOk = false, screenOffOk = false)))
        shot("Control-Device-NoPerms-${sizeTag()}") { ControlScreen("aaaaaaaaaaaa", TAB_DEVICE, {}, {}, {}) }
    }

    @Config(qualifiers = "w960dp-h600dp-land-mdpi")
    @Test fun controlDeviceNoPerms960() = deviceTabNoPerms()
    @Config(qualifiers = "w1024dp-h768dp-land-mdpi")
    @Test fun controlDeviceNoPerms1024() = deviceTabNoPerms()

    /** The Screen card's "Lock to landscape" switch: on and working, and on but missing the overlay permission. */
    private fun deviceTabLandscape(tag: String, l: com.nbaltas.nickos.link.NetMessages.LandscapeStatus) {
        set(app.master, "_status", mapOf("aaaaaaaaaaaa" to status(82, fg = "org.jellyfin.mobile", fgLabel = "Jellyfin").copy(landscape = l)))
        shot("Control-Device-Landscape-$tag-${sizeTag()}") { ControlScreen("aaaaaaaaaaaa", TAB_DEVICE, {}, {}, {}) }
    }

    private val landscapeOn = com.nbaltas.nickos.link.NetMessages.LandscapeStatus(true, "on")
    private val landscapeBlocked = com.nbaltas.nickos.link.NetMessages.LandscapeStatus(true, "no_permission")

    // At 960 the Device tab is one scrolling column and the control sits below the first screen. The picture helper
    // cannot scroll, so the 960 picture is taken on a tall 960 wide screen that shows the whole column, control included.
    @Config(qualifiers = "w960dp-h1100dp-mdpi")
    @Test fun controlDeviceLandscapeOnColumn960() = deviceTabLandscape("On", landscapeOn)
    @Config(qualifiers = "w960dp-h1100dp-mdpi")
    @Test fun controlDeviceLandscapePendingColumn960() = deviceTabLandscape("Pending", com.nbaltas.nickos.link.NetMessages.LandscapeStatus(true, "pending"))
    @Config(qualifiers = "w1024dp-h768dp-land-mdpi")
    @Test fun controlDeviceLandscapeOn1024() = deviceTabLandscape("On", landscapeOn)
    @Config(qualifiers = "w1024dp-h768dp-land-mdpi")
    @Test fun controlDeviceLandscapeBlocked1024() = deviceTabLandscape("NoPermission", landscapeBlocked)

    /** The "Touch" switch under Landscape: touch turned off and the overlay shield in place. The foreman records it. */
    @Config(qualifiers = "w960dp-h1100dp-mdpi")
    @Test fun deviceTouchSwitch() {
        val s = status(82, fg = "org.jellyfin.mobile", fgLabel = "Jellyfin")
            .copy(landscape = landscapeOn, caps = listOf("touch1"), touch = com.nbaltas.nickos.link.NetMessages.TouchStatus(true, "on"))
        set(app.master, "_status", mapOf("aaaaaaaaaaaa" to s))
        shot("Device-TouchSwitch") { ControlScreen("aaaaaaaaaaaa", TAB_DEVICE, {}, {}, {}) }
    }

    /** A node with touch1 and landscape: the Touch switch and "Lock to landscape" must both be fully visible. The foreman records them. */
    private fun deviceTouchLandscape(size: String) {
        val s = status(82, fg = "org.jellyfin.mobile", fgLabel = "Jellyfin")
            .copy(landscape = landscapeOn, caps = listOf("touch1"), touch = com.nbaltas.nickos.link.NetMessages.TouchStatus(true, "on"))
        set(app.master, "_status", mapOf("aaaaaaaaaaaa" to s))
        shot("Control-Device-Touch-Landscape-$size") { ControlScreen("aaaaaaaaaaaa", TAB_DEVICE, {}, {}, {}) }
    }

    @Config(qualifiers = "w1024dp-h768dp-land-mdpi")
    @Test fun controlDeviceTouchLandscape1024() = deviceTouchLandscape("1024x768")
    @Config(qualifiers = "w960dp-h600dp-land-mdpi")
    @Test fun controlDeviceTouchLandscape960() = deviceTouchLandscape("960x600")

    /** A real 0.12 tablet: ring1, touch1, landscape, power and screen together. Every control must be visible at 1024 and 1280. */
    private fun deviceAllFeatures(size: String) {
        val s = status(82, fg = "org.jellyfin.mobile", fgLabel = "Jellyfin", caps = listOf("sync1", "ring1", "pause1", "screen1", "touch1"))
            .copy(landscape = landscapeOn, touch = com.nbaltas.nickos.link.NetMessages.TouchStatus(true, "on"),
                power = NetMessages.PowerStatus(true, 5, NetMessages.STATE_NO_PERMISSION))
        set(app.master, "_status", mapOf("aaaaaaaaaaaa" to s))
        shot("Control-Device-AllFeatures-$size") { ControlScreen("aaaaaaaaaaaa", TAB_DEVICE, {}, {}, {}) }
    }

    @Config(qualifiers = "w1024dp-h768dp-land-mdpi")
    @Test fun controlDeviceAllFeatures1024() = deviceAllFeatures("1024x768")
    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    @Test fun controlDeviceAllFeatures1280() = deviceAllFeatures("1280x800")
    @Config(qualifiers = "w960dp-h600dp-land-mdpi")
    @Test fun controlDeviceAllFeatures960() = deviceAllFeatures("960x600")

    /** The same page scrolled to the bottom: the last row of cards is complete, none is clipped inside. */
    @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
    @Config(qualifiers = "w1024dp-h768dp-land-mdpi")
    @Test fun controlDeviceAllFeatures1024Scrolled() {
        val s = status(82, fg = "org.jellyfin.mobile", fgLabel = "Jellyfin", caps = listOf("sync1", "ring1", "pause1", "screen1", "touch1"))
            .copy(landscape = landscapeOn, touch = com.nbaltas.nickos.link.NetMessages.TouchStatus(true, "on"),
                power = NetMessages.PowerStatus(true, 5, NetMessages.STATE_NO_PERMISSION))
        set(app.master, "_status", mapOf("aaaaaaaaaaaa" to s))
        androidx.compose.ui.test.runAndroidComposeUiTest<androidx.activity.ComponentActivity> {
            setContent { NickTheme { Box(Modifier.fillMaxSize().background(C.bg)) { ControlScreen("aaaaaaaaaaaa", TAB_DEVICE, {}, {}, {}) } } }
            waitForIdle()
            repeat(3) { onRoot().performTouchInput { swipeUp() } }
            waitForIdle()
            onRoot().captureRoboImage(
                "../design/screens/Control-Device-AllFeatures-1024x768-Scrolled.png",
                roborazziOptions = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = CHANGE_THRESHOLD)),
            )
        }
    }

    private fun nodeMenu() = shot("Node-ParentMenu-${sizeTag()}") {
        com.nbaltas.nickos.ui.node.ParentMenu(onKidMode = {}, onAllApps = {}, onSettings = {}, onPairAnother = {}, onUpdate = {}, onReset = {}, onAllowCamera = {}, onAllowBrightness = {}, onAllowScreenOff = {}, onTalkNotifications = {})
    }

    @Config(qualifiers = "w960dp-h600dp-land-mdpi")
    @Test fun nodeParentMenu960() = nodeMenu()
    @Config(qualifiers = "w1024dp-h768dp-land-mdpi")
    @Test fun nodeParentMenu1024() = nodeMenu()

    // ---- see and control a kid tablet's screen ----
    private fun remoteMenu(shared: Boolean, control: Boolean) =
        shot("Node-ParentMenu-Remote-${if (shared) "Shared" else "Off"}-${if (control) "Control" else "NoControl"}-${sizeTag()}") {
            com.nbaltas.nickos.ui.node.ParentMenu(
                onKidMode = {}, onAllApps = {}, onSettings = {}, onPairAnother = {}, onUpdate = {}, onReset = {},
                onScreenShare = {}, screenShared = shared, onRemoteControl = {}, remoteControlOn = control,
            )
        }

    @Config(qualifiers = "w960dp-h600dp-land-mdpi")
    @Test fun nodeParentMenuRemoteOff960() = remoteMenu(shared = false, control = false)
    @Config(qualifiers = "w960dp-h600dp-land-mdpi")
    @Test fun nodeParentMenuRemoteOn960() = remoteMenu(shared = true, control = true)

    /** A stand-in for the kid tablet's screen: a portrait picture with a few blocks on it. */
    private fun kidScreen(): android.graphics.Bitmap {
        val b = android.graphics.Bitmap.createBitmap(600, 960, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(b)
        c.drawColor(0xFF1E2A44.toInt())
        val p = android.graphics.Paint().apply { color = 0xFFF2A65A.toInt() }
        for (i in 0 until 3) c.drawRect(60f, 80f + i * 260f, 540f, 280f + i * 260f, p)
        return b
    }

    private fun viewer(name: String, control: Boolean, frame: android.graphics.Bitmap? = kidScreen(), state: String = "live", lost: Boolean = false) =
        shot(name) {
            com.nbaltas.nickos.ui.master.ScreenViewer(frame, state, control, lost, {}, {}, {}, {}, { _, _ -> }, { _, _, _, _, _ -> })
        }

    @Test fun screenViewerViewOnly() = viewer("Screen-Viewer-ViewOnly", control = false)
    @Test fun screenViewerWithControl() = viewer("Screen-Viewer-Control", control = true)
    @Test fun screenViewerNeedsConsent() = viewer("Screen-Viewer-NeedsConsent", control = false, frame = null, state = "needs_consent")
    @Test fun screenViewerLost() = viewer("Screen-Viewer-Lost", control = false, lost = true)

    // ---- Tapo camera on the car network ----
    private fun tapo(id: String, name: String, host: String) =
        app.store.saveCamera(Camera(id, name, host = host, user = "nick", pass = "x"))

    private fun camerasTapoStates() {
        seedCams()
        cams(setOf(CH), mapOf(CH to camLive()))
        camPicture(CH, camBlue.first, camBlue.second)
        tapo("baby", "Baby cam", "192.168.49.23")
        tapo("nursery", "Nursery", "192.168.49.24")
        shot("Cameras-Tapo-States-${sizeTag()}") {
            CamerasScreen(
                {}, {}, live = false,
                rtspPreset = mapOf("baby" to com.nbaltas.nickos.cam.RtspNews.UNREACHABLE, "nursery" to com.nbaltas.nickos.cam.RtspNews.WRONG_PASSWORD),
            )
        }
    }

    @Config(qualifiers = "w960dp-h600dp-land-mdpi")
    @Test fun camerasTapoStates960() = camerasTapoStates()
    @Config(qualifiers = "w1024dp-h768dp-land-mdpi")
    @Test fun camerasTapoStates1024() = camerasTapoStates()

    private fun camerasAdd() {
        seedCams()
        shot("Cameras-Add-${sizeTag()}") {
            CamerasScreen({}, {}, live = false, startEditor = Camera(com.nbaltas.nickos.data.Store.randomHex(6), ""), foundPreset = listOf("192.168.49.23"))
        }
    }

    @Config(qualifiers = "w960dp-h600dp-land-mdpi")
    @Test fun camerasAdd960() = camerasAdd()
    @Config(qualifiers = "w1024dp-h768dp-land-mdpi")
    @Test fun camerasAdd1024() = camerasAdd()

    private fun camerasAddNotFound() {
        seedCams()
        shot("Cameras-Add-NotFound-${sizeTag()}") {
            CamerasScreen({}, {}, live = false, startEditor = Camera(com.nbaltas.nickos.data.Store.randomHex(6), ""), foundPreset = emptyList())
        }
    }

    @Config(qualifiers = "w960dp-h600dp-land-mdpi")
    @Test fun camerasAddNotFound960() = camerasAddNotFound()
    @Config(qualifiers = "w1024dp-h768dp-land-mdpi")
    @Test fun camerasAddNotFound1024() = camerasAddNotFound()

    // ---- the Node asks for brightness and screen off by itself ----
    private fun nodePermissionCard() {
        nodeSeed()
        shot("Node-PermissionCard-${sizeTag()}") {
            kidHome()
            com.nbaltas.nickos.ui.node.PermissionCard(onAllow = {}, onNotNow = {})
        }
    }

    @Config(qualifiers = "w960dp-h600dp-land-mdpi")
    @Test fun nodePermissionCard960() = nodePermissionCard()
    @Config(qualifiers = "w1024dp-h768dp-land-mdpi")
    @Test fun nodePermissionCard1024() = nodePermissionCard()

    /** After the parent taps a greyed Brightness or Off: the Master says it asked and what to do next. */
    private fun deviceTabAsked() {
        set(app.master, "_status", mapOf("aaaaaaaaaaaa" to status(82, fg = "org.jellyfin.mobile", fgLabel = "Jellyfin").copy(brightnessOk = false, screenOffOk = false)))
        set(app.master, "_asked", mapOf("aaaaaaaaaaaa" to FIXED_NOW))
        shot("Control-Device-Asked-${sizeTag()}") { ControlScreen("aaaaaaaaaaaa", TAB_DEVICE, {}, {}, {}) }
    }

    @Config(qualifiers = "w960dp-h600dp-land-mdpi")
    @Test fun controlDeviceAsked960() = deviceTabAsked()
    @Config(qualifiers = "w1024dp-h768dp-land-mdpi")
    @Test fun controlDeviceAsked1024() = deviceTabAsked()

    private fun carNetRevealed() {
        com.nbaltas.nickos.ui.master.carNetStartRevealed = true
        try {
            shot("Settings-Tablets-CarNet-Revealed-${sizeTag()}") { SettingsScreen(0, {}, {}, {}) }
        } finally {
            com.nbaltas.nickos.ui.master.carNetStartRevealed = false
        }
    }

    @Config(qualifiers = "w960dp-h600dp-land-mdpi")
    @Test fun carNetRevealed960() = carNetRevealed()
    @Config(qualifiers = "w1024dp-h768dp-land-mdpi")
    @Test fun carNetRevealed1024() = carNetRevealed()
}

