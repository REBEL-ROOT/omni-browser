package com.rebelroot.omni.sync.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.rebelroot.omni.MainActivity
import com.rebelroot.omni.R
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class SyncNotificationType {
    SYNC_SUCCESS,
    DEVICE_PAIRED,
    FIREFOX_SYNC,
    SYNC_ERROR
}

data class SyncEvent(
    val type: SyncNotificationType,
    val title: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

object SyncNotificationManager {
    const val CHANNEL_ID = "omni_sync_notifications"
    const val CHANNEL_NAME = "Omni Sync & Firefox Sync"
    private const val NOTIFICATION_ID_BASE = 87650

    private val _syncEvents = MutableSharedFlow<SyncEvent>(extraBufferCapacity = 32)
    val syncEvents: SharedFlow<SyncEvent> = _syncEvents.asSharedFlow()

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                importance
            ).apply {
                description = "Status and transfer updates for bookmarks, tabs, and device synchronization."
                setShowBadge(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    fun notifySyncSuccess(context: Context, peerName: String, bookmarkCount: Int, tabCount: Int) {
        val title = "Omni Sync: Synchronized"
        val message = buildString {
            append("Synced with $peerName (")
            val parts = mutableListOf<String>()
            if (bookmarkCount > 0) parts.add("$bookmarkCount bookmarks")
            if (tabCount > 0) parts.add("$tabCount tabs")
            if (parts.isEmpty()) parts.add("Up to date")
            append(parts.joinToString(", "))
            append(")")
        }

        _syncEvents.tryEmit(SyncEvent(SyncNotificationType.SYNC_SUCCESS, title, message))
        showNotification(context, NOTIFICATION_ID_BASE + 1, title, message)
    }

    fun notifyDevicePaired(context: Context, peerName: String) {
        val title = "Omni Sync: Device Linked"
        val message = "Successfully paired with $peerName via encrypted LAN."

        _syncEvents.tryEmit(SyncEvent(SyncNotificationType.DEVICE_PAIRED, title, message))
        showNotification(context, NOTIFICATION_ID_BASE + 2, title, message)
    }

    fun notifyFirefoxSync(context: Context, email: String, summary: String) {
        val title = "Firefox Sync Complete"
        val message = "Account: $email • $summary"

        _syncEvents.tryEmit(SyncEvent(SyncNotificationType.FIREFOX_SYNC, title, message))
        showNotification(context, NOTIFICATION_ID_BASE + 3, title, message)
    }

    fun notifySyncError(context: Context, error: String) {
        val title = "Omni Sync Notice"
        val message = error

        _syncEvents.tryEmit(SyncEvent(SyncNotificationType.SYNC_ERROR, title, message))
    }

    private fun showNotification(context: Context, id: Int, title: String, message: String) {
        try {
            createNotificationChannel(context)

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("NAVIGATE_TO", "omni_sync")
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_omni_logo_dark)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setColor(0xFF2563EB.toInt())
                .build()

            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Android 13+ POST_NOTIFICATIONS permission handled gracefully
        } catch (_: Exception) {}
    }
}
