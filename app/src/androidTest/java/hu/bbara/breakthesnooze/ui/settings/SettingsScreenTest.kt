package hu.bbara.breakthesnooze.ui.settings

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import hu.bbara.breakthesnooze.data.settings.model.SettingsState
import hu.bbara.breakthesnooze.designsystem.BreakTheSnoozeTheme
import hu.bbara.breakthesnooze.ui.alarm.dismiss.AlarmDismissTaskType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var settingsState: MutableState<SettingsState>
    private var lastSelectedTask: AlarmDismissTaskType? = null
    private var lastDebugToggle: Boolean? = null
    private var lastLaunchedRingtoneIntent: Intent? = null
    private var defaultRingtoneSelectedInvocations: Int = 0

    @Test
    fun `selecting different default task updates state`() {
        setContent()

        val label = composeRule.activity.getString(AlarmDismissTaskType.OBJECT_DETECTION.optionLabelResId)
        composeRule.onNodeWithText(label).performClick()

        composeRule.runOnIdle {
            assertThat(lastSelectedTask).isEqualTo(AlarmDismissTaskType.OBJECT_DETECTION)
            assertThat(settingsState.value.defaultDismissTask).isEqualTo(AlarmDismissTaskType.OBJECT_DETECTION)
        }
    }

    @Test
    fun `ringtone picker result persists selection`() {
        setContent()

        composeRule.onNodeWithTag(SettingsTestTags.RINGTONE_ROW, useUnmergedTree = true).performClick()

        val uri = Settings.System.DEFAULT_ALARM_ALERT_URI
        composeRule.runOnIdle {
            handleRingtonePickerResult(
                Activity.RESULT_OK,
                Intent().apply { putExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, uri) }
            ) { selectedUri ->
                settingsState.value = settingsState.value.copy(defaultRingtoneUri = selectedUri)
            }
        }

        composeRule.runOnIdle {
            assertThat(settingsState.value.defaultRingtoneUri).isEqualTo(uri.toString())
        }
    }

    @Test
    fun `clear button resets ringtone to default`() {
        setContent(
            SettingsState(
                defaultDismissTask = AlarmDismissTaskType.MATH_CHALLENGE,
                defaultRingtoneUri = "content://custom",
                debugModeEnabled = false
            )
        )

        composeRule.onNodeWithTag(SettingsTestTags.RINGTONE_CLEAR, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.runOnIdle {
            assertThat(defaultRingtoneSelectedInvocations).isEqualTo(1)
            assertThat(settingsState.value.defaultRingtoneUri).isNull()
        }
    }

    @Test
    fun `toggling debug switch invokes callback`() {
        setContent()

        composeRule.onNodeWithTag(SettingsTestTags.DEBUG_SWITCH, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.runOnIdle {
            assertThat(lastDebugToggle).isTrue()
            assertThat(settingsState.value.debugModeEnabled).isTrue()
        }
    }

    private fun setContent(initial: SettingsState = SettingsState()) {
        lastLaunchedRingtoneIntent = null
        defaultRingtoneSelectedInvocations = 0
        composeRule.setContent {
            BreakTheSnoozeTheme {
                val state = remember { mutableStateOf(initial) }
                settingsState = state
                SettingsRoute(
                    settings = state.value,
                    onDefaultTaskSelected = { task ->
                        lastSelectedTask = task
                        state.value = state.value.copy(defaultDismissTask = task)
                    },
                    onDefaultRingtoneSelected = { uri ->
                        defaultRingtoneSelectedInvocations++
                        state.value = state.value.copy(defaultRingtoneUri = uri)
                    },
                    onDebugModeToggled = { enabled ->
                        lastDebugToggle = enabled
                        state.value = state.value.copy(debugModeEnabled = enabled)
                    },
                    onTightGapWarningToggled = { enabled ->
                        state.value = state.value.copy(tightGapWarningEnabled = enabled)
                    },
                    onBack = {},
                    onLaunchRingtonePicker = { intent -> lastLaunchedRingtoneIntent = intent }
                )
            }
        }
    }

}
