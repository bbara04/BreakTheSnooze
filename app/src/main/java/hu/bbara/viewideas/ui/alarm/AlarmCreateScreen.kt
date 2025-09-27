package hu.bbara.viewideas.ui.alarm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun AlarmCreateRoute(
    draft: AlarmCreationState,
    onUpdateDraft: (AlarmCreationState) -> Unit,
    onNudgeTime: () -> Unit,
    onSelectPreset: (LocalTime) -> Unit,
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

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text(text = "New alarm") },
                actions = {
                    TextButton(onClick = onReset) {
                        Text(text = "Reset")
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
                    Text(text = "Save alarm")
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
                Button(onClick = onNudgeTime) {
                    Text(text = draft.time?.format(timeFormatter) ?: "Pick a time")
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    presetTimes.forEach { preset ->
                        TextButton(onClick = { onSelectPreset(preset) }) {
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
}
