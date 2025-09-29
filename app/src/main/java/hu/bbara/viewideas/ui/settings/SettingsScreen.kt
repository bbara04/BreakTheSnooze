package hu.bbara.viewideas.ui.settings

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import hu.bbara.viewideas.R
import hu.bbara.viewideas.data.settings.SettingsState
import hu.bbara.viewideas.ui.alarm.dismiss.AlarmDismissTaskType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsRoute(
    settings: SettingsState,
    onDefaultTaskSelected: (AlarmDismissTaskType) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    var vibrationEnabled by rememberSaveable { mutableStateOf(true) }
    var requireDismissTask by rememberSaveable { mutableStateOf(true) }
    var playBackupSound by rememberSaveable { mutableStateOf(false) }
    var snoozeMinutes by rememberSaveable { mutableFloatStateOf(5f) }
    val selectedTask = settings.defaultDismissTask

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = stringResource(id = R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.settings_back_content_description)
                        )
                    }
                },
                scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            SettingsSection(
                title = stringResource(id = R.string.settings_defaults_title)
            ) {
                Text(
                    text = stringResource(id = R.string.settings_default_task_title),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = stringResource(id = R.string.settings_default_task_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AlarmDismissTaskType.values().forEach { option ->
                        DefaultTaskOption(
                            option = option,
                            selected = option == selectedTask,
                            onSelect = { onDefaultTaskSelected(option) }
                        )
                    }
                }
            }

            SettingsSection(
                title = stringResource(id = R.string.settings_general_title)
            ) {
                SettingsToggleRow(
                    title = stringResource(id = R.string.settings_vibration_title),
                    description = stringResource(id = R.string.settings_vibration_subtitle),
                    checked = vibrationEnabled,
                    onCheckedChange = { vibrationEnabled = it }
                )
                HorizontalDivider()
                SettingsToggleRow(
                    title = stringResource(id = R.string.settings_require_task_title),
                    description = stringResource(id = R.string.settings_require_task_subtitle),
                    checked = requireDismissTask,
                    onCheckedChange = { requireDismissTask = it }
                )
            }

            SettingsSection(
                title = stringResource(id = R.string.settings_notifications_title)
            ) {
                SettingsToggleRow(
                    title = stringResource(id = R.string.settings_backup_sound_title),
                    description = stringResource(id = R.string.settings_backup_sound_subtitle),
                    checked = playBackupSound,
                    onCheckedChange = { playBackupSound = it }
                )
            }

            SettingsSection(
                title = stringResource(id = R.string.settings_snooze_title)
            ) {
                Text(
                    text = stringResource(id = R.string.settings_snooze_description, snoozeMinutes.toInt()),
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = snoozeMinutes,
                    onValueChange = { snoozeMinutes = it },
                    valueRange = 1f..15f,
                    steps = 14
                )
                Text(
                    text = stringResource(id = R.string.settings_snooze_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun DefaultTaskOption(
    option: AlarmDismissTaskType,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Surface(
        onClick = onSelect,
        shape = MaterialTheme.shapes.large,
        tonalElevation = if (selected) 6.dp else 2.dp,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            RadioButton(selected = selected, onClick = null)
            Text(
                text = stringResource(id = option.optionLabelResId),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
