package hu.bbara.breakthesnooze.feature.alarm.service

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.PowerManager
import android.os.IBinder
import androidx.test.core.app.ApplicationProvider
import hu.bbara.breakthesnooze.MainDispatcherRule
import hu.bbara.breakthesnooze.data.alarm.AlarmRepository
import hu.bbara.breakthesnooze.data.alarm.AlarmRepositoryProvider
import hu.bbara.breakthesnooze.data.alarm.AlarmScheduler
import hu.bbara.breakthesnooze.data.alarm.AlarmSchedulerProvider
import hu.bbara.breakthesnooze.ui.alarm.AlarmRingingActivity
import hu.bbara.breakthesnooze.ui.alarm.AlarmUiModel
import hu.bbara.breakthesnooze.ui.alarm.dismiss.AlarmDismissTaskType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowApplication
import org.robolectric.shadows.ShadowPowerManager
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.S])
class AlarmReceiverTest {

    /*
     Test plan:
     - Verify repeating alarms trigger reschedule, service launch, and activity launch when screen is off.
     - Verify one-shot alarms are deactivated and cancelled after firing.
     - Verify missing alarms are cancelled gracefully without launching services.
     - Verify goAsync pending result finishes on success and when collaborators throw.
     - Verify invalid action or alarm id shortcuts exit without side effects.
    */

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private val context: Application = ApplicationProvider.getApplicationContext()
    private lateinit var shadowApp: ShadowApplication
    private lateinit var powerManager: PowerManager
    private lateinit var shadowPowerManager: ShadowPowerManager

    @Before
    fun setUp() {
        shadowApp = shadowOf(context)
        powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        shadowPowerManager = shadowOf(powerManager)
        shadowPowerManager.setIsInteractive(false)
        clearStartedComponents()
        clearProviders()
    }

    @After
    fun tearDown() {
        clearProviders()
    }

    @Test
    fun `AlarmReceiverReschedulesRepeatingAlarm_whenRepeatDaysPresent`() {
        val alarm = baseAlarm().copy(repeatDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))
        val repository = RecordingAlarmRepository(mapOf(alarm.id to alarm))
        val scheduler = RecordingAlarmScheduler()
        installProviders(repository, scheduler)
        val receiver = AlarmReceiver()
        val pendingResult = preparePendingResult(receiver)

        receiver.onReceive(context, createIntent(alarm.id))
        shadowOf(Looper.getMainLooper()).idle()

        assertEventually("schedule should be invoked") { scheduler.scheduledIds.isNotEmpty() }
        assertTrue("PendingResult should finish", waitUntilFinished(pendingResult))
        assertEquals(listOf(alarm.id), scheduler.scheduledIds)
        assertTrue(scheduler.cancelledIds.isEmpty())
        assertTrue(repository.updateRequests.isEmpty())

        val serviceIntent = shadowApp.nextStartedService
        assertNotNull("Foreground service should be started", serviceIntent)
        assertEquals(AlarmRingtoneService::class.java.name, serviceIntent!!.component?.className)
        assertEquals(AlarmIntents.ACTION_ALARM_FIRED, serviceIntent.action)

