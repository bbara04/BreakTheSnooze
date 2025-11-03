package hu.bbara.breakthesnooze.ui.alarm

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import hu.bbara.breakthesnooze.MainDispatcherRule
import hu.bbara.breakthesnooze.data.alarm.AlarmRepository
import hu.bbara.breakthesnooze.data.alarm.AlarmScheduler
import hu.bbara.breakthesnooze.data.alarm.WakeEvent
import hu.bbara.breakthesnooze.data.settings.SettingsRepository
import hu.bbara.breakthesnooze.ui.alarm.dismiss.AlarmDismissTaskType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class AlarmViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val tempDirs = mutableListOf<File>()
    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
        scopes.clear()
        tempDirs.forEach { it.deleteRecursively() }
        tempDirs.clear()
    }

    @Test
    fun `startCreating uses settings defaults`() = runTest {
        val settingsRepository = createSettingsRepository()
        val repository = FakeAlarmRepository()
        val scheduler = FakeAlarmScheduler()

        val viewModel = AlarmViewModel(repository, scheduler, settingsRepository)
        advanceUntilIdle()

        viewModel.startCreating()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(AlarmDestination.Create, state.destination)
        assertEquals(AlarmDismissTaskType.DEFAULT, state.draft.dismissTask)
    }

    @Test
    fun `onToggleAlarm updates state and cancels when disabling`() = runTest {
        val settingsRepository = createSettingsRepository()
        val alarm = sampleAlarm(id = 1, isActive = true)
        val repository = FakeAlarmRepository(listOf(alarm))
        val scheduler = FakeAlarmScheduler()
        val viewModel = AlarmViewModel(repository, scheduler, settingsRepository)
        advanceUntilIdle()

        viewModel.onToggleAlarm(1, false)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.alarms.first().isActive)
    }

    private fun sampleAlarm(
        id: Int,
        isActive: Boolean
    ): AlarmUiModel = AlarmUiModel(
        id = id,
        time = LocalTime.of(6, 45),
        label = "Alarm $id",
        isActive = isActive,
        repeatDays = setOf(DayOfWeek.MONDAY),
        soundUri = null,
        dismissTask = AlarmDismissTaskType.DEFAULT,
        qrBarcodeValue = null,
        qrRequiredUniqueCount = 0
    )

    private fun createSettingsRepository(): SettingsRepository {
        val dir = Files.createTempDirectory("alarm-vm-settings").toFile()
        tempDirs.add(dir)
        val scope = CoroutineScope(mainDispatcherRule.dispatcher + SupervisorJob())
        scopes.add(scope)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { File(dir, "settings.preferences_pb") }
        )
        return SettingsRepository(dataStore)
    }
}

private class FakeAlarmRepository(
    initialAlarms: List<AlarmUiModel> = emptyList()
) : AlarmRepository {

    private val alarmsFlow = MutableStateFlow(initialAlarms)
    private val wakeEventsFlow = MutableStateFlow<List<WakeEvent>>(emptyList())
    private var nextWakeEventId = 1L

    override val alarms: Flow<List<AlarmUiModel>> = alarmsFlow
    override val wakeEvents: Flow<List<WakeEvent>> = wakeEventsFlow

    override suspend fun upsertAlarm(alarm: AlarmUiModel): AlarmUiModel? {
        val assigned = if (alarm.id == 0) {
            val nextId = (alarmsFlow.value.maxOfOrNull { it.id } ?: 0) + 1
            alarm.copy(id = nextId)
        } else {
            alarm
        }
        val updated = alarmsFlow.value.filterNot { it.id == assigned.id } + assigned
        alarmsFlow.value = updated.sortedBy { it.id }
        return assigned
    }

    override suspend fun updateAlarmActive(id: Int, isActive: Boolean): AlarmUiModel? {
        val updated = alarmsFlow.value.map { alarm ->
            if (alarm.id == id) alarm.copy(isActive = isActive) else alarm
        }
        alarmsFlow.value = updated
        return updated.firstOrNull { it.id == id }
    }

    override suspend fun deleteAlarm(id: Int) {
        alarmsFlow.value = alarmsFlow.value.filterNot { it.id == id }
    }

    override suspend fun ensureSeedData() = Unit

    override suspend fun getAlarmById(id: Int): AlarmUiModel? {
        return alarmsFlow.value.firstOrNull { it.id == id }
    }

    override suspend fun addWakeEvent(
        alarmId: Int,
        alarmLabel: String,
        dismissTask: AlarmDismissTaskType,
        completedAt: Instant
    ) {
        val event = WakeEvent(
            id = nextWakeEventId++,
            alarmId = alarmId,
            alarmLabel = alarmLabel,
            dismissTask = dismissTask,
            completedAt = completedAt
        )
        wakeEventsFlow.value = wakeEventsFlow.value + event
    }
}

private class FakeAlarmScheduler : AlarmScheduler {
    val scheduled = mutableListOf<AlarmUiModel>()
    val cancelledIds = mutableListOf<Int>()
    val synchronizedBatches = mutableListOf<List<AlarmUiModel>>()

    override fun schedule(alarm: AlarmUiModel) {
        scheduled.add(alarm)
    }

    override fun cancel(alarmId: Int) {
        cancelledIds.add(alarmId)
    }

    override fun synchronize(alarms: List<AlarmUiModel>) {
        synchronizedBatches.add(alarms)
    }
}
