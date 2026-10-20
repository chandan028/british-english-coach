package com.britishcoach.app.ai

import com.britishcoach.core.analysis.TranscriptComparison
import com.britishcoach.core.coach.CoachPersona
import com.britishcoach.core.coach.FeedbackParser
import com.britishcoach.core.coach.FeedbackPrompts
import com.britishcoach.core.coach.Grounding
import com.britishcoach.core.coach.RuleBasedFeedback
import com.britishcoach.core.coach.StructuredFeedback
import com.britishcoach.core.model.EvidenceLevel
import com.britishcoach.core.model.FeedbackDetail
import com.britishcoach.core.model.LessonContent
import com.britishcoach.core.model.PracticeItem
import com.britishcoach.core.model.Stage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

/**
 * The coach's brain. Uses Gemma when it's loaded and falls back to the built-in
 * rules otherwise, so a model problem never stops a lesson.
 */
class CoachService(private val model: CoachModel) {

    val isAiReady: Boolean get() = model.state.value is ModelState.Ready

    suspend fun feedbackForAttempt(
        lesson: LessonContent,
        item: PracticeItem,
        comparison: TranscriptComparison,
        recognizerConfidence: Float?,
        attemptNumber: Int,
        previousMatched: Int?,
        stage: Stage,
        detail: FeedbackDetail,
    ): StructuredFeedback {
        val rules = RuleBasedFeedback.forAttempt(lesson, item, comparison, attemptNumber, previousMatched)
        if (!isAiReady || comparison.isEmptyRecognition) return rules
        return try {
            val raw = withTimeout(AI_TIMEOUT_MS) {
                model.generate(
                    CoachPersona.systemPrompt(stage, detail),
                    FeedbackPrompts.repeatAttempt(lesson, item, comparison, recognizerConfidence, attemptNumber, detail),
                    temperature = 0.3,
                )
            }
            val ai = FeedbackParser.parse(raw, EvidenceLevel.TRANSCRIPTION) ?: return rules
            if (ai.spoken.isBlank()) return rules
            // Keep the measured transcript differences even if the model left them out.
            val corrections = ai.corrections.ifEmpty { rules.corrections }
            ai.copy(corrections = corrections, suggestRetry = ai.suggestRetry || rules.suggestRetry && !comparison.isExactMatch)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            rules
        }
    }

    /** Starts a guided (lesson-based) or free conversation. Throws if the AI isn't ready. */
    suspend fun startConversation(lesson: LessonContent?, stage: Stage, detail: FeedbackDetail): ConversationSession {
        val system = CoachPersona.systemPrompt(stage, detail) + "\n" + if (lesson?.conversationTopic != null) {
            "- This is guided conversation practice about: ${lesson.conversationTopic}. Ask one question at a time and keep the learner talking. " +
                "Don't correct every sentence; at most mention one important correction briefly, then carry on."
        } else {
            "- This is free conversation practice. Chat naturally about whatever the learner likes, ask one question at a time, " +
                "and stay in character as their English coach. Don't correct every sentence."
        }
        val opening = lesson?.openingQuestion ?: "Hello! What would you like to chat about today? It can be anything at all."
        return ConversationSession(model.startChat(system), opening, lesson?.conversationTopic, model, stage, detail)
    }

    companion object {
        const val AI_TIMEOUT_MS = 90_000L
    }
}

class ConversationSession internal constructor(
    private val chat: CoachChat,
    val openingLine: String,
    val topic: String?,
    private val model: CoachModel,
    private val stage: Stage,
    private val detail: FeedbackDetail,
) : AutoCloseable {
    private val turns = mutableListOf("Coach" to openingLine)
    private var first = true

    val transcript: List<Pair<String, String>> get() = turns.toList()

    /** Streams the coach's reply. The full, cleaned reply is recorded when the stream completes. */
    fun reply(learnerText: String): Flow<String> = flow {
        turns += "Learner" to learnerText
        val message = if (first) {
            first = false
            "(You opened the conversation by saying: \"$openingLine\")\nLearner: $learnerText"
        } else {
            learnerText
        }
        val sb = StringBuilder()
        chat.send(message).collect { chunk ->
            sb.append(chunk)
            emit(sb.toString())
        }
        val clean = Grounding.sanitize(sb.toString(), EvidenceLevel.TRANSCRIPTION)
        turns += "Coach" to clean
        emit(clean)
    }

    /** The end-of-conversation review, with corrections for the notebook. */
    suspend fun review(): StructuredFeedback? {
        if (turns.none { it.first == "Learner" }) return null
        return try {
            val raw = withTimeout(CoachService.AI_TIMEOUT_MS) {
                model.generate(CoachPersona.systemPrompt(stage, detail), FeedbackPrompts.conversationSummary(turns), temperature = 0.3)
            }
            FeedbackParser.parse(raw, EvidenceLevel.TRANSCRIPTION)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            null
        }
    }

    override fun close() = chat.close()
}
