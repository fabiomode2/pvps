package dev.pvps.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.pvps.MainActivity
import dev.pvps.PvpsApp
import dev.pvps.R
import dev.pvps.data.model.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Keeps the process alive while SSH sessions are active. The actual SSH
 * connection manager lives in the application container; this service owns
 * the foreground notification, the wake lock that lets keepalives run while the
 * screen is off, and the network callback that triggers an immediate redial
 * after a Wi-Fi/LTE handoff.
 */
class ConnectionService : Service() {

    private val container get() = (application as PvpsApp).container
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var wakeLock: PowerManager.WakeLock? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

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

        scope.launch {
            container.settings.settings.collectLatest {
                updateWakeLock()
            }
        }
        scope.launch {
            container.ssh.states.collectLatest { publishStatus(it) }
        }
        registerNetworkCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // SSH is a long-lived network session, not a Bluetooth/USB "connected
        // device". specialUse is the only FGS type Android allows here without
        // requesting unrelated runtime permissions (the old connectedDevice
        // declaration crashed with SecurityException on Android 14+).
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification("SSH session active"),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
        )
        updateWakeLock()
        publishStatus(container.ssh.states.value)
        // If Android kills the process, the SSH sessions are gone too. Restarting
        // the service without them would only show a misleading notification.
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        unregisterNetworkCallback()
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    private fun publishStatus(states: Map<String, ConnectionState>) {
        val connected = states.values.count { it.status == ConnectionState.Status.CONNECTED }
        val reconnecting = states.values.count {
            it.status == ConnectionState.Status.RECONNECTING ||
                it.status == ConnectionState.Status.CONNECTING
        }
        val text = when {
            connected == 0 && reconnecting > 0 -> "Reconnecting to SSH…"
            connected == 0 -> "SSH session active"
            reconnecting > 0 -> "$connected connected · reconnecting…"
            connected == 1 -> "1 SSH link active"
            else -> "$connected SSH links active"
        }
        update(this, text)
    }

    @SuppressLint("WakelockTimeout")
    private fun updateWakeLock() {
        val wanted = container.settings.settings.value.keepConnectionAlive
        if (wanted) {
            if (wakeLock == null) {
                val power = getSystemService(PowerManager::class.java) ?: return
                runCatching {
                    wakeLock = power.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        "pvps:ssh-keepalive"
                    ).apply {
                        setReferenceCounted(false)
                        acquire()
                    }
                }
            }
        } else {
            releaseWakeLock()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { lock ->
            runCatching { if (lock.isHeld) lock.release() }
        }
        wakeLock = null
    }

    private fun registerNetworkCallback() {
        runCatching {
            val connectivity = getSystemService(ConnectivityManager::class.java)
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    // The default network changed (Wi-Fi -> LTE, VPN up, ...).
                    // Probe/redial immediately instead of waiting for the
                    // watchdog or a 45s backoff.
                    container.ssh.onNetworkAvailable()
                    container.openCode.onResume()
                }
            }
            connectivity.registerDefaultNetworkCallback(callback)
            networkCallback = callback
        }
    }

    private fun unregisterNetworkCallback() {
        val callback = networkCallback ?: return
        networkCallback = null
        runCatching {
            getSystemService(ConnectivityManager::class.java)
                .unregisterNetworkCallback(callback)
        }
    }

    private fun buildNotification(text: String = "SSH session active"): Notification =
        builder(text).build()

    private fun builder(text: String): NotificationCompat.Builder {
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
