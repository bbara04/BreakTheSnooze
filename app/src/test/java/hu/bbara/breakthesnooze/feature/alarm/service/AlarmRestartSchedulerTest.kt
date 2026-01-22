package hu.bbara.breakthesnooze.feature.alarm.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.S])
@LooperMode(LooperMode.Mode.LEGACY)
class AlarmRestartSchedulerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var alarmManager: AlarmManager
    private lateinit var scheduler: AlarmRestartScheduler

    @Before
    fun setUp() {
        alarmManager = context.getSystemService(AlarmManager::class.java)
        ShadowAlarmManager.reset()
        scheduler = AlarmRestartScheduler(context)
    }

    @Test
    fun `schedule creates restart alarm and cancel removes it`() {
        val alarmId = 12
        val before = System.currentTimeMillis()

        scheduler.schedule(alarmId)

        val after = System.currentTimeMillis()
        val requestCode = requestCodeFor(alarmId)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            createIntent(alarmId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
        assertThat(pendingIntent).isNotNull()

        val shadowAlarmManager = shadowOf(alarmManager)
        val scheduledAlarm = shadowAlarmManager.nextScheduledAlarm
        assertThat(scheduledAlarm).isNotNull()
        assertThat(scheduledAlarm!!.operation).isEqualTo(pendingIntent)
        val scheduledAt = scheduledAlarm.triggerAtTime
        assertThat(scheduledAt).isAtLeast(before + AlarmRestartScheduler.RESTART_DELAY_MS)
        assertThat(scheduledAt).isAtMost(after + AlarmRestartScheduler.RESTART_DELAY_MS + 500)

        scheduler.cancel(alarmId)

        val clearedIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            createIntent(alarmId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
        assertThat(clearedIntent).isNull()
        assertThat(shadowAlarmManager.scheduledAlarms).isEmpty()
    }

    private fun requestCodeFor(alarmId: Int): Int {
        val field = AlarmRestartScheduler::class.java.getDeclaredField("REQUEST_CODE_BASE").apply {
            isAccessible = true
        }
        val base = field.getInt(null)
        return base + alarmId
    }

    private fun createIntent(alarmId: Int) = Intent(context, AlarmReceiver::class.java).apply {
        action = AlarmIntents.ACTION_ALARM_FIRED
        putExtra(AlarmIntents.EXTRA_ALARM_ID, alarmId)
    }
}
