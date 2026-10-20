package com.britishcoach.app.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.Upsert
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

@Dao
interface LessonDao {
    @Upsert
    suspend fun upsertAll(lessons: List<LessonEntity>)

    @Query("SELECT * FROM lessons ORDER BY lessonOrder")
    fun observeAll(): Flow<List<LessonEntity>>

    @Query("SELECT * FROM lessons")
    suspend fun all(): List<LessonEntity>

    @Query("UPDATE lessons SET referenceAudioPath = :path WHERE id = :id")
    suspend fun setReferenceAudio(id: String, path: String?)

    @Query("DELETE FROM lessons WHERE id NOT IN (:keep)")
    suspend fun deleteOthers(keep: List<String>)
}

@Dao
interface AttemptDao {
    @Insert
    suspend fun insert(attempt: PracticeAttemptEntity): Long

    @Insert
    suspend fun insertAll(attempts: List<PracticeAttemptEntity>)

    @Query("SELECT * FROM practice_attempts ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<PracticeAttemptEntity>>

    @Query("SELECT * FROM practice_attempts ORDER BY timestamp DESC")
    suspend fun all(): List<PracticeAttemptEntity>

    @Query("SELECT * FROM practice_attempts WHERE lessonId = :lessonId AND targetText = :target ORDER BY timestamp DESC LIMIT 1")
    suspend fun latestFor(lessonId: String, target: String): PracticeAttemptEntity?

    @Query("SELECT COUNT(*) FROM practice_attempts WHERE lessonId = :lessonId AND targetText = :target")
    suspend fun countFor(lessonId: String, target: String): Int

    @Query("UPDATE practice_attempts SET selfRatedDifficulty = :rating WHERE id = :id")
    suspend fun rate(id: Long, rating: Int)

    @Query("SELECT recordingPath FROM practice_attempts WHERE recordingPath IS NOT NULL AND timestamp < :before")
    suspend fun recordingsOlderThan(before: Long): List<String>

    @Query("UPDATE practice_attempts SET recordingPath = NULL WHERE timestamp < :before")
    suspend fun clearRecordingsOlderThan(before: Long)

    @Query("SELECT recordingPath FROM practice_attempts WHERE recordingPath IS NOT NULL")
    suspend fun allRecordings(): List<String>

    @Query("UPDATE practice_attempts SET recordingPath = NULL")
    suspend fun clearAllRecordings()

    @Query("DELETE FROM practice_attempts")
    suspend fun deleteAll()
}

@Dao
interface MistakeDao {
    @Insert
    suspend fun insert(m: MistakeEntity): Long

    @Insert
    suspend fun insertAll(m: List<MistakeEntity>)

    @Update
    suspend fun update(m: MistakeEntity)

    @Query("SELECT * FROM mistakes ORDER BY CASE masteryStatus WHEN 'MASTERED' THEN 1 ELSE 0 END, nextReviewAt")
    fun observeAll(): Flow<List<MistakeEntity>>

    @Query("SELECT * FROM mistakes")
    suspend fun all(): List<MistakeEntity>

    @Query("SELECT * FROM mistakes WHERE id = :id")
    suspend fun byId(id: Long): MistakeEntity?

    @Query("SELECT * FROM mistakes WHERE example = :example AND category = :category AND masteryStatus != 'MASTERED' LIMIT 1")
    suspend fun findOpen(example: String, category: String): MistakeEntity?

    @Query("DELETE FROM mistakes WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM mistakes")
    suspend fun deleteAll()
}

@Dao
interface ProgressDao {
    @Upsert
    suspend fun upsert(p: UserProgressEntity)

    @Upsert
    suspend fun upsertAll(p: List<UserProgressEntity>)

    @Query("SELECT * FROM user_progress")
    fun observeAll(): Flow<List<UserProgressEntity>>

    @Query("SELECT * FROM user_progress")
    suspend fun all(): List<UserProgressEntity>

    @Query("SELECT * FROM user_progress WHERE lessonId = :id")
    suspend fun byId(id: String): UserProgressEntity?

    @Query("DELETE FROM user_progress")
    suspend fun deleteAll()
}

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(s: PracticeSessionEntity): Long

    @Insert
    suspend fun insertAll(s: List<PracticeSessionEntity>)

    @Query("SELECT * FROM practice_sessions ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<PracticeSessionEntity>>

    @Query("SELECT * FROM practice_sessions")
    suspend fun all(): List<PracticeSessionEntity>

    @Query("DELETE FROM practice_sessions")
    suspend fun deleteAll()
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = 0")
    fun observe(): Flow<SettingsEntity?>

    @Query("SELECT * FROM settings WHERE id = 0")
    suspend fun get(): SettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(s: SettingsEntity)
}

@Database(
    entities = [
        LessonEntity::class,
        PracticeAttemptEntity::class,
        MistakeEntity::class,
        UserProgressEntity::class,
        PracticeSessionEntity::class,
        SettingsEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun lessons(): LessonDao
    abstract fun attempts(): AttemptDao
    abstract fun mistakes(): MistakeDao
    abstract fun progress(): ProgressDao
    abstract fun sessions(): SessionDao
    abstract fun settings(): SettingsDao

    /** Used by restore: replaces learning history in one transaction. */
    suspend fun replaceHistory(
        attempts: List<PracticeAttemptEntity>,
        mistakes: List<MistakeEntity>,
        progress: List<UserProgressEntity>,
        sessions: List<PracticeSessionEntity>,
    ) = withTransaction {
        attempts().deleteAll(); attempts().insertAll(attempts)
        mistakes().deleteAll(); mistakes().insertAll(mistakes)
        progress().deleteAll(); progress().upsertAll(progress)
        sessions().deleteAll(); sessions().insertAll(sessions)
    }

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "coach.db").build()
    }
}
