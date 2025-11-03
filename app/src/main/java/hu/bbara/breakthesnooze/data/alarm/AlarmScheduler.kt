package hu.bbara.breakthesnooze.data.alarm

import hu.bbara.breakthesnooze.ui.alarm.AlarmUiModel

interface AlarmScheduler {
    fun schedule(alarm: AlarmUiModel)
    fun cancel(alarmId: Int)
    fun synchronize(alarms: List<AlarmUiModel>)
}
