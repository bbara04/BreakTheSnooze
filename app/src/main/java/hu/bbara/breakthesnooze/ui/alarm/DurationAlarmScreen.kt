package hu.bbara.breakthesnooze.ui.alarm

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.BasicAlertDialog
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
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import hu.bbara.breakthesnooze.R
import hu.bbara.breakthesnooze.ui.alarm.dismiss.AlarmDismissTaskType
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import java.util.Date
import kotlin.math.abs

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
    var isDurationPickerInteracting by remember { mutableStateOf(false) }
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
                .verticalScroll(scrollState, enabled = !isDurationPickerInteracting),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            DurationPicker(
                hours = draft.hours,
                minutes = draft.minutes,
                onHoursChange = onHoursChange,
                onMinutesChange = onMinutesChange,
                onInteractionChange = { isDurationPickerInteracting = it }
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
    onMinutesChange: (Int) -> Unit,
    onInteractionChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val hourValues = remember { (0..99).toList() }
    val minuteValues = remember { (0..59).toList() }
    val clampedHours = hours.coerceIn(hourValues.first(), hourValues.last())
    val clampedMinutes = minutes.coerceIn(minuteValues.first(), minuteValues.last())
    val timeFormatter = remember(context) { DateFormat.getTimeFormat(context) }
    val summaryTime = remember(clampedHours, clampedMinutes, timeFormatter) {
        val totalMinutes = clampedHours * 60L + clampedMinutes
        val futureMillis = System.currentTimeMillis() + totalMinutes * 60_000L
        timeFormatter.format(Date(futureMillis))
    }

    var hoursInteracting by remember { mutableStateOf(false) }
    var minutesInteracting by remember { mutableStateOf(false) }

    LaunchedEffect(hoursInteracting, minutesInteracting) {
        onInteractionChange(hoursInteracting || minutesInteracting)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(id = R.string.duration_alarm_duration_title),
            style = MaterialTheme.typography.titleMedium
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DurationRollerColumn(
                label = stringResource(id = R.string.duration_alarm_hours_label),
                values = hourValues,
                selectedValue = clampedHours,
                onValueSelected = onHoursChange,
                onInteractionChange = { hoursInteracting = it },
                modifier = Modifier.weight(1f)
            )
            DurationRollerColumn(
                label = stringResource(id = R.string.duration_alarm_minutes_label),
                values = minuteValues,
                selectedValue = clampedMinutes,
                onValueSelected = onMinutesChange,
                onInteractionChange = { minutesInteracting = it },
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            text = stringResource(id = R.string.duration_alarm_summary, summaryTime),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DurationRollerColumn(
    label: String,
    values: List<Int>,
    selectedValue: Int,
    onValueSelected: (Int) -> Unit,
    onInteractionChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    if (values.isEmpty()) return
    val itemHeight = 48.dp
    val visibleItems = 5
    val paddingItems = (visibleItems - 1) / 2
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = values.indexOf(selectedValue).coerceIn(0, values.lastIndex)
    )
    val flingBehavior = rememberSnapFlingBehavior(listState)

    LaunchedEffect(selectedValue) {
        val targetIndex = values.indexOf(selectedValue).coerceIn(0, values.lastIndex)
        if (listState.layoutInfo.visibleItemsInfo.isEmpty()) {
            listState.scrollToItem(targetIndex)
        } else {
            val centeredIndex = calculateCenteredIndex(listState)
            if (centeredIndex != targetIndex && !listState.isScrollInProgress) {
                listState.animateScrollToItem(targetIndex)
            }
        }
    }

    val currentSelectedValue by rememberUpdatedState(selectedValue)
    var pointerActive by remember { mutableStateOf(false) }
    var scrollInProgress by remember { mutableStateOf(false) }

    LaunchedEffect(listState) {
        snapshotFlow { calculateCenteredIndex(listState) }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { index ->
                val value = values.getOrNull(index) ?: return@collect
                if (value != currentSelectedValue) {
                    onValueSelected(value)
                }
            }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { inProgress -> scrollInProgress = inProgress }
    }

    LaunchedEffect(pointerActive, scrollInProgress) {
        onInteractionChange(pointerActive || scrollInProgress)
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val interactionModifier = Modifier.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                pointerActive = true
                try {
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.none { it.pressed }) break
                    } while (true)
                } finally {
                    pointerActive = false
                }
            }
        }

        DisposableEffect(Unit) {
            onDispose { pointerActive = false }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight * visibleItems)
                .then(interactionModifier)
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp),
                tonalElevation = 6.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(itemHeight)
            ) {}
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = itemHeight * paddingItems),
                flingBehavior = flingBehavior,
                userScrollEnabled = true
            ) {
                items(count = values.size) { index ->
                    val value = values[index]
                    val isSelected = value == selectedValue
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(itemHeight),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = value.toString().padStart(2, '0'),
                            style = if (isSelected) MaterialTheme.typography.displayMedium else MaterialTheme.typography.headlineMedium,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun calculateCenteredIndex(state: LazyListState): Int? {
    val layoutInfo = state.layoutInfo
    if (layoutInfo.visibleItemsInfo.isEmpty() || layoutInfo.viewportSize.height == 0) return null
    val viewportCenter = layoutInfo.viewportStartOffset + layoutInfo.viewportSize.height / 2
    return layoutInfo.visibleItemsInfo.minByOrNull { item ->
        val itemCenter = item.offset + item.size / 2
        abs(itemCenter - viewportCenter)
    }?.index
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
