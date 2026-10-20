package com.britishcoach.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.britishcoach.app.AppContainer
import com.britishcoach.app.ai.ConversationSession
import com.britishcoach.app.audio.AudioRecorder
import com.britishcoach.app.audio.RecordingState
import com.britishcoach.app.speech.TranscriberStatus
import com.britishcoach.app.speech.TranscriptionResult
import com.britishcoach.app.ui.components.CoachBubble
import com.britishcoach.app.ui.components.EvidenceNote
import com.britishcoach.app.ui.components.LearnerBubble
import com.britishcoach.app.ui.components.RecordButton
import com.britishcoach.app.ui.components.SectionCard
import com.britishcoach.app.ui.containerViewModel
import com.britishcoach.core.coach.StructuredFeedback
import com.britishcoach.core.learning.LessonUnlockPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatLine(val fromCoach: Boolean, val text: String)

data class ConversationUiState(
    val title: String = "Conversation",
    val starting: Boolean = true,
    val unavailable: String? = null,
    val lines: List<ChatLine> = emptyList(),
    val coachTyping: Boolean = false,
    val transcribing: Boolean = false,
    val message: String? = null,
    val ending: Boolean = false,
    val review: StructuredFeedback? = null,
    val ended: Boolean = false,
    val canTranscribe: Boolean = true,
)

class ConversationViewModel(private val c: AppContainer, private val lessonId: String?) : ViewModel() {
    private val _state = MutableStateFlow(ConversationUiState())
    val state: StateFlow<ConversationUiState> = _state.asStateFlow()
    val recorder = AudioRecorder()
    private var session: ConversationSession? = null
    private var replyJob: Job? = null
    private val openedAt = System.currentTimeMillis()

    init {
        val lesson = lessonId?.let { c.lessons.lesson(it) }
        _state.update { it.copy(title = lesson?.conversationTopic ?: "Free conversation") }
        viewModelScope.launch {
            val canTranscribe = c.transcriber.status() is TranscriberStatus.Ready
            if (!c.coach.isAiReady) {
                _state.update { it.copy(starting = false, unavailable = com.britishcoach.core.coach.RuleBasedFeedback.conversationUnavailable(), canTranscribe = canTranscribe) }
                return@launch
            }
            try {
                val settings = c.settings.current()
                val stage = LessonUnlockPolicy.currentStage(c.lessons.lessons, c.lessons.progress())
                val s = c.coach.startConversation(lesson, stage, settings.feedbackDetail)
                session = s
                _state.update { it.copy(starting = false, lines = listOf(ChatLine(true, s.openingLine)), canTranscribe = canTranscribe) }
                c.voice.speak(s.openingLine)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.update { it.copy(starting = false, unavailable = "The AI coach couldn't start a conversation (${e.message}). Lessons still work.") }
            }
        }
        viewModelScope.launch {
            recorder.state.collect { st ->
                when (st) {
                    is RecordingState.Finished -> onRecorded(st)
                    is RecordingState.Failed -> _state.update { it.copy(message = st.message) }
                    else -> Unit
                }
            }
        }
    }

    fun toggleRecording() {
        if (recorder.isRecording) { recorder.stop(); return }
        c.voice.stop()
        _state.update { it.copy(message = null) }
        recorder.start(c.recordings.newRecordingFile(), maxDurationMs = 90_000)
    }

    private fun onRecorded(rec: RecordingState.Finished) {
        recorder.reset()
        viewModelScope.launch {
            _state.update { it.copy(transcribing = true) }
            val r = c.transcriber.transcribe(rec.file)
            rec.file.delete() // conversation audio isn't kept; only the text is used
            _state.update { it.copy(transcribing = false) }
            when (r) {
                is TranscriptionResult.Success -> send(r.transcription.text)
                TranscriptionResult.NoSpeech -> _state.update { it.copy(message = "I didn't catch any words there. Could you try again?") }
                is TranscriptionResult.Error -> _state.update { it.copy(message = r.message + " You can type your answer instead.") }
            }
        }
    }

    fun send(text: String) {
        val s = session ?: return
        val clean = text.trim()
        if (clean.isEmpty() || _state.value.coachTyping) return
        _state.update { it.copy(lines = it.lines + ChatLine(false, clean) + ChatLine(true, ""), coachTyping = true, message = null) }
        replyJob = viewModelScope.launch {
            var last = ""
            try {
                s.reply(clean).collect { partial ->
                    last = partial
                    _state.update { st -> st.copy(lines = st.lines.dropLast(1) + ChatLine(true, partial)) }
                }
                _state.update { it.copy(coachTyping = false) }
                if (last.isNotBlank()) c.voice.speak(last)
            } catch (e: CancellationException) {
                _state.update { it.copy(coachTyping = false, lines = it.lines.dropLast(1) + ChatLine(true, last.ifBlank { "(stopped)" })) }
                throw e
            } catch (e: Throwable) {
                _state.update {
                    it.copy(coachTyping = false, lines = it.lines.dropLast(1), message = "Sorry, I lost my train of thought there. Could you say that again?")
                }
            }
        }
    }

