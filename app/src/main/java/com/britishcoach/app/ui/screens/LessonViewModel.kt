package com.britishcoach.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.britishcoach.app.AppContainer
import com.britishcoach.app.audio.RecordingState
import com.britishcoach.app.data.repo.NewAttempt
import com.britishcoach.app.speech.TranscriberStatus
import com.britishcoach.app.speech.TranscriptionResult
import com.britishcoach.core.analysis.TranscriptComparer
import com.britishcoach.core.analysis.TranscriptComparison
import com.britishcoach.core.coach.Correction
import com.britishcoach.core.coach.StructuredFeedback
import com.britishcoach.core.learning.LessonUnlockPolicy
import com.britishcoach.core.model.ErrorCategory
import com.britishcoach.core.model.EvidenceLevel
import com.britishcoach.core.model.LessonCategory
import com.britishcoach.core.model.LessonContent
import com.britishcoach.core.model.PracticeMode
import com.britishcoach.core.model.Stage
import com.britishcoach.core.model.PracticeItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File

enum class Processing { NONE, TRANSCRIBING, THINKING }

data class LessonUiState(
    val lesson: LessonContent? = null,
    val itemIndex: Int = 0,
    val referenceLoading: Boolean = false,
    val processing: Processing = Processing.NONE,
    val myRecording: File? = null,
    val myRecordingMs: Long = 0,
    val comparison: TranscriptComparison? = null,
    val confidence: Float? = null,
    val feedback: StructuredFeedback? = null,
    val lastAttemptId: Long? = null,
    val attemptNumber: Int = 0,
    val message: String? = null,
    val transcriber: TranscriberStatus = TranscriberStatus.Checking,
    /** Items practised in this visit, so we know when the lesson is complete. */
    val practisedItems: Set<Int> = emptySet(),
    val completed: Boolean = false,
    val reviewingMistakeId: Long? = null,
) {
    val item: PracticeItem? get() = lesson?.items?.getOrNull(itemIndex)
    val isLastItem: Boolean get() = lesson != null && itemIndex >= lesson.items.lastIndex
}

/**
 * Drives one repeat-after-the-coach lesson: reference playback, recording,
 * offline transcription, comparison, feedback, saving and revision.
 */
