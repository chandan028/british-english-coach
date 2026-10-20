package com.britishcoach.app.ai

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.StatFs
import com.britishcoach.core.model.ModelCatalog
import com.britishcoach.core.model.ModelOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface ModelFileState {
    data object NotDownloaded : ModelFileState
    data class Downloading(val option: ModelOption, val downloadedBytes: Long, val totalBytes: Long, val waitingReason: String?) : ModelFileState {
        val fraction: Float? get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else null
    }
    data class Importing(val fraction: Float?) : ModelFileState
    data class Ready(val option: ModelOption, val file: File) : ModelFileState
    data class Failed(val message: String) : ModelFileState
}

/**
 * Downloads the Gemma model with one tap, using Android's DownloadManager so a
 * 2–3 GB download survives the app being closed, shows a system notification
 * and resumes after connection drops. After that the app never needs the network.
 *
 * The file lives in the app's own storage (Android/data/<app>/files/models), not
 * inside the APK, and is removed if the app is uninstalled.
 */
class ModelDownloader(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dm = context.getSystemService(DownloadManager::class.java)
    private val prefs = context.getSharedPreferences("model_download", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow<ModelFileState>(ModelFileState.NotDownloaded)
    val state: StateFlow<ModelFileState> = _state.asStateFlow()
    private var pollJob: Job? = null

    val modelsDir: File get() = File(context.getExternalFilesDir(null) ?: context.filesDir, "models").apply { mkdirs() }

    fun fileFor(option: ModelOption) = File(modelsDir, option.fileName)

    /** Restores state on app start: an installed model, or a download still in progress. */
    fun refresh(preferredId: String?) {
        val activeId = prefs.getLong(KEY_ID, -1L)
        val activeOption = prefs.getString(KEY_OPTION, null)?.let { ModelCatalog.byId(it) }
        if (activeId >= 0 && activeOption != null) {
            poll(activeId, activeOption)
            return
        }
        val preferred = ModelCatalog.byId(preferredId)
        val installed = (listOf(preferred) + ModelCatalog.options).firstOrNull { isValid(it, fileFor(it)) }
        _state.value = if (installed != null) ModelFileState.Ready(installed, fileFor(installed)) else ModelFileState.NotDownloaded
    }

    fun freeBytes(): Long = runCatching { StatFs(modelsDir.absolutePath).availableBytes }.getOrDefault(0L)

    /** The one-tap download. Returns an error message to show, or null when the download started. */
    fun start(option: ModelOption, wifiOnly: Boolean): String? {
        if (_state.value is ModelFileState.Downloading) return null
        val needed = ModelCatalog.requiredFreeBytes(option)
        if (freeBytes() < needed) {
            return "Not enough free storage. The model needs about ${gb(needed)} free; this phone has ${gb(freeBytes())}."
        }
        val target = fileFor(option)
        target.delete()
        val request = DownloadManager.Request(Uri.parse(option.url))
            .setTitle("Coach AI model")
            .setDescription("${option.displayName}, about ${gb(option.approxBytes)}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, null, "models/${option.fileName}")
            .setAllowedOverMetered(!wifiOnly)
            .setAllowedOverRoaming(false)
        val id = try {
            dm.enqueue(request)
        } catch (e: Exception) {
            return "The download couldn't start: ${e.message ?: "unknown error"}."
        }
        prefs.edit().putLong(KEY_ID, id).putString(KEY_OPTION, option.id).apply()
        _state.value = ModelFileState.Downloading(option, 0, option.approxBytes, null)
        poll(id, option)
        return null
    }

    fun cancel() {
        val id = prefs.getLong(KEY_ID, -1L)
        if (id >= 0) dm.remove(id)
        pollJob?.cancel()
        prefs.getString(KEY_OPTION, null)?.let { fileFor(ModelCatalog.byId(it)).delete() }
        prefs.edit().clear().apply()
        _state.value = ModelFileState.NotDownloaded
    }

    fun delete(option: ModelOption) {
        fileFor(option).delete()
        refresh(null)
    }

    /** For a model file the learner already has (for example copied from a computer). */
    suspend fun importFrom(uri: Uri, option: ModelOption): String? = withContext(Dispatchers.IO) {
        val target = fileFor(option)
        val tmp = File(modelsDir, option.fileName + ".import")
        try {
            val total = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }?.takeIf { it > 0 }
            if (total != null && freeBytes() < total + 200_000_000L) return@withContext "Not enough free storage to copy the model."
            _state.value = ModelFileState.Importing(0f)
            context.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(1 shl 20)
                    var copied = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        copied += n
                        _state.value = ModelFileState.Importing(total?.let { copied.toFloat() / it })
                    }
                }
            } ?: return@withContext "That file couldn't be opened."
            if (!isValid(option, tmp)) {
                tmp.delete()
                refresh(option.id)
                return@withContext "That doesn't look like a LiteRT-LM model file (.litertlm)."
            }
            target.delete()
            tmp.renameTo(target)
            _state.value = ModelFileState.Ready(option, target)
            null
        } catch (e: Exception) {
            tmp.delete()
            refresh(option.id)
            "Copying the model failed: ${e.message ?: "unknown error"}."
        }
    }

    private fun poll(id: Long, option: ModelOption) {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                val cursor = dm.query(DownloadManager.Query().setFilterById(id))
                if (cursor == null || !cursor.moveToFirst()) {
                    cursor?.close()
                    prefs.edit().clear().apply()
                    _state.value = ModelFileState.Failed("The download was cancelled.")
                    return@launch
                }
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val soFar = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                cursor.close()
                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        prefs.edit().clear().apply()
                        val file = fileFor(option)
                        _state.value = if (isValid(option, file)) {
                            ModelFileState.Ready(option, file)
                        } else {
                            file.delete()
                            ModelFileState.Failed("The downloaded file is incomplete or isn't a model file. Please try again.")
                        }
                        return@launch
                    }
                    DownloadManager.STATUS_FAILED -> {
                        prefs.edit().clear().apply()
                        dm.remove(id)
                        _state.value = ModelFileState.Failed(failureMessage(reason))
                        return@launch
                    }
                    DownloadManager.STATUS_PAUSED -> _state.value =
                        ModelFileState.Downloading(option, soFar, if (total > 0) total else option.approxBytes, pausedMessage(reason))
                    else -> _state.value =
                        ModelFileState.Downloading(option, soFar, if (total > 0) total else option.approxBytes, null)
                }
                delay(700)
            }
        }
    }

    private fun isValid(option: ModelOption, file: File): Boolean {
        if (!file.exists() || file.length() < option.minValidBytes) return false
        // Reject HTML error pages saved under the model's name.
        val head = runCatching { file.inputStream().use { s -> ByteArray(16).also { s.read(it) } } }.getOrNull() ?: return false
        val text = String(head, Charsets.ISO_8859_1).trimStart().lowercase()
        return !(text.startsWith("<!doctype") || text.startsWith("<html") || text.startsWith("{"))
    }

    private fun pausedMessage(reason: Int) = when (reason) {
        DownloadManager.PAUSED_QUEUED_FOR_WIFI -> "Waiting for Wi-Fi"
        DownloadManager.PAUSED_WAITING_FOR_NETWORK -> "Waiting for a connection"
        DownloadManager.PAUSED_WAITING_TO_RETRY -> "Connection dropped, retrying"
        else -> "Paused"
    }

    private fun failureMessage(reason: Int) = when (reason) {
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "The phone ran out of storage during the download."
        DownloadManager.ERROR_HTTP_DATA_ERROR, DownloadManager.ERROR_CANNOT_RESUME -> "The connection failed during the download. Please try again."
        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "The phone's storage wasn't available."
        in 400..599 -> "The model server returned error $reason. Please try again later."
        else -> "The download failed (code $reason). Please try again."
    }

    companion object {
        private const val KEY_ID = "download_id"
        private const val KEY_OPTION = "option_id"
        fun gb(bytes: Long) = "%.1f GB".format(bytes / 1_000_000_000.0)
    }
}
