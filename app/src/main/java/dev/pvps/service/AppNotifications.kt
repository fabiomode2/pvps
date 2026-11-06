package dev.pvps.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.pvps.MainActivity
import dev.pvps.R

/**
 * Lightweight notification hub for OpenCode events. The foreground SSH
 * notification intentionally stays quiet; this channel is what tells the user
 * that an agent finished a response, needs permission, or asked a question.
 */
object AppNotifications {

    const val EXTRA_SESSION_ID = "opencode_session"

    private const val CHANNEL_ID = "opencode_events"
    private const val GROUP = "dev.pvps.opencode"
    private var appContext: Context? = null

    @Volatile
    var appVisible: Boolean = false

    fun init(context: Context) {
        appContext = context.applicationContext
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "OpenCode activity",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Responses, questions and permission requests from OpenCode"
            setShowBadge(true)
            // Notification text is derived from remote OpenCode sessions. Treat
            // it as a lock-screen secret so the system does not render it while
            // the device is locked. This is independent of the in-app Device lock.
            setLockscreenVisibility(NotificationCompat.VISIBILITY_SECRET)
        }
        manager.createNotificationChannel(channel)
    }

    fun response(sessionId: String, sessionTitle: String, text: String) {
        if (appVisible) return
        notify(
            id = ("response:$sessionId").hashCode(),
            title = "OpenCode · ${sessionTitle.ifBlank { "Session" }}",
            text = text.lineSequence().firstOrNull { it.isNotBlank() }?.take(160)
                ?: "Response ready",
            sessionId = sessionId
        )
    }

    fun permission(sessionId: String, sessionTitle: String, action: String) {
        if (appVisible) return
        notify(
            id = ("permission:$sessionId").hashCode(),
            title = "Permission needed · ${sessionTitle.ifBlank { "OpenCode" }}",
            text = action.ifBlank { "OpenCode is waiting for your approval" },
            sessionId = sessionId
        )
    }

    fun form(sessionId: String, sessionTitle: String, title: String) {
        if (appVisible) return
        notify(
            id = ("form:$sessionId").hashCode(),
            title = "Question · ${sessionTitle.ifBlank { "OpenCode" }}",
            text = title.ifBlank { "OpenCode asked a question" },
            sessionId = sessionId
        )
    }

    private fun notify(id: Int, title: String, text: String, sessionId: String) {
        val context = appContext ?: return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_SESSION_ID, sessionId)
        }
        val pending = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_pvps)
            .setColor(0xFFD1FA76.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setGroup(GROUP)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            // Privacy boundary: remote session text is secret on the lock
            // screen, and no public version is provided to replace it.
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setPublicVersion(null)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }
}