class LessonViewModel(
    private val c: AppContainer,
    lessonId: String,
    startItem: Int,
    mistakeId: Long?,
) : ViewModel() {

    private val _state = MutableStateFlow(LessonUiState())
    val state: StateFlow<LessonUiState> = _state.asStateFlow()
    val recording: StateFlow<RecordingState> get() = recorder.state
    val playingPath = c.player.playing
    val speaking = c.voice.speaking

    private val recorder = com.britishcoach.app.audio.AudioRecorder()
    private val openedAt = System.currentTimeMillis()
    private var processJob: Job? = null
    private val json = Json { encodeDefaults = true }

    init {
        viewModelScope.launch { open(c.lessons.lesson(lessonId), startItem, mistakeId) }
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

    private suspend fun open(lesson: LessonContent?, startItem: Int, mistakeId: Long?) {
        val mistake = mistakeId?.let { c.mistakes.byId(it) }
        if (mistakeId != null && mistake == null) {
            _state.value = LessonUiState(message = "This notebook item no longer exists.")
            return
        }
        val itemInLesson = mistake?.let { m -> lesson?.items?.indexOfFirst { it.text == m.example }?.takeIf { it >= 0 } }
        if (mistake != null && itemInLesson == null) {
            // A notebook item that isn't a lesson sentence (e.g. from conversation): practise it on its own.
            _state.value = LessonUiState(
                lesson = LessonContent(
                    id = NOTEBOOK_LESSON_ID,
                    title = "Notebook practice",
                    stage = Stage.FOUNDATIONS,
                    category = LessonCategory.SENTENCES,
                    lessonOrder = 0,
                    mode = PracticeMode.REPEAT_AFTER_COACH,
                    introduction = mistake.explanation,
                    items = listOf(PracticeItem(mistake.correctedText ?: mistake.example)),
                    tips = listOfNotNull(mistake.exercise.takeIf { it.isNotBlank() }),
                ),
                reviewingMistakeId = mistakeId,
            )
        } else if (lesson == null) {
            _state.value = LessonUiState(message = "This lesson couldn't be found.")
            return
        } else {
            // Respect unlocks even when opened from a deep link; revision may open any lesson.
            val unlocked = LessonUnlockPolicy.unlockedIds(c.lessons.lessons, c.lessons.progress())
            if (lesson.id !in unlocked && mistake == null) {
                _state.value = LessonUiState(message = "Finish the earlier lessons to unlock this one.")
                return
            }
            val index = itemInLesson ?: startItem.coerceIn(0, lesson.items.lastIndex)
            _state.value = LessonUiState(lesson = lesson, itemIndex = index, reviewingMistakeId = mistakeId)
        }
        loadAttemptNumber()
        _state.update { it.copy(transcriber = c.transcriber.status()) }
    }

    private suspend fun loadAttemptNumber() {
        val s = _state.value
        val lesson = s.lesson ?: return
        val item = s.item ?: return
        _state.update { it.copy(attemptNumber = c.practice.attemptCount(lesson.id, item.text)) }
    }

    fun playReference(slow: Boolean = false) {
        val item = _state.value.item ?: return
        c.player.stop()
        viewModelScope.launch {
            val rate = c.settings.current().speechRate
            val ok = c.voice.speak(spokenForm(item.text), if (slow) rate * 0.7f else rate)
            if (!ok) _state.update { it.copy(message = "The reference voice isn't available. Check the voice settings.") }
        }
        // Cache the reference audio so it can be replayed and stored with the lesson.
        val lesson = _state.value.lesson ?: return
        if (_state.value.itemIndex == 0) {
            viewModelScope.launch {
                c.voice.synthesizeToCache(spokenForm(item.text))?.let { c.lessons.setReferenceAudio(lesson.id, it.absolutePath) }
            }
        }
    }

    fun speakFeedback() {
        val f = _state.value.feedback ?: return
        viewModelScope.launch { c.voice.speak(f.spoken) }
    }

    fun toggleRecording() {
        if (recorder.isRecording) {
            recorder.stop()
            return
        }
        c.voice.stop()
        c.player.stop()
        _state.update { it.copy(message = null, feedback = null, comparison = null) }
        recorder.start(c.recordings.newRecordingFile())
    }

    fun playMine() {
        val f = _state.value.myRecording ?: return
        c.voice.stop()
        if (playingPath.value == f.absolutePath) c.player.stop()
        else c.player.play(f) { msg -> _state.update { it.copy(message = msg) } }
    }

    private fun onRecorded(rec: RecordingState.Finished) {
        recorder.reset()
        _state.update { it.copy(myRecording = rec.file, myRecordingMs = rec.durationMs) }
        if (rec.durationMs < 400) {
            _state.update { it.copy(message = "That recording was very short. Tap the microphone, say the sentence, then tap again to stop.") }
            return
        }
        if (rec.tooQuiet) {
            _state.update { it.copy(message = "The recording is very quiet. Try speaking a little louder or closer to the phone.") }
        }
        processJob?.cancel()
        processJob = viewModelScope.launch { process(rec) }
    }

    private suspend fun process(rec: RecordingState.Finished) {
        val s = _state.value
        val lesson = s.lesson ?: return
        val item = s.item ?: return
        val now = System.currentTimeMillis()
        val attemptNumber = c.practice.attemptCount(lesson.id, item.text) + 1

        if (s.transcriber !is TranscriberStatus.Ready) {
            // No offline recogniser: keep the recording for self-comparison and say so honestly.
            saveAttempt(lesson, item, "", null, rec, now, "Transcription isn't available, so compare your recording with the reference yourself.", EvidenceLevel.NONE, false)
            _state.update {
                it.copy(
                    attemptNumber = attemptNumber,
                    feedback = StructuredFeedback(
                        spoken = "Listen to your recording and then the reference. Do the stressed syllables match? When you're happy, move on.",
                        evidence = EvidenceLevel.NONE,
                        fromFallback = true,
                    ),
                    practisedItems = it.practisedItems + it.itemIndex,
                )
            }
            return
        }

        _state.update { it.copy(processing = Processing.TRANSCRIBING) }
        val result = c.transcriber.transcribe(rec.file)
        val (text, conf) = when (result) {
            is TranscriptionResult.Success -> result.transcription.text to result.transcription.confidence
            TranscriptionResult.NoSpeech -> "" to null
            is TranscriptionResult.Error -> {
                _state.update { it.copy(processing = Processing.NONE, message = result.message) }
                return
            }
        }
        val comparison = TranscriptComparer.compare(item.text, text)
        _state.update { it.copy(processing = Processing.THINKING, comparison = comparison, confidence = conf) }

        val settings = c.settings.current()
        val previous = c.practice.latestAttempt(lesson.id, item.text)
        val stage = LessonUnlockPolicy.currentStage(c.lessons.lessons, c.lessons.progress())
        val feedback = c.coach.feedbackForAttempt(
            lesson, item, comparison, conf, attemptNumber, previous?.matchedWordCount, stage, settings.feedbackDetail,
        )
        val id = saveAttempt(lesson, item, text, comparison, rec, now, feedback.spoken, feedback.evidence, !feedback.fromFallback, conf)
        if (feedback.corrections.isNotEmpty() && !comparison.isExactMatch) {
            c.mistakes.saveCorrections(lesson.id, item.text, text, feedback.corrections.withDefaults(item), now)
        }
        s.reviewingMistakeId?.let { c.mistakes.reviewed(it, success = comparison.isExactMatch, now = now) }

        _state.update {
            it.copy(
                processing = Processing.NONE,
                feedback = feedback,
                lastAttemptId = id,
                attemptNumber = attemptNumber,
                practisedItems = it.practisedItems + it.itemIndex,
            )
        }
        c.voice.speak(feedback.spoken)
    }

    private fun List<Correction>.withDefaults(item: PracticeItem) = map {
        if (it.category == ErrorCategory.TRANSCRIPT_DIFFERENCE && it.corrected == null) it.copy(corrected = item.text) else it
    }

    private suspend fun saveAttempt(
        lesson: LessonContent,
        item: PracticeItem,
        recognized: String,
        comparison: TranscriptComparison?,
        rec: RecordingState.Finished,
        now: Long,
        feedback: String,
        evidence: EvidenceLevel,
        usedAi: Boolean,
        confidence: Float? = null,
    ): Long {
        val assessment = comparison?.let {
            json.encodeToString(AssessmentData.serializer(), AssessmentData(it, confidence))
        }
        val id = c.practice.saveAttempt(
            NewAttempt(
                lessonId = lesson.id,
                timestamp = now,
                targetText = item.text,
                recognizedText = recognized,
                durationMs = rec.durationMs,
                feedback = feedback,
                assessmentJson = assessment,
                evidence = evidence.name,
                targetWordCount = comparison?.targetWordCount ?: 0,
                matchedWordCount = comparison?.matchedCount ?: 0,
                differingWords = comparison?.differingTargetWords.orEmpty(),
                recordingPath = rec.file.absolutePath,
                usedAi = usedAi,
            ),
        )
        if (lesson.id != NOTEBOOK_LESSON_ID) c.lessons.recordAttempt(lesson.id)
        return id
    }

    fun retry() {
        c.player.stop()
        _state.update { it.copy(feedback = null, comparison = null, message = null, myRecording = null) }
    }

    fun rateDifficulty(rating: Int) {
        val id = _state.value.lastAttemptId ?: return
        viewModelScope.launch {
            c.practice.rateDifficulty(id, rating)
            _state.update { it.copy(message = "Thanks, noted.") }
        }
    }

    fun next() {
        val s = _state.value
        val lesson = s.lesson ?: return
        c.voice.stop()
        c.player.stop()
        if (s.isLastItem) {
            viewModelScope.launch {
                if (s.practisedItems.isNotEmpty() && lesson.id != NOTEBOOK_LESSON_ID) c.lessons.markCompleted(lesson.id, System.currentTimeMillis())
                _state.update { it.copy(completed = true) }
            }
        } else {
            _state.update {
                it.copy(itemIndex = it.itemIndex + 1, feedback = null, comparison = null, myRecording = null, message = null, lastAttemptId = null)
            }
            viewModelScope.launch { loadAttemptNumber() }
        }
    }

    fun previous() {
        if (_state.value.itemIndex == 0) return
        _state.update { it.copy(itemIndex = it.itemIndex - 1, feedback = null, comparison = null, myRecording = null, message = null) }
        viewModelScope.launch { loadAttemptNumber() }
    }

    fun refreshTranscriber() {
        viewModelScope.launch { _state.update { it.copy(transcriber = c.transcriber.status()) } }
    }

    fun downloadSpeechPack() {
        viewModelScope.launch {
            val ok = c.transcriber.downloadLanguagePack()
            _state.update {
                it.copy(
                    message = if (ok) "Downloading the offline British English speech pack. This happens once." else "The speech pack couldn't be downloaded on this phone.",
                    transcriber = c.transcriber.status(),
                )
            }
        }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    override fun onCleared() {
        recorder.cancel()
        c.voice.stop()
        c.player.stop()
        val spent = (System.currentTimeMillis() - openedAt).coerceAtMost(MAX_SESSION_MS)
        c.appScope.launch { c.practice.recordPracticeTime(openedAt, spent, "LESSON") }
    }

    companion object {
        private const val MAX_SESSION_MS = 45L * 60 * 1000
        const val NOTEBOOK_LESSON_ID = "notebook"

        /** Removes lesson annotations like "(noun)" and splits word pairs so TTS reads them clearly. */
        fun spokenForm(text: String): String =
            text.replace(Regex("\\([^)]*\\)"), "").replace(", ", "... ").trim()
    }
}

@kotlinx.serialization.Serializable
data class AssessmentData(
    val comparison: TranscriptComparison,
    val recognizerConfidence: Float?,
    /** Filled only when a validated phoneme-level engine is added. */
    val phonemeAssessment: String? = null,
)
