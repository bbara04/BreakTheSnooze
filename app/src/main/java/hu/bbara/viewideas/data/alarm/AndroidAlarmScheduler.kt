package hu.bbara.viewideas.data.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.AlarmManagerCompat
import androidx.core.content.ContextCompat
import hu.bbara.viewideas.alarm.AlarmIntents
import hu.bbara.viewideas.alarm.AlarmReceiver
import hu.bbara.viewideas.ui.alarm.AlarmRingingActivity
import hu.bbara.viewideas.ui.alarm.AlarmUiModel

class AndroidAlarmScheduler(
    private val context: Context
) : AlarmScheduler {

    private val alarmManager: AlarmManager? = ContextCompat.getSystemService(context, AlarmManager::class.java)

    override fun schedule(alarm: AlarmUiModel) {
        val manager = alarmManager ?: return
        cancel(alarm.id)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && manager.canScheduleExactAlarms().not()) {
            Log.w(TAG, "Exact alarm scheduling not permitted; skipping for id=${alarm.id}")
            return
        }
        val triggerAtMillis = calculateNextTriggerMillis(alarm) ?: return
        val alarmIntent = createAlarmPendingIntent(alarm.id, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        val showIntent = createShowIntent(alarm.id)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val info = AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent)
            runCatching { manager.setAlarmClock(info, alarmIntent) }
                .onFailure { Log.w(TAG, "Failed to schedule alarm id=${alarm.id}", it) }
        } else {
            runCatching {
                AlarmManagerCompat.setExactAndAllowWhileIdle(manager, AlarmManager.RTC_WAKEUP, triggerAtMillis, alarmIntent)
            }.onFailure { Log.w(TAG, "Failed to schedule alarm id=${alarm.id}", it) }
        }
    }

    override fun cancel(alarmId: Int) {
        val manager = alarmManager ?: return
        createAlarmPendingIntent(alarmId, PendingIntent.FLAG_NO_CREATE)?.let { pending ->
            manager.cancel(pending)
            pending.cancel()
        }
    }

    override fun synchronize(alarms: List<AlarmUiModel>) {
        val active = alarms.filter { it.isActive }
        val inactiveIds = alarms.filterNot { it.isActive }.map { it.id }.toSet()

        inactiveIds.forEach { cancel(it) }
        active.forEach { schedule(it) }
    }

    private fun createAlarmPendingIntent(alarmId: Int, flag: Int): PendingIntent? {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmIntents.ACTION_ALARM_FIRED
            putExtra(AlarmIntents.EXTRA_ALARM_ID, alarmId)
        }
        val mergedFlags = if (flag == 0) PendingIntent.FLAG_IMMUTABLE else flag or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, alarmId, intent, mergedFlags)
    }

    private fun createShowIntent(alarmId: Int): PendingIntent {
        return PendingIntent.getActivity(
            context,
            alarmId,
            AlarmRingingActivity.createIntent(context, alarmId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val TAG = "AndroidAlarmScheduler"
    }
}
