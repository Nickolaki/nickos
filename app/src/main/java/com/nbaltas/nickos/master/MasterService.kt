package com.nbaltas.nickos.master

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.nbaltas.nickos.App
import com.nbaltas.nickos.R
import com.nbaltas.nickos.data.Role

/**
 * Keeps the parent tablet's process alive and in the foreground, so the car network it hosts stays up while the
 * screen is off and the app is in the background. It owns nothing else: [MasterRuntime.start] creates and runs
 * the network. Started by the activity when the role becomes Master, and by the boot receiver.
 *
 * Type connectedDevice: it is not one of the Android 15 time-limited service types (dataSync would be).
 */
class MasterService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // Channels exist from Android 8; the compat builder ignores the channel id below that.
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "NickOS car network", NotificationManager.IMPORTANCE_MIN))
        }
        val n = NotificationCompat.Builder(this, CHANNEL)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(TITLE)
            .setContentText(TEXT)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        else startForeground(NOTIFICATION_ID, n)
        // A sticky restart after the tablet was reset to no role must not bring the network back (the foreground call
        // above has to come first: Android kills a service that was started as foreground and never says so).
        if (App.get(this).store.role.value != Role.MASTER) { stopSelf(); return }
        // After a reboot the process starts here with no screen: the runtime (and so the network) must start too.
        App.get(this).master.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    companion object {
        private const val CHANNEL = "carnet"
        private const val NOTIFICATION_ID = 2
        const val TITLE = "NickOS"
        const val TEXT = "Hosting the car network"

        /** False when Android refuses a foreground start from where we are (a background context). Never throws. */
        fun start(ctx: Context): Boolean = try {
            ContextCompat.startForegroundService(ctx, Intent(ctx, MasterService::class.java)); true
        } catch (e: Exception) { Log.w("NickOS.Master", "master service not started: ${e.javaClass.simpleName}"); false }

        fun stop(ctx: Context) { try { ctx.stopService(Intent(ctx, MasterService::class.java)) } catch (_: Exception) {} }
    }
}
