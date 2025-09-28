package hu.bbara.viewideas.ui.alarm.dismiss

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import hu.bbara.viewideas.R
import kotlinx.coroutines.delay
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

class FocusTimerDismissTask(
    private val config: FocusTimerConfig = FocusTimerConfig()
) : AlarmDismissTask {

    override val id: String = "focus_timer"
    override val labelResId: Int = R.string.alarm_focus_task

    @Composable
    override fun Content(
        modifier: Modifier,
        onCompleted: () -> Unit,
        onCancelled: () -> Unit
    ) {
        FocusTimerContent(
            modifier = modifier,
            config = config,
            onCompleted = onCompleted,
            onCancelled = onCancelled
        )
    }
}

data class FocusTimerConfig(
    val totalDurationSeconds: Int = 120,
    val confirmationWindowSeconds: Int = 3,
    val minConfirmationInterval: Int = 15,
    val maxConfirmationInterval: Int = 45
)

@Composable
private fun FocusTimerContent(
    modifier: Modifier,
    config: FocusTimerConfig,
    onCompleted: () -> Unit,
    onCancelled: () -> Unit
) {
    var remainingSeconds by remember { mutableStateOf(config.totalDurationSeconds) }
    var confirmationRequested by remember { mutableStateOf(false) }
    var confirmationRemaining by remember { mutableStateOf(config.confirmationWindowSeconds) }
    var currentInterval by remember { mutableStateOf(generateInterval(config)) }
    var failure by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (remainingSeconds > 0 && !failure) {
            delay(1.seconds)
            remainingSeconds -= 1
            currentInterval -= 1

            if (confirmationRequested) {
                confirmationRemaining -= 1
                if (confirmationRemaining <= 0) {
                    failure = true
                    break
                }
            }

            if (!confirmationRequested && currentInterval <= 0 && remainingSeconds > config.confirmationWindowSeconds) {
                confirmationRequested = true
                confirmationRemaining = config.confirmationWindowSeconds
            }
        }
        if (!failure && remainingSeconds <= 0) {
            onCompleted()
        }
    }

    LaunchedEffect(confirmationRequested) {
        if (!confirmationRequested) {
            currentInterval = generateInterval(config)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(
                id = R.string.focus_timer_remaining,
                remainingSeconds / 60,
                remainingSeconds % 60
            ),
            style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.ExtraBold),
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp)
        )

        AnimatedVisibility(
            visible = confirmationRequested,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.focus_confirm_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
                Text(
                    text = "${confirmationRemaining}s",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Button(onClick = {
                    confirmationRequested = false
                    confirmationRemaining = config.confirmationWindowSeconds
                }) {
                    Text(text = stringResource(id = R.string.focus_confirm_button))
                }
            }
        }

        AnimatedVisibility(
            visible = failure,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Text(
                text = stringResource(id = R.string.focus_fail_message),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Button(
            onClick = onCancelled,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        ) {
            Text(text = stringResource(id = R.string.math_challenge_cancel))
        }
    }
}

private fun generateInterval(config: FocusTimerConfig): Int {
    return Random.nextInt(config.minConfirmationInterval, config.maxConfirmationInterval + 1)
}

@Preview
@Composable
private fun FocusTimerPreview() {
    MaterialTheme {
        Surface {
            FocusTimerDismissTask().Content(
                modifier = Modifier.fillMaxSize(),
                onCompleted = {},
                onCancelled = {}
            )
        }
    }
}
