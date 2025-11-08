package hu.bbara.breakthesnooze.ui.alarm

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import hu.bbara.breakthesnooze.R
import hu.bbara.breakthesnooze.ui.alarm.dismiss.AlarmDismissTaskType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DurationAlarmRoute(
    draft: DurationAlarmCreationState,
    onLabelChange: (String) -> Unit,
    onHoursChange: (Int) -> Unit,
    onMinutesChange: (Int) -> Unit,
    onSoundSelected: (String?) -> Unit,
    onDismissTaskSelected: (AlarmDismissTaskType) -> Unit,
    onQrBarcodeValueChange: (String?) -> Unit,
    onQrScanModeChange: (QrScanMode) -> Unit,
    onQrUniqueCountChange: (Int) -> Unit,
    onCreate: () -> Unit,
    isSaving: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val canSave = draft.totalMinutes > 0 && !isSaving
    var showTaskDialog by rememberSaveable { mutableStateOf(false) }
    var showQrScanner by rememberSaveable { mutableStateOf(false) }
    val qrMode = if (!draft.qrBarcodeValue.isNullOrBlank()) {
        QrScanMode.SpecificCode
    } else if (draft.qrRequiredUniqueCount >= MIN_QR_UNIQUE_COUNT) {
        QrScanMode.UniqueCodes
    } else {
        QrScanMode.SpecificCode
    }
    val soundPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
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
    )
    val soundName = remember(draft.soundUri) { resolveRingtoneTitle(context, draft.soundUri) }

    BackHandler(enabled = true, onBack = onBack)

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                title = { Text(text = stringResource(id = R.string.duration_alarm_title)) },
                actions = {
                    TextButton(onClick = onCreate, enabled = canSave) {
                        Text(text = stringResource(id = R.string.duration_alarm_start_button))
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
            DurationPicker(
                hours = draft.hours,
                minutes = draft.minutes,
                onHoursChange = onHoursChange,
                onMinutesChange = onMinutesChange
            )

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = stringResource(id = R.string.duration_alarm_label_title), style = MaterialTheme.typography.titleMedium)
                TextField(
                    value = draft.label,
                    onValueChange = onLabelChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(text = stringResource(id = R.string.duration_alarm_label_hint)) }
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = stringResource(id = R.string.duration_alarm_sound_title), style = MaterialTheme.typography.titleMedium)
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
                            text = soundName ?: stringResource(id = R.string.duration_alarm_sound_default),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = stringResource(id = R.string.duration_alarm_sound_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (draft.soundUri != null) {
                    TextButton(onClick = { onSoundSelected(null) }) {
                        Text(text = stringResource(id = R.string.duration_alarm_sound_reset))
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = stringResource(id = R.string.duration_alarm_task_title), style = MaterialTheme.typography.titleMedium)
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
                        Icon(imageVector = taskIconFor(draft.dismissTask), contentDescription = null)
                        Text(
                            text = stringResource(id = draft.dismissTask.optionLabelResId),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }

            if (draft.dismissTask == AlarmDismissTaskType.QR_BARCODE_SCAN) {
                QrBarcodeSettings(
                    qrBarcodeValue = draft.qrBarcodeValue,
                    qrRequiredUniqueCount = draft.qrRequiredUniqueCount,
                    qrMode = qrMode,
                    onQrScanModeChange = onQrScanModeChange,
                    onQrBarcodeValueChange = onQrBarcodeValueChange,
                    onQrUniqueCountChange = onQrUniqueCountChange,
                    onShowQrScanner = { showQrScanner = true }
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
                        text = stringResource(id = R.string.duration_alarm_task_title),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    AlarmDismissTaskType.values().toList().chunked(2).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            row.forEach { task ->
                                TaskOptionCard(
                                    label = stringResource(id = task.optionLabelResId),
                                    icon = taskIconFor(task),
                                    selected = task == draft.dismissTask,
                                    onClick = {
                                        onDismissTaskSelected(task)
                                        if (task != AlarmDismissTaskType.QR_BARCODE_SCAN) {
                                            onQrBarcodeValueChange(null)
                                        }
                                        showTaskDialog = false
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (row.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showTaskDialog = false }) {
                            Text(text = stringResource(id = R.string.duration_alarm_dialog_done))
                        }
                    }
                }
            }
        }
    }

    if (showQrScanner) {
        AssignQrBarcodeScreen(
            onDismiss = { showQrScanner = false },
            onBarcodeCaptured = {
                onQrBarcodeValueChange(it)
                showQrScanner = false
            }
        )
    }
}

@Composable
private fun DurationPicker(
    hours: Int,
    minutes: Int,
    onHoursChange: (Int) -> Unit,
    onMinutesChange: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(id = R.string.duration_alarm_duration_title),
            style = MaterialTheme.typography.titleMedium
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DurationValueCard(
                label = stringResource(id = R.string.duration_alarm_hours_label),
                value = hours,
                onIncrement = { onHoursChange((hours + 1).coerceAtMost(99)) },
                onDecrement = { onHoursChange((hours - 1).coerceAtLeast(0)) },
                modifier = Modifier.weight(1f)
            )
            DurationValueCard(
                label = stringResource(id = R.string.duration_alarm_minutes_label),
                value = minutes.coerceIn(0, 59),
                onIncrement = { onMinutesChange((minutes + 1).coerceAtMost(59)) },
                onDecrement = { onMinutesChange((minutes - 1).coerceAtLeast(0)) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun DurationValueCard(
    label: String,
    value: Int,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(text = value.toString().padStart(2, '0'), style = MaterialTheme.typography.displaySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton(onClick = onDecrement) {
                    Icon(imageVector = Icons.Filled.Remove, contentDescription = null)
                }
                IconButton(onClick = onIncrement) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun TaskOptionCard(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        tonalElevation = if (selected) 6.dp else 2.dp,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 18.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null)
            Text(text = label, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
    }
}
