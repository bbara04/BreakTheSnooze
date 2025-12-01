package hu.bbara.breakthesnooze.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import hu.bbara.breakthesnooze.data.alarm.db.AlarmDao
import hu.bbara.breakthesnooze.data.alarm.db.AlarmDatabase
import hu.bbara.breakthesnooze.data.alarm.db.WakeEventDao
import hu.bbara.breakthesnooze.data.alarm.repository.AlarmRepository
import hu.bbara.breakthesnooze.data.alarm.repository.DefaultAlarmRepository
import hu.bbara.breakthesnooze.data.alarm.scheduler.AlarmScheduler
import hu.bbara.breakthesnooze.data.alarm.scheduler.AndroidAlarmScheduler
import hu.bbara.breakthesnooze.data.duration.db.DurationAlarmDao
import hu.bbara.breakthesnooze.data.duration.repository.DefaultDurationAlarmRepository
import hu.bbara.breakthesnooze.data.duration.repository.DurationAlarmRepository
import hu.bbara.breakthesnooze.data.duration.scheduler.AndroidDurationAlarmScheduler
import hu.bbara.breakthesnooze.data.duration.scheduler.DurationAlarmScheduler
import hu.bbara.breakthesnooze.data.settings.repository.SettingsRepository
import hu.bbara.breakthesnooze.data.settings.repository.settingsDataStore
import hu.bbara.breakthesnooze.ui.alarm.domain.CreateDurationAlarmUseCase
import hu.bbara.breakthesnooze.ui.alarm.domain.DeleteDurationAlarmUseCase
import hu.bbara.breakthesnooze.ui.alarm.domain.SaveAlarmUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAlarmDatabase(@ApplicationContext context: Context): AlarmDatabase =
        AlarmDatabase.getInstance(context)

    @Provides
    fun provideAlarmDao(database: AlarmDatabase): AlarmDao = database.alarmDao()

    @Provides
    fun provideWakeEventDao(database: AlarmDatabase): WakeEventDao = database.wakeEventDao()

    @Provides
    fun provideDurationAlarmDao(database: AlarmDatabase): DurationAlarmDao = database.durationAlarmDao()

    @Provides
    @Singleton
    fun provideAlarmRepository(
        alarmDao: AlarmDao,
        wakeEventDao: WakeEventDao
    ): AlarmRepository = DefaultAlarmRepository(alarmDao, wakeEventDao)

    @Provides
    @Singleton
    fun provideDurationAlarmRepository(
        durationAlarmDao: DurationAlarmDao
    ): DurationAlarmRepository = DefaultDurationAlarmRepository(durationAlarmDao)

    @Provides
    @Singleton
    fun provideAlarmScheduler(@ApplicationContext context: Context): AlarmScheduler =
        AndroidAlarmScheduler(context)

    @Provides
    @Singleton
    fun provideDurationAlarmScheduler(@ApplicationContext context: Context): DurationAlarmScheduler =
        AndroidDurationAlarmScheduler(context)

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.settingsDataStore

    @Provides
    @Singleton
    fun provideSettingsRepository(dataStore: DataStore<Preferences>): SettingsRepository =
        SettingsRepository(dataStore)

    @Provides
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    fun provideSaveAlarmUseCase(
        repository: AlarmRepository,
        scheduler: AlarmScheduler
    ): SaveAlarmUseCase = SaveAlarmUseCase(repository, scheduler)

    @Provides
    fun provideCreateDurationAlarmUseCase(
        repository: DurationAlarmRepository,
        scheduler: DurationAlarmScheduler
    ): CreateDurationAlarmUseCase = CreateDurationAlarmUseCase(repository, scheduler)

    @Provides
    fun provideDeleteDurationAlarmUseCase(
        repository: DurationAlarmRepository,
        scheduler: DurationAlarmScheduler
    ): DeleteDurationAlarmUseCase = DeleteDurationAlarmUseCase(repository, scheduler)
}
