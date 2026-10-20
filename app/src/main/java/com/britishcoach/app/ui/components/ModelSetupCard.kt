package com.britishcoach.app.ui.components

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.britishcoach.app.AppContainer
import com.britishcoach.app.ai.ModelDownloader
import com.britishcoach.app.ai.ModelFileState
import com.britishcoach.app.ai.ModelState
import com.britishcoach.app.data.repo.AppSettings
import com.britishcoach.core.model.ModelCatalog
import kotlinx.coroutines.launch

/**
 * One-tap setup for the AI coach: downloads Gemma once, then everything runs
 * offline. Shows progress, waiting reasons and failures in plain words.
 * [compact] hides the card entirely once the coach is ready (used on Home).
 */
@Composable
fun ModelSetupCard(container: AppContainer, compact: Boolean) {
    val file by container.downloader.state.collectAsState()
    val model by container.model.state.collectAsState()
    val settings by container.settings.settings.collectAsState(initial = AppSettings())
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var askedNotifications by remember { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { error = container.downloader.importFrom(uri, ModelCatalog.byId(settings.modelId)) }
    }

    if (compact && model is ModelState.Ready) return

    val option = ModelCatalog.byId(settings.modelId)
    SectionCard(title = "AI coach") {
        when (val f = file) {
            ModelFileState.NotDownloaded, is ModelFileState.Failed -> {
                Text(
                    "Download the coach's AI model once (${ModelDownloader.gb(option.approxBytes)}). After that, conversations and " +
                        "personalised feedback work completely offline. Lessons already work without it.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (f is ModelFileState.Failed) Text(f.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Button(
                    onClick = {
                        if (!askedNotifications && Build.VERSION.SDK_INT >= 33) {
                            askedNotifications = true
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        error = container.downloader.start(option, settings.downloadOnWifiOnly)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (settings.downloadOnWifiOnly) "Download AI coach (Wi-Fi)" else "Download AI coach") }
                if (!compact) {
                    TextButton(onClick = { importer.launch(arrayOf("*/*")) }) { Text("I already have the model file") }
                }
            }
            is ModelFileState.Downloading -> {
                val pct = f.fraction?.let { " · ${(it * 100).toInt()}%" }.orEmpty()
                Text(
                    "Downloading ${ModelDownloader.gb(f.downloadedBytes)} of ${ModelDownloader.gb(f.totalBytes)}$pct",
                    style = MaterialTheme.typography.bodyMedium,
                )
                val fr = f.fraction
                if (fr != null) LinearProgressIndicator(progress = { fr }, modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    f.waitingReason ?: "You can leave the app; the download continues in the background.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { container.downloader.cancel() }) { Text("Cancel download") }
            }
            is ModelFileState.Importing -> {
                Text("Copying the model file…", style = MaterialTheme.typography.bodyMedium)
                val fr = f.fraction
                if (fr != null) LinearProgressIndicator(progress = { fr }, modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            is ModelFileState.Ready -> when (val m = model) {
                is ModelState.Ready -> Text("Ready and running offline (${m.backend}).", style = MaterialTheme.typography.bodyMedium)
                is ModelState.Loading -> {
                    Text(m.message, style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                is ModelState.Failed -> {
                    Text(m.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { container.reloadModel() }) { Text("Try again") }
                        if (!compact) OutlinedButton(onClick = { container.downloader.delete(f.option) }) { Text("Delete model") }
                    }
                }
                ModelState.Unloaded, ModelState.NotInstalled -> {
                    Text("The model is downloaded but not loaded.", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { container.reloadModel() }) { Text("Load AI coach") }
                }
            }
        }
        if (!compact && file is ModelFileState.Ready && model is ModelState.Ready) {
            val f = file as ModelFileState.Ready
            Text("${f.option.displayName} · ${ModelDownloader.gb(f.file.length())}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { container.downloader.delete(f.option) }) { Text("Delete model to free space") }
        }
    }
}
