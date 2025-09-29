package hu.bbara.viewideas.ui.alarm.dismiss

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

interface AlarmDismissTask {
    val id: String
    @get:StringRes
    val labelResId: Int

    @Composable
    fun Content(
        modifier: Modifier,
        onCompleted: () -> Unit,
        onCancelled: () -> Unit
    )
}
