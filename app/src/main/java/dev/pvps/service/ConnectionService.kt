package dev.pvps.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.pvps.MainActivity
import dev.pvps.R

/**
 * Keeps the process alive while SSH sessions are active. The actual SSH
 * connection manager lives in the application container; this service owns
 * the foreground notification and is the anchor Android requires for
 * long-lived sockets.
 */
class ConnectionService : Service() {

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.connection_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.connection_channel_desc)
            setShowBadge(false)
            // The update notification carries a vault-derived host label. Keep
            // it off the lock screen; the ongoing private notification still
            // shows the label when the device is unlocked.
            setLockscreenVisibility(NotificationCompat.VISIBILITY_SECRET)
        }
        manager.createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // SSH is a long-lived network session, not a Bluetooth/USB "connected
        // device". specialUse is the only FGS type Android allows here without
        // requesting unrelated runtime permissions (the old connectedDevice
        // declaration crashed with SecurityException on Android 14+).
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
        )
        // If Android kills the process, the SSH sessions are gone too. Restarting
        // the service without them would only show a misleading notification.
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(text: String = "SSH session active"): Notification {
        val pending = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_pvps)
            .setColor(0xFF8BFFD1.toInt())
            .setContentTitle("PVPS")
            .setContentText(text)
            .setContentIntent(pending)
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            // Connection status may reveal vault host labels; keep it secret on
            // the lock screen and provide no public replacement.
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "ssh_connection"
        const val NOTIFICATION_ID = 1001

        fun start(context: android.content.Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ConnectionService::class.java)
            )
        }

        fun update(context: android.content.Context, text: String) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val pending = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_pvps)
                .setColor(0xFFD1FA76.toInt())
                .setContentTitle("PVPS")
                .setContentText(text)
                .setContentIntent(pending)
                .setOngoing(true)
                .setSilent(true)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                // Host label stays in this private notification only; no public
                // version is attached, and lock-screen rendering is suppressed.
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .build()
            runCatching { manager.notify(NOTIFICATION_ID, notification) }
        }

        fun stop(context: android.content.Context) {
            context.stopService(Intent(context, ConnectionService::class.java))
        }
    }
}
