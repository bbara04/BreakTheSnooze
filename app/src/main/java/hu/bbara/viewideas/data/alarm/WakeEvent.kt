package hu.bbara.viewideas.data.alarm

import hu.bbara.viewideas.ui.alarm.dismiss.AlarmDismissTaskType
import java.time.Instant

data class WakeEvent(
    val id: Long,
    val alarmId: Int,
    val alarmLabel: String,
    val dismissTask: AlarmDismissTaskType,
    val completedAt: Instant
)
