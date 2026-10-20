package com.britishcoach.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import com.britishcoach.app.ai.ModelState
import com.britishcoach.app.ui.Routes
import com.britishcoach.app.ui.components.ModelSetupCard
import com.britishcoach.app.ui.components.ScreenTitle
import com.britishcoach.app.ui.components.SectionCard
import com.britishcoach.app.ui.containerViewModel
import com.britishcoach.core.learning.LessonRecommender
import com.britishcoach.core.learning.LessonUnlockPolicy
import com.britishcoach.core.model.LessonContent
import com.britishcoach.core.model.PracticeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class SpeakState(val recommended: LessonContent? = null, val conversations: List<LessonContent> = emptyList())

class SpeakViewModel(c: AppContainer) : ViewModel() {
    val state: StateFlow<SpeakState> = c.lessons.observeProgress().map { p ->
        val unlocked = LessonUnlockPolicy.unlockedIds(c.lessons.lessons, p)
        SpeakState(
            recommended = LessonRecommender.recommend(c.lessons.lessons, p, System.currentTimeMillis()),
            conversations = c.lessons.lessons.filter { it.mode == PracticeMode.GUIDED_CONVERSATION && it.id in unlocked },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SpeakState())
}

@Composable
fun SpeakScreen(container: AppContainer, nav: NavHostController) {
    val vm = containerViewModel { SpeakViewModel(container) }
    val s by vm.state.collectAsState()
    val model by container.model.state.collectAsState()
    val aiReady = model is ModelState.Ready

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenTitle("Speak", "Choose how you'd like to practise.")

        SectionCard(title = "Repeat after the coach", onClick = s.recommended?.let { l -> { nav.navigate(Routes.lesson(l.id)) } }) {
            Text("Listen to a sentence, record yourself, and compare. Works fully offline, with or without the AI coach.", style = MaterialTheme.typography.bodyMedium)
            s.recommended?.let { Text("Next up: ${it.title}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
        }

        if (!aiReady) ModelSetupCard(container, compact = true)

        SectionCard(title = "Guided conversation") {
            Text("Your coach introduces a topic and asks one question at a time. Important corrections are summarised at the end.", style = MaterialTheme.typography.bodyMedium)
            if (s.conversations.isEmpty()) {
                Text("Conversation topics unlock in Stage 3. Keep going with the lessons!", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        s.conversations.forEach { l ->
            SectionCard(onClick = if (aiReady) ({ nav.navigate(Routes.conversation(l.id)) }) else null) {
                Text(l.conversationTopic ?: l.title, style = MaterialTheme.typography.titleMedium)
                Text("From: ${l.title}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        SectionCard(title = "Free conversation", onClick = if (aiReady) ({ nav.navigate(Routes.conversation(null)) }) else null) {
            Text("Chat about anything you like. Your coach stays in character and gives a short review at the end.", style = MaterialTheme.typography.bodyMedium)
            if (!aiReady) Text("Needs the AI coach.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
