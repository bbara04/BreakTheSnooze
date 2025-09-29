package hu.bbara.viewideas.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import hu.bbara.viewideas.ui.alarm.dismiss.AlarmDismissTaskType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val SETTINGS_STORE_NAME = "app_settings"

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = SETTINGS_STORE_NAME)

data class SettingsState(
    val defaultDismissTask: AlarmDismissTaskType = AlarmDismissTaskType.DEFAULT,
    val defaultRingtoneUri: String? = null
)

class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    private val defaultTaskKey = stringPreferencesKey("default_dismiss_task")
    private val defaultRingtoneKey = stringPreferencesKey("default_ringtone_uri")

    val settings: Flow<SettingsState> = dataStore.data.map { prefs ->
        val storedTask = prefs[defaultTaskKey]
        val storedRingtone = prefs[defaultRingtoneKey]
        SettingsState(
            defaultDismissTask = AlarmDismissTaskType.fromStorageKey(storedTask),
            defaultRingtoneUri = storedRingtone
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
                prefs.remove(defaultRingtoneKey)
            } else {
                prefs[defaultRingtoneKey] = uri
            }
        }
    }
}
