package com.britishcoach.core

import com.britishcoach.core.analysis.TranscriptComparer
import com.britishcoach.core.analysis.WordDiff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptComparerTest {

    @Test
    fun `exact match ignores case and punctuation`() {
        val c = TranscriptComparer.compare("Hello, how are you?", "hello how are you")
        assertTrue(c.isExactMatch)
        assertEquals(4, c.matchedCount)
    }

    @Test
    fun `contractions digits and spellings are normalised`() {
        assertTrue(TranscriptComparer.compare("I'm fine, thank you.", "I am fine thank you").isExactMatch)
        assertTrue(TranscriptComparer.compare("The train leaves at nine.", "the train leaves at 9").isExactMatch)
        assertTrue(TranscriptComparer.compare("My favourite colour", "my favorite color").isExactMatch)
        assertTrue(TranscriptComparer.compare("I’m here", "i'm here").isExactMatch)
    }

    @Test
    fun `lesson annotations in brackets are ignored`() {
        assertTrue(TranscriptComparer.compare("a present (noun)", "a present").isExactMatch)
    }

    @Test
    fun `substitution is reported with expected and heard`() {
        val c = TranscriptComparer.compare("I think so", "I sink so")
        assertEquals(listOf(WordDiff.Substitution("think", "sink")), c.substitutions)
        assertEquals(listOf("think"), c.differingTargetWords)
        assertEquals(2, c.matchedCount)
        assertFalse(c.isExactMatch)
    }

    @Test
    fun `omission and insertion`() {
        val c = TranscriptComparer.compare("She walked to the shops", "she walked the shops today")
        assertEquals(listOf(WordDiff.Omission("to")), c.omissions)
        assertEquals(listOf(WordDiff.Insertion("today")), c.insertions)
        assertEquals(5, c.targetWordCount)
    }

    @Test
    fun `empty recognition`() {
        val c = TranscriptComparer.compare("Hello", "")
        assertTrue(c.isEmptyRecognition)
        assertEquals(listOf(WordDiff.Omission("hello")), c.diffs)
    }
}
