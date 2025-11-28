package hu.bbara.breakthesnooze.data.duration.model

import hu.bbara.breakthesnooze.data.alarm.model.AlarmKind
import hu.bbara.breakthesnooze.data.alarm.model.uniqueAlarmId
import hu.bbara.breakthesnooze.ui.alarm.AlarmUiModel
import java.time.ZoneId

fun DurationAlarm.toAlarmUiModel(): AlarmUiModel {
    val uniqueId = uniqueAlarmId(AlarmKind.Duration, id)
    val triggerTime = triggerAt.atZone(ZoneId.systemDefault()).toLocalTime()
    return AlarmUiModel(
        id = uniqueId,
        time = triggerTime,
        label = label,
        isActive = true,
        repeatDays = emptySet(),
        soundUri = soundUri,
        dismissTask = dismissTask,
        qrBarcodeValue = qrBarcodeValue,
        qrRequiredUniqueCount = qrRequiredUniqueCount
    )
}
