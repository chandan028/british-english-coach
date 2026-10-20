package com.britishcoach.app.data.repo

import android.content.Context
import com.britishcoach.app.data.db.AppDatabase
import com.britishcoach.app.data.db.LessonEntity
import com.britishcoach.app.data.db.MistakeEntity
import com.britishcoach.app.data.db.PracticeAttemptEntity
import com.britishcoach.app.data.db.PracticeSessionEntity
import com.britishcoach.app.data.db.SettingsEntity
import com.britishcoach.app.data.db.UserProgressEntity
import com.britishcoach.core.backup.AttemptRecord
import com.britishcoach.core.backup.BackupCodec
import com.britishcoach.core.backup.BackupFile
import com.britishcoach.core.backup.MistakeRecord
import com.britishcoach.core.backup.ProgressRecord
import com.britishcoach.core.backup.SessionRecord
import com.britishcoach.core.backup.SettingsRecord
import com.britishcoach.core.coach.Correction
import com.britishcoach.core.curriculum.Curriculum
import com.britishcoach.core.learning.DueMistake
import com.britishcoach.core.learning.LessonProgress
import com.britishcoach.core.learning.RevisionScheduler
import com.britishcoach.core.model.CompletionStatus
import com.britishcoach.core.model.ErrorCategory
import com.britishcoach.core.model.FeedbackDetail
import com.britishcoach.core.model.LessonContent
import com.britishcoach.core.model.MasteryStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

private fun SettingsEntity.toDomain() = AppSettings(
    preferredVoice = preferredVoice,
    speechRate = speechRate,
    dailyGoalMinutes = dailyGoalMinutes,
    feedbackDetail = runCatching { FeedbackDetail.valueOf(feedbackDetail) }.getOrDefault(FeedbackDetail.STANDARD),
    recordingRetentionDays = recordingRetention,
    modelId = modelId,
    downloadOnWifiOnly = downloadOnWifiOnly,
    useGpu = useGpu,
)

private fun AppSettings.toEntity() = SettingsEntity(
    preferredVoice = preferredVoice,
    speechRate = speechRate,
    dailyGoalMinutes = dailyGoalMinutes,
    feedbackDetail = feedbackDetail.name,
    recordingRetention = recordingRetentionDays,
    modelId = modelId,
    downloadOnWifiOnly = downloadOnWifiOnly,
    useGpu = useGpu,
)

class RoomSettingsRepository(private val db: AppDatabase) : SettingsRepository {
    override val settings: Flow<AppSettings> = db.settings().observe().map { it?.toDomain() ?: AppSettings() }
    override suspend fun current(): AppSettings = db.settings().get()?.toDomain() ?: AppSettings()
    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        db.settings().put(transform(current()).toEntity())
    }
}

private fun UserProgressEntity.toDomain() = LessonProgress(
    lessonId = lessonId,
    status = runCatching { CompletionStatus.valueOf(completionStatus) }.getOrDefault(CompletionStatus.NOT_STARTED),
    attempts = attempts,
    lastCompletedAt = lastCompletedAt,
    revisionDueAt = revisionDueAt,
)

class RoomLessonRepository(private val db: AppDatabase) : LessonRepository {
    override val lessons: List<LessonContent> = Curriculum.lessons

    override fun lesson(id: String): LessonContent? = Curriculum.byId(id)

    override fun observeProgress(): Flow<Map<String, LessonProgress>> =
        db.progress().observeAll().map { list -> list.associate { it.lessonId to it.toDomain() } }

    override suspend fun progress(): Map<String, LessonProgress> =
        db.progress().all().associate { it.lessonId to it.toDomain() }

    override suspend fun seedCatalogue() {
        val existing = db.lessons().all().associate { it.id to it.referenceAudioPath }
        db.lessons().upsertAll(
            lessons.map { l ->
                LessonEntity(
                    id = l.id,
                    title = l.title,
                    level = l.stage.number,
                    category = l.category.name,
                    targetText = l.targetText,
                    referenceAudioPath = existing[l.id],
                    phoneticGuide = l.phoneticGuide,
                    lessonOrder = l.lessonOrder,
                )
            },
        )
        db.lessons().deleteOthers(lessons.map { it.id })
    }

    override suspend fun recordAttempt(lessonId: String) {
        val p = db.progress().byId(lessonId)
        db.progress().upsert(
            p?.copy(
                attempts = p.attempts + 1,
                completionStatus = if (p.completionStatus == CompletionStatus.COMPLETED.name) p.completionStatus else CompletionStatus.IN_PROGRESS.name,
            ) ?: UserProgressEntity(lessonId, CompletionStatus.IN_PROGRESS.name, 1, null, null),
        )
    }

