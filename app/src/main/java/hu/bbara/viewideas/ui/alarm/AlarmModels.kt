package hu.bbara.viewideas.ui.alarm

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

enum class AlarmDestination { List, Create }

internal val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

internal val dayOrder = listOf(
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY,
    DayOfWeek.SATURDAY,
    DayOfWeek.SUNDAY
)

data class AlarmUiModel(
    val id: Int,
    val time: LocalTime,
    val label: String,
    val isActive: Boolean,
    val repeatDays: Set<DayOfWeek>
)

data class AlarmCreationState(
    val time: LocalTime?,
    val label: String,
    val repeatDays: Set<DayOfWeek>
)

data class UpcomingAlarm(
    val alarm: AlarmUiModel,
    val triggerAt: LocalDateTime,
    val remaining: Duration
)

internal val alarmSorter = compareByDescending<AlarmUiModel> { it.isActive }.thenBy { it.time }

internal fun AlarmUiModel.toCreationState(): AlarmCreationState =
    AlarmCreationState(
        time = time,
        label = label,
        repeatDays = repeatDays.toSet()
    )

internal fun sampleAlarms(): List<AlarmUiModel> {
    return listOf(
        AlarmUiModel(
            id = 1,
            time = LocalTime.of(7, 0),
            label = "Morning run",
            isActive = true,
            repeatDays = dayOrder.take(5).toSet()
        ),
        AlarmUiModel(
            id = 2,
            time = LocalTime.of(8, 30),
            label = "Team standup",
            isActive = true,
            repeatDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)
        ),
        AlarmUiModel(
            id = 3,
            time = LocalTime.of(22, 0),
            label = "Wind down",
            isActive = false,
            repeatDays = setOf(DayOfWeek.SUNDAY)
        )
    ).sortedWith(alarmSorter)
}

internal fun sampleDraft(): AlarmCreationState = AlarmCreationState(
    time = LocalTime.of(6, 30),
    label = "Gym",
    repeatDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
)

internal fun resolveNextAlarm(alarms: List<AlarmUiModel>): UpcomingAlarm? {
    val now = LocalDateTime.now()
    return alarms
        .filter { it.isActive }
        .mapNotNull { alarm ->
            nextTriggerFrom(alarm, now)?.let { trigger ->
                UpcomingAlarm(
                    alarm = alarm,
                    triggerAt = trigger,
                    remaining = Duration.between(now, trigger)
                )
            }
        }
        .minByOrNull { it.remaining.toMinutes().coerceAtLeast(0) }
}

private fun nextTriggerFrom(alarm: AlarmUiModel, reference: LocalDateTime): LocalDateTime? {
    val days = if (alarm.repeatDays.isEmpty()) setOf(reference.dayOfWeek) else alarm.repeatDays
    val today = reference.toLocalDate()
    val nowTime = reference.toLocalTime()

    var soonest: LocalDateTime? = null

    for (day in days) {
        val dayDifference = ((day.value - reference.dayOfWeek.value) + 7) % 7
        var date = today.plusDays(dayDifference.toLong())
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

internal fun formatRemaining(duration: Duration): String {
    val totalMinutes = duration.toMinutes().coerceAtLeast(0)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    val parts = buildList {
        if (hours > 0) add("${hours}h")
        if (minutes > 0 || hours == 0L) add("${minutes}m")
    }
    return "In ${parts.joinToString(" ")}" + if (duration.isNegative) " (passed)" else ""
}

internal fun formatDays(days: Set<DayOfWeek>): String {
    if (days.isEmpty()) {
        return "Once"
    }
    return if (days.size == 7) {
        "Every day"
    } else if (days == dayOrder.take(5).toSet()) {
        "Weekdays"
    } else if (days == dayOrder.takeLast(2).toSet()) {
        "Weekends"
    } else {
        days.sortedBy { dayOrder.indexOf(it) }
            .joinToString(separator = ", ") { it.displayName() }
    }
}

internal fun DayOfWeek.displayName(): String =
    getDisplayName(TextStyle.SHORT, Locale.getDefault()).replaceFirstChar { it.titlecase(Locale.getDefault()) }
