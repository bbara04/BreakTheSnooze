package hu.bbara.viewideas.ui.alarm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import hu.bbara.viewideas.ui.theme.ViewIdeasTheme
import java.time.DayOfWeek
import java.time.LocalTime

@Composable
fun AlarmScreen(
    modifier: Modifier = Modifier,
    viewModel: AlarmViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    AlarmScreenContent(
        uiState = uiState,
        onToggle = viewModel::onToggleAlarm,
        onDelete = viewModel::deleteAlarm,
        onStartCreate = viewModel::startCreating,
        onUpdateDraft = viewModel::updateDraft,
        onNudgeTime = viewModel::nudgeDraftTime,
        onSelectPreset = viewModel::selectPresetTime,
        onToggleDay = viewModel::toggleDraftDay,
        onResetDraft = viewModel::resetDraft,
        onSaveDraft = viewModel::saveDraft,
        onCancel = viewModel::cancelCreation,
        modifier = modifier
    )
}

@Composable
private fun AlarmScreenContent(
    uiState: AlarmUiState,
    onToggle: (Int, Boolean) -> Unit,
    onDelete: (Int) -> Unit,
    onStartCreate: () -> Unit,
    onUpdateDraft: (AlarmCreationState) -> Unit,
    onNudgeTime: () -> Unit,
    onSelectPreset: (LocalTime) -> Unit,
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
            onCreate = onStartCreate,
            modifier = modifier
        )

        AlarmDestination.Create -> AlarmCreateRoute(
            draft = uiState.draft,
            onUpdateDraft = onUpdateDraft,
            onNudgeTime = onNudgeTime,
            onSelectPreset = onSelectPreset,
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
            onStartCreate = {},
            onUpdateDraft = {},
            onNudgeTime = {},
            onSelectPreset = {},
            onToggleDay = {},
            onResetDraft = {},
            onSaveDraft = {},
            onCancel = {}
        )
    }
}
