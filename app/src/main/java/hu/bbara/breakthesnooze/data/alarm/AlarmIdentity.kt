package hu.bbara.breakthesnooze.data.alarm

enum class AlarmKind { Standard, Duration }

const val DURATION_ALARM_ID_OFFSET: Int = 1_000_000_000

fun uniqueAlarmId(kind: AlarmKind, rawId: Int): Int {
    return when (kind) {
        AlarmKind.Standard -> rawId
        AlarmKind.Duration -> DURATION_ALARM_ID_OFFSET + rawId
    }
}

fun detectAlarmKind(uniqueId: Int): AlarmKind {
    return if (uniqueId >= DURATION_ALARM_ID_OFFSET) {
        AlarmKind.Duration
    } else {
        AlarmKind.Standard
    }
}

fun rawAlarmIdFromUnique(uniqueId: Int): Int {
    return if (detectAlarmKind(uniqueId) == AlarmKind.Duration) {
        uniqueId - DURATION_ALARM_ID_OFFSET
    } else {
        uniqueId
    }
}
