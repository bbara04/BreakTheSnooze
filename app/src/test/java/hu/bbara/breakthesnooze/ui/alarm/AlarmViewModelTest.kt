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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        settingsRepository.setDefaultDismissTask(AlarmDismissTaskType.MATH_CHALLENGE)
        val repository = FakeAlarmRepository()
        val scheduler = FakeAlarmScheduler()

        val viewModel = AlarmViewModel(repository, scheduler, settingsRepository)
        advanceUntilIdle()
        waitUntil { viewModel.uiState.value.settings.defaultDismissTask == AlarmDismissTaskType.MATH_CHALLENGE }

        viewModel.startCreating()
        waitUntil { viewModel.uiState.value.destination == AlarmDestination.Create }

        val state = viewModel.uiState.value
        assertEquals(AlarmDestination.Create, state.destination)
        assertEquals(AlarmDismissTaskType.MATH_CHALLENGE, state.draft.dismissTask)
    }

    @Test
    fun `saveDraft with valid time upserts alarm schedules it and resets state`() = runTest {
        val settingsRepository = createSettingsRepository()
        val repository = FakeAlarmRepository()
        val scheduler = FakeAlarmScheduler()
        val viewModel = AlarmViewModel(repository, scheduler, settingsRepository)
        advanceUntilIdle()

        viewModel.startCreating()
        advanceUntilIdle()

        val pendingDraft = viewModel.uiState.value.draft.copy(
            time = LocalTime.of(6, 15),
            label = "Workout",
            dismissTask = AlarmDismissTaskType.MEMORY
        )
        viewModel.updateDraft(pendingDraft)
        viewModel.saveDraft()
        repository.awaitUpsertCount(1)
        scheduler.awaitScheduleCount(1)
        advanceUntilIdle()

        val saved = repository.upsertedAlarms.single()
        assertEquals("Workout", saved.label)
        assertEquals(AlarmDismissTaskType.MEMORY, saved.dismissTask)

        val state = viewModel.uiState.value
        assertNull(state.editingAlarm)
        assertEquals(emptySet<Int>(), state.selectedAlarmIds)
        assertTrue(state.draft.time != null)
        assertTrue(scheduler.scheduled.contains(saved))
    }

    @Test
    fun `saveDraft without time is ignored`() = runTest {
        val settingsRepository = createSettingsRepository()
        val repository = FakeAlarmRepository()
        val scheduler = FakeAlarmScheduler()
        val viewModel = AlarmViewModel(repository, scheduler, settingsRepository)
        advanceUntilIdle()

        viewModel.startCreating()
        advanceUntilIdle()

        val clearedDraft = viewModel.uiState.value.draft.copy(time = null)
        viewModel.updateDraft(clearedDraft)
        viewModel.saveDraft()
        advanceUntilIdle()

        assertTrue(repository.upsertedAlarms.isEmpty())
        assertTrue(scheduler.scheduled.isEmpty())
        assertEquals(AlarmDestination.Create, viewModel.uiState.value.destination)
    }

    @Test
    fun `startEditing loads draft and cancelCreation returns to list`() = runTest {
        val settingsRepository = createSettingsRepository()
        val alarm = sampleAlarm(id = 3, isActive = true).copy(label = "Lunch")
        val repository = FakeAlarmRepository(listOf(alarm))
        val scheduler = FakeAlarmScheduler()
        val viewModel = AlarmViewModel(repository, scheduler, settingsRepository)
        advanceUntilIdle()
        waitUntil { viewModel.uiState.value.alarms.isNotEmpty() }

        viewModel.startEditing(3)
        advanceUntilIdle()
        waitUntil { viewModel.uiState.value.editingAlarm?.id == 3 }

        val editState = viewModel.uiState.value
        assertEquals(alarm.toCreationState(), editState.draft)
        assertEquals(alarm, editState.editingAlarm)
        assertEquals(AlarmDestination.Create, editState.destination)

        viewModel.cancelCreation()
        advanceUntilIdle()

        val cancelled = viewModel.uiState.value
        assertEquals(AlarmDestination.List, cancelled.destination)
        assertNull(cancelled.editingAlarm)
        assertEquals(emptySet<Int>(), cancelled.selectedAlarmIds)
    }

    @Test
    fun `onToggleAlarm updates state and cancels when disabling`() = runTest {
        val settingsRepository = createSettingsRepository()
        val alarm = sampleAlarm(id = 1, isActive = true)
        val repository = FakeAlarmRepository(listOf(alarm))
        val scheduler = FakeAlarmScheduler()
        val viewModel = AlarmViewModel(repository, scheduler, settingsRepository)
        advanceUntilIdle()
        waitUntil { viewModel.uiState.value.alarms.isNotEmpty() }

        viewModel.onToggleAlarm(1, false)
        repository.awaitUpdateActiveCount(1)
        scheduler.awaitCancelCount(1)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.alarms.first().isActive)
        assertEquals(listOf(1 to false), repository.updateActiveCalls)
        assertEquals(listOf(1), scheduler.cancelledIds)
    }

