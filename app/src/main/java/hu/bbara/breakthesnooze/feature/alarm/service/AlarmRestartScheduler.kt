package hu.bbara.breakthesnooze.feature.alarm.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.AlarmManagerCompat
import androidx.core.content.ContextCompat

class AlarmRestartScheduler(private val context: Context) {

    private val alarmManager: AlarmManager? = ContextCompat.getSystemService(context, AlarmManager::class.java)

    fun schedule(alarmId: Int) {
        val manager = alarmManager ?: return
        cancel(alarmId)
        val triggerAtMillis = System.currentTimeMillis() + RESTART_DELAY_MS
        pendingIntent(alarmId)?.let { pending ->
            AlarmManagerCompat.setExactAndAllowWhileIdle(
                manager,
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pending
            )
            Log.d(TAG, "Restart alarm scheduled for alarmId=$alarmId at $triggerAtMillis")
        }
    }

    fun cancel(alarmId: Int) {
        val manager = alarmManager ?: return
        pendingIntent(alarmId, PendingIntent.FLAG_NO_CREATE)?.let { pending ->
            manager.cancel(pending)
            pending.cancel()
            Log.d(TAG, "Restart alarm cancelled for alarmId=$alarmId")
        }
    }

    private fun pendingIntent(alarmId: Int, flag: Int = PendingIntent.FLAG_UPDATE_CURRENT): PendingIntent? {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmIntents.ACTION_ALARM_FIRED
            putExtra(AlarmIntents.EXTRA_ALARM_ID, alarmId)
        }
        val mergedFlags = flag or PendingIntent.FLAG_IMMUTABLE
        val requestCode = REQUEST_CODE_BASE + alarmId
        return PendingIntent.getBroadcast(context, requestCode, intent, mergedFlags)
    }

    companion object {
        private const val TAG = "AlarmRestartScheduler"
        private const val REQUEST_CODE_BASE = 20_000
        internal const val RESTART_DELAY_MS = 5 * 60 * 1000L
    }
}
