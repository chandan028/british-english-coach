package com.britishcoach.core

import com.britishcoach.core.backup.BackupCodec
import com.britishcoach.core.backup.BackupFile
import com.britishcoach.core.backup.ProgressRecord
import com.britishcoach.core.curriculum.Curriculum
import com.britishcoach.core.learning.DailySessionPlanner
import com.britishcoach.core.learning.DueMistake
import com.britishcoach.core.learning.LessonProgress
import com.britishcoach.core.learning.LessonRecommender
import com.britishcoach.core.learning.LessonUnlockPolicy
import com.britishcoach.core.learning.RevisionScheduler
import com.britishcoach.core.learning.SessionStepType
import com.britishcoach.core.model.CompletionStatus
import com.britishcoach.core.model.MasteryStatus
import com.britishcoach.core.model.Stage
import com.britishcoach.core.progress.AttemptFact
import com.britishcoach.core.progress.PracticeTimeFact
import com.britishcoach.core.progress.ProgressCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class LearningTest {
    private val lessons = Curriculum.lessons
    private val day = 24L * 60 * 60 * 1000
    private val now = LocalDate.of(2026, 10, 20).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun done(vararg ids: String) =
        ids.associateWith { LessonProgress(it, CompletionStatus.COMPLETED, 1, now - day) }

    @Test
    fun `curriculum has five stages with unique ids and sequential order`() {
        assertEquals(Stage.entries.toSet(), lessons.map { it.stage }.toSet())
        assertEquals(lessons.size, lessons.map { it.id }.toSet().size)
        assertEquals((1..lessons.size).toList(), lessons.map { it.lessonOrder })
        assertTrue(lessons.all { it.items.isNotEmpty() })
        assertTrue(lessons.filter { it.conversationTopic != null }.all { it.openingQuestion != null })
    }

    @Test
    fun `only the first lesson is open at the start`() {
        assertEquals(setOf("s1-greetings"), LessonUnlockPolicy.unlockedIds(lessons, emptyMap()))
        assertEquals(Stage.FOUNDATIONS, LessonUnlockPolicy.currentStage(lessons, emptyMap()))
    }

    @Test
    fun `completing a lesson unlocks the next and stage two opens at sixty percent`() {
        val stage1 = Curriculum.forStage(Stage.FOUNDATIONS).map { it.id }
        val one = LessonUnlockPolicy.unlockedIds(lessons, done(stage1[0]))
        assertTrue(stage1[1] in one)
        val most = LessonUnlockPolicy.unlockedIds(lessons, done(*stage1.take(4).toTypedArray()))
        assertTrue(Curriculum.forStage(Stage.PRONUNCIATION).first().id in most)
        assertEquals(Stage.PRONUNCIATION, LessonUnlockPolicy.currentStage(lessons, done(*stage1.take(4).toTypedArray())))
    }

    @Test
    fun `completed lessons stay open`() {
        val later = Curriculum.forStage(Stage.ADVANCED).last().id
        assertTrue(later in LessonUnlockPolicy.unlockedIds(lessons, done(later)))
    }

    @Test
    fun `recommender prefers due revision then next new lesson`() {
        val p = done("s1-greetings").toMutableMap()
        assertEquals("s1-th", LessonRecommender.recommend(lessons, p, now)?.id)
        p["s1-greetings"] = p["s1-greetings"]!!.copy(revisionDueAt = now - 1)
        assertEquals("s1-greetings", LessonRecommender.recommend(lessons, p, now)?.id)
    }

    @Test
    fun `revision scheduler moves up on success and resets on failure`() {
        val a = RevisionScheduler.schedule(0, success = true, now = now)
        assertEquals(1, a.box)
        assertEquals(now + 2 * day, a.nextReviewAt)
        val b = RevisionScheduler.schedule(3, success = false, now = now)
        assertEquals(0, b.box)
        assertEquals(MasteryStatus.LEARNING, b.status)
        assertEquals(MasteryStatus.MASTERED, RevisionScheduler.schedule(5, true, now).status)
    }

    @Test
    fun `daily plan uses the PRD split for twenty minutes`() {
        val plan = DailySessionPlanner.plan(20, lessons, emptyMap(), listOf(DueMistake(7, "s1-th", "think", now - 1)), now)
        assertEquals(listOf(3, 5, 7, 3, 2), plan.steps.map { it.minutes })
        assertEquals(7L, plan.steps.last().mistakeId)
        assertEquals(20, plan.steps.sumOf { it.minutes })
    }

    @Test
    fun `short plan keeps total and drops optional steps`() {
        val plan = DailySessionPlanner.plan(8, lessons, emptyMap(), emptyList(), now)
        assertEquals(8, plan.steps.sumOf { it.minutes })
        assertFalse(plan.steps.any { it.type == SessionStepType.REVISION })
        assertFalse(plan.steps.any { it.type == SessionStepType.CORRECTION })
        val odd = DailySessionPlanner.plan(33, lessons, emptyMap(), emptyList(), now)
        assertEquals(33, odd.steps.sumOf { it.minutes })
    }

    @Test
    fun `progress comes only from saved records`() {
        val empty = ProgressCalculator.summarize(emptyList(), emptyList(), now, ZoneOffset.UTC)
        assertEquals(0, empty.minutesThisWeek)
        assertEquals(0, empty.currentStreakDays)
        assertNull(empty.weeklyTrend.last().transcriptMatchRate)

        val attempts = listOf(
            AttemptFact(now - 1000, 5000, "s1-th", 4, 3, listOf("think")),
            AttemptFact(now - day, 5000, "s1-th", 4, 4, emptyList()),
            AttemptFact(now - 2 * day, 5000, "s1-th", 4, 2, listOf("think", "this")),
        )
        val time = listOf(PracticeTimeFact(now - 1000, 10 * 60_000), PracticeTimeFact(now - day, 5 * 60_000))
        val s = ProgressCalculator.summarize(attempts, time, now, ZoneOffset.UTC)
        assertEquals(15, s.minutesThisWeek)
        assertEquals(10, s.minutesToday)
        assertEquals(3, s.currentStreakDays)
        assertEquals("think", s.recurringWords.single().word)
        assertEquals(2, s.recurringWords.single().times)
    }

    @Test
    fun `backup round trips and rejects foreign files`() {
        val file = BackupFile(exportedAt = now, progress = listOf(ProgressRecord("s1-th", "COMPLETED", 2, now, null)))
        assertEquals(file, BackupCodec.decode(BackupCodec.encode(file)))
        val err = runCatching { BackupCodec.decode("{\"hello\":1}") }.exceptionOrNull()
        assertTrue(err is IllegalArgumentException)
    }
}
