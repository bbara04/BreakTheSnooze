package hu.bbara.viewideas.data.alarm

import hu.bbara.viewideas.ui.alarm.AlarmUiModel

interface AlarmScheduler {
    fun schedule(alarm: AlarmUiModel)
    fun cancel(alarmId: Int)
    fun synchronize(alarms: List<AlarmUiModel>)
}
