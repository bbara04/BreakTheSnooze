package hu.bbara.breakthesnooze.data.settings.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import hu.bbara.breakthesnooze.data.settings.model.SettingsState
import hu.bbara.breakthesnooze.ui.alarm.dismiss.AlarmDismissTaskType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val SETTINGS_STORE_NAME = "app_settings"
const val DEFAULT_COUNTDOWN_DURATION_MINUTES: Int = 60

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = SETTINGS_STORE_NAME)

class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    private val defaultTaskKey = stringPreferencesKey("default_dismiss_task")
    private val defaultRingtoneKey = stringPreferencesKey("default_ringtone_uri")
    private val debugModeKey = booleanPreferencesKey("debug_mode_enabled")
    private val defaultCountdownDurationKey = intPreferencesKey("default_countdown_duration_minutes")
    private val tightGapWarningEnabledKey = booleanPreferencesKey("tight_gap_warning_enabled")

    val settings: Flow<SettingsState> = dataStore.data.map { prefs ->
        val storedTask = prefs[defaultTaskKey]
        val storedRingtone = prefs[defaultRingtoneKey]
        val storedDuration = prefs[defaultCountdownDurationKey] ?: DEFAULT_COUNTDOWN_DURATION_MINUTES
        SettingsState(
            defaultDismissTask = AlarmDismissTaskType.fromStorageKey(storedTask),
            defaultRingtoneUri = storedRingtone,
            debugModeEnabled = prefs[debugModeKey] ?: false,
            defaultCountdownDurationMinutes = storedDuration.coerceAtLeast(0),
            tightGapWarningEnabled = prefs[tightGapWarningEnabledKey] ?: false
        )
    }

    suspend fun setDefaultDismissTask(task: AlarmDismissTaskType) {
        dataStore.edit { prefs ->
            prefs[defaultTaskKey] = task.storageKey
        }
    }

    suspend fun setDefaultRingtone(uri: String?) {
        dataStore.edit { prefs ->
            if (uri.isNullOrBlank()) {
                prefs -= defaultRingtoneKey
            } else {
                prefs[defaultRingtoneKey] = uri
            }
        }
    }

    suspend fun setDebugModeEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[debugModeKey] = enabled
        }
    }

    suspend fun setTightGapWarningEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[tightGapWarningEnabledKey] = enabled
        }
    }

    suspend fun setDefaultCountdownDuration(minutes: Int) {
        val sanitized = minutes.coerceAtLeast(0)
        dataStore.edit { prefs ->
            prefs[defaultCountdownDurationKey] = sanitized
        }
    }
}
