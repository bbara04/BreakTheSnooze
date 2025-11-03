package hu.bbara.breakthesnooze.wear

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

object WearAlarmMessenger {

    private const val MESSAGE_PATH_ALARM_STARTED = "/breakthesnooze/alarm-started"
    private const val MESSAGE_PATH_ON_BODY_QUERY = "/breakthesnooze/onbody-query"
    private const val MESSAGE_PATH_ON_BODY_RESPONSE = "/breakthesnooze/onbody-response"
    private const val TAG = "WearAlarmMessenger"
    private const val ATTRIBUTION_TAG = "wear_alarm_bridge"
    private const val ON_BODY_RESPONSE_TIMEOUT_MS = 10_000L

    suspend fun notifyAlarmStarted(context: Context, alarmId: Int) {
        val appContext = prepareContext(context)
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

    suspend fun hasConnectedWearDevice(context: Context): Boolean {
        val appContext = prepareContext(context)
        val nodes = getConnectedNodes(appContext) ?: return false
        val hasConnectedNode = nodes.isNotEmpty()
        Log.d(TAG, "hasConnectedWearDevice result=$hasConnectedNode (nodes=${nodes.size})")
        return hasConnectedNode
    }

    suspend fun isWearDeviceOnBody(context: Context): Boolean? {
        val appContext = prepareContext(context)
        val nodes = getConnectedNodes(appContext) ?: return null
        val targetNode = nodes.firstOrNull() ?: return null
        val messageClient = Wearable.getMessageClient(appContext)
        Log.d(TAG, "Requesting on-body state from node=${targetNode.displayName}")

        val result = withTimeoutOrNull(ON_BODY_RESPONSE_TIMEOUT_MS) {
            suspendCancellableCoroutine<Boolean?> { continuation ->
                var listener: MessageClient.OnMessageReceivedListener? = null

                fun cleanup() {
                    listener?.let { messageClient.removeListener(it) }
                    listener = null
                }

                listener = MessageClient.OnMessageReceivedListener { event ->
                    if (event.path != MESSAGE_PATH_ON_BODY_RESPONSE) {
                        return@OnMessageReceivedListener
                    }
                    val value = parseOnBodyResponse(event)
                    Log.d(TAG, "Received on-body response=$value from node=${event.sourceNodeId}")
                    cleanup()
                    if (continuation.isActive) {
                        continuation.resume(value)
                    }
                }

                continuation.invokeOnCancellation { cleanup() }

                val registeredListener = listener
                if (registeredListener == null) {
                    cleanup()
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                    return@suspendCancellableCoroutine
                }

                messageClient.addListener(registeredListener)
                    .addOnSuccessListener {
                        if (!continuation.isActive) {
                            cleanup()
                            return@addOnSuccessListener
                        }
                        messageClient.sendMessage(targetNode.id, MESSAGE_PATH_ON_BODY_QUERY, ByteArray(0))
                            .addOnFailureListener { error ->
                                Log.w(TAG, "Failed to request on-body state", error)
                                cleanup()
                                if (continuation.isActive) {
                                    continuation.resume(null)
                                }
                            }
                    }
                    .addOnFailureListener { error ->
                        Log.w(TAG, "Failed to add on-body response listener", error)
                        cleanup()
                        if (continuation.isActive) {
                            continuation.resume(null)
                        }
                    }
            }
        }
        Log.d(TAG, "isWearDeviceOnBody result=$result")
        return result
    }

    private suspend fun getConnectedNodes(context: Context) = runCatching {
        Wearable.getNodeClient(context).connectedNodes.await()
    }.onFailure { error ->
        if (error is CancellationException) throw error
        Log.w(TAG, "Unable to query connected wear nodes", error)
    }.getOrNull()

    private fun parseOnBodyResponse(event: MessageEvent): Boolean? {
        val payload = event.data?.decodeToString() ?: return null
        return when (payload) {
            "1" -> true
            "0" -> false
            else -> null
        }
    }

    private fun prepareContext(context: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val attrContext = runCatching { context.createAttributionContext(ATTRIBUTION_TAG) }
                .onFailure { error ->
                    Log.w(TAG, "Failed to create attribution context", error)
                }
                .getOrNull()
            if (attrContext != null) {
                return attrContext.applicationContext ?: attrContext
            }
        }
        return context.applicationContext ?: context
    }
}
