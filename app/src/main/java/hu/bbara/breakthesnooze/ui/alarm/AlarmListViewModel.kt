package hu.bbara.breakthesnooze.ui.alarm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hu.bbara.breakthesnooze.data.alarm.model.WakeEvent
import hu.bbara.breakthesnooze.data.alarm.repository.AlarmRepository
import hu.bbara.breakthesnooze.data.alarm.scheduler.AlarmScheduler
import hu.bbara.breakthesnooze.ui.alarm.domain.DeleteAlarmUseCase
import hu.bbara.breakthesnooze.ui.alarm.domain.SynchronizeAlarmsUseCase
import hu.bbara.breakthesnooze.ui.alarm.domain.ToggleAlarmActiveUseCase
import hu.bbara.breakthesnooze.util.logDuration
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AlarmListViewModel(
    private val repository: AlarmRepository,
    scheduler: AlarmScheduler,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val toggleAlarmUseCase = ToggleAlarmActiveUseCase(repository, scheduler)
    private val deleteAlarmUseCase = DeleteAlarmUseCase(repository, scheduler)
    private val synchronizeAlarmsUseCase = SynchronizeAlarmsUseCase(scheduler)

    private val _state = MutableStateFlow(AlarmListState())
    val state: StateFlow<AlarmListState> = _state.asStateFlow()
    private var synchronizeJob: Job? = null

    init {
        viewModelScope.launch(ioDispatcher) {
            logDuration(TAG, "ensureSeedData") { repository.ensureSeedData() }
        }

        viewModelScope.launch {
            combine(repository.alarms, repository.wakeEvents) { alarms, events ->
                alarms to events
            }.collect { (alarms, events) ->
                _state.update { state ->
                    state.copy(alarms = alarms, wakeEvents = events)
                }
                synchronizeAlarms(alarms)
            }
        }
    }

    fun selectHomeTab(tab: AlarmHomeTab) {
        _state.update { state ->
            if (state.homeTab == tab) state else state.copy(homeTab = tab)
        }
    }

    fun setBreakdownPeriod(period: BreakdownPeriod) {
        _state.update { state -> state.copy(breakdownPeriod = period) }
    }

    fun onToggleAlarm(id: Int, isActive: Boolean) {
        val previousState = _state.value
        _state.update { state ->
            val updatedAlarms = state.alarms.map { alarm ->
                if (alarm.id == id) alarm.copy(isActive = isActive) else alarm
            }
            state.copy(alarms = updatedAlarms)
        }
        viewModelScope.launch {
            val updated = withContext(ioDispatcher) {
                logDuration(TAG, "updateAlarmActive_$id") {
                    toggleAlarmUseCase(id, isActive)
                }
            }
            if (updated == null) {
                _state.value = previousState
            }
        }
    }

    fun deleteAlarm(id: Int) {
        val previousState = _state.value
        _state.update { state ->
            state.copy(alarms = state.alarms.filterNot { it.id == id })
        }
        viewModelScope.launch {
            val deleted = withContext(ioDispatcher) {
                logDuration(TAG, "delete_$id") { deleteAlarmUseCase(id) }
            }
            if (!deleted) {
                _state.value = previousState
            }
        }
    }

    fun enterSelection(id: Int) {
        _state.update { state ->
            state.copy(selectedAlarmIds = state.selectedAlarmIds + id)
        }
    }

    fun toggleSelection(id: Int) {
        _state.update { state ->
            val updated = if (state.selectedAlarmIds.contains(id)) {
                state.selectedAlarmIds - id
            } else {
                state.selectedAlarmIds + id
            }
            state.copy(selectedAlarmIds = updated)
        }
    }

    fun clearSelection() {
        _state.update { state -> state.copy(selectedAlarmIds = emptySet()) }
    }

    fun deleteSelected() {
        val ids = _state.value.selectedAlarmIds
        if (ids.isEmpty()) return
        val previousState = _state.value
        _state.update { state ->
            state.copy(
                alarms = state.alarms.filterNot { ids.contains(it.id) },
                selectedAlarmIds = emptySet()
            )
        }
        viewModelScope.launch {
            val deleted = withContext(Dispatchers.IO) {
                logDuration(TAG, "deleteSelected_${ids.size}") {
                    ids.all { deleteAlarmUseCase(it) }
                }
            }
            if (!deleted) {
                _state.value = previousState
            }
        }
    }

    private fun synchronizeAlarms(alarms: List<AlarmUiModel>) {
        synchronizeJob?.cancel()
        synchronizeJob = viewModelScope.launch(ioDispatcher) {
            logDuration(TAG, "synchronize_${alarms.size}") {
                synchronizeAlarmsUseCase(alarms)
            }
        }
    }

    companion object {
        private const val TAG = "AlarmListViewModel"
    }
}

data class AlarmListState(
    val alarms: List<AlarmUiModel> = emptyList(),
    val wakeEvents: List<WakeEvent> = emptyList(),
    val selectedAlarmIds: Set<Int> = emptySet(),
    val homeTab: AlarmHomeTab = AlarmHomeTab.Alarms,
    val breakdownPeriod: BreakdownPeriod = BreakdownPeriod.Weekly
)
