package hu.bbara.breakthesnooze.ui.alarm.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.VisibleForTesting
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import hu.bbara.breakthesnooze.R
import hu.bbara.breakthesnooze.designsystem.BreakTheSnoozeTheme
import hu.bbara.breakthesnooze.ui.alarm.breakdown.AlarmBreakdownRoute
import hu.bbara.breakthesnooze.ui.alarm.create.AlarmCreateRoute
import hu.bbara.breakthesnooze.ui.alarm.create.AlarmEditorViewModel
import hu.bbara.breakthesnooze.ui.alarm.dismiss.AlarmDismissTaskType
import hu.bbara.breakthesnooze.ui.alarm.duration.DurationAlarmCreationState
import hu.bbara.breakthesnooze.ui.alarm.duration.DurationAlarmRoute
import hu.bbara.breakthesnooze.ui.alarm.duration.DurationAlarmUiModel
import hu.bbara.breakthesnooze.ui.alarm.duration.DurationAlarmViewModel
import hu.bbara.breakthesnooze.ui.alarm.duration.sampleDurationDraft
import hu.bbara.breakthesnooze.ui.alarm.list.AlarmListRoute
import hu.bbara.breakthesnooze.ui.alarm.list.AlarmListViewModel
import hu.bbara.breakthesnooze.ui.alarm.model.AlarmCreationState
import hu.bbara.breakthesnooze.ui.alarm.model.AlarmDestination
import hu.bbara.breakthesnooze.ui.alarm.model.AlarmHomeTab
import hu.bbara.breakthesnooze.ui.alarm.model.AlarmUiState
import hu.bbara.breakthesnooze.ui.alarm.model.BreakdownPeriod
import hu.bbara.breakthesnooze.ui.alarm.model.QrScanMode
import hu.bbara.breakthesnooze.ui.alarm.model.sampleAlarms
import hu.bbara.breakthesnooze.ui.alarm.model.sampleDraft
import hu.bbara.breakthesnooze.ui.alarm.settings.AlarmSettingsViewModel
import hu.bbara.breakthesnooze.ui.settings.SettingsRoute
import java.time.DayOfWeek
import java.time.LocalTime

@Composable
fun AlarmScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val listViewModel: AlarmListViewModel = hiltViewModel()
    val editorViewModel: AlarmEditorViewModel = hiltViewModel()
    val durationViewModel: DurationAlarmViewModel = hiltViewModel()
    val settingsViewModel: AlarmSettingsViewModel = hiltViewModel()

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

    val listState by listViewModel.state.collectAsState()
    val editorState by editorViewModel.state.collectAsState()
    val durationState by durationViewModel.state.collectAsState()
    val settingsState by settingsViewModel.state.collectAsState()
    val uiState = AlarmUiState(
        alarms = listState.alarms,
        wakeEvents = listState.wakeEvents,
        settings = settingsState,
        draft = editorState.draft,
        durationAlarms = durationState.durationAlarms,
        durationDraft = durationState.durationDraft,
        destination = editorState.destination,
        homeTab = listState.homeTab,
        breakdownPeriod = listState.breakdownPeriod,
        editingAlarm = editorState.editingAlarm,
        selectedAlarmIds = listState.selectedAlarmIds,
        isSavingDuration = durationState.isSavingDuration
    )

    BackHandler(
        enabled = uiState.selectedAlarmIds.isNotEmpty() ||
            uiState.destination == AlarmDestination.Create ||
            uiState.destination == AlarmDestination.Settings
    ) {
        when {
            uiState.selectedAlarmIds.isNotEmpty() -> listViewModel.clearSelection()
            uiState.destination == AlarmDestination.Create -> editorViewModel.cancelCreation()
            uiState.destination == AlarmDestination.Settings -> editorViewModel.closeSettings()
        }
    }

    AlarmScreenContent(
        uiState = uiState,
        onToggle = listViewModel::onToggleAlarm,
        onDelete = listViewModel::deleteAlarm,
        onEdit = { id ->
            listViewModel.clearSelection()
            editorViewModel.startEditing(id)
        },
        onStartCreate = {
            listViewModel.clearSelection()
            editorViewModel.startCreating()
        },
        onUpdateDraft = editorViewModel::updateDraft,
        onTimeSelected = editorViewModel::setDraftTime,
        onToggleDay = editorViewModel::toggleDraftDay,
        onSoundSelected = editorViewModel::setDraftSound,
        onDismissTaskSelected = editorViewModel::setDraftDismissTask,
        onQrBarcodeValueChange = editorViewModel::setDraftQrBarcodeValue,
        onQrScanModeChange = editorViewModel::setDraftQrScanMode,
        onQrUniqueCountChange = editorViewModel::setDraftQrUniqueCount,
        onSaveDraft = editorViewModel::saveDraft,
        onCancel = editorViewModel::cancelCreation,
        onOpenSettings = editorViewModel::openSettings,
        onCloseSettings = editorViewModel::closeSettings,
        onDefaultTaskSelected = settingsViewModel::setDefaultDismissTask,
        onDefaultRingtoneSelected = settingsViewModel::setDefaultRingtone,
        onDebugModeToggled = settingsViewModel::setDebugMode,
        onTightGapWarningToggled = settingsViewModel::setTightGapWarningEnabled,
        onEnterSelection = listViewModel::enterSelection,
        onToggleSelection = listViewModel::toggleSelection,
        onClearSelection = listViewModel::clearSelection,
        onDeleteSelection = listViewModel::deleteSelected,
        onSelectHomeTab = {
            listViewModel.selectHomeTab(it)
            if (it == AlarmHomeTab.Duration) {
                durationViewModel.prepareDurationDraft()
            }
        },
        onBreakdownPeriodSelected = listViewModel::setBreakdownPeriod,
        durationAlarms = uiState.durationAlarms,
        onCancelDurationAlarm = durationViewModel::deleteDurationAlarm,
        durationDraft = uiState.durationDraft,
        onDurationLabelChange = durationViewModel::setDurationLabel,
        onDurationHoursChange = durationViewModel::setDurationHours,
        onDurationMinutesChange = durationViewModel::setDurationMinutes,
        onDurationSoundSelected = durationViewModel::setDurationSound,
        onDurationDismissTaskSelected = durationViewModel::setDurationDismissTask,
        onDurationQrBarcodeValueChange = durationViewModel::setDurationQrBarcodeValue,
        onDurationQrScanModeChange = durationViewModel::setDurationQrScanMode,
        onDurationQrUniqueCountChange = durationViewModel::setDurationQrUniqueCount,
        onCreateDurationAlarm = {
            durationViewModel.saveDurationDraft()
            // Move back to the alarms tab after creating a duration alarm
            listViewModel.selectHomeTab(AlarmHomeTab.Alarms)
        },
        onSaveDefaultDuration = durationViewModel::saveDefaultDuration,
        isSavingDuration = uiState.isSavingDuration,
        modifier = modifier
    )
}