        val activityIntent = shadowApp.nextStartedActivity
        assertNotNull("Alarm screen should launch when display off", activityIntent)
        assertEquals(AlarmRingingActivity::class.java.name, activityIntent!!.component?.className)
    }

    @Test
    fun `AlarmReceiverDisablesOneShotAlarm_whenNoRepeatDays`() {
        val alarm = baseAlarm().copy(repeatDays = emptySet())
        val repository = RecordingAlarmRepository(mapOf(alarm.id to alarm))
        val scheduler = RecordingAlarmScheduler()
        installProviders(repository, scheduler)
        val receiver = AlarmReceiver()
        val pendingResult = preparePendingResult(receiver)

        receiver.onReceive(context, createIntent(alarm.id))
        shadowOf(Looper.getMainLooper()).idle()

        assertEventually("updateAlarmActive should be invoked") { repository.updateRequests.isNotEmpty() }
        assertEventually("cancel should be invoked") { scheduler.cancelledIds.isNotEmpty() }
        assertTrue(waitUntilFinished(pendingResult))
        assertEquals(listOf(alarm.id to false), repository.updateRequests)
        assertTrue("Repeating reschedule not expected", scheduler.scheduledIds.isEmpty())
    }

    @Test
    fun `AlarmReceiverCancelsAndSkipsLaunch_whenAlarmMissing`() {
        val repository = RecordingAlarmRepository(emptyMap())
        val scheduler = RecordingAlarmScheduler()
        installProviders(repository, scheduler)
        val receiver = AlarmReceiver()
        val pendingResult = preparePendingResult(receiver)

        receiver.onReceive(context, createIntent(id = 77))
        shadowOf(Looper.getMainLooper()).idle()

        assertEventually("cancel should be invoked", condition = { scheduler.cancelledIds.isNotEmpty() })
        assertTrue(waitUntilFinished(pendingResult))
        assertEquals(listOf(77), scheduler.cancelledIds)
        assertNull("No service should start when alarm is missing", shadowApp.nextStartedService)
        assertNull("No activity should launch when alarm missing", shadowApp.nextStartedActivity)
    }

    @Test
    fun `AlarmReceiverFinishesPendingResult_whenSchedulerThrows`() {
        val alarm = baseAlarm()
        val repository = RecordingAlarmRepository(mapOf(alarm.id to alarm))
        val scheduler = ThrowingAlarmScheduler()
        installProviders(repository, scheduler)
        val receiver = AlarmReceiver()
        val pendingResult = preparePendingResult(receiver)

        receiver.onReceive(context, createIntent(alarm.id))
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue("finish() must be invoked even on exception", waitUntilFinished(pendingResult))
    }

    @Test
    fun `AlarmReceiverFinishesPendingResult_whenRepositoryThrows`() {
        val repository = object : RecordingAlarmRepository(emptyMap()) {
            override suspend fun getAlarmById(id: Int): AlarmUiModel? {
                throw IllegalStateException("boom")
            }
        }
        val scheduler = RecordingAlarmScheduler()
        installProviders(repository, scheduler)
        val receiver = AlarmReceiver()
        val pendingResult = preparePendingResult(receiver)

        receiver.onReceive(context, createIntent(id = 500))
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue("finish() must be invoked when repository fails", waitUntilFinished(pendingResult))
    }

    @Test
    fun `AlarmReceiverReturnsEarly_whenActionMismatched`() {
        val repository = RecordingAlarmRepository(emptyMap())
        val scheduler = RecordingAlarmScheduler()
        installProviders(repository, scheduler)
        val receiver = AlarmReceiver()

        receiver.onReceive(context, Intent("custom.action").apply {
            putExtra(AlarmIntents.EXTRA_ALARM_ID, 10)
        })

        assertTrue("Scheduler should not be invoked", scheduler.scheduledIds.isEmpty() && scheduler.cancelledIds.isEmpty())
        assertNull("No service should start", shadowApp.nextStartedService)
    }

    @Test
    fun `AlarmReceiverReturnsEarly_whenAlarmIdMissing`() {
        val repository = RecordingAlarmRepository(emptyMap())
        val scheduler = RecordingAlarmScheduler()
        installProviders(repository, scheduler)
        val receiver = AlarmReceiver()

        receiver.onReceive(context, Intent(AlarmIntents.ACTION_ALARM_FIRED))

        assertTrue(scheduler.cancelledIds.isEmpty())
        assertNull(shadowApp.nextStartedService)
    }

    private fun createIntent(id: Int) = Intent(AlarmIntents.ACTION_ALARM_FIRED).apply {
        putExtra(AlarmIntents.EXTRA_ALARM_ID, id)
    }

    private fun baseAlarm(): AlarmUiModel = AlarmUiModel(
        id = 101,
        time = LocalTime.of(7, 0),
        label = "Alarm",
        isActive = true,
        repeatDays = setOf(DayOfWeek.MONDAY),
        soundUri = null,
        dismissTask = AlarmDismissTaskType.DEFAULT,
        qrBarcodeValue = null,
        qrRequiredUniqueCount = 0
    )

    private fun clearStartedComponents() {
        while (shadowApp.nextStartedService != null) { /* drain */ }
        while (shadowApp.nextStartedActivity != null) { /* drain */ }
    }

    private fun installProviders(repository: AlarmRepository, scheduler: AlarmScheduler) {
        setProviderField(AlarmRepositoryProvider::class.java, "repository", repository)
        setProviderField(AlarmSchedulerProvider::class.java, "scheduler", scheduler)
    }

    private fun clearProviders() {
        setProviderField(AlarmRepositoryProvider::class.java, "repository", null)
        setProviderField(AlarmSchedulerProvider::class.java, "scheduler", null)
    }

    private fun setProviderField(owner: Class<*>, fieldName: String, value: Any?) {
        val field = owner.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(null, value)
    }

    private fun preparePendingResult(receiver: AlarmReceiver): BroadcastReceiver.PendingResult {
        val constructor = BroadcastReceiver.PendingResult::class.java.getDeclaredConstructor(
            Int::class.javaPrimitiveType,
            String::class.java,
            Bundle::class.java,
            Int::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            android.os.IBinder::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        )
        constructor.isAccessible = true
        val pendingResult = constructor.newInstance(
            0,
            null,
            null,
            0,
            false,
            false,
            Binder(),
            0,
            0
        )
        val field = BroadcastReceiver::class.java.getDeclaredField("mPendingResult").apply {
            isAccessible = true
        }
        field.set(receiver, pendingResult)
        return pendingResult
    }

    private fun waitUntilFinished(
        pendingResult: BroadcastReceiver.PendingResult,
        timeoutSeconds: Long = 5
    ): Boolean {
        val finishedField = pendingResult.javaClass.getDeclaredField("mFinished").apply {
            isAccessible = true
        }
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
        while (System.nanoTime() < deadline) {
            if (finishedField.getBoolean(pendingResult)) {
                return true
            }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        return finishedField.getBoolean(pendingResult)
    }

    private fun assertEventually(message: String, timeoutSeconds: Long = 5, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
        while (System.nanoTime() < deadline) {
            if (condition()) {
                return
            }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        fail(message)
    }

    private open class RecordingAlarmRepository(
        private val storedAlarms: Map<Int, AlarmUiModel>
    ) : AlarmRepository {

        val updateRequests = mutableListOf<Pair<Int, Boolean>>()

        override val alarms: Flow<List<AlarmUiModel>>
            get() = throw UnsupportedOperationException()

        override val wakeEvents: Flow<List<hu.bbara.breakthesnooze.data.alarm.WakeEvent>>
            get() = throw UnsupportedOperationException()

        override suspend fun upsertAlarm(alarm: AlarmUiModel): AlarmUiModel? {
            throw UnsupportedOperationException()
        }

        override suspend fun updateAlarmActive(id: Int, isActive: Boolean): AlarmUiModel? {
            updateRequests += id to isActive
            return storedAlarms[id]?.copy(isActive = isActive)
        }

        override suspend fun deleteAlarm(id: Int) {
            throw UnsupportedOperationException()
        }

        override suspend fun ensureSeedData() {
            throw UnsupportedOperationException()
        }

        override suspend fun getAlarmById(id: Int): AlarmUiModel? {
            println("getAlarmById invoked with id=$id")
            return storedAlarms[id]
        }

        override suspend fun addWakeEvent(
            alarmId: Int,
            alarmLabel: String,
            dismissTask: hu.bbara.breakthesnooze.ui.alarm.dismiss.AlarmDismissTaskType,
            completedAt: java.time.Instant
        ) {
            throw UnsupportedOperationException()
        }
    }

    private class RecordingAlarmScheduler : AlarmScheduler {
        val scheduledIds = mutableListOf<Int>()
        val cancelledIds = mutableListOf<Int>()

        override fun schedule(alarm: AlarmUiModel) {
            println("schedule invoked for id=${alarm.id}")
            scheduledIds += alarm.id
        }

        override fun cancel(alarmId: Int) {
            cancelledIds += alarmId
        }

        override fun synchronize(alarms: List<AlarmUiModel>) {
            throw UnsupportedOperationException()
        }
    }

    private class ThrowingAlarmScheduler : AlarmScheduler {
        override fun schedule(alarm: AlarmUiModel) {
            throw IllegalStateException("schedule failure")
        }

        override fun cancel(alarmId: Int) = Unit
        override fun synchronize(alarms: List<AlarmUiModel>) = Unit
    }
}
