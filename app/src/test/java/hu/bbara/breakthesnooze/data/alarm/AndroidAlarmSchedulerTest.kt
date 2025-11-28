package hu.bbara.breakthesnooze.data.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import hu.bbara.breakthesnooze.data.alarm.scheduler.AndroidAlarmScheduler
import hu.bbara.breakthesnooze.data.alarm.scheduler.calculateNextTriggerMillis
import hu.bbara.breakthesnooze.feature.alarm.service.AlarmIntents
import hu.bbara.breakthesnooze.feature.alarm.service.AlarmReceiver
import hu.bbara.breakthesnooze.ui.alarm.AlarmRingingActivity
import hu.bbara.breakthesnooze.ui.alarm.AlarmUiModel
import hu.bbara.breakthesnooze.ui.alarm.dismiss.AlarmDismissTaskType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowAlarmManager
import org.robolectric.shadows.ShadowLog
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.S])
@LooperMode(LooperMode.Mode.LEGACY)
class AndroidAlarmSchedulerTest {

    /*
     Test plan:
     - Verify schedule() issues an exact alarm with AlarmClockInfo when permission granted.
     - Verify schedule() logs a warning and does not schedule when exact alarms are disallowed.
     - Verify cancel() removes matching PendingIntent and clears AlarmManager state.
     - Verify cancel() is safe when nothing exists.
     - Verify synchronize() schedules active alarms, cancels inactive ones, and keeps unique intents.
     - Verify scheduling respects repeat-day calculations across DST transitions.
    */

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var scheduler: AndroidAlarmScheduler
    private lateinit var clock: Clock
    private lateinit var alarmManager: AlarmManager
    private var originalTimeZone: TimeZone? = null