//    @Test
//    fun `deleteAlarm removes editing alarm cancels scheduler and resets draft`() = runTest {
//        val settingsRepository = createSettingsRepository()
//        settingsRepository.setDefaultDismissTask(AlarmDismissTaskType.FOCUS_TIMER)
//        val repository = FakeAlarmRepository(listOf(sampleAlarm(id = 1, isActive = true)))
//        val scheduler = FakeAlarmScheduler()
//        val viewModel = AlarmViewModel(repository, scheduler, settingsRepository)
//        advanceUntilIdle()
//        waitUntil { viewModel.uiState.value.alarms.isNotEmpty() }
//
//        viewModel.startEditing(1)
//        advanceUntilIdle()
//        waitUntil { viewModel.uiState.value.editingAlarm?.id == 1 }
//
//        viewModel.deleteAlarm(1)
//        repository.awaitDeleteCount(1)
//        scheduler.awaitCancelCount(1)
//        advanceUntilIdle()
//        advanceUntilIdle()
//
//        val state = viewModel.uiState.value
//        assertEquals(AlarmDestination.List, state.destination)
//        assertNull(state.editingAlarm)
//        assertEquals(emptySet<Int>(), state.selectedAlarmIds)
//        assertEquals(AlarmDismissTaskType.FOCUS_TIMER, state.draft.dismissTask)
//        assertFalse(state.alarms.any { it.id == 1 })
//    }

//    @Test
//    fun `deleteAlarm failure restores previous state without cancelling`() = runTest {
//        val settingsRepository = createSettingsRepository()
//        val repository = FakeAlarmRepository(listOf(sampleAlarm(id = 1, isActive = true)))
//        repository.failDeleteIds.add(1)
//        val scheduler = FakeAlarmScheduler()
//        val viewModel = AlarmViewModel(repository, scheduler, settingsRepository)
//        advanceUntilIdle()
//        waitUntil { viewModel.uiState.value.alarms.isNotEmpty() }
//
//        val previous = viewModel.uiState.value
//        viewModel.deleteAlarm(1)
//        advanceUntilIdle()
//        advanceUntilIdle()
//
//        assertEquals(previous, viewModel.uiState.value)
//        assertTrue(repository.deletedIds.isEmpty())
//        assertTrue(scheduler.cancelledIds.isEmpty())
//    }

//    @Test
//    fun `deleteSelected removes each selected alarm and clears selection`() = runTest {
//        val settingsRepository = createSettingsRepository()
//        settingsRepository.setDefaultDismissTask(AlarmDismissTaskType.MATH_CHALLENGE)
//        val repository = FakeAlarmRepository(
//            listOf(
//                sampleAlarm(id = 1, isActive = true),
//                sampleAlarm(id = 2, isActive = false),
//                sampleAlarm(id = 3, isActive = true)
//            )
//        )
//        val scheduler = FakeAlarmScheduler()
//        val viewModel = AlarmViewModel(repository, scheduler, settingsRepository)
//        advanceUntilIdle()
//        waitUntil { viewModel.uiState.value.alarms.size == 3 }
//
//        viewModel.startEditing(1)
//        advanceUntilIdle()
//        waitUntil { viewModel.uiState.value.editingAlarm?.id == 1 }
//
//        viewModel.enterSelection(1)
//        viewModel.toggleSelection(2)
//        viewModel.deleteSelected()
//        repository.awaitDeleteCount(2)
//        scheduler.awaitCancelCount(2)
//        advanceUntilIdle()
//        advanceUntilIdle()
//
//        val state = viewModel.uiState.value
//        assertEquals(setOf(1, 2), repository.deletedIds.toSet())
//        assertEquals(setOf(1, 2), scheduler.cancelledIds.toSet())
//        assertEquals(1, state.alarms.size)
//        assertEquals(3, state.alarms.single().id)
//        assertEquals(emptySet<Int>(), state.selectedAlarmIds)
//        assertEquals(AlarmDestination.List, state.destination)
//        assertNull(state.editingAlarm)
//        assertFalse(state.alarms.any { it.id == 1 || it.id == 2 })
//        assertEquals(AlarmDismissTaskType.MATH_CHALLENGE, state.draft.dismissTask)
//    }