private fun <S> AnimatedContentTransitionScope<S>.slideLeft(): ContentTransform =
    slideIntoContainer(
        AnimatedContentTransitionScope.SlideDirection.Left
    ) + fadeIn() togetherWith slideOutOfContainer(
        AnimatedContentTransitionScope.SlideDirection.Left
    ) + fadeOut()

private fun <S> AnimatedContentTransitionScope<S>.slideRight(): ContentTransform =
    slideIntoContainer(
        AnimatedContentTransitionScope.SlideDirection.Right
    ) + fadeIn() togetherWith slideOutOfContainer(
        AnimatedContentTransitionScope.SlideDirection.Right
    ) + fadeOut()

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
    onTightGapWarningToggled: (Boolean) -> Unit,
    onEnterSelection: (Int) -> Unit,
    onToggleSelection: (Int) -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelection: () -> Unit,
    onSelectHomeTab: (AlarmHomeTab) -> Unit,
    onBreakdownPeriodSelected: (BreakdownPeriod) -> Unit,
    durationAlarms: List<DurationAlarmUiModel>,
    onCancelDurationAlarm: (Int) -> Unit,
    durationDraft: DurationAlarmCreationState,
    onDurationLabelChange: (String) -> Unit,
    onDurationHoursChange: (Int) -> Unit,
    onDurationMinutesChange: (Int) -> Unit,
    onDurationSoundSelected: (String?) -> Unit,
    onDurationDismissTaskSelected: (AlarmDismissTaskType) -> Unit,
    onDurationQrBarcodeValueChange: (String?) -> Unit,
    onDurationQrScanModeChange: (QrScanMode) -> Unit,
    onDurationQrUniqueCountChange: (Int) -> Unit,
    onCreateDurationAlarm: () -> Unit,
    onSaveDefaultDuration: () -> Unit,
    isSavingDuration: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = uiState.destination,
        transitionSpec = {
            when (initialState) {
                AlarmDestination.List -> {
                    if (targetState == AlarmDestination.Create) {
                        slideLeft()
                    } else {
                        slideRight()
                    }
                }
                AlarmDestination.Create -> {
                    slideRight()
                }
                AlarmDestination.Settings -> {
                    slideLeft()
                }
            }
        },
        label = "alarm_destination"
    ) { destination ->
        when (destination) {
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
                durationAlarms = durationAlarms,
                onCancelDurationAlarm = onCancelDurationAlarm,
                durationDraft = durationDraft,
                onDurationLabelChange = onDurationLabelChange,
                onDurationHoursChange = onDurationHoursChange,
                onDurationMinutesChange = onDurationMinutesChange,
                onDurationSoundSelected = onDurationSoundSelected,
                onDurationDismissTaskSelected = onDurationDismissTaskSelected,
                onDurationQrBarcodeValueChange = onDurationQrBarcodeValueChange,
                onDurationQrScanModeChange = onDurationQrScanModeChange,
                onDurationQrUniqueCountChange = onDurationQrUniqueCountChange,
                onCreateDurationAlarm = onCreateDurationAlarm,
                onSaveDefaultDuration = onSaveDefaultDuration,
                isSavingDuration = isSavingDuration,
                tightGapWarningEnabled = uiState.settings.tightGapWarningEnabled,
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
                onTightGapWarningToggled = onTightGapWarningToggled,
                onBack = onCloseSettings,
                modifier = modifier
            )
        }
    }
}

