package hu.bbara.breakthesnooze.di

import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import hu.bbara.breakthesnooze.data.alarm.repository.AlarmRepository
import hu.bbara.breakthesnooze.data.alarm.scheduler.AlarmScheduler
import hu.bbara.breakthesnooze.data.duration.repository.DurationAlarmRepository
import hu.bbara.breakthesnooze.data.duration.scheduler.DurationAlarmScheduler
import hu.bbara.breakthesnooze.data.settings.repository.SettingsRepository
import hu.bbara.breakthesnooze.ui.alarm.domain.CreateDurationAlarmUseCase
import hu.bbara.breakthesnooze.ui.alarm.domain.DeleteDurationAlarmUseCase
import hu.bbara.breakthesnooze.ui.alarm.domain.SaveAlarmUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [AppModule::class]
)
object TestAppModule {

    @Provides
    fun provideAlarmRepository(): AlarmRepository = TestAppModuleBindings.alarmRepository

    @Provides
    fun provideDurationAlarmRepository(): DurationAlarmRepository = TestAppModuleBindings.durationAlarmRepository

    @Provides
    fun provideAlarmScheduler(): AlarmScheduler = TestAppModuleBindings.alarmScheduler

    @Provides
    fun provideDurationAlarmScheduler(): DurationAlarmScheduler = TestAppModuleBindings.durationAlarmScheduler

    @Provides
    fun provideSettingsRepository(): SettingsRepository = TestAppModuleBindings.settingsRepository

    @Provides
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.Unconfined

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

object TestAppModuleBindings {
    lateinit var alarmRepository: AlarmRepository
    lateinit var durationAlarmRepository: DurationAlarmRepository
    lateinit var alarmScheduler: AlarmScheduler
    lateinit var durationAlarmScheduler: DurationAlarmScheduler
    lateinit var settingsRepository: SettingsRepository
}
