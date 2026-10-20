package com.britishcoach.core.progress

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** The facts about one saved attempt that progress maths needs. */
data class AttemptFact(
    val timestamp: Long,
    val durationMs: Long,
    val lessonId: String?,
    val targetWordCount: Int,
    val matchedWordCount: Int,
    val differingWords: List<String>,
)

/** Minutes practised, from timed sessions (lessons and conversations). */
data class PracticeTimeFact(val startedAt: Long, val durationMs: Long)

data class DayMinutes(val date: LocalDate, val minutes: Int)

data class WeekTrend(
    val weekStart: LocalDate,
    val attempts: Int,
    /** Share of target words the recogniser matched that week, 0..1. A transcript measure only. */
    val transcriptMatchRate: Double?,
)

data class RecurringWord(val word: String, val times: Int)

data class ProgressSummary(
    val last7Days: List<DayMinutes>,
    val minutesThisWeek: Int,
    val minutesToday: Int,
    val attemptsThisWeek: Int,
    val totalAttempts: Int,
    val practiceDaysThisWeek: Int,
    val currentStreakDays: Int,
    val recurringWords: List<RecurringWord>,
    val weeklyTrend: List<WeekTrend>,
)

/** Every number here is computed from saved records. Nothing is estimated or invented. */
object ProgressCalculator {

    fun summarize(
        attempts: List<AttemptFact>,
        practiceTime: List<PracticeTimeFact>,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        trendWeeks: Int = 6,
    ): ProgressSummary {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        fun dateOf(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

        val msByDay = practiceTime.groupBy { dateOf(it.startedAt) }.mapValues { (_, v) -> v.sumOf { it.durationMs } }
        val last7 = (6 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            DayMinutes(d, msToMinutes(msByDay[d] ?: 0L))
        }
        val weekStart = today.minusDays(6)
        val attemptsThisWeek = attempts.count { !dateOf(it.timestamp).isBefore(weekStart) }

        val activeDays = (msByDay.filterValues { it > 0 }.keys + attempts.map { dateOf(it.timestamp) }).toSet()
        var streak = 0
        var cursor = if (today in activeDays) today else today.minusDays(1)
        while (cursor in activeDays) { streak++; cursor = cursor.minusDays(1) }

        val recurring = attempts
            .filter { !dateOf(it.timestamp).isBefore(today.minusDays(30)) }
            .flatMap { it.differingWords.distinct() }
            .groupingBy { it }.eachCount()
            .filter { it.value >= 2 }
            .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(8)
            .map { RecurringWord(it.key, it.value) }

        val mondayThisWeek = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val trend = (trendWeeks - 1 downTo 0).map { back ->
            val start = mondayThisWeek.minusWeeks(back.toLong())
            val inWeek = attempts.filter {
                val d = dateOf(it.timestamp)
                !d.isBefore(start) && ChronoUnit.DAYS.between(start, d) < 7
            }
            val target = inWeek.sumOf { it.targetWordCount }
            WeekTrend(start, inWeek.size, if (target > 0) inWeek.sumOf { it.matchedWordCount }.toDouble() / target else null)
        }

        return ProgressSummary(
            last7Days = last7,
            minutesThisWeek = last7.sumOf { it.minutes },
            minutesToday = last7.last().minutes,
            attemptsThisWeek = attemptsThisWeek,
            totalAttempts = attempts.size,
            practiceDaysThisWeek = activeDays.count { !it.isBefore(weekStart) && !it.isAfter(today) },
            currentStreakDays = streak,
            recurringWords = recurring,
            weeklyTrend = trend,
        )
    }

    /** Rounds to the nearest minute, but any practice at all counts as at least one minute. */
    fun msToMinutes(ms: Long): Int = when {
        ms <= 0 -> 0
        ms < 60_000 -> 1
        else -> ((ms + 30_000) / 60_000).toInt()
    }
}
