package hu.bbara.breakthesnooze.ui.alarm

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import hu.bbara.breakthesnooze.designsystem.BreakTheSnoozeTheme
import hu.bbara.breakthesnooze.ui.alarm.dismiss.AlarmDismissTaskType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalTime
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class AlarmListScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `alarm list shows empty state when no alarms`() {
        composeTestRule.setContent {
            BreakTheSnoozeTheme {
                AlarmListRoute(
                    durationAlarms = emptyList(),
                    onCancelDurationAlarm = {},
                    alarms = emptyList(),
                    onToggle = { _, _ -> },
                    onEdit = {},
                    selectedIds = emptySet(),
                    onEnterSelection = {},
                    onToggleSelection = {},
                    onClearSelection = {},
                    onDeleteSelection = {},
                    onCreate = {},
                    onOpenSettings = {},
                    tightGapWarningEnabled = false
                )
            }
        }

        composeTestRule.onNodeWithText("No alarms scheduled").assertIsDisplayed()
        composeTestRule.onNodeWithText("No alarms yet").assertIsDisplayed()
    }

    @Test
    fun `selection top bar appears when selection active`() {
        composeTestRule.setContent {
            BreakTheSnoozeTheme {
                AlarmListRoute(
                    durationAlarms = emptyList(),
                    onCancelDurationAlarm = {},
                    alarms = listOf(sampleAlarm(id = 1)),
                    onToggle = { _, _ -> },
                    onEdit = {},
                    selectedIds = setOf(1),
                    onEnterSelection = {},
                    onToggleSelection = {},
                    onClearSelection = {},
                    onDeleteSelection = {},
                    onCreate = {},
                    onOpenSettings = {},
                    tightGapWarningEnabled = false
                )
            }
        }

        composeTestRule.onNodeWithText("1 selected").assertIsDisplayed()
    }

    @Test
    fun `toggling switch invokes callback`() {
        val toggleResult = AtomicReference<Pair<Int, Boolean>?>(null)

        composeTestRule.setContent {
            BreakTheSnoozeTheme {
                AlarmListRoute(
                    durationAlarms = emptyList(),
                    onCancelDurationAlarm = {},
                    alarms = listOf(sampleAlarm(id = 7, isActive = true)),
                    onToggle = { id, isActive -> toggleResult.set(id to isActive) },
                    onEdit = {},
                    selectedIds = emptySet(),
                    onEnterSelection = {},
                    onToggleSelection = {},
                    onClearSelection = {},
                    onDeleteSelection = {},
                    onCreate = {},
                    onOpenSettings = {},
                    tightGapWarningEnabled = false
                )
            }
        }

        composeTestRule.onNode(isToggleable()).performClick()
        composeTestRule.waitForIdle()

        val result = toggleResult.get()
        assertNotNull(result)
        assertEquals(7, result!!.first)
        assertFalse(result.second)
    }

    private fun sampleAlarm(
        id: Int,
        isActive: Boolean = true
    ): AlarmUiModel = AlarmUiModel(
        id = id,
        time = LocalTime.of(7, 0),
        label = "Alarm $id",
        isActive = isActive,
        repeatDays = emptySet(),
        soundUri = null,
        dismissTask = AlarmDismissTaskType.DEFAULT,
        qrBarcodeValue = null,
        qrRequiredUniqueCount = 0
    )
}
