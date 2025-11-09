package hu.bbara.breakthesnooze.ui.alarm

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hu.bbara.breakthesnooze.data.alarm.AlarmRepository
import hu.bbara.breakthesnooze.data.alarm.AlarmScheduler
import hu.bbara.breakthesnooze.data.alarm.WakeEvent
import hu.bbara.breakthesnooze.data.alarm.duration.DurationAlarm
import hu.bbara.breakthesnooze.data.alarm.duration.DurationAlarmRepository
import hu.bbara.breakthesnooze.data.alarm.duration.DurationAlarmScheduler
import hu.bbara.breakthesnooze.data.alarm.toUiModelWithId
import hu.bbara.breakthesnooze.data.settings.SettingsRepository
import hu.bbara.breakthesnooze.data.settings.SettingsState
import hu.bbara.breakthesnooze.ui.alarm.dismiss.AlarmDismissTaskType
import hu.bbara.breakthesnooze.util.logDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalTime

class AlarmViewModel(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val settingsRepository: SettingsRepository,
    private val durationRepository: DurationAlarmRepository,
    private val durationScheduler: DurationAlarmScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlarmUiState())
    val uiState: StateFlow<AlarmUiState> = _uiState.asStateFlow()
    private var synchronizeJob: Job? = null
    private var durationSynchronizeJob: Job? = null

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

        viewModelScope.launch {
            durationRepository.alarms.collect { durationAlarms ->
                _uiState.update { state ->
                    state.copy(durationAlarms = durationAlarms.map { it.toUiModel() })
                }
                synchronizeDurationAlarms(durationAlarms)
            }
        }

        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update { state ->
                    state.copy(settings = settings)
                }
            }
        }

        viewModelScope.launch {
            repository.wakeEvents.collect { events ->
                _uiState.update { state ->
                    state.copy(wakeEvents = events)
                }
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
                            draft = sampleDraft(
                                defaultTask = state.settings.defaultDismissTask,
                                defaultSound = state.settings.defaultRingtoneUri
                            ),
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

    fun deleteDurationAlarm(id: Int) {
        viewModelScope.launch {
            val deleted = withContext(Dispatchers.IO) {
                logDuration(TAG, "delete_duration_$id") {
                    runCatching { durationRepository.delete(id) }.isSuccess
                }
            }
            if (deleted) {
                withContext(Dispatchers.IO) {
                    logDuration(TAG, "cancel_duration_$id") {
                        durationScheduler.cancel(id)
                    }
                }
            }
        }
    }

    fun startCreating() {
        logDuration(TAG, "startCreating") {}
        _uiState.update { state ->
            state.copy(
                draft = sampleDraft(
                    defaultTask = state.settings.defaultDismissTask,
                    defaultSound = state.settings.defaultRingtoneUri
                ),
                destination = AlarmDestination.Create,
                editingAlarm = null
            )
        }
    }

    fun openSettings() {
        _uiState.update { state ->
            state.copy(
                destination = AlarmDestination.Settings,
                selectedAlarmIds = emptySet()
            )
        }
    }

    fun closeSettings() {
        _uiState.update { state ->
            state.copy(destination = AlarmDestination.List)
        }
    }

    fun selectHomeTab(tab: AlarmHomeTab) {
        _uiState.update { state ->
            if (state.homeTab == tab) return@update state
            val updatedDraft = if (tab == AlarmHomeTab.Duration) {
                state.durationDraft.withDurationMinutes(state.settings.defaultCountdownDurationMinutes)
            } else {
                state.durationDraft
            }
            state.copy(homeTab = tab, durationDraft = updatedDraft)
        }
    }

    fun setBreakdownPeriod(period: BreakdownPeriod) {
        _uiState.update { state -> state.copy(breakdownPeriod = period) }
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

    fun setDraftDismissTask(task: AlarmDismissTaskType) {
        _uiState.update { state ->
            val updatedDraft = if (task == AlarmDismissTaskType.QR_BARCODE_SCAN) {
                state.draft.copy(dismissTask = task)
            } else {
                state.draft.copy(
                    dismissTask = task,
                    qrBarcodeValue = null,
                    qrRequiredUniqueCount = 0
                )
            }
            state.copy(draft = updatedDraft)
        }
    }

    fun setDraftQrBarcodeValue(value: String?) {
        _uiState.update { state ->
            state.copy(
                draft = state.draft.copy(
                    qrBarcodeValue = value,
                    qrRequiredUniqueCount = if (value.isNullOrBlank()) state.draft.qrRequiredUniqueCount else 0
                )
            )
        }
    }

    fun setDraftQrScanMode(mode: QrScanMode) {
        _uiState.update { state ->
            if (state.draft.dismissTask != AlarmDismissTaskType.QR_BARCODE_SCAN) return@update state
            val updated = when (mode) {
                QrScanMode.SpecificCode -> state.draft.copy(qrRequiredUniqueCount = 0)
                QrScanMode.UniqueCodes -> state.draft.copy(
                    qrBarcodeValue = null,
                    qrRequiredUniqueCount = if (state.draft.qrRequiredUniqueCount >= MIN_QR_UNIQUE_COUNT) {
                        state.draft.qrRequiredUniqueCount
                    } else {
                        DEFAULT_QR_UNIQUE_COUNT
                    }
                )
            }
            state.copy(draft = updated)
        }
    }

    fun setDraftQrUniqueCount(count: Int) {
        _uiState.update { state ->
            if (state.draft.dismissTask != AlarmDismissTaskType.QR_BARCODE_SCAN) return@update state
            val clamped = count.coerceIn(MIN_QR_UNIQUE_COUNT, MAX_QR_UNIQUE_COUNT)
            state.copy(draft = state.draft.copy(qrRequiredUniqueCount = clamped, qrBarcodeValue = null))
        }
    }

    fun setDurationLabel(label: String) {
        _uiState.update { state ->
            state.copy(durationDraft = state.durationDraft.copy(label = label))
        }
    }

    fun setDurationHours(hours: Int) {
        _uiState.update { state ->
            val sanitized = hours.coerceIn(0, MAX_DURATION_HOURS)
            state.copy(durationDraft = state.durationDraft.copy(hours = sanitized))
        }
    }

    fun setDurationMinutes(minutes: Int) {
        _uiState.update { state ->
            val sanitized = minutes.coerceIn(0, 59)
            state.copy(durationDraft = state.durationDraft.copy(minutes = sanitized))
        }
    }

    fun saveDefaultDuration() {
        val totalMinutes = _uiState.value.durationDraft.totalMinutes
        if (totalMinutes <= 0) return
        viewModelScope.launch {
            settingsRepository.setDefaultCountdownDuration(totalMinutes)
        }
    }

    fun setDurationSound(uri: String?) {
        _uiState.update { state ->
            state.copy(durationDraft = state.durationDraft.copy(soundUri = uri))
        }
    }

    fun setDurationDismissTask(task: AlarmDismissTaskType) {
        _uiState.update { state ->
            val updatedDraft = if (task == AlarmDismissTaskType.QR_BARCODE_SCAN) {
                state.durationDraft.copy(dismissTask = task)
            } else {
                state.durationDraft.copy(
                    dismissTask = task,
                    qrBarcodeValue = null,
                    qrRequiredUniqueCount = 0
                )
            }
            state.copy(durationDraft = updatedDraft)
        }
    }

    fun setDurationQrBarcodeValue(value: String?) {
        _uiState.update { state ->
            state.copy(
                durationDraft = state.durationDraft.copy(
                    qrBarcodeValue = value,
                    qrRequiredUniqueCount = if (value.isNullOrBlank()) {
                        state.durationDraft.qrRequiredUniqueCount
                    } else {
                        0
                    }
                )
            )
        }
    }

    fun setDurationQrScanMode(mode: QrScanMode) {
        _uiState.update { state ->
            if (state.durationDraft.dismissTask != AlarmDismissTaskType.QR_BARCODE_SCAN) return@update state
            val updated = when (mode) {
                QrScanMode.SpecificCode -> state.durationDraft.copy(qrRequiredUniqueCount = 0)
                QrScanMode.UniqueCodes -> state.durationDraft.copy(
                    qrBarcodeValue = null,
                    qrRequiredUniqueCount = if (state.durationDraft.qrRequiredUniqueCount >= MIN_QR_UNIQUE_COUNT) {
                        state.durationDraft.qrRequiredUniqueCount
                    } else {
                        DEFAULT_QR_UNIQUE_COUNT
                    }
                )
            }
            state.copy(durationDraft = updated)
        }
    }

    fun setDurationQrUniqueCount(count: Int) {
        _uiState.update { state ->
            if (state.durationDraft.dismissTask != AlarmDismissTaskType.QR_BARCODE_SCAN) return@update state
            val clamped = count.coerceIn(MIN_QR_UNIQUE_COUNT, MAX_QR_UNIQUE_COUNT)
            state.copy(
                durationDraft = state.durationDraft.copy(
                    qrRequiredUniqueCount = clamped,
                    qrBarcodeValue = null
                )
            )
        }
    }

    fun setDefaultDismissTask(task: AlarmDismissTaskType) {
        viewModelScope.launch {
            settingsRepository.setDefaultDismissTask(task)
        }
    }

    fun setDefaultRingtone(uri: String?) {
        viewModelScope.launch {
            settingsRepository.setDefaultRingtone(uri)
        }
    }

    fun setDebugMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setDebugModeEnabled(enabled)
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
                state.copy(
                    draft = sampleDraft(
                        defaultTask = state.settings.defaultDismissTask,
                        defaultSound = state.settings.defaultRingtoneUri
                    )
                )
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
                        draft = sampleDraft(
                            defaultTask = state.settings.defaultDismissTask,
                            defaultSound = state.settings.defaultRingtoneUri
                        ),
                        destination = AlarmDestination.List,
                        editingAlarm = null,
                        selectedAlarmIds = emptySet()
                    )
                }
            }
        }
    }

    fun saveDurationDraft() {
        val draftSnapshot = _uiState.value.durationDraft
        val totalMinutes = draftSnapshot.totalMinutes
        if (totalMinutes <= 0 || _uiState.value.isSavingDuration) {
            return
        }
        _uiState.update { state -> state.copy(isSavingDuration = true) }
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                logDuration(TAG, "create_duration") {
                    durationRepository.create(
                        durationMinutes = totalMinutes,
                        label = draftSnapshot.label.ifBlank { "Alarm" },
                        soundUri = draftSnapshot.soundUri,
                        dismissTask = draftSnapshot.dismissTask,
                        qrBarcodeValue = if (draftSnapshot.dismissTask == AlarmDismissTaskType.QR_BARCODE_SCAN) {
                            draftSnapshot.qrBarcodeValue
                        } else {
                            null
                        },
                        qrRequiredUniqueCount = if (draftSnapshot.dismissTask == AlarmDismissTaskType.QR_BARCODE_SCAN) {
                            draftSnapshot.qrRequiredUniqueCount
                        } else {
                            0
                        }
                    )
                }
            }
            _uiState.update { state -> state.copy(isSavingDuration = false) }
            saved?.let { alarm ->
                withContext(Dispatchers.IO) {
                    logDuration(TAG, "schedule_duration_${alarm.id}") {
                        durationScheduler.schedule(alarm)
                    }
                }
                resetDurationDraft()
                _uiState.update { state ->
                    state.copy(homeTab = AlarmHomeTab.Alarms)
                }
            }
        }
    }

    fun resetDurationDraft() {
        _uiState.update { state ->
            state.copy(
                durationDraft = sampleDurationDraft(
                    defaultTask = state.settings.defaultDismissTask,
                    defaultSound = state.settings.defaultRingtoneUri,
                    defaultDurationMinutes = state.settings.defaultCountdownDurationMinutes
                )
            )
        }
    }

    fun cancelCreation() {
        _uiState.update { state ->
            state.copy(
                draft = sampleDraft(
                    defaultTask = state.settings.defaultDismissTask,
                    defaultSound = state.settings.defaultRingtoneUri
                ),
                destination = AlarmDestination.List,
                editingAlarm = null
            )
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
                        draft = if (editingCleared == null) {
                            sampleDraft(
                                defaultTask = state.settings.defaultDismissTask,
                                defaultSound = state.settings.defaultRingtoneUri
                            )
                        } else state.draft,
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

    private fun synchronizeDurationAlarms(alarms: List<DurationAlarm>) {
        durationSynchronizeJob?.cancel()
        durationSynchronizeJob = viewModelScope.launch(Dispatchers.IO) {
            logDuration(TAG, "synchronize_duration_${alarms.size}") {
                durationScheduler.synchronize(alarms)
            }
        }
    }

    private fun DurationAlarmCreationState.withDurationMinutes(totalMinutes: Int): DurationAlarmCreationState {
        val clamped = totalMinutes.coerceIn(0, MAX_DURATION_HOURS * 60)
        val hours = clamped / 60
        val minutes = clamped % 60
        return copy(hours = hours, minutes = minutes)
    }

    companion object {
        private const val TAG = "AlarmViewModel"
        private const val MAX_DURATION_HOURS = 72
    }
}

data class AlarmUiState(
    val alarms: List<AlarmUiModel> = emptyList(),
    val wakeEvents: List<WakeEvent> = emptyList(),
    val settings: SettingsState = SettingsState(),
    val draft: AlarmCreationState = sampleDraft(
        defaultTask = settings.defaultDismissTask,
        defaultSound = settings.defaultRingtoneUri
    ),
    val durationAlarms: List<DurationAlarmUiModel> = emptyList(),
    val durationDraft: DurationAlarmCreationState = sampleDurationDraft(
        defaultTask = settings.defaultDismissTask,
        defaultSound = settings.defaultRingtoneUri,
        defaultDurationMinutes = settings.defaultCountdownDurationMinutes
    ),
    val destination: AlarmDestination = AlarmDestination.List,
    val homeTab: AlarmHomeTab = AlarmHomeTab.Alarms,
    val breakdownPeriod: BreakdownPeriod = BreakdownPeriod.Weekly,
    val editingAlarm: AlarmUiModel? = null,
    val selectedAlarmIds: Set<Int> = emptySet(),
    val isSavingDuration: Boolean = false
)

enum class AlarmHomeTab { Alarms, Duration, Breakdown }

enum class BreakdownPeriod { Weekly, Monthly }
