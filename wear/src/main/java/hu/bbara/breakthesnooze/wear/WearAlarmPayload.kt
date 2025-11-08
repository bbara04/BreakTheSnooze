package hu.bbara.breakthesnooze.wear

import android.util.Log
import org.json.JSONObject

data class WearAlarmPayload(
    val alarmId: Int,
    val label: String?
) {
    companion object {
        private const val TAG = "WearAlarmPayload"

        fun fromBytes(data: ByteArray?): WearAlarmPayload? {
            if (data == null) {
                Log.w(TAG, "Missing payload bytes")
                return null
            }
            val payload = runCatching { data.decodeToString() }
                .onFailure { error ->
                    Log.w(TAG, "Failed to decode payload bytes", error)
                }
                .getOrNull() ?: return null
            return fromString(payload)
        }

        private fun fromString(raw: String): WearAlarmPayload? {
            val trimmed = raw.trim()
            val jsonPayload = runCatching {
                val json = JSONObject(trimmed)
                val id = json.optInt("id", -1)
                if (id == -1) {
                    null
                } else {
                    val label = if (json.has("label")) json.optString("label") else null
                    WearAlarmPayload(id, label?.takeIf { it.isNotBlank() })
                }
            }.getOrNull()
            if (jsonPayload != null) {
                return jsonPayload
            }
            val fallbackId = trimmed.toIntOrNull()
            if (fallbackId != null) {
                Log.d(TAG, "Parsed legacy payload for alarmId=$fallbackId")
                return WearAlarmPayload(fallbackId, null)
            }
            Log.w(TAG, "Unable to parse wear alarm payload=$trimmed")
            return null
        }
    }
}
