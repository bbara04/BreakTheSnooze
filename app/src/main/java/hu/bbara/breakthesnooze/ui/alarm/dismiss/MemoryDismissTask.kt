package hu.bbara.breakthesnooze.ui.alarm.dismiss

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import hu.bbara.breakthesnooze.R
import kotlinx.coroutines.delay
import kotlin.random.Random

class MemoryDismissTask(
    private val targetLength: Int = 5,
    private val stepDurationMillis: Long = 320,
    private val pauseDurationMillis: Long = 120,
    private val feedbackDurationMillis: Long = 260,
    private val gridDimension: Int = 4
) : AlarmDismissTask {

    override val id: String = "memory_task"
    override val labelResId: Int = R.string.alarm_memory_task

    @Composable
    override fun Content(
        modifier: Modifier,
        onCompleted: () -> Unit,
        onCancelled: () -> Unit
    ) {
        var sequence by rememberSaveable { mutableStateOf<List<Int>>(emptyList()) }
        var isShowing by rememberSaveable { mutableStateOf(false) }
        var inputIndex by rememberSaveable { mutableStateOf(0) }
        var round by rememberSaveable { mutableStateOf(0) }
        var showError by rememberSaveable { mutableStateOf(false) }
        var inputsLocked by rememberSaveable { mutableStateOf(true) }

        var showHighlightIndex by remember { mutableStateOf(-1) }
        var feedbackHighlight by remember { mutableStateOf<FeedbackHighlight?>(null) }
        var pendingNextRound by rememberSaveable { mutableStateOf(false) }
        var pendingCompletion by rememberSaveable { mutableStateOf(false) }
        var replayPending by remember { mutableStateOf(false) }

        val totalCells = gridDimension * gridDimension

        fun startNextRound() {
            val nextIndex = Random.nextInt(from = 0, until = totalCells)
            val updatedSequence = sequence + nextIndex
            sequence = updatedSequence
            round = updatedSequence.size
            inputIndex = 0
            showError = false
            inputsLocked = true
            replayPending = false
            isShowing = true
        }

        LaunchedEffect(Unit) {
            if (sequence.isEmpty()) {
                startNextRound()
            }
        }

        LaunchedEffect(sequence, isShowing) {
            if (sequence.isEmpty() || !isShowing) return@LaunchedEffect
            val initialDelay = if (replayPending) feedbackDurationMillis else pauseDurationMillis
            delay(initialDelay)
            replayPending = false
            feedbackHighlight = null
            sequence.forEachIndexed { idx, value ->
                showHighlightIndex = value
                delay(stepDurationMillis)
                showHighlightIndex = -1
                if (idx != sequence.lastIndex) {
                    delay(pauseDurationMillis)
                }
            }
            showHighlightIndex = -1
            isShowing = false
            inputsLocked = false
        }

        LaunchedEffect(feedbackHighlight) {
            val current = feedbackHighlight ?: return@LaunchedEffect
            delay(feedbackDurationMillis)
            if (feedbackHighlight == current) {
                feedbackHighlight = null
            }
        }

        LaunchedEffect(pendingNextRound) {
            if (!pendingNextRound) return@LaunchedEffect
            delay(feedbackDurationMillis)
            pendingNextRound = false
            startNextRound()
        }

        LaunchedEffect(pendingCompletion) {
            if (!pendingCompletion) return@LaunchedEffect
            delay(feedbackDurationMillis)
            pendingCompletion = false
            onCompleted()
        }

        Surface(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.memory_title),
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val displayRound = if (round <= 0) 1 else minOf(round, targetLength)
                    Text(
                        text = stringResource(id = R.string.memory_progress, displayRound, targetLength),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = stringResource(id = R.string.memory_instructions),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    if (showError) {
                        Text(
                            text = stringResource(id = R.string.memory_wrong),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val offColor = MaterialTheme.colorScheme.surfaceVariant
                    val showingColor = MaterialTheme.colorScheme.primary
                    val correctColor = Color(0xFF2E7D32)
                    val incorrectColor = MaterialTheme.colorScheme.error
                    val shape = RoundedCornerShape(12.dp)

                    repeat(gridDimension) { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            repeat(gridDimension) { column ->
                                val index = row * gridDimension + column
                                val color = when {
                                    showHighlightIndex == index -> showingColor
                                    feedbackHighlight?.index == index -> when (feedbackHighlight?.type) {
                                        FeedbackType.Correct -> correctColor
                                        FeedbackType.Incorrect -> incorrectColor
                                        null -> offColor
                                    }
                                    else -> offColor
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(shape)
                                        .background(color)
                                        .clickable(enabled = !isShowing && !inputsLocked) {
                                            val expected = sequence.getOrNull(inputIndex)
                                            if (expected == index) {
                                                feedbackHighlight = FeedbackHighlight(index, FeedbackType.Correct)
                                                inputIndex += 1
                                                if (inputIndex == sequence.size) {
                                                    inputsLocked = true
                                                    if (sequence.size >= targetLength) {
                                                        pendingCompletion = true
                                                    } else {
                                                        pendingNextRound = true
                                                    }
                                                }
                                            } else {
                                                feedbackHighlight = FeedbackHighlight(index, FeedbackType.Incorrect)
                                                showError = true
                                                inputIndex = 0
                                                inputsLocked = true
                                                replayPending = true
                                                isShowing = true
                                            }
                                        }
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        onClick = onCancelled,
                        shape = MaterialTheme.shapes.medium,
                        tonalElevation = 2.dp,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Text(text = stringResource(id = R.string.memory_cancel))
                        }
                    }
                    Surface(
                        onClick = {
                            showError = false
                            inputIndex = 0
                            feedbackHighlight = null
                            inputsLocked = true
                            replayPending = false
                            pendingNextRound = false
                            isShowing = true
                        },
                        enabled = !isShowing && !pendingCompletion,
                        shape = MaterialTheme.shapes.medium,
                        tonalElevation = 2.dp,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Text(text = stringResource(id = R.string.memory_replay))
                        }
                    }
                }
            }
        }
    }
}

private data class FeedbackHighlight(val index: Int, val type: FeedbackType)

private enum class FeedbackType { Correct, Incorrect }
