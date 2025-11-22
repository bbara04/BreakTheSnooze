package hu.bbara.breakthesnooze.ui.alarm

import hu.bbara.breakthesnooze.data.alarm.calculateNextTrigger
import hu.bbara.breakthesnooze.ui.alarm.dismiss.AlarmDismissTaskType
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

enum class AlarmDestination { List, Create, Settings }

enum class QrScanMode { SpecificCode, UniqueCodes }

internal const val MIN_QR_UNIQUE_COUNT: Int = 2
internal const val MAX_QR_UNIQUE_COUNT: Int = 5
internal const val DEFAULT_QR_UNIQUE_COUNT: Int = 3

internal val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val timeFormatter12Hour: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

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
    val repeatDays: Set<DayOfWeek>,
    val soundUri: String?,
    val dismissTask: AlarmDismissTaskType,
    val qrBarcodeValue: String?,
    val qrRequiredUniqueCount: Int
)

data class AlarmCreationState(
    val time: LocalTime?,
    val label: String,
    val repeatDays: Set<DayOfWeek>,
    val soundUri: String?,
    val dismissTask: AlarmDismissTaskType,
    val qrBarcodeValue: String?,
    val qrRequiredUniqueCount: Int
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
        repeatDays = repeatDays.toSet(),
        soundUri = soundUri,
        dismissTask = dismissTask,
        qrBarcodeValue = qrBarcodeValue,
        qrRequiredUniqueCount = qrRequiredUniqueCount
    )

internal fun LocalTime.formatForDisplay(is24Hour: Boolean): String {
    val formatter = if (is24Hour) timeFormatter else timeFormatter12Hour
    return format(formatter)
}

internal fun sampleAlarms(): List<AlarmUiModel> {
    return listOf(
        AlarmUiModel(
            id = 1,
            time = LocalTime.of(7, 0),
            label = "Morning run",
            isActive = true,
            repeatDays = dayOrder.take(5).toSet(),
            soundUri = null,
            dismissTask = AlarmDismissTaskType.OBJECT_DETECTION,
            qrBarcodeValue = null,
            qrRequiredUniqueCount = 0
        ),
        AlarmUiModel(
            id = 2,
            time = LocalTime.of(8, 30),
            label = "Team standup",
            isActive = true,
            repeatDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
            soundUri = null,
            dismissTask = AlarmDismissTaskType.MATH_CHALLENGE,
            qrBarcodeValue = null,
            qrRequiredUniqueCount = 0
        ),
        AlarmUiModel(
            id = 3,
            time = LocalTime.of(22, 0),
            label = "Wind down",
            isActive = false,
            repeatDays = setOf(DayOfWeek.SUNDAY),
            soundUri = null,
            dismissTask = AlarmDismissTaskType.FOCUS_TIMER,
            qrBarcodeValue = null,
            qrRequiredUniqueCount = 0
        )
    ).sortedWith(alarmSorter)
}

internal fun defaultAlarmTime(): LocalTime {
    return LocalTime.now().plusMinutes(1).withSecond(0).withNano(0)
}

internal fun sampleDraft(
    useCurrentTime: Boolean = true,
    defaultTask: AlarmDismissTaskType = AlarmDismissTaskType.DEFAULT,
    defaultSound: String? = null
): AlarmCreationState = AlarmCreationState(
    time = if (useCurrentTime) defaultAlarmTime() else LocalTime.of(6, 30),
    label = "",
    repeatDays = emptySet(),
    soundUri = defaultSound,
    dismissTask = defaultTask,
    qrBarcodeValue = null,
    qrRequiredUniqueCount = 0
)

internal fun resolveNextAlarm(
    alarms: List<AlarmUiModel>,
    durationAlarms: List<DurationAlarmUiModel> = emptyList()
): UpcomingAlarm? {
    val zoneId = ZoneId.systemDefault()
    val now = LocalDateTime.now(zoneId)
    val standardUpcoming = alarms
        .filter { it.isActive }
        .mapNotNull { alarm ->
            calculateNextTrigger(alarm, now)?.let { trigger ->
                UpcomingAlarm(
                    alarm = alarm,
                    triggerAt = trigger,
                    remaining = Duration.between(now, trigger)
                )
            }
        }
    val durationUpcoming = durationAlarms.mapNotNull { alarm ->
        val trigger = LocalDateTime.ofInstant(alarm.triggerAt, zoneId)
        if (trigger.isBefore(now)) {
            null
        } else {
            UpcomingAlarm(
                alarm = alarm.toAlarmUiModel(),
                triggerAt = trigger,
                remaining = Duration.between(now, trigger)
            )
        }
    }
    return (standardUpcoming + durationUpcoming)
        .minByOrNull { it.remaining.toMinutes().coerceAtLeast(0) }
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
