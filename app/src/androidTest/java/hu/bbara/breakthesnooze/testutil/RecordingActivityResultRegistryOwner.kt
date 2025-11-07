package hu.bbara.breakthesnooze.testutil

import android.content.Intent
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat

class RecordingActivityResultRegistryOwner : ActivityResultRegistryOwner {
    val registry = RecordingActivityResultRegistry()
    override val activityResultRegistry: ActivityResultRegistry
        get() = registry
}

class RecordingActivityResultRegistry : ActivityResultRegistry() {
    data class Launch(val requestCode: Int, val input: Any?)

    private var lastRequestCode: Int? = null
    val launches = mutableListOf<Launch>()

    override fun <I, O> onLaunch(
        requestCode: Int,
        contract: ActivityResultContract<I, O>,
        input: I,
        options: ActivityOptionsCompat?
    ) {
        launches += Launch(requestCode, input)
        lastRequestCode = requestCode
    }

    fun dispatchResult(resultCode: Int, data: Intent? = null) {
        val requestCode = lastRequestCode ?: error("No pending ActivityResult launch")
        dispatchResult(requestCode, resultCode, data)
        lastRequestCode = null
    }

    fun hasPendingLaunch(): Boolean = lastRequestCode != null
}
