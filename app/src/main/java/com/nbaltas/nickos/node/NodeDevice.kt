package com.nbaltas.nickos.node

import android.app.ActivityManager
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Base64
import android.util.Log
import android.view.KeyEvent
import androidx.core.graphics.drawable.toBitmap
import com.nbaltas.nickos.EntrySwitch
import com.nbaltas.nickos.MainActivity
import com.nbaltas.nickos.entryIntent
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

/** Needed for Device Owner. Set by the QR setup or once over adb: see SETUP.md. */
class AdminReceiver : DeviceAdminReceiver() {
    /** Pre-Android-12 QR setup finishes here: remember the role and open NickOS's own setup. */
    override fun onProfileProvisioningComplete(ctx: Context, intent: Intent) {
        @Suppress("DEPRECATION")
        Provisioning.remember(ctx, intent.getParcelableExtra(DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE))
        EntrySwitch.applyStored(ctx)
        NodeDevice.makeHome(ctx)
        ctx.startActivity(entryIntent(ctx).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

data class InstalledApp(val pkg: String, val label: String)

/** Everything a Node can do to its own tablet. */
class NodeDevice(private val ctx: Context) {
    private val dpm = ctx.getSystemService(DevicePolicyManager::class.java)
    private val audio = ctx.getSystemService(AudioManager::class.java)
    private val power = ctx.getSystemService(PowerManager::class.java)
    private val admin = ComponentName(ctx, AdminReceiver::class.java)

    /** Package most recently launched by NickOS, the fallback when usage access isn't granted. */
    @Volatile var lastLaunched: String? = null

    val isDeviceOwner get() = dpm.isDeviceOwnerApp(ctx.packageName)
    /** Device admin active (granted once on the Node, or implied by Device Owner): needed to turn the screen off. */
    val isAdmin get() = dpm.isAdminActive(admin)

    /** WRITE_SETTINGS granted: how a normally installed Node may change the brightness. */
    fun canWriteSettings() = Build.VERSION.SDK_INT < 23 || Settings.System.canWrite(ctx)

    /** Can [setBrightness] actually change anything here? The Master greys the slider out when this is false. */
    val brightnessOk get() = ControlAccess.brightness(isDeviceOwner, Build.VERSION.SDK_INT, canWriteSettings())

    /** Can [screenOff] actually turn the screen off here? */
    val screenOffOk get() = ControlAccess.screenOff(isAdmin)

    /** Opens Android's "modify system settings" page for NickOS: the one-time OK that lets the Master change brightness. */
    fun writeSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, android.net.Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Opens Android's "activate device admin" prompt: the one-time OK that lets the Master turn this screen off. */
    fun deviceAdminIntent(): Intent =
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
            .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "NickOS uses this only so the parent's tablet can turn this screen off.")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    // ---- battery / screen ----
    fun battery(): Pair<Int, Boolean> {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        return (if (level < 0) 0 else level * 100 / scale) to charging
    }

    /** Battery temperature in tenths of a degree C, or -1 when the tablet does not say. */
    fun batteryTemperatureTenths(): Int =
        ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1

    /** The notification permission Android 13+ asks for (before that there is nothing to ask). A Device Owner is granted it by policy. */
    val notificationsOk get() = Build.VERSION.SDK_INT < 33 || ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun hasCameraPermission() =ctx.checkSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    val screenOn get() = power.isInteractive

    fun screenOff() {
        if (isAdmin) try { dpm.lockNow() } catch (e: Exception) { Log.w(TAG, "lockNow", e) }
    }

    fun wake() = com.nbaltas.nickos.power.ScreenWake.wake(ctx)

    // ---- volume / brightness (0-100) ----
    fun volume(): Int {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return (audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f / max).roundToInt()
    }

    fun setVolume(pct: Int) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (pct.coerceIn(0, 100) * max / 100f).roundToInt(), AudioManager.FLAG_SHOW_UI)
    }

    /** Reach the integrated player's media session when Jellyfin remote commands are ignored. */
    fun mediaKey(key: String) {
        val code = when (key) {
            "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE
            "play" -> KeyEvent.KEYCODE_MEDIA_PLAY
            "stop" -> KeyEvent.KEYCODE_MEDIA_STOP
            "rewind" -> KeyEvent.KEYCODE_MEDIA_REWIND
            "forward" -> KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
            else -> return
        }
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    // ---- ring a lost tablet ----
    private var player: android.media.MediaPlayer? = null

    fun alarmVolume(): Int = audio.getStreamVolume(AudioManager.STREAM_ALARM)

    fun setAlarmVolume(v: Int): Boolean {
        return try {
            audio.setStreamVolume(AudioManager.STREAM_ALARM, v.coerceIn(0, audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)), 0)
            (alarmVolume() == v).also { if (!it) Log.w(TAG, "alarm volume restoration not confirmed") }
        } catch (e: Exception) { Log.w(TAG, "alarm volume", e); false }
    }

