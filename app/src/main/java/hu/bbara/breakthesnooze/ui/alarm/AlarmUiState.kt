package hu.bbara.breakthesnooze.ui.alarm

import hu.bbara.breakthesnooze.data.alarm.WakeEvent
import hu.bbara.breakthesnooze.data.settings.SettingsState

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
