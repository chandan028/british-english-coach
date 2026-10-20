package com.britishcoach.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.britishcoach.app.AppContainer
import com.britishcoach.app.data.db.PracticeAttemptEntity
import com.britishcoach.app.ui.components.ScreenTitle
import com.britishcoach.app.ui.components.SectionCard
import com.britishcoach.app.ui.containerViewModel
import com.britishcoach.core.model.CompletionStatus
import com.britishcoach.core.model.Stage
import com.britishcoach.core.progress.ProgressCalculator
import com.britishcoach.core.progress.ProgressSummary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.text.DateFormat
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale

data class ProgressUiState(
    val summary: ProgressSummary? = null,
    val completedByStage: Map<Stage, Pair<Int, Int>> = emptyMap(),
    val history: List<PracticeAttemptEntity> = emptyList(),
    val difficulty: Map<Int, Int> = emptyMap(),
    val goalMinutes: Int = 20,
)

class ProgressViewModel(c: AppContainer) : ViewModel() {
    val state: StateFlow<ProgressUiState> = combine(
        c.practice.observeAttempts(),
        c.practice.observeSessions(),
        c.lessons.observeProgress(),
        c.settings.settings,
    ) { attempts, sessions, progress, settings ->
        ProgressUiState(
            summary = ProgressCalculator.summarize(attempts.map { it.toFact() }, sessions.map { it.toFact() }, System.currentTimeMillis()),
            completedByStage = Stage.entries.associateWith { st ->
                val ls = c.lessons.lessons.filter { it.stage == st }
                ls.count { progress[it.id]?.status == CompletionStatus.COMPLETED } to ls.size
            },
            history = attempts.take(25),
            difficulty = attempts.mapNotNull { it.selfRatedDifficulty }.groupingBy { it }.eachCount(),
            goalMinutes = settings.dailyGoalMinutes,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUiState())
}

@Composable
fun ProgressScreen(container: AppContainer) {
    val vm = containerViewModel { ProgressViewModel(container) }
    val s by vm.state.collectAsState()
    val sum = s.summary

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenTitle("Progress", "Everything here is counted from your saved practice.")

        if (sum == null || (sum.totalAttempts == 0 && sum.minutesThisWeek == 0)) {
            SectionCard { Text("No practice saved yet. Complete a lesson and your progress will appear here.", style = MaterialTheme.typography.bodyMedium) }
        }

        sum?.let {
            SectionCard(title = "Practice minutes, last 7 days") {
                MinutesChart(it, s.goalMinutes)
                Text("${it.minutesThisWeek} minutes this week · ${it.practiceDaysThisWeek} practice days · ${it.totalAttempts} attempts in total", style = MaterialTheme.typography.bodyMedium)
            }

            SectionCard(title = "Weekly trend") {
                Text(
                    "Share of target words the speech recogniser matched. This describes transcripts, which also depend on noise and the recogniser; it isn't a pronunciation score.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val fmt = DateTimeFormatter.ofPattern("d MMM")
                it.weeklyTrend.forEach { w ->
                    Column {
                        Text(
                            "Week of ${w.weekStart.format(fmt)} · ${w.attempts} attempts" +
                                (w.transcriptMatchRate?.let { r -> " · ${(r * 100).toInt()}% of words matched" } ?: ""),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        w.transcriptMatchRate?.let { r -> LinearProgressIndicator(progress = { r.toFloat() }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) }
                    }
                }
            }

            if (it.recurringWords.isNotEmpty()) {
                SectionCard(title = "Repeated transcript differences (last 30 days)") {
                    it.recurringWords.forEach { w -> Text("${w.word} · ${w.times} times", style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }

        SectionCard(title = "Lessons completed") {
            s.completedByStage.forEach { (stage, pair) ->
                val (done, total) = pair
                Text("Stage ${stage.number} · ${stage.title}: $done of $total", style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(progress = { if (total == 0) 0f else done.toFloat() / total }, modifier = Modifier.fillMaxWidth())
            }
        }

        if (s.difficulty.isNotEmpty()) {
            SectionCard(title = "How practice felt") {
                Text("Easy ${s.difficulty[1] ?: 0} · OK ${s.difficulty[2] ?: 0} · Hard ${s.difficulty[3] ?: 0}", style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (s.history.isNotEmpty()) {
            SectionCard(title = "Attempt history") {
                val fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                s.history.forEach { a ->
                    Column(Modifier.padding(vertical = 2.dp)) {
                        Text(a.targetText, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            buildString {
                                append(fmt.format(Date(a.timestamp)))
                                if (a.targetWordCount > 0) append(" · ${a.matchedWordCount}/${a.targetWordCount} words matched")
                                else append(" · not transcribed")
                                if (a.recognizedText.isNotBlank()) append(" · \"${a.recognizedText}\"")
                            },
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MinutesChart(sum: ProgressSummary, goal: Int) {
    val bar = MaterialTheme.colorScheme.primary
    val goalColor = MaterialTheme.colorScheme.secondary
    val track = MaterialTheme.colorScheme.surfaceVariant
    val max = maxOf(goal, sum.last7Days.maxOf { it.minutes }, 1)
    Column {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val n = sum.last7Days.size
            val slot = size.width / n
            val bw = slot * 0.55f
            sum.last7Days.forEachIndexed { i, d ->
                val x = i * slot + (slot - bw) / 2
                drawRoundRect(track, Offset(x, 0f), Size(bw, size.height), CornerRadius(8f, 8f))
                val h = size.height * d.minutes / max
                drawRoundRect(bar, Offset(x, size.height - h), Size(bw, h), CornerRadius(8f, 8f))
            }
            val gy = size.height * (1 - goal.toFloat() / max)
            drawLine(goalColor, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 3f)
        }
        Row(Modifier.fillMaxWidth()) {
            sum.last7Days.forEach { d ->
                Text(
                    d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.UK) + "\n${d.minutes}m",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        Row { Canvas(Modifier.width(16.dp).height(10.dp)) { drawLine(goalColor, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 3f) }
            Text(" daily goal: $goal minutes", style = MaterialTheme.typography.labelSmall) }
    }
}
