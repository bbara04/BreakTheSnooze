package hu.bbara.viewideas.ui.alarm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import hu.bbara.viewideas.ui.theme.ViewIdeasTheme
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private val dayOrder = listOf(
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY,
    DayOfWeek.SATURDAY,
    DayOfWeek.SUNDAY
)

data class AlarmUiModel(
    val id: Int,
    val time: LocalTime,
    val label: String,
    val isActive: Boolean,
    val repeatDays: Set<DayOfWeek>
)

data class AlarmCreationState(
    val time: LocalTime?,
    val label: String,
    val repeatDays: Set<DayOfWeek>
)

data class UpcomingAlarm(
    val alarm: AlarmUiModel,
    val triggerAt: LocalDateTime,
    val remaining: Duration
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmScreen(modifier: Modifier = Modifier) {
    var alarms by remember { mutableStateOf(sampleAlarms()) }
    var draft by remember { mutableStateOf(sampleDraft()) }

    val upcomingAlarm = remember(alarms) { resolveNextAlarm(alarms) }
    val (activeAlarms, inactiveAlarms) = remember(alarms) {
        val active = alarms.filter { it.isActive }
        val inactive = alarms.filterNot { it.isActive }
        active to inactive
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = "Alarms") },
                actions = {
                    AssistChip(
                        onClick = {
                            draft = draft.copy(
                                time = LocalTime.now().plusMinutes(15).withSecond(0).withNano(0),
                                repeatDays = dayOrder.take(5).toSet(),
                                label = "Weekday wake"
                            )
                        },
                        leadingIcon = {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                        },
                        label = { Text("Quick add") }
                    )
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                UpcomingAlarmCard(upcomingAlarm)
            }

            item {
                AlarmCreationCard(
                    draft = draft,
                    onUpdateDraft = { draft = it },
                    onCreate = {
                        val time = draft.time
                        val repeatDays = draft.repeatDays
                        if (time != null && repeatDays.isNotEmpty()) {
                            val nextId = (alarms.maxOfOrNull { alarm -> alarm.id } ?: 0) + 1
                            val newAlarm = AlarmUiModel(
                                id = nextId,
                                time = time,
                                label = draft.label.ifBlank { "New alarm" },
                                isActive = true,
                                repeatDays = repeatDays
                            )
                            alarms = (alarms + newAlarm).sortedWith(alarmSorter)
                            draft = sampleDraft()
                        }
                    },
                    onNudgeTime = {
                        val baseTime = draft.time ?: LocalTime.now().withSecond(0).withNano(0)
                        val nudged = baseTime.plusMinutes(15).withSecond(0).withNano(0)
                        draft = draft.copy(time = nudged)
                    }
                )
            }

            if (alarms.isEmpty()) {
                item {
                    EmptyState()
                }
            } else {
                if (activeAlarms.isNotEmpty()) {
                    item { SectionHeader(title = "Active") }
                    items(activeAlarms, key = { it.id }) { alarm ->
                        AlarmRow(
                            alarm = alarm,
                            onToggle = { isChecked ->
                                alarms = alarms.map {
                                    if (it.id == alarm.id) it.copy(isActive = isChecked) else it
                                }.sortedWith(alarmSorter)
                            },
                            onDelete = {
                                alarms = alarms.filterNot { it.id == alarm.id }
                            }
                        )
                    }
                }

                if (inactiveAlarms.isNotEmpty()) {
                    item { SectionHeader(title = "Inactive") }
                    items(inactiveAlarms, key = { it.id }) { alarm ->
                        AlarmRow(
                            alarm = alarm,
                            onToggle = { isChecked ->
                                alarms = alarms.map {
                                    if (it.id == alarm.id) it.copy(isActive = isChecked) else it
                                }.sortedWith(alarmSorter)
                            },
                            onDelete = {
                                alarms = alarms.filterNot { it.id == alarm.id }
                            }
                        )
                    }
                }
            }
        }
    }
}

