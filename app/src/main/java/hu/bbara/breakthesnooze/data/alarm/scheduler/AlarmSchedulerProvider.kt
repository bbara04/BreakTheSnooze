package hu.bbara.breakthesnooze.data.alarm.scheduler

import android.content.Context

object AlarmSchedulerProvider {
    @Volatile
    private var scheduler: AlarmScheduler? = null

    fun getScheduler(context: Context): AlarmScheduler {
        return scheduler ?: synchronized(this) {
            scheduler ?: AndroidAlarmScheduler(context.applicationContext).also { scheduler = it }
        }
    }
}
