package hu.bbara.viewideas.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import hu.bbara.viewideas.R
import hu.bbara.viewideas.ui.alarm.AlarmRingingActivity
import hu.bbara.viewideas.ui.alarm.AlarmUiModel

object AlarmNotifications {
    private const val CHANNEL_ID = "alarm_channel"
    private const val NOTIFICATION_ID_BASE = 10_000

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val notificationManager = ContextCompat.getSystemService(context, NotificationManager::class.java)
            ?: return
        val existing = notificationManager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.alarm_notification_title),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.alarm_notification_content)
            enableVibration(true)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), audioAttributes)
        }
        notificationManager.createNotificationChannel(channel)
    }

    fun notificationId(alarmId: Int): Int = NOTIFICATION_ID_BASE + alarmId

    fun buildNotification(context: Context, alarm: AlarmUiModel): Notification {
        ensureChannel(context)
        val label = alarm.label.ifBlank { context.getString(R.string.alarm_label_default) }
        val smallIcon = android.R.drawable.ic_lock_idle_alarm

        val fullScreenIntent = PendingIntent.getActivity(
            context,
            alarm.id,
            AlarmRingingActivity.createIntent(context, alarm.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            context,
            alarm.id,
            Intent(context, AlarmRingtoneService::class.java).apply {
                action = AlarmIntents.ACTION_STOP_ALARM
                putExtra(AlarmIntents.EXTRA_ALARM_ID, alarm.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val defaultSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(smallIcon)
            .setContentTitle(label)
            .setContentText(context.getString(R.string.alarm_notification_content))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setSound(defaultSound)
            .setFullScreenIntent(fullScreenIntent, true)
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.alarm_ringing_message)))
            .addAction(smallIcon, context.getString(R.string.alarm_stop), stopIntent)
            .setDeleteIntent(stopIntent)
            .build()
    }
}
