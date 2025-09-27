package hu.bbara.viewideas.data.alarm

import hu.bbara.viewideas.ui.alarm.AlarmCreationState
import hu.bbara.viewideas.ui.alarm.AlarmUiModel
import hu.bbara.viewideas.ui.alarm.dayOrder
import hu.bbara.viewideas.ui.alarm.sampleAlarms
import hu.bbara.viewideas.ui.alarm.timeFormatter
import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

interface AlarmRepository {
    val alarms: Flow<List<AlarmUiModel>>
    suspend fun upsertAlarm(alarm: AlarmUiModel): AlarmUiModel?
    suspend fun updateAlarmActive(id: Int, isActive: Boolean): AlarmUiModel?
    suspend fun deleteAlarm(id: Int)
    suspend fun ensureSeedData()
    suspend fun getAlarmById(id: Int): AlarmUiModel?
}

class DefaultAlarmRepository(
    private val alarmDao: AlarmDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AlarmRepository {

    override val alarms: Flow<List<AlarmUiModel>> =
        alarmDao.observeAlarms().map { entities ->
            entities.map { it.toUiModel() }
        }

    override suspend fun upsertAlarm(alarm: AlarmUiModel): AlarmUiModel? {
        return withContext(ioDispatcher) {
            val entity = alarm.toEntity()
            val rowId = alarmDao.insertOrReplace(entity).toInt()
            val targetId = if (alarm.id == 0) rowId else alarm.id
            alarmDao.getById(targetId)?.toUiModel()
        }
    }

    override suspend fun updateAlarmActive(id: Int, isActive: Boolean): AlarmUiModel? {
        return withContext(ioDispatcher) {
            val entity = alarmDao.getById(id) ?: return@withContext null
            val updated = entity.copy(isActive = isActive)
            alarmDao.update(updated)
            updated.toUiModel()
        }
    }

    override suspend fun deleteAlarm(id: Int) {
        withContext(ioDispatcher) {
            alarmDao.deleteById(id)
        }
    }

    override suspend fun ensureSeedData() {
        withContext(ioDispatcher) {
            if (alarmDao.countAlarms() == 0) {
                sampleAlarms().forEach { alarm ->
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
}

private fun AlarmEntity.toUiModel(): AlarmUiModel {
    return AlarmUiModel(
        id = id,
        time = LocalTime.parse(time, timeFormatter),
        label = label,
        isActive = isActive,
        repeatDays = repeatDays.toDaySet()
    )
}

private fun AlarmUiModel.toEntity(): AlarmEntity {
    return AlarmEntity(
        id = id,
        time = time.format(timeFormatter),
        label = label,
        isActive = isActive,
        repeatDays = repeatDays.toStorageString()
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
    if (repeatDays.isEmpty()) return null
    return AlarmUiModel(
        id = id,
        time = time,
        label = label,
        isActive = isActive,
        repeatDays = repeatDays
    )
}
