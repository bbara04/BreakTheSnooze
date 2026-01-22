package hu.bbara.breakthesnooze.feature.alarm.wakecheck

import android.content.Context

class WakeCheckStorage(private val context: Context) {

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun save(payload: WakeCheckPayload) {
        prefs.edit().putString(key(payload.alarmId), payload.toJson()).apply()
    }

    fun get(alarmId: Int): WakeCheckPayload? {
        return WakeCheckPayload.fromJson(prefs.getString(key(alarmId), null))
    }

    fun remove(alarmId: Int) {
        prefs.edit().remove(key(alarmId)).apply()
    }

    private fun key(alarmId: Int): String = "$KEY_PREFIX$alarmId"

    companion object {
        private const val PREFS_NAME = "wake_check_prefs"
        private const val KEY_PREFIX = "payload_"
    }
}
