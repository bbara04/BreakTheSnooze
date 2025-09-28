package hu.bbara.viewideas.alarm

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.IBinder
import hu.bbara.viewideas.data.alarm.AlarmRepositoryProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AlarmRingtoneService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var mediaPlayer: MediaPlayer? = null
    private var currentAlarmId: Int? = null
    private var playbackJob: kotlinx.coroutines.Job? = null
    private var isPaused: Boolean = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val alarmId = intent?.getIntExtra(AlarmIntents.EXTRA_ALARM_ID, -1) ?: -1
        if (alarmId == -1) {
            stopSelf()
            return START_NOT_STICKY
        }

        when (action) {
            AlarmIntents.ACTION_STOP_ALARM -> {
                stopAlarm(alarmId)
                return START_NOT_STICKY
            }

            AlarmIntents.ACTION_PAUSE_ALARM -> {
                pauseAlarm(alarmId)
            }

            AlarmIntents.ACTION_RESUME_ALARM -> {
                resumeAlarm(alarmId)
            }

            AlarmIntents.ACTION_ALARM_FIRED, null -> {
                startAlarm(alarmId)
            }
        }

        return START_STICKY
    }

    private fun startAlarm(alarmId: Int) {
        if (currentAlarmId == alarmId && mediaPlayer?.isPlaying == true) {
            return
        }
        currentAlarmId = alarmId
        isPaused = false

        playbackJob?.cancel()
        playbackJob = serviceScope.launch {
            val repository = AlarmRepositoryProvider.getRepository(applicationContext)
            val alarm = repository.getAlarmById(alarmId)

            if (alarm == null) {
                stopSelf()
                return@launch
            }

            val notification = withContext(Dispatchers.Default) {
                AlarmNotifications.buildNotification(applicationContext, alarm)
            }
            startForeground(AlarmNotifications.notificationId(alarmId), notification)
            launchRingingActivity(alarmId)
            startPlayback(alarm.soundUri)
        }
    }

    private fun launchRingingActivity(alarmId: Int) {
        val intent = hu.bbara.viewideas.ui.alarm.AlarmRingingActivity.createIntent(
            applicationContext,
            alarmId
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(intent) }
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
        stopPlayback()
        playbackJob?.cancel()
        playbackJob = null
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        currentAlarmId = null
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
        super.onDestroy()
    }
}
