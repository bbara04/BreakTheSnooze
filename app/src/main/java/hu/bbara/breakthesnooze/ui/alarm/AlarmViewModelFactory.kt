package hu.bbara.breakthesnooze.ui.alarm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import hu.bbara.breakthesnooze.data.alarm.AlarmRepository
import hu.bbara.breakthesnooze.data.alarm.AlarmScheduler
import hu.bbara.breakthesnooze.data.settings.SettingsRepository

class AlarmViewModelFactory(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val settingsRepository: SettingsRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AlarmViewModel::class.java)) {
            return AlarmViewModel(repository, scheduler, settingsRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