private val alarmSorter = compareByDescending<AlarmUiModel> { it.isActive }.thenBy { it.time }

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun UpcomingAlarmCard(upcomingAlarm: UpcomingAlarm?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Next alarm",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (upcomingAlarm == null) {
                Text(
                    text = "No alarms scheduled",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            } else {
                Text(
                    text = upcomingAlarm.triggerAt.toLocalTime().format(timeFormatter),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = formatRemaining(upcomingAlarm.remaining),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
                if (upcomingAlarm.alarm.label.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = upcomingAlarm.alarm.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlarmCreationCard(
    draft: AlarmCreationState,
    onUpdateDraft: (AlarmCreationState) -> Unit,
    onCreate: () -> Unit,
    onNudgeTime: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(text = "Create alarm", style = MaterialTheme.typography.titleMedium)

            TextButton(onClick = onNudgeTime) {
                Text(text = draft.time?.format(timeFormatter) ?: "Pick a time")
            }

            TextField(
                value = draft.label,
                onValueChange = { onUpdateDraft(draft.copy(label = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Label (optional)") }
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Active on", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    dayOrder.forEach { day ->
                        val selected = draft.repeatDays.contains(day)
                        FilterChip(
                            selected = selected,
                            onClick = {
                                onUpdateDraft(
                                    if (selected) {
                                        draft.copy(repeatDays = draft.repeatDays - day)
                                    } else {
                                        draft.copy(repeatDays = draft.repeatDays + day)
                                    }
                                )
                            },
                            label = { Text(day.displayName()) }
                        )
                    }
                }
            }

            Button(
                onClick = onCreate,
                enabled = draft.time != null && draft.repeatDays.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Add alarm")
            }
        }
    }
}

@Composable
private fun AlarmRow(
    alarm: AlarmUiModel,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = alarm.time.format(timeFormatter),
                    style = MaterialTheme.typography.headlineMedium
                )
                if (alarm.label.isNotBlank()) {
                    Text(
                        text = alarm.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = formatDays(alarm.repeatDays),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Switch(checked = alarm.isActive, onCheckedChange = onToggle)
                IconButton(onClick = onDelete) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Delete alarm")
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 2.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "No alarms yet",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Set your first alarm to get started.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun sampleAlarms(): List<AlarmUiModel> {
    return listOf(
        AlarmUiModel(
            id = 1,
            time = LocalTime.of(7, 0),
            label = "Morning run",
            isActive = true,
            repeatDays = dayOrder.take(5).toSet()
        ),
        AlarmUiModel(
            id = 2,
            time = LocalTime.of(8, 30),
            label = "Team standup",
            isActive = true,
            repeatDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)
        ),
        AlarmUiModel(
            id = 3,
            time = LocalTime.of(22, 0),
            label = "Wind down",
            isActive = false,
            repeatDays = setOf(DayOfWeek.SUNDAY)
        )
    ).sortedWith(alarmSorter)
}

private fun sampleDraft(): AlarmCreationState = AlarmCreationState(
    time = LocalTime.of(6, 30),
    label = "Gym",
    repeatDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
)

private fun resolveNextAlarm(alarms: List<AlarmUiModel>): UpcomingAlarm? {
    val now = LocalDateTime.now()
    return alarms
        .filter { it.isActive }
        .mapNotNull { alarm ->
            nextTriggerFrom(alarm, now)?.let { trigger ->
                UpcomingAlarm(
                    alarm = alarm,
                    triggerAt = trigger,
                    remaining = Duration.between(now, trigger)
                )
            }
        }
        .minByOrNull { it.remaining.toMinutes().coerceAtLeast(0) }
}

private fun nextTriggerFrom(alarm: AlarmUiModel, reference: LocalDateTime): LocalDateTime? {
    val days = if (alarm.repeatDays.isEmpty()) setOf(reference.dayOfWeek) else alarm.repeatDays
    val today = reference.toLocalDate()
    val nowTime = reference.toLocalTime()

    var soonest: LocalDateTime? = null

    for (day in days) {
        val dayDifference = ((day.value - reference.dayOfWeek.value) + 7) % 7
        var date = today.plusDays(dayDifference.toLong())
        var candidate = LocalDateTime.of(date, alarm.time)

        if (dayDifference == 0 && alarm.time <= nowTime) {
            candidate = candidate.plusDays(7)
        }

        if (soonest == null || candidate.isBefore(soonest)) {
            soonest = candidate
        }
    }

    return soonest
}

private fun formatRemaining(duration: Duration): String {
    val totalMinutes = duration.toMinutes().coerceAtLeast(0)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    val parts = buildList {
        if (hours > 0) add("${hours}h")
        if (minutes > 0 || hours == 0L) add("${minutes}m")
    }
    return "In ${parts.joinToString(" ")}" + if (duration.isNegative) " (passed)" else ""
}

private fun formatDays(days: Set<DayOfWeek>): String {
    if (days.isEmpty()) {
        return "Once"
    }
    return if (days.size == 7) {
        "Every day"
    } else if (days == dayOrder.take(5).toSet()) {
        "Weekdays"
    } else if (days == dayOrder.takeLast(2).toSet()) {
        "Weekends"
    } else {
        days.sortedBy { dayOrder.indexOf(it) }
            .joinToString(separator = ", ") { it.displayName() }
    }
}

private fun DayOfWeek.displayName(): String =
    getDisplayName(TextStyle.SHORT, Locale.getDefault()).replaceFirstChar { it.titlecase(Locale.getDefault()) }

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun AlarmScreenPreview() {
    ViewIdeasTheme {
        AlarmScreen()
    }
}
