package hu.bbara.breakthesnooze.ui.alarm

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hu.bbara.breakthesnooze.MainDispatcherRule
import hu.bbara.breakthesnooze.data.alarm.model.WakeEvent
import hu.bbara.breakthesnooze.data.alarm.repository.AlarmRepository
import hu.bbara.breakthesnooze.data.alarm.scheduler.AlarmScheduler
import hu.bbara.breakthesnooze.data.duration.model.DurationAlarm
import hu.bbara.breakthesnooze.data.duration.repository.DurationAlarmRepository
import hu.bbara.breakthesnooze.data.duration.scheduler.DurationAlarmScheduler
import hu.bbara.breakthesnooze.data.settings.repository.SettingsRepository
import hu.bbara.breakthesnooze.ui.alarm.create.AlarmEditorViewModel
import hu.bbara.breakthesnooze.ui.alarm.dismiss.AlarmDismissTaskType
import hu.bbara.breakthesnooze.ui.alarm.domain.CreateDurationAlarmUseCase
import hu.bbara.breakthesnooze.ui.alarm.domain.DeleteDurationAlarmUseCase
import hu.bbara.breakthesnooze.ui.alarm.domain.SaveAlarmUseCase
import hu.bbara.breakthesnooze.ui.alarm.duration.DurationAlarmViewModel
import hu.bbara.breakthesnooze.ui.alarm.list.AlarmListViewModel
import hu.bbara.breakthesnooze.ui.alarm.model.AlarmDestination
import hu.bbara.breakthesnooze.ui.alarm.model.AlarmUiModel
import hu.bbara.breakthesnooze.ui.alarm.settings.AlarmSettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.DayOfWeek
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class AlarmViewModelsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val tempDirs = mutableListOf<File>()
    private val scopes = mutableListOf<CoroutineScope>()
    private val viewModels = mutableListOf<ViewModel>()

    @After
    fun tearDown() {
        viewModels.forEach { it.viewModelScope.cancel() }
        viewModels.clear()
        scopes.forEach { it.cancel() }
        scopes.clear()
        tempDirs.forEach { it.deleteRecursively() }
        tempDirs.clear()
    }

    @Test
    fun `editor start creating uses settings defaults`() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val settings = createSettingsRepository()
        settings.setDefaultDismissTask(AlarmDismissTaskType.MATH_CHALLENGE)
        val repository = FakeAlarmRepository()
        val scheduler = FakeAlarmScheduler()
        val viewModel = AlarmEditorViewModel(repository, settings, saveAlarmUseCase = SaveAlarmUseCase(repository, scheduler)).also {
            viewModels += it
        }
        advanceUntilIdle()

        viewModel.startCreating()
        advanceUntilIdle()
        val state = viewModel.state.first { it.destination == AlarmDestination.Create }

        assertEquals(AlarmDismissTaskType.MATH_CHALLENGE, state.draft.dismissTask)
    }

    @Test
    fun `list toggle invokes scheduler`() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val settings = createSettingsRepository()
        val alarm = sampleAlarm(id = 1, isActive = true)
        val repository = FakeAlarmRepository(listOf(alarm))
        val scheduler = FakeAlarmScheduler()
        val viewModel = AlarmListViewModel(
            repository = repository,
            scheduler = scheduler,
            ioDispatcher = mainDispatcherRule.dispatcher
        ).also { viewModels += it }
        advanceUntilIdle()

        viewModel.onToggleAlarm(1, false)
        advanceUntilIdle()
        assertEquals(listOf(1 to false), repository.updateActiveCalls)
        assertEquals(listOf(1), scheduler.cancelledIds)
    }

    @Test
    fun `duration save schedules alarm`() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val settings = createSettingsRepository()
        val durationRepository = FakeDurationAlarmRepository()
        val scheduler = FakeDurationAlarmScheduler()
        val viewModel = DurationAlarmViewModel(
            repository = durationRepository,
            settingsRepository = settings,
            createDurationAlarmUseCase = CreateDurationAlarmUseCase(durationRepository, scheduler),
            deleteDurationAlarmUseCase = DeleteDurationAlarmUseCase(durationRepository, scheduler),
            ioDispatcher = mainDispatcherRule.dispatcher
        ).also { viewModels += it }
        advanceUntilIdle()

        viewModel.setDurationHours(0)
        viewModel.setDurationMinutes(5)
        viewModel.setDurationLabel("Nap")
        viewModel.saveDurationDraft()
        advanceUntilIdle()

        assertTrue(durationRepository.created.isNotEmpty())
        assertTrue(scheduler.scheduled.isNotEmpty())
    }

    @Test
    fun `settings updates persist`() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val settings = createSettingsRepository()
        val viewModel = AlarmSettingsViewModel(settings).also { viewModels += it }
        advanceUntilIdle()

        viewModel.setDefaultDismissTask(AlarmDismissTaskType.FOCUS_TIMER)
        viewModel.setDefaultRingtone("content://tone")
        viewModel.setDebugMode(true)
        advanceUntilIdle()

        val updated = settings.settings.first()
        assertEquals(AlarmDismissTaskType.FOCUS_TIMER, updated.defaultDismissTask)
        assertEquals("content://tone", updated.defaultRingtoneUri)
        assertTrue(updated.debugModeEnabled)
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
        val tempDir = Files.createTempDirectory("settings").toFile().also { tempDirs.add(it) }
        val scope = CoroutineScope(SupervisorJob() + mainDispatcherRule.dispatcher).also { scopes.add(it) }
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            tempDir.resolve("datastore.preferences_pb")
        }
        return SettingsRepository(dataStore)
    }
}

