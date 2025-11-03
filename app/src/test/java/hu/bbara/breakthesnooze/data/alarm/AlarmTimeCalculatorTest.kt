package hu.bbara.breakthesnooze.data.alarm

import hu.bbara.breakthesnooze.ui.alarm.AlarmUiModel
import hu.bbara.breakthesnooze.ui.alarm.dismiss.AlarmDismissTaskType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class AlarmTimeCalculatorTest {

    @Test
    fun `calculateNextTrigger returns same-day time for one-time alarm in the future`() {
        val reference = LocalDateTime.of(2024, 5, 10, 8, 0)
        val alarm = baseAlarm().copy(time = LocalTime.of(9, 30), repeatDays = emptySet())

        val nextTrigger = calculateNextTrigger(alarm, reference)

        val expected = reference.withHour(9).withMinute(30).withSecond(0).withNano(0)
        assertEquals(expected, nextTrigger)
    }

    @Test
    fun `calculateNextTrigger returns next-day time for one-time alarm in the past`() {
        val reference = LocalDateTime.of(2024, 5, 10, 12, 0)
        val alarm = baseAlarm().copy(time = LocalTime.of(9, 0), repeatDays = emptySet())

        val nextTrigger = calculateNextTrigger(alarm, reference)

        val expected = reference.plusDays(1).withHour(9).withMinute(0).withSecond(0).withNano(0)
        assertEquals(expected, nextTrigger)
    }

    @Test
    fun `calculateNextTrigger picks closest repeat day when today time passed`() {
        val reference = LocalDateTime.of(2024, 5, 6, 10, 0) // Monday
        val alarm = baseAlarm().copy(
            time = LocalTime.of(9, 0),
            repeatDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)
        )

        val nextTrigger = calculateNextTrigger(alarm, reference)

        val expected = LocalDateTime.of(LocalDate.of(2024, 5, 8), LocalTime.of(9, 0)) // Wednesday
        assertEquals(expected, nextTrigger)
    }

    @Test
    fun `calculateNextTrigger keeps today for repeating alarm when time not passed`() {
        val reference = LocalDateTime.of(2024, 5, 6, 8, 0) // Monday
        val alarm = baseAlarm().copy(
            time = LocalTime.of(9, 0),
            repeatDays = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)
        )

        val nextTrigger = calculateNextTrigger(alarm, reference)

        val expected = LocalDateTime.of(LocalDate.of(2024, 5, 6), LocalTime.of(9, 0))
        assertEquals(expected, nextTrigger)
    }

    @Test
    fun `calculateNextTriggerMillis matches millis of calculateNextTrigger`() {
        val reference = LocalDateTime.of(2024, 5, 6, 8, 0)
        val alarm = baseAlarm().copy(time = LocalTime.of(9, 15), repeatDays = emptySet())

        val nextMillis = calculateNextTriggerMillis(alarm, reference)

        val expectedDateTime = calculateNextTrigger(alarm, reference)
        assertNotNull(expectedDateTime)
        val expectedMillis = expectedDateTime!!
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        assertEquals(expectedMillis, nextMillis)
    }

    private fun baseAlarm(): AlarmUiModel = AlarmUiModel(
        id = 1,
        time = LocalTime.of(6, 0),
        label = "Test Alarm",
        isActive = true,
        repeatDays = emptySet(),
        soundUri = null,
        dismissTask = AlarmDismissTaskType.DEFAULT,
        qrBarcodeValue = null,
        qrRequiredUniqueCount = 0
    )
}
