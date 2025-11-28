package hu.bbara.breakthesnooze.ui.alarm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import hu.bbara.breakthesnooze.data.alarm.AlarmRepository
import hu.bbara.breakthesnooze.data.alarm.AlarmScheduler
import hu.bbara.breakthesnooze.data.duration.DurationAlarmRepository
import hu.bbara.breakthesnooze.data.duration.DurationAlarmScheduler
import hu.bbara.breakthesnooze.data.settings.SettingsRepository

class AlarmViewModelFactory(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val settingsRepository: SettingsRepository,
    private val durationRepository: DurationAlarmRepository,
    private val durationScheduler: DurationAlarmScheduler
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AlarmViewModel::class.java)) {
            return AlarmViewModel(
                repository,
                scheduler,
                settingsRepository,
                durationRepository,
                durationScheduler
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
