package hu.bbara.breakthesnooze.data.duration

import android.content.Context

object DurationAlarmSchedulerProvider {
    @Volatile
    private var scheduler: DurationAlarmScheduler? = null

    fun getScheduler(context: Context): DurationAlarmScheduler {
        return scheduler ?: synchronized(this) {
            scheduler ?: AndroidDurationAlarmScheduler(context.applicationContext).also { scheduler = it }
        }
    }
}
