package hu.bbara.breakthesnooze.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import hu.bbara.breakthesnooze.data.alarm.AlarmRepository
import hu.bbara.breakthesnooze.data.alarm.AlarmScheduler
import hu.bbara.breakthesnooze.ui.alarm.AlarmRingingActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var alarmRepository: AlarmRepository
    @Inject lateinit var alarmScheduler: AlarmScheduler

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
                val alarm = alarmRepository.getAlarmById(alarmId)

                if (alarm == null) {
                    alarmScheduler.cancel(alarmId)
                    pendingResult.finish()
                    return@launch
                }

                if (alarm.repeatDays.isEmpty()) {
                    alarmRepository.updateAlarmActive(alarmId, false)
                    alarmScheduler.cancel(alarmId)
                } else {
                    alarmScheduler.schedule(alarm)
                }

                val serviceIntent = Intent(context, AlarmRingtoneService::class.java).apply {
                    action = AlarmIntents.ACTION_ALARM_FIRED
                    putExtra(AlarmIntents.EXTRA_ALARM_ID, alarmId)
                }

                ContextCompat.startForegroundService(context, serviceIntent)
                if (shouldLaunchAlarmScreen(context)) {
                    withContext(Dispatchers.Main) {
                        context.startActivity(AlarmRingingActivity.createIntent(context, alarmId))
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
