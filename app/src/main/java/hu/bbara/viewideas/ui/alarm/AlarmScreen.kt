package hu.bbara.viewideas.ui.alarm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import hu.bbara.viewideas.data.alarm.AlarmRepositoryProvider
import hu.bbara.viewideas.ui.theme.ViewIdeasTheme
import java.time.DayOfWeek
import java.time.LocalTime

@Composable
fun AlarmScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember(context) { AlarmRepositoryProvider.getRepository(context) }
    val alarmViewModel: AlarmViewModel = viewModel(
        factory = remember(repository) { AlarmViewModelFactory(repository) }
    )

    val uiState by alarmViewModel.uiState.collectAsState()

    AlarmScreenContent(
        uiState = uiState,
        onToggle = alarmViewModel::onToggleAlarm,
        onDelete = alarmViewModel::deleteAlarm,
        onEdit = alarmViewModel::startEditing,
        onStartCreate = alarmViewModel::startCreating,
        onUpdateDraft = alarmViewModel::updateDraft,
        onSelectPreset = alarmViewModel::selectPresetTime,
        onTimeSelected = alarmViewModel::setDraftTime,
        onToggleDay = alarmViewModel::toggleDraftDay,
        onResetDraft = alarmViewModel::resetDraft,
        onSaveDraft = alarmViewModel::saveDraft,
        onCancel = alarmViewModel::cancelCreation,
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
    onSelectPreset: (LocalTime) -> Unit,
    onTimeSelected: (LocalTime) -> Unit,
    onToggleDay: (DayOfWeek) -> Unit,
    onResetDraft: () -> Unit,
    onSaveDraft: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (uiState.destination) {
        AlarmDestination.List -> AlarmListRoute(
            alarms = uiState.alarms,
            onToggle = onToggle,
            onDelete = onDelete,
            onEdit = onEdit,
            onCreate = onStartCreate,
            modifier = modifier
        )

        AlarmDestination.Create -> AlarmCreateRoute(
            draft = uiState.draft,
            isEditing = uiState.editingAlarm != null,
            onUpdateDraft = onUpdateDraft,
            onSelectPreset = onSelectPreset,
            onTimeSelected = onTimeSelected,
            onToggleDay = onToggleDay,
            onReset = onResetDraft,
            onSave = onSaveDraft,
            onCancel = onCancel,
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
                draft = sampleDraft(),
                destination = AlarmDestination.List
            ),
            onToggle = { _, _ -> },
            onDelete = {},
            onEdit = {},
            onStartCreate = {},
            onUpdateDraft = {},
            onSelectPreset = {},
            onTimeSelected = {},
            onToggleDay = {},
            onResetDraft = {},
            onSaveDraft = {},
            onCancel = {}
        )
    }
}
