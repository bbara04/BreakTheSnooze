package hu.bbara.viewideas.wear

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

object WearAlarmMessenger {

    private const val MESSAGE_PATH_ALARM_STARTED = "/viewideas/alarm-started"
    private const val TAG = "WearAlarmMessenger"
    private const val ATTRIBUTION_TAG = "wear_alarm_bridge"

    suspend fun notifyAlarmStarted(context: Context, alarmId: Int) {
        val attrContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { context.createAttributionContext(ATTRIBUTION_TAG) }
                .onFailure { error ->
                    Log.w(TAG, "Failed to create attribution context", error)
                }
                .getOrNull() ?: context
        } else {
            context
        }
        val appContext = attrContext.applicationContext ?: attrContext
        Log.d(TAG, "notifyAlarmStarted for alarmId=$alarmId")
        val nodes = getConnectedNodes(appContext) ?: run {
            Log.w(TAG, "No connected nodes (null list) for alarmId=$alarmId")
            return
        }
        if (nodes.isEmpty()) {
            Log.w(TAG, "No connected wear nodes for alarmId=$alarmId")
            return
        }
        Log.d(TAG, "Found ${nodes.size} connected node(s) for alarmId=$alarmId")

        val payload = alarmId.toString().toByteArray(Charsets.UTF_8)
        val messageClient = Wearable.getMessageClient(appContext)
        for (node in nodes) {
            Log.d(TAG, "Sending message to node=${node.displayName} (${node.id})")
            runCatching {
                messageClient.sendMessage(node.id, MESSAGE_PATH_ALARM_STARTED, payload).await()
            }.onFailure { error ->
                if (error is CancellationException) throw error
                Log.w(TAG, "Failed to send alarm start message to node ${node.displayName}", error)
            }.onSuccess {
                Log.i(TAG, "Sent alarm start message to node ${node.displayName}")
            }
        }
    }

    private suspend fun getConnectedNodes(context: Context) = runCatching {
        Wearable.getNodeClient(context).connectedNodes.await()
    }.onFailure { error ->
        if (error is CancellationException) throw error
        Log.w(TAG, "Unable to query connected wear nodes", error)
    }.getOrNull()
}