    override suspend fun markCompleted(lessonId: String, now: Long) {
        val p = db.progress().byId(lessonId)
        // A completed lesson comes back for a light review a few days later.
        val due = now + RevisionScheduler.intervalsDays[2] * 24L * 60 * 60 * 1000
        db.progress().upsert(
            (p ?: UserProgressEntity(lessonId, CompletionStatus.COMPLETED.name, 0, now, due))
                .copy(completionStatus = CompletionStatus.COMPLETED.name, lastCompletedAt = now, revisionDueAt = due),
        )
    }

    override suspend fun setReferenceAudio(lessonId: String, path: String?) = db.lessons().setReferenceAudio(lessonId, path)
}

class RoomPracticeRepository(private val db: AppDatabase) : PracticeRepository {
    override fun observeAttempts() = db.attempts().observeAll()
    override fun observeSessions() = db.sessions().observeAll()

    override suspend fun saveAttempt(attempt: NewAttempt): Long = db.attempts().insert(
        PracticeAttemptEntity(
            lessonId = attempt.lessonId,
            timestamp = attempt.timestamp,
            recognizedText = attempt.recognizedText,
            targetText = attempt.targetText,
            duration = attempt.durationMs,
            feedback = attempt.feedback,
            assessmentData = attempt.assessmentJson,
            evidence = attempt.evidence,
            targetWordCount = attempt.targetWordCount,
            matchedWordCount = attempt.matchedWordCount,
            differingWords = attempt.differingWords.joinToString(","),
            recordingPath = attempt.recordingPath,
            usedAi = attempt.usedAi,
        ),
    )

    override suspend fun attemptCount(lessonId: String, target: String) = db.attempts().countFor(lessonId, target)
    override suspend fun latestAttempt(lessonId: String, target: String) = db.attempts().latestFor(lessonId, target)
    override suspend fun rateDifficulty(attemptId: Long, rating: Int) = db.attempts().rate(attemptId, rating.coerceIn(1, 3))

    override suspend fun recordPracticeTime(startedAt: Long, durationMs: Long, mode: String) {
        if (durationMs >= 5_000) db.sessions().insert(PracticeSessionEntity(startedAt = startedAt, durationMs = durationMs, mode = mode))
    }
}

class RoomMistakeRepository(private val db: AppDatabase) : MistakeRepository {
    override fun observeAll() = db.mistakes().observeAll()
    override suspend fun byId(id: Long) = db.mistakes().byId(id)

    override suspend fun due(now: Long): List<DueMistake> = db.mistakes().all()
        .filter { it.masteryStatus != MasteryStatus.MASTERED.name && it.nextReviewAt <= now }
        .map { DueMistake(it.id, it.lessonId, it.example, it.nextReviewAt) }

    override suspend fun saveCorrections(
        lessonId: String?,
        example: String,
        recognized: String,
        corrections: List<Correction>,
        now: Long,
    ) {
        for (c in corrections) {
            val existing = db.mistakes().findOpen(example, c.category.name)
            if (existing != null) {
                db.mistakes().update(
                    existing.copy(
                        recognizedText = recognized,
                        correctedText = c.corrected ?: existing.correctedText,
                        description = c.description,
                        explanation = c.explanation,
                        exercise = c.exercise,
                        attemptCount = existing.attemptCount + 1,
                        lastPractisedAt = now,
                        reviewBox = 0,
                        nextReviewAt = RevisionScheduler.firstReview(now),
                        masteryStatus = MasteryStatus.LEARNING.name,
                    ),
                )
            } else {
                db.mistakes().insert(
                    MistakeEntity(
                        lessonId = lessonId,
                        category = c.category.name,
                        example = example,
                        recognizedText = recognized,
                        correctedText = c.corrected,
                        description = c.description,
                        explanation = c.explanation,
                        exercise = c.exercise,
                        attemptCount = 1,
                        createdAt = now,
                        lastPractisedAt = now,
                        nextReviewAt = RevisionScheduler.firstReview(now),
                        reviewBox = 0,
                        masteryStatus = MasteryStatus.LEARNING.name,
                    ),
                )
            }
        }
    }

    override suspend fun reviewed(id: Long, success: Boolean, now: Long) {
        val m = db.mistakes().byId(id) ?: return
        val r = RevisionScheduler.schedule(m.reviewBox, success, now)
        db.mistakes().update(
            m.copy(
                attemptCount = m.attemptCount + 1,
                lastPractisedAt = now,
                reviewBox = r.box,
                nextReviewAt = r.nextReviewAt,
                masteryStatus = r.status.name,
            ),
        )
    }

    override suspend fun markMastered(id: Long, now: Long) {
        val m = db.mistakes().byId(id) ?: return
        db.mistakes().update(m.copy(masteryStatus = MasteryStatus.MASTERED.name, lastPractisedAt = now))
    }

