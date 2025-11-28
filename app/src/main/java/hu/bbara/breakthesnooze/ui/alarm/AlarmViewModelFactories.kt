package hu.bbara.breakthesnooze.ui.alarm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import hu.bbara.breakthesnooze.data.alarm.AlarmRepository
import hu.bbara.breakthesnooze.data.alarm.AlarmScheduler
import hu.bbara.breakthesnooze.data.duration.DurationAlarmRepository
import hu.bbara.breakthesnooze.data.duration.DurationAlarmScheduler
import hu.bbara.breakthesnooze.data.settings.SettingsRepository
import hu.bbara.breakthesnooze.ui.alarm.domain.CreateDurationAlarmUseCase
import hu.bbara.breakthesnooze.ui.alarm.domain.DeleteDurationAlarmUseCase
import hu.bbara.breakthesnooze.ui.alarm.domain.SaveAlarmUseCase

class AlarmListViewModelFactory(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AlarmListViewModel::class.java)) {
            return AlarmListViewModel(
                repository = repository,
                scheduler = scheduler
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

class AlarmEditorViewModelFactory(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val settingsRepository: SettingsRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AlarmEditorViewModel::class.java)) {
            return AlarmEditorViewModel(
                repository = repository,
                settingsRepository = settingsRepository,
                saveAlarmUseCase = SaveAlarmUseCase(repository, scheduler)
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

class DurationAlarmViewModelFactory(
    private val repository: DurationAlarmRepository,
    private val scheduler: DurationAlarmScheduler,
    private val settingsRepository: SettingsRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DurationAlarmViewModel::class.java)) {
            return DurationAlarmViewModel(
                repository = repository,
                settingsRepository = settingsRepository,
                createDurationAlarmUseCase = CreateDurationAlarmUseCase(repository, scheduler),
                deleteDurationAlarmUseCase = DeleteDurationAlarmUseCase(repository, scheduler)
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

class AlarmSettingsViewModelFactory(
    private val repository: SettingsRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AlarmSettingsViewModel::class.java)) {
            return AlarmSettingsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
