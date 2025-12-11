package hu.bbara.breakthesnooze.ui.alarm

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import hu.bbara.breakthesnooze.designsystem.BreakTheSnoozeTheme
import hu.bbara.breakthesnooze.testutil.RecordingActivityResultRegistryOwner
import hu.bbara.breakthesnooze.ui.alarm.create.AlarmCreateRoute
import hu.bbara.breakthesnooze.ui.alarm.model.AlarmCreationState
import hu.bbara.breakthesnooze.ui.alarm.model.DEFAULT_QR_UNIQUE_COUNT
import hu.bbara.breakthesnooze.ui.alarm.model.QrScanMode
import hu.bbara.breakthesnooze.ui.alarm.model.sampleDraft
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class AlarmCreateScreenRingtoneTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var draftState: MutableState<AlarmCreationState>
    private lateinit var registryOwner: RecordingActivityResultRegistryOwner

    @Test
    fun `selecting valid ringtone updates draft`() {
        setContent()

        composeRule.onNodeWithText("Tap to choose a ringtone", useUnmergedTree = true).performClick()
        composeRule.runOnIdle {
            assertThat(registryOwner.registry.hasPendingLaunch()).isTrue()
        }

        val uri = Settings.System.DEFAULT_ALARM_ALERT_URI
        composeRule.runOnIdle {
            registryOwner.registry.dispatchResult(
                Activity.RESULT_OK,
                Intent().apply { putExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, uri) }
            )
        }

        composeRule.runOnIdle {
            assertThat(draftState.value.soundUri).isEqualTo(uri.toString())
        }
    }

    @Test
    fun `cancelled picker keeps existing sound`() {
        val initialUri = "content://existing"
        setContent(sampleDraft(useCurrentTime = false).copy(time = LocalTime.of(6, 0), soundUri = initialUri))

        composeRule.onNodeWithText("Tap to choose a ringtone", useUnmergedTree = true).performClick()

        composeRule.runOnIdle {
            registryOwner.registry.dispatchResult(Activity.RESULT_CANCELED, Intent())
        }

        composeRule.runOnIdle {
            assertThat(draftState.value.soundUri).isEqualTo(initialUri)
        }
    }

    @Test
    fun `missing uri result falls back to default label`() {
        setContent()

        composeRule.onNodeWithText("Tap to choose a ringtone", useUnmergedTree = true).performClick()
        composeRule.runOnIdle {
            registryOwner.registry.dispatchResult(Activity.RESULT_OK, Intent())
        }

        composeRule.runOnIdle {
            assertThat(draftState.value.soundUri).isNull()
        }
        composeRule.onNodeWithText("Default alarm").assertIsDisplayed()
    }

    @Test
    fun `clear button resets sound selection`() {
        setContent(sampleDraft(useCurrentTime = false).copy(time = LocalTime.of(6, 0), soundUri = "content://custom"))

        composeRule.onNodeWithText("Use default").performClick()

        composeRule.runOnIdle {
            assertThat(draftState.value.soundUri).isNull()
        }
    }

    private fun setContent(initialDraft: AlarmCreationState = sampleDraft(useCurrentTime = false).copy(time = LocalTime.of(7, 30))) {
        registryOwner = RecordingActivityResultRegistryOwner()
        composeRule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) {
                BreakTheSnoozeTheme {
                    val state = remember { mutableStateOf(initialDraft) }
                    draftState = state
                    AlarmCreateRoute(
                        draft = state.value,
                        isEditing = false,
                        onUpdateDraft = { state.value = it },
                        onTimeSelected = { time -> state.value = state.value.copy(time = time) },
                        onToggleDay = { day ->
                            val current = state.value.repeatDays
                            val updated = if (current.contains(day)) current - day else current + day
                            state.value = state.value.copy(repeatDays = updated)
                        },
                        onSoundSelected = { uri -> state.value = state.value.copy(soundUri = uri) },
                        onDismissTaskSelected = { task -> state.value = state.value.copy(dismissTask = task) },
                        onQrBarcodeValueChange = { value -> state.value = state.value.copy(qrBarcodeValue = value) },
                        onQrScanModeChange = { mode ->
                            state.value = if (mode == QrScanMode.SpecificCode) {
                                state.value.copy(qrBarcodeValue = "", qrRequiredUniqueCount = 0)
                            } else {
                                state.value.copy(qrBarcodeValue = null, qrRequiredUniqueCount = DEFAULT_QR_UNIQUE_COUNT)
                            }
                        },
                        onQrUniqueCountChange = { count ->
                            state.value = state.value.copy(qrRequiredUniqueCount = count)
                        },
                        onSave = {},
                        onCancel = {}
                    )
                }
            }
        }
    }
}
