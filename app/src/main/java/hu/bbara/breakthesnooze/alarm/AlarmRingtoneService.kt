package hu.bbara.breakthesnooze.alarm

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.IBinder
import android.util.Log
import hu.bbara.breakthesnooze.data.alarm.AlarmKind
import hu.bbara.breakthesnooze.data.alarm.AlarmRepositoryProvider
import hu.bbara.breakthesnooze.data.alarm.detectAlarmKind
import hu.bbara.breakthesnooze.data.alarm.duration.DurationAlarmPlaybackStore
import hu.bbara.breakthesnooze.data.alarm.duration.DurationAlarmRepositoryProvider
import hu.bbara.breakthesnooze.data.alarm.duration.toAlarmUiModel
import hu.bbara.breakthesnooze.data.alarm.rawAlarmIdFromUnique
import hu.bbara.breakthesnooze.ui.alarm.AlarmUiModel
import hu.bbara.breakthesnooze.wear.WearAlarmMessenger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class AlarmRingtoneService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var mediaPlayer: MediaPlayer? = null
    private var currentAlarmId: Int? = null
    private var currentAlarm: AlarmUiModel? = null
    private var playbackJob: kotlinx.coroutines.Job? = null
    private var isPaused: Boolean = false
    private var wearNotificationSentForId: Int? = null
    private var wearFallbackJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val alarmId = intent?.getIntExtra(AlarmIntents.EXTRA_ALARM_ID, -1) ?: -1
        if (alarmId == -1) {
            Log.w(TAG, "onStartCommand without valid alarmId, stopping service")
            stopSelf()
            return START_NOT_STICKY
        }

        when (action) {
            AlarmIntents.ACTION_STOP_ALARM -> {
                Log.d(TAG, "Received stop command for alarmId=$alarmId")
                stopAlarm(alarmId)
                return START_NOT_STICKY
            }
            AlarmIntents.ACTION_WEAR_ACK -> {
                Log.d(TAG, "Received wear acknowledgement for alarmId=$alarmId")
                acknowledgeWear(alarmId)
            }

            AlarmIntents.ACTION_PAUSE_ALARM -> {
                Log.d(TAG, "Received pause command for alarmId=$alarmId")
                pauseAlarm(alarmId)
            }

            AlarmIntents.ACTION_RESUME_ALARM -> {
                Log.d(TAG, "Received resume command for alarmId=$alarmId")
                resumeAlarm(alarmId)
            }

            AlarmIntents.ACTION_ALARM_FIRED, null -> {
                Log.d(TAG, "Received fired command for alarmId=$alarmId")
                startAlarm(alarmId)
            }
        }

        return START_STICKY
    }

    private fun startAlarm(alarmId: Int) {
        if (currentAlarmId == alarmId && mediaPlayer?.isPlaying == true) {
            Log.d(TAG, "startAlarm ignored; already playing for alarmId=$alarmId")
            return
        }
        currentAlarmId = alarmId
        isPaused = false

        playbackJob?.cancel()
        wearFallbackJob?.cancel()
        wearFallbackJob = null
        playbackJob = serviceScope.launch {
            val alarm = resolveAlarm(alarmId)

            if (alarm == null) {
                Log.w(TAG, "No alarm found for alarmId=$alarmId, stopping service")
                currentAlarm = null
                stopSelf()
                return@launch
            }

            currentAlarm = alarm
            val notification = withContext(Dispatchers.Default) {
                AlarmNotifications.buildNotification(applicationContext, alarm)
            }
            startForeground(AlarmNotifications.notificationId(alarm.id), notification)
            Log.d(TAG, "Foreground notification started for alarmId=${alarm.id}")
            launchRingingActivity(alarm.id)
            val isWearConnected = withTimeoutOrNull(WEAR_HANDSHAKE_TIMEOUT_MS) {
                withContext(Dispatchers.IO) {
                    runCatching {
                        WearAlarmMessenger.hasConnectedWearDevice(applicationContext)
                    }.onFailure { error ->
                        if (error is CancellationException) throw error
                        Log.w(TAG, "Failed to determine wear connection state", error)
                    }.getOrDefault(false)
                }
            } ?: false

            val isWearOnBody = if (isWearConnected) {
                withContext(Dispatchers.IO) {
                    runCatching {
                        WearAlarmMessenger.isWearDeviceOnBody(applicationContext)
                    }.onFailure { error ->
                        if (error is CancellationException) throw error
                        Log.w(TAG, "Failed to resolve wear on-body state", error)
                    }.getOrNull()
                }
            } else {
                null
            }

            if (isWearConnected && isWearOnBody == true) {
                Log.i(TAG, "Wear device connected and on wrist; deferring phone ringtone for grace period")
                scheduleWearFallback(WEAR_GRACE_PERIOD_MS, "initial wear grace period")
            } else {
                if (isWearConnected) {
                    val reason = when (isWearOnBody) {
                        false -> "detected off wrist"
                        null -> "on-body state unknown"
                        else -> "state mismatch"
                    }
                    Log.i(TAG, "Wear device connected but $reason; starting phone ringtone immediately")
                } else {
                    Log.d(TAG, "No wear device connection; starting phone ringtone")
                }
                startPlayback(alarm.soundUri)
            }
            notifyWearDevice(alarm)
        }
    }

    private fun launchRingingActivity(alarmId: Int) {
        if (!shouldLaunchAlarmScreen(applicationContext)) {
            Log.d(TAG, "Skipping AlarmRingingActivity launch; screen is interactive")
            return
        }
        val intent = hu.bbara.breakthesnooze.ui.alarm.AlarmRingingActivity.createIntent(
            applicationContext,
            alarmId
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(intent) }
            .onSuccess { Log.d(TAG, "Launched AlarmRingingActivity for alarmId=$alarmId") }
            .onFailure { Log.w(TAG, "Failed to launch AlarmRingingActivity for alarmId=$alarmId", it) }
    }

    private fun startPlayback(soundUri: String?) {
        stopPlayback()
        val uri = soundUri?.let { runCatching { android.net.Uri.parse(it) }.getOrNull() }
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: return
        val mediaPlayer = MediaPlayer()
        mediaPlayer.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        mediaPlayer.isLooping = true
        runCatching {
            mediaPlayer.setDataSource(applicationContext, uri)
            mediaPlayer.prepare()
            mediaPlayer.start()
            this.mediaPlayer = mediaPlayer
        }.onFailure {
            mediaPlayer.release()
        }
    }

    private fun stopAlarm(alarmId: Int) {
        Log.d(TAG, "Stopping alarm for alarmId=$alarmId")
        stopPlayback()
        playbackJob?.cancel()
        playbackJob = null
        wearFallbackJob?.cancel()
        wearFallbackJob = null
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        currentAlarmId = null
        currentAlarm = null
        DurationAlarmPlaybackStore.remove(alarmId)
        wearNotificationSentForId = null
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.cancel(AlarmNotifications.notificationId(alarmId))
        sendBroadcast(Intent(AlarmIntents.ACTION_ALARM_DISMISSED).apply {
            setPackage(packageName)
            putExtra(AlarmIntents.EXTRA_ALARM_ID, alarmId)
        })
        stopSelf()
    }

    private fun pauseAlarm(alarmId: Int) {
        if (currentAlarmId != alarmId) return
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                isPaused = true
            }
        }
    }

    private fun resumeAlarm(alarmId: Int) {
        if (currentAlarmId != alarmId || !isPaused) return
        mediaPlayer?.let { player ->
            if (!player.isPlaying) {
                player.start()
                isPaused = false
            }
        }
    }

    private fun stopPlayback() {
        mediaPlayer?.let { player ->
            runCatching {
                if (player.isPlaying) player.stop()
            }
            player.release()
        }
        mediaPlayer = null
        isPaused = false
    }

    override fun onDestroy() {
        stopPlayback()
        playbackJob?.cancel()
        playbackJob = null
        wearFallbackJob?.cancel()
        wearFallbackJob = null
        currentAlarm = null
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
    }

    private suspend fun notifyWearDevice(alarm: AlarmUiModel) {
        val alarmId = alarm.id
        if (wearNotificationSentForId == alarmId) return
        Log.d(TAG, "notifyWearDevice invoked for alarmId=$alarmId")
        runCatching {
            WearAlarmMessenger.notifyAlarmStarted(
                context = this@AlarmRingtoneService,
                alarmId = alarmId,
                label = alarm.label
            )
        }.onSuccess {
            wearNotificationSentForId = alarmId
            Log.i(TAG, "Wear notification sent for alarmId=$alarmId")
        }.onFailure { error ->
            if (error is CancellationException) throw error
            Log.w(TAG, "Failed to notify wear device for alarmId=$alarmId", error)
        }
    }

    private fun scheduleWearFallback(delayMs: Long, description: String) {
        val alarmId = currentAlarmId
        if (alarmId == null) {
            Log.d(TAG, "Skipping wear fallback scheduling ($description); no active alarm id")
            return
        }
        val alarmSoundUri = currentAlarm?.soundUri
        wearFallbackJob?.cancel()
        wearFallbackJob = serviceScope.launch {
            Log.d(TAG, "Wear fallback ($description) scheduled in ${delayMs}ms for alarmId=$alarmId")
            delay(delayMs)
            if (currentAlarmId == alarmId && mediaPlayer?.isPlaying != true && !isPaused) {
                Log.i(TAG, "Wear fallback ($description) fired; starting phone ringtone for alarmId=$alarmId")
                val soundUri = currentAlarm?.soundUri ?: alarmSoundUri
                startPlayback(soundUri)
            } else {
                Log.d(TAG, "Wear fallback ($description) skipped; state changed for alarmId=$alarmId")
            }
            wearFallbackJob = null
        }
    }

    private fun acknowledgeWear(alarmId: Int) {
        if (currentAlarmId != alarmId) {
            Log.d(TAG, "Ignoring wear acknowledgement for stale alarmId=$alarmId current=$currentAlarmId")
            return
        }
        if (wearFallbackJob == null) {
            Log.d(TAG, "Wear acknowledgement received but no fallback pending for alarmId=$alarmId")
        } else {
            Log.d(TAG, "Wear acknowledgement received; replacing pending fallback for alarmId=$alarmId")
        }
        Log.i(
            TAG,
            "Wear acknowledgement received; scheduling phone ringtone after ${WEAR_POST_STOP_DELAY_MS / 1000} seconds for alarmId=$alarmId"
        )
        scheduleWearFallback(WEAR_POST_STOP_DELAY_MS, "post-watch-stop")
    }

    private suspend fun resolveAlarm(uniqueAlarmId: Int): AlarmUiModel? {
        return when (detectAlarmKind(uniqueAlarmId)) {
            AlarmKind.Standard -> {
                val repository = AlarmRepositoryProvider.getRepository(applicationContext)
                DurationAlarmPlaybackStore.remove(uniqueAlarmId)
                repository.getAlarmById(uniqueAlarmId)
            }

            AlarmKind.Duration -> {
                val repository = DurationAlarmRepositoryProvider.getRepository(applicationContext)
                val rawId = rawAlarmIdFromUnique(uniqueAlarmId)
                val alarm = repository.getById(rawId) ?: return null
                repository.delete(rawId)
                return alarm.toAlarmUiModel().also {
                    DurationAlarmPlaybackStore.put(it.id, it)
                }
            }
        }
    }

    companion object {
        private const val TAG = "AlarmRingtoneService"
        private const val WEAR_HANDSHAKE_TIMEOUT_MS = 2_000L
        private const val WEAR_GRACE_PERIOD_MS = 15_000L
        private const val WEAR_POST_STOP_DELAY_MS = 60_000L
    }
}
