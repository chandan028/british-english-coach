package com.britishcoach.core.model

import kotlinx.serialization.Serializable

/** The five curriculum stages from the PRD, in learning order. */
enum class Stage(val number: Int, val title: String, val summary: String) {
    FOUNDATIONS(1, "Foundations", "Sounds, basic words and simple sentences"),
    PRONUNCIATION(2, "Pronunciation", "Vowels, consonants, syllables and word stress"),
    EVERYDAY(3, "Everyday conversation", "Introductions, questions and daily situations"),
    PROFESSIONAL(4, "Fluency and professional English", "Stories, meetings, interviews and technical talk"),
    ADVANCED(5, "Advanced communication", "Opinions, debate, connected speech and nuance");

    companion object {
        fun fromNumber(n: Int): Stage = entries.firstOrNull { it.number == n } ?: FOUNDATIONS
    }
}

enum class LessonCategory(val label: String) {
    SOUNDS("Sounds"),
    VOCABULARY("Vocabulary"),
    SENTENCES("Sentences"),
    EXPRESSIONS("Expressions"),
    VOWELS("Vowels"),
    CONSONANTS("Consonants"),
    WORD_STRESS("Word stress"),
    SENTENCE_STRESS("Sentence stress"),
    INTONATION("Intonation"),
    CONNECTED_SPEECH("Connected speech"),
    CONVERSATION("Conversation"),
    PROFESSIONAL("Professional"),
}

/** How a lesson is practised. */
enum class PracticeMode { REPEAT_AFTER_COACH, GUIDED_CONVERSATION }

/**
 * One practice item in a lesson. A lesson has several so that a session can
 * cycle through words and sentences rather than repeating one line.
 */
@Serializable
data class PracticeItem(
    val text: String,
    /** IPA in Standard Southern British English, with primary stress marked by ˈ. */
    val phonetic: String? = null,
    /** Human-readable stress guide, e.g. "TO-ma-to → to-MAH-to" with stressed syllable in capitals. */
    val stressGuide: String? = null,
)

data class LessonContent(
    val id: String,
    val title: String,
    val stage: Stage,
    val category: LessonCategory,
    /** Order within the whole curriculum, starting at 1. */
    val lessonOrder: Int,
    val mode: PracticeMode,
    /** What the coach says to introduce the lesson. */
    val introduction: String,
    /** The main target items, in practice order. */
    val items: List<PracticeItem>,
    /** Short, practical tips about the target sound, stress or usage. */
    val tips: List<String> = emptyList(),
    /** For guided conversation lessons: the topic and the coach's first question. */
    val conversationTopic: String? = null,
    val openingQuestion: String? = null,
) {
    val targetText: String get() = items.firstOrNull()?.text.orEmpty()
    val phoneticGuide: String? get() = items.firstOrNull()?.phonetic
}

/**
 * The kind of difference or mistake. The PRD requires pronunciation guidance to be
 * kept separate from grammar and vocabulary, and transcript differences to be
 * kept separate from proven pronunciation errors.
 */
enum class ErrorCategory(val label: String) {
    /** The recogniser heard different or missing words. Not proof of mispronunciation. */
    TRANSCRIPT_DIFFERENCE("Transcript difference"),
    GRAMMAR("Grammar"),
    VOCABULARY("Vocabulary"),
    BRITISH_USAGE("British usage"),
    PRONUNCIATION_PRACTICE("Pronunciation practice"),
    FLUENCY("Fluency"),
    /** Only used when a validated phoneme-level assessment engine supplied the result. */
    MEASURED_PRONUNCIATION("Measured pronunciation"),
}

enum class MasteryStatus { LEARNING, REVIEWING, MASTERED }

enum class CompletionStatus { NOT_STARTED, IN_PROGRESS, COMPLETED }

/**
 * How strong the evidence behind a piece of feedback is. Shown in the UI so the
 * learner always knows whether a result was measured or is guidance.
 */
enum class EvidenceLevel(val label: String, val explanation: String) {
    NONE(
        "No analysis",
        "No speech was analysed. This is general guidance only.",
    ),
    TRANSCRIPTION(
        "Transcript comparison",
        "Based only on the words the speech recogniser wrote down. A different word can come from " +
            "pronunciation, background noise, or the recogniser itself, so it is not proof of a mispronunciation.",
    ),
    VALIDATED_ASSESSMENT(
        "Measured by assessment engine",
        "Sound-level results from a validated pronunciation assessment engine.",
    ),
}

enum class FeedbackDetail { BRIEF, STANDARD, DETAILED }
