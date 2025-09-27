package hu.bbara.viewideas.ui.alarm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun AlarmCreateRoute(
    draft: AlarmCreationState,
    isEditing: Boolean,
    onUpdateDraft: (AlarmCreationState) -> Unit,
    onSelectPreset: (LocalTime) -> Unit,
    onTimeSelected: (LocalTime) -> Unit,
    onToggleDay: (DayOfWeek) -> Unit,
    onReset: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val canSave = draft.time != null && draft.repeatDays.isNotEmpty()
    val presetTimes = remember {
        listOf(LocalTime.of(6, 30), LocalTime.of(7, 0), LocalTime.of(8, 30), LocalTime.of(21, 30))
    }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    val timePickerState = rememberTimePickerState(is24Hour = true)

    LaunchedEffect(draft.time) {
        val base = draft.time ?: LocalTime.now().withSecond(0).withNano(0)
        timePickerState.hour = base.hour
        timePickerState.minute = base.minute
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text(text = if (isEditing) "Edit alarm" else "New alarm") },
                actions = {
                    TextButton(onClick = onReset) {
                        Text(text = if (isEditing) "Revert" else "Reset")
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = onSave,
                    enabled = canSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Text(text = if (isEditing) "Update alarm" else "Save alarm")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "Time", style = MaterialTheme.typography.titleMedium)
                Button(onClick = { showTimePicker = true }) {
                    Text(text = draft.time?.format(timeFormatter) ?: "Pick a time")
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    presetTimes.forEach { preset ->
                        TextButton(onClick = {
                            onSelectPreset(preset)
                            showTimePicker = false
                        }) {
                            Text(text = preset.format(timeFormatter))
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "Label", style = MaterialTheme.typography.titleMedium)
                TextField(
                    value = draft.label,
                    onValueChange = { onUpdateDraft(draft.copy(label = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(text = "Alarm label") }
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "Active on", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    dayOrder.forEach { day ->
                        val selected = draft.repeatDays.contains(day)
                        TextButton(onClick = { onToggleDay(day) }) {
                            Text(
                                text = day.displayName(),
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                Text(
                    text = when {
                        draft.repeatDays.isEmpty() -> "Occurs once"
                        draft.repeatDays.size == 7 -> "Repeats every day"
                        else -> "Repeats on ${formatDays(draft.repeatDays)}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }

    if (showTimePicker) {
        BasicAlertDialog(onDismissRequest = { showTimePicker = false }) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    TimePicker(state = timePickerState)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showTimePicker = false }) {
                            Text(text = "Cancel")
                        }
                        TextButton(
                            onClick = {
                                onTimeSelected(LocalTime.of(timePickerState.hour, timePickerState.minute))
                                showTimePicker = false
                            }
                        ) {
                            Text(text = "Set time")
                        }
                    }
                }
            }
        }
    }
}
