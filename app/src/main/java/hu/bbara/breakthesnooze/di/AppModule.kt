package hu.bbara.breakthesnooze.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import hu.bbara.breakthesnooze.data.alarm.AlarmDao
import hu.bbara.breakthesnooze.data.alarm.AlarmDatabase
import hu.bbara.breakthesnooze.data.alarm.AlarmRepository
import hu.bbara.breakthesnooze.data.alarm.AlarmScheduler
import hu.bbara.breakthesnooze.data.alarm.AndroidAlarmScheduler
import hu.bbara.breakthesnooze.data.alarm.DefaultAlarmRepository
import hu.bbara.breakthesnooze.data.alarm.WakeEventDao
import hu.bbara.breakthesnooze.data.settings.SettingsRepository
import hu.bbara.breakthesnooze.data.settings.settingsDataStore
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAlarmDatabase(@ApplicationContext context: Context): AlarmDatabase {
        return AlarmDatabase.getInstance(context)
    }

    @Provides
    fun provideAlarmDao(database: AlarmDatabase): AlarmDao = database.alarmDao()

    @Provides
    fun provideWakeEventDao(database: AlarmDatabase): WakeEventDao = database.wakeEventDao()

    @Provides
    @Singleton
    fun provideAlarmRepository(
        alarmDao: AlarmDao,
        wakeEventDao: WakeEventDao
    ): AlarmRepository = DefaultAlarmRepository(alarmDao, wakeEventDao)

    @Provides
    @Singleton
    fun provideAlarmScheduler(@ApplicationContext context: Context): AlarmScheduler {
        return AndroidAlarmScheduler(context)
    }

    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context
    ): DataStore<Preferences> = context.settingsDataStore

    @Provides
    @Singleton
    fun provideSettingsRepository(
        dataStore: DataStore<Preferences>
    ): SettingsRepository = SettingsRepository(dataStore)
}
