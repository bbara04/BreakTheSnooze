package hu.bbara.breakthesnooze.ui.alarm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hu.bbara.breakthesnooze.data.alarm.repository.AlarmRepository
import hu.bbara.breakthesnooze.data.settings.model.SettingsState
import hu.bbara.breakthesnooze.data.settings.repository.SettingsRepository
import hu.bbara.breakthesnooze.ui.alarm.dismiss.AlarmDismissTaskType
import hu.bbara.breakthesnooze.ui.alarm.domain.SaveAlarmUseCase
import hu.bbara.breakthesnooze.ui.alarm.domain.withToggledDay
import hu.bbara.breakthesnooze.util.logDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalTime
import javax.inject.Inject

@HiltViewModel
class AlarmEditorViewModel @Inject constructor(
    private val repository: AlarmRepository,
    settingsRepository: SettingsRepository,
    saveAlarmUseCase: SaveAlarmUseCase
) : ViewModel() {

    private val settingsStateFlow = MutableStateFlow(SettingsState())
    private val saveUseCase = saveAlarmUseCase

    private val _state = MutableStateFlow(
        AlarmEditorState(
            draft = sampleDraft(
                defaultTask = settingsStateFlow.value.defaultDismissTask,
                defaultSound = settingsStateFlow.value.defaultRingtoneUri
            )
        )
    )
    val state: StateFlow<AlarmEditorState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                settingsStateFlow.value = settings
                _state.update { state ->
                    state.copy(
                        draft = state.draft.copyDefaultsIfBlank(
                            task = settings.defaultDismissTask,
                            sound = settings.defaultRingtoneUri
                        )
                    )
                }
            }
        }

        viewModelScope.launch {
            repository.alarms.collect { alarms ->
                _state.update { state ->
                    val editing = state.editingAlarm
                    val updatedEditing = editing?.let { current ->
                        alarms.firstOrNull { it.id == current.id }
                    }
                    state.copy(editingAlarm = updatedEditing)
                }
            }
        }
    }

    fun startCreating() {
        val settings = settingsStateFlow.value
        _state.update { state ->
            state.copy(
                draft = sampleDraft(
                    defaultTask = settings.defaultDismissTask,
                    defaultSound = settings.defaultRingtoneUri
                ),
                destination = AlarmDestination.Create,
                editingAlarm = null
            )
        }
    }

    fun startEditing(id: Int) {
        viewModelScope.launch {
            val target = repository.getAlarmById(id) ?: return@launch
            _state.update {
                it.copy(
                    draft = target.toCreationState(),
                    destination = AlarmDestination.Create,
                    editingAlarm = target
                )
            }
        }
    }

    fun updateDraft(draft: AlarmCreationState) {
        _state.update { state -> state.copy(draft = draft) }
    }

    fun selectPresetTime(preset: LocalTime) {
        _state.update { state -> state.copy(draft = state.draft.copy(time = preset)) }
    }

    fun setDraftTime(time: LocalTime) {
        _state.update { state -> state.copy(draft = state.draft.copy(time = time)) }
    }

    fun setDraftSound(soundUri: String?) {
        _state.update { state -> state.copy(draft = state.draft.copy(soundUri = soundUri)) }
    }

    fun setDraftDismissTask(task: AlarmDismissTaskType) {
        _state.update { state ->
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
        _state.update { state ->
            state.copy(
                draft = state.draft.copy(
                    qrBarcodeValue = value,
                    qrRequiredUniqueCount = if (value.isNullOrBlank()) state.draft.qrRequiredUniqueCount else 0
                )
            )
        }
    }

    fun setDraftQrScanMode(mode: QrScanMode) {
        _state.update { state ->
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
        _state.update { state ->
            if (state.draft.dismissTask != AlarmDismissTaskType.QR_BARCODE_SCAN) return@update state
            val clamped = count.coerceIn(MIN_QR_UNIQUE_COUNT, MAX_QR_UNIQUE_COUNT)
            state.copy(draft = state.draft.copy(qrRequiredUniqueCount = clamped, qrBarcodeValue = null))
        }
    }

    fun toggleDraftDay(day: DayOfWeek) {
        _state.update { state -> state.copy(draft = state.draft.withToggledDay(day)) }
    }

    fun resetDraft() {
        _state.update { state ->
            val editing = state.editingAlarm
            if (editing != null) {
                state.copy(draft = editing.toCreationState())
            } else {
                val settings = settingsStateFlow.value
                state.copy(
                    draft = sampleDraft(
                        defaultTask = settings.defaultDismissTask,
                        defaultSound = settings.defaultRingtoneUri
                    )
                )
            }
        }
    }

    fun saveDraft() {
        val draftSnapshot = _state.value.draft
        val editing = _state.value.editingAlarm
        viewModelScope.launch {
            val settings = settingsStateFlow.value
            val saved = withContext(Dispatchers.IO) {
                logDuration(TAG, "upsert_${editing?.id ?: 0}") {
                    saveUseCase(
                        draft = draftSnapshot,
                        editingId = editing?.id
                    )
                }
            }
            saved?.let {
                _state.update { state ->
                    state.copy(
                        draft = sampleDraft(
                            defaultTask = settings.defaultDismissTask,
                            defaultSound = settings.defaultRingtoneUri
                        ),
                        destination = AlarmDestination.List,
                        editingAlarm = null
                    )
                }
            }
        }
    }

    fun cancelCreation() {
        val settings = settingsStateFlow.value
        _state.update { state ->
            state.copy(
                draft = sampleDraft(
                    defaultTask = settings.defaultDismissTask,
                    defaultSound = settings.defaultRingtoneUri
                ),
                destination = AlarmDestination.List,
                editingAlarm = null
            )
        }
    }

    fun closeSettings() {
        _state.update { state -> state.copy(destination = AlarmDestination.List) }
    }

    fun openSettings() {
        _state.update { state -> state.copy(destination = AlarmDestination.Settings) }
    }

    companion object {
        private const val TAG = "AlarmEditorViewModel"
    }
}

data class AlarmEditorState(
    val draft: AlarmCreationState = sampleDraft(),
    val destination: AlarmDestination = AlarmDestination.List,
    val editingAlarm: AlarmUiModel? = null
)

private fun AlarmCreationState.copyDefaultsIfBlank(task: AlarmDismissTaskType, sound: String?): AlarmCreationState {
    return copy(
        dismissTask = dismissTask.takeIf { it != AlarmDismissTaskType.QR_BARCODE_SCAN } ?: task,
        soundUri = soundUri ?: sound
    )
}
