package hu.bbara.breakthesnooze.data.duration.scheduler

import hu.bbara.breakthesnooze.data.duration.model.DurationAlarm

interface DurationAlarmScheduler {
    fun schedule(alarm: DurationAlarm)
    fun cancel(alarmId: Int)
    fun synchronize(alarms: List<DurationAlarm>)
}
