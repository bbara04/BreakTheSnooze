package hu.bbara.breakthesnooze.ui.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import hu.bbara.breakthesnooze.R
import hu.bbara.breakthesnooze.data.alarm.model.AlarmKind
import hu.bbara.breakthesnooze.data.alarm.model.detectAlarmKind
import hu.bbara.breakthesnooze.data.alarm.model.rawAlarmIdFromUnique
import hu.bbara.breakthesnooze.data.alarm.repository.AlarmRepositoryProvider
import hu.bbara.breakthesnooze.data.duration.model.DurationAlarmPlaybackStore
import hu.bbara.breakthesnooze.data.duration.model.toAlarmUiModel
import hu.bbara.breakthesnooze.data.duration.repository.DurationAlarmRepositoryProvider
import hu.bbara.breakthesnooze.data.settings.repository.SettingsRepositoryProvider
import hu.bbara.breakthesnooze.designsystem.BreakTheSnoozeTheme
import hu.bbara.breakthesnooze.feature.alarm.service.AlarmIntents
import hu.bbara.breakthesnooze.feature.alarm.service.AlarmService
import hu.bbara.breakthesnooze.ui.alarm.dismiss.AlarmDismissTask
import hu.bbara.breakthesnooze.ui.alarm.dismiss.AlarmDismissTaskType
import hu.bbara.breakthesnooze.ui.alarm.dismiss.FocusTimerDismissTask
import hu.bbara.breakthesnooze.ui.alarm.dismiss.MathChallengeDismissTask
import hu.bbara.breakthesnooze.ui.alarm.dismiss.createTask
import kotlinx.coroutines.launch
import java.time.Instant

class AlarmRingingActivity : ComponentActivity() {

    private var alarmId: Int = -1
    private var alarmKind: AlarmKind = AlarmKind.Standard
    private val alarmState: MutableState<AlarmUiModel?> = mutableStateOf(null)
    private var dismissalReceiver: BroadcastReceiver? = null
    private val availableTasks: MutableState<List<AlarmDismissTask>> = mutableStateOf(emptyList())
    private val taskTypeById: MutableState<Map<String, AlarmDismissTaskType>> = mutableStateOf(emptyMap())
    private val activeTask: MutableState<AlarmDismissTask?> = mutableStateOf(null)
    private val activeTaskType: MutableState<AlarmDismissTaskType?> = mutableStateOf(null)
    private var taskCompleted = false
    private val debugModeEnabled = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        alarmId = intent?.getIntExtra(EXTRA_ALARM_ID, -1) ?: -1
        if (alarmId == -1) {
            finish()
            return
        }
        alarmKind = detectAlarmKind(alarmId)

