package com.britishcoach.core.learning

import com.britishcoach.core.model.CompletionStatus
import com.britishcoach.core.model.LessonContent
import com.britishcoach.core.model.MasteryStatus
import com.britishcoach.core.model.PracticeMode
import com.britishcoach.core.model.Stage

private const val DAY_MS = 24L * 60 * 60 * 1000

/**
 * Spaced revision in the Leitner style. Each successful review moves an item to
 * a longer interval; a struggle sends it back to the start.
 */
object RevisionScheduler {
    /** Days until the next review for each box. Box 0 is "practise again tomorrow". */
    val intervalsDays = listOf(1, 2, 4, 7, 14, 30)

    /** Successful reviews in the last box before an item counts as mastered. */
    const val MASTERED_BOX = 5

    data class Result(val box: Int, val nextReviewAt: Long, val status: MasteryStatus)

    fun schedule(currentBox: Int, success: Boolean, now: Long): Result {
        val box = if (success) (currentBox + 1).coerceAtMost(intervalsDays.lastIndex) else 0
        val status = when {
            success && currentBox >= MASTERED_BOX -> MasteryStatus.MASTERED
            box >= 2 -> MasteryStatus.REVIEWING
            else -> MasteryStatus.LEARNING
        }
        return Result(box, now + intervalsDays[box] * DAY_MS, status)
    }

    fun firstReview(now: Long): Long = now + intervalsDays[0] * DAY_MS

    fun isDue(nextReviewAt: Long?, now: Long): Boolean = nextReviewAt != null && nextReviewAt <= now
}

/** A lesson's saved progress, as the domain sees it. */
data class LessonProgress(
    val lessonId: String,
    val status: CompletionStatus,
    val attempts: Int = 0,
    val lastCompletedAt: Long? = null,
    val revisionDueAt: Long? = null,
)

/**
 * Lessons unlock in order. The first lesson of each stage after the first
 * unlocks once most of the previous stage is done, so one hard lesson can't
 * block a whole stage. Completed lessons always stay open.
 */
object LessonUnlockPolicy {
    const val STAGE_UNLOCK_FRACTION = 0.6

    fun unlockedIds(lessons: List<LessonContent>, progress: Map<String, LessonProgress>): Set<String> {
        val sorted = lessons.sortedBy { it.lessonOrder }
        val done = { id: String -> progress[id]?.status == CompletionStatus.COMPLETED }
        val unlocked = mutableSetOf<String>()
        val byStage = sorted.groupBy { it.stage }
        for (stage in Stage.entries) {
            val inStage = byStage[stage].orEmpty()
            if (inStage.isEmpty()) continue
            val stageOpen = if (stage == Stage.FOUNDATIONS) {
                true
            } else {
                val prev = byStage[Stage.fromNumber(stage.number - 1)].orEmpty()
                prev.isEmpty() || prev.count { done(it.id) } >= Math.ceil(prev.size * STAGE_UNLOCK_FRACTION).toInt()
            }
            inStage.forEachIndexed { index, lesson ->
                val open = done(lesson.id) ||
                    (stageOpen && (index == 0 || done(inStage[index - 1].id)))
                if (open) unlocked += lesson.id
            }
        }
        return unlocked
    }

    /** The learner's level: the highest stage with an unlocked lesson. */
    fun currentStage(lessons: List<LessonContent>, progress: Map<String, LessonProgress>): Stage {
        val unlocked = unlockedIds(lessons, progress)
        return lessons.filter { it.id in unlocked }.maxOfOrNull { it.stage.number }
            ?.let { Stage.fromNumber(it) } ?: Stage.FOUNDATIONS
    }
}

/** A notebook item that is due, reduced to what planning needs. */
data class DueMistake(val mistakeId: Long, val lessonId: String?, val targetText: String, val nextReviewAt: Long)

