package hu.bbara.viewideas.wear

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class PhoneMessageListenerService : WearableListenerService() {

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        createNotificationChannel()
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d(TAG, "onMessageReceived path=${messageEvent.path} size=${messageEvent.data?.size ?: 0}")
        if (messageEvent.path == MESSAGE_PATH) {
            val alarmId = messageEvent.data?.decodeToString()?.toIntOrNull() ?: -1
            showOverlay(alarmId)
        } else {
            Log.w(TAG, "Ignoring message for unexpected path=${messageEvent.path}")
        }
    }

    private fun showOverlay(alarmId: Int) {
        val overlayIntent = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_SHOW_OVERLAY
            putExtra(MainActivity.EXTRA_ALARM_ID, alarmId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            overlayIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(getString(R.string.overlay_title))
            .setContentText(getString(R.string.overlay_message))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(true)
            .setOngoing(true)
            .build()

        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)

        kotlin.runCatching { startActivity(overlayIntent) }
            .onSuccess { Log.d(TAG, "Started MainActivity from message") }
            .onFailure { Log.w(TAG, "Failed to start MainActivity", it) }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.overlay_title),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.overlay_message)
                enableVibration(true)
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        super.onDestroy()
    }

    companion object {
        const val MESSAGE_PATH = "/viewideas/alarm-started"
        private const val TAG = "WearPhoneListener"
        const val CHANNEL_ID = "wear_overlay_channel"
        const val NOTIFICATION_ID = 101
    }
}