//    @Test
//    fun `deleteSelected failure restores previous state`() = runTest {
//        val settingsRepository = createSettingsRepository()
//        val repository = FakeAlarmRepository(
//            listOf(
//                sampleAlarm(id = 1, isActive = true),
//                sampleAlarm(id = 2, isActive = true)
//            )
//        )
//        repository.failDeleteIds.add(2)
//        val scheduler = FakeAlarmScheduler()
//        val viewModel = AlarmViewModel(repository, scheduler, settingsRepository)
//        advanceUntilIdle()
//        waitUntil { viewModel.uiState.value.alarms.size == 2 }
//
//        viewModel.enterSelection(1)
//        viewModel.toggleSelection(2)
//        val previous = viewModel.uiState.value
//
//        viewModel.deleteSelected()
//        advanceUntilIdle()
//        advanceUntilIdle()
//
//        assertEquals(previous, viewModel.uiState.value)
//        assertTrue(repository.deletedIds.isEmpty())
//        assertTrue(scheduler.cancelledIds.isEmpty())
//    }

//    @Test
//    fun `saveDraft upserts alarm schedules it and clears editor`() = runTest {
//        val settingsRepository = createSettingsRepository()
//        val repository = FakeAlarmRepository()
//        val scheduler = FakeAlarmScheduler()
//        val viewModel = AlarmViewModel(repository, scheduler, settingsRepository)
//        advanceUntilIdle()
//
//        viewModel.startCreating()
//        advanceUntilIdle()
//
//        val updatedDraft = viewModel.uiState.value.draft.copy(
//            label = "Morning workout",
//            dismissTask = AlarmDismissTaskType.MATH_CHALLENGE
//        )
//        viewModel.updateDraft(updatedDraft)
//        viewModel.saveDraft()
//        repository.awaitUpsertCount(1)
//        scheduler.awaitScheduleCount(1)
//        advanceUntilIdle()
//        advanceUntilIdle()
//
//        assertEquals(1, repository.upsertedAlarms.size)
//        val saved = repository.upsertedAlarms.single()
//        assertEquals("Morning workout", saved.label)
//        assertTrue(saved.isActive)
//        assertEquals(listOf(saved), scheduler.scheduled)
//        val state = viewModel.uiState.value
//        assertEquals(AlarmDestination.List, state.destination)
//        assertNull(state.editingAlarm)
//        assertEquals(emptySet<Int>(), state.selectedAlarmIds)
//        assertEquals(AlarmDismissTaskType.DEFAULT, state.draft.dismissTask)
//    }

    @Test
    fun `resetDraft while editing restores current alarm`() = runTest {
        val settingsRepository = createSettingsRepository()
        val alarm = sampleAlarm(id = 1, isActive = true).copy(
            label = "Gym time",
            soundUri = "content://sound",
            qrBarcodeValue = "code",
            qrRequiredUniqueCount = MIN_QR_UNIQUE_COUNT
        )
        val repository = FakeAlarmRepository(listOf(alarm))
        val scheduler = FakeAlarmScheduler()
        val viewModel = AlarmViewModel(repository, scheduler, settingsRepository)
        advanceUntilIdle()
        waitUntil { viewModel.uiState.value.alarms.isNotEmpty() }

        viewModel.startEditing(1)
        advanceUntilIdle()
        waitUntil { viewModel.uiState.value.editingAlarm?.id == 1 }

        val mutated = viewModel.uiState.value.draft.copy(label = "Mutated", soundUri = "muted")
        viewModel.updateDraft(mutated)
        viewModel.resetDraft()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(alarm.toCreationState(), state.draft)
        assertEquals(alarm, state.editingAlarm)
    }

    @Test
    fun `selection operations mutate selected alarm ids`() = runTest {
        val settingsRepository = createSettingsRepository()
        val repository = FakeAlarmRepository(
            listOf(sampleAlarm(id = 1, isActive = true), sampleAlarm(id = 2, isActive = true))
        )
        val scheduler = FakeAlarmScheduler()
        val viewModel = AlarmViewModel(repository, scheduler, settingsRepository)
        advanceUntilIdle()

        viewModel.enterSelection(1)
        assertEquals(setOf(1), viewModel.uiState.value.selectedAlarmIds)

        viewModel.toggleSelection(2)
        assertEquals(setOf(1, 2), viewModel.uiState.value.selectedAlarmIds)

        viewModel.toggleSelection(1)
        assertEquals(setOf(2), viewModel.uiState.value.selectedAlarmIds)

        viewModel.clearSelection()
        assertEquals(emptySet<Int>(), viewModel.uiState.value.selectedAlarmIds)
    }

    @Test
    fun `qr configuration updates draft consistently`() = runTest {
        val settingsRepository = createSettingsRepository()
        val repository = FakeAlarmRepository()
        val scheduler = FakeAlarmScheduler()
        val viewModel = AlarmViewModel(repository, scheduler, settingsRepository)
        advanceUntilIdle()

        viewModel.startCreating()
        advanceUntilIdle()

        viewModel.setDraftDismissTask(AlarmDismissTaskType.QR_BARCODE_SCAN)
        var state = viewModel.uiState.value
        assertEquals(AlarmDismissTaskType.QR_BARCODE_SCAN, state.draft.dismissTask)

        viewModel.setDraftQrBarcodeValue("code-123")
        state = viewModel.uiState.value
        assertEquals("code-123", state.draft.qrBarcodeValue)
        assertEquals(0, state.draft.qrRequiredUniqueCount)

        viewModel.setDraftQrScanMode(QrScanMode.UniqueCodes)
        state = viewModel.uiState.value
        assertNull(state.draft.qrBarcodeValue)
        assertEquals(DEFAULT_QR_UNIQUE_COUNT, state.draft.qrRequiredUniqueCount)

        viewModel.setDraftQrUniqueCount(MAX_QR_UNIQUE_COUNT + 1)
        state = viewModel.uiState.value
        assertNull(state.draft.qrBarcodeValue)
        assertEquals(MAX_QR_UNIQUE_COUNT, state.draft.qrRequiredUniqueCount)

        viewModel.setDraftDismissTask(AlarmDismissTaskType.MATH_CHALLENGE)
        state = viewModel.uiState.value
        assertEquals(AlarmDismissTaskType.MATH_CHALLENGE, state.draft.dismissTask)
        assertNull(state.draft.qrBarcodeValue)
        assertEquals(0, state.draft.qrRequiredUniqueCount)
    }

    @Test
    fun `qr scan mode ignored when dismiss task is not QR`() = runTest {
        val settingsRepository = createSettingsRepository()
        val repository = FakeAlarmRepository()
        val scheduler = FakeAlarmScheduler()
        val viewModel = AlarmViewModel(repository, scheduler, settingsRepository)
        advanceUntilIdle()

        val initialDraft = viewModel.uiState.value.draft
        viewModel.setDraftQrScanMode(QrScanMode.UniqueCodes)
        assertEquals(initialDraft, viewModel.uiState.value.draft)
    }

    @Test
    fun `default settings updates persist to repository`() = runTest {
        val settingsRepository = createSettingsRepository()
        val repository = FakeAlarmRepository()
        val scheduler = FakeAlarmScheduler()
        val viewModel = AlarmViewModel(repository, scheduler, settingsRepository)
        advanceUntilIdle()

        viewModel.setDefaultDismissTask(AlarmDismissTaskType.FOCUS_TIMER)
        viewModel.setDefaultRingtone("content://tone")
        viewModel.setDebugMode(true)
        advanceUntilIdle()

        val settings = settingsRepository.settings.first()
        assertEquals(AlarmDismissTaskType.FOCUS_TIMER, settings.defaultDismissTask)
        assertEquals("content://tone", settings.defaultRingtoneUri)
        assertTrue(settings.debugModeEnabled)
    }

    @Test
    fun `repository updates trigger scheduler synchronization`() = runTest {
        val settingsRepository = createSettingsRepository()
        val initial = listOf(
            sampleAlarm(id = 1, isActive = true),
            sampleAlarm(id = 2, isActive = false)
        )
        val repository = FakeAlarmRepository(initial)
        val scheduler = FakeAlarmScheduler()
        val viewModel = AlarmViewModel(repository, scheduler, settingsRepository)
        scheduler.awaitSynchronizeCount(1)

        assertEquals(listOf(initial), scheduler.synchronizedBatches)

        repository.upsertAlarm(sampleAlarm(id = 3, isActive = true))
        scheduler.awaitSynchronizeCount(2)

        assertEquals(2, scheduler.synchronizedBatches.size)
        assertEquals(3, scheduler.synchronizedBatches.last().size)
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

    private suspend fun TestScope.waitUntil(condition: () -> Boolean) {
        if (condition()) return
        withTimeout(5_000) {
            while (!condition()) {
                advanceUntilIdle()
                yield()
            }
        }
    }
}

