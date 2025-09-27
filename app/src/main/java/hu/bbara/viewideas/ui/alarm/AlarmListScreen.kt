package hu.bbara.viewideas.ui.alarm

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

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
        if (!listState.isScrollInProgress && listState.firstVisibleItemIndex == 0) {
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
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .navigationBarsPadding(),
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

@OptIn(ExperimentalMaterial3Api::class)
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
            .combinedClickable(
                onClick = {
                    if (selectionActive) {
                        onToggleSelection()
                    } else {
                        onEdit()
                    }
                },
                onLongClick = {
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
            Switch(checked = alarm.isActive, onCheckedChange = onToggle)
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
                text = "Tap the add button to schedule your first alarm.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
