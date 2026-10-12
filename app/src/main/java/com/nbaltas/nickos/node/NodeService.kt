package com.nbaltas.nickos.node

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import com.nbaltas.nickos.App
import com.nbaltas.nickos.R
import com.nbaltas.nickos.data.Role

/** Keeps the Node reachable while the kid is in another app or the screen is off. */
class NodeService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    private var notification: Notification? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "NickOS link", NotificationManager.IMPORTANCE_MIN))
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("NickOS")
            .setContentText("Linked to the parent tablet")
            .setOngoing(true)
            .build()
        notification = n
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        else startForeground(1, n)
        instance = this
        App.get(this).node.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    /**
     * Adds the camera type to this foreground service while the tablet camera streams, and drops
     * it afterwards. Same notification, so nothing changes on the kid's screen. False when
     * Android refuses (the app is in the background with no exemption): the camera must not open.
     */
    fun cameraType(on: Boolean): Boolean {
        // The camera type arrived with Android 11; before that the plain service may open the camera.
        if (Build.VERSION.SDK_INT < 30) return true
        val n = notification ?: return false
        val type = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or (if (on) ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA else 0)
        return try { startForeground(1, n, type); true } catch (e: Exception) { Log.w("NickOS.Node", "camera service type refused", e); false }
    }

    companion object {
        private const val CHANNEL = "link"

        /** The running service, for [camera]. */
        @Volatile private var instance: NodeService? = null

        /** See [cameraType]. False when the service is not running. */
        fun camera(on: Boolean): Boolean = instance?.cameraType(on) ?: false

        fun start(ctx: Context) = ContextCompat.startForegroundService(ctx, Intent(ctx, NodeService::class.java))

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, NodeService::class.java))
            App.get(ctx).node.stop()
        }
    }
}

/** Starts the link again after a reboot, so a Node is reachable before anyone opens it. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (App.get(ctx).store.role.value != Role.NODE) return
        NodeService.start(ctx)
        // After a self-update the kiosk task died with the old process: put the kid home back.
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            ctx.startActivity(Intent(ctx, com.nbaltas.nickos.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
