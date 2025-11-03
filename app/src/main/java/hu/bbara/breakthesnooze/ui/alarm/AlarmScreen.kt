package hu.bbara.breakthesnooze.ui.alarm

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import hu.bbara.breakthesnooze.R
import hu.bbara.breakthesnooze.data.alarm.AlarmRepositoryProvider
import hu.bbara.breakthesnooze.data.alarm.AlarmSchedulerProvider
import hu.bbara.breakthesnooze.data.settings.SettingsRepositoryProvider
import hu.bbara.breakthesnooze.ui.alarm.dismiss.AlarmDismissTaskType
import hu.bbara.breakthesnooze.ui.settings.SettingsRoute
import hu.bbara.breakthesnooze.ui.theme.BreakTheSnoozeTheme
import java.time.DayOfWeek
import java.time.LocalTime

@Composable
fun AlarmScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember(context) { AlarmRepositoryProvider.getRepository(context) }
    val scheduler = remember(context) { AlarmSchedulerProvider.getScheduler(context) }
    val settingsRepository = remember(context) { SettingsRepositoryProvider.getRepository(context) }
    val alarmViewModel: AlarmViewModel = viewModel(
        factory = remember(repository, scheduler, settingsRepository) {
            AlarmViewModelFactory(repository, scheduler, settingsRepository)
        }
    )

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = {}
        )
        LaunchedEffect(Unit) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val uiState by alarmViewModel.uiState.collectAsState()

    BackHandler(
        enabled = uiState.selectedAlarmIds.isNotEmpty() ||
            uiState.destination == AlarmDestination.Create ||
            uiState.destination == AlarmDestination.Settings
    ) {
        when {
            uiState.selectedAlarmIds.isNotEmpty() -> alarmViewModel.clearSelection()
            uiState.destination == AlarmDestination.Create -> alarmViewModel.cancelCreation()
            uiState.destination == AlarmDestination.Settings -> alarmViewModel.closeSettings()
        }
    }

    AlarmScreenContent(
        uiState = uiState,
        onToggle = alarmViewModel::onToggleAlarm,
        onDelete = alarmViewModel::deleteAlarm,
        onEdit = alarmViewModel::startEditing,
        onStartCreate = alarmViewModel::startCreating,
        onUpdateDraft = alarmViewModel::updateDraft,
        onTimeSelected = alarmViewModel::setDraftTime,
        onToggleDay = alarmViewModel::toggleDraftDay,
        onSoundSelected = alarmViewModel::setDraftSound,
        onDismissTaskSelected = alarmViewModel::setDraftDismissTask,
        onQrBarcodeValueChange = alarmViewModel::setDraftQrBarcodeValue,
        onQrScanModeChange = alarmViewModel::setDraftQrScanMode,
        onQrUniqueCountChange = alarmViewModel::setDraftQrUniqueCount,
        onSaveDraft = alarmViewModel::saveDraft,
        onCancel = alarmViewModel::cancelCreation,
        onOpenSettings = alarmViewModel::openSettings,
        onCloseSettings = alarmViewModel::closeSettings,
        onDefaultTaskSelected = alarmViewModel::setDefaultDismissTask,
        onDefaultRingtoneSelected = alarmViewModel::setDefaultRingtone,
        onDebugModeToggled = alarmViewModel::setDebugMode,
        onEnterSelection = alarmViewModel::enterSelection,
        onToggleSelection = alarmViewModel::toggleSelection,
        onClearSelection = alarmViewModel::clearSelection,
        onDeleteSelection = alarmViewModel::deleteSelected,
        onSelectHomeTab = alarmViewModel::selectHomeTab,
        onBreakdownPeriodSelected = alarmViewModel::setBreakdownPeriod,
        modifier = modifier
    )
}

