package hu.bbara.breakthesnooze.ui.alarm.dismiss

import androidx.annotation.StringRes
import hu.bbara.breakthesnooze.R

enum class AlarmDismissTaskType(val storageKey: String, @StringRes val optionLabelResId: Int) {
    OBJECT_DETECTION("object_detection", R.string.alarm_scan_object),
    MATH_CHALLENGE("math_challenge", R.string.alarm_math_challenge),
    QR_BARCODE_SCAN("qr_barcode_scan", R.string.alarm_qr_barcode_scan),
    FOCUS_TIMER("focus_timer", R.string.alarm_focus_task),
    MEMORY("memory", R.string.alarm_memory_task);

    companion object {
        val DEFAULT: AlarmDismissTaskType = MATH_CHALLENGE

        fun fromStorageKey(storageKey: String?): AlarmDismissTaskType {
            if (storageKey.isNullOrBlank()) return DEFAULT
            return values().firstOrNull { it.storageKey == storageKey } ?: DEFAULT
        }
    }
}

fun AlarmDismissTaskType.createTask(
    showDebugOverlay: Boolean = false,
    qrBarcodeValue: String? = null,
    qrRequiredUniqueCount: Int = 0
): AlarmDismissTask = when (this) {
    AlarmDismissTaskType.OBJECT_DETECTION -> ObjectDetectionDismissTask(showDebugOverlay = showDebugOverlay)
    AlarmDismissTaskType.MATH_CHALLENGE -> MathChallengeDismissTask()
    AlarmDismissTaskType.QR_BARCODE_SCAN -> QrBarcodeScanDismissTask(
        expectedValue = qrBarcodeValue,
        requiredUniqueCount = qrRequiredUniqueCount
    )
    AlarmDismissTaskType.FOCUS_TIMER -> FocusTimerDismissTask()
    AlarmDismissTaskType.MEMORY -> MemoryDismissTask()
}
