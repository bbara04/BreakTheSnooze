package hu.bbara.breakthesnooze.data.alarm.duration

import hu.bbara.breakthesnooze.ui.alarm.dismiss.AlarmDismissTaskType
import java.time.Duration
import java.time.Instant

data class DurationAlarm(
    val id: Int,
    val label: String,
    val durationMinutes: Int,
    val createdAt: Instant,
    val triggerAt: Instant,
    val soundUri: String?,
    val dismissTask: AlarmDismissTaskType,
    val qrBarcodeValue: String?,
    val qrRequiredUniqueCount: Int
) {
    val duration: Duration = Duration.ofMinutes(durationMinutes.toLong())
}
