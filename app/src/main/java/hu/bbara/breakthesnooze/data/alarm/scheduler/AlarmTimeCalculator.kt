package hu.bbara.breakthesnooze.data.alarm.scheduler

import hu.bbara.breakthesnooze.ui.alarm.AlarmUiModel
import java.time.LocalDateTime
import java.time.ZoneId

fun calculateNextTrigger(
    alarm: AlarmUiModel,
    reference: LocalDateTime = LocalDateTime.now(ZoneId.systemDefault())
): LocalDateTime? {
    if (alarm.repeatDays.isEmpty()) {
        val candidate = reference.withHour(alarm.time.hour).withMinute(alarm.time.minute).withSecond(0).withNano(0)
        return if (candidate.isAfter(reference)) candidate else candidate.plusDays(1)
    }

    val today = reference.toLocalDate()
    val nowTime = reference.toLocalTime()
    var soonest: LocalDateTime? = null

    for (day in alarm.repeatDays) {
        val dayDifference = ((day.value - reference.dayOfWeek.value) + 7) % 7
        val date = today.plusDays(dayDifference.toLong())
        var candidate = LocalDateTime.of(date, alarm.time)
        if (dayDifference == 0 && alarm.time <= nowTime) {
            candidate = candidate.plusDays(7)
        }
        if (soonest == null || candidate.isBefore(soonest)) {
            soonest = candidate
        }
    }

    return soonest
}

fun calculateNextTriggerMillis(alarm: AlarmUiModel, reference: LocalDateTime = LocalDateTime.now()): Long? {
    return calculateNextTrigger(alarm, reference)
        ?.atZone(ZoneId.systemDefault())
        ?.toInstant()
        ?.toEpochMilli()
}
