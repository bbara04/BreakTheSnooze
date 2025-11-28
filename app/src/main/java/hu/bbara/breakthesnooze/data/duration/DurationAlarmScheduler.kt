package hu.bbara.breakthesnooze.data.duration

interface DurationAlarmScheduler {
    fun schedule(alarm: DurationAlarm)
    fun cancel(alarmId: Int)
    fun synchronize(alarms: List<DurationAlarm>)
}
