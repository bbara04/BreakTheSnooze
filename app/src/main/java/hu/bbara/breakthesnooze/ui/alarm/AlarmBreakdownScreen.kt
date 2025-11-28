package hu.bbara.breakthesnooze.ui.alarm

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import hu.bbara.breakthesnooze.R
import hu.bbara.breakthesnooze.data.alarm.model.WakeEvent
import hu.bbara.breakthesnooze.designsystem.BreakTheSnoozeTheme
import hu.bbara.breakthesnooze.ui.alarm.dismiss.AlarmDismissTaskType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AlarmBreakdownRoute(
    events: List<WakeEvent>,
    period: BreakdownPeriod,
    onPeriodChange: (BreakdownPeriod) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val zoneId = remember { ZoneId.systemDefault() }
    val today = LocalDate.now(zoneId)
    var selectedDate by remember(period, today) { mutableStateOf(today) }
    var referenceMonth by remember(period, today) { mutableStateOf(today.withDayOfMonth(1)) }
    val eventsByDate = remember(events, zoneId) {
        events.groupBy { it.completedAt.atZone(zoneId).toLocalDate() }
            .mapValues { (_, value) -> value.sortedByDescending { event -> event.completedAt } }
    }
    val weekStart = remember(today) {
        today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }
    val weekDays = remember(weekStart) { (0..6).map { offset -> weekStart.plusDays(offset.toLong()) } }

    LaunchedEffect(period, today) {
        if (period == BreakdownPeriod.Weekly && selectedDate !in weekDays) {
            selectedDate = today
        }
    }

    LaunchedEffect(period, selectedDate) {
        if (period == BreakdownPeriod.Monthly) {
            referenceMonth = selectedDate.withDayOfMonth(1)
        }
    }

    val selectedDayEvents = eventsByDate[selectedDate].orEmpty()

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(id = R.string.settings_title)
                        )
                    }
                },
                title = { Text(text = stringResource(id = R.string.alarm_breakdown_title)) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
        ) {
            BreakdownPeriodSelector(
                selected = period,
                onSelected = onPeriodChange
            )

            Spacer(modifier = Modifier.height(16.dp))

            when (period) {
                BreakdownPeriod.Weekly -> WeeklyCalendar(
                    days = weekDays,
                    selectedDate = selectedDate,
                    today = today,
                    eventsByDate = eventsByDate,
                    onSelectDay = { selectedDate = it }
                )

                BreakdownPeriod.Monthly -> MonthlyCalendar(
                    month = referenceMonth,
                    selectedDate = selectedDate,
                    today = today,
                    eventsByDate = eventsByDate,
                    onSelectDay = { selectedDate = it },
                    onMonthChange = { newMonth ->
                        referenceMonth = newMonth
                        selectedDate = if (newMonth == today.withDayOfMonth(1)) today else newMonth
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            PastAlarmsSection(
                selectedDate = selectedDate,
                events = selectedDayEvents,
                zoneId = zoneId,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}

@Composable
private fun BreakdownPeriodSelector(
    selected: BreakdownPeriod,
    onSelected: (BreakdownPeriod) -> Unit
) {
    val options = listOf(BreakdownPeriod.Weekly, BreakdownPeriod.Monthly)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelected(option) },
                label = {
                    Text(
                        text = when (option) {
                            BreakdownPeriod.Weekly -> stringResource(id = R.string.alarm_breakdown_weekly)
                            BreakdownPeriod.Monthly -> stringResource(id = R.string.alarm_breakdown_monthly)
                        }
                    )
                }
            )
        }
    }
}

@Composable
private fun WeeklyCalendar(
    days: List<LocalDate>,
    selectedDate: LocalDate,
    today: LocalDate,
    eventsByDate: Map<LocalDate, List<WakeEvent>>,
    onSelectDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        DayOfWeekHeader()
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            days.forEach { day ->
                CalendarDayCell(
                    date = day,
                    isSelected = day == selectedDate,
                    isToday = day == today,
                    hasEvents = !eventsByDate[day].isNullOrEmpty(),
                    isInCurrentMonth = true,
                    onSelectDay = onSelectDay
                )
            }
        }
    }
}

@Composable
private fun MonthlyCalendar(
    month: LocalDate,
    selectedDate: LocalDate,
    today: LocalDate,
    eventsByDate: Map<LocalDate, List<WakeEvent>>,
    onSelectDay: (LocalDate) -> Unit,
    onMonthChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val monthLabelFormatter = remember { DateTimeFormatter.ofPattern("LLLL yyyy") }
    val monthLabel = remember(month) { month.format(monthLabelFormatter) }
    val monthStart = remember(month) { month.withDayOfMonth(1) }
    val weeks = remember(month) { buildMonthWeeks(monthStart) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { onMonthChange(month.minusMonths(1)) }) {
                Icon(
                    imageVector = Icons.Default.ChevronLeft,
                    contentDescription = stringResource(id = R.string.alarm_breakdown_previous_month)
                )
            }
            Text(
                text = monthLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            IconButton(onClick = { onMonthChange(month.plusMonths(1)) }) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = stringResource(id = R.string.alarm_breakdown_next_month)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        DayOfWeekHeader()
        Spacer(modifier = Modifier.height(8.dp))
        weeks.forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    CalendarDayCell(
                        date = day,
                        isSelected = day == selectedDate,
                        isToday = day == today,
                        hasEvents = !eventsByDate[day].isNullOrEmpty(),
                        isInCurrentMonth = day.month == monthStart.month,
                        onSelectDay = onSelectDay
                    )
                }
            }
        }
    }
}

