package hu.bbara.breakthesnooze.data.duration

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.AlarmManagerCompat
import androidx.core.content.ContextCompat
import hu.bbara.breakthesnooze.alarm.AlarmIntents
import hu.bbara.breakthesnooze.alarm.AlarmReceiver
import hu.bbara.breakthesnooze.data.alarm.AlarmKind
import hu.bbara.breakthesnooze.data.alarm.uniqueAlarmId
import hu.bbara.breakthesnooze.ui.alarm.AlarmRingingActivity
import java.time.Clock

class AndroidDurationAlarmScheduler(
    private val context: Context,
    private val clock: Clock = Clock.systemDefaultZone()
) : DurationAlarmScheduler {

    private val alarmManager: AlarmManager? = ContextCompat.getSystemService(context, AlarmManager::class.java)

    override fun schedule(alarm: DurationAlarm) {
        val manager = alarmManager ?: return
        val uniqueId = uniqueAlarmId(AlarmKind.Duration, alarm.id)
        cancel(alarm.id)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && manager.canScheduleExactAlarms().not()) {
            Log.w(TAG, "Exact alarm scheduling not permitted; skipping duration alarm id=${alarm.id}")
            return
        }
        val triggerAtMillis = alarm.triggerAt.toEpochMilli()
        val pendingIntent = createPendingIntent(uniqueId, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        val showIntent = createShowIntent(uniqueId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val info = AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent)
            runCatching { manager.setAlarmClock(info, pendingIntent) }
                .onFailure { Log.w(TAG, "Failed to schedule duration alarm id=${alarm.id}", it) }
        } else {
            runCatching {
                AlarmManagerCompat.setExactAndAllowWhileIdle(
                    manager,
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }.onFailure { Log.w(TAG, "Failed to schedule duration alarm id=${alarm.id}", it) }
        }
        Log.d(TAG, "Scheduled duration alarm id=${alarm.id} at=${alarm.triggerAt}")
    }

    override fun cancel(alarmId: Int) {
        val manager = alarmManager ?: return
        val uniqueId = uniqueAlarmId(AlarmKind.Duration, alarmId)
        createPendingIntent(uniqueId, PendingIntent.FLAG_NO_CREATE)?.let { pending ->
            manager.cancel(pending)
            pending.cancel()
            Log.d(TAG, "Cancelled duration alarm id=$alarmId")
        }
    }

    override fun synchronize(alarms: List<DurationAlarm>) {
        val now = clock.instant().toEpochMilli()
        val futureAlarms = alarms.filter { it.triggerAt.toEpochMilli() >= now }
        Log.d(TAG, "Synchronizing duration alarms scheduled=${futureAlarms.size}")
        futureAlarms.forEach { schedule(it) }
    }

    private fun createPendingIntent(uniqueId: Int, flag: Int): PendingIntent? {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmIntents.ACTION_ALARM_FIRED
            putExtra(AlarmIntents.EXTRA_ALARM_ID, uniqueId)
        }
        val mergedFlags = if (flag == 0) PendingIntent.FLAG_IMMUTABLE else flag or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, uniqueId, intent, mergedFlags)
    }

    private fun createShowIntent(uniqueId: Int): PendingIntent {
        return PendingIntent.getActivity(
            context,
            uniqueId,
            AlarmRingingActivity.createIntent(context, uniqueId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val TAG = "DurationAlarmScheduler"
    }
}
