package hu.bbara.breakthesnooze.ui.alarm

import android.text.format.DateFormat
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import hu.bbara.breakthesnooze.R
import hu.bbara.breakthesnooze.data.alarm.calculateNextTrigger
import hu.bbara.breakthesnooze.ui.alarm.dismiss.AlarmDismissTaskType
import hu.bbara.breakthesnooze.ui.theme.BreakTheSnoozeTheme
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

private const val NEXT_DAY_WARNING_GAP_HOURS = 4L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AlarmListRoute(
    durationAlarms: List<DurationAlarmUiModel>,
    onCancelDurationAlarm: (Int) -> Unit,
    alarms: List<AlarmUiModel>,
    onToggle: (id: Int, isActive: Boolean) -> Unit,
    onEdit: (id: Int) -> Unit,
    selectedIds: Set<Int>,
    onEnterSelection: (Int) -> Unit,
    onToggleSelection: (Int) -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelection: () -> Unit,
    onCreate: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val is24Hour = remember(context) { DateFormat.is24HourFormat(context) }
    val upcomingAlarm = remember(alarms, durationAlarms) { resolveNextAlarm(alarms, durationAlarms) }
    val (activeAlarms, inactiveAlarms) = remember(alarms) {
        val active = alarms.filter { it.isActive }
        val inactive = alarms.filterNot { it.isActive }
        active to inactive
    }
    val orderedAlarms = remember(activeAlarms, inactiveAlarms) { activeAlarms + inactiveAlarms }
    val hasAnyAlarms = durationAlarms.isNotEmpty() || orderedAlarms.isNotEmpty()

    val selectionActive = selectedIds.isNotEmpty()
    val listState = rememberLazyListState()
    LaunchedEffect(listState.isScrollInProgress) {
        Log.d("AlarmListRoute", "ScrollCoroutine is active: ${listState.isScrollInProgress}")
        if (!listState.isScrollInProgress && listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset > 0) {
            val halfOfFirstVisibleItem =
                listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size?.div(
                    2
                ) ?: 0
            if (listState.firstVisibleItemScrollOffset <= halfOfFirstVisibleItem) {
                listState.animateScrollToItem(0, 0)
            } else {
                listState.animateScrollToItem(1, 0)
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            if (selectionActive) {
                SelectionTopBar(
                    count = selectedIds.size,
                    onClearSelection = onClearSelection,
                    onDeleteSelection = onDeleteSelection
                )
            } else {
                CenterAlignedTopAppBar(
                    title = { Text(text = "Alarms") },
                    navigationIcon = {
                        IconButton(onClick = onOpenSettings) {
                            Icon(imageVector = Icons.Default.Settings, contentDescription = "Open settings")
                        }
                    },
                    actions = {
                        IconButton(onClick = onCreate) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "Create alarm")
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                UpcomingAlarmCard(
                    upcomingAlarm = upcomingAlarm,
                    is24Hour = is24Hour,
                    alarms = alarms,
                    durationAlarms = durationAlarms,
                    onDisableAlarm = { onToggle(it, false) },
                    onCancelDurationAlarm = onCancelDurationAlarm
                )
            }

            if (!hasAnyAlarms) {
                item { EmptyState() }
            }

            if (durationAlarms.isNotEmpty()) {
                item { DurationAlarmSectionHeader() }
                items(durationAlarms, key = { "duration_${it.id}" }) { alarm ->
                    DurationAlarmRow(
                        alarm = alarm,
                        is24Hour = is24Hour,
                        onCancel = { onCancelDurationAlarm(alarm.id) }
                    )
                }
            }

            if (activeAlarms.isNotEmpty()) {
                item { AlarmSectionHeader(text = stringResource(id = R.string.alarm_section_enabled)) }
                items(activeAlarms, key = { it.id }) { alarm ->
                    AlarmRow(
                        alarm = alarm,
                        onToggle = { onToggle(alarm.id, it) },
                        onEdit = { onEdit(alarm.id) },
                        onEnterSelection = { onEnterSelection(alarm.id) },
                        onToggleSelection = { onToggleSelection(alarm.id) },
                        is24Hour = is24Hour,
                        selectionActive = selectionActive,
                        isSelected = selectedIds.contains(alarm.id)
                    )
                }
            }

            if (inactiveAlarms.isNotEmpty()) {
                item { AlarmSectionHeader(text = stringResource(id = R.string.alarm_section_disabled)) }
                items(inactiveAlarms, key = { it.id }) { alarm ->
                    AlarmRow(
                        alarm = alarm,
                        onToggle = { onToggle(alarm.id, it) },
                        onEdit = { onEdit(alarm.id) },
                        onEnterSelection = { onEnterSelection(alarm.id) },
                        onToggleSelection = { onToggleSelection(alarm.id) },
                        is24Hour = is24Hour,
                        selectionActive = selectionActive,
                        isSelected = selectedIds.contains(alarm.id)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun AlarmListRoutePreview() {
    BreakTheSnoozeTheme {
        AlarmListRoute(
            durationAlarms = emptyList(),
            onCancelDurationAlarm = {},
            alarms = sampleAlarms(),
            onToggle = { _, _ -> },
            onEdit = {},
            selectedIds = emptySet(),
            onEnterSelection = {},
            onToggleSelection = {},
            onClearSelection = {},
            onDeleteSelection = {},
            onCreate = {},
            onOpenSettings = {},
            modifier = Modifier.fillMaxSize()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    count: Int,
    onClearSelection: () -> Unit,
    onDeleteSelection: () -> Unit
) {
    CenterAlignedTopAppBar(
        navigationIcon = {
            IconButton(onClick = onClearSelection) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Clear selection")
            }
        },
        title = {
            Text(text = "$count selected")
        },
        actions = {
            IconButton(onClick = onDeleteSelection) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete selected")
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun SelectionTopBarPreview() {
    BreakTheSnoozeTheme {
        SelectionTopBar(
            count = 3,
            onClearSelection = {},
            onDeleteSelection = {}
        )
    }
}

@Composable
private fun DurationAlarmSectionHeader() {
    Text(
        text = stringResource(id = R.string.duration_alarm_section_title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun AlarmSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DurationAlarmRow(
    alarm: DurationAlarmUiModel,
    is24Hour: Boolean,
    onCancel: () -> Unit
) {
    val triggerTime = remember(alarm.triggerAt, is24Hour) {
        val time = alarm.triggerAt.atZone(ZoneId.systemDefault()).toLocalTime()
        time.formatForDisplay(is24Hour)
    }
    var remaining by remember(alarm.triggerAt) {
        mutableStateOf(Duration.between(Instant.now(), alarm.triggerAt))
    }
    LaunchedEffect(alarm.triggerAt) {
        while (true) {
            remaining = Duration.between(Instant.now(), alarm.triggerAt)
            if (remaining.isNegative) break
            delay(15_000)
        }
    }
    val containerColor = MaterialTheme.colorScheme.surfaceTint.copy(alpha = 0.12f)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { clip = true },
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = triggerTime,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val displayLabel = alarm.label.ifBlank { stringResource(id = R.string.alarm_label_default) }
                Text(
                    text = displayLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatRemaining(remaining),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            FilledIconButton(onClick = onCancel) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(id = R.string.duration_alarm_cancel)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DurationAlarmRowPreview() {
    BreakTheSnoozeTheme {
        DurationAlarmRow(
            alarm = DurationAlarmUiModel(
                id = 1,
                label = "Power nap",
                triggerAt = Instant.now().plus(Duration.ofMinutes(45)),
                duration = Duration.ofMinutes(45),
                soundUri = null,
                dismissTask = AlarmDismissTaskType.MATH_CHALLENGE,
                qrBarcodeValue = null,
                qrRequiredUniqueCount = 0
            ),
            is24Hour = true,
            onCancel = {}
        )
    }
}

@Composable
private fun UpcomingAlarmCard(
    upcomingAlarm: UpcomingAlarm?,
    is24Hour: Boolean,
    alarms: List<AlarmUiModel>,
    durationAlarms: List<DurationAlarmUiModel>,
    onDisableAlarm: (Int) -> Unit,
    onCancelDurationAlarm: (Int) -> Unit
) {
    val tightNextDayAlarms = findTightNextDayAlarms(alarms, durationAlarms)
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
                    text = upcomingAlarm.triggerAt.toLocalTime().formatForDisplay(is24Hour),
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
            if (tightNextDayAlarms != null) {
                val (firstNextDay, secondNextDay) = tightNextDayAlarms
                Spacer(modifier = Modifier.height(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Warning: two alarms within ${NEXT_DAY_WARNING_GAP_HOURS} hours today or tomorrow.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Scheduled at ${firstNextDay.triggerAt.toLocalTime().formatForDisplay(is24Hour)} and ${secondNextDay.triggerAt.toLocalTime().formatForDisplay(is24Hour)}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                    Button(
                        onClick = {
                            when (secondNextDay) {
                                is UpcomingScheduledAlarm.Standard -> onDisableAlarm(secondNextDay.alarm.id)
                                is UpcomingScheduledAlarm.Duration -> onCancelDurationAlarm(secondNextDay.alarm.id)
                            }
                        }
                    ) {
                        Text(text = "Disable second alarm")
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun UpcomingAlarmCardPreview() {
    val sampleAlarm = sampleAlarms().firstOrNull()
    BreakTheSnoozeTheme {
        UpcomingAlarmCard(
            upcomingAlarm = sampleAlarm?.let {
                UpcomingAlarm(
                    alarm = it,
                    triggerAt = LocalDateTime.now().plusHours(1),
                    remaining = Duration.ofHours(1)
                )
            },
            is24Hour = true,
            alarms = sampleAlarms(),
            durationAlarms = emptyList(),
            onDisableAlarm = {},
            onCancelDurationAlarm = {}
        )
    }
}

private sealed class UpcomingScheduledAlarm {
    abstract val triggerAt: LocalDateTime

    data class Standard(val alarm: AlarmUiModel, override val triggerAt: LocalDateTime) : UpcomingScheduledAlarm()
    data class Duration(val alarm: DurationAlarmUiModel, override val triggerAt: LocalDateTime) : UpcomingScheduledAlarm()
}

private fun findTightNextDayAlarms(
    alarms: List<AlarmUiModel>,
    durationAlarms: List<DurationAlarmUiModel>,
    zoneId: ZoneId = ZoneId.systemDefault(),
    gapThresholdHours: Long = NEXT_DAY_WARNING_GAP_HOURS,
    reference: LocalDateTime = LocalDateTime.now(zoneId)
): Pair<UpcomingScheduledAlarm, UpcomingScheduledAlarm>? {
    val today = reference.toLocalDate()
    val tomorrow = today.plusDays(1)
    val targetDates = listOf(today, tomorrow)
    val nextDayAlarms = buildList {
        alarms
            .asSequence()
            .filter { it.isActive }
            .flatMap { alarm ->
                if (alarm.repeatDays.isEmpty()) {
                    val trigger = calculateNextTrigger(alarm, reference)
                    if (trigger != null && trigger.toLocalDate() in targetDates) sequenceOf(
                        UpcomingScheduledAlarm.Standard(alarm, trigger)
                    ) else emptySequence()
                } else {
                    targetDates.asSequence()
                        .filter { date -> alarm.repeatDays.contains(date.dayOfWeek) }
                        .map { date -> LocalDateTime.of(date, alarm.time) }
                        .filter { candidate -> candidate.isAfter(reference) }
                        .map { candidate -> UpcomingScheduledAlarm.Standard(alarm, candidate) }
                }
            }
            .forEach { add(it) }

        durationAlarms
            .asSequence()
            .map { durationAlarm ->
                val trigger = LocalDateTime.ofInstant(durationAlarm.triggerAt, zoneId)
                UpcomingScheduledAlarm.Duration(durationAlarm, trigger)
            }
            .filter { it.triggerAt.toLocalDate() in targetDates && it.triggerAt.isAfter(reference) }
            .forEach { add(it) }
    }.sortedBy { it.triggerAt }

    if (nextDayAlarms.size < 2) return null

    val maxGap = Duration.ofHours(gapThresholdHours)
    nextDayAlarms.windowed(size = 2, step = 1, partialWindows = false).forEach { pair ->
        val first = pair[0]
        val second = pair[1]
        val gap = Duration.between(first.triggerAt, second.triggerAt)
        if (!gap.isNegative && gap <= maxGap) {
            return first to second
        }
    }
    return null
}

private const val TAG_ALARM_ROW = "AlarmRow"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun AlarmRow(
    alarm: AlarmUiModel,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onEnterSelection: () -> Unit,
    onToggleSelection: () -> Unit,
    is24Hour: Boolean,
    selectionActive: Boolean,
    isSelected: Boolean
) {
    val containerColor = when {
        isSelected -> MaterialTheme.colorScheme.secondaryContainer
        alarm.isActive -> MaterialTheme.colorScheme.surfaceTint.copy(alpha = 0.12f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val primaryTextColor = if (alarm.isActive) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val secondaryTextColor = if (alarm.isActive) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    }
    val accentColor = if (alarm.isActive) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { clip = true }
            .then(
                if (isSelected) Modifier.border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = MaterialTheme.shapes.medium
                ) else Modifier
            )
            .combinedClickable(
                onClick = {
                    Log.d(TAG_ALARM_ROW, "click id=${alarm.id} selection=$selectionActive")
                    if (selectionActive) {
                        onToggleSelection()
                    } else {
                        Log.d(TAG_ALARM_ROW, "edit dispatched id=${alarm.id}")
                        onEdit()
                    }
                },
                onLongClick = {
                    Log.d(TAG_ALARM_ROW, "longClick id=${alarm.id} selection=$selectionActive")
                    if (!selectionActive) {
                        onEnterSelection()
                    } else {
                        onToggleSelection()
                    }
                }
            ),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = alarm.time.formatForDisplay(is24Hour),
                    style = MaterialTheme.typography.headlineMedium,
                    color = primaryTextColor
                )
                if (alarm.label.isNotBlank()) {
                    Text(
                        text = alarm.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryTextColor
                    )
                }
                Text(
                    text = formatDays(alarm.repeatDays),
                    style = MaterialTheme.typography.labelLarge,
                    color = accentColor
                )
            }
            Switch(
                checked = alarm.isActive,
                onCheckedChange = {
                    Log.d(TAG_ALARM_ROW, "switch id=${alarm.id} -> $it")
                    onToggle(it)
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AlarmRowPreview() {
    BreakTheSnoozeTheme {
        AlarmRow(
            alarm = sampleAlarms().first(),
            onToggle = {},
            onEdit = {},
            onEnterSelection = {},
            onToggleSelection = {},
            is24Hour = true,
            selectionActive = false,
            isSelected = false
        )
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
                text = "Tap the add button to schedule your first alarm.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EmptyStatePreview() {
    BreakTheSnoozeTheme {
        EmptyState()
    }
}
