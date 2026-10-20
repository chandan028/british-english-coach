package com.britishcoach.core.coach

import com.britishcoach.core.analysis.TranscriptComparison
import com.britishcoach.core.analysis.WordDiff
import com.britishcoach.core.model.ErrorCategory
import com.britishcoach.core.model.EvidenceLevel
import com.britishcoach.core.model.FeedbackDetail
import com.britishcoach.core.model.LessonContent
import com.britishcoach.core.model.PracticeItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One correction worth saving in the mistake notebook. */
@Serializable
data class Correction(
    val category: ErrorCategory,
    /** Short description, e.g. "'thirty' was written as 'thirteen'". */
    val description: String,
    /** The corrected sentence, when a correction makes sense. */
    val corrected: String? = null,
    val explanation: String,
    val exercise: String,
)

/** Feedback for one attempt, whether written by the model or by the built-in rules. */
@Serializable
data class StructuredFeedback(
    /** What the coach says, ready for text-to-speech. */
    val spoken: String,
    val corrections: List<Correction> = emptyList(),
    val suggestRetry: Boolean = false,
    val evidence: EvidenceLevel = EvidenceLevel.TRANSCRIPTION,
    /** True when the built-in rules produced this, because the model was unavailable or failed. */
    val fromFallback: Boolean = false,
)

/** The JSON shape we ask Gemma to produce. Kept small: small models follow small schemas better. */
@Serializable
internal data class ModelFeedbackJson(
    val say: String = "",
    val retry: Boolean = false,
    val corrections: List<ModelCorrectionJson> = emptyList(),
)

@Serializable
internal data class ModelCorrectionJson(
    val type: String = "",
    val issue: String = "",
    val corrected: String? = null,
    val why: String = "",
    val practice: String = "",
)

/** Builds prompts for the model. All acoustic facts come from the app, never from the model. */
object FeedbackPrompts {

    fun repeatAttempt(
        lesson: LessonContent,
        item: PracticeItem,
        comparison: TranscriptComparison,
        recognizerConfidence: Float?,
        attemptNumber: Int,
        detail: FeedbackDetail,
    ): String = buildString {
        appendLine("TASK: Give feedback on a repeat-after-me attempt.")
        appendLine("Lesson: ${lesson.title} (${lesson.category.label}).")
        appendLine("Target: \"${item.text}\"")
        item.phonetic?.let { appendLine("Reference IPA (SSBE): $it") }
        item.stressGuide?.let { appendLine("Stress guide: $it") }
        if (lesson.tips.isNotEmpty()) appendLine("Lesson tips: ${lesson.tips.joinToString(" ")}")
        appendLine("Speech recogniser output: \"${comparison.recognized}\"")
        appendLine("Word comparison: ${describe(comparison)}")
        if (recognizerConfidence != null) {
            appendLine("Recogniser confidence reported by the device: ${String.format(java.util.Locale.ROOT, "%.2f", recognizerConfidence)} (0 to 1). This is about the transcript, not pronunciation.")
        } else {
            appendLine("The recogniser did not report a confidence value.")
        }
        appendLine("This is attempt number $attemptNumber for this item.")
        appendLine("Evidence available: transcript only. No sound-level pronunciation assessment was run.")
        appendLine()
        appendLine("RULES:")
        appendLine("- A different or missing word may come from pronunciation, noise or the recogniser. Say it is uncertain; never state that a sound was mispronounced.")
        appendLine("- You may give pronunciation guidance for the target (sounds, stress, rhythm) as a tip to practise, not as a diagnosis.")
        appendLine("- If the transcript matches, acknowledge it briefly and honestly; don't over-praise.")
        appendLine("- At most two corrections.")
        appendLine("- ${lengthRule(detail)}")
        appendLine()
        append(JSON_INSTRUCTIONS)
    }

    fun conversationTurnReview(learnerText: String, topic: String?): String = buildString {
        appendLine("TASK: The learner said the following during conversation practice (from the speech recogniser):")
        appendLine("\"$learnerText\"")
        topic?.let { appendLine("Topic: $it") }
        appendLine("Find at most one important grammar or vocabulary issue, only if there clearly is one. Ignore missing punctuation and capitalisation; they come from the recogniser.")
        appendLine("Use type GRAMMAR, VOCABULARY or BRITISH_USAGE. If nothing important, return an empty corrections list.")
        appendLine("Put an empty string in \"say\".")
        appendLine()
        append(JSON_INSTRUCTIONS)
    }

    fun conversationSummary(transcript: List<Pair<String, String>>): String = buildString {
        appendLine("TASK: The conversation practice has ended. Here is the transcript (learner turns come from a speech recogniser):")
        transcript.forEach { (who, text) -> appendLine("$who: $text") }
        appendLine()
        appendLine("Write a short, warm spoken review in \"say\": one thing that went well (only if true) and the one or two most useful corrections.")
        appendLine("List those corrections in \"corrections\" with type GRAMMAR, VOCABULARY, BRITISH_USAGE or FLUENCY. Don't comment on pronunciation: you only have text.")
        appendLine()
        append(JSON_INSTRUCTIONS)
    }

    private fun lengthRule(detail: FeedbackDetail) = when (detail) {
        FeedbackDetail.BRIEF -> "\"say\" is one or two short sentences."
        FeedbackDetail.STANDARD -> "\"say\" is two to four short sentences."
        FeedbackDetail.DETAILED -> "\"say\" is up to six short sentences."
    }

