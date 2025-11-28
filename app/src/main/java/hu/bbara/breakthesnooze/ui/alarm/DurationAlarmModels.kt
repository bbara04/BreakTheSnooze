package hu.bbara.breakthesnooze.ui.alarm

import hu.bbara.breakthesnooze.data.alarm.AlarmKind
import hu.bbara.breakthesnooze.data.alarm.uniqueAlarmId
import hu.bbara.breakthesnooze.data.duration.DurationAlarm
import hu.bbara.breakthesnooze.data.settings.DEFAULT_COUNTDOWN_DURATION_MINUTES
import hu.bbara.breakthesnooze.ui.alarm.dismiss.AlarmDismissTaskType
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

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

fun DurationAlarmCreationState.toDurationAlarm(): DurationAlarm {
    val now = Instant.now()
    return DurationAlarm(
        id = 0,
        label = label.ifBlank { "Alarm" },
        durationMinutes = totalMinutes,
        createdAt = now,
        triggerAt = now.plusSeconds(totalMinutes * 60L),
        soundUri = soundUri,
        dismissTask = dismissTask,
        qrBarcodeValue = if (dismissTask == AlarmDismissTaskType.QR_BARCODE_SCAN) qrBarcodeValue else null,
        qrRequiredUniqueCount = if (dismissTask == AlarmDismissTaskType.QR_BARCODE_SCAN) qrRequiredUniqueCount else 0
    )
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
    defaultSound: String?,
    defaultDurationMinutes: Int = DEFAULT_COUNTDOWN_DURATION_MINUTES
): DurationAlarmCreationState {
    val sanitizedMinutes = defaultDurationMinutes.coerceIn(0, 99 * 60 + 59)
    val hours = sanitizedMinutes / 60
    val minutes = sanitizedMinutes % 60
    return DurationAlarmCreationState(
        hours = hours,
        minutes = minutes,
        label = "",
        soundUri = defaultSound,
        dismissTask = defaultTask,
        qrBarcodeValue = null,
        qrRequiredUniqueCount = 0
    )
}

internal fun DurationAlarmUiModel.toAlarmUiModel(): AlarmUiModel {
    val localTime = triggerAt.atZone(ZoneId.systemDefault()).toLocalTime()
    return AlarmUiModel(
        id = uniqueAlarmId(AlarmKind.Duration, id),
        time = localTime,
        label = label,
        isActive = true,
        repeatDays = emptySet(),
        soundUri = soundUri,
        dismissTask = dismissTask,
        qrBarcodeValue = qrBarcodeValue,
        qrRequiredUniqueCount = qrRequiredUniqueCount
    )
}
