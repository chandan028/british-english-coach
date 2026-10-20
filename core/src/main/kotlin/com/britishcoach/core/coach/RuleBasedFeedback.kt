package com.britishcoach.core.coach

import com.britishcoach.core.analysis.TranscriptComparison
import com.britishcoach.core.analysis.WordDiff
import com.britishcoach.core.model.ErrorCategory
import com.britishcoach.core.model.EvidenceLevel
import com.britishcoach.core.model.LessonContent
import com.britishcoach.core.model.PracticeItem

/**
 * Feedback written from the transcript comparison alone, in the coach's voice.
 * Used whenever the AI model is not installed, is still loading, or fails, so
 * lessons always work.
 */
object RuleBasedFeedback {

    fun forAttempt(
        lesson: LessonContent,
        item: PracticeItem,
        comparison: TranscriptComparison,
        attemptNumber: Int,
        previousMatched: Int? = null,
    ): StructuredFeedback {
        if (comparison.isEmptyRecognition) {
            return StructuredFeedback(
                spoken = "I didn't get any words from that recording. Try holding the phone a little closer and speaking at a steady pace, then have another go.",
                suggestRetry = true,
                evidence = EvidenceLevel.TRANSCRIPTION,
                fromFallback = true,
            )
        }

        val tip = lesson.tips.getOrNull((attemptNumber - 1).coerceAtLeast(0) % lesson.tips.size.coerceAtLeast(1))
        if (comparison.isExactMatch) {
            val opening = when {
                previousMatched != null && previousMatched < comparison.matchedCount -> "That's better than last time: every word came through as expected."
                attemptNumber == 1 -> "Every word came through as expected."
                else -> "Every word came through clearly this time."
            }
            val stress = item.stressGuide?.let { " For rhythm, compare yourself with the stress pattern: $it" }.orEmpty()
            return StructuredFeedback(
                spoken = "$opening$stress",
                suggestRetry = false,
                evidence = EvidenceLevel.TRANSCRIPTION,
                fromFallback = true,
            )
        }

        val focus = comparison.diffs.firstOrNull { it !is WordDiff.Match && it !is WordDiff.Insertion }
            ?: comparison.diffs.first { it is WordDiff.Insertion }
        val corrections = mutableListOf<Correction>()
        val spoken = StringBuilder()

        spoken.append(
            when {
                attemptNumber >= 3 -> "That's absolutely fine, this one takes practice. "
                comparison.matchedCount * 2 >= comparison.targetWordCount -> "Almost there. "
                else -> "Let's take this one a bit more slowly. "
            },
        )

        when (focus) {
            is WordDiff.Substitution -> {
                spoken.append("The recogniser wrote \"${focus.heard}\" where the sentence has \"${focus.expected}\". ")
                spoken.append("That might be the sound, or just the recogniser, so let's check by listening again. ")
                corrections += Correction(
                    category = ErrorCategory.TRANSCRIPT_DIFFERENCE,
                    description = "\"${focus.expected}\" was transcribed as \"${focus.heard}\"",
                    corrected = item.text,
                    explanation = "A different word in the transcript can come from pronunciation, background noise or the recogniser. It isn't proof of a mistake.",
                    exercise = "Listen to \"${focus.expected}\" on its own, say it three times slowly, then say the whole sentence.",
                )
            }
            is WordDiff.Omission -> {
                spoken.append("\"${focus.expected}\" didn't appear in the transcript. ")
                spoken.append("Make sure every word is there, even the small ones, then try again. ")
                corrections += Correction(
                    category = ErrorCategory.TRANSCRIPT_DIFFERENCE,
                    description = "\"${focus.expected}\" was missing from the transcript",
                    corrected = item.text,
                    explanation = "The word may have been skipped, said very quietly, or not picked up by the recogniser.",
                    exercise = "Say the sentence slowly, tapping once for each word, then speed up.",
                )
            }
            is WordDiff.Insertion -> {
                spoken.append("The transcript has an extra word, \"${focus.heard}\". ")
                spoken.append("Try saying just the target sentence, without adding anything. ")
            }
            is WordDiff.Match -> Unit
        }

        if (attemptNumber >= 3 && focus is WordDiff.Substitution) {
            spoken.append("Let's practise just \"${focus.expected}\" first, then put the whole sentence together.")
        } else if (tip != null) {
            spoken.append("Tip: $tip")
        }

        return StructuredFeedback(
            spoken = spoken.toString().trim(),
            corrections = corrections,
            suggestRetry = true,
            evidence = EvidenceLevel.TRANSCRIPTION,
            fromFallback = true,
        )
    }

    /** Coach line used in conversation mode when the model is unavailable. */
    fun conversationUnavailable(): String =
        "I'm sorry, the AI coach isn't available right now, so I can't hold a conversation. " +
            "You can still practise every lesson with the reference voice and transcript comparison."
}
