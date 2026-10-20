package com.britishcoach.core.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Export format for progress backups. Plain JSON so it stays readable and can be
 * restored on a new phone. Recordings are not included; they stay on the device.
 */
@Serializable
data class BackupFile(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val exportedAt: Long,
    val attempts: List<AttemptRecord> = emptyList(),
    val mistakes: List<MistakeRecord> = emptyList(),
    val progress: List<ProgressRecord> = emptyList(),
    val sessions: List<SessionRecord> = emptyList(),
    val settings: SettingsRecord? = null,
) {
    companion object {
        const val FORMAT = "british-english-coach-backup"
        const val VERSION = 1
    }
}

@Serializable
data class AttemptRecord(
    val lessonId: String?,
    val timestamp: Long,
    val targetText: String,
    val recognizedText: String,
    val durationMs: Long,
    val feedback: String,
    val assessmentData: String?,
    val evidence: String,
    val targetWordCount: Int,
    val matchedWordCount: Int,
    val differingWords: List<String> = emptyList(),
    val selfRatedDifficulty: Int? = null,
)

@Serializable
data class MistakeRecord(
    val lessonId: String?,
    val category: String,
    val targetText: String,
    val recognizedText: String,
    val correctedText: String?,
    val description: String,
    val explanation: String,
    val exercise: String,
    val attemptCount: Int,
    val createdAt: Long,
    val lastPractisedAt: Long,
    val nextReviewAt: Long,
    val reviewBox: Int,
    val masteryStatus: String,
)

@Serializable
data class ProgressRecord(
    val lessonId: String,
    val completionStatus: String,
    val attempts: Int,
    val lastCompletedAt: Long?,
    val revisionDueAt: Long?,
)

@Serializable
data class SessionRecord(val startedAt: Long, val durationMs: Long, val mode: String)

@Serializable
data class SettingsRecord(
    val preferredVoice: String?,
    val speechRate: Float,
    val dailyGoalMinutes: Int,
    val feedbackDetail: String,
    val recordingRetentionDays: Int,
)

object BackupCodec {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    /** Throws [IllegalArgumentException] with a readable message if the file isn't a backup we can read. */
    fun decode(text: String): BackupFile {
        val file = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (e: Exception) {
            throw IllegalArgumentException("This file isn't a British English Coach backup.", e)
        }
        require(file.format == BackupFile.FORMAT) { "This file isn't a British English Coach backup." }
        require(file.version <= BackupFile.VERSION) { "This backup was made by a newer version of the app." }
        return file
    }
}
