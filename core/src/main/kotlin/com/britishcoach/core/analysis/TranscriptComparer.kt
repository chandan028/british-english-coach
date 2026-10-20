package com.britishcoach.core.analysis

import kotlinx.serialization.Serializable

/** One aligned position between the target sentence and what the recogniser wrote. */
@Serializable
sealed interface WordDiff {
    @Serializable
    data class Match(val word: String) : WordDiff

    /** The recogniser wrote [heard] where the target had [expected]. */
    @Serializable
    data class Substitution(val expected: String, val heard: String) : WordDiff

    /** A target word with nothing recognised in its place. */
    @Serializable
    data class Omission(val expected: String) : WordDiff

    /** A recognised word that is not in the target. */
    @Serializable
    data class Insertion(val heard: String) : WordDiff
}

@Serializable
data class TranscriptComparison(
    val target: String,
    val recognized: String,
    val diffs: List<WordDiff>,
) {
    val targetWordCount: Int get() = diffs.count { it !is WordDiff.Insertion }
    val matchedCount: Int get() = diffs.count { it is WordDiff.Match }
    val substitutions: List<WordDiff.Substitution> get() = diffs.filterIsInstance<WordDiff.Substitution>()
    val omissions: List<WordDiff.Omission> get() = diffs.filterIsInstance<WordDiff.Omission>()
    val insertions: List<WordDiff.Insertion> get() = diffs.filterIsInstance<WordDiff.Insertion>()

    val isExactMatch: Boolean get() = targetWordCount > 0 && matchedCount == targetWordCount && insertions.isEmpty()
    val isEmptyRecognition: Boolean get() = recognized.isBlank()

    /**
     * Words the recogniser matched, as a count over the target. This describes the
     * transcript only. It is deliberately not presented as a pronunciation score.
     */
    val matchedWordsLabel: String get() = "$matchedCount of $targetWordCount words recognised as expected"

    /** Target words that were missing or recognised as something else, for revision tracking. */
    val differingTargetWords: List<String>
        get() = diffs.mapNotNull {
            when (it) {
                is WordDiff.Substitution -> it.expected
                is WordDiff.Omission -> it.expected
                else -> null
            }
        }
}

/**
 * Word-level comparison between the target text and the recogniser output, using a
 * minimum edit distance alignment. Normalisation removes differences that don't
 * matter for speaking practice (case, punctuation, digits vs words, common
 * contractions, British/American spellings), so they don't show up as "mistakes".
 */
object TranscriptComparer {

    fun compare(target: String, recognized: String): TranscriptComparison {
        val t = tokenize(target)
        val r = tokenize(recognized)
        return TranscriptComparison(target, recognized, align(t, r))
    }

    fun tokenize(text: String): List<String> {
        val cleaned = text
            .lowercase()
            .replace('’', '\'')
            .replace('‘', '\'')
            .replace(Regex("\\([^)]*\\)"), " ") // "(noun)" style lesson annotations
            .replace(Regex("[^a-z0-9' ]"), " ")
        return cleaned.split(Regex("\\s+"))
            .map { it.trim('\'') }
            .filter { it.isNotEmpty() }
            .flatMap { expand(it) }
            .map { normalizeWord(it) }
    }

    private val contractions = mapOf(
        "i'm" to listOf("i", "am"),
        "you're" to listOf("you", "are"),
        "we're" to listOf("we", "are"),
        "they're" to listOf("they", "are"),
        "it's" to listOf("it", "is"),
        "that's" to listOf("that", "is"),
        "there's" to listOf("there", "is"),
        "what's" to listOf("what", "is"),
        "let's" to listOf("let", "us"),
        "don't" to listOf("do", "not"),
        "doesn't" to listOf("does", "not"),
        "didn't" to listOf("did", "not"),
        "can't" to listOf("can", "not"),
        "won't" to listOf("will", "not"),
        "isn't" to listOf("is", "not"),
        "aren't" to listOf("are", "not"),
        "wasn't" to listOf("was", "not"),
        "i've" to listOf("i", "have"),
        "we've" to listOf("we", "have"),
        "you've" to listOf("you", "have"),
        "i'll" to listOf("i", "will"),
        "we'll" to listOf("we", "will"),
        "you'll" to listOf("you", "will"),
    )

    private val numbers = mapOf(
        "0" to "zero", "1" to "one", "2" to "two", "3" to "three", "4" to "four", "5" to "five",
        "6" to "six", "7" to "seven", "8" to "eight", "9" to "nine", "10" to "ten",
        "11" to "eleven", "12" to "twelve", "13" to "thirteen", "14" to "fourteen", "15" to "fifteen",
        "16" to "sixteen", "17" to "seventeen", "18" to "eighteen", "19" to "nineteen", "20" to "twenty",
        "30" to "thirty", "40" to "forty", "50" to "fifty", "3rd" to "third", "1st" to "first", "2nd" to "second",
    )

    /** American spellings a recogniser may output, mapped to the British spelling used in lessons. */
    private val spellings = mapOf(
        "color" to "colour", "favorite" to "favourite", "center" to "centre", "centers" to "centres",
        "theater" to "theatre", "realize" to "realise", "organize" to "organise", "traveling" to "travelling",
        "neighbor" to "neighbour", "honor" to "honour", "analyze" to "analyse", "program" to "programme",
        "okay" to "ok", "mom" to "mum", "gray" to "grey",
    )

    private fun expand(token: String): List<String> = contractions[token] ?: listOf(token)

    private fun normalizeWord(w: String): String = numbers[w] ?: spellings[w] ?: w

    private fun align(t: List<String>, r: List<String>): List<WordDiff> {
        val n = t.size
        val m = r.size
        // dp[i][j] = edit distance between t[0 until i] and r[0 until j]
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in 0..n) dp[i][0] = i
        for (j in 0..m) dp[0][j] = j
        for (i in 1..n) {
            for (j in 1..m) {
                val cost = if (t[i - 1] == r[j - 1]) 0 else 1
                dp[i][j] = minOf(dp[i - 1][j - 1] + cost, dp[i - 1][j] + 1, dp[i][j - 1] + 1)
            }
        }
        val out = ArrayDeque<WordDiff>()
        var i = n
        var j = m
        while (i > 0 || j > 0) {
            when {
                i > 0 && j > 0 && t[i - 1] == r[j - 1] && dp[i][j] == dp[i - 1][j - 1] -> {
                    out.addFirst(WordDiff.Match(t[i - 1])); i--; j--
                }
                i > 0 && j > 0 && dp[i][j] == dp[i - 1][j - 1] + 1 -> {
                    out.addFirst(WordDiff.Substitution(t[i - 1], r[j - 1])); i--; j--
                }
                i > 0 && dp[i][j] == dp[i - 1][j] + 1 -> {
                    out.addFirst(WordDiff.Omission(t[i - 1])); i--
                }
                else -> {
                    out.addFirst(WordDiff.Insertion(r[j - 1])); j--
                }
            }
        }
        return out.toList()
    }
}
