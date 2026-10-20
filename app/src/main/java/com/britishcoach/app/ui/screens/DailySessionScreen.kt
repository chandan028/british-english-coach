package com.britishcoach.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.britishcoach.app.AppContainer
import com.britishcoach.app.ai.ModelState
import com.britishcoach.app.ui.Routes
import com.britishcoach.app.ui.components.CoachBubble
import com.britishcoach.app.ui.components.SectionCard
import com.britishcoach.app.ui.containerViewModel
import com.britishcoach.app.ui.navigateTab
import com.britishcoach.core.learning.DailySessionPlan
import com.britishcoach.core.learning.DailySessionPlanner
import com.britishcoach.core.learning.SessionStep
import com.britishcoach.core.learning.SessionStepType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DailyState(val plan: DailySessionPlan? = null, val done: Set<Int> = emptySet(), val minutes: Int = 20)

class DailySessionViewModel(private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(DailyState())
    val state: StateFlow<DailyState> = _state.asStateFlow()
    val startedAt = System.currentTimeMillis()

    init {
        viewModelScope.launch { replan(c.settings.current().dailyGoalMinutes) }
    }

    fun replan(minutes: Int) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        val plan = DailySessionPlanner.plan(minutes, c.lessons.lessons, c.lessons.progress(), c.mistakes.due(now), now)
        _state.update { it.copy(plan = plan, minutes = minutes) }
    }

    fun markDone(index: Int) = _state.update { it.copy(done = it.done + index) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailySessionScreen(container: AppContainer, nav: NavHostController) {
    val vm = containerViewModel { DailySessionViewModel(container) }
    val s by vm.state.collectAsState()
    val model by container.model.state.collectAsState()
    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) { elapsed = System.currentTimeMillis() - vm.startedAt; delay(1_000) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Today's session") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CoachBubble("Here's our plan for today. Take each step at your own pace; I've built it around your lessons and anything due for revision.")
            Text("How much time do you have?", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10, 20, 30).forEach { m -> FilterChip(selected = s.minutes == m, onClick = { vm.replan(m) }, label = { Text("$m min") }) }
            }
            Text("Elapsed: ${elapsed / 60_000} min", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            s.plan?.steps?.forEachIndexed { i, step ->
                val done = i in s.done
                SectionCard(onClick = {
                    vm.markDone(i)
                    openStep(nav, step, model is ModelState.Ready)
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (done) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                            contentDescription = if (done) "Done" else "Not started",
                            tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Column(Modifier.padding(start = 12.dp)) {
                            Text("${i + 1}. ${step.type.title} · ${step.minutes} min", style = MaterialTheme.typography.titleMedium)
                            Text(describe(container, step, model is ModelState.Ready), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (s.plan != null && s.done.size == s.plan!!.steps.size) {
                CoachBubble("That's today's session done. Thank you for showing up and practising; that consistency is what makes the difference.")
            }
        }
    }
}

private fun describe(c: AppContainer, step: SessionStep, aiReady: Boolean): String {
    val lesson = step.lessonId?.let { c.lessons.lesson(it) }
    return when (step.type) {
        SessionStepType.LISTENING -> "Listen to the reference voice: ${lesson?.title ?: "today's lesson"}"
        SessionStepType.PRONUNCIATION -> "Record and compare: ${lesson?.title ?: "today's lesson"}"
        SessionStepType.SPEAKING ->
            if (aiReady && lesson?.conversationTopic != null) "Conversation: ${lesson.conversationTopic}"
            else if (aiReady) "Free conversation with your coach"
            else "Speaking task: ${lesson?.title ?: "today's lesson"} (conversation needs the AI coach)"
        SessionStepType.CORRECTION -> "Look over your most useful corrections"
        SessionStepType.REVISION -> if (step.mistakeId != null) "Repeat a difficult item from your notebook" else "Repeat an earlier lesson"
    }
}

private fun openStep(nav: NavHostController, step: SessionStep, aiReady: Boolean) {
    when (step.type) {
        SessionStepType.LISTENING, SessionStepType.PRONUNCIATION -> step.lessonId?.let { nav.navigate(Routes.lesson(it)) }
        SessionStepType.SPEAKING -> if (aiReady) nav.navigate(Routes.conversation(step.lessonId)) else step.lessonId?.let { nav.navigate(Routes.lesson(it)) }
        SessionStepType.CORRECTION -> nav.navigateTab("mistakes")
        SessionStepType.REVISION -> step.lessonId?.let { nav.navigate(Routes.lesson(it, mistakeId = step.mistakeId ?: -1)) }
    }
}
