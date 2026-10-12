package com.nbaltas.nickos.update

import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import com.nbaltas.nickos.BuildConfig
import com.nbaltas.nickos.data.optStr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class UpdateInfo(val versionCode: Int, val versionName: String, val notes: String?, val apkUrl: String, val sha256: String?, val signature: String? = null) {
    val newer get() = versionCode > BuildConfig.VERSION_CODE
}

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val pct: Int) : UpdateState
    data class Installing(val info: UpdateInfo) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * NickOS updates from nickflix-config's public /nickos track. Kid tablets with the kid lock
 * (Device Owner) install silently; everywhere else Android shows its own Install prompt.
 */
object Updater {
    private const val TAG = "NickOS.Update"
    private val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state

    val versionUrl get() = "${BuildConfig.UPDATE_BASE}/nickos/version"

    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        _state.value = UpdateState.Checking
        try {
            val o = http.newCall(Request.Builder().url(versionUrl).build()).execute().use { r ->
                if (!r.isSuccessful) throw Exception("Update server answered ${r.code}")
                JSONObject(r.body!!.string())
            }
            val url = o.optStr("apkUrl")
            val info = if (url == null) null else UpdateInfo(o.optInt("versionCode"), o.optString("versionName"), o.optStr("notes"), url, o.optStr("sha256"), o.optStr("signature"))
            _state.value = if (info != null && info.newer) UpdateState.Available(info) else UpdateState.UpToDate
            info
        } catch (e: Exception) {
            Log.w(TAG, "check", e)
            _state.value = UpdateState.Failed("Can't reach the update server")
            null
        }
    }

    /** Checks, then downloads + installs if there's something newer. Used by the Master's "Update" on Nodes. */
    suspend fun updateIfNewer(ctx: Context) {
        val info = check() ?: return
        if (info.newer) install(ctx, info)
    }

    suspend fun install(ctx: Context, info: UpdateInfo) = withContext(Dispatchers.IO) {
        try {
            _state.value = UpdateState.Downloading(info, 0)
            val file = File(ctx.cacheDir, "nickos-update.apk")
            val md = MessageDigest.getInstance("SHA-256")
            http.newCall(Request.Builder().url(info.apkUrl).build()).execute().use { r ->
                if (!r.isSuccessful) throw Exception("Download failed (${r.code})")
                val body = r.body!!
                val total = body.contentLength()
                body.byteStream().use { input ->
                    file.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n); md.update(buf, 0, n); done += n
                            if (total > 0) _state.value = UpdateState.Downloading(info, (done * 100 / total).toInt())
                        }
                    }
                }
            }
            val hex = md.digest().joinToString("") { "%02x".format(it) }
            if (info.sha256 != null && !hex.equals(info.sha256, ignoreCase = true)) throw Exception("Download was corrupted. Try again.")
            _state.value = UpdateState.Installing(info)
            commit(ctx, file)
        } catch (e: Exception) {
            Log.w(TAG, "install", e)
            _state.value = UpdateState.Failed(e.message ?: "Update failed")
        }
    }

    private fun commit(ctx: Context, apk: File) {
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(ctx.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { s ->
            s.openWrite("nickos.apk", 0, apk.length()).use { out -> apk.inputStream().use { it.copyTo(out) }; s.fsync(out) }
            val intent = Intent(ctx, InstallReceiver::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            s.commit(PendingIntent.getBroadcast(ctx, id, intent, flags).intentSender)
        }
    }

    internal fun onResult(ctx: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // Not Device Owner: Android's own "Install?" screen, tapped by a person.
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirm?.let { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            PackageInstaller.STATUS_SUCCESS -> _state.value = UpdateState.Idle
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Log.w(TAG, "install status $status: $msg")
                _state.value = UpdateState.Failed(if (status == PackageInstaller.STATUS_FAILURE_ABORTED) "Update cancelled" else "Update failed")
            }
        }
    }

    fun isDeviceOwner(ctx: Context) = ctx.getSystemService(DevicePolicyManager::class.java).isDeviceOwnerApp(ctx.packageName)
}

class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) = Updater.onResult(ctx, intent)
}