private class FakeAlarmRepository(
    initialAlarms: List<AlarmUiModel> = emptyList()
) : AlarmRepository {

    private val alarmsFlow = MutableStateFlow(initialAlarms.sortedBy { it.id })
    private val wakeEventsFlow = MutableStateFlow<List<WakeEvent>>(emptyList())
    private var nextWakeEventId = 1L
    private val deleteEvents = Channel<Unit>(Channel.UNLIMITED)
    private val upsertEvents = Channel<Unit>(Channel.UNLIMITED)
    private val updateActiveEvents = Channel<Unit>(Channel.UNLIMITED)

    val deletedIds = mutableListOf<Int>()
    val failDeleteIds = mutableSetOf<Int>()
    val upsertedAlarms = mutableListOf<AlarmUiModel>()
    val updateActiveCalls = mutableListOf<Pair<Int, Boolean>>()

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
        upsertedAlarms.add(assigned)
        upsertEvents.trySend(Unit)
        return assigned
    }

    override suspend fun updateAlarmActive(id: Int, isActive: Boolean): AlarmUiModel? {
        updateActiveCalls.add(id to isActive)
        updateActiveEvents.trySend(Unit)
        val updated = alarmsFlow.value.map { alarm ->
            if (alarm.id == id) alarm.copy(isActive = isActive) else alarm
        }
        alarmsFlow.value = updated
        return updated.firstOrNull { it.id == id }
    }

    override suspend fun deleteAlarm(id: Int) {
        if (failDeleteIds.contains(id)) {
            throw IllegalStateException("delete failure for $id")
        }
        alarmsFlow.value = alarmsFlow.value.filterNot { it.id == id }
        deletedIds.add(id)
        deleteEvents.trySend(Unit)
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

    suspend fun awaitDeleteCount(expected: Int) {
        var remaining = expected - deletedIds.size
        while (remaining > 0) {
            deleteEvents.receive()
            remaining--
        }
    }

    suspend fun awaitUpsertCount(expected: Int) {
        var remaining = expected - upsertedAlarms.size
        while (remaining > 0) {
            upsertEvents.receive()
            remaining--
        }
    }

    suspend fun awaitUpdateActiveCount(expected: Int) {
        var remaining = expected - updateActiveCalls.size
        while (remaining > 0) {
            updateActiveEvents.receive()
            remaining--
        }
    }
}

