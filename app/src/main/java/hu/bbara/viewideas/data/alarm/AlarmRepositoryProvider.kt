package hu.bbara.viewideas.data.alarm

import android.content.Context

object AlarmRepositoryProvider {
    @Volatile
    private var repository: AlarmRepository? = null

    fun getRepository(context: Context): AlarmRepository {
        return repository ?: synchronized(this) {
            repository ?: DefaultAlarmRepository(
                AlarmDatabase.getInstance(context).alarmDao()
            ).also { repository = it }
        }
    }
}