    override suspend fun delete(id: Long) = db.mistakes().delete(id)
}

class RoomBackupRepository(
    private val db: AppDatabase,
    private val settings: SettingsRepository,
) : BackupRepository {

    override suspend fun exportJson(now: Long): String = withContext(Dispatchers.IO) {
        val s = settings.current()
        BackupCodec.encode(
            BackupFile(
                exportedAt = now,
                attempts = db.attempts().all().map {
                    AttemptRecord(
                        it.lessonId, it.timestamp, it.targetText, it.recognizedText, it.duration, it.feedback,
                        it.assessmentData, it.evidence, it.targetWordCount, it.matchedWordCount,
                        it.differingWords.split(',').filter(String::isNotBlank), it.selfRatedDifficulty,
                    )
                },
                mistakes = db.mistakes().all().map {
                    MistakeRecord(
                        it.lessonId, it.category, it.example, it.recognizedText, it.correctedText, it.description,
                        it.explanation, it.exercise, it.attemptCount, it.createdAt, it.lastPractisedAt,
                        it.nextReviewAt, it.reviewBox, it.masteryStatus,
                    )
                },
                progress = db.progress().all().map {
                    ProgressRecord(it.lessonId, it.completionStatus, it.attempts, it.lastCompletedAt, it.revisionDueAt)
                },
                sessions = db.sessions().all().map { SessionRecord(it.startedAt, it.durationMs, it.mode) },
                settings = SettingsRecord(s.preferredVoice, s.speechRate, s.dailyGoalMinutes, s.feedbackDetail.name, s.recordingRetentionDays),
            ),
        )
    }

    override suspend fun restoreJson(json: String): Unit = withContext(Dispatchers.IO) {
        val file = BackupCodec.decode(json)
        db.replaceHistory(
            attempts = file.attempts.map {
                PracticeAttemptEntity(
                    lessonId = it.lessonId, timestamp = it.timestamp, recognizedText = it.recognizedText,
                    targetText = it.targetText, duration = it.durationMs, feedback = it.feedback,
                    assessmentData = it.assessmentData, evidence = it.evidence, targetWordCount = it.targetWordCount,
                    matchedWordCount = it.matchedWordCount, differingWords = it.differingWords.joinToString(","),
                    recordingPath = null, selfRatedDifficulty = it.selfRatedDifficulty,
                )
            },
            mistakes = file.mistakes.map {
                MistakeEntity(
                    lessonId = it.lessonId,
                    category = runCatching { ErrorCategory.valueOf(it.category).name }.getOrDefault(ErrorCategory.TRANSCRIPT_DIFFERENCE.name),
                    example = it.targetText, recognizedText = it.recognizedText, correctedText = it.correctedText,
                    description = it.description, explanation = it.explanation, exercise = it.exercise,
                    attemptCount = it.attemptCount, createdAt = it.createdAt, lastPractisedAt = it.lastPractisedAt,
                    nextReviewAt = it.nextReviewAt, reviewBox = it.reviewBox, masteryStatus = it.masteryStatus,
                )
            },
            progress = file.progress.map {
                UserProgressEntity(it.lessonId, it.completionStatus, it.attempts, it.lastCompletedAt, it.revisionDueAt)
            },
            sessions = file.sessions.map { PracticeSessionEntity(startedAt = it.startedAt, durationMs = it.durationMs, mode = it.mode) },
        )
        file.settings?.let { r ->
            settings.update {
                it.copy(
                    preferredVoice = r.preferredVoice,
                    speechRate = r.speechRate,
                    dailyGoalMinutes = r.dailyGoalMinutes,
                    feedbackDetail = runCatching { FeedbackDetail.valueOf(r.feedbackDetail) }.getOrDefault(FeedbackDetail.STANDARD),
                    recordingRetentionDays = r.recordingRetentionDays,
                )
            }
        }
        Unit
    }
}

class FileRecordingStore(context: Context, private val db: AppDatabase) : RecordingStore {
    private val dir = File(context.filesDir, "recordings").apply { mkdirs() }

    override fun newRecordingFile(): File = File(dir, "attempt-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}.wav")

    override suspend fun applyRetention(days: Int, now: Long): Unit = withContext(Dispatchers.IO) {
        if (days <= 0) return@withContext
        val before = now - days * 24L * 60 * 60 * 1000
        db.attempts().recordingsOlderThan(before).forEach { File(it).delete() }
        db.attempts().clearRecordingsOlderThan(before)
    }

    override suspend fun deleteAllRecordings(): Unit = withContext(Dispatchers.IO) {
        dir.listFiles()?.forEach { it.delete() }
        db.attempts().clearAllRecordings()
    }
}
