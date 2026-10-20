package com.britishcoach.app

import android.content.Context
import com.britishcoach.app.ai.CoachModel
import com.britishcoach.app.ai.CoachService
import com.britishcoach.app.ai.LiteRtCoachModel
import com.britishcoach.app.ai.ModelDownloader
import com.britishcoach.app.ai.ModelFileState
import com.britishcoach.app.audio.AudioPlayer
import com.britishcoach.app.audio.CoachVoice
import com.britishcoach.app.data.db.AppDatabase
import com.britishcoach.app.data.repo.BackupRepository
import com.britishcoach.app.data.repo.FileRecordingStore
import com.britishcoach.app.data.repo.LessonRepository
import com.britishcoach.app.data.repo.MistakeRepository
import com.britishcoach.app.data.repo.PracticeRepository
import com.britishcoach.app.data.repo.RecordingStore
import com.britishcoach.app.data.repo.RoomBackupRepository
import com.britishcoach.app.data.repo.RoomLessonRepository
import com.britishcoach.app.data.repo.RoomMistakeRepository
import com.britishcoach.app.data.repo.RoomPracticeRepository
import com.britishcoach.app.data.repo.RoomSettingsRepository
import com.britishcoach.app.data.repo.SettingsRepository
import com.britishcoach.app.speech.OnDeviceSpeechTranscriber
import com.britishcoach.app.speech.SpeechTranscriber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Hand-written dependency container. Small enough that a DI framework isn't worth it. */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val db = AppDatabase.create(context)

    val settings: SettingsRepository = RoomSettingsRepository(db)
    val lessons: LessonRepository = RoomLessonRepository(db)
    val practice: PracticeRepository = RoomPracticeRepository(db)
    val mistakes: MistakeRepository = RoomMistakeRepository(db)
    val backup: BackupRepository = RoomBackupRepository(db, settings)
    val recordings: RecordingStore = FileRecordingStore(context, db)

    val voice = CoachVoice(context)
    val player = AudioPlayer()
    val transcriber: SpeechTranscriber = OnDeviceSpeechTranscriber(context)

    val downloader = ModelDownloader(context)
    val model: CoachModel = LiteRtCoachModel(context)
    val coach = CoachService(model)

    fun start() {
        appScope.launch {
            lessons.seedCatalogue()
            val s = settings.current()
            voice.configure(s.preferredVoice, s.speechRate)
            recordings.applyRetention(s.recordingRetentionDays, System.currentTimeMillis())
            downloader.refresh(s.modelId)
        }
        // Keep the voice in sync with settings.
        appScope.launch {
            settings.settings.map { it.preferredVoice to it.speechRate }.distinctUntilChanged()
                .collect { (v, r) -> voice.configure(v, r) }
        }
        // Load the model in the background whenever a model file becomes available.
        appScope.launch {
            downloader.state.collect { st ->
                when (st) {
                    is ModelFileState.Ready -> {
                        val gpu = settings.settings.first().useGpu
                        settings.update { it.copy(modelId = st.option.id) }
                        model.load(st.file, gpu)
                    }
                    ModelFileState.NotDownloaded -> model.markNotInstalled()
                    else -> Unit
                }
            }
        }
    }

    /** Re-loads the model, e.g. after changing the GPU setting or after a memory failure. */
    fun reloadModel() {
        val st = downloader.state.value
        if (st is ModelFileState.Ready) {
            appScope.launch {
                model.unload()
                model.load(st.file, settings.current().useGpu)
            }
        }
    }
}
