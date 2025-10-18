package hu.bbara.viewideas.ui.alarm.dismiss

import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmDismissTaskTypeTest {

    @Test
    fun `fromStorageKey falls back to default for null or unknown`() {
        assertEquals(AlarmDismissTaskType.DEFAULT, AlarmDismissTaskType.fromStorageKey(null))
        assertEquals(AlarmDismissTaskType.DEFAULT, AlarmDismissTaskType.fromStorageKey("missing"))
    }

    @Test
    fun `createTask produces expected implementation and ids`() {
        val expectedIds = mapOf(
            AlarmDismissTaskType.OBJECT_DETECTION to "object_detection",
            AlarmDismissTaskType.MATH_CHALLENGE to "math_challenge",
            AlarmDismissTaskType.QR_BARCODE_SCAN to "qr_barcode_scan",
            AlarmDismissTaskType.FOCUS_TIMER to "focus_timer",
            AlarmDismissTaskType.MEMORY to "memory_task"
        )

        expectedIds.forEach { (type, expectedId) ->
            val task = type.createTask()
            assertEquals("Task id should match implementation id", expectedId, task.id)
        }
    }
}
