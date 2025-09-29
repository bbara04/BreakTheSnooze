package hu.bbara.viewideas.ui.alarm

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import hu.bbara.viewideas.data.alarm.AlarmRepositoryProvider
import hu.bbara.viewideas.data.alarm.AlarmSchedulerProvider
import hu.bbara.viewideas.ui.alarm.dismiss.AlarmDismissTaskType
import hu.bbara.viewideas.ui.settings.SettingsRoute
import hu.bbara.viewideas.ui.theme.ViewIdeasTheme
import java.time.DayOfWeek
import java.time.LocalTime

@Composable
fun AlarmScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember(context) { AlarmRepositoryProvider.getRepository(context) }
    val scheduler = remember(context) { AlarmSchedulerProvider.getScheduler(context) }
    val alarmViewModel: AlarmViewModel = viewModel(
        factory = remember(repository, scheduler) { AlarmViewModelFactory(repository, scheduler) }
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
        onSaveDraft = alarmViewModel::saveDraft,
        onCancel = alarmViewModel::cancelCreation,
        onOpenSettings = alarmViewModel::openSettings,
        onCloseSettings = alarmViewModel::closeSettings,
        onEnterSelection = alarmViewModel::enterSelection,
        onToggleSelection = alarmViewModel::toggleSelection,
        onClearSelection = alarmViewModel::clearSelection,
        onDeleteSelection = alarmViewModel::deleteSelected,
        modifier = modifier.navigationBarsPadding()
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
    onSaveDraft: () -> Unit,
    onCancel: () -> Unit,
    onOpenSettings: () -> Unit,
    onCloseSettings: () -> Unit,
    onEnterSelection: (Int) -> Unit,
    onToggleSelection: (Int) -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (uiState.destination) {
        AlarmDestination.List -> AlarmListRoute(
            alarms = uiState.alarms,
            onToggle = onToggle,
            onEdit = onEdit,
            selectedIds = uiState.selectedAlarmIds,
            onEnterSelection = onEnterSelection,
            onToggleSelection = onToggleSelection,
            onClearSelection = onClearSelection,
            onDeleteSelection = onDeleteSelection,
            onCreate = onStartCreate,
            onOpenSettings = onOpenSettings,
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
            onSave = onSaveDraft,
            onCancel = onCancel,
            modifier = modifier
        )

        AlarmDestination.Settings -> SettingsRoute(
            onBack = onCloseSettings,
            modifier = modifier
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun AlarmScreenPreview() {
    ViewIdeasTheme {
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
            onSaveDraft = {},
            onCancel = {},
            onOpenSettings = {},
            onCloseSettings = {},
            onEnterSelection = {},
            onToggleSelection = {},
            onClearSelection = {},
            onDeleteSelection = {}
        )
    }
}
