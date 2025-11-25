package hu.bbara.breakthesnooze.alarm

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import hu.bbara.breakthesnooze.MainDispatcherRule
import hu.bbara.breakthesnooze.ui.alarm.AlarmUiModel
import hu.bbara.breakthesnooze.ui.alarm.dismiss.AlarmDismissTaskType
import kotlinx.coroutines.Job
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.S])
class AlarmRingtoneServiceTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun pauseCancelsWearFallbackWhenNoPlaybackStarted() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val controller = Robolectric.buildService(AlarmRingtoneService::class.java).create()
        val service = controller.get()

        setField(service, "currentAlarmId", 42)
        setField(service, "currentAlarm", baseAlarm(id = 42))

        val ackIntent = Intent(context, AlarmRingtoneService::class.java).apply {
            action = AlarmIntents.ACTION_WEAR_ACK
            putExtra(AlarmIntents.EXTRA_ALARM_ID, 42)
        }
        service.onStartCommand(ackIntent, 0, 0)

        val scheduledJob = getField<Job?>(service, "wearFallbackJob")
        assertThat(scheduledJob).isNotNull()
        assertThat(scheduledJob!!.isActive).isTrue()

        val pauseIntent = Intent(context, AlarmRingtoneService::class.java).apply {
            action = AlarmIntents.ACTION_PAUSE_ALARM
            putExtra(AlarmIntents.EXTRA_ALARM_ID, 42)
        }
        service.onStartCommand(pauseIntent, 0, 0)

        assertThat(getField<Job?>(service, "wearFallbackJob")).isNull()
        assertThat(getField<Boolean>(service, "isPaused")).isTrue()

        controller.destroy()
    }

    private fun baseAlarm(id: Int) = AlarmUiModel(
        id = id,
        time = LocalTime.of(7, 0),
        label = "Alarm",
        isActive = true,
        repeatDays = setOf(DayOfWeek.MONDAY),
        soundUri = null,
        dismissTask = AlarmDismissTaskType.DEFAULT,
        qrBarcodeValue = null,
        qrRequiredUniqueCount = 0
    )

    private fun <T> getField(service: AlarmRingtoneService, name: String): T {
        val field = AlarmRingtoneService::class.java.getDeclaredField(name)
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return field.get(service) as T
    }

    private fun setField(service: AlarmRingtoneService, name: String, value: Any?) {
        val field = AlarmRingtoneService::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.set(service, value)
    }
}
