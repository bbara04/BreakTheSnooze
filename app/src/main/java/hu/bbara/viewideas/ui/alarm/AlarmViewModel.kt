package hu.bbara.viewideas.ui.alarm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hu.bbara.viewideas.data.alarm.AlarmRepository
import hu.bbara.viewideas.data.alarm.toUiModelWithId
import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AlarmViewModel(
    private val repository: AlarmRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlarmUiState())
    val uiState: StateFlow<AlarmUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureSeedData()
        }

        viewModelScope.launch {
            repository.alarms.collect { alarms ->
                _uiState.update { state ->
                    val editing = state.editingAlarm
                    val updatedEditing = editing?.let { current ->
                        alarms.firstOrNull { it.id == current.id }
                    }
                    state.copy(alarms = alarms, editingAlarm = updatedEditing)
                }
            }
        }
    }

    fun onToggleAlarm(id: Int, isActive: Boolean) {
        viewModelScope.launch {
            repository.updateAlarmActive(id, isActive)
        }
    }

    fun deleteAlarm(id: Int) {
        viewModelScope.launch {
            repository.deleteAlarm(id)
            _uiState.update { state ->
                if (state.editingAlarm?.id == id) {
                    state.copy(
                        draft = sampleDraft(),
                        destination = AlarmDestination.List,
                        editingAlarm = null
                    )
                } else {
                    state
                }
            }
        }
    }

    fun startCreating() {
        _uiState.update { state ->
            state.copy(
                draft = sampleDraft(),
                destination = AlarmDestination.Create,
                editingAlarm = null
            )
        }
    }

    fun startEditing(id: Int) {
        val target = _uiState.value.alarms.firstOrNull { it.id == id } ?: return
        _uiState.update { state ->
            state.copy(
                draft = target.toCreationState(),
                destination = AlarmDestination.Create,
                editingAlarm = target
            )
        }
    }

    fun updateDraft(draft: AlarmCreationState) {
        _uiState.update { state -> state.copy(draft = draft) }
    }

    fun selectPresetTime(preset: LocalTime) {
        _uiState.update { state ->
            state.copy(draft = state.draft.copy(time = preset))
        }
    }

    fun setDraftTime(time: LocalTime) {
        _uiState.update { state ->
            state.copy(draft = state.draft.copy(time = time))
        }
    }

    fun toggleDraftDay(day: DayOfWeek) {
        _uiState.update { state ->
            val current = state.draft.repeatDays
            val updated = if (current.contains(day)) current - day else current + day
            state.copy(draft = state.draft.copy(repeatDays = updated))
        }
    }

    fun resetDraft() {
        _uiState.update { state ->
            val editing = state.editingAlarm
            if (editing != null) {
                val refreshed = state.alarms.firstOrNull { it.id == editing.id } ?: editing
                state.copy(draft = refreshed.toCreationState())
            } else {
                state.copy(draft = sampleDraft())
            }
        }
    }

    fun saveDraft() {
        val draftSnapshot = _uiState.value.draft
        val editing = _uiState.value.editingAlarm
        val model = draftSnapshot.toUiModelWithId(
            id = editing?.id ?: 0,
            isActive = editing?.isActive ?: true
        ) ?: return
        viewModelScope.launch {
            repository.upsertAlarm(model)
            _uiState.update { state ->
                state.copy(
                    draft = sampleDraft(),
                    destination = AlarmDestination.List,
                    editingAlarm = null
                )
            }
        }
    }

    fun cancelCreation() {
        _uiState.update { state ->
            state.copy(draft = sampleDraft(), destination = AlarmDestination.List, editingAlarm = null)
        }
    }
}

data class AlarmUiState(
    val alarms: List<AlarmUiModel> = emptyList(),
    val draft: AlarmCreationState = sampleDraft(),
    val destination: AlarmDestination = AlarmDestination.List,
    val editingAlarm: AlarmUiModel? = null
)
