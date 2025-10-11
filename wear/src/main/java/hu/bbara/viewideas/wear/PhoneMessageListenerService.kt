package hu.bbara.viewideas.wear

import android.content.Intent
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class PhoneMessageListenerService : WearableListenerService() {

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d(TAG, "onMessageReceived path=${messageEvent.path} size=${messageEvent.data?.size ?: 0}")
        if (messageEvent.path == MESSAGE_PATH) {
            val overlayIntent = Intent(this, WearOverlayActivity::class.java).apply {
                action = WearOverlayActivity.ACTION_SHOW_OVERLAY
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            kotlin.runCatching { startActivity(overlayIntent) }
                .onSuccess { Log.d(TAG, "Started WearOverlayActivity from message") }
                .onFailure { Log.w(TAG, "Failed to start WearOverlayActivity", it) }
        } else {
            Log.w(TAG, "Ignoring message for unexpected path=${messageEvent.path}")
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        super.onDestroy()
    }

    companion object {
        const val MESSAGE_PATH = "/viewideas/alarm-started"
        private const val TAG = "WearPhoneListener"
    }
}
