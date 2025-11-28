package hu.bbara.breakthesnooze.data.alarm

import hu.bbara.breakthesnooze.data.alarm.db.AlarmDao
import hu.bbara.breakthesnooze.data.alarm.db.AlarmEntity
import hu.bbara.breakthesnooze.data.alarm.db.WakeEventDao
import hu.bbara.breakthesnooze.data.alarm.db.WakeEventEntity
import hu.bbara.breakthesnooze.data.alarm.repository.DefaultAlarmRepository
import hu.bbara.breakthesnooze.ui.alarm.AlarmUiModel
import hu.bbara.breakthesnooze.ui.alarm.dismiss.AlarmDismissTaskType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultAlarmRepositoryTest {

    @Test
    fun `upsertAlarm inserts new alarm and exposes it through flow`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val alarmDao = FakeAlarmDao()
        val wakeEventDao = FakeWakeEventDao()
        val repository = DefaultAlarmRepository(alarmDao, wakeEventDao, dispatcher)

        val saved = repository.upsertAlarm(sampleAlarm(id = 0))

        assertNotNull(saved)
        val savedAlarm = saved!!
        assertTrue(savedAlarm.id > 0)
        val observed = repository.alarms.filter { it.isNotEmpty() }.first()
        assertEquals(listOf(savedAlarm), observed)
    }

    @Test
    fun `updateAlarmActive toggles alarm state`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val alarmDao = FakeAlarmDao()
        val wakeEventDao = FakeWakeEventDao()
        val repository = DefaultAlarmRepository(alarmDao, wakeEventDao, dispatcher)
        val saved = repository.upsertAlarm(sampleAlarm(id = 0, isActive = true))!!

        val updated = repository.updateAlarmActive(saved.id, false)

        assertNotNull(updated)
        val observed = repository.alarms.filter { it.any { alarm -> alarm.id == saved.id } }.first()
        assertEquals(false, observed.first { it.id == saved.id }.isActive)
    }

    @Test
    fun `deleteAlarm removes alarm from flow`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val alarmDao = FakeAlarmDao()
        val wakeEventDao = FakeWakeEventDao()
        val repository = DefaultAlarmRepository(alarmDao, wakeEventDao, dispatcher)
        val saved = repository.upsertAlarm(sampleAlarm(id = 0))!!

        repository.deleteAlarm(saved.id)

        val observed = repository.alarms.first()
        assertTrue(observed.isEmpty())
        assertNull(alarmDao.getById(saved.id))
    }

    @Test
    fun `ensureSeedData populates repository when empty`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val alarmDao = FakeAlarmDao()
        val wakeEventDao = FakeWakeEventDao()
        val repository = DefaultAlarmRepository(alarmDao, wakeEventDao, dispatcher)

        repository.ensureSeedData()

        val observed = repository.alarms.filter { it.isNotEmpty() }.first()
        assertTrue(observed.isNotEmpty())
        assertTrue(observed.all { !it.isActive })
    }

    @Test
    fun `addWakeEvent stores event and enforces retention`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val alarmDao = FakeAlarmDao()
        val wakeEventDao = FakeWakeEventDao()
        val repository = DefaultAlarmRepository(alarmDao, wakeEventDao, dispatcher)

        // Pre-populate with an old event that should be cleaned up.
        val oldInstant = Instant.now().minus(500, ChronoUnit.DAYS)
        wakeEventDao.insert(
            WakeEventEntity(
                id = 1,
                alarmId = 99,
                alarmLabel = "Old alarm",
                dismissTask = AlarmDismissTaskType.DEFAULT.storageKey,
                completedAt = oldInstant.toEpochMilli()
            )
        )

        repository.addWakeEvent(
            alarmId = 5,
            alarmLabel = "Morning alarm",
            dismissTask = AlarmDismissTaskType.FOCUS_TIMER,
            completedAt = Instant.now()
        )

        val observed = repository.wakeEvents.filter { it.isNotEmpty() }.first()
        assertEquals(1, observed.size)
        val event = observed.first()
        assertEquals(5, event.alarmId)
        assertEquals("Morning alarm", event.alarmLabel)
        assertEquals(AlarmDismissTaskType.FOCUS_TIMER, event.dismissTask)
    }

    private fun sampleAlarm(
        id: Int,
        isActive: Boolean = true,
        time: LocalTime = LocalTime.of(6, 30)
    ): AlarmUiModel {
        return AlarmUiModel(
            id = id,
            time = time,
            label = "Alarm $id",
            isActive = isActive,
            repeatDays = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
            soundUri = "content://alarm/$id",
            dismissTask = AlarmDismissTaskType.MATH_CHALLENGE,
            qrBarcodeValue = null,
            qrRequiredUniqueCount = 0
        )
    }
}

private class FakeAlarmDao : AlarmDao {
    private val data = mutableListOf<AlarmEntity>()
    private val flow = MutableStateFlow<List<AlarmEntity>>(emptyList())
    private var nextId = 1

    override fun observeAlarms(): Flow<List<AlarmEntity>> = flow

    override suspend fun insertOrReplace(entity: AlarmEntity): Long {
        val targetId = if (entity.id == 0) nextId++ else entity.id
        val stored = entity.copy(id = targetId)
        val index = data.indexOfFirst { it.id == targetId }
        if (index >= 0) {
            data[index] = stored
        } else {
            data.add(stored)
        }
        publish()
        return targetId.toLong()
    }

    override suspend fun update(entity: AlarmEntity) {
        val index = data.indexOfFirst { it.id == entity.id }
        if (index >= 0) {
            data[index] = entity
            publish()
        }
    }

    override suspend fun deleteById(id: Int) {
        data.removeAll { it.id == id }
        publish()
    }

    override suspend fun getById(id: Int): AlarmEntity? {
        return data.firstOrNull { it.id == id }
    }

    override suspend fun countAlarms(): Int = data.size

    private fun publish() {
        flow.value = data
    }
}

private class FakeWakeEventDao : WakeEventDao {
    private val events = mutableListOf<WakeEventEntity>()
    private val flow = MutableStateFlow<List<WakeEventEntity>>(emptyList())
    private var nextId = 1L

    override suspend fun insert(event: WakeEventEntity): Long {
        val targetId = if (event.id == 0L) nextId++ else event.id
        val stored = event.copy(id = targetId)
        events.add(stored)
        flow.value = events.sortedByDescending { it.completedAt }
        return targetId
    }

    override fun observeEvents(): Flow<List<WakeEventEntity>> = flow

    override suspend fun deleteOlderThan(thresholdMillis: Long) {
        events.removeAll { it.completedAt < thresholdMillis }
        flow.value = events.sortedByDescending { it.completedAt }
    }
}
