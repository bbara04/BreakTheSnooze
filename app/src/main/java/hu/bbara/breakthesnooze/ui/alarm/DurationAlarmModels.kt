package hu.bbara.breakthesnooze.ui.alarm

import hu.bbara.breakthesnooze.data.alarm.duration.DurationAlarm
import hu.bbara.breakthesnooze.ui.alarm.dismiss.AlarmDismissTaskType
import java.time.Duration
import java.time.Instant

data class DurationAlarmUiModel(
    val id: Int,
    val label: String,
    val triggerAt: Instant,
    val duration: Duration,
    val soundUri: String?,
    val dismissTask: AlarmDismissTaskType,
    val qrBarcodeValue: String?,
    val qrRequiredUniqueCount: Int
)

data class DurationAlarmCreationState(
    val hours: Int,
    val minutes: Int,
    val label: String,
    val soundUri: String?,
    val dismissTask: AlarmDismissTaskType,
    val qrBarcodeValue: String?,
    val qrRequiredUniqueCount: Int
) {
    val totalMinutes: Int
        get() = (hours.coerceAtLeast(0) * 60) + minutes.coerceIn(0, 59)
}

fun DurationAlarm.toUiModel(): DurationAlarmUiModel {
    return DurationAlarmUiModel(
        id = id,
        label = label,
        triggerAt = triggerAt,
        duration = duration,
        soundUri = soundUri,
        dismissTask = dismissTask,
        qrBarcodeValue = qrBarcodeValue,
        qrRequiredUniqueCount = qrRequiredUniqueCount
    )
}

fun sampleDurationDraft(
    defaultTask: AlarmDismissTaskType,
    defaultSound: String?
): DurationAlarmCreationState {
    return DurationAlarmCreationState(
        hours = 1,
        minutes = 0,
        label = "Power nap",
        soundUri = defaultSound,
        dismissTask = defaultTask,
        qrBarcodeValue = null,
        qrRequiredUniqueCount = 0
    )
}