private class FakeAlarmRepository(
    initial: List<AlarmUiModel> = emptyList()
) : AlarmRepository {
    private val alarmsFlow = MutableStateFlow(initial)
    private val wakeEventsFlow = MutableStateFlow(emptyList<WakeEvent>())
    val updateActiveCalls = mutableListOf<Pair<Int, Boolean>>()

    override val alarms: Flow<List<AlarmUiModel>> = alarmsFlow
    override val wakeEvents: Flow<List<WakeEvent>> = wakeEventsFlow

    override suspend fun ensureSeedData() = Unit

    override suspend fun upsertAlarm(alarm: AlarmUiModel): AlarmUiModel {
        alarmsFlow.value = alarmsFlow.value.filterNot { it.id == alarm.id } + alarm
        return alarm
    }

    override suspend fun updateAlarmActive(id: Int, isActive: Boolean): AlarmUiModel? {
        updateActiveCalls += id to isActive
        val current = alarmsFlow.value.firstOrNull { it.id == id } ?: return null
        val updated = current.copy(isActive = isActive)
        alarmsFlow.value = alarmsFlow.value.map { if (it.id == id) updated else it }
        return updated
    }

    override suspend fun deleteAlarm(id: Int) {
        alarmsFlow.value = alarmsFlow.value.filterNot { it.id == id }
    }

    override suspend fun getAlarmById(id: Int): AlarmUiModel? =
        alarmsFlow.value.firstOrNull { it.id == id }

    override suspend fun addWakeEvent(
        alarmId: Int,
        alarmLabel: String,
        dismissTask: AlarmDismissTaskType,
        completedAt: java.time.Instant
    ) {
        wakeEventsFlow.value = wakeEventsFlow.value + WakeEvent(0, alarmId, alarmLabel, dismissTask, completedAt)
    }
}

private class FakeAlarmScheduler : AlarmScheduler {
    val cancelledIds = mutableListOf<Int>()
    val scheduled = mutableListOf<AlarmUiModel>()
    override fun schedule(alarm: AlarmUiModel) {
        scheduled += alarm
    }

    override fun cancel(id: Int) {
        cancelledIds += id
    }

    override fun synchronize(alarms: List<AlarmUiModel>) = Unit
}

private class FakeDurationAlarmRepository : DurationAlarmRepository {
    private val flow = MutableStateFlow(emptyList<DurationAlarm>())
    val created = mutableListOf<DurationAlarm>()
    override val alarms: Flow<List<DurationAlarm>> = flow

    override suspend fun create(
        durationMinutes: Int,
        label: String,
        soundUri: String?,
        dismissTask: AlarmDismissTaskType,
        qrBarcodeValue: String?,
        qrRequiredUniqueCount: Int
    ): DurationAlarm? {
        val now = java.time.Instant.now()
        val alarm = DurationAlarm(
            id = created.size + 1,
            label = label,
            durationMinutes = durationMinutes,
            createdAt = now,
            triggerAt = now.plusSeconds(durationMinutes * 60L),
            soundUri = soundUri,
            dismissTask = dismissTask,
            qrBarcodeValue = qrBarcodeValue,
            qrRequiredUniqueCount = qrRequiredUniqueCount
        )
        created += alarm
        flow.value = flow.value + alarm
        return alarm
    }

    override suspend fun delete(id: Int) {
        flow.value = flow.value.filterNot { it.id == id }
    }

    override suspend fun getById(id: Int): DurationAlarm? = flow.value.firstOrNull { it.id == id }
}

private class FakeDurationAlarmScheduler : DurationAlarmScheduler {
    val scheduled = mutableListOf<DurationAlarm>()
    override fun schedule(alarm: DurationAlarm) {
        scheduled += alarm
    }

    override fun cancel(id: Int) = Unit

    override fun synchronize(alarms: List<DurationAlarm>) = Unit
}
