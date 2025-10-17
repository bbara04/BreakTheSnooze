package hu.bbara.viewideas.wear

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable

class MainActivity : ComponentActivity() {

    private var overlayVisible by mutableStateOf(false)
    private var alarmId: Int = -1
    private lateinit var messageClient: MessageClient
    private var isVibrating = false
    private val vibrator: Vibrator? by lazy { resolveVibrator() }
    private val messageListener = MessageClient.OnMessageReceivedListener { event ->
        Log.d(TAG, "Activity listener received path=${event.path}")
        if (event.path == PhoneMessageListenerService.MESSAGE_PATH) {
            val receivedId = event.data?.decodeToString()?.toIntOrNull() ?: -1
            runOnUiThread {
                overlayVisible = true
                alarmId = receivedId
                Log.d(TAG, "Overlay made visible from activity listener")
                updateVibrationState()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setShowWhenLocked(true)
        setTurnScreenOn(true)

        messageClient = Wearable.getMessageClient(this)

        overlayVisible = shouldShowOverlay(intent)
        alarmId = intent?.getIntExtra(EXTRA_ALARM_ID, -1) ?: -1
        Log.d(TAG, "onCreate overlayVisible=$overlayVisible action=${intent?.action} alarmId=$alarmId")
        if (!overlayVisible) {
            finish()
            return
        }

        cancelOverlayNotification()
        updateVibrationState()

        setContent {
            MaterialTheme {
                OverlayScreen(
                    isStopEnabled = alarmId >= 0,
                    onStop = ::dismissOverlay
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        messageClient.addListener(messageListener)
        Log.d(TAG, "Message listener registered")
    }

    override fun onStop() {
        messageClient.removeListener(messageListener)
        Log.d(TAG, "Message listener removed")
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        overlayVisible = shouldShowOverlay(intent)
        alarmId = intent.getIntExtra(EXTRA_ALARM_ID, -1)
        Log.d(TAG, "onNewIntent overlayVisible=$overlayVisible action=${intent.action} alarmId=$alarmId")
        if (!overlayVisible) {
            finish()
            return
        }
        cancelOverlayNotification()
        updateVibrationState()
    }

    private fun dismissOverlay() {
        overlayVisible = false
        updateVibrationState()
        finish()
        Log.d(TAG, "dismissOverlay invoked")
        cancelOverlayNotification()
    }

    private fun shouldShowOverlay(intent: Intent?): Boolean {
        return intent?.action == ACTION_SHOW_OVERLAY
    }

    private fun cancelOverlayNotification() {
        NotificationManagerCompat.from(this).cancel(PhoneMessageListenerService.NOTIFICATION_ID)
    }

    override fun onDestroy() {
        stopVibration()
        super.onDestroy()
    }

    private fun updateVibrationState() {
        if (overlayVisible) {
            startVibration()
        } else {
            stopVibration()
        }
    }

    private fun startVibration() {
        if (isVibrating) {
            return
        }
        val resolvedVibrator = vibrator
        if (resolvedVibrator == null) {
            Log.w(TAG, "No vibrator service available")
            return
        }
        if (!resolvedVibrator.hasVibrator()) {
            Log.w(TAG, "Device does not support vibration")
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createWaveform(VIBRATION_PATTERN, 0)
            resolvedVibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            resolvedVibrator.vibrate(VIBRATION_PATTERN, 0)
        }
        isVibrating = true
        Log.d(TAG, "Alarm vibration started")
    }

    private fun stopVibration() {
        if (!isVibrating) {
            return
        }
        vibrator?.cancel()
        isVibrating = false
        Log.d(TAG, "Alarm vibration stopped")
    }

    private fun resolveVibrator(): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    companion object {
        const val ACTION_SHOW_OVERLAY = "hu.bbara.viewideas.wear.action.SHOW_OVERLAY"
        const val EXTRA_ALARM_ID = "hu.bbara.viewideas.wear.extra.ALARM_ID"
        private const val TAG = "WearMainActivity"
        private val VIBRATION_PATTERN = longArrayOf(0, 600, 400)
    }
}

@Composable
private fun OverlayScreen(
    isStopEnabled: Boolean,
    onStop: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1C1C1C)),
        contentAlignment = Alignment.Center
    ) {
        Button(
            onClick = onStop,
            enabled = isStopEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Text(
                text = stringResource(id = R.string.overlay_stop),
                style = MaterialTheme.typography.title3,
                textAlign = TextAlign.Center
            )
        }
    }
}
