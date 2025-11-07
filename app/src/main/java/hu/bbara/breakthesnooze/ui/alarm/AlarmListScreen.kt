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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import hu.bbara.breakthesnooze.ui.theme.BreakTheSnoozeTheme
import java.time.Duration
import java.time.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AlarmListRoute(
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
    val upcomingAlarm = remember(alarms) { resolveNextAlarm(alarms) }
    val (activeAlarms, inactiveAlarms) = remember(alarms) {
        val active = alarms.filter { it.isActive }
        val inactive = alarms.filterNot { it.isActive }
        active to inactive
    }
    val orderedAlarms = remember(activeAlarms, inactiveAlarms) { activeAlarms + inactiveAlarms }

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
                UpcomingAlarmCard(upcomingAlarm, is24Hour)
            }

            if (orderedAlarms.isEmpty()) {
                item { EmptyState() }
            } else {
                items(orderedAlarms, key = { it.id }) { alarm ->
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
private fun UpcomingAlarmCard(upcomingAlarm: UpcomingAlarm?, is24Hour: Boolean) {
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
            is24Hour = true
        )
    }
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
