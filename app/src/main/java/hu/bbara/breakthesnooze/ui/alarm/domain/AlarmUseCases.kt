package hu.bbara.breakthesnooze.ui.alarm.domain

import hu.bbara.breakthesnooze.data.alarm.repository.AlarmRepository
import hu.bbara.breakthesnooze.data.alarm.repository.toUiModelWithId
import hu.bbara.breakthesnooze.data.alarm.scheduler.AlarmScheduler
import hu.bbara.breakthesnooze.data.duration.model.DurationAlarm
import hu.bbara.breakthesnooze.data.duration.repository.DurationAlarmRepository
import hu.bbara.breakthesnooze.data.duration.scheduler.DurationAlarmScheduler
import hu.bbara.breakthesnooze.ui.alarm.AlarmCreationState
import hu.bbara.breakthesnooze.ui.alarm.AlarmUiModel
import java.time.DayOfWeek

class ToggleAlarmActiveUseCase(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler
) {
    suspend operator fun invoke(id: Int, isActive: Boolean): AlarmUiModel? {
        val updated = repository.updateAlarmActive(id, isActive) ?: return null
        if (updated.isActive) {
            scheduler.schedule(updated)
        } else {
            scheduler.cancel(updated.id)
        }
        return updated
    }
}

class DeleteAlarmUseCase(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler
) {
    suspend operator fun invoke(id: Int): Boolean {
        val deleted = runCatching { repository.deleteAlarm(id) }.isSuccess
        if (deleted) {
            scheduler.cancel(id)
        }
        return deleted
    }
}

class SaveAlarmUseCase(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler
) {
    suspend operator fun invoke(
        draft: AlarmCreationState,
        editingId: Int?
    ): AlarmUiModel? {
        val model = draft.toUiModelWithId(
            id = editingId ?: 0,
            isActive = true
        ) ?: return null
        val saved = repository.upsertAlarm(model) ?: return null
        if (saved.isActive) {
            scheduler.schedule(saved)
        } else {
            scheduler.cancel(saved.id)
        }
        return saved
    }
}

class SynchronizeAlarmsUseCase(
    private val scheduler: AlarmScheduler
) {
    suspend operator fun invoke(alarms: List<AlarmUiModel>) {
        scheduler.synchronize(alarms)
    }
}

class DeleteDurationAlarmUseCase(
    private val repository: DurationAlarmRepository,
    private val scheduler: DurationAlarmScheduler
) {
    suspend operator fun invoke(id: Int): Boolean {
        val deleted = runCatching { repository.delete(id) }.isSuccess
        if (deleted) {
            scheduler.cancel(id)
        }
        return deleted
    }
}

class CreateDurationAlarmUseCase(
    private val repository: DurationAlarmRepository,
    private val scheduler: DurationAlarmScheduler
) {
    suspend operator fun invoke(
        alarm: DurationAlarm
    ): DurationAlarm {
        val saved = repository.create(
            durationMinutes = alarm.durationMinutes,
            label = alarm.label,
            soundUri = alarm.soundUri,
            dismissTask = alarm.dismissTask,
            qrBarcodeValue = alarm.qrBarcodeValue,
            qrRequiredUniqueCount = alarm.qrRequiredUniqueCount
        )
        saved?.let { scheduler.schedule(it) }
        return saved ?: alarm
    }
}

fun AlarmCreationState.withToggledDay(day: DayOfWeek): AlarmCreationState {
    val updated = if (repeatDays.contains(day)) repeatDays - day else repeatDays + day
    return copy(repeatDays = updated)
}
