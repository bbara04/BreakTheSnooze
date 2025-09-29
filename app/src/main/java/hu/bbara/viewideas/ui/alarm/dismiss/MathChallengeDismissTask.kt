package hu.bbara.viewideas.ui.alarm.dismiss

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backspace
import androidx.compose.material.icons.outlined.Done
import hu.bbara.viewideas.R

class MathChallengeDismissTask(
    private val config: MathChallengeConfig = MathChallengeConfig()
) : AlarmDismissTask {

    override val id: String = "math_challenge"
    override val labelResId: Int = R.string.alarm_math_challenge

    @Composable
    override fun Content(
        modifier: Modifier,
        onCompleted: () -> Unit,
        onCancelled: () -> Unit
    ) {
        var questions by remember { mutableStateOf(generateQuestions(config)) }
        var currentIndex by rememberSaveable { mutableStateOf(0) }
        var answer by rememberSaveable { mutableStateOf("") }
        var showError by rememberSaveable { mutableStateOf(false) }

        LaunchedEffect(config) {
            questions = generateQuestions(config)
            currentIndex = 0
            answer = ""
            showError = false
        }

        val current = questions[currentIndex]
        val questionLabel = "${current.first} × ${current.second}" // e.g., 6 × 7

        Surface(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(id = R.string.math_challenge_title),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(
                        id = R.string.math_challenge_progress,
                        currentIndex + 1,
                        questions.size
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = questionLabel,
                    style = MaterialTheme.typography.displayMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(id = R.string.math_challenge_instructions),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    tonalElevation = 4.dp,
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = answer.ifEmpty { "–" },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        style = MaterialTheme.typography.displaySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (showError) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(id = R.string.math_challenge_incorrect),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                MathKeypad(
                    modifier = Modifier.fillMaxWidth(),
                    onKeyPressed = { key ->
                        when (key) {
                            MathKey.Enter -> {
                                val userAnswer = answer.toIntOrNull()
                                val correctAnswer = current.first * current.second
                                if (userAnswer == correctAnswer) {
                                    if (currentIndex == questions.lastIndex) {
                                        onCompleted()
                                    } else {
                                        currentIndex += 1
                                        answer = ""
                                        showError = false
                                    }
                                } else {
                                    showError = true
                                }
                            }
                            MathKey.Delete -> {
                                if (answer.isNotEmpty()) {
                                    answer = answer.dropLast(1)
                                }
                            }
                            is MathKey.Digit -> {
                                if (answer.length < 5) {
                                    answer += key.value
                                }
                            }
                        }
                    }
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onCancelled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text(text = stringResource(id = R.string.math_challenge_cancel))
                }
            }
        }
    }

private fun generateQuestions(config: MathChallengeConfig): List<Pair<Int, Int>> {
    val range = when (config.difficulty) {
        MathDifficulty.Easy -> 2..9
        MathDifficulty.Medium -> 5..12
        MathDifficulty.Hard -> 10..20
        }
        val count = maxOf(1, config.questionCount)
        return List(count) {
            range.random() to range.random()
        }
    }
}

@Composable
private fun MathKeypad(
    modifier: Modifier = Modifier,
    onKeyPressed: (MathKey) -> Unit
) {
    val rows = listOf(
        listOf(MathKey.Digit("1"), MathKey.Digit("2"), MathKey.Digit("3")),
        listOf(MathKey.Digit("4"), MathKey.Digit("5"), MathKey.Digit("6")),
        listOf(MathKey.Digit("7"), MathKey.Digit("8"), MathKey.Digit("9")),
        listOf(MathKey.Delete, MathKey.Digit("0"), MathKey.Enter)
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                row.forEach { key ->
                    Button(
                        onClick = { onKeyPressed(key) },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                    ) {
                        when (key) {
                            is MathKey.Digit -> Text(
                                text = key.value,
                                style = MaterialTheme.typography.titleMedium
                            )
                            MathKey.Delete -> Icon(
                                imageVector = Icons.Outlined.Backspace,
                                contentDescription = stringResource(id = R.string.math_challenge_delete)
                            )
                            MathKey.Enter -> Icon(
                                imageVector = Icons.Outlined.Done,
                                contentDescription = stringResource(id = R.string.math_challenge_enter)
                            )
                        }
                    }
                }
            }
        }
    }
}

private sealed class MathKey {
    data class Digit(val value: String) : MathKey()
    object Delete : MathKey()
    object Enter : MathKey()
}

@Preview(showBackground = true)
@Composable
private fun MathChallengeDismissTaskPreview() {
    MaterialTheme {
        Surface {
            MathChallengeDismissTask(
                config = MathChallengeConfig(
                    difficulty = MathDifficulty.Easy,
                    questionCount = 2
                )
            ).Content(
                modifier = Modifier.fillMaxSize(),
                onCompleted = {},
                onCancelled = {}
            )
        }
    }
}

data class MathChallengeConfig(
    val difficulty: MathDifficulty = MathDifficulty.Medium,
    val questionCount: Int = 3
)

enum class MathDifficulty { Easy, Medium, Hard }
