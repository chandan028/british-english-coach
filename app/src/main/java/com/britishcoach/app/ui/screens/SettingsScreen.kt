package com.britishcoach.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.britishcoach.app.AppContainer
import com.britishcoach.app.audio.VoiceStatus
import com.britishcoach.app.data.repo.AppSettings
import com.britishcoach.app.speech.TranscriberStatus
import com.britishcoach.app.ui.components.ModelSetupCard
import com.britishcoach.app.ui.components.ScreenTitle
import com.britishcoach.app.ui.components.SectionCard
import com.britishcoach.core.model.FeedbackDetail
import com.britishcoach.core.model.ModelCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer) {
    val settings by container.settings.settings.collectAsState(initial = AppSettings())
    val voiceStatus by container.voice.status.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }
    var confirmDeleteRecordings by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<String?>(null) }
    var transcriber by remember { mutableStateOf<TranscriberStatus>(TranscriberStatus.Checking) }
    LaunchedEffect(Unit) { transcriber = container.transcriber.status() }

    fun update(t: (AppSettings) -> AppSettings) = scope.launch { container.settings.update(t) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            message = runCatching {
                val json = container.backup.exportJson(System.currentTimeMillis())
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } }
                "Progress exported."
            }.getOrElse { "Export failed: ${it.message}" }
        }
    }
    val restorer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            pendingRestore = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
            }
            if (pendingRestore == null) message = "That file couldn't be read."
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenTitle("Settings")
        message?.let { Text(it, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodyMedium) }

        // ---------- Voice ----------
        SectionCard(title = "Coach's voice") {
            when (val v = voiceStatus) {
                VoiceStatus.Initialising -> Text("Checking the voices on this phone…")
                is VoiceStatus.Unavailable -> Text(v.message, color = MaterialTheme.colorScheme.error)
                VoiceStatus.NoBritishVoice -> {
                    Text("No British English voice is installed. Install one from your speech engine's voice data (for example Speech Services by Google › Install voice data › English (United Kingdom)).", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { runCatching { context.startActivity(container.voice.installVoiceDataIntent()) } }) { Text("Install voice data") }
                    TextButton(onClick = { container.voice.refreshVoices() }) { Text("Check again") }
                }
                is VoiceStatus.Ready -> {
                    Text(
                        "Android doesn't tell apps whether a voice is female or male, so preview the British voices and choose the one you'd like as your coach.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (v.voices.isEmpty()) Text("Using the phone's default British English voice.", style = MaterialTheme.typography.bodyMedium)
                    v.voices.forEach { opt ->
                        Row(
                            Modifier.fillMaxWidth().selectable(
                                selected = v.selected?.name == opt.name,
                                role = Role.RadioButton,
                                onClick = { update { it.copy(preferredVoice = opt.name) } },
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = v.selected?.name == opt.name, onClick = null)
                            Text(opt.label, Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = {
                                scope.launch {
                                    container.voice.configure(opt.name, settings.speechRate)
                                    container.voice.speak("Hello, I'm your English coach. Lovely to meet you.")
                                    container.voice.configure(settings.preferredVoice ?: opt.name, settings.speechRate)
                                }
                            }) { Text("Preview") }
                        }
                    }
                    TextButton(onClick = { runCatching { context.startActivity(container.voice.installVoiceDataIntent()) } }) { Text("Get more voices") }
                }
            }
            var rate by remember(settings.speechRate) { mutableFloatStateOf(settings.speechRate) }
            Text("Speech speed: ${"%.2f".format(rate)}×", style = MaterialTheme.typography.labelLarge)
            Slider(value = rate, onValueChange = { rate = it }, valueRange = 0.5f..1.3f, steps = 7, onValueChangeFinished = { update { it.copy(speechRate = rate) } })
            OutlinedButton(onClick = { scope.launch { container.voice.speak("Let's practise that sentence once more.", rate) } }) { Text("Test speed") }
        }

        // ---------- Learning ----------
        SectionCard(title = "Daily practice") {
            var goal by remember(settings.dailyGoalMinutes) { mutableFloatStateOf(settings.dailyGoalMinutes.toFloat()) }
            Text("Daily goal: ${goal.toInt()} minutes", style = MaterialTheme.typography.labelLarge)
            Slider(value = goal, onValueChange = { goal = it }, valueRange = 5f..60f, steps = 10, onValueChangeFinished = { update { it.copy(dailyGoalMinutes = goal.toInt()) } })
            Text("Feedback detail", style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                FeedbackDetail.entries.forEachIndexed { i, d ->
                    SegmentedButton(
                        selected = settings.feedbackDetail == d,
                        onClick = { update { it.copy(feedbackDetail = d) } },
                        shape = SegmentedButtonDefaults.itemShape(i, FeedbackDetail.entries.size),
                    ) { Text(d.name.lowercase().replaceFirstChar { it.uppercase() }) }
                }
            }
        }

        // ---------- AI ----------
        ModelSetupCard(container, compact = false)
        SectionCard(title = "AI model options") {
            ModelCatalog.options.forEach { opt ->
                Row(
                    Modifier.fillMaxWidth().selectable(selected = ModelCatalog.byId(settings.modelId).id == opt.id, role = Role.RadioButton) {
                        update { it.copy(modelId = opt.id) }
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = ModelCatalog.byId(settings.modelId).id == opt.id, onClick = null)
                    Column(Modifier.padding(start = 8.dp)) {
                        Text(opt.displayName, style = MaterialTheme.typography.bodyMedium)
                        Text(opt.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            SwitchRow("Download only on Wi-Fi", settings.downloadOnWifiOnly) { v -> update { it.copy(downloadOnWifiOnly = v) } }
            SwitchRow("Use the GPU when possible", settings.useGpu) { v ->
                scope.launch { container.settings.update { it.copy(useGpu = v) }; container.reloadModel() }
            }
        }

        // ---------- Speech recognition ----------
        SectionCard(title = "Offline speech recognition") {
            Text(
                when (val t = transcriber) {
                    TranscriberStatus.Checking -> "Checking…"
                    TranscriberStatus.Ready -> "Ready. Your speech is transcribed on the phone, without the internet."
                    TranscriberStatus.LanguagePackMissing -> "The British English speech pack isn't installed yet."
                    TranscriberStatus.DownloadingLanguagePack -> "The speech pack is downloading."
                    is TranscriberStatus.Unavailable -> t.reason
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (transcriber == TranscriberStatus.LanguagePackMissing) {
                Button(onClick = { scope.launch { container.transcriber.downloadLanguagePack(); transcriber = container.transcriber.status() } }) {
                    Text("Download speech pack")
                }
            }
        }

        // ---------- Recordings & privacy ----------
        SectionCard(title = "Recordings and privacy") {
            Text("Everything stays on this phone. There's no account, no tracking and no cloud processing.", style = MaterialTheme.typography.bodyMedium)
            Text("Keep my recordings for", style = MaterialTheme.typography.labelLarge)
            val choices = listOf(7 to "7 days", 30 to "30 days", 90 to "90 days", 0 to "Until I delete")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                choices.forEachIndexed { i, (days, label) ->
                    SegmentedButton(
                        selected = settings.recordingRetentionDays == days,
                        onClick = {
                            scope.launch {
                                container.settings.update { it.copy(recordingRetentionDays = days) }
                                container.recordings.applyRetention(days, System.currentTimeMillis())
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(i, choices.size),
                    ) { Text(label, style = MaterialTheme.typography.labelSmall) }
                }
            }
            OutlinedButton(onClick = { confirmDeleteRecordings = true }) { Text("Delete all recordings") }
        }

        SectionCard(title = "Backup") {
            Text("Export your progress, attempts and notebook to a file, and restore it later or on a new phone. Recordings aren't included.", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    exporter.launch("british-coach-backup-${SimpleDateFormat("yyyy-MM-dd", Locale.UK).format(Date())}.json")
                }) { Text("Export") }
                OutlinedButton(onClick = { restorer.launch(arrayOf("application/json", "text/*", "*/*")) }) { Text("Restore") }
            }
        }
    }

    if (confirmDeleteRecordings) {
        AlertDialog(
            onDismissRequest = { confirmDeleteRecordings = false },
            title = { Text("Delete all recordings?") },
            text = { Text("Your saved audio will be removed. Transcripts, feedback and progress are kept.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteRecordings = false
                    scope.launch { container.recordings.deleteAllRecordings(); message = "All recordings deleted." }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteRecordings = false }) { Text("Cancel") } },
        )
    }

    pendingRestore?.let { json ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Restore this backup?") },
            text = { Text("Your current progress, attempts and notebook will be replaced by the backup.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    scope.launch {
                        message = runCatching { container.backup.restoreJson(json); "Backup restored." }
                            .getOrElse { it.message ?: "That backup couldn't be restored." }
                    }
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