private class FakeAlarmScheduler : AlarmScheduler {
    val scheduled = mutableListOf<AlarmUiModel>()
    val cancelledIds = mutableListOf<Int>()
    val synchronizedBatches = mutableListOf<List<AlarmUiModel>>()
    private val scheduleEvents = Channel<Unit>(Channel.UNLIMITED)
    private val cancelEvents = Channel<Unit>(Channel.UNLIMITED)
    private val synchronizeEvents = Channel<Unit>(Channel.UNLIMITED)

    override fun schedule(alarm: AlarmUiModel) {
        scheduled.add(alarm)
        scheduleEvents.trySend(Unit)
    }

    override fun cancel(alarmId: Int) {
        cancelledIds.add(alarmId)
        cancelEvents.trySend(Unit)
    }

    override fun synchronize(alarms: List<AlarmUiModel>) {
        synchronizedBatches.add(alarms)
        synchronizeEvents.trySend(Unit)
    }

    suspend fun awaitScheduleCount(expected: Int) {
        var remaining = expected - scheduled.size
        while (remaining > 0) {
            scheduleEvents.receive()
            remaining--
        }
    }

    suspend fun awaitCancelCount(expected: Int) {
        var remaining = expected - cancelledIds.size
        while (remaining > 0) {
            cancelEvents.receive()
            remaining--
        }
    }

    suspend fun awaitSynchronizeCount(expected: Int) {
        var remaining = expected - synchronizedBatches.size
        while (remaining > 0) {
            synchronizeEvents.receive()
            remaining--
        }
    }
}
