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
    val defaultDismissTask: AlarmDismissTaskType = AlarmDismissTaskType.DEFAULT
)

class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    private val defaultTaskKey = stringPreferencesKey("default_dismiss_task")

    val settings: Flow<SettingsState> = dataStore.data.map { prefs ->
        val storedTask = prefs[defaultTaskKey]
        SettingsState(
            defaultDismissTask = AlarmDismissTaskType.fromStorageKey(storedTask)
        )
    }

    suspend fun setDefaultDismissTask(task: AlarmDismissTaskType) {
        dataStore.edit { prefs ->
            prefs[defaultTaskKey] = task.storageKey
        }
    }
}
