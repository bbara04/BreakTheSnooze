package hu.bbara.breakthesnooze.ui.alarm

import hu.bbara.breakthesnooze.data.alarm.repository.toUiModelWithId
import hu.bbara.breakthesnooze.ui.alarm.dismiss.AlarmDismissTaskType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.util.Locale

class AlarmModelsTest {

    private var originalLocale: Locale? = null

    @Before
    fun setUp() {
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun tearDown() {
        originalLocale?.let { Locale.setDefault(it) }
    }

    @Test
    fun `AlarmUiModel converts to creation state with copied data`() {
        val alarm = baseAlarm().copy(
            time = LocalTime.of(7, 45),
            label = "Morning workout",
            repeatDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
            soundUri = "content://alarm",
            dismissTask = AlarmDismissTaskType.MEMORY,
            qrBarcodeValue = "code-123",
            qrRequiredUniqueCount = 2
        )

        val creationState = alarm.toCreationState()

        assertEquals(alarm.time, creationState.time)
        assertEquals(alarm.label, creationState.label)
        assertEquals(alarm.repeatDays, creationState.repeatDays)
        assertEquals(alarm.soundUri, creationState.soundUri)
        assertEquals(alarm.dismissTask, creationState.dismissTask)
        assertEquals(alarm.qrBarcodeValue, creationState.qrBarcodeValue)
        assertEquals(alarm.qrRequiredUniqueCount, creationState.qrRequiredUniqueCount)
        assertFalse("Repeat days should be copied not referenced", alarm.repeatDays === creationState.repeatDays)
    }

    @Test
    fun `formatForDisplay renders times in 24 and 12 hour formats`() {
        val time = LocalTime.of(15, 5)

        assertEquals("15:05", time.formatForDisplay(is24Hour = true))
        assertEquals("3:05 PM", time.formatForDisplay(is24Hour = false))
    }

    @Test
    fun `formatRemaining shows combined hours and minutes`() {
        val duration = Duration.ofMinutes(95)

        val formatted = formatRemaining(duration)

        assertEquals("In 1h 35m", formatted)
    }

    @Test
    fun `formatRemaining indicates passed durations`() {
        val duration = Duration.ofMinutes(-5)

        val formatted = formatRemaining(duration)

        assertEquals("In 0m (passed)", formatted)
    }

    @Test
    fun `formatDays handles known presets`() {
        assertEquals("Once", formatDays(emptySet()))
        assertEquals("Every day", formatDays(dayOrder.toSet()))
        assertEquals("Weekdays", formatDays(dayOrder.take(5).toSet()))
        assertEquals("Weekends", formatDays(dayOrder.takeLast(2).toSet()))
    }

    @Test
    fun `formatDays lists specific days in order`() {
        val formatted = formatDays(setOf(DayOfWeek.WEDNESDAY, DayOfWeek.MONDAY, DayOfWeek.SATURDAY))

        assertEquals("Mon, Wed, Sat", formatted)
    }

    @Test
    fun `AlarmCreationState converts to ui model when time available`() {
        val creationState = AlarmCreationState(
            time = LocalTime.of(6, 30),
            label = "Alarm",
            repeatDays = setOf(DayOfWeek.FRIDAY),
            soundUri = "content://alarm",
            dismissTask = AlarmDismissTaskType.DEFAULT,
            qrBarcodeValue = "code",
            qrRequiredUniqueCount = 3
        )

        val uiModel = creationState.toUiModelWithId(id = 5, isActive = false)

        assertNotNull(uiModel)
        uiModel!!
        assertEquals(5, uiModel.id)
        assertEquals(false, uiModel.isActive)
        assertEquals(creationState.time, uiModel.time)
        assertEquals(creationState.label, uiModel.label)
        assertEquals(creationState.repeatDays, uiModel.repeatDays)
        assertEquals(creationState.soundUri, uiModel.soundUri)
        assertEquals(creationState.dismissTask, uiModel.dismissTask)
        assertEquals(creationState.qrBarcodeValue, uiModel.qrBarcodeValue)
        assertEquals(creationState.qrRequiredUniqueCount, uiModel.qrRequiredUniqueCount)
    }

    @Test
    fun `AlarmCreationState returns null when time missing`() {
        val creationState = AlarmCreationState(
            time = null,
            label = "Alarm",
            repeatDays = emptySet(),
            soundUri = null,
            dismissTask = AlarmDismissTaskType.DEFAULT,
            qrBarcodeValue = null,
            qrRequiredUniqueCount = 0
        )

        assertNull(creationState.toUiModelWithId())
    }

    @Test
    fun `resolveNextAlarm returns null when all alarms inactive`() {
        val alarms = listOf(
            baseAlarm().copy(isActive = false),
            baseAlarm().copy(id = 2, isActive = false)
        )

        assertNull(resolveNextAlarm(alarms))
    }

    private fun baseAlarm(): AlarmUiModel = AlarmUiModel(
        id = 1,
        time = LocalTime.of(6, 0),
        label = "Test",
        isActive = true,
        repeatDays = emptySet(),
        soundUri = null,
        dismissTask = AlarmDismissTaskType.DEFAULT,
        qrBarcodeValue = null,
        qrRequiredUniqueCount = 0
    )
}
