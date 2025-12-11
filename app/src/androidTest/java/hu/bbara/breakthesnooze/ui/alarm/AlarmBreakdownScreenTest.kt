package hu.bbara.breakthesnooze.ui.alarm

import androidx.activity.ComponentActivity
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import hu.bbara.breakthesnooze.R
import hu.bbara.breakthesnooze.data.alarm.model.WakeEvent
import hu.bbara.breakthesnooze.designsystem.BreakTheSnoozeTheme
import hu.bbara.breakthesnooze.ui.alarm.breakdown.AlarmBreakdownRoute
import hu.bbara.breakthesnooze.ui.alarm.dismiss.AlarmDismissTaskType
import hu.bbara.breakthesnooze.ui.alarm.model.BreakdownPeriod
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@RunWith(AndroidJUnit4::class)
class AlarmBreakdownScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var periodState: MutableState<BreakdownPeriod>

    @Test
    fun emptyStateVisibleWhenNoEvents() {
        setContent(emptyList())

        val emptyText = composeRule.activity.getString(R.string.alarm_breakdown_empty_day)
        composeRule.onNodeWithText(emptyText).assertIsDisplayed()
    }

    @Test
    fun selectingDifferentDayShowsThatDaysEvents() {
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)
        val yesterday = today.minusDays(1)
        val events = listOf(
            wakeEvent(id = 1, daysAgo = 0, label = "Today alarm"),
            wakeEvent(id = 2, daysAgo = 1, label = "Yesterday alarm")
        )
        setContent(events)

        composeRule.onNodeWithText("Today alarm").assertIsDisplayed()

        composeRule.onNodeWithTag("calendar_day_${yesterday}", useUnmergedTree = true)
            .performClick()

        composeRule.onNodeWithText("Yesterday alarm").assertIsDisplayed()
    }

    @Test
    fun switchingToMonthlyUpdatesCalendarHeader() {
        val events = listOf(wakeEvent(id = 10, daysAgo = 0, label = "Morning"))
        setContent(events)

        val monthlyLabel = composeRule.activity.getString(R.string.alarm_breakdown_monthly)
        composeRule.onNodeWithText(monthlyLabel).performClick()

        val formatter = DateTimeFormatter.ofPattern("LLLL yyyy")
        val currentMonth = LocalDate.now().withDayOfMonth(1).format(formatter)
        composeRule.onNodeWithText(currentMonth).assertIsDisplayed()

        val nextDesc = composeRule.activity.getString(R.string.alarm_breakdown_next_month)
        composeRule.onNodeWithContentDescription(nextDesc).performClick()

        val nextMonthLabel = LocalDate.now().plusMonths(1).withDayOfMonth(1).format(formatter)
        composeRule.onNodeWithText(nextMonthLabel).assertIsDisplayed()

        composeRule.runOnIdle {
            assertThat(periodState.value).isEqualTo(BreakdownPeriod.Monthly)
        }
    }

    private fun setContent(events: List<WakeEvent>, initialPeriod: BreakdownPeriod = BreakdownPeriod.Weekly) {
        composeRule.setContent {
            BreakTheSnoozeTheme {
                val period = remember { mutableStateOf(initialPeriod) }
                periodState = period
                AlarmBreakdownRoute(
                    events = events,
                    period = period.value,
                    onPeriodChange = { period.value = it },
                    onOpenSettings = {}
                )
            }
        }
    }

    private fun wakeEvent(id: Long, daysAgo: Long, label: String): WakeEvent {
        val instant = Instant.now().minusSeconds(daysAgo * 24 * 60 * 60)
        return WakeEvent(
            id = id,
            alarmId = id.toInt(),
            alarmLabel = label,
            dismissTask = AlarmDismissTaskType.MATH_CHALLENGE,
            completedAt = instant
        )
    }
}
