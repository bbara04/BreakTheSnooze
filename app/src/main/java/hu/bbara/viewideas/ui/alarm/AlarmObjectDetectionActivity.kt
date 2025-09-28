package hu.bbara.viewideas.ui.alarm

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import hu.bbara.objectdetection.MainScreen
import hu.bbara.viewideas.alarm.AlarmIntents
import hu.bbara.viewideas.alarm.AlarmRingtoneService
import hu.bbara.viewideas.ui.theme.ViewIdeasTheme

class AlarmObjectDetectionActivity : ComponentActivity() {

    private var alarmId: Int = -1
    private var detectionSatisfied: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        alarmId = intent?.getIntExtra(EXTRA_ALARM_ID, -1) ?: -1
        if (alarmId == -1) {
            finish()
            return
        }

        setResult(Activity.RESULT_CANCELED)
        sendAlarmCommand(AlarmIntents.ACTION_PAUSE_ALARM)

        setContent {
            ViewIdeasTheme {
                MainScreen(
                    modifier = Modifier.fillMaxSize(),
                    targetLabel = TARGET_LABEL,
                    confidenceThreshold = DETECTION_THRESHOLD,
                    onDetectionSuccess = { onDetectionSuccess() },
                    onCancel = { finish() }
                )
            }
        }
    }

    override fun onDestroy() {
        if (!detectionSatisfied) {
            sendAlarmCommand(AlarmIntents.ACTION_RESUME_ALARM)
        }
        super.onDestroy()
    }

    private fun onDetectionSuccess() {
        if (detectionSatisfied) return
        detectionSatisfied = true
        setResult(Activity.RESULT_OK)
        sendAlarmCommand(AlarmIntents.ACTION_STOP_ALARM)
        finish()
    }

    private fun sendAlarmCommand(action: String) {
        if (alarmId == -1) return
        val intent = Intent(this, AlarmRingtoneService::class.java).apply {
            this.action = action
            putExtra(AlarmIntents.EXTRA_ALARM_ID, alarmId)
        }
        ContextCompat.startForegroundService(this, intent)
    }

    companion object {
        private const val EXTRA_ALARM_ID = "alarmObjectDetectionId"
        private const val TARGET_LABEL = "toothbrush"
        private const val DETECTION_THRESHOLD = 0.6f

        fun createIntent(context: Context, alarmId: Int): Intent {
            return Intent(context, AlarmObjectDetectionActivity::class.java).apply {
                putExtra(EXTRA_ALARM_ID, alarmId)
            }
        }
    }
}
