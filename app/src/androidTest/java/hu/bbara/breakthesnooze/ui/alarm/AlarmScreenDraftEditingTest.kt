package hu.bbara.breakthesnooze.ui.alarm

import androidx.activity.ComponentActivity
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import hu.bbara.breakthesnooze.data.settings.model.SettingsState
import hu.bbara.breakthesnooze.designsystem.BreakTheSnoozeTheme
import hu.bbara.breakthesnooze.ui.alarm.dismiss.AlarmDismissTaskType
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class AlarmScreenDraftEditingTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var uiStateHolder: MutableState<AlarmUiState>
    private var saveInvocations = 0
    private var cancelInvocations = 0

    @Before
    fun setUpContent() {
        saveInvocations = 0
        cancelInvocations = 0
        composeRule.setContent {
            BreakTheSnoozeTheme {
                val state = remember {
                    mutableStateOf(
                        AlarmUiState(
                            alarms = emptyList(),
                            wakeEvents = emptyList(),
                            settings = SettingsState(defaultDismissTask = AlarmDismissTaskType.DEFAULT),
                            draft = sampleDraft(useCurrentTime = false, defaultTask = AlarmDismissTaskType.DEFAULT).copy(
                                time = null,
                                label = "",
                                repeatDays = emptySet(),
                                soundUri = null
                            ),
                            destination = AlarmDestination.Create
                        )
                    )
                }
                uiStateHolder = state
                AlarmScreenContentForTest(
                    uiState = state.value,
                    onToggle = { _, _ -> },
                    onDelete = {},
                    onEdit = {},
                    onStartCreate = {},
                    onUpdateDraft = { draft -> state.value = state.value.copy(draft = draft) },
                    onTimeSelected = { time ->
                        state.value = state.value.copy(draft = state.value.draft.copy(time = time))
                    },
                    onToggleDay = { day ->
                        val current = state.value.draft.repeatDays
                        val updated = if (current.contains(day)) current - day else current + day
                        state.value = state.value.copy(draft = state.value.draft.copy(repeatDays = updated))
                    },
                    onSoundSelected = { sound ->
                        state.value = state.value.copy(draft = state.value.draft.copy(soundUri = sound))
                    },
                    onDismissTaskSelected = { task ->
                        state.value = state.value.copy(draft = state.value.draft.copy(dismissTask = task))
                    },
                    onQrBarcodeValueChange = { value ->
                        state.value = state.value.copy(draft = state.value.draft.copy(qrBarcodeValue = value))
                    },
                    onQrScanModeChange = { mode ->
                        val draft = state.value.draft
                        val updated = if (mode == QrScanMode.SpecificCode) {
                            draft.copy(qrRequiredUniqueCount = 0)
                        } else {
                            draft.copy(qrBarcodeValue = null, qrRequiredUniqueCount = DEFAULT_QR_UNIQUE_COUNT)
                        }
                        state.value = state.value.copy(draft = updated)
                    },
                    onQrUniqueCountChange = { count ->
                        state.value = state.value.copy(
                            draft = state.value.draft.copy(qrRequiredUniqueCount = count)
                        )
                    },
                    onSaveDraft = { saveInvocations++ },
                    onCancel = { cancelInvocations++ },
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
                    modifier = androidx.compose.ui.Modifier
                )
            }
        }
    }

    @Test
    fun `save disabled until time selected then enabled after time provided`() {
        composeRule.onNodeWithTag(AlarmCreateTestTags.SAVE_BUTTON).assertIsNotEnabled()

        composeRule.runOnIdle {
            uiStateHolder.value = uiStateHolder.value.copy(
                draft = uiStateHolder.value.draft.copy(time = LocalTime.of(6, 30))
            )
        }

        composeRule.onNodeWithTag(AlarmCreateTestTags.SAVE_BUTTON).assertIsEnabled()
    }

    @Test
    fun `label input updates draft state`() {
        composeRule.onNodeWithTag(AlarmCreateTestTags.LABEL_FIELD, useUnmergedTree = true)
            .performTextInput("Morning Run")

        composeRule.runOnIdle {
            assertThat(uiStateHolder.value.draft.label).isEqualTo("Morning Run")
        }
    }

    @Test
    fun `cancel invokes callback`() {
        composeRule.onNodeWithTag(AlarmCreateTestTags.BACK_BUTTON).performClick()
        composeRule.runOnIdle {
            assertThat(cancelInvocations).isEqualTo(1)
        }
    }

    @Test
    fun `save invokes callback when time selected`() {
        composeRule.runOnIdle {
            uiStateHolder.value = uiStateHolder.value.copy(
                draft = uiStateHolder.value.draft.copy(time = LocalTime.of(7, 45))
            )
        }

        composeRule.onNodeWithTag(AlarmCreateTestTags.SAVE_BUTTON).performClick()

        composeRule.runOnIdle {
            assertThat(saveInvocations).isEqualTo(1)
        }
    }
}
