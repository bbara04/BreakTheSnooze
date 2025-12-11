package hu.bbara.breakthesnooze.data.alarm.repository

import android.util.Log
import hu.bbara.breakthesnooze.data.alarm.db.AlarmDao
import hu.bbara.breakthesnooze.data.alarm.db.AlarmEntity
import hu.bbara.breakthesnooze.data.alarm.db.WakeEventDao
import hu.bbara.breakthesnooze.data.alarm.db.WakeEventEntity
import hu.bbara.breakthesnooze.data.alarm.model.WakeEvent
import hu.bbara.breakthesnooze.ui.alarm.dismiss.AlarmDismissTaskType
import hu.bbara.breakthesnooze.ui.alarm.model.AlarmCreationState
import hu.bbara.breakthesnooze.ui.alarm.model.AlarmUiModel
import hu.bbara.breakthesnooze.ui.alarm.model.dayOrder
import hu.bbara.breakthesnooze.ui.alarm.model.sampleAlarms
import hu.bbara.breakthesnooze.ui.alarm.model.timeFormatter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.temporal.ChronoUnit

interface AlarmRepository {
    val alarms: Flow<List<AlarmUiModel>>
    val wakeEvents: Flow<List<WakeEvent>>
    suspend fun upsertAlarm(alarm: AlarmUiModel): AlarmUiModel?
    suspend fun updateAlarmActive(id: Int, isActive: Boolean): AlarmUiModel?
    suspend fun deleteAlarm(id: Int)
    suspend fun ensureSeedData()
    suspend fun getAlarmById(id: Int): AlarmUiModel?
    suspend fun addWakeEvent(
        alarmId: Int,
        alarmLabel: String,
        dismissTask: AlarmDismissTaskType,
        completedAt: Instant = Instant.now()
    )
}

class DefaultAlarmRepository(
    private val alarmDao: AlarmDao,
    private val wakeEventDao: WakeEventDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AlarmRepository {

    override val alarms: Flow<List<AlarmUiModel>> =
        alarmDao.observeAlarms().map { entities ->
            entities.map { it.toUiModel() }
        }

    override val wakeEvents: Flow<List<WakeEvent>> =
        wakeEventDao.observeEvents().map { events ->
            events.map { it.toDomain() }
        }

    override suspend fun upsertAlarm(alarm: AlarmUiModel): AlarmUiModel? {
        return withContext(ioDispatcher) {
            Log.d(TAG, "upsert id=${alarm.id}")
            val entity = alarm.toEntity()
            val rowId = alarmDao.insertOrReplace(entity).toInt()
            val targetId = if (alarm.id == 0) rowId else alarm.id
            alarmDao.getById(targetId)?.toUiModel()
        }
    }

    override suspend fun updateAlarmActive(id: Int, isActive: Boolean): AlarmUiModel? {
        return withContext(ioDispatcher) {
            Log.d(TAG, "updateActive id=$id -> $isActive")
            val entity = alarmDao.getById(id) ?: return@withContext null
            val updated = entity.copy(isActive = isActive)
            alarmDao.update(updated)
            updated.toUiModel()
        }
    }

    override suspend fun deleteAlarm(id: Int) {
        withContext(ioDispatcher) {
            Log.d(TAG, "delete id=$id")
            alarmDao.deleteById(id)
        }
    }

    override suspend fun ensureSeedData() {
        withContext(ioDispatcher) {
            if (alarmDao.countAlarms() == 0) {
                sampleAlarms().map { alarm ->
                    alarm.copy(isActive = false)
                }.forEach { alarm ->
                    alarmDao.insertOrReplace(alarm.toEntity())
                }
            }
        }
    }

    override suspend fun getAlarmById(id: Int): AlarmUiModel? {
        return withContext(ioDispatcher) {
            alarmDao.getById(id)?.toUiModel()
        }
    }

    override suspend fun addWakeEvent(
        alarmId: Int,
        alarmLabel: String,
        dismissTask: AlarmDismissTaskType,
        completedAt: Instant
    ) {
        withContext(ioDispatcher) {
            wakeEventDao.insert(
                WakeEventEntity(
                    alarmId = alarmId,
                    alarmLabel = alarmLabel,
                    dismissTask = dismissTask.storageKey,
                    completedAt = completedAt.toEpochMilli()
                )
            )
            val retentionThreshold = Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS)
            wakeEventDao.deleteOlderThan(retentionThreshold.toEpochMilli())
        }
    }

    companion object {
        private const val TAG = "AlarmRepository"
        private const val RETENTION_DAYS = 365L
    }
}

private fun AlarmEntity.toUiModel(): AlarmUiModel {
    return AlarmUiModel(
        id = id,
        time = LocalTime.parse(time, timeFormatter),
        label = label,
        isActive = isActive,
        repeatDays = repeatDays.toDaySet(),
        soundUri = soundUri,
        dismissTask = AlarmDismissTaskType.fromStorageKey(dismissTask),
        qrBarcodeValue = qrBarcodeValue,
        qrRequiredUniqueCount = qrUniqueRequiredCount
    )
}

private fun AlarmUiModel.toEntity(): AlarmEntity {
    return AlarmEntity(
        id = id,
        time = time.format(timeFormatter),
        label = label,
        isActive = isActive,
        repeatDays = repeatDays.toStorageString(),
        soundUri = soundUri,
        dismissTask = dismissTask.storageKey,
        qrBarcodeValue = qrBarcodeValue,
        qrUniqueRequiredCount = qrRequiredUniqueCount
    )
}

private fun WakeEventEntity.toDomain(): WakeEvent {
    return WakeEvent(
        id = id,
        alarmId = alarmId,
        alarmLabel = alarmLabel,
        dismissTask = AlarmDismissTaskType.fromStorageKey(dismissTask),
        completedAt = Instant.ofEpochMilli(completedAt)
    )
}

private fun String.toDaySet(): Set<DayOfWeek> {
    if (isBlank()) return emptySet()
    return split(",").mapNotNull { token -> runCatching { DayOfWeek.valueOf(token) }.getOrNull() }
        .toCollection(linkedSetOf())
}

private fun Set<DayOfWeek>.toStorageString(): String {
    if (isEmpty()) return ""
    return this.sortedBy { dayOrder.indexOf(it) }.joinToString(separator = ",") { it.name }
}

fun AlarmCreationState.toUiModelWithId(
    id: Int = 0,
    isActive: Boolean = true
): AlarmUiModel? {
    val time = time ?: return null
    return AlarmUiModel(
        id = id,
        time = time,
        label = label,
        isActive = isActive,
        repeatDays = repeatDays,
        soundUri = soundUri,
        dismissTask = dismissTask,
        qrBarcodeValue = qrBarcodeValue,
        qrRequiredUniqueCount = qrRequiredUniqueCount
    )
}
