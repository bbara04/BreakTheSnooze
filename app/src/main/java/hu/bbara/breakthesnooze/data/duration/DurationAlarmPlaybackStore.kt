package hu.bbara.breakthesnooze.data.duration

import hu.bbara.breakthesnooze.ui.alarm.AlarmUiModel
import java.util.concurrent.ConcurrentHashMap

object DurationAlarmPlaybackStore {
    private val cache = ConcurrentHashMap<Int, AlarmUiModel>()

    fun put(alarmId: Int, alarm: AlarmUiModel) {
        cache[alarmId] = alarm
    }

    fun get(alarmId: Int): AlarmUiModel? = cache[alarmId]

    fun remove(alarmId: Int) {
        cache.remove(alarmId)
    }
}