    /** Alarm volume to the top and the default alarm sound looping. USAGE_ALARM keeps it loud with the media volume muted. */
    fun startAlarm(raiseVolume: Boolean = true) {
        if (raiseVolume) setAlarmVolume(audio.getStreamMaxVolume(AudioManager.STREAM_ALARM))
        // MediaPlayer on every version: Ringtone.isLooping only exists from API 28, and the app supports 24.
        val mp = android.media.MediaPlayer()
        try {
            val uri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
                ?: android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE)
            mp.setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            mp.setDataSource(ctx, uri)
            mp.isLooping = true
            mp.prepare()
            mp.start()
            player = mp
        } catch (e: Exception) {
            Log.w(TAG, "alarm sound", e)
            try { mp.release() } catch (_: Exception) {}
        }
    }

    fun stopAlarm() {
        player?.let {
            try { it.stop() } catch (e: Exception) { Log.w(TAG, "alarm stop", e) }
            try { it.release() } catch (_: Exception) {}
        }
        player = null
    }

    fun brightness(): Int = try {
        (Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS) * 100f / 255f).roundToInt()
    } catch (e: Exception) { 50 }

    fun setBrightness(pct: Int) {
        val v = (pct.coerceIn(1, 100) * 255f / 100f).roundToInt().toString()
        try {
            if (isDeviceOwner && Build.VERSION.SDK_INT >= 28) {
                dpm.setSystemSetting(admin, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL.toString())
                dpm.setSystemSetting(admin, Settings.System.SCREEN_BRIGHTNESS, v)
            } else if (Settings.System.canWrite(ctx)) {
                Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, v.toInt())
            }
        } catch (e: Exception) { Log.w(TAG, "brightness", e) }
    }

    // ---- apps ----
    fun installedApps(): List<InstalledApp> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.pkg != ctx.packageName }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
    }

    fun label(pkg: String): String? = try {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) { null }

    fun iconBase64(pkg: String, px: Int = 96): String? = try {
        val bmp = ctx.packageManager.getApplicationIcon(pkg).toBitmap(px, px, Bitmap.Config.ARGB_8888)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    } catch (e: Exception) { null }

    fun launch(pkg: String): Boolean {
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        lastLaunched = pkg
        return try { ctx.startActivity(i); true } catch (e: Exception) { Log.w(TAG, "launch $pkg", e); false }
    }

    /**
     * Opens Android's accessibility settings, where a grown-up switches on NickOS remote control once. A Device Owner tablet
     * first lets the service through when it limits accessibility services (the limit stays as it was otherwise). The switch
     * itself is still a tap in Settings: Android lets no app turn an accessibility service on for itself.
     * On Android 13 and up an APK that was sideloaded (not installed over adb or by NickOS's own updater) has the switch
     * greyed out until "Allow restricted settings" is chosen in the app's info page.
     */
    fun remoteInputIntent(): Intent {
        if (isDeviceOwner) try {
            val permitted = dpm.getPermittedAccessibilityServices(admin)
            // null means every service is allowed already.
            if (permitted != null && ctx.packageName !in permitted) dpm.setPermittedAccessibilityServices(admin, permitted + ctx.packageName)
        } catch (e: Exception) { Log.w(TAG, "accessibility allow list", e) }
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /**
     * The most specific accessibility page: on Android 13+ the details page of the NickOS service, if some activity answers
     * it, else the general accessibility settings.
     */
    fun remoteInputPageIntent(): Intent {
        val general = remoteInputIntent()
        if (Build.VERSION.SDK_INT < 33) return general
        val details = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS") // Settings.ACTION_ACCESSIBILITY_DETAILS_SETTINGS, API 33
            .putExtra(Intent.EXTRA_COMPONENT_NAME, ComponentName(ctx, RemoteInputService::class.java).flattenToString())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (details.resolveActivity(ctx.packageManager) != null) details else general
    }

    fun goHome() {
        lastLaunched = null
        ctx.startActivity(
            entryIntent(ctx)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
    }

    /**
     * Home, stop Jellyfin, reopen. killBackgroundProcesses can't stop it while its playback service
     * runs, but hiding an app stops all its processes, so the kid lock (Device Owner) uses that.
     */
    fun restart(pkg: String) {
        goHome()
        Thread {
            Thread.sleep(800)
            if (isDeviceOwner) try {
                dpm.setApplicationHidden(admin, pkg, true)
                dpm.setApplicationHidden(admin, pkg, false)
            } catch (e: Exception) { Log.w(TAG, "restart $pkg", e) }
            else ctx.getSystemService(ActivityManager::class.java).killBackgroundProcesses(pkg)
            Thread.sleep(1200)
            launch(pkg)
        }.start()
    }

    fun jellyfinPackage(): String? = JELLYFIN_PACKAGES.firstOrNull { label(it) != null }

    private var usageCursor = 0L
    private var usageLast: String? = null

    /**
     * Foreground package, via usage access when granted (SETUP.md), else what NickOS last opened.
     * Reads usage events incrementally: 6h back the first time, then only what's new.
     */
    @Synchronized
    fun foregroundPackage(homeVisible: Boolean): String? {
        if (homeVisible) return null
        if (hasUsageAccess()) {
            val usm = ctx.getSystemService(UsageStatsManager::class.java)
            val now = System.currentTimeMillis()
            val from = if (usageCursor == 0L) now - 6 * 60 * 60 * 1000L else usageCursor - 2000
            val events = usm.queryEvents(from, now)
            val e = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) usageLast = e.packageName
            }
            usageCursor = now
            val last = usageLast
            if (last != null) return if (last == ctx.packageName || isLauncher(last)) null else last
        }
        return lastLaunched
    }

    private fun isLauncher(pkg: String): Boolean {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return ctx.packageManager.queryIntentActivities(i, 0).any { it.activityInfo.packageName == pkg }
    }

    fun hasUsageAccess(): Boolean {
        val ops = ctx.getSystemService(android.app.AppOpsManager::class.java)
        @Suppress("DEPRECATION")
        val mode = ops.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), ctx.packageName)
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    fun canOverlay() = Settings.canDrawOverlays(ctx)

    // ---- launcher mode (a tablet that is not Device Owner) ----
    /** Whether NickOS is the home screen. The resolver's own package (`android`) means no default is set. */
    fun isHome(): Boolean {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return ctx.packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == ctx.packageName
    }

    /** Asks for the home role: the system dialog on Android 10+, else the Home app settings page. */
    fun homeRequestIntent(): Intent =
        com.nbaltas.nickos.homeRequestIntent(ctx) ?: Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** The notification settings for the "Parent talking" channel (the app's notification page before Android 8). */
    fun talkChannelSettingsIntent(): Intent = TalkNotice.channelSettingsIntent(ctx)

    fun overlayIntent() = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${ctx.packageName}"))

    fun usageIntent() = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    fun adminIntent() = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
        .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Lets NickOS turn the screen off at bedtime.")

    fun kioskState() = KioskState(isDeviceOwner, isHome(), canOverlay(), hasUsageAccess(), isAdmin)

    // ---- lockdown ----
    /**
     * Kiosk mode, the same Android feature Fully Kiosk Plus uses. Only works as Device Owner.
     * [packages] are the apps the kid may enter; everything else (home, recents, settings,
     * notifications) is blocked while the Node activity runs in lock task.
     */
    fun applyLockdown(packages: List<String>) {
        if (!isDeviceOwner) return
        try {
            dpm.setLockTaskPackages(admin, (listOf(ctx.packageName) + packages).distinct().toTypedArray())
            if (Build.VERSION.SDK_INT >= 28) dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_NONE)
            makeHome(ctx)
            dpm.setKeyguardDisabled(admin, true)
            if (Build.VERSION.SDK_INT >= 33) dpm.setPermissionGrantState(
                admin, ctx.packageName, android.Manifest.permission.POST_NOTIFICATIONS, DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED,
            )
            // Watch together reads the tablet's video files (and writes them, before Android 10).
            LocalVideos.permissions().forEach {
                dpm.setPermissionGrantState(admin, ctx.packageName, it, DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED)
            }
            // Tablet cameras. Android 12+ can refuse this (sensor permissions opted out at setup, some
            // makers' builds), so the answer is never assumed: hasCameraPermission() is what counts.
            if (!hasCameraPermission() && !dpm.setPermissionGrantState(
                    admin, ctx.packageName, android.Manifest.permission.CAMERA, DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED,
                )
            ) Log.w(TAG, "device policy did not grant the camera permission")
            dpm.setStatusBarDisabled(admin, true)
        } catch (e: Exception) { Log.w(TAG, "lockdown", e) }
    }

    /** Undo everything [applyLockdown] did and give up Device Owner, so the tablet is normal again. */
    /**
     * Parent mode: end lock task for every task. stopLockTask() only releases the caller's task, so
     * Jellyfin (or any app the kid opened) kept the tablet locked. Kid mode re-applies the allowlist.
     */
    fun pauseLockdown() {
        if (!isDeviceOwner) return
        try { dpm.setLockTaskPackages(admin, arrayOf()) } catch (e: Exception) { Log.w(TAG, "pause lockdown", e) }
        // Leaving lock task clears the real status bar flags; unset ours too so the next disable is not seen as a no-op.
        try { dpm.setStatusBarDisabled(admin, false) } catch (e: Exception) { Log.w(TAG, "pause status bar", e) }
    }

    /** Re-asserts the status bar disable, for after startLockTask() which resets the real flags. */
    fun disableStatusBar() {
        if (!isDeviceOwner) return
        try { dpm.setStatusBarDisabled(admin, true) } catch (e: Exception) { Log.w(TAG, "status bar", e) }
    }

    /**
     * Best effort: switch the remote control accessibility service back on after an update dropped it. Appends to the
     * enabled list and keeps every other entry. Needs WRITE_SECURE_SETTINGS (or an old Android's device policy); never throws.
     * Only called on a tablet where a grown-up turned remote control on before. False when Android refused it (SecurityException):
     * the caller remembers that and stops trying.
     */
    fun reenableRemoteInput(): Boolean {
        try {
            val flat = KioskLock.serviceName(ctx.packageName, RemoteInputService::class.java.name)
            val cr = ctx.contentResolver
            val next = KioskLock.mergeAccessibility(Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), flat)
            if (isDeviceOwner && Build.VERSION.SDK_INT < 28) {
                dpm.setSecureSetting(admin, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, next)
                dpm.setSecureSetting(admin, Settings.Secure.ACCESSIBILITY_ENABLED, "1")
            } else {
                Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, next)
                Settings.Secure.putString(cr, Settings.Secure.ACCESSIBILITY_ENABLED, "1")
            }
        } catch (e: SecurityException) { Log.d(TAG, "re-enabling remote control is not allowed here", e); return false
        } catch (e: Throwable) { Log.d(TAG, "could not re-enable remote control", e) }
        return true
    }

    /** True while no lock task is running (API 23+; older Android has no way to ask, so it counts as running). */
    fun lockTaskNone(): Boolean =
        Build.VERSION.SDK_INT >= 23 && ctx.getSystemService(ActivityManager::class.java).lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE

    fun releaseLockdown() {
        if (!isDeviceOwner) {
            // Launcher mode: a plain device admin would block uninstalling NickOS after a reset.
            if (isAdmin) try { dpm.removeActiveAdmin(admin) } catch (e: Exception) { Log.w(TAG, "remove admin", e) }
            return
        }
        try {
            dpm.setStatusBarDisabled(admin, false)
            dpm.setKeyguardDisabled(admin, false)
            dpm.clearPackagePersistentPreferredActivities(admin, ctx.packageName)
            dpm.setLockTaskPackages(admin, arrayOf())
            @Suppress("DEPRECATION")
            dpm.clearDeviceOwnerApp(ctx.packageName)
        } catch (e: Exception) { Log.w(TAG, "release", e) }
    }

    fun deviceNames(): List<String> = listOfNotNull(
        Settings.Global.getString(ctx.contentResolver, Settings.Global.DEVICE_NAME),
        Build.MODEL,
    ).distinct()

    companion object {
        private const val TAG = "NickOS.Device"

        /** The HOME + DEFAULT filter the persistent preferred activity is registered for. */
        fun homeFilter() = IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME); addCategory(Intent.CATEGORY_DEFAULT)
        }

        /**
         * Device Owner only: make NickOS the persistent home screen. Never throws, because a crash during the
         * provisioning wizard fails the whole setup with "Can't set up device". Returns whether it was set.
         * Always [MainActivity], the component an updated kid tablet already has as its preference.
         */
        fun makeHome(ctx: Context): Boolean = try {
            val dpm = ctx.getSystemService(DevicePolicyManager::class.java)
            if (dpm.isDeviceOwnerApp(ctx.packageName)) {
                dpm.addPersistentPreferredActivity(ComponentName(ctx, AdminReceiver::class.java), homeFilter(), ComponentName(ctx, MainActivity::class.java))
                Log.i(TAG, "home preference set")
                true
            } else false
        } catch (e: Throwable) { Log.w(TAG, "make home", e); false }
        val JELLYFIN_PACKAGES = listOf("org.jellyfin.mobile", "org.jellyfin.androidtv")
    }
}
