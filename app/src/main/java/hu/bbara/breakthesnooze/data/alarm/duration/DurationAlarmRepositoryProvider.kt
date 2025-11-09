package hu.bbara.breakthesnooze.data.alarm.duration

import android.content.Context
import hu.bbara.breakthesnooze.data.alarm.AlarmDatabase

object DurationAlarmRepositoryProvider {
    @Volatile
    private var repository: DurationAlarmRepository? = null

    fun getRepository(context: Context): DurationAlarmRepository {
        return repository ?: synchronized(this) {
            repository ?: AlarmDatabase.getInstance(context).let { database ->
                DefaultDurationAlarmRepository(database.durationAlarmDao())
            }.also { repository = it }
        }
    }
}
