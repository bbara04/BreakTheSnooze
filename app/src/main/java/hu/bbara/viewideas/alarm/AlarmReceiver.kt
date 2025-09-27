package hu.bbara.viewideas.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import hu.bbara.viewideas.data.alarm.AlarmRepositoryProvider
import hu.bbara.viewideas.data.alarm.AlarmSchedulerProvider
import hu.bbara.viewideas.ui.alarm.AlarmRingingActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != AlarmIntents.ACTION_ALARM_FIRED) {
            return
        }
        val alarmId = intent.getIntExtra(AlarmIntents.EXTRA_ALARM_ID, -1)
        if (alarmId == -1) {
            return
        }

        val pendingResult = goAsync()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope.launch {
            try {
                val repository = AlarmRepositoryProvider.getRepository(context.applicationContext)
                val scheduler = AlarmSchedulerProvider.getScheduler(context.applicationContext)
                val alarm = repository.getAlarmById(alarmId)

                if (alarm == null) {
                    scheduler.cancel(alarmId)
                    pendingResult.finish()
                    return@launch
                }

                if (alarm.repeatDays.isEmpty()) {
                    repository.updateAlarmActive(alarmId, false)
                    scheduler.cancel(alarmId)
                } else {
                    scheduler.schedule(alarm)
                }

                val serviceIntent = Intent(context, AlarmRingtoneService::class.java).apply {
                    action = AlarmIntents.ACTION_ALARM_FIRED
                    putExtra(AlarmIntents.EXTRA_ALARM_ID, alarmId)
                }

                ContextCompat.startForegroundService(context, serviceIntent)
                context.startActivity(AlarmRingingActivity.createIntent(context, alarmId))
            } finally {
                pendingResult.finish()
            }
        }
    }
}
