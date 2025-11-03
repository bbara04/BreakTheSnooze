package hu.bbara.breakthesnooze.data.alarm

import android.content.Context

object AlarmRepositoryProvider {
    @Volatile
    private var repository: AlarmRepository? = null

    fun getRepository(context: Context): AlarmRepository {
        return repository ?: synchronized(this) {
            repository ?: AlarmDatabase.getInstance(context).let { database ->
                DefaultAlarmRepository(
                    alarmDao = database.alarmDao(),
                    wakeEventDao = database.wakeEventDao()
                )
            }.also { repository = it }
        }
    }
}
