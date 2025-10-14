package hu.bbara.viewideas.ui.alarm

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import hu.bbara.viewideas.R
import hu.bbara.viewideas.data.alarm.WakeEvent
import hu.bbara.viewideas.ui.alarm.dismiss.AlarmDismissTaskType
import hu.bbara.viewideas.ui.theme.ViewIdeasTheme
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
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
    val breakdown = remember(events, period, zoneId) {
        buildWakeBreakdown(events, period, zoneId)
    }

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

            if (breakdown.isEmpty()) {
                EmptyBreakdownState(modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(breakdown) { group ->
                        WakeBreakdownCard(group = group)
                    }
                }
            }
        }
    }
}

@Composable
private fun BreakdownPeriodSelector(
    selected: BreakdownPeriod,
    onSelected: (BreakdownPeriod) -> Unit
) {
    val options = listOf(BreakdownPeriod.Weekly, BreakdownPeriod.Monthly)
    androidx.compose.foundation.layout.Row(
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
private fun WakeBreakdownCard(group: WakeBreakdownGroup) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = group.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            group.entries.forEachIndexed { index, entry ->
                WakeBreakdownRow(entry = entry)
                if (index != group.entries.lastIndex) {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun WakeBreakdownRow(entry: WakeBreakdownEntry) {
    val context = LocalContext.current
    val is24Hour = remember(context) { DateFormat.is24HourFormat(context) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEE, MMM d") }
    val dateLabel = remember(entry.date) { entry.date.format(dateFormatter) }
    val timeLabel = remember(entry.time to is24Hour) { entry.time.formatForDisplay(is24Hour) }
    val taskLabel = stringResource(id = entry.task.optionLabelResId)

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "$dateLabel · $timeLabel",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (entry.label.isNotBlank()) {
            Text(
                text = entry.label,
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

@Composable
private fun EmptyBreakdownState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = androidx.compose.material.icons.Icons.Default.Alarm,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(id = R.string.alarm_breakdown_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(id = R.string.alarm_breakdown_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private data class WakeBreakdownGroup(
    val title: String,
    val entries: List<WakeBreakdownEntry>
)

private data class WakeBreakdownEntry(
    val date: LocalDate,
    val time: LocalTime,
    val label: String,
    val task: AlarmDismissTaskType
)

private fun buildWakeBreakdown(
    events: List<WakeEvent>,
    period: BreakdownPeriod,
    zoneId: ZoneId
): List<WakeBreakdownGroup> {
    if (events.isEmpty()) return emptyList()
    val sorter = events.sortedByDescending { it.completedAt }
    val grouped = when (period) {
        BreakdownPeriod.Weekly -> sorter.groupBy { event ->
            event.completedAt.atZone(zoneId).toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        }
        BreakdownPeriod.Monthly -> sorter.groupBy { event ->
            event.completedAt.atZone(zoneId).toLocalDate().withDayOfMonth(1)
        }
    }

    val titleFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    val monthFormatter = DateTimeFormatter.ofPattern("LLLL yyyy")

    return grouped.entries
        .sortedByDescending { it.key }
        .map { (key, items) ->
            val title = when (period) {
                BreakdownPeriod.Weekly -> formatWeekRangeTitle(key, titleFormatter)
                BreakdownPeriod.Monthly -> key.format(monthFormatter)
            }
            val entries = items.sortedByDescending { it.completedAt }
                .map { event ->
                    val localDateTime = event.completedAt.atZone(zoneId)
                    WakeBreakdownEntry(
                        date = localDateTime.toLocalDate(),
                        time = localDateTime.toLocalTime(),
                        label = event.alarmLabel,
                        task = event.dismissTask
                    )
                }
            WakeBreakdownGroup(title = title, entries = entries)
        }
}

private fun formatWeekRangeTitle(
    start: LocalDate,
    formatter: DateTimeFormatter
): String {
    val end = start.plusDays(6)
    val startLabel = start.format(formatter)
    val endLabel = end.format(formatter)
    return "Week of $startLabel – $endLabel"
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun AlarmBreakdownPreview() {
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
    ViewIdeasTheme {
        AlarmBreakdownRoute(
            events = sampleEvents,
            period = BreakdownPeriod.Weekly,
            onPeriodChange = {},
            onOpenSettings = {}
        )
    }
}