@Composable
private fun AlarmScreenContent(
    uiState: AlarmUiState,
    onToggle: (Int, Boolean) -> Unit,
    onDelete: (Int) -> Unit,
    onEdit: (Int) -> Unit,
    onStartCreate: () -> Unit,
    onUpdateDraft: (AlarmCreationState) -> Unit,
    onTimeSelected: (LocalTime) -> Unit,
    onToggleDay: (DayOfWeek) -> Unit,
    onSoundSelected: (String?) -> Unit,
    onDismissTaskSelected: (AlarmDismissTaskType) -> Unit,
    onQrBarcodeValueChange: (String?) -> Unit,
    onQrScanModeChange: (QrScanMode) -> Unit,
    onQrUniqueCountChange: (Int) -> Unit,
    onSaveDraft: () -> Unit,
    onCancel: () -> Unit,
    onOpenSettings: () -> Unit,
    onCloseSettings: () -> Unit,
    onDefaultTaskSelected: (AlarmDismissTaskType) -> Unit,
    onDefaultRingtoneSelected: (String?) -> Unit,
    onDebugModeToggled: (Boolean) -> Unit,
    onEnterSelection: (Int) -> Unit,
    onToggleSelection: (Int) -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelection: () -> Unit,
    onSelectHomeTab: (AlarmHomeTab) -> Unit,
    onBreakdownPeriodSelected: (BreakdownPeriod) -> Unit,
    modifier: Modifier = Modifier
) {
    when (uiState.destination) {
        AlarmDestination.List -> AlarmHomeRoute(
            uiState = uiState,
            onToggle = onToggle,
            onEdit = onEdit,
            onEnterSelection = onEnterSelection,
            onToggleSelection = onToggleSelection,
            onClearSelection = onClearSelection,
            onDeleteSelection = onDeleteSelection,
            onCreate = onStartCreate,
            onOpenSettings = onOpenSettings,
            onSelectHomeTab = onSelectHomeTab,
            onBreakdownPeriodSelected = onBreakdownPeriodSelected,
            modifier = modifier
        )

        AlarmDestination.Create -> AlarmCreateRoute(
            draft = uiState.draft,
            isEditing = uiState.editingAlarm != null,
            onUpdateDraft = onUpdateDraft,
            onTimeSelected = onTimeSelected,
            onToggleDay = onToggleDay,
            onSoundSelected = onSoundSelected,
            onDismissTaskSelected = onDismissTaskSelected,
            onQrBarcodeValueChange = onQrBarcodeValueChange,
            onQrScanModeChange = onQrScanModeChange,
            onQrUniqueCountChange = onQrUniqueCountChange,
            onSave = onSaveDraft,
            onCancel = onCancel,
            modifier = modifier
        )

        AlarmDestination.Settings -> SettingsRoute(
            settings = uiState.settings,
            onDefaultTaskSelected = onDefaultTaskSelected,
            onDefaultRingtoneSelected = onDefaultRingtoneSelected,
            onDebugModeToggled = onDebugModeToggled,
            onBack = onCloseSettings,
            modifier = modifier
        )
    }
}

@Composable
private fun AlarmHomeRoute(
    uiState: AlarmUiState,
    onToggle: (Int, Boolean) -> Unit,
    onEdit: (Int) -> Unit,
    onEnterSelection: (Int) -> Unit,
    onToggleSelection: (Int) -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelection: () -> Unit,
    onCreate: () -> Unit,
    onOpenSettings: () -> Unit,
    onSelectHomeTab: (AlarmHomeTab) -> Unit,
    onBreakdownPeriodSelected: (BreakdownPeriod) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = uiState.homeTab == AlarmHomeTab.Alarms,
                    onClick = { onSelectHomeTab(AlarmHomeTab.Alarms) },
                    icon = { Icon(imageVector = Icons.Default.Alarm, contentDescription = null) },
                    label = { Text(text = stringResource(id = R.string.alarm_tab_alarms)) }
                )
                NavigationBarItem(
                    selected = uiState.homeTab == AlarmHomeTab.Breakdown,
                    onClick = { onSelectHomeTab(AlarmHomeTab.Breakdown) },
                    icon = { Icon(imageVector = Icons.Default.Leaderboard, contentDescription = null) },
                    label = { Text(text = stringResource(id = R.string.alarm_tab_breakdown)) }
                )
            }
        },
        contentWindowInsets = WindowInsets(
            left = 0.dp,
            top = 0.dp,
            right = 0.dp,
            bottom = 0.dp
        )
    ) { innerPadding ->
        when (uiState.homeTab) {
            AlarmHomeTab.Alarms -> AlarmListRoute(
                alarms = uiState.alarms,
                onToggle = onToggle,
                onEdit = onEdit,
                selectedIds = uiState.selectedAlarmIds,
                onEnterSelection = onEnterSelection,
                onToggleSelection = onToggleSelection,
                onClearSelection = onClearSelection,
                onDeleteSelection = onDeleteSelection,
                onCreate = onCreate,
                onOpenSettings = onOpenSettings,
                modifier = Modifier.padding(innerPadding)
            )

            AlarmHomeTab.Breakdown -> AlarmBreakdownRoute(
                events = uiState.wakeEvents,
                period = uiState.breakdownPeriod,
                onPeriodChange = onBreakdownPeriodSelected,
                onOpenSettings = onOpenSettings,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun AlarmScreenPreview() {
    BreakTheSnoozeTheme {
        AlarmScreenContent(
            uiState = AlarmUiState(
                alarms = sampleAlarms(),
                draft = sampleDraft(useCurrentTime = false),
                destination = AlarmDestination.List
            ),
            onToggle = { _, _ -> },
            onDelete = {},
            onEdit = {},
            onStartCreate = {},
            onUpdateDraft = {},
            onTimeSelected = {},
            onToggleDay = {},
            onSoundSelected = {},
            onDismissTaskSelected = {},
            onQrBarcodeValueChange = {},
            onQrScanModeChange = {},
            onQrUniqueCountChange = {},
            onSaveDraft = {},
            onCancel = {},
            onOpenSettings = {},
            onCloseSettings = {},
            onDefaultTaskSelected = {},
            onDefaultRingtoneSelected = {},
            onDebugModeToggled = {},
            onEnterSelection = {},
            onToggleSelection = {},
            onClearSelection = {},
            onDeleteSelection = {},
            onSelectHomeTab = {},
            onBreakdownPeriodSelected = {}
        )
    }
}
