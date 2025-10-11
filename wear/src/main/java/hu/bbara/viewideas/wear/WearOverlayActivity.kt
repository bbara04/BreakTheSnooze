package hu.bbara.viewideas.wear

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable

class WearOverlayActivity : ComponentActivity() {

    private var overlayVisible by mutableStateOf(false)
    private lateinit var messageClient: MessageClient
    private val messageListener = MessageClient.OnMessageReceivedListener { event ->
        Log.d(TAG, "Activity listener received path=${event.path}")
        if (event.path == PhoneMessageListenerService.MESSAGE_PATH) {
            runOnUiThread {
                overlayVisible = true
                Log.d(TAG, "Overlay made visible from activity listener")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setShowWhenLocked(true)
        setTurnScreenOn(true)

        messageClient = Wearable.getMessageClient(this)

        overlayVisible = isOverlayIntent(intent)
        Log.d(TAG, "onCreate overlayVisible=$overlayVisible action=${intent?.action}")

        setContent {
            MaterialTheme {
                Scaffold {
                    if (overlayVisible) {
                        OverlayScreen(onDismiss = ::dismissOverlay)
                    } else {
                        IdleScreen()
                    }
                }
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
        overlayVisible = isOverlayIntent(intent)
        Log.d(TAG, "onNewIntent overlayVisible=$overlayVisible action=${intent.action}")
    }

    private fun dismissOverlay() {
        overlayVisible = false
        finish()
        Log.d(TAG, "dismissOverlay invoked")
    }

    private fun isOverlayIntent(intent: Intent?): Boolean {
        return intent?.action == ACTION_SHOW_OVERLAY
    }

    companion object {
        const val ACTION_SHOW_OVERLAY = "hu.bbara.viewideas.wear.action.SHOW_OVERLAY"
        private const val TAG = "WearOverlayActivity"
    }
}

@Composable
private fun OverlayScreen(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1C1C1C)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = stringResource(id = R.string.overlay_title),
                style = MaterialTheme.typography.title3,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(id = R.string.overlay_message),
                style = MaterialTheme.typography.body2,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
            Button(
                modifier = Modifier.padding(top = 16.dp),
                onClick = onDismiss
            ) {
                Text(text = stringResource(id = R.string.overlay_dismiss))
            }
        }
    }
}

@Composable
private fun IdleScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(id = R.string.idle_message),
            style = MaterialTheme.typography.body2,
            textAlign = TextAlign.Center
        )
    }
}
