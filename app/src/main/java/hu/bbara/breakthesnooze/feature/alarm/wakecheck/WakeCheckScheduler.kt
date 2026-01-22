package hu.bbara.breakthesnooze.feature.alarm.wakecheck

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.AlarmManagerCompat
import androidx.core.content.ContextCompat

class WakeCheckScheduler(private val context: Context) {

    private val alarmManager: AlarmManager? = ContextCompat.getSystemService(context, AlarmManager::class.java)

    fun schedule(payload: WakeCheckPayload) {
        val manager = alarmManager ?: return
        cancelScheduledIntents(manager, payload.alarmId)
        WakeCheckStorage(context).save(payload)
        val triggerAt = System.currentTimeMillis() + WAKE_CHECK_DELAY_MS
        pendingIntentFor(WakeCheckIntents.ACTION_SHOW_WAKE_CHECK, payload.alarmId)?.let { pending ->
            Log.d(TAG, "Wake-check scheduled for alarmId=${payload.alarmId} in ${WAKE_CHECK_DELAY_MS}ms")
            setExact(manager, triggerAt, pending)
        }
    }

    fun scheduleFallback(alarmId: Int) {
        val manager = alarmManager ?: return
        val triggerAt = System.currentTimeMillis() + WAKE_CHECK_FALLBACK_MS
        pendingIntentFor(WakeCheckIntents.ACTION_WAKE_CHECK_FALLBACK, alarmId)?.let { pending ->
            Log.d(TAG, "Wake-check fallback scheduled for alarmId=$alarmId in ${WAKE_CHECK_FALLBACK_MS}ms")
            setExact(manager, triggerAt, pending)
        }
    }

    fun cancel(alarmId: Int) {
        alarmManager?.let { manager ->
            cancelScheduledIntents(manager, alarmId)
        }
        WakeCheckStorage(context).remove(alarmId)
    }

    private fun cancelScheduledIntents(manager: AlarmManager, alarmId: Int) {
        listOf(
            WakeCheckIntents.ACTION_SHOW_WAKE_CHECK,
            WakeCheckIntents.ACTION_WAKE_CHECK_FALLBACK
        ).forEach { action ->
            pendingIntentFor(action, alarmId, PendingIntent.FLAG_NO_CREATE)?.let { pending ->
                manager.cancel(pending)
                pending.cancel()
            }
        }
    }

    private fun pendingIntentFor(action: String, alarmId: Int, flag: Int = PendingIntent.FLAG_UPDATE_CURRENT): PendingIntent? {
        val intent = Intent(context, WakeCheckReceiver::class.java).apply {
            this.action = action
            putExtra(WakeCheckIntents.EXTRA_ALARM_ID, alarmId)
        }
        val mergedFlags = flag or PendingIntent.FLAG_IMMUTABLE
        val requestCode = when (action) {
            WakeCheckIntents.ACTION_SHOW_WAKE_CHECK -> REQUEST_CODE_SHOW_BASE + alarmId
            WakeCheckIntents.ACTION_WAKE_CHECK_FALLBACK -> REQUEST_CODE_FALLBACK_BASE + alarmId
            else -> REQUEST_CODE_SHOW_BASE + alarmId
        }
        return PendingIntent.getBroadcast(context, requestCode, intent, mergedFlags)
    }

    private fun setExact(manager: AlarmManager, triggerAt: Long, pendingIntent: PendingIntent) {
        AlarmManagerCompat.setExactAndAllowWhileIdle(
            manager,
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            pendingIntent
        )
    }

    companion object {
        private const val TAG = "WakeCheckScheduler"
        internal const val WAKE_CHECK_DELAY_MS = 10 * 60 * 1000L
        internal const val WAKE_CHECK_FALLBACK_MS = 3 * 60 * 1000L
        private const val REQUEST_CODE_SHOW_BASE = 40_000
        private const val REQUEST_CODE_FALLBACK_BASE = 50_000
    }
}
