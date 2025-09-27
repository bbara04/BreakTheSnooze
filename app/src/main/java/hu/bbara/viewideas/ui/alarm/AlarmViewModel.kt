package hu.bbara.viewideas.ui.alarm

import androidx.lifecycle.ViewModel
import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class AlarmViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(
        AlarmUiState(
            alarms = sampleAlarms(),
            draft = sampleDraft()
        )
    )
    val uiState: StateFlow<AlarmUiState> = _uiState.asStateFlow()

    fun onToggleAlarm(id: Int, isActive: Boolean) {
        _uiState.update { state ->
            state.copy(
                alarms = state.alarms
                    .map { if (it.id == id) it.copy(isActive = isActive) else it }
                    .sortedWith(alarmSorter)
            )
        }
    }

    fun deleteAlarm(id: Int) {
        _uiState.update { state ->
            state.copy(alarms = state.alarms.filterNot { it.id == id })
        }
    }

    fun startCreating() {
        _uiState.update { state ->
            state.copy(draft = sampleDraft(), destination = AlarmDestination.Create)
        }
    }

    fun updateDraft(draft: AlarmCreationState) {
        _uiState.update { state -> state.copy(draft = draft) }
    }

    fun nudgeDraftTime() {
        _uiState.update { state ->
            val baseTime = state.draft.time ?: LocalTime.now().withSecond(0).withNano(0)
            val nudged = baseTime.plusMinutes(15).withSecond(0).withNano(0)
            state.copy(draft = state.draft.copy(time = nudged))
        }
    }

    fun selectPresetTime(preset: LocalTime) {
        _uiState.update { state ->
            state.copy(draft = state.draft.copy(time = preset))
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
        _uiState.update { state -> state.copy(draft = sampleDraft()) }
    }

    fun saveDraft() {
        _uiState.update { state ->
            val time = state.draft.time
            val repeatDays = state.draft.repeatDays
            if (time == null || repeatDays.isEmpty()) {
                state
            } else {
                val nextId = (state.alarms.maxOfOrNull { it.id } ?: 0) + 1
                val newAlarm = AlarmUiModel(
                    id = nextId,
                    time = time,
                    label = state.draft.label.ifBlank { "New alarm" },
                    isActive = true,
                    repeatDays = repeatDays
                )
                state.copy(
                    alarms = (state.alarms + newAlarm).sortedWith(alarmSorter),
                    draft = sampleDraft(),
                    destination = AlarmDestination.List
                )
            }
        }
    }

    fun cancelCreation() {
        _uiState.update { state ->
            state.copy(draft = sampleDraft(), destination = AlarmDestination.List)
        }
    }
}

data class AlarmUiState(
    val alarms: List<AlarmUiModel> = emptyList(),
    val draft: AlarmCreationState = sampleDraft(),
    val destination: AlarmDestination = AlarmDestination.List
)
