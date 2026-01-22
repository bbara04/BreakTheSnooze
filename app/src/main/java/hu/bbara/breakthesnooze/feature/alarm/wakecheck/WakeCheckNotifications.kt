package hu.bbara.breakthesnooze.feature.alarm.wakecheck

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import hu.bbara.breakthesnooze.R

object WakeCheckNotifications {
    private const val CHANNEL_ID = "wake_check_channel"
    private const val NOTIFICATION_ID_BASE = 20_000

    fun showWakeCheck(context: Context, payload: WakeCheckPayload) {
        ensureChannel(context)
        val notification = buildNotification(context, payload)
        NotificationManagerCompat.from(context).notify(notificationId(payload.alarmId), notification)
    }

    fun dismiss(context: Context, alarmId: Int) {
        NotificationManagerCompat.from(context).cancel(notificationId(alarmId))
    }

    private fun buildNotification(context: Context, payload: WakeCheckPayload): android.app.Notification {
        val ackIntent = Intent(context, WakeCheckReceiver::class.java).apply {
            action = WakeCheckIntents.ACTION_ACK_WAKE_CHECK
            putExtra(WakeCheckIntents.EXTRA_ALARM_ID, payload.alarmId)
        }
        val ackPendingIntent = PendingIntent.getBroadcast(
            context,
            payload.alarmId,
            ackIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val label = payload.label.ifBlank { context.getString(R.string.alarm_label_default) }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(context.getString(R.string.wake_check_title))
            .setContentText(context.getString(R.string.wake_check_body, label))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(ackPendingIntent)
            .addAction(
                0,
                context.getString(R.string.wake_check_confirm),
                ackPendingIntent
            )
            .build()
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = ContextCompat.getSystemService(context, NotificationManager::class.java) ?: return
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.wake_check_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.wake_check_channel_description)
            enableVibration(true)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    private fun notificationId(alarmId: Int): Int = NOTIFICATION_ID_BASE + alarmId
}
