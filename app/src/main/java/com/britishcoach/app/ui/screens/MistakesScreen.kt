package com.britishcoach.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.britishcoach.app.AppContainer
import com.britishcoach.app.data.db.MistakeEntity
import com.britishcoach.app.ui.Routes
import com.britishcoach.app.ui.components.Pill
import com.britishcoach.app.ui.components.ScreenTitle
import com.britishcoach.app.ui.components.SectionCard
import com.britishcoach.app.ui.containerViewModel
import com.britishcoach.core.model.MasteryStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

enum class MistakeFilter(val label: String) { DUE("Due now"), LEARNING("Learning"), MASTERED("Mastered"), ALL("All") }

class MistakesViewModel(private val c: AppContainer) : ViewModel() {
    val mistakes: StateFlow<List<MistakeEntity>> = c.mistakes.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun markMastered(id: Long) = viewModelScope.launch { c.mistakes.markMastered(id, System.currentTimeMillis()) }
    fun delete(id: Long) = viewModelScope.launch { c.mistakes.delete(id) }
}

@Composable
fun MistakesScreen(container: AppContainer, nav: NavHostController) {
    val vm = containerViewModel { MistakesViewModel(container) }
    val all by vm.mistakes.collectAsState()
    var filter by remember { mutableStateOf(MistakeFilter.DUE) }
    var confirmDelete by remember { mutableStateOf<MistakeEntity?>(null) }
    val now = System.currentTimeMillis()
    val shown = all.filter {
        when (filter) {
            MistakeFilter.DUE -> it.masteryStatus != MasteryStatus.MASTERED.name && it.nextReviewAt <= now
            MistakeFilter.LEARNING -> it.masteryStatus != MasteryStatus.MASTERED.name
            MistakeFilter.MASTERED -> it.masteryStatus == MasteryStatus.MASTERED.name
            MistakeFilter.ALL -> true
        }
    }
    val fmt = DateFormat.getDateInstance(DateFormat.MEDIUM)

    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ScreenTitle("My mistakes", "Useful corrections are saved here automatically and come back for revision.") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(MistakeFilter.entries) { f ->
                    FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
                }
            }
        }
        if (shown.isEmpty()) {
            item {
                Text(
                    when (filter) {
                        MistakeFilter.DUE -> "Nothing is due for revision right now."
                        MistakeFilter.MASTERED -> "Items you've mastered will appear here."
                        else -> "No saved corrections yet. They'll appear as you practise."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        items(shown, key = { it.id }) { m ->
            SectionCard {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(categoryLabel(m.category))
                    if (m.masteryStatus == MasteryStatus.MASTERED.name) Pill("Mastered", container = MaterialTheme.colorScheme.primaryContainer)
                }
                Text(m.example, style = MaterialTheme.typography.titleMedium)
                if (m.recognizedText.isNotBlank()) Text("Transcript: \"${m.recognizedText}\"", style = MaterialTheme.typography.bodyMedium)
                m.correctedText?.takeIf { it != m.example }?.let { Text("Better: $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
                Text(m.explanation, style = MaterialTheme.typography.bodyMedium)
                if (m.exercise.isNotBlank()) Text("Exercise: ${m.exercise}", style = MaterialTheme.typography.bodySmall)
                Text(
                    "Practised ${m.attemptCount}× · last ${fmt.format(Date(m.lastPractisedAt))}" +
                        if (m.masteryStatus != MasteryStatus.MASTERED.name) " · next review ${fmt.format(Date(m.nextReviewAt))}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(onClick = {
                        // Lesson sentences are practised inside their lesson; anything else on its own.
                        val inLesson = m.lessonId?.let { container.lessons.lesson(it) }?.items?.any { it.text == m.example } == true
                        nav.navigate(Routes.lesson(if (inLesson) m.lessonId!! else LessonViewModel.NOTEBOOK_LESSON_ID, mistakeId = m.id))
                    }) { Text("Practise") }
                    if (m.masteryStatus != MasteryStatus.MASTERED.name) TextButton(onClick = { vm.markMastered(m.id) }) { Text("Mastered") }
                    TextButton(onClick = { confirmDelete = m }) { Text("Delete") }
                }
            }
        }
    }

    confirmDelete?.let { m ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete this item?") },
            text = { Text("\"${m.example}\" will be removed from your notebook.") },
            confirmButton = { TextButton(onClick = { vm.delete(m.id); confirmDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Keep") } },
        )
    }
}
