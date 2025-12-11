package hu.bbara.breakthesnooze.data.alarm.scheduler

import hu.bbara.breakthesnooze.ui.alarm.model.AlarmUiModel

interface AlarmScheduler {
    fun schedule(alarm: AlarmUiModel)
    fun cancel(alarmId: Int)
    fun synchronize(alarms: List<AlarmUiModel>)
}
