package hu.bbara.breakthesnooze.feature.alarm.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import hu.bbara.breakthesnooze.data.alarm.model.AlarmKind
import hu.bbara.breakthesnooze.data.alarm.model.detectAlarmKind
import hu.bbara.breakthesnooze.data.alarm.model.rawAlarmIdFromUnique
import hu.bbara.breakthesnooze.data.alarm.repository.AlarmRepositoryProvider
import hu.bbara.breakthesnooze.data.alarm.scheduler.AlarmSchedulerProvider
import hu.bbara.breakthesnooze.data.duration.repository.DurationAlarmRepositoryProvider
import hu.bbara.breakthesnooze.data.duration.scheduler.DurationAlarmSchedulerProvider
import hu.bbara.breakthesnooze.ui.alarm.AlarmRingingActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != AlarmIntents.ACTION_ALARM_FIRED) {
            return
        }
        val uniqueAlarmId = intent.getIntExtra(AlarmIntents.EXTRA_ALARM_ID, -1)
        if (uniqueAlarmId == -1) {
            return
        }
        val alarmKind = detectAlarmKind(uniqueAlarmId)

        val pendingResult = goAsync()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope.launch {
            try {
                if (alarmKind == AlarmKind.Duration) {
                    handleDurationAlarm(context, uniqueAlarmId)
                    return@launch
                }

                val alarmId = uniqueAlarmId
                val repository = AlarmRepositoryProvider.getRepository(context.applicationContext)
                val scheduler = AlarmSchedulerProvider.getScheduler(context.applicationContext)
                val alarm = repository.getAlarmById(alarmId)

                if (alarm == null) {
                    scheduler.cancel(alarmId)
                    return@launch
                }

                if (alarm.repeatDays.isEmpty()) {
                    repository.updateAlarmActive(alarmId, false)
                    scheduler.cancel(alarmId)
                } else {
                    scheduler.schedule(alarm)
                }

                val serviceIntent = Intent(context, AlarmService::class.java).apply {
                    action = AlarmIntents.ACTION_ALARM_FIRED
                    putExtra(AlarmIntents.EXTRA_ALARM_ID, alarmId)
                }

                ContextCompat.startForegroundService(context, serviceIntent.apply {
                    putExtra(AlarmIntents.EXTRA_ALARM_ID, uniqueAlarmId)
                })
                if (shouldLaunchAlarmScreen(context)) {
                    withContext(Dispatchers.Main) {
                        context.startActivity(AlarmRingingActivity.createIntent(context, uniqueAlarmId))
                    }
                }
            } finally {
                withContext(pendingResultDispatcher + NonCancellable) {
                    pendingResult.finish()
                    pendingResult.ensureFinishedFlagVisible()
                }
            }
        }
    }

    private suspend fun handleDurationAlarm(context: Context, uniqueAlarmId: Int) {
        val rawId = rawAlarmIdFromUnique(uniqueAlarmId)
        val repository = DurationAlarmRepositoryProvider.getRepository(context.applicationContext)
        val scheduler = DurationAlarmSchedulerProvider.getScheduler(context.applicationContext)
        val alarm = repository.getById(rawId)
        if (alarm == null) {
            scheduler.cancel(rawId)
            return
        }
        scheduler.cancel(rawId)
        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            action = AlarmIntents.ACTION_ALARM_FIRED
            putExtra(AlarmIntents.EXTRA_ALARM_ID, uniqueAlarmId)
        }
        ContextCompat.startForegroundService(context, serviceIntent)
        if (shouldLaunchAlarmScreen(context)) {
            withContext(Dispatchers.Main) {
                context.startActivity(AlarmRingingActivity.createIntent(context, uniqueAlarmId))
            }
        }
    }

    private companion object {
        private val pendingResultDispatcher by lazy {
            val handler = Handler(Looper.getMainLooper())
            object : kotlinx.coroutines.CoroutineDispatcher() {
                override fun dispatch(context: CoroutineContext, block: Runnable) {
                    if (Looper.myLooper() == handler.looper) {
                        block.run()
                    } else {
                        handler.post(block)
                    }
                }
            }
        }
    }
}

private fun BroadcastReceiver.PendingResult.ensureFinishedFlagVisible() {
    try {
        val finishedField = BroadcastReceiver.PendingResult::class.java.getDeclaredField("mFinished").apply {
            isAccessible = true
        }
        if (!finishedField.getBoolean(this)) {
            finishedField.setBoolean(this, true)
        }
    } catch (_: Throwable) {
        // Best-effort: reflection may fail on future API levels.
    }
}
