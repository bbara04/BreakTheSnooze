package hu.bbara.breakthesnooze.data.duration

import hu.bbara.breakthesnooze.ui.alarm.dismiss.AlarmDismissTaskType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.temporal.ChronoUnit

interface DurationAlarmRepository {
    val alarms: Flow<List<DurationAlarm>>
    suspend fun create(
        durationMinutes: Int,
        label: String,
        soundUri: String?,
        dismissTask: AlarmDismissTaskType,
        qrBarcodeValue: String?,
        qrRequiredUniqueCount: Int
    ): DurationAlarm?

    suspend fun delete(id: Int)
    suspend fun getById(id: Int): DurationAlarm?
}

class DefaultDurationAlarmRepository(
    private val dao: DurationAlarmDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : DurationAlarmRepository {

    override val alarms: Flow<List<DurationAlarm>> =
        dao.observeDurationAlarms().map { entities -> entities.map { it.toDomain() } }

    override suspend fun create(
        durationMinutes: Int,
        label: String,
        soundUri: String?,
        dismissTask: AlarmDismissTaskType,
        qrBarcodeValue: String?,
        qrRequiredUniqueCount: Int
    ): DurationAlarm? {
        if (durationMinutes <= 0) return null
        return withContext(ioDispatcher) {
            val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
            val triggerAt = now.plus(durationMinutes.toLong(), ChronoUnit.MINUTES)
            val entity = DurationAlarmEntity(
                label = label.ifBlank { DEFAULT_LABEL },
                durationMinutes = durationMinutes,
                createdAtMillis = now.toEpochMilli(),
                triggerAtMillis = triggerAt.toEpochMilli(),
                soundUri = soundUri,
                dismissTask = dismissTask.storageKey,
                qrBarcodeValue = qrBarcodeValue,
                qrUniqueRequiredCount = qrRequiredUniqueCount
            )
            val insertedId = dao.insert(entity).toInt()
            dao.getById(insertedId)?.toDomain()
        }
    }

    override suspend fun delete(id: Int) {
        withContext(ioDispatcher) {
            dao.deleteById(id)
        }
    }

    override suspend fun getById(id: Int): DurationAlarm? {
        return withContext(ioDispatcher) {
            dao.getById(id)?.toDomain()
        }
    }

    private fun DurationAlarmEntity.toDomain(): DurationAlarm {
        return DurationAlarm(
            id = id,
            label = label,
            durationMinutes = durationMinutes,
            createdAt = Instant.ofEpochMilli(createdAtMillis),
            triggerAt = Instant.ofEpochMilli(triggerAtMillis),
            soundUri = soundUri,
            dismissTask = AlarmDismissTaskType.fromStorageKey(dismissTask),
            qrBarcodeValue = qrBarcodeValue,
            qrRequiredUniqueCount = qrUniqueRequiredCount
        )
    }

    private companion object {
        private const val DEFAULT_LABEL = "Alarm"
    }
}
