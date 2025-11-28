package hu.bbara.breakthesnooze.ui.alarm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hu.bbara.breakthesnooze.data.duration.repository.DurationAlarmRepository
import hu.bbara.breakthesnooze.data.settings.repository.SettingsRepository
import hu.bbara.breakthesnooze.ui.alarm.dismiss.AlarmDismissTaskType
import hu.bbara.breakthesnooze.ui.alarm.domain.CreateDurationAlarmUseCase
import hu.bbara.breakthesnooze.ui.alarm.domain.DeleteDurationAlarmUseCase
import hu.bbara.breakthesnooze.util.logDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DurationAlarmViewModel(
    private val repository: DurationAlarmRepository,
    private val settingsRepository: SettingsRepository,
    private val createDurationAlarmUseCase: CreateDurationAlarmUseCase,
    private val deleteDurationAlarmUseCase: DeleteDurationAlarmUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(
        DurationAlarmState(
            durationDraft = sampleDurationDraft(
                defaultTask = AlarmDismissTaskType.DEFAULT,
                defaultSound = null,
                defaultDurationMinutes = 0
            )
        )
    )
    val state: StateFlow<DurationAlarmState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _state.update { state ->
                    val draftTask = if (state.durationDraft.dismissTask == AlarmDismissTaskType.QR_BARCODE_SCAN) {
                        state.durationDraft.dismissTask
                    } else {
                        settings.defaultDismissTask
                    }
                    val draftSound = state.durationDraft.soundUri ?: settings.defaultRingtoneUri
                    state.copy(
                        durationDraft = state.durationDraft.copy(
                            dismissTask = draftTask,
                            soundUri = draftSound
                        ),
                        defaultDurationMinutes = settings.defaultCountdownDurationMinutes
                    )
                }
                prepareDurationDraft()
            }
        }

        viewModelScope.launch {
            repository.alarms.collect { alarms ->
                _state.update { state ->
                    state.copy(durationAlarms = alarms.map { it.toUiModel() })
                }
            }
        }
    }

    fun deleteDurationAlarm(id: Int) {
        viewModelScope.launch {
            val deleted = withContext(Dispatchers.IO) {
                logDuration(TAG, "delete_duration_$id") {
                    deleteDurationAlarmUseCase(id)
                }
            }
        }
    }

    fun setDurationLabel(label: String) {
        _state.update { state ->
            state.copy(durationDraft = state.durationDraft.copy(label = label))
        }
    }

    fun setDurationHours(hours: Int) {
        _state.update { state ->
            val sanitized = hours.coerceIn(0, MAX_DURATION_HOURS)
            state.copy(durationDraft = state.durationDraft.copy(hours = sanitized))
        }
    }

    fun setDurationMinutes(minutes: Int) {
        _state.update { state ->
            val sanitized = minutes.coerceIn(0, 59)
            state.copy(durationDraft = state.durationDraft.copy(minutes = sanitized))
        }
    }

    fun setDurationSound(uri: String?) {
        _state.update { state ->
            state.copy(durationDraft = state.durationDraft.copy(soundUri = uri))
        }
    }

    fun setDurationDismissTask(task: AlarmDismissTaskType) {
        _state.update { state ->
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
        _state.update { state ->
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
        _state.update { state ->
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
        _state.update { state ->
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

    fun saveDefaultDuration() {
        val totalMinutes = _state.value.durationDraft.totalMinutes
        if (totalMinutes <= 0) return
        viewModelScope.launch {
            settingsRepository.setDefaultCountdownDuration(totalMinutes)
        }
    }

    fun saveDurationDraft() {
        val draftSnapshot = _state.value.durationDraft
        val totalMinutes = draftSnapshot.totalMinutes
        if (totalMinutes <= 0 || _state.value.isSavingDuration) {
            return
        }
        _state.update { state -> state.copy(isSavingDuration = true) }
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                logDuration(TAG, "create_duration") {
                    createDurationAlarmUseCase(draftSnapshot.toDurationAlarm())
                }
            }
            _state.update { state -> state.copy(isSavingDuration = false) }
            saved.let {
                resetDurationDraft()
            }
        }
    }

    fun prepareDurationDraft() {
        _state.update { state ->
            val minutes = state.defaultDurationMinutes.coerceAtLeast(0)
            val hoursPart = minutes / 60
            val minutesPart = minutes % 60
            state.copy(
                durationDraft = state.durationDraft.copy(
                    hours = hoursPart,
                    minutes = minutesPart
                )
            )
        }
    }

    fun resetDurationDraft() {
        _state.update { state ->
            state.copy(
                durationDraft = sampleDurationDraft(
                    defaultTask = state.durationDraft.dismissTask,
                    defaultSound = state.durationDraft.soundUri,
                    defaultDurationMinutes = state.defaultDurationMinutes
                )
            )
        }
    }

    companion object {
        private const val TAG = "DurationAlarmVM"
        private const val MAX_DURATION_HOURS = 72
    }
}

data class DurationAlarmState(
    val durationAlarms: List<DurationAlarmUiModel> = emptyList(),
    val durationDraft: DurationAlarmCreationState = sampleDurationDraft(
        defaultTask = AlarmDismissTaskType.DEFAULT,
        defaultSound = null
    ),
    val defaultDurationMinutes: Int = 0,
    val isSavingDuration: Boolean = false
)
