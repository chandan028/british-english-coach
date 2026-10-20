package com.britishcoach.core

import com.britishcoach.core.analysis.TranscriptComparer
import com.britishcoach.core.coach.CoachPersona
import com.britishcoach.core.coach.FeedbackParser
import com.britishcoach.core.coach.FeedbackPrompts
import com.britishcoach.core.coach.Grounding
import com.britishcoach.core.coach.RuleBasedFeedback
import com.britishcoach.core.curriculum.Curriculum
import com.britishcoach.core.model.ErrorCategory
import com.britishcoach.core.model.EvidenceLevel
import com.britishcoach.core.model.FeedbackDetail
import com.britishcoach.core.model.Stage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackTest {

    @Test
    fun `parser extracts json wrapped in a code fence`() {
        val raw = """
            Sure! Here you go:
            ```json
            {"say": "Lovely, nearly there. Let's work on 'think'.", "retry": true,
             "corrections": [{"type": "transcript_difference", "issue": "'think' written as 'sink'",
               "corrected": "I think so.", "why": "Could be the th sound, or the recogniser {sometimes} errs.", "practice": "Say think three times."}]}
            ```
        """.trimIndent()
        val f = FeedbackParser.parse(raw, EvidenceLevel.TRANSCRIPTION)
        assertNotNull(f)
        f!!
        assertTrue(f.suggestRetry)
        assertEquals(ErrorCategory.TRANSCRIPT_DIFFERENCE, f.corrections.single().category)
        assertEquals("I think so.", f.corrections.single().corrected)
    }

    @Test
    fun `parser returns null for non json`() {
        assertNull(FeedbackParser.parse("I couldn't do that.", EvidenceLevel.TRANSCRIPTION))
    }

    @Test
    fun `model cannot claim a measured result without an assessment engine`() {
        val raw = """{"say":"ok","corrections":[{"type":"MEASURED_PRONUNCIATION","issue":"th","why":"x","practice":"y"}]}"""
        val f = FeedbackParser.parse(raw, EvidenceLevel.TRANSCRIPTION)!!
        assertEquals(ErrorCategory.PRONUNCIATION_PRACTICE, f.corrections.single().category)
    }

    @Test
    fun `grounding removes scores and hearing claims`() {
        val out = Grounding.sanitize("I heard you say sink. Your pronunciation is 85% accurate, about 7/10.", EvidenceLevel.TRANSCRIPTION)
        assertFalse(out.contains("85"))
        assertFalse(out.contains("7/10"))
        assertFalse(out.contains("I heard", ignoreCase = true))
        assertTrue(Grounding.violates("You mispronounced it.", EvidenceLevel.TRANSCRIPTION))
        assertFalse(Grounding.violates("Let's practise the word again.", EvidenceLevel.TRANSCRIPTION))
    }

    @Test
    fun `rule based feedback never diagnoses pronunciation`() {
        val lesson = Curriculum.byId("s1-th")!!
        val item = lesson.items[0]
        val c = TranscriptComparer.compare(item.text, "sink")
        val f = RuleBasedFeedback.forAttempt(lesson, item, c, attemptNumber = 1)
        assertTrue(f.suggestRetry)
        assertTrue(f.fromFallback)
        assertEquals(EvidenceLevel.TRANSCRIPTION, f.evidence)
        assertEquals(ErrorCategory.TRANSCRIPT_DIFFERENCE, f.corrections.single().category)
        assertFalse(Grounding.violates(f.spoken, EvidenceLevel.TRANSCRIPTION))
    }

    @Test
    fun `exact match is acknowledged without a retry`() {
        val lesson = Curriculum.byId("s1-greetings")!!
        val item = lesson.items[0]
        val f = RuleBasedFeedback.forAttempt(lesson, item, TranscriptComparer.compare(item.text, item.text), 1)
        assertFalse(f.suggestRetry)
        assertTrue(f.corrections.isEmpty())
    }

    @Test
    fun `prompt carries evidence and persona keeps the PRD wording`() {
        val lesson = Curriculum.byId("s1-th")!!
        val item = lesson.items[0]
        val prompt = FeedbackPrompts.repeatAttempt(lesson, item, TranscriptComparer.compare(item.text, "sink"), null, 1, FeedbackDetail.STANDARD)
        assertTrue(prompt.contains("transcript only"))
        assertTrue(prompt.contains("expected 'think' but recogniser wrote 'sink'"))
        assertTrue(CoachPersona.systemPrompt(Stage.FOUNDATIONS, FeedbackDetail.BRIEF).startsWith("You are my personal British English speaking coach."))
    }
}