        configureWindow()
        wakeScreen()
        observeAlarm()
        observeSettings()
        registerDismissReceiver()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = stopAlarmAndFinish()
        })

        setContent {
            BreakTheSnoozeTheme {
                AlarmRingingScreen(
                    alarm = alarmState.value,
                    onStop = { stopAlarmAndFinish() },
                    tasks = availableTasks.value,
                    activeTask = activeTask.value,
                    onTaskSelected = { startTask(it) },
                    onTaskCompleted = { onTaskCompleted() },
                    onTaskCancelled = { cancelActiveTask() },
                    showDebugCancel = debugModeEnabled.value,
                    onDebugCancel = { stopAlarmAndFinish() }
                )
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
        }
    }

    override fun onDestroy() {
        dismissalReceiver?.let { unregisterReceiver(it) }
        dismissalReceiver = null
        if (activeTask.value != null && !taskCompleted) {
            sendAlarmCommand(AlarmIntents.ACTION_RESUME_ALARM)
        }
        super.onDestroy()
    }

    private fun observeAlarm() {
        lifecycleScope.launch {
            val alarm = when (alarmKind) {
                AlarmKind.Standard -> {
                    val repository = AlarmRepositoryProvider.getRepository(applicationContext)
                    repository.getAlarmById(alarmId)
                }

                AlarmKind.Duration -> {
                    val repository = DurationAlarmRepositoryProvider.getRepository(applicationContext)
                    val rawId = rawAlarmIdFromUnique(alarmId)
                    val resolved = repository.getById(rawId)?.toAlarmUiModel()
                    resolved ?: DurationAlarmPlaybackStore.get(alarmId)
                }
            }
            alarmState.value = alarm
            updateTasksForAlarm(alarm)
        }
    }

    private fun observeSettings() {
        lifecycleScope.launch {
            val settingsRepository = SettingsRepositoryProvider.getRepository(applicationContext)
            settingsRepository.settings.collect { settings ->
                debugModeEnabled.value = settings.debugModeEnabled
                updateTasksForAlarm(alarmState.value)
            }
        }
    }

    private fun updateTasksForAlarm(alarm: AlarmUiModel?) {
        val tasks = alarm?.let { buildTasksForAlarm(it) } ?: emptyList()
        availableTasks.value = tasks
        taskTypeById.value = alarm?.let { buildTaskTypeMap(it, tasks) } ?: emptyMap()
        val currentId = activeTask.value?.id
        if (currentId != null && tasks.none { it.id == currentId }) {
            activeTask.value = null
            activeTaskType.value = null
        }
    }

    private fun buildTasksForAlarm(alarm: AlarmUiModel): List<AlarmDismissTask> {
        val primary = alarm.dismissTask.createTask(
            showDebugOverlay = debugModeEnabled.value,
            qrBarcodeValue = alarm.qrBarcodeValue,
            qrRequiredUniqueCount = alarm.qrRequiredUniqueCount
        )
        val backup = FocusTimerDismissTask()
        return if (primary.id == backup.id) {
            listOf(primary)
        } else {
            listOf(primary, backup)
        }
    }

    private fun buildTaskTypeMap(
        alarm: AlarmUiModel,
        tasks: List<AlarmDismissTask>
    ): Map<String, AlarmDismissTaskType> {
        if (tasks.isEmpty()) return emptyMap()
        val mapping = mutableMapOf<String, AlarmDismissTaskType>()
        tasks.forEach { task ->
            val type = when (task) {
                is FocusTimerDismissTask -> AlarmDismissTaskType.FOCUS_TIMER
                else -> alarm.dismissTask
            }
            mapping[task.id] = type
        }
        return mapping
    }

    private fun stopAlarmAndFinish() {
        sendAlarmCommand(AlarmIntents.ACTION_STOP_ALARM)
        finish()
    }

    private fun startTask(task: AlarmDismissTask) {
        if (activeTask.value?.id == task.id) return
        sendAlarmCommand(AlarmIntents.ACTION_PAUSE_ALARM)
        taskCompleted = false
        activeTask.value = task
        activeTaskType.value = taskTypeById.value[task.id] ?: alarmState.value?.dismissTask
    }

    private fun cancelActiveTask() {
        if (activeTask.value == null) return
        activeTask.value = null
        sendAlarmCommand(AlarmIntents.ACTION_RESUME_ALARM)
        activeTaskType.value = null
    }

    private fun onTaskCompleted() {
        if (taskCompleted) return
        taskCompleted = true
        recordWakeEvent()
        sendAlarmCommand(AlarmIntents.ACTION_STOP_ALARM)
        finish()
    }

    private fun recordWakeEvent() {
        val alarm = alarmState.value ?: return
        lifecycleScope.launch {
            val taskType = activeTaskType.value ?: alarm.dismissTask
            val repository = AlarmRepositoryProvider.getRepository(applicationContext)
            repository.addWakeEvent(
                alarmId = alarm.id,
                alarmLabel = alarm.label,
                dismissTask = taskType,
                completedAt = Instant.now()
            )
        }
    }

    private fun sendAlarmCommand(action: String) {
        val intent = Intent(this, AlarmService::class.java).apply {
            this.action = action
            putExtra(AlarmIntents.EXTRA_ALARM_ID, alarmId)
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun configureWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView)?.let { controller ->
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.navigationBars())
        }
    }

    private fun wakeScreen() {
        val powerManager = ContextCompat.getSystemService(this, PowerManager::class.java) ?: return
        @Suppress("DEPRECATION")
        val wakeLock = powerManager.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
            "$packageName:AlarmWakeLock"
        )
        runCatching { wakeLock.acquire(5_000) }
            .onFailure { wakeLock.releaseIfHeld() }
        if (wakeLock.isHeld) {
            window.decorView.postDelayed({ wakeLock.releaseIfHeld() }, 2_000)
        }
    }

    private fun registerDismissReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == AlarmIntents.ACTION_ALARM_DISMISSED) {
                    val dismissedId = intent.getIntExtra(AlarmIntents.EXTRA_ALARM_ID, -1)
                    if (dismissedId == alarmId) {
                        finish()
                    }
                }
            }
        }
        dismissalReceiver = receiver
        ContextCompat.registerReceiver(
            this,
            receiver,
            IntentFilter(AlarmIntents.ACTION_ALARM_DISMISSED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    companion object {
        private const val EXTRA_ALARM_ID = "alarmFullScreenId"

        fun createIntent(context: Context, alarmId: Int): Intent {
            return Intent(context, AlarmRingingActivity::class.java).apply {
                putExtra(EXTRA_ALARM_ID, alarmId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }
    }
}

private fun PowerManager.WakeLock.releaseIfHeld() {
    if (isHeld) {
        runCatching { release() }
    }
}

@Composable
private fun AlarmRingingScreen(
    alarm: AlarmUiModel?,
    onStop: () -> Unit,
    tasks: List<AlarmDismissTask>,
    activeTask: AlarmDismissTask?,
    onTaskSelected: (AlarmDismissTask) -> Unit,
    onTaskCompleted: () -> Unit,
    onTaskCancelled: () -> Unit,
    showDebugCancel: Boolean,
    onDebugCancel: () -> Unit
) {
    if (activeTask != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            activeTask.Content(
                modifier = Modifier.fillMaxSize(),
                onCompleted = onTaskCompleted,
                onCancelled = onTaskCancelled
            )
            if (showDebugCancel) {
                DebugCancelButton(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                    onClick = onDebugCancel
                )
            }
        }
        return
    }

    val context = LocalContext.current
    val label = alarm?.label?.takeIf { it.isNotBlank() } ?: stringResource(id = R.string.alarm_label_default)
    val timeText = alarm?.time?.formatForDisplay(android.text.format.DateFormat.is24HourFormat(context))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        if (showDebugCancel) {
            DebugCancelButton(
                onClick = onDebugCancel,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(id = R.string.alarm_ringing_message),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            timeText?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }
            if (tasks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(40.dp))
                tasks.forEachIndexed { index, task ->
                    if (index > 0) {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    Button(
                        onClick = { onTaskSelected(task) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                    ) {
                        Text(
                            text = stringResource(id = task.labelResId),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun AlarmRingingScreenPreview() {
    val sampleAlarm = sampleAlarms().firstOrNull()
    val tasks = listOf(
        MathChallengeDismissTask(),
        FocusTimerDismissTask()
    )
    BreakTheSnoozeTheme {
        AlarmRingingScreen(
            alarm = sampleAlarm,
            onStop = {},
            tasks = tasks,
            activeTask = null,
            onTaskSelected = {},
            onTaskCompleted = {},
            onTaskCancelled = {},
            showDebugCancel = true,
            onDebugCancel = {}
        )
    }
}

@Composable
private fun DebugCancelButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .height(44.dp)
    ) {
        Text(text = "Cancel")
    }
}

@Preview
@Composable
private fun DebugCancelButtonPreview() {
    BreakTheSnoozeTheme {
        DebugCancelButton(onClick = {})
    }
}
