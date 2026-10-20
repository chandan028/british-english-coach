package com.britishcoach.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Hearing
import androidx.compose.material.icons.outlined.SlowMotionVideo
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.britishcoach.app.AppContainer
import com.britishcoach.app.audio.RecordingState
import com.britishcoach.app.speech.TranscriberStatus
import com.britishcoach.app.ui.components.CoachBubble
import com.britishcoach.app.ui.components.EvidenceNote
import com.britishcoach.app.ui.components.RecordButton
import com.britishcoach.app.ui.components.SectionCard
import com.britishcoach.app.ui.containerViewModel
import com.britishcoach.app.ui.theme.TargetTextStyle
import com.britishcoach.core.analysis.TranscriptComparison
import com.britishcoach.core.analysis.WordDiff
import com.britishcoach.core.model.PracticeMode

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LessonScreen(
    container: AppContainer,
    lessonId: String,
    startItem: Int,
    mistakeId: Long?,
    onBack: () -> Unit,
    onConversation: (String) -> Unit,
) {
    val vm = containerViewModel(key = "lesson-$lessonId-$mistakeId") { LessonViewModel(container, lessonId, startItem, mistakeId) }
    val s by vm.state.collectAsState()
    val rec by vm.recording.collectAsState()
    val playing by vm.playingPath.collectAsState()
    val speaking by vm.speaking.collectAsState()

    val context = LocalContext.current
    var hasMic by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    var micDenied by remember { mutableStateOf(false) }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasMic = granted
        micDenied = !granted
        if (granted) vm.toggleRecording()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.lesson?.title ?: "Lesson") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val lesson = s.lesson
        val item = s.item
        if (lesson == null || item == null) {
            Column(Modifier.padding(padding).padding(24.dp)) {
                Text(s.message ?: "Loading…", style = MaterialTheme.typography.bodyLarge)
            }
            return@Scaffold
        }

        if (s.completed) {
            Column(Modifier.padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CoachBubble("Well done for working through \"${lesson.title}\". Come back to it any time; it will also come up again for a quick review in a few days.")
                if (lesson.mode == PracticeMode.GUIDED_CONVERSATION) {
                    Button(onClick = { onConversation(lesson.id) }, modifier = Modifier.fillMaxWidth()) { Text("Practise in a conversation") }
                }
                OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back to lessons") }
            }
            return@Scaffold
        }

        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (s.itemIndex == 0 && s.feedback == null && s.reviewingMistakeId == null) CoachBubble(lesson.introduction)

            // Target sentence
            SectionCard {
                Text("${s.itemIndex + 1} of ${lesson.items.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(item.text, style = TargetTextStyle)
                item.phonetic?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary) }
                item.stressGuide?.let { Text("Stress: $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { vm.playReference() }) {
                        Icon(Icons.AutoMirrored.Outlined.VolumeUp, null); Spacer(Modifier.padding(3.dp)); Text("Listen")
                    }
                    OutlinedButton(onClick = { vm.playReference(slow = true) }) {
                        Icon(Icons.Outlined.SlowMotionVideo, null); Spacer(Modifier.padding(3.dp)); Text("Slowly")
                    }
                }
            }

            // Recording
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                val recording = rec as? RecordingState.Recording
                RecordButton(
                    recording = recording != null,
                    level = recording?.level ?: 0f,
                    enabled = s.processing == Processing.NONE && !speaking,
                ) {
                    if (hasMic) vm.toggleRecording() else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
                Text(
                    when {
                        recording != null -> "Recording… ${recording.elapsedMs / 1000}s · tap to stop"
                        s.processing == Processing.TRANSCRIBING -> "Writing down what you said…"
                        s.processing == Processing.THINKING -> "Your coach is preparing feedback…"
                        speaking -> "Listening to the coach…"
                        else -> "Tap to record your attempt"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (recording != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (s.processing != Processing.NONE) {
                    Spacer(Modifier.height(8.dp)); CircularProgressIndicator()
                }
                if (micDenied) {
                    Text(
                        "The coach needs microphone access to record you. You can allow it in Android Settings › Apps › British English Coach › Permissions.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center,
                    )
                }
                s.myRecording?.let { file ->
                    val isPlaying = playing == file.absolutePath
                    TextButton(onClick = { vm.playMine() }) {
                        Icon(if (isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow, null)
                        Text(if (isPlaying) "Stop" else "Play my recording (${s.myRecordingMs / 1000.0}s)")
                    }
                }
            }

            TranscriberNotice(s.transcriber, onDownload = vm::downloadSpeechPack, onRecheck = vm::refreshTranscriber)

            s.message?.let { msg ->
                Text(msg, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
            }

            AnimatedVisibility(s.feedback != null) {
                val f = s.feedback ?: return@AnimatedVisibility
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CoachBubble(f.spoken)
                    s.comparison?.let { WordComparison(it) }
                    f.corrections.firstOrNull()?.let { c ->
                        SectionCard(title = c.category.label) {
                            Text(c.explanation, style = MaterialTheme.typography.bodyMedium)
                            if (c.exercise.isNotBlank()) Text("Try this: ${c.exercise}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                    }
                    EvidenceNote(f.evidence, f.fromFallback)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = vm::speakFeedback) { Icon(Icons.Outlined.Hearing, null); Text(" Hear again") }
                    }
                    Text("How did that feel?", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = { vm.rateDifficulty(1) }, label = { Text("Easy") })
                        AssistChip(onClick = { vm.rateDifficulty(2) }, label = { Text("OK") })
                        AssistChip(onClick = { vm.rateDifficulty(3) }, label = { Text("Hard") })
                    }
                }
            }

            if (lesson.tips.isNotEmpty() && s.feedback == null) {
                SectionCard(title = "Tips") {
                    lesson.tips.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = vm::retry, modifier = Modifier.weight(1f), enabled = s.feedback != null) { Text("Try again") }
                Button(onClick = vm::next, modifier = Modifier.weight(1f)) {
                    Text(if (s.isLastItem) "Finish" else "Next")
                }
            }
            if (s.itemIndex > 0) TextButton(onClick = vm::previous) { Text("Previous item") }
            if (lesson.mode == PracticeMode.GUIDED_CONVERSATION) {
                OutlinedButton(onClick = { onConversation(lesson.id) }, modifier = Modifier.fillMaxWidth()) { Text("Skip to the conversation") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    LaunchedEffect(Unit) { vm.refreshTranscriber() }
}

@Composable
private fun TranscriberNotice(status: TranscriberStatus, onDownload: () -> Unit, onRecheck: () -> Unit) {
    when (status) {
        TranscriberStatus.Ready, TranscriberStatus.Checking -> Unit
        TranscriberStatus.LanguagePackMissing -> SectionCard(title = "Offline speech recognition") {
            Text("To write down what you say without the internet, the phone needs its British English speech pack. It's a one-time download.", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onDownload) { Text("Download speech pack") }
        }
        TranscriberStatus.DownloadingLanguagePack -> SectionCard(title = "Offline speech recognition") {
            Text("The British English speech pack is downloading. You can keep practising with listening and replay meanwhile.", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onRecheck) { Text("Check again") }
        }
        is TranscriberStatus.Unavailable -> SectionCard(title = "Transcription unavailable") {
            Text(status.reason, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Shows the target words, marking ones the recogniser wrote differently or missed. */
@Composable
fun WordComparison(c: TranscriptComparison) {
    SectionCard(title = "What the recogniser wrote") {
        Text(if (c.recognized.isBlank()) "(nothing recognised)" else "\"${c.recognized}\"", style = MaterialTheme.typography.bodyLarge)
        val matched = MaterialTheme.colorScheme.primary
        val diff = MaterialTheme.colorScheme.secondary
        val text = buildAnnotatedString {
            c.diffs.forEach { d ->
                when (d) {
                    is WordDiff.Match -> withStyle(SpanStyle(color = matched)) { append(d.word) }
                    is WordDiff.Substitution -> withStyle(SpanStyle(color = diff, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)) {
                        append(d.expected)
                    }
                    is WordDiff.Omission -> withStyle(SpanStyle(color = diff, textDecoration = TextDecoration.LineThrough)) { append(d.expected) }
                    is WordDiff.Insertion -> withStyle(SpanStyle(color = diff, fontWeight = FontWeight.Light)) { append("+${d.heard}") }
                }
                append(" ")
            }
        }
        Text(text, style = MaterialTheme.typography.bodyLarge)
        Text(c.matchedWordsLabel + ". Underlined: written differently. Struck through: missing.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
