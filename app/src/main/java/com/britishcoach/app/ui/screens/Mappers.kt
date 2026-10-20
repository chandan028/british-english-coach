package com.britishcoach.app.ui.screens

import com.britishcoach.app.data.db.PracticeAttemptEntity
import com.britishcoach.app.data.db.PracticeSessionEntity
import com.britishcoach.core.progress.AttemptFact
import com.britishcoach.core.progress.PracticeTimeFact

fun PracticeAttemptEntity.toFact() = AttemptFact(
    timestamp = timestamp,
    durationMs = duration,
    lessonId = lessonId,
    targetWordCount = targetWordCount,
    matchedWordCount = matchedWordCount,
    differingWords = differingWords.split(',').filter { it.isNotBlank() },
)

fun PracticeSessionEntity.toFact() = PracticeTimeFact(startedAt, durationMs)
