package hu.bbara.viewideas.ui.alarm

import android.app.Activity
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import hu.bbara.viewideas.R
import hu.bbara.viewideas.ui.alarm.dismiss.AlarmDismissTaskType
import java.time.DayOfWeek
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AlarmCreateRoute(
    draft: AlarmCreationState,
    isEditing: Boolean,
    onUpdateDraft: (AlarmCreationState) -> Unit,
    onTimeSelected: (LocalTime) -> Unit,
    onToggleDay: (DayOfWeek) -> Unit,
    onSoundSelected: (String?) -> Unit,
    onDismissTaskSelected: (AlarmDismissTaskType) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val is24Hour = remember(context) { DateFormat.is24HourFormat(context) }
    val scrollState = rememberScrollState()
    val canSave = draft.time != null
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    val timePickerState = rememberTimePickerState(is24Hour = is24Hour)
    val soundName = remember(draft.soundUri) { resolveRingtoneTitle(context, draft.soundUri) }
    var showTaskDialog by rememberSaveable { mutableStateOf(false) }
    val soundPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val uri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            onSoundSelected(uri?.toString())
        }
    }

    LaunchedEffect(draft.time) {
        val base = draft.time ?: LocalTime.now().plusMinutes(1).withSecond(0).withNano(0)
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
                    TextButton(onClick = onSave, enabled = canSave) {
                        Text(text = if (isEditing) "Update" else "Save")
                    }
                }
            )
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
                Surface(
                    onClick = { showTimePicker = true },
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    tonalElevation = 6.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = draft.time?.formatForDisplay(is24Hour) ?: "Pick a time",
                            style = MaterialTheme.typography.displayMedium
                        )
                        Text(
                            text = if (draft.time != null) "Tap to adjust" else "Tap to choose",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
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
                Text(text = "Sound", style = MaterialTheme.typography.titleMedium)
                Surface(
                    onClick = {
                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                            val existing = draft.soundUri?.let { Uri.parse(it) }
                            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing)
                        }
                        soundPickerLauncher.launch(intent)
                    },
                    shape = MaterialTheme.shapes.extraLarge,
                    tonalElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = soundName ?: "Default alarm",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Tap to choose a ringtone",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (draft.soundUri != null) {
                    TextButton(onClick = { onSoundSelected(null) }) {
                        Text(text = "Use default")
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(id = R.string.alarm_task_section_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Surface(
                    onClick = { showTaskDialog = true },
                    shape = MaterialTheme.shapes.extraLarge,
                    tonalElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = taskIconFor(draft.dismissTask),
                            contentDescription = null
                        )
                        Text(
                            text = stringResource(id = draft.dismissTask.optionLabelResId),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }

            if (showTaskDialog) {
                BasicAlertDialog(onDismissRequest = { showTaskDialog = false }) {
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        tonalElevation = 6.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = stringResource(id = R.string.alarm_task_section_title),
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            AlarmDismissTaskType.values().toList().chunked(2).forEach { rowTasks ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    rowTasks.forEach { option ->
                                        val selected = option == draft.dismissTask
                                        Surface(
                                            onClick = {
                                                onDismissTaskSelected(option)
                                                showTaskDialog = false
                                            },
                                            shape = MaterialTheme.shapes.large,
                                            tonalElevation = if (selected) 6.dp else 2.dp,
                                            color = if (selected) {
                                                MaterialTheme.colorScheme.primaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant
                                            },
                                            contentColor = if (selected) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = taskIconFor(option),
                                                        contentDescription = null
                                                    )
                                                    Text(
                                                        text = stringResource(id = option.optionLabelResId),
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        textAlign = TextAlign.Center
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    if (rowTasks.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "Active on", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    dayOrder.forEach { day ->
                        val selected = draft.repeatDays.contains(day)
                        TextButton(
                            onClick = { onToggleDay(day) },
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minWidth = 0.dp),
                            shape = MaterialTheme.shapes.large,
                            contentPadding = PaddingValues(vertical = 8.dp),
                            colors = if (selected) {
                                ButtonDefaults.textButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        ) {
                            Text(text = day.displayName())
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
                    horizontalAlignment = Alignment.CenterHorizontally,
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

private fun resolveRingtoneTitle(context: android.content.Context, soundUri: String?): String? {
    if (soundUri.isNullOrBlank()) return null
    return runCatching {
        val uri = Uri.parse(soundUri)
        val ringtone: Ringtone? = RingtoneManager.getRingtone(context, uri)
        ringtone?.getTitle(context)
    }.getOrNull()
}

private fun taskIconFor(type: AlarmDismissTaskType): ImageVector = when (type) {
    AlarmDismissTaskType.OBJECT_DETECTION -> Icons.Filled.PhotoCamera
    AlarmDismissTaskType.MATH_CHALLENGE -> Icons.Filled.Calculate
    AlarmDismissTaskType.FOCUS_TIMER -> Icons.Filled.Timer
    AlarmDismissTaskType.QR_BARCODE_SCAN -> Icons.Filled.QrCode2
}