@Composable
private fun DayOfWeekHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        dayOrder.forEach { day ->
            Text(
                text = day.displayName(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun RowScope.CalendarDayCell(
    date: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    hasEvents: Boolean,
    isInCurrentMonth: Boolean,
    onSelectDay: (LocalDate) -> Unit
) {
    val baseModifier = Modifier
        .weight(1f)
        .aspectRatio(1f)
        .padding(4.dp)

    Surface(
        modifier = baseModifier
            .testTag("calendar_day_${date}")
            .clip(RoundedCornerShape(12.dp))
            .clickable { onSelectDay(date) },
        shape = RoundedCornerShape(12.dp),
        color = when {
            isSelected -> MaterialTheme.colorScheme.primaryContainer
            isInCurrentMonth -> MaterialTheme.colorScheme.surfaceVariant
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        },
        contentColor = when {
            isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onSurface
        },
        tonalElevation = if (isSelected) 6.dp else 0.dp,
        border = if (isToday && !isSelected) BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary
        ) else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else if (!isInCurrentMonth) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            if (hasEvents) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                )
            } else {
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun PastAlarmsSection(
    selectedDate: LocalDate,
    events: List<WakeEvent>,
    zoneId: ZoneId,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val is24Hour = remember(context) { DateFormat.is24HourFormat(context) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEEE, MMM d") }
    val dateLabel = remember(selectedDate) { selectedDate.format(dateFormatter) }

    Column(modifier = modifier) {
        Text(
            text = dateLabel,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (events.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                Text(
                    text = stringResource(id = R.string.alarm_breakdown_empty_day),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(events, key = { it.id }) { event ->
                    PastAlarmRow(
                        event = event,
                        zoneId = zoneId,
                        is24Hour = is24Hour
                    )
                }
            }
        }
    }
}

@Composable
private fun PastAlarmRow(
    event: WakeEvent,
    zoneId: ZoneId,
    is24Hour: Boolean,
    modifier: Modifier = Modifier
) {
    val eventDateTime = remember(event.completedAt to zoneId) { event.completedAt.atZone(zoneId) }
    val timeLabel = remember(eventDateTime.toLocalTime() to is24Hour) {
        eventDateTime.toLocalTime().formatForDisplay(is24Hour)
    }
    val taskLabel = stringResource(id = event.dismissTask.optionLabelResId)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = timeLabel,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (event.alarmLabel.isNotBlank()) {
                Text(
                    text = event.alarmLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = taskLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun buildMonthWeeks(monthStart: LocalDate): List<List<LocalDate>> {
    val start = monthStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val end = monthStart.withDayOfMonth(monthStart.lengthOfMonth())
        .with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
    val weeks = mutableListOf<List<LocalDate>>()
    var current = start
    while (!current.isAfter(end)) {
        val week = (0..6).map { offset -> current.plusDays(offset.toLong()) }
        weeks += week
        current = current.plusWeeks(1)
    }
    return weeks
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun AlarmBreakdownWeeklyPreview() {
    val now = Instant.now()
    val sampleEvents = listOf(
        WakeEvent(
            id = 1,
            alarmId = 1,
            alarmLabel = "Morning run",
            dismissTask = AlarmDismissTaskType.MATH_CHALLENGE,
            completedAt = now
        ),
        WakeEvent(
            id = 2,
            alarmId = 2,
            alarmLabel = "Gym",
            dismissTask = AlarmDismissTaskType.OBJECT_DETECTION,
            completedAt = now.minusSeconds(60 * 60 * 24 * 2)
        ),
        WakeEvent(
            id = 3,
            alarmId = 3,
            alarmLabel = "Study",
            dismissTask = AlarmDismissTaskType.FOCUS_TIMER,
            completedAt = now.minusSeconds(60 * 60 * 24 * 8)
        )
    )
    BreakTheSnoozeTheme {
        AlarmBreakdownRoute(
            events = sampleEvents,
            period = BreakdownPeriod.Weekly,
            onPeriodChange = {},
            onOpenSettings = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun AlarmBreakdownMonthlyPreview() {
    val now = Instant.now()
    val sampleEvents = listOf(
        WakeEvent(
            id = 1,
            alarmId = 1,
            alarmLabel = "Morning run",
            dismissTask = AlarmDismissTaskType.MATH_CHALLENGE,
            completedAt = now
        ),
        WakeEvent(
            id = 2,
            alarmId = 2,
            alarmLabel = "Gym",
            dismissTask = AlarmDismissTaskType.OBJECT_DETECTION,
            completedAt = now.minusSeconds(60 * 60 * 24 * 12)
        ),
        WakeEvent(
            id = 3,
            alarmId = 3,
            alarmLabel = "Study",
            dismissTask = AlarmDismissTaskType.FOCUS_TIMER,
            completedAt = now.minusSeconds(60 * 60 * 24 * 34)
        )
    )
    BreakTheSnoozeTheme {
        AlarmBreakdownRoute(
            events = sampleEvents,
            period = BreakdownPeriod.Monthly,
            onPeriodChange = {},
            onOpenSettings = {}
        )
    }
}
