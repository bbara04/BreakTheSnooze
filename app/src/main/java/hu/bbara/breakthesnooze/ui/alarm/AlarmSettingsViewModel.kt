package hu.bbara.breakthesnooze.ui.alarm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hu.bbara.breakthesnooze.data.settings.model.SettingsState
import hu.bbara.breakthesnooze.data.settings.repository.SettingsRepository
import hu.bbara.breakthesnooze.ui.alarm.dismiss.AlarmDismissTaskType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlarmSettingsViewModel @Inject constructor(
    private val repository: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.settings.collect { settings ->
                _state.value = settings
            }
        }
    }

    fun setDefaultDismissTask(task: AlarmDismissTaskType) {
        viewModelScope.launch {
            repository.setDefaultDismissTask(task)
        }
    }

    fun setDefaultRingtone(uri: String?) {
        viewModelScope.launch {
            repository.setDefaultRingtone(uri)
        }
    }

    fun setDebugMode(enabled: Boolean) {
        viewModelScope.launch {
            repository.setDebugModeEnabled(enabled)
        }
    }

    fun setTightGapWarningEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setTightGapWarningEnabled(enabled)
        }
    }
}
