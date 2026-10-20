package com.britishcoach.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.britishcoach.app.AppContainer
import com.britishcoach.app.data.db.MistakeEntity
import com.britishcoach.app.data.db.PracticeAttemptEntity
import com.britishcoach.app.ui.Routes
import com.britishcoach.app.ui.components.ModelSetupCard
import com.britishcoach.app.ui.components.Pill
import com.britishcoach.app.ui.components.ScreenTitle
import com.britishcoach.app.ui.components.SectionCard
import com.britishcoach.app.ui.containerViewModel
import com.britishcoach.app.ui.navigateTab
import com.britishcoach.core.learning.LessonRecommender
import com.britishcoach.core.learning.LessonUnlockPolicy
import com.britishcoach.core.model.CompletionStatus
import com.britishcoach.core.model.ErrorCategory
import com.britishcoach.core.model.LessonContent
import com.britishcoach.core.model.MasteryStatus
import com.britishcoach.core.model.Stage
import com.britishcoach.core.progress.ProgressCalculator
import com.britishcoach.core.progress.ProgressSummary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.text.DateFormat
import java.util.Date

data class HomeState(
    val loading: Boolean = true,
    val stage: Stage = Stage.FOUNDATIONS,
    val today: LessonContent? = null,
    val goalMinutes: Int = 20,
    val lessonsCompleted: Int = 0,
    val totalLessons: Int = 0,
    val summary: ProgressSummary? = null,
    val recent: List<PracticeAttemptEntity> = emptyList(),
    val frequentMistakes: List<MistakeEntity> = emptyList(),
    val dueCount: Int = 0,
)

class HomeViewModel(c: AppContainer) : ViewModel() {
    val state: StateFlow<HomeState> = combine(
        c.lessons.observeProgress(),
        c.practice.observeAttempts(),
        c.practice.observeSessions(),
        c.mistakes.observeAll(),
        c.settings.settings,
    ) { progress, attempts, sessions, mistakes, settings ->
        val now = System.currentTimeMillis()
        val lessons = c.lessons.lessons
        HomeState(
            loading = false,
            stage = LessonUnlockPolicy.currentStage(lessons, progress),
            today = LessonRecommender.recommend(lessons, progress, now),
            goalMinutes = settings.dailyGoalMinutes,
            lessonsCompleted = progress.values.count { it.status == CompletionStatus.COMPLETED },
            totalLessons = lessons.size,
            summary = ProgressCalculator.summarize(attempts.map { it.toFact() }, sessions.map { it.toFact() }, now),
            recent = attempts.take(4),
            frequentMistakes = mistakes.filter { it.masteryStatus != MasteryStatus.MASTERED.name }
                .sortedByDescending { it.attemptCount }.take(3),
            dueCount = mistakes.count { it.masteryStatus != MasteryStatus.MASTERED.name && it.nextReviewAt <= now },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())
}

@Composable
fun HomeScreen(container: AppContainer, nav: NavHostController) {
    val vm = containerViewModel { HomeViewModel(container) }
    val s by vm.state.collectAsState()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenTitle(greeting(), "Stage ${s.stage.number} of 5 · ${s.stage.title}")

        val minutesToday = s.summary?.minutesToday ?: 0
        SectionCard(title = "Today's practice") {
            Text("$minutesToday of ${s.goalMinutes} minutes", style = MaterialTheme.typography.bodyLarge)
            LinearProgressIndicator(
                progress = { (minutesToday.toFloat() / s.goalMinutes.coerceAtLeast(1)).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { nav.navigate(Routes.DAILY) }, modifier = Modifier.fillMaxWidth()) {
                Text("Start today's ${s.goalMinutes}-minute session")
            }
        }

        s.today?.let { lesson ->
            SectionCard(title = "Recommended lesson", onClick = { nav.navigate(Routes.lesson(lesson.id)) }) {
                Text(lesson.title, style = MaterialTheme.typography.titleLarge)
                Text(lesson.introduction, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(lesson.stage.title)
                    Pill(lesson.category.label)
                }
            }
        }

        ModelSetupCard(container, compact = true)

        if (s.dueCount > 0 || s.frequentMistakes.isNotEmpty() || !s.summary?.recurringWords.isNullOrEmpty()) {
            SectionCard(title = "To revise") {
                if (s.dueCount > 0) Text("${s.dueCount} notebook item${if (s.dueCount == 1) "" else "s"} due for review", style = MaterialTheme.typography.bodyLarge)
                s.frequentMistakes.forEach { m ->
                    Text("• ${m.example}  (${categoryLabel(m.category)}, ${m.attemptCount}×)", style = MaterialTheme.typography.bodyMedium)
                }
                val words = s.summary?.recurringWords.orEmpty()
                if (words.isNotEmpty()) {
                    Text("Words that often came out differently in transcripts:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(words.take(6).joinToString(" · ") { "${it.word} (${it.times})" }, style = MaterialTheme.typography.bodyMedium)
                }
                OutlinedButton(onClick = { nav.navigateTab("mistakes") }) { Text("Open my mistakes") }
            }
        }

        s.summary?.let { sum ->
            SectionCard(title = "This week", onClick = { nav.navigateTab("progress") }) {
                Text("${sum.minutesThisWeek} minutes · ${sum.attemptsThisWeek} attempts · ${sum.practiceDaysThisWeek} of 7 days", style = MaterialTheme.typography.bodyLarge)
                Text("Lessons completed: ${s.lessonsCompleted} of ${s.totalLessons}", style = MaterialTheme.typography.bodyMedium)
                if (sum.currentStreakDays > 1) Text("You've practised ${sum.currentStreakDays} days in a row.", style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (s.recent.isNotEmpty()) {
            SectionCard(title = "Recent practice") {
                val fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                s.recent.forEach { a ->
                    Column {
                        Text(a.targetText, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                        Text(
                            "${fmt.format(Date(a.timestamp))} · ${a.matchedWordCount} of ${a.targetWordCount} words matched",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else if (!s.loading) {
            SectionCard {
                Text("Welcome! Your progress will appear here as soon as you complete your first practice.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun greeting(): String {
    val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when (h) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }
}

fun categoryLabel(name: String): String = runCatching { ErrorCategory.valueOf(name).label }.getOrDefault(name)
