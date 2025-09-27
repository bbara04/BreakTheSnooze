package hu.bbara.viewideas.alarm

import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
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

        serviceScope.launch {
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
            startPlayback()
        }
    }

    private fun startPlayback() {
        stopPlayback()
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
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
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        currentAlarmId = null
        NotificationManagerCompat.from(this).cancel(AlarmNotifications.notificationId(alarmId))
        sendBroadcast(Intent(AlarmIntents.ACTION_ALARM_DISMISSED).apply {
            setPackage(packageName)
            putExtra(AlarmIntents.EXTRA_ALARM_ID, alarmId)
        })
        stopSelf()
    }

    private fun stopPlayback() {
        mediaPlayer?.let { player ->
            runCatching {
                if (player.isPlaying) player.stop()
            }
            player.release()
        }
        mediaPlayer = null
    }

    override fun onDestroy() {
        stopPlayback()
        super.onDestroy()
    }
}
