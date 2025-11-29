// How to run: ./gradlew :app:testDebugUnitTest
package hu.bbara.breakthesnooze.ui.alarm

import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Looper
import androidx.compose.runtime.MutableState
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import hu.bbara.breakthesnooze.MainDispatcherRule
import hu.bbara.breakthesnooze.data.alarm.repository.AlarmRepository
import hu.bbara.breakthesnooze.data.alarm.scheduler.AlarmScheduler
import hu.bbara.breakthesnooze.data.duration.repository.DurationAlarmRepository
import hu.bbara.breakthesnooze.data.duration.scheduler.DurationAlarmScheduler
import hu.bbara.breakthesnooze.data.settings.model.SettingsState
import hu.bbara.breakthesnooze.data.settings.repository.SettingsRepository
import hu.bbara.breakthesnooze.di.AppModule
import hu.bbara.breakthesnooze.di.TestAppModuleBindings
import hu.bbara.breakthesnooze.feature.alarm.service.AlarmIntents
import hu.bbara.breakthesnooze.feature.alarm.service.AlarmRingtoneService
import hu.bbara.breakthesnooze.ui.alarm.dismiss.AlarmDismissTask
import hu.bbara.breakthesnooze.ui.alarm.dismiss.AlarmDismissTaskType
import hu.bbara.breakthesnooze.ui.alarm.dismiss.FocusTimerDismissTask
import hu.bbara.breakthesnooze.ui.alarm.dismiss.MathChallengeDismissTask
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric.buildActivity
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowApplication
import java.time.DayOfWeek
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@HiltAndroidTest
@UninstallModules(AppModule::class)
@Config(application = HiltTestApplication::class, sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class AlarmRingingActivityTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    private val application: Application = ApplicationProvider.getApplicationContext()
    private lateinit var shadowApp: ShadowApplication
    private lateinit var alarmRepository: AlarmRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var settingsState: MutableStateFlow<SettingsState>

    @Before
    fun setUp() {
        shadowApp = shadowOf(application)
        drainStartedServices()
        alarmRepository = mockk(relaxed = true)
        settingsState = MutableStateFlow(SettingsState(debugModeEnabled = false))
        settingsRepository = mockk(relaxed = true)
        every { settingsRepository.settings } returns settingsState
        TestAppModuleBindings.alarmRepository = alarmRepository
        TestAppModuleBindings.settingsRepository = settingsRepository
        TestAppModuleBindings.durationAlarmRepository = RingingFakeDurationAlarmRepository()
        TestAppModuleBindings.durationAlarmScheduler = RingingFakeDurationAlarmScheduler()
        TestAppModuleBindings.alarmScheduler = NoopAlarmScheduler()
        hiltRule.inject()
    }

    @After
    fun tearDown() {
        drainStartedServices()
    }

    @Test
    fun buildsPrimaryAndBackupDismissTasks_inConfiguredOrder() {
        val alarm = baseAlarm(dismissTask = AlarmDismissTaskType.MATH_CHALLENGE)
        val activity = launchActivity(alarm)

        val tasks = activity.tasksState().value
        assertThat(tasks).hasSize(2)
        assertThat(tasks.first()).isInstanceOf(MathChallengeDismissTask::class.java)
        assertThat(tasks.last()).isInstanceOf(FocusTimerDismissTask::class.java)
    }

    @Test
    fun buildsSingleTask_whenPrimaryAlreadyFocusTimer() {
        val alarm = baseAlarm(dismissTask = AlarmDismissTaskType.FOCUS_TIMER)
        val activity = launchActivity(alarm)

        val tasks = activity.tasksState().value
        assertThat(tasks).hasSize(1)
        assertThat(tasks.first()).isInstanceOf(FocusTimerDismissTask::class.java)
    }

    @Test
    fun clearsAvailableTasks_whenAlarmMissing() {
        coEvery { alarmRepository.getAlarmById(TEST_ALARM_ID) } returns null
        val controller = buildActivity(
            AlarmRingingActivity::class.java,
            AlarmRingingActivity.createIntent(application, TEST_ALARM_ID)
        )
        controller.setup()
        advanceCoroutines()
        val activity = controller.get()

        assertThat(activity.tasksState().value).isEmpty()
        assertThat(activity.activeTaskState().value).isNull()
    }

    @Test
    fun activeTaskCleared_whenTaskListNoLongerContainsSelection() {
        val initialAlarm = baseAlarm(dismissTask = AlarmDismissTaskType.MATH_CHALLENGE)
        val activity = launchActivity(initialAlarm)
        val mathTask = activity.tasksState().value.first()

        activity.callPrivate("startTask", mathTask)
        advanceCoroutines()
        assertThat(activity.activeTaskState().value?.id).isEqualTo(mathTask.id)

        val updatedAlarm = initialAlarm.copy(dismissTask = AlarmDismissTaskType.FOCUS_TIMER)
        coEvery { alarmRepository.getAlarmById(TEST_ALARM_ID) } returns updatedAlarm
        activity.callPrivate("updateTasksForAlarm", updatedAlarm)

        assertThat(activity.tasksState().value).hasSize(1)
        assertThat(activity.activeTaskState().value).isNull()
        assertThat(activity.activeTaskTypeState().value).isNull()
    }

    @Test
    fun debugControlsReflectSettingsFlowChanges() {
        val alarm = baseAlarm()
        val activity = launchActivity(alarm)
        assertThat(activity.debugFlagState().value).isFalse()

        settingsState.value = SettingsState(debugModeEnabled = true)
        advanceCoroutines()
        assertThat(activity.debugFlagState().value).isTrue()

        settingsState.value = SettingsState(debugModeEnabled = false)
        advanceCoroutines()
        assertThat(activity.debugFlagState().value).isFalse()
    }

    @Test
    fun debugCancelStopsAlarmViaBackPress() {
        val alarm = baseAlarm()
        val controller = launchController(alarm)
        val activity = controller.get()

        activity.onBackPressedDispatcher.onBackPressed()

        val intent = shadowApp.nextStartedService
        assertThat(intent).isNotNull()
        assertThat(intent!!.component?.className).isEqualTo(AlarmRingtoneService::class.java.name)
        assertThat(intent.action).isEqualTo(AlarmIntents.ACTION_STOP_ALARM)
        assertThat(intent.getIntExtra(AlarmIntents.EXTRA_ALARM_ID, -1)).isEqualTo(TEST_ALARM_ID)
        assertThat(activity.isFinishing).isTrue()
    }

    @Test
    fun onTaskCompleted_recordsWakeEventOnceAndStopsAlarm() {
        val alarm = baseAlarm(dismissTask = AlarmDismissTaskType.MATH_CHALLENGE)
        val activity = launchActivity(alarm)
        val focusTask = activity.tasksState().value.last()
        coEvery { alarmRepository.addWakeEvent(any(), any(), any(), any()) } returns Unit

        activity.callPrivate("startTask", focusTask)
        advanceCoroutines()
        drainStartedServices()
        activity.callPrivate("onTaskCompleted")
        advanceCoroutines()

        coVerify(exactly = 1) {
            alarmRepository.addWakeEvent(
                alarmId = alarm.id,
                alarmLabel = alarm.label,
                dismissTask = AlarmDismissTaskType.FOCUS_TIMER,
                completedAt = any()
            )
        }

        val stopIntent = shadowApp.nextStartedService
        assertThat(stopIntent?.action).isEqualTo(AlarmIntents.ACTION_STOP_ALARM)
        assertThat(activity.isFinishing).isTrue()
    }

    @Test
    fun onTaskCompleted_isIdempotentAcrossMultipleInvocations() {
        val alarm = baseAlarm()
        val activity = launchActivity(alarm)
        coEvery { alarmRepository.addWakeEvent(any(), any(), any(), any()) } returns Unit

        activity.callPrivate("onTaskCompleted")
        advanceCoroutines()
        activity.callPrivate("onTaskCompleted")
        advanceCoroutines()

        coVerify(exactly = 1) {
            alarmRepository.addWakeEvent(alarm.id, alarm.label, alarm.dismissTask, any())
        }
    }

    @Test
    fun startTaskPausesAlarmAndStoresContext() {
        val alarm = baseAlarm()
        val activity = launchActivity(alarm)
        val task = activity.tasksState().value.first()

        activity.callPrivate("startTask", task)

        val intent = shadowApp.nextStartedService
        assertThat(intent?.action).isEqualTo(AlarmIntents.ACTION_PAUSE_ALARM)
        assertThat(activity.activeTaskState().value?.id).isEqualTo(task.id)
        assertThat(activity.activeTaskTypeState().value).isEqualTo(alarm.dismissTask)
    }

    @Test
    fun cancelActiveTaskResumesAlarmAndClearsState() {
        val alarm = baseAlarm()
        val activity = launchActivity(alarm)
        val task = activity.tasksState().value.first()
        activity.callPrivate("startTask", task)
        drainStartedServices()

        activity.callPrivate("cancelActiveTask")

        val intent = shadowApp.nextStartedService
        assertThat(intent?.action).isEqualTo(AlarmIntents.ACTION_RESUME_ALARM)
        assertThat(activity.activeTaskState().value).isNull()
        assertThat(activity.activeTaskTypeState().value).isNull()
    }

    @Test
    fun onDestroyResumesAlarmWhenTaskIncomplete() {
        val alarm = baseAlarm()
        val controller = launchController(alarm)
        val activity = controller.get()
        val task = activity.tasksState().value.first()
        activity.callPrivate("startTask", task)
        drainStartedServices()

        controller.pause().stop().destroy()

        val intent = shadowApp.nextStartedService
        assertThat(intent?.action).isEqualTo(AlarmIntents.ACTION_RESUME_ALARM)
    }

    @Test
    fun dismissReceiverFinishesActivityWhenAlarmDismissed() {
        val alarm = baseAlarm()
        val controller = launchController(alarm)
        val activity = controller.get()

        application.sendBroadcast(Intent(AlarmIntents.ACTION_ALARM_DISMISSED).apply {
            putExtra(AlarmIntents.EXTRA_ALARM_ID, TEST_ALARM_ID)
        })
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(activity.isFinishing).isTrue()
    }

    @Test
    fun activityFinishesImmediately_whenAlarmIdMissing() {
        val controller = buildActivity(
            AlarmRingingActivity::class.java,
            Intent(application, AlarmRingingActivity::class.java)
        )
        controller.setup()

        assertThat(controller.get().isFinishing).isTrue()
    }

    private fun launchActivity(alarm: AlarmUiModel): AlarmRingingActivity {
        return launchController(alarm).get()
    }

    private fun launchController(alarm: AlarmUiModel): ActivityController<AlarmRingingActivity> {
        coEvery { alarmRepository.getAlarmById(TEST_ALARM_ID) } returns alarm
        val intent = AlarmRingingActivity.createIntent(application, TEST_ALARM_ID)
        val controller = buildActivity(AlarmRingingActivity::class.java, intent)
        controller.setup()
        advanceCoroutines()
        return controller
    }

    private fun advanceCoroutines() {
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun AlarmRingingActivity.tasksState(): MutableState<List<AlarmDismissTask>> =
        mutableState("availableTasks")

    private fun AlarmRingingActivity.activeTaskState(): MutableState<AlarmDismissTask?> =
        mutableState("activeTask")

    private fun AlarmRingingActivity.activeTaskTypeState(): MutableState<AlarmDismissTaskType?> =
        mutableState("activeTaskType")

    private fun AlarmRingingActivity.debugFlagState(): MutableState<Boolean> =
        mutableState("debugModeEnabled")

    @Suppress("UNCHECKED_CAST")
    private fun <T> AlarmRingingActivity.mutableState(fieldName: String): MutableState<T> {
        val field = AlarmRingingActivity::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.get(this) as MutableState<T>
    }

    private fun AlarmRingingActivity.callPrivate(name: String, vararg args: Any?) {
        val method = AlarmRingingActivity::class.java.declaredMethods.first { method ->
            method.name == name && method.parameterTypes.size == args.size
        }
        method.isAccessible = true
        method.invoke(this, *args)
    }

    private fun drainStartedServices() {
        while (shadowApp.nextStartedService != null) {
            // Drain queue
        }
    }

    private fun baseAlarm(
        id: Int = TEST_ALARM_ID,
        dismissTask: AlarmDismissTaskType = AlarmDismissTaskType.MATH_CHALLENGE
    ): AlarmUiModel {
        return AlarmUiModel(
            id = id,
            time = LocalTime.of(6, 30),
            label = "Test",
            isActive = true,
            repeatDays = setOf(DayOfWeek.MONDAY),
            soundUri = null,
            dismissTask = dismissTask,
            qrBarcodeValue = "qr",
            qrRequiredUniqueCount = 2
        )
    }

    companion object {
        private const val TEST_ALARM_ID = 99
    }
}

private class RingingFakeDurationAlarmRepository : DurationAlarmRepository {
    override val alarms = MutableStateFlow(emptyList<hu.bbara.breakthesnooze.data.duration.model.DurationAlarm>())
    override suspend fun create(
        durationMinutes: Int,
        label: String,
        soundUri: String?,
        dismissTask: AlarmDismissTaskType,
        qrBarcodeValue: String?,
        qrRequiredUniqueCount: Int
    ) = null

    override suspend fun delete(id: Int) = Unit
    override suspend fun getById(id: Int) = null
}

private class RingingFakeDurationAlarmScheduler : DurationAlarmScheduler {
    override fun schedule(alarm: hu.bbara.breakthesnooze.data.duration.model.DurationAlarm) = Unit
    override fun cancel(alarmId: Int) = Unit
    override fun synchronize(alarms: List<hu.bbara.breakthesnooze.data.duration.model.DurationAlarm>) = Unit
}

private class NoopAlarmScheduler : AlarmScheduler {
    override fun schedule(alarm: AlarmUiModel) = Unit
    override fun cancel(alarmId: Int) = Unit
    override fun synchronize(alarms: List<AlarmUiModel>) = Unit
}
