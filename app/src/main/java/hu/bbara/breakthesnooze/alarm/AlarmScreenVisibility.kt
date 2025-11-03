package hu.bbara.breakthesnooze.alarm

import android.content.Context
import android.os.PowerManager
import androidx.core.content.getSystemService

internal fun shouldLaunchAlarmScreen(context: Context): Boolean {
    val appContext = context.applicationContext
    val powerManager = appContext.getSystemService<PowerManager>()
    val isScreenOn = powerManager?.isInteractive ?: true
    return !isScreenOn
}