    fun stopReply() {
        replyJob?.cancel()
        c.voice.stop()
    }

    fun end() {
        val s = session ?: run { _state.update { it.copy(ended = true) }; return }
        stopReply()
        recorder.cancel()
        _state.update { it.copy(ending = true) }
        viewModelScope.launch {
            val review = s.review()
            val now = System.currentTimeMillis()
            if (review != null && review.corrections.isNotEmpty()) {
                review.corrections.forEach { corr ->
                    c.mistakes.saveCorrections(lessonId, corr.description, "", listOf(corr), now)
                }
            }
            _state.update { it.copy(ending = false, review = review, ended = true) }
            review?.spoken?.takeIf { it.isNotBlank() }?.let { c.voice.speak(it) }
        }
    }

    override fun onCleared() {
        recorder.cancel()
        replyJob?.cancel()
        c.voice.stop()
        session?.close()
        val spent = (System.currentTimeMillis() - openedAt).coerceAtMost(60L * 60 * 1000)
        c.appScope.launch { c.practice.recordPracticeTime(openedAt, spent, "CONVERSATION") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(container: AppContainer, lessonId: String?, onBack: () -> Unit, onOpenSettings: () -> Unit) {
    val vm = containerViewModel(key = "conv-$lessonId") { ConversationViewModel(container, lessonId) }
    val s by vm.state.collectAsState()
    val rec by vm.recorder.state.collectAsState()
    val speaking by container.voice.speaking.collectAsState()
    val listState = rememberLazyListState()
    var typed by remember { mutableStateOf("") }
    val context = LocalContext.current
    var hasMic by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasMic = granted
        if (granted) vm.toggleRecording()
    }

    LaunchedEffect(s.lines.size, s.lines.lastOrNull()?.text?.length) {
        if (s.lines.isNotEmpty()) listState.animateScrollToItem(s.lines.lastIndex)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.title, maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { if (!s.ended && s.unavailable == null && !s.starting) TextButton(onClick = vm::end) { Text("End") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when {
                s.starting -> Column(Modifier.padding(24.dp)) {
                    Text("Your coach is getting ready…"); LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
                }
                s.unavailable != null -> Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CoachBubble(s.unavailable!!)
                    Button(onClick = onOpenSettings) { Text("Set up the AI coach") }
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        itemsIndexed(s.lines) { _, line ->
                            if (line.fromCoach) CoachBubble(line.text.ifEmpty { "…" }) else LearnerBubble(line.text)
                        }
                        if (s.ending) item { Text("Your coach is writing a short review…", style = MaterialTheme.typography.bodyMedium) }
                        s.review?.let { r ->
                            item {
                                SectionCard(title = "Review") {
                                    Text(r.spoken, style = MaterialTheme.typography.bodyLarge)
                                    r.corrections.forEach { corr ->
                                        Text("• ${corr.description}", style = MaterialTheme.typography.bodyMedium)
                                        corr.corrected?.let { Text("   Better: $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
                                        Text("   ${corr.explanation}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (r.corrections.isNotEmpty()) Text("Saved to your mistakes notebook.", style = MaterialTheme.typography.labelMedium)
                                    EvidenceNote(r.evidence, r.fromFallback)
                                }
                            }
                        }
                        if (s.ended && s.review == null && !s.ending) {
                            item { Text("Conversation ended. There wasn't enough to review this time.", style = MaterialTheme.typography.bodyMedium) }
                        }
                    }
                    s.message?.let { Text(it, Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodyMedium) }
                    if (!s.ended) {
                        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                val recording = rec as? RecordingState.Recording
                                if (s.coachTyping) {
                                    OutlinedButton(onClick = vm::stopReply) { Text("Stop the coach") }
                                } else if (s.canTranscribe) {
                                    RecordButton(recording != null, recording?.level ?: 0f, enabled = !s.transcribing && !speaking) {
                                        if (hasMic) vm.toggleRecording() else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                    Text(
                                        when {
                                            recording != null -> "Listening… tap when you've finished"
                                            s.transcribing -> "Writing down what you said…"
                                            speaking -> "Coach is speaking…"
                                            else -> "Tap and answer aloud"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        value = typed,
                                        onValueChange = { typed = it },
                                        modifier = Modifier.weight(1f),
                                        placeholder = { Text("…or type your answer") },
                                        singleLine = true,
                                    )
                                    IconButton(onClick = { vm.send(typed); typed = "" }, enabled = typed.isNotBlank() && !s.coachTyping) {
                                        Icon(Icons.AutoMirrored.Filled.Send, "Send")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
