package com.britishcoach.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.britishcoach.app.data.db.AppDatabase
import com.britishcoach.app.data.repo.RoomBackupRepository
import com.britishcoach.app.data.repo.RoomLessonRepository
import com.britishcoach.app.data.repo.RoomMistakeRepository
import com.britishcoach.app.data.repo.RoomSettingsRepository
import com.britishcoach.core.coach.Correction
import com.britishcoach.core.model.CompletionStatus
import com.britishcoach.core.model.ErrorCategory
import com.britishcoach.core.model.MasteryStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private lateinit var db: AppDatabase
    private val now = 1_800_000_000_000L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
    }

    @After
    fun tearDown() = db.close()

    private val correction = Correction(ErrorCategory.TRANSCRIPT_DIFFERENCE, "'think' written as 'sink'", "think", "Could be noise.", "Say it slowly.")

    @Test
    fun repeatedCorrectionsMergeIntoOneNotebookItem() = runBlocking {
        val repo = RoomMistakeRepository(db)
        repo.saveCorrections("s1-th", "think", "sink", listOf(correction), now)
        repo.saveCorrections("s1-th", "think", "fink", listOf(correction), now + 1000)
        val all = db.mistakes().all()
        assertEquals(1, all.size)
        assertEquals(2, all.single().attemptCount)
        assertEquals("fink", all.single().recognizedText)
    }

    @Test
    fun successfulReviewsEventuallyMaster() = runBlocking {
        val repo = RoomMistakeRepository(db)
        repo.saveCorrections("s1-th", "think", "sink", listOf(correction), now)
        val id = db.mistakes().all().single().id
        repeat(6) { repo.reviewed(id, success = true, now = now) }
        assertEquals(MasteryStatus.MASTERED.name, db.mistakes().byId(id)!!.masteryStatus)
    }

    @Test
    fun backupRoundTrip() = runBlocking {
        val lessons = RoomLessonRepository(db)
        val settings = RoomSettingsRepository(db)
        lessons.seedCatalogue()
        lessons.markCompleted("s1-greetings", now)
        val backup = RoomBackupRepository(db, settings)
        val json = backup.exportJson(now)
        db.progress().deleteAll()
        backup.restoreJson(json)
        assertEquals(CompletionStatus.COMPLETED, lessons.progress()["s1-greetings"]?.status)
    }
}
