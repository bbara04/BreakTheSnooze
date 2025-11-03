package hu.bbara.viewideas.wear

import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService

class PhoneMessageListenerService : WearableListenerService() {

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        OnBodyStatusMonitor.ensureInitialized(applicationContext)
        createNotificationChannel()
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d(TAG, "onMessageReceived path=${messageEvent.path} size=${messageEvent.data?.size ?: 0}")
        when (messageEvent.path) {
            MESSAGE_PATH -> {
                val alarmId = messageEvent.data?.decodeToString()?.toIntOrNull() ?: -1
                if (shouldHandleAlarmOnWatch()) {
                    showOverlay(alarmId)
                } else {
                    Log.i(TAG, "Skipping watch alarm presentation; watch not detected as being worn")
                }
            }
            ON_BODY_QUERY_PATH -> respondOnBodyStatus(messageEvent)
            else -> Log.w(TAG, "Ignoring message for unexpected path=${messageEvent.path}")
        }
    }

    private fun respondOnBodyStatus(messageEvent: MessageEvent) {
        OnBodyStatusMonitor.ensureInitialized(applicationContext)
        val cachedState = OnBodyStatusMonitor.getOnBodyState()
        val state = cachedState ?: OnBodyStatusMonitor.tryReadImmediateState(applicationContext)
        val payload = when (state) {
            true -> "1"
            false -> "0"
            null -> "-1"
        }.toByteArray(Charsets.UTF_8)
        Wearable.getMessageClient(this)
            .sendMessage(messageEvent.sourceNodeId, ON_BODY_RESPONSE_PATH, payload)
            .addOnSuccessListener { Log.d(TAG, "Sent on-body response state=$state") }
            .addOnFailureListener { error ->
                Log.w(TAG, "Failed to send on-body response", error)
            }
    }

    private fun shouldHandleAlarmOnWatch(): Boolean {
        val cachedState = OnBodyStatusMonitor.getOnBodyState()
        if (cachedState == false) {
            Log.d(TAG, "On-body monitor reports device is off wrist (cached)")
            return false
        }
        if (cachedState == null) {
            val immediateState = OnBodyStatusMonitor.tryReadImmediateState(applicationContext)
            if (immediateState == false) {
                Log.d(TAG, "Immediate on-body reading reports device off wrist")
                return false
            } else if (immediateState == null) {
                Log.d(TAG, "Immediate on-body reading unavailable; falling back to heuristics")
            }
        }

        val keyguardManager = getSystemService(KeyguardManager::class.java)
        if (keyguardManager?.isDeviceLocked == true || keyguardManager?.isKeyguardLocked == true) {
            Log.d(TAG, "Watch is locked; treating as not worn")
            return false
        }

        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val isCharging = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)?.let { it != 0 } ?: false
        if (isCharging) {
            Log.d(TAG, "Watch is charging; treating as not worn")
            return false
        }

        return true
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

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(getString(R.string.overlay_title))
            .setContentText(getString(R.string.overlay_message))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(false)
            .setOngoing(true)
            .setContentIntent(pendingIntent)

        val ongoingStatus = Status.forPart(Status.TextPart(getString(R.string.overlay_message)))

        OngoingActivity.Builder(this, NOTIFICATION_ID, notificationBuilder)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setStatus(ongoingStatus)
            .setStaticIcon(android.R.drawable.ic_lock_idle_alarm)
            .setTouchIntent(pendingIntent)
            .build()
            .apply(this)

        NotificationManagerCompat.from(this)
            .notify(NOTIFICATION_ID, notificationBuilder.build())

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
        const val ON_BODY_QUERY_PATH = "/viewideas/onbody-query"
        const val ON_BODY_RESPONSE_PATH = "/viewideas/onbody-response"
        private const val TAG = "WearPhoneListener"
        const val CHANNEL_ID = "wear_overlay_channel"
        const val NOTIFICATION_ID = 101
    }
}
