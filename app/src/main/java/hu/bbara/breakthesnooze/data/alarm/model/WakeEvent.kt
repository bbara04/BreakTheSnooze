package hu.bbara.breakthesnooze.data.alarm.model

import hu.bbara.breakthesnooze.ui.alarm.dismiss.AlarmDismissTaskType
import java.time.Instant

data class WakeEvent(
    val id: Long,
    val alarmId: Int,
    val alarmLabel: String,
    val dismissTask: AlarmDismissTaskType,
    val completedAt: Instant
)
