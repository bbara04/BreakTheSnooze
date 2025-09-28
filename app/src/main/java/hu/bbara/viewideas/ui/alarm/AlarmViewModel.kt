package hu.bbara.viewideas.ui.alarm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import hu.bbara.viewideas.data.alarm.AlarmRepository
import hu.bbara.viewideas.data.alarm.AlarmScheduler
import hu.bbara.viewideas.data.alarm.toUiModelWithId
import hu.bbara.viewideas.util.logDuration
import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AlarmViewModel(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlarmUiState())
    val uiState: StateFlow<AlarmUiState> = _uiState.asStateFlow()
    private var synchronizeJob: Job? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            logDuration(TAG, "ensureSeedData") {
                repository.ensureSeedData()
            }
        }

        viewModelScope.launch {
            repository.alarms.collect { alarms ->
                logDuration(TAG, "collect_updateUi") {
                    _uiState.update { state ->
                        val editing = state.editingAlarm
                        val updatedEditing = editing?.let { current ->
                            alarms.firstOrNull { it.id == current.id }
                        }
                        state.copy(alarms = alarms, editingAlarm = updatedEditing)
                    }
                    Log.d(TAG, "collect size=${alarms.size}")
                }
                synchronizeAlarms(alarms)
            }
        }
    }

    fun onToggleAlarm(id: Int, isActive: Boolean) {
        val previousState = _uiState.value
        _uiState.update { state ->
            val updatedAlarms = state.alarms.map { alarm ->
                if (alarm.id == id) alarm.copy(isActive = isActive) else alarm
            }
            val updatedEditing = state.editingAlarm?.takeIf { it.id == id }?.copy(isActive = isActive)
                ?: state.editingAlarm
            state.copy(alarms = updatedAlarms, editingAlarm = updatedEditing)
        }
        viewModelScope.launch {
            val updated = withContext(Dispatchers.IO) {
                logDuration(TAG, "updateAlarmActive_$id") {
                    repository.updateAlarmActive(id, isActive)
                }
            }
            if (updated != null) {
                withContext(Dispatchers.IO) {
                    if (updated.isActive) {
                        logDuration(TAG, "schedule_$id") { scheduler.schedule(updated) }
                    } else {
                        logDuration(TAG, "cancel_$id") { scheduler.cancel(updated.id) }
                    }
                }
            } else {
                _uiState.value = previousState
            }
        }
    }

    fun deleteAlarm(id: Int) {
        val previousState = _uiState.value
        _uiState.update { state ->
            val updatedAlarms = state.alarms.filterNot { it.id == id }
            val editing = state.editingAlarm?.takeIf { it.id != id }
            state.copy(alarms = updatedAlarms, editingAlarm = editing)
        }
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                logDuration(TAG, "deleteAlarm_$id") {
                    runCatching { repository.deleteAlarm(id) }.isSuccess
                }
            }
            if (success) {
                withContext(Dispatchers.IO) {
                    logDuration(TAG, "cancel_$id") { scheduler.cancel(id) }
                }
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
            } else {
                _uiState.value = previousState
            }
        }
    }

    fun startCreating() {
        logDuration(TAG, "startCreating") {}
        _uiState.update { state ->
            state.copy(
                draft = sampleDraft(),
                destination = AlarmDestination.Create,
                editingAlarm = null
            )
        }
    }

    fun startEditing(id: Int) {
        logDuration(TAG, "startEditing_$id") {}
        val target = _uiState.value.alarms.firstOrNull { it.id == id } ?: return
        _uiState.update { state ->
            state.copy(
                draft = target.toCreationState(),
                destination = AlarmDestination.Create,
                editingAlarm = target,
                selectedAlarmIds = emptySet()
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

    fun setDraftSound(soundUri: String?) {
        _uiState.update { state ->
            state.copy(draft = state.draft.copy(soundUri = soundUri))
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
            isActive = true
        ) ?: return
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                logDuration(TAG, "upsert_${editing?.id ?: 0}") {
                    repository.upsertAlarm(model)
                }
            }
            saved?.let { alarm ->
                withContext(Dispatchers.IO) {
                    if (alarm.isActive) {
                        logDuration(TAG, "schedule_${alarm.id}") { scheduler.schedule(alarm) }
                    } else {
                        logDuration(TAG, "cancel_${alarm.id}") { scheduler.cancel(alarm.id) }
                    }
                }
                _uiState.update { state ->
                    state.copy(
                        draft = sampleDraft(),
                        destination = AlarmDestination.List,
                        editingAlarm = null,
                        selectedAlarmIds = emptySet()
                    )
                }
            }
        }
    }

    fun cancelCreation() {
        _uiState.update { state ->
            state.copy(draft = sampleDraft(), destination = AlarmDestination.List, editingAlarm = null)
        }
    }

    fun enterSelection(id: Int) {
        _uiState.update { state ->
            val updated = state.selectedAlarmIds + id
            state.copy(selectedAlarmIds = updated)
        }
    }

    fun toggleSelection(id: Int) {
        _uiState.update { state ->
            val current = state.selectedAlarmIds
            val updated = if (current.contains(id)) current - id else current + id
            state.copy(selectedAlarmIds = updated)
        }
    }

    fun clearSelection() {
        _uiState.update { state -> state.copy(selectedAlarmIds = emptySet()) }
    }

    fun deleteSelected() {
        val ids = _uiState.value.selectedAlarmIds
        if (ids.isEmpty()) return
        val previousState = _uiState.value
        _uiState.update { state ->
            val updatedAlarms = state.alarms.filterNot { ids.contains(it.id) }
            val editing = state.editingAlarm?.takeIf { !ids.contains(it.id) }
            state.copy(alarms = updatedAlarms, editingAlarm = editing)
        }
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                logDuration(TAG, "deleteSelected_${ids.size}") {
                    runCatching {
                        ids.forEach { repository.deleteAlarm(it) }
                    }.isSuccess
                }
            }
            if (success) {
                withContext(Dispatchers.IO) {
                    ids.forEach { id ->
                        logDuration(TAG, "cancel_$id") { scheduler.cancel(id) }
                    }
                }
                _uiState.update { state ->
                    val editing = state.editingAlarm
                    val editingCleared = if (editing != null && ids.contains(editing.id)) null else editing
                    state.copy(
                        draft = if (editingCleared == null) sampleDraft() else state.draft,
                        destination = if (editingCleared == null) AlarmDestination.List else state.destination,
                        editingAlarm = editingCleared,
                        selectedAlarmIds = emptySet()
                    )
                }
            } else {
                _uiState.value = previousState
            }
        }
    }

    private fun synchronizeAlarms(alarms: List<AlarmUiModel>) {
        synchronizeJob?.cancel()
        synchronizeJob = viewModelScope.launch(Dispatchers.IO) {
            logDuration(TAG, "synchronize_${alarms.size}") {
                scheduler.synchronize(alarms)
            }
        }
    }

    companion object {
        private const val TAG = "AlarmViewModel"
    }
}

data class AlarmUiState(
    val alarms: List<AlarmUiModel> = emptyList(),
    val draft: AlarmCreationState = sampleDraft(),
    val destination: AlarmDestination = AlarmDestination.List,
    val editingAlarm: AlarmUiModel? = null,
    val selectedAlarmIds: Set<Int> = emptySet()
)