object LessonRecommender {
    /**
     * Today's lesson: a lesson that has revision due first, then the first
     * unlocked lesson not yet completed, then the earliest completed lesson
     * that hasn't been practised for the longest time.
     */
    fun recommend(
        lessons: List<LessonContent>,
        progress: Map<String, LessonProgress>,
        now: Long,
    ): LessonContent? {
        val sorted = lessons.sortedBy { it.lessonOrder }
        val unlocked = LessonUnlockPolicy.unlockedIds(sorted, progress)
        sorted.firstOrNull { RevisionScheduler.isDue(progress[it.id]?.revisionDueAt, now) }?.let { return it }
        sorted.firstOrNull { it.id in unlocked && progress[it.id]?.status != CompletionStatus.COMPLETED }?.let { return it }
        return sorted.filter { it.id in unlocked }.minByOrNull { progress[it.id]?.lastCompletedAt ?: 0L }
    }
}

enum class SessionStepType(val title: String, val description: String) {
    LISTENING("Listening", "Listen to the reference voice"),
    PRONUNCIATION("Pronunciation", "Practise target sounds and words"),
    SPEAKING("Speaking", "A guided conversation or speaking task"),
    CORRECTION("Correction", "Review the most useful feedback"),
    REVISION("Revision", "Repeat a previous difficult word or sentence"),
}

data class SessionStep(
    val type: SessionStepType,
    val minutes: Int,
    val lessonId: String?,
    /** For revision steps: the notebook item to practise. */
    val mistakeId: Long? = null,
)

data class DailySessionPlan(val totalMinutes: Int, val steps: List<SessionStep>)

/**
 * Splits the daily session in the PRD's proportions (3/5/7/3/2 out of 20) and
 * fills each step from the learner's own lessons and due mistakes.
 */
object DailySessionPlanner {
    private val baseShares = listOf(
        SessionStepType.LISTENING to 3,
        SessionStepType.PRONUNCIATION to 5,
        SessionStepType.SPEAKING to 7,
        SessionStepType.CORRECTION to 3,
        SessionStepType.REVISION to 2,
    )

    fun plan(
        minutes: Int,
        lessons: List<LessonContent>,
        progress: Map<String, LessonProgress>,
        dueMistakes: List<DueMistake>,
        now: Long,
    ): DailySessionPlan {
        val total = minutes.coerceIn(5, 90)
        val sorted = lessons.sortedBy { it.lessonOrder }
        val unlocked = LessonUnlockPolicy.unlockedIds(sorted, progress)
        val today = LessonRecommender.recommend(sorted, progress, now)
        val speakingLesson = sorted.lastOrNull { it.id in unlocked && it.mode == PracticeMode.GUIDED_CONVERSATION }
            ?: today
        val revision = dueMistakes.minByOrNull { it.nextReviewAt }

        // Short sessions drop the correction and revision steps first when nothing is due.
        val shares = baseShares.filter { (type, _) ->
            when (type) {
                SessionStepType.REVISION -> revision != null || total >= 15
                SessionStepType.CORRECTION -> total >= 10
                else -> true
            }
        }
        val shareSum = shares.sumOf { it.second }
        val raw = shares.map { (type, share) -> type to total * share.toDouble() / shareSum }
        val rounded = raw.map { (type, m) -> type to maxOf(1, Math.floor(m).toInt()) }.toMutableList()
        // Give leftover minutes to the steps with the largest remainder, keeping the total exact.
        var leftover = total - rounded.sumOf { it.second }
        val order = raw.indices.sortedByDescending { raw[it].second - Math.floor(raw[it].second) }
        var k = 0
        while (leftover > 0) {
            val idx = order[k % order.size]
            rounded[idx] = rounded[idx].first to rounded[idx].second + 1
            leftover--; k++
        }
        while (leftover < 0) {
            val idx = rounded.indices.maxBy { rounded[it].second }
            rounded[idx] = rounded[idx].first to rounded[idx].second - 1
            leftover++
        }

        val steps = rounded.map { (type, m) ->
            when (type) {
                SessionStepType.LISTENING, SessionStepType.PRONUNCIATION, SessionStepType.CORRECTION ->
                    SessionStep(type, m, today?.id)
                SessionStepType.SPEAKING -> SessionStep(type, m, speakingLesson?.id)
                SessionStepType.REVISION -> SessionStep(type, m, revision?.lessonId ?: today?.id, revision?.mistakeId)
            }
        }
        return DailySessionPlan(total, steps)
    }
}
