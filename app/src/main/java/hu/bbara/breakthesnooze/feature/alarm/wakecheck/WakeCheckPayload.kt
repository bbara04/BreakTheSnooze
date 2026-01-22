package hu.bbara.breakthesnooze.feature.alarm.wakecheck

import hu.bbara.breakthesnooze.ui.alarm.dismiss.AlarmDismissTaskType
import hu.bbara.breakthesnooze.ui.alarm.model.AlarmUiModel
import hu.bbara.breakthesnooze.ui.alarm.model.timeFormatter
import org.json.JSONObject
import java.time.LocalTime

data class WakeCheckPayload(
    val alarmId: Int,
    val label: String,
    val time: String,
    val soundUri: String?,
    val dismissTaskKey: String,
    val qrBarcodeValue: String?,
    val qrRequiredUniqueCount: Int
) {
    fun toJson(): String {
        return JSONObject()
            .put(KEY_ALARM_ID, alarmId)
            .put(KEY_LABEL, label)
            .put(KEY_TIME, time)
            .put(KEY_SOUND_URI, soundUri)
            .put(KEY_DISMISS_TASK, dismissTaskKey)
            .put(KEY_QR_VALUE, qrBarcodeValue)
            .put(KEY_QR_COUNT, qrRequiredUniqueCount)
            .toString()
    }

    fun toAlarmUiModel(): AlarmUiModel {
        val parsedTime = runCatching { LocalTime.parse(time, timeFormatter) }
            .getOrElse { _: Throwable -> LocalTime.now() }
        return AlarmUiModel(
            id = alarmId,
            time = parsedTime,
            label = label,
            isActive = true,
            repeatDays = emptySet(),
            soundUri = soundUri,
            dismissTask = AlarmDismissTaskType.fromStorageKey(dismissTaskKey),
            qrBarcodeValue = qrBarcodeValue,
            qrRequiredUniqueCount = qrRequiredUniqueCount
        )
    }

    companion object {
        private const val KEY_ALARM_ID = "alarmId"
        private const val KEY_LABEL = "label"
        private const val KEY_TIME = "time"
        private const val KEY_SOUND_URI = "soundUri"
        private const val KEY_DISMISS_TASK = "dismissTask"
        private const val KEY_QR_VALUE = "qrBarcodeValue"
        private const val KEY_QR_COUNT = "qrRequiredUniqueCount"

        fun fromAlarm(alarm: AlarmUiModel): WakeCheckPayload {
            return WakeCheckPayload(
                alarmId = alarm.id,
                label = alarm.label,
                time = alarm.time.format(timeFormatter),
                soundUri = alarm.soundUri,
                dismissTaskKey = alarm.dismissTask.storageKey,
                qrBarcodeValue = alarm.qrBarcodeValue,
                qrRequiredUniqueCount = alarm.qrRequiredUniqueCount
            )
        }

        fun fromJson(raw: String?): WakeCheckPayload? {
            if (raw.isNullOrBlank()) return null
            return runCatching {
                val json = JSONObject(raw)
                WakeCheckPayload(
                    alarmId = json.getInt(KEY_ALARM_ID),
                    label = json.getString(KEY_LABEL),
                    time = json.getString(KEY_TIME),
                    soundUri = json.optString(KEY_SOUND_URI).takeIf { it.isNotBlank() },
                    dismissTaskKey = json.getString(KEY_DISMISS_TASK),
                    qrBarcodeValue = json.optString(KEY_QR_VALUE).takeIf { it.isNotBlank() },
                    qrRequiredUniqueCount = json.optInt(KEY_QR_COUNT, 0)
                )
            }.getOrNull()
        }
    }
}
