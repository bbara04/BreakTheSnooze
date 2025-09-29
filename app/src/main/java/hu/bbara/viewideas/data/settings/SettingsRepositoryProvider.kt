package hu.bbara.viewideas.data.settings

import android.content.Context

object SettingsRepositoryProvider {

    @Volatile
    private var repository: SettingsRepository? = null

    fun getRepository(context: Context): SettingsRepository {
        return repository ?: synchronized(this) {
            repository ?: SettingsRepository(context.settingsDataStore).also { repository = it }
        }
    }
}
