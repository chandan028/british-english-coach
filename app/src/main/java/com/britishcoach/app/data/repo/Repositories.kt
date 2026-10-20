package com.britishcoach.app.data.repo

import com.britishcoach.core.coach.Correction
import com.britishcoach.core.learning.DueMistake
import com.britishcoach.core.learning.LessonProgress
import com.britishcoach.core.model.FeedbackDetail
import com.britishcoach.core.model.LessonContent
import com.britishcoach.app.data.db.MistakeEntity
import com.britishcoach.app.data.db.PracticeAttemptEntity
import com.britishcoach.app.data.db.PracticeSessionEntity
import kotlinx.coroutines.flow.Flow

data class AppSettings(
    val preferredVoice: String? = null,
    val speechRate: Float = 0.9f,
    val dailyGoalMinutes: Int = 20,
    val feedbackDetail: FeedbackDetail = FeedbackDetail.STANDARD,
    val recordingRetentionDays: Int = 30,
    val modelId: String? = null,
    val downloadOnWifiOnly: Boolean = true,
    val useGpu: Boolean = true,
)

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun current(): AppSettings
    suspend fun update(transform: (AppSettings) -> AppSettings)
}

interface LessonRepository {
    val lessons: List<LessonContent>
    fun lesson(id: String): LessonContent?
    fun observeProgress(): Flow<Map<String, LessonProgress>>
    suspend fun progress(): Map<String, LessonProgress>
    suspend fun seedCatalogue()
    suspend fun recordAttempt(lessonId: String)
    suspend fun markCompleted(lessonId: String, now: Long)
    suspend fun setReferenceAudio(lessonId: String, path: String?)
}

/** Everything needed to save one attempt. */
data class NewAttempt(
    val lessonId: String?,
    val timestamp: Long,
    val targetText: String,
    val recognizedText: String,
    val durationMs: Long,
    val feedback: String,
    val assessmentJson: String?,
    val evidence: String,
    val targetWordCount: Int,
    val matchedWordCount: Int,
    val differingWords: List<String>,
    val recordingPath: String?,
    val usedAi: Boolean,
)

interface PracticeRepository {
    fun observeAttempts(): Flow<List<PracticeAttemptEntity>>
    fun observeSessions(): Flow<List<PracticeSessionEntity>>
    suspend fun saveAttempt(attempt: NewAttempt): Long
    suspend fun attemptCount(lessonId: String, target: String): Int
    suspend fun latestAttempt(lessonId: String, target: String): PracticeAttemptEntity?
    suspend fun rateDifficulty(attemptId: Long, rating: Int)
    suspend fun recordPracticeTime(startedAt: Long, durationMs: Long, mode: String)
}

interface MistakeRepository {
    fun observeAll(): Flow<List<MistakeEntity>>
    suspend fun byId(id: Long): MistakeEntity?
    suspend fun due(now: Long): List<DueMistake>
    /** Saves useful corrections, merging repeats of the same item instead of duplicating them. */
    suspend fun saveCorrections(
        lessonId: String?,
        example: String,
        recognized: String,
        corrections: List<Correction>,
        now: Long,
    )
    /** Records a revision attempt and schedules the next review. */
    suspend fun reviewed(id: Long, success: Boolean, now: Long)
    suspend fun markMastered(id: Long, now: Long)
    suspend fun delete(id: Long)
}

interface BackupRepository {
    suspend fun exportJson(now: Long): String
    /** Replaces progress with the backup. Throws [IllegalArgumentException] for unreadable files. */
    suspend fun restoreJson(json: String)
}

interface RecordingStore {
    fun newRecordingFile(): java.io.File
    suspend fun applyRetention(days: Int, now: Long)
    suspend fun deleteAllRecordings()
}