    @Before
    fun setUp() {
        originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        ShadowLog.reset()
        updateClock(Instant.parse("2024-05-06T05:00:00Z"))
        alarmManager = context.getSystemService(AlarmManager::class.java)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun `AndroidAlarmSchedulerSchedulesExactAlarm_whenPermissionGranted`() {
        val alarm = createAlarm(id = 42, time = LocalTime.of(7, 30), repeatDays = emptySet())
        ShadowAlarmManager.setCanScheduleExactAlarms(true)

        scheduler.schedule(alarm)

        val alarmClock = alarmManager.nextAlarmClock
        assertNotNull("Expected alarm clock info to be scheduled", alarmClock)
        val expectedMillis = calculateNextTriggerMillis(
            alarm,
            LocalDateTime.now(clock)
        )
        assertEquals(expectedMillis, alarmClock!!.triggerTime)

        val firingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id,
            createAlarmIntent(alarm.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
        assertNotNull("PendingIntent should exist after scheduling", firingIntent)
        assertTrue(firingIntent!!.isImmutable)
        assertTrue(firingIntent.isBroadcast)

        val showIntent = alarmClock.showIntent
        val showShadow = shadowOf(showIntent)
        val launchedIntent = showShadow.savedIntent
        assertNotNull("Show intent should route to AlarmRingingActivity", launchedIntent)
        assertEquals(AlarmRingingActivity::class.java.name, launchedIntent!!.component?.className)
    }

    @Test
    fun `AndroidAlarmSchedulerFallsBack_whenExactNotAllowed`() {
        val alarm = createAlarm(id = 5, time = LocalTime.of(9, 15))
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        ShadowLog.reset()

        try {
            scheduler.schedule(alarm)
        } finally {
            ShadowAlarmManager.setCanScheduleExactAlarms(true)
        }

        assertNull("No alarm should be scheduled without permission", alarmManager.nextAlarmClock)
        val warningLogs = ShadowLog.getLogsForTag("AndroidAlarmScheduler")
        assertTrue(
            "Expected warning about exact alarm permission",
            warningLogs.any { it.msg.contains("Exact alarm scheduling not permitted; skipping") }
        )
    }

    @Test
    fun `AndroidAlarmSchedulerCancelsAlarm_whenCancelInvoked`() {
        val alarm = createAlarm(id = 7, time = LocalTime.of(6, 45))
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        scheduler.schedule(alarm)

        scheduler.cancel(alarm.id)

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id,
            createAlarmIntent(alarm.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
        assertNull("PendingIntent should be removed after cancel", pendingIntent)
        assertNull("AlarmManager should not have remaining alarm clock", alarmManager.nextAlarmClock)
    }

    @Test
    fun `AndroidAlarmSchedulerCancelIsNoOp_whenAlarmNotScheduled`() {
        scheduler.cancel(999)
        assertNull(alarmManager.nextAlarmClock)
    }

    @Test
    fun `AndroidAlarmSchedulerSynchronizeSchedulesActive_andCancelsInactive`() {
        val activeAlarm = createAlarm(id = 1, time = LocalTime.of(7, 0), repeatDays = setOf(DayOfWeek.MONDAY))
        val inactiveAlarm = createAlarm(id = 2, time = LocalTime.of(8, 0)).copy(isActive = false)
        val newlyActiveAlarm = createAlarm(id = 3, time = LocalTime.of(9, 30))
        ShadowAlarmManager.setCanScheduleExactAlarms(true)

        // Pre-populate with an old schedule for the inactive alarm to verify cancellation.
        scheduler.schedule(inactiveAlarm.copy(isActive = true))

        scheduler.synchronize(listOf(activeAlarm, inactiveAlarm, newlyActiveAlarm))

        val activeIntent = PendingIntent.getBroadcast(
            context,
            activeAlarm.id,
            createAlarmIntent(activeAlarm.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
        val inactiveIntent = PendingIntent.getBroadcast(
            context,
            inactiveAlarm.id,
            createAlarmIntent(inactiveAlarm.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
        val newIntent = PendingIntent.getBroadcast(
            context,
            newlyActiveAlarm.id,
            createAlarmIntent(newlyActiveAlarm.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )

        assertNotNull("Existing active alarm should remain scheduled", activeIntent)
        assertNull("Inactive alarm should be cancelled", inactiveIntent)
        assertNotNull("Newly active alarm should be scheduled", newIntent)

        alarmManager.nextAlarmClock?.let { info ->
            assertTrue(
                "The next alarm clock should be scheduled in the future",
                info.triggerTime >= clock.millis()
            )
        }
    }

    @Test
    fun `AndroidAlarmSchedulerSchedulesAcrossDSTForward_withoutSkippingDay`() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
        updateClock(
            LocalDateTime.of(2024, 3, 9, 23, 0)
                .atZone(ZoneId.systemDefault())
                .toInstant()
        )
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val alarm = createAlarm(
            id = 12,
            time = LocalTime.of(2, 30),
            repeatDays = setOf(DayOfWeek.SUNDAY)
        )

        scheduler.schedule(alarm)

        val alarmClock = alarmManager.nextAlarmClock
        assertNotNull(alarmClock)
        val expectedMillis = calculateNextTriggerMillis(
            alarm,
            LocalDateTime.now(clock)
        )
        assertEquals("Trigger should land on the first valid post-DST time", expectedMillis, alarmClock!!.triggerTime)
    }

    private fun createAlarm(
        id: Int,
        time: LocalTime,
        repeatDays: Set<DayOfWeek> = emptySet()
    ): AlarmUiModel {
        return AlarmUiModel(
            id = id,
            time = time,
            label = "Alarm $id",
            isActive = true,
            repeatDays = repeatDays,
            soundUri = "content://alarms/$id",
            dismissTask = AlarmDismissTaskType.DEFAULT,
            qrBarcodeValue = null,
            qrRequiredUniqueCount = 0
        )
    }

    private fun createAlarmIntent(alarmId: Int) = android.content.Intent(context, AlarmReceiver::class.java).apply {
        action = AlarmIntents.ACTION_ALARM_FIRED
        putExtra(AlarmIntents.EXTRA_ALARM_ID, alarmId)
    }

    private fun updateClock(instant: Instant) {
        clock = Clock.fixed(instant, ZoneId.systemDefault())
        scheduler = AndroidAlarmScheduler(context, clock)
    }
}
