package hu.bbara.breakthesnooze.wear

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.compose.ui.tooling.preview.Preview
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainActivity : ComponentActivity() {

    private var overlayVisible by mutableStateOf(false)
    private var alarmId: Int = -1
    private var alarmLabel by mutableStateOf<String?>(null)
    private lateinit var messageClient: MessageClient
    private var isVibrating = false
    private val vibrator: Vibrator? by lazy { resolveVibrator() }
    private val messageListener = MessageClient.OnMessageReceivedListener { event ->
        Log.d(TAG, "Activity listener received path=${event.path}")
        if (event.path == PhoneMessageListenerService.MESSAGE_PATH) {
            val payload = WearAlarmPayload.fromBytes(event.data)
            val receivedId = payload?.alarmId ?: -1
            if (receivedId == -1) {
                Log.w(TAG, "Activity listener received invalid payload")
                return@OnMessageReceivedListener
            }
            runOnUiThread {
                overlayVisible = true
                alarmId = receivedId
                alarmLabel = payload?.label
                Log.d(TAG, "Overlay made visible from activity listener")
                updateWakeState()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setShowWhenLocked(true)
        setTurnScreenOn(true)
        OnBodyStatusMonitor.ensureInitialized(applicationContext)

        messageClient = Wearable.getMessageClient(this)

        overlayVisible = shouldShowOverlay(intent)
        alarmId = intent?.getIntExtra(EXTRA_ALARM_ID, -1) ?: -1
        alarmLabel = intent?.getStringExtra(EXTRA_ALARM_LABEL)
        Log.d(TAG, "onCreate overlayVisible=$overlayVisible action=${intent?.action} alarmId=$alarmId label=$alarmLabel")
        if (!overlayVisible) {
            alarmLabel = null
            finish()
            return
        }

        updateWakeState()

        setContent {
            MaterialTheme {
                OverlayScreen(
                    isStopEnabled = alarmId >= 0,
                    alarmLabel = alarmLabel,
                    onStop = ::stopAlarmFromWear
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
        alarmLabel = intent.getStringExtra(EXTRA_ALARM_LABEL)
        Log.d(TAG, "onNewIntent overlayVisible=$overlayVisible action=${intent.action} alarmId=$alarmId label=$alarmLabel")
        if (!overlayVisible) {
            alarmLabel = null
            finish()
            return
        }
        updateWakeState()
    }

    private fun stopAlarmFromWear() {
        lifecycleScope.launch {
            overlayVisible = false
            updateWakeState()

            val payload = alarmId.takeIf { it >= 0 }
                ?.toString()
                ?.toByteArray(Charsets.UTF_8)
                ?: ByteArray(0)

            val nodes = runCatching {
                Wearable.getNodeClient(this@MainActivity).connectedNodes.await()
            }.onFailure { error ->
                Log.w(TAG, "Failed to resolve connected nodes for watch stop acknowledgement", error)
            }.getOrNull().orEmpty()

            nodes.forEach { node ->
                runCatching {
                    messageClient.sendMessage(node.id, ACK_MESSAGE_PATH, payload).await()
                }.onSuccess {
                    Log.i(TAG, "Sent watch stop acknowledgement to node=${node.displayName}")
                }.onFailure { error ->
                    Log.w(TAG, "Failed to send watch stop acknowledgement to node=${node.displayName}", error)
                }
            }

            dismissOverlay()
        }
    }

    private fun dismissOverlay() {
        overlayVisible = false
        alarmLabel = null
        updateWakeState()
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
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        stopVibration()
        super.onDestroy()
    }

    private fun updateWakeState() {
        if (overlayVisible) {
            setTurnScreenOn(true)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            startVibration()
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
        const val ACTION_SHOW_OVERLAY = "hu.bbara.breakthesnooze.wear.action.SHOW_OVERLAY"
        const val EXTRA_ALARM_ID = "hu.bbara.breakthesnooze.wear.extra.ALARM_ID"
        const val EXTRA_ALARM_LABEL = "hu.bbara.breakthesnooze.wear.extra.ALARM_LABEL"
        private const val TAG = "WearMainActivity"
        private val VIBRATION_PATTERN = longArrayOf(0, 600, 400)
        private const val ACK_MESSAGE_PATH = "/breakthesnooze/alarm-ack"
    }
}

@Composable
private fun OverlayScreen(
    isStopEnabled: Boolean,
    alarmLabel: String?,
    onStop: () -> Unit
) {
    val displayLabel = alarmLabel?.takeIf { it.isNotBlank() }
        ?: stringResource(id = R.string.overlay_alarm_default_label)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1C1C1C)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CurrentTimeText()
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = displayLabel,
                style = MaterialTheme.typography.title2,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onStop,
                enabled = isStopEnabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(id = R.string.overlay_stop),
                    style = MaterialTheme.typography.title3,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun CurrentTimeText(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val is24HourFormat = remember(context) { DateFormat.is24HourFormat(context) }
    val timeFormatter = remember(is24HourFormat) {
        DateTimeFormatter.ofPattern(if (is24HourFormat) "HH:mm" else "h:mm a")
    }
    var currentTime by remember { mutableStateOf(LocalTime.now()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = LocalTime.now()
            delay(1_000L)
        }
    }

    Text(
        text = currentTime.format(timeFormatter),
        style = MaterialTheme.typography.display1,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth()
    )
}

@Preview(showBackground = true)
@Composable
private fun OverlayScreenPreview() {
    MaterialTheme {
        OverlayScreen(
            isStopEnabled = true,
            alarmLabel = "Morning alarm",
            onStop = {}
        )
    }
}
