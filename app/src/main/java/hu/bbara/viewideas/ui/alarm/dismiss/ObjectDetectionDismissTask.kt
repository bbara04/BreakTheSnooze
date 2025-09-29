package hu.bbara.viewideas.ui.alarm.dismiss

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import hu.bbara.viewideas.R
import hu.bbara.viewideas.objectdetection.ObjectDetectionScreen

class ObjectDetectionDismissTask(
    private val showDebugOverlay: Boolean = false
) : AlarmDismissTask {
    override val id: String = "object_detection"
    override val labelResId: Int = R.string.alarm_scan_object

    @Composable
    override fun Content(
        modifier: Modifier,
        onCompleted: () -> Unit,
        onCancelled: () -> Unit
    ) {
        ObjectDetectionScreen(
            modifier = modifier,
            onDetectionSuccess = onCompleted,
            onCancel = onCancelled,
            autoRequestPermission = false,
            showDebugOverlay = showDebugOverlay
        )
    }
}