@VisibleForTesting
@Composable
internal fun AlarmScreenContentForTest(
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
    onTightGapWarningToggled: (Boolean) -> Unit,
    onEnterSelection: (Int) -> Unit,
    onToggleSelection: (Int) -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelection: () -> Unit,
    onSelectHomeTab: (AlarmHomeTab) -> Unit,
    onBreakdownPeriodSelected: (BreakdownPeriod) -> Unit,
    durationAlarms: List<DurationAlarmUiModel> = emptyList(),
    onCancelDurationAlarm: (Int) -> Unit = {},
    durationDraft: DurationAlarmCreationState = sampleDurationDraft(
        defaultTask = AlarmDismissTaskType.DEFAULT,
        defaultSound = null
    ),
    onDurationLabelChange: (String) -> Unit = {},
    onDurationHoursChange: (Int) -> Unit = {},
    onDurationMinutesChange: (Int) -> Unit = {},
    onDurationSoundSelected: (String?) -> Unit = {},
    onDurationDismissTaskSelected: (AlarmDismissTaskType) -> Unit = {},
    onDurationQrBarcodeValueChange: (String?) -> Unit = {},
    onDurationQrScanModeChange: (QrScanMode) -> Unit = {},
    onDurationQrUniqueCountChange: (Int) -> Unit = {},
    onCreateDurationAlarm: () -> Unit = {},
    onSaveDefaultDuration: () -> Unit = {},
    isSavingDuration: Boolean = false,
    modifier: Modifier = Modifier
) {
    AlarmScreenContent(
        uiState = uiState,
        onToggle = onToggle,
        onDelete = onDelete,
        onEdit = onEdit,
        onStartCreate = onStartCreate,
        onUpdateDraft = onUpdateDraft,
        onTimeSelected = onTimeSelected,
        onToggleDay = onToggleDay,
        onSoundSelected = onSoundSelected,
        onDismissTaskSelected = onDismissTaskSelected,
        onQrBarcodeValueChange = onQrBarcodeValueChange,
        onQrScanModeChange = onQrScanModeChange,
        onQrUniqueCountChange = onQrUniqueCountChange,
        onSaveDraft = onSaveDraft,
        onCancel = onCancel,
        onOpenSettings = onOpenSettings,
        onCloseSettings = onCloseSettings,
        onDefaultTaskSelected = onDefaultTaskSelected,
        onDefaultRingtoneSelected = onDefaultRingtoneSelected,
        onDebugModeToggled = onDebugModeToggled,
        onTightGapWarningToggled = onTightGapWarningToggled,
        onEnterSelection = onEnterSelection,
        onToggleSelection = onToggleSelection,
        onClearSelection = onClearSelection,
        onDeleteSelection = onDeleteSelection,
        onSelectHomeTab = onSelectHomeTab,
        onBreakdownPeriodSelected = onBreakdownPeriodSelected,
        durationAlarms = durationAlarms,
        onCancelDurationAlarm = onCancelDurationAlarm,
        durationDraft = durationDraft,
        onDurationLabelChange = onDurationLabelChange,
        onDurationHoursChange = onDurationHoursChange,
        onDurationMinutesChange = onDurationMinutesChange,
        onDurationSoundSelected = onDurationSoundSelected,
        onDurationDismissTaskSelected = onDurationDismissTaskSelected,
        onDurationQrBarcodeValueChange = onDurationQrBarcodeValueChange,
        onDurationQrScanModeChange = onDurationQrScanModeChange,
        onDurationQrUniqueCountChange = onDurationQrUniqueCountChange,
        onCreateDurationAlarm = onCreateDurationAlarm,
        onSaveDefaultDuration = onSaveDefaultDuration,
        isSavingDuration = isSavingDuration,
        modifier = modifier
    )
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
    durationAlarms: List<DurationAlarmUiModel>,
    onCancelDurationAlarm: (Int) -> Unit,
    durationDraft: DurationAlarmCreationState,
    onDurationLabelChange: (String) -> Unit,
    onDurationHoursChange: (Int) -> Unit,
    onDurationMinutesChange: (Int) -> Unit,
    onDurationSoundSelected: (String?) -> Unit,
    onDurationDismissTaskSelected: (AlarmDismissTaskType) -> Unit,
    onDurationQrBarcodeValueChange: (String?) -> Unit,
    onDurationQrScanModeChange: (QrScanMode) -> Unit,
    onDurationQrUniqueCountChange: (Int) -> Unit,
    onCreateDurationAlarm: () -> Unit,
    onSaveDefaultDuration: () -> Unit,
    isSavingDuration: Boolean,
    tightGapWarningEnabled: Boolean,
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
                    selected = uiState.homeTab == AlarmHomeTab.Duration,
                    onClick = { onSelectHomeTab(AlarmHomeTab.Duration) },
                    icon = { Icon(imageVector = Icons.Default.Timer, contentDescription = null) },
                    label = { Text(text = stringResource(id = R.string.alarm_tab_duration)) }
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
        AnimatedContent(
            targetState = uiState.homeTab,
            transitionSpec = {
                when (initialState) {
                    AlarmHomeTab.Alarms -> {
                        slideLeft()
                    }
                    AlarmHomeTab.Duration -> {
                        if (targetState == AlarmHomeTab.Alarms) {
                            slideRight()
                        } else {
                            slideLeft()
                        }
                    }
                    AlarmHomeTab.Breakdown -> {
                        slideRight()
                    }
                }
            },
            label = "alarm_home_tab"
        ) { homeTab ->
            when (homeTab) {
                AlarmHomeTab.Alarms -> AlarmListRoute(
                    alarms = uiState.alarms,
                    durationAlarms = durationAlarms,
                    onCancelDurationAlarm = onCancelDurationAlarm,
                    onToggle = onToggle,
                    onEdit = onEdit,
                    selectedIds = uiState.selectedAlarmIds,
                    onEnterSelection = onEnterSelection,
                    onToggleSelection = onToggleSelection,
                    onClearSelection = onClearSelection,
                    onDeleteSelection = onDeleteSelection,
                    onCreate = onCreate,
                    onOpenSettings = onOpenSettings,
                    tightGapWarningEnabled = tightGapWarningEnabled,
                    modifier = Modifier.padding(innerPadding)
                )

                AlarmHomeTab.Duration -> DurationAlarmRoute(
                    draft = durationDraft,
                    onLabelChange = onDurationLabelChange,
                    onHoursChange = onDurationHoursChange,
                    onMinutesChange = onDurationMinutesChange,
                    onSaveDefaultDuration = onSaveDefaultDuration,
                    onSoundSelected = onDurationSoundSelected,
                    onDismissTaskSelected = onDurationDismissTaskSelected,
                    onQrBarcodeValueChange = onDurationQrBarcodeValueChange,
                    onQrScanModeChange = onDurationQrScanModeChange,
                    onQrUniqueCountChange = onDurationQrUniqueCountChange,
                    onCreate = onCreateDurationAlarm,
                    isSaving = isSavingDuration,
                    onBack = { onSelectHomeTab(AlarmHomeTab.Alarms) },
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
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun AlarmScreenPreview() {
    BreakTheSnoozeTheme {
        val previewState = AlarmUiState(
            alarms = sampleAlarms(),
            draft = sampleDraft(useCurrentTime = false),
            destination = AlarmDestination.List
        )
        AlarmScreenContent(
            uiState = previewState,
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
            onTightGapWarningToggled = {},
            onEnterSelection = {},
            onToggleSelection = {},
            onClearSelection = {},
            onDeleteSelection = {},
            onSelectHomeTab = {},
            onBreakdownPeriodSelected = {},
            durationAlarms = emptyList(),
            onCancelDurationAlarm = {},
            durationDraft = sampleDurationDraft(
                defaultTask = AlarmDismissTaskType.DEFAULT,
                defaultSound = null
            ),
            onDurationLabelChange = {},
            onDurationHoursChange = {},
            onDurationMinutesChange = {},
            onDurationSoundSelected = {},
            onDurationDismissTaskSelected = {},
            onDurationQrBarcodeValueChange = {},
            onDurationQrScanModeChange = {},
            onDurationQrUniqueCountChange = {},
            onCreateDurationAlarm = {},
            onSaveDefaultDuration = {},
            isSavingDuration = false
        )
    }
}
