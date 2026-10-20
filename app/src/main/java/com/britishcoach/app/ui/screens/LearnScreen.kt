package com.britishcoach.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.britishcoach.app.AppContainer
import com.britishcoach.app.ui.Routes
import com.britishcoach.app.ui.components.ScreenTitle
import com.britishcoach.app.ui.containerViewModel
import com.britishcoach.core.learning.LessonProgress
import com.britishcoach.core.learning.LessonUnlockPolicy
import com.britishcoach.core.model.CompletionStatus
import com.britishcoach.core.model.LessonContent
import com.britishcoach.core.model.Stage
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class LearnState(
    val lessons: List<LessonContent> = emptyList(),
    val progress: Map<String, LessonProgress> = emptyMap(),
    val unlocked: Set<String> = emptySet(),
)

class LearnViewModel(c: AppContainer) : ViewModel() {
    val state: StateFlow<LearnState> = c.lessons.observeProgress().map { p ->
        LearnState(c.lessons.lessons, p, LessonUnlockPolicy.unlockedIds(c.lessons.lessons, p))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LearnState(c.lessons.lessons))
}

@Composable
fun LearnScreen(container: AppContainer, nav: NavHostController) {
    val vm = containerViewModel { LearnViewModel(container) }
    val s by vm.state.collectAsState()
    val byStage = s.lessons.groupBy { it.stage }

    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { ScreenTitle("Learn", "Five stages from first sounds to advanced conversation. Completed lessons stay open to revisit.") }
        for (stage in Stage.entries) {
            val lessons = byStage[stage].orEmpty()
            item(key = "h-${stage.name}") {
                val done = lessons.count { s.progress[it.id]?.status == CompletionStatus.COMPLETED }
                Column(Modifier.padding(top = 14.dp, bottom = 2.dp)) {
                    Text("Stage ${stage.number} · ${stage.title}", style = MaterialTheme.typography.titleLarge)
                    Text("${stage.summary} · $done of ${lessons.size} done", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(lessons, key = { it.id }) { lesson ->
                val open = lesson.id in s.unlocked
                val status = s.progress[lesson.id]?.status ?: CompletionStatus.NOT_STARTED
                LessonRow(lesson, open, status) { nav.navigate(Routes.lesson(lesson.id)) }
            }
        }
    }
}

@Composable
private fun LessonRow(lesson: LessonContent, open: Boolean, status: CompletionStatus, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        enabled = open,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            val (icon, tint) = when {
                status == CompletionStatus.COMPLETED -> Icons.Filled.CheckCircle to MaterialTheme.colorScheme.primary
                open -> Icons.Outlined.PlayCircle to MaterialTheme.colorScheme.secondary
                else -> Icons.Outlined.Lock to MaterialTheme.colorScheme.onSurfaceVariant
            }
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(lesson.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        !open -> "Finish the previous lesson to unlock"
                        lesson.conversationTopic != null -> "${lesson.category.label} · phrases and conversation"
                        else -> "${lesson.category.label} · ${lesson.items.size} items"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
