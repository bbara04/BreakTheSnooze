package hu.bbara.breakthesnooze.data.settings.model

import hu.bbara.breakthesnooze.data.settings.repository.DEFAULT_COUNTDOWN_DURATION_MINUTES
import hu.bbara.breakthesnooze.ui.alarm.dismiss.AlarmDismissTaskType

data class SettingsState(
    val defaultDismissTask: AlarmDismissTaskType = AlarmDismissTaskType.DEFAULT,
    val defaultRingtoneUri: String? = null,
    val debugModeEnabled: Boolean = false,
    val defaultCountdownDurationMinutes: Int = DEFAULT_COUNTDOWN_DURATION_MINUTES,
    val tightGapWarningEnabled: Boolean = true
)
