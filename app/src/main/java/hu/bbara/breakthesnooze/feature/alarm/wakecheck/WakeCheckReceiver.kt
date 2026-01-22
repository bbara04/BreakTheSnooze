package hu.bbara.breakthesnooze.feature.alarm.wakecheck

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import hu.bbara.breakthesnooze.feature.alarm.service.AlarmIntents
import hu.bbara.breakthesnooze.feature.alarm.service.AlarmService

class WakeCheckReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val alarmId = intent?.getIntExtra(WakeCheckIntents.EXTRA_ALARM_ID, -1) ?: -1
        if (alarmId == -1) return
        when (intent?.action) {
            WakeCheckIntents.ACTION_SHOW_WAKE_CHECK -> handleShow(context, alarmId)
            WakeCheckIntents.ACTION_ACK_WAKE_CHECK -> handleAck(context, alarmId)
            WakeCheckIntents.ACTION_WAKE_CHECK_FALLBACK -> handleFallback(context, alarmId)
        }
    }

    private fun handleShow(context: Context, alarmId: Int) {
        val payload = WakeCheckStorage(context).get(alarmId)
        if (payload == null) {
            Log.d(TAG, "Wake-check show skipped; payload missing for alarmId=$alarmId")
            WakeCheckScheduler(context).cancel(alarmId)
            return
        }
        WakeCheckNotifications.showWakeCheck(context, payload)
        WakeCheckScheduler(context).scheduleFallback(alarmId)
    }

    private fun handleAck(context: Context, alarmId: Int) {
        Log.i(TAG, "Wake-check acknowledged for alarmId=$alarmId")
        WakeCheckNotifications.dismiss(context, alarmId)
        WakeCheckScheduler(context).cancel(alarmId)
    }

    private fun handleFallback(context: Context, alarmId: Int) {
        val storage = WakeCheckStorage(context)
        val payload = storage.get(alarmId)
        Log.w(TAG, "Wake-check fallback triggered for alarmId=$alarmId (payload exists=${payload != null})")
        WakeCheckNotifications.dismiss(context, alarmId)
        WakeCheckScheduler(context).cancel(alarmId)
        val restartIntent = Intent(context, AlarmService::class.java).apply {
            action = AlarmIntents.ACTION_WAKE_CHECK_RESTART
            putExtra(AlarmIntents.EXTRA_ALARM_ID, alarmId)
            payload?.let { putExtra(WakeCheckIntents.EXTRA_WAKE_CHECK_PAYLOAD, it.toJson()) }
        }
        ContextCompat.startForegroundService(context, restartIntent)
    }

    companion object {
        private const val TAG = "WakeCheckReceiver"
    }
}
