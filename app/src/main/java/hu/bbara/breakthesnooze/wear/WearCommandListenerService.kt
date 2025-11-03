package hu.bbara.breakthesnooze.wear

import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import hu.bbara.breakthesnooze.alarm.AlarmIntents
import hu.bbara.breakthesnooze.alarm.AlarmRingtoneService

class WearCommandListenerService : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            MESSAGE_PATH_ALARM_STOP -> handleStopCommand(messageEvent)
            MESSAGE_PATH_ALARM_ACK -> handleAck(messageEvent)
        }
    }

    companion object {
        private const val TAG = "WearCommandListener"
        private const val MESSAGE_PATH_ALARM_STOP = "/breakthesnooze/alarm-stop"
        private const val MESSAGE_PATH_ALARM_ACK = "/breakthesnooze/alarm-ack"
    }

    private fun handleStopCommand(messageEvent: MessageEvent) {
        val alarmId = messageEvent.data?.decodeToString()?.toIntOrNull() ?: -1
        Log.i(TAG, "Received stop command from wear for alarmId=$alarmId")
        val stopIntent = Intent(applicationContext, AlarmRingtoneService::class.java).apply {
            action = AlarmIntents.ACTION_STOP_ALARM
            putExtra(AlarmIntents.EXTRA_ALARM_ID, alarmId)
        }
        ContextCompat.startForegroundService(applicationContext, stopIntent)
    }

    private fun handleAck(messageEvent: MessageEvent) {
        val alarmId = messageEvent.data?.decodeToString()?.toIntOrNull() ?: -1
        Log.i(TAG, "Received watch acknowledgement for alarmId=$alarmId")
        val ackIntent = Intent(applicationContext, AlarmRingtoneService::class.java).apply {
            action = AlarmIntents.ACTION_WEAR_ACK
            putExtra(AlarmIntents.EXTRA_ALARM_ID, alarmId)
        }
        ContextCompat.startForegroundService(applicationContext, ackIntent)
    }
}