    fun describe(c: TranscriptComparison): String {
        if (c.isEmptyRecognition) return "nothing was recognised."
        if (c.isExactMatch) return "all ${c.targetWordCount} words matched."
        val parts = c.diffs.mapNotNull {
            when (it) {
                is WordDiff.Match -> null
                is WordDiff.Substitution -> "expected '${it.expected}' but recogniser wrote '${it.heard}'"
                is WordDiff.Omission -> "'${it.expected}' was not in the transcript"
                is WordDiff.Insertion -> "extra word '${it.heard}'"
            }
        }
        return "${c.matchedCount} of ${c.targetWordCount} words matched; " + parts.joinToString("; ") + "."
    }

    private const val JSON_INSTRUCTIONS =
        "Reply with only one JSON object, no other text, in this form:\n" +
            "{\"say\": \"what you say to the learner\", \"retry\": true or false, " +
            "\"corrections\": [{\"type\": \"TRANSCRIPT_DIFFERENCE|GRAMMAR|VOCABULARY|BRITISH_USAGE|PRONUNCIATION_PRACTICE|FLUENCY\", " +
            "\"issue\": \"short description\", \"corrected\": \"corrected sentence or null\", " +
            "\"why\": \"one-sentence explanation\", \"practice\": \"one short exercise\"}]}"
}

/** Parses model output leniently and applies the grounding rules before anything is shown. */
object FeedbackParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    /** Returns null when no usable JSON was found, so the caller can fall back to the rules. */
    fun parse(raw: String, evidence: EvidenceLevel): StructuredFeedback? {
        val obj = extractJsonObject(raw) ?: return null
        val parsed = runCatching { json.decodeFromString(ModelFeedbackJson.serializer(), obj) }.getOrNull() ?: return null
        val corrections = parsed.corrections.take(3).mapNotNull { c ->
            val category = parseCategory(c.type, evidence) ?: return@mapNotNull null
            if (c.issue.isBlank() && c.why.isBlank()) return@mapNotNull null
            Correction(
                category = category,
                description = Grounding.sanitize(c.issue, evidence),
                corrected = c.corrected?.takeIf { it.isNotBlank() && it != "null" },
                explanation = Grounding.sanitize(c.why, evidence),
                exercise = Grounding.sanitize(c.practice, evidence),
            )
        }
        return StructuredFeedback(
            spoken = Grounding.sanitize(parsed.say.trim(), evidence),
            corrections = corrections,
            suggestRetry = parsed.retry,
            evidence = evidence,
        )
    }

    private fun parseCategory(type: String, evidence: EvidenceLevel): ErrorCategory? {
        val t = type.trim().uppercase().replace(' ', '_')
        val cat = ErrorCategory.entries.firstOrNull { it.name == t } ?: when {
            t.contains("PRONUN") -> ErrorCategory.PRONUNCIATION_PRACTICE
            t.contains("GRAMMAR") -> ErrorCategory.GRAMMAR
            t.contains("VOCAB") -> ErrorCategory.VOCABULARY
            t.contains("BRIT") -> ErrorCategory.BRITISH_USAGE
            t.contains("TRANSCRIPT") -> ErrorCategory.TRANSCRIPT_DIFFERENCE
            t.contains("FLUEN") -> ErrorCategory.FLUENCY
            else -> null
        } ?: return null
        // The model may not upgrade a result to "measured" on its own.
        if (cat == ErrorCategory.MEASURED_PRONUNCIATION && evidence != EvidenceLevel.VALIDATED_ASSESSMENT) {
            return ErrorCategory.PRONUNCIATION_PRACTICE
        }
        return cat
    }

    /** Finds the first balanced {...} block, ignoring code fences or chatter around it. */
    fun extractJsonObject(raw: String): String? {
        val start = raw.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until raw.length) {
            val ch = raw[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    ch == '\\' -> escaped = true
                    ch == '"' -> inString = false
                }
                continue
            }
            when (ch) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return raw.substring(start, i + 1)
                }
            }
        }
        return null
    }
}

/**
 * Enforces the PRD's honesty rules on model text: no invented scores or
 * percentages, and no claims of having heard audio when only a transcript exists.
 */
object Grounding {
    private val percent = Regex("\\b\\d{1,3}(\\.\\d+)?\\s?(%|percent|per cent)", RegexOption.IGNORE_CASE)
    private val scoreOutOf = Regex("\\b\\d{1,3}(\\.\\d+)?\\s?(/|out of)\\s?\\d{1,3}\\b", RegexOption.IGNORE_CASE)
    private val heardClaims = listOf(
        Regex("\\bI (could )?(hear|heard)\\b", RegexOption.IGNORE_CASE) to "the transcript suggests",
        Regex("\\bI (have )?(listened|analysed|analyzed)( to)?\\b", RegexOption.IGNORE_CASE) to "looking at the transcript,",
        Regex("\\byou (mis)?pronounced\\b", RegexOption.IGNORE_CASE) to "the recogniser wrote",
        Regex("\\byour accent (is|was|sounds)\\b", RegexOption.IGNORE_CASE) to "your speech was",
    )
    private val markdown = Regex("[*_#`]+")

    fun sanitize(text: String, evidence: EvidenceLevel): String {
        var out = text.replace(markdown, "")
        if (evidence != EvidenceLevel.VALIDATED_ASSESSMENT) {
            out = out.replace(percent, "").replace(scoreOutOf, "")
            for ((pattern, replacement) in heardClaims) out = out.replace(pattern, replacement)
        }
        return out.replace(Regex("\\s{2,}"), " ").replace(Regex("\\s+([.,!?])"), "$1").trim()
    }

    /** True when text would need changing; used in tests and for logging prompt quality. */
    fun violates(text: String, evidence: EvidenceLevel): Boolean = sanitize(text, evidence) != text.trim()
}
