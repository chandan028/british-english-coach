# My British English Coach

A personal, offline-first Android app for practising spoken British English with a warm, patient coach. Gemma 4 E2B runs on the phone through LiteRT-LM, speech is transcribed on the phone, and all progress stays on the phone.

## Build and install

1. Open this folder in Android Studio (Narwhal or newer) and let Gradle sync.
2. Plug in your phone with USB debugging on and press **Run**, or build an APK with `./gradlew :app:assembleRelease` (it's signed with the debug key so it installs directly; personal use only).

Requirements: Android 8.0+ to install. Offline transcription needs **Android 13+**. The AI coach needs a phone with about 6 GB RAM or more and ~3 GB free storage.

## First run (all one-time, all one tap)

| What | Where | Size |
|---|---|---|
| AI coach model (Gemma 4 E2B Instruct) | Home or Settings › **Download AI coach** | ~2.6 GB |
| Offline British English speech pack | Settings › Offline speech recognition › **Download speech pack** (or the prompt on a lesson) | Small, managed by Android |
| A British English voice | Settings › Coach's voice › **Get more voices** (only if none is listed) | Managed by your speech engine |

The model downloads from Google's official [litert-community/gemma-4-E2B-it-litert-lm](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm) repository (Apache 2.0) using Android's DownloadManager, so it continues in the background, shows a notification, waits for Wi-Fi by default and resumes after drops. The file is stored in the app's own storage (`Android/data/com.britishcoach.app/files/models/`), never inside the APK. After that the app needs no internet. If you already have a `.litertlm` file, use Settings › **I already have the model file**.

Lessons, the reference voice, recording, replay and transcript comparison all work before the model is downloaded, and keep working if it ever fails to load.

## What's in it

- **Home**: current stage, today's recommended lesson, daily goal and minutes, revision due, recurring transcript differences, weekly summary, recent practice.
- **Learn**: 31 lessons across the five PRD stages, with SSBE IPA, stress guides and tips. Lessons unlock in order (the next stage opens at 60% of the previous one) and completed lessons always stay open.
- **Lesson / Repeat after the coach**: Listen, Slowly, record (with a live level meter), replay yourself, word-by-word comparison, coach feedback (spoken aloud), Try again, Next, and an Easy/OK/Hard self-rating.
- **Speak**: repeat mode, guided conversations (Stage 3+) and free conversation. Speak or type answers; the coach asks one question at a time and gives a short review at the end, which is saved to the notebook.
- **My Mistakes**: corrections saved automatically with target, transcript, correction, category, explanation, exercise, attempts, last practised and next review. Practise, mark mastered or delete. Spaced revision at 1, 2, 4, 7, 14 and 30 days.
- **Progress**: minutes for the last 7 days against your goal, weekly trend, lessons per stage, repeated differences, difficulty ratings and attempt history.
- **Daily session**: the PRD's 3/5/7/3/2-minute plan, re-planned for 10, 20 or 30 minutes, filled from your own lessons and due revision.
- **Settings**: voice choice with preview, speech speed, daily goal, feedback detail, model download/import/delete, Wi-Fi-only, GPU on/off, speech pack, recording retention, delete all recordings, export and restore.

## Honest feedback rules (built in, not just prompted)

- Every result is labelled with its evidence: *transcript comparison*, *no analysis*, or (only once a validated engine is added) *measured by assessment engine*.
- The coach's prompt is the PRD persona, word for word, plus app rules saying it only receives text.
- Model output passes through `Grounding.sanitize`, which strips invented percentages and scores and rewrites "I heard you…" or "you mispronounced…" when only a transcript exists. The model can't label anything as a measured pronunciation result.
- A different word in the transcript is always described as uncertain ("the recogniser wrote…"), never as proof of a mispronunciation.
- There is no accent score and no single proficiency number. The weekly "words matched" figure is explicitly described as a transcript measure.

## Architecture

```
core/   Pure Kotlin, no Android: curriculum, transcript comparison, persona and prompts,
        JSON feedback parsing and grounding, rule-based fallback feedback, revision
        scheduling, unlocks, recommendations, daily planner, progress maths, backup format.
app/    Android: Jetpack Compose UI (MVVM), Room database, repositories, AudioRecord
        recorder (16 kHz WAV), MediaPlayer playback, TextToSpeech voice, on-device
        SpeechRecognizer, DownloadManager model download, LiteRT-LM engine.
```

- `ai/CoachModel.kt`: the only file that touches LiteRT-LM. One engine, one dedicated thread, GPU first then CPU fallback, a free-memory check before loading, and every failure turned into a message rather than a crash.
- `ai/CoachService.kt`: Gemma when ready, rule-based feedback otherwise; generation is cancellable (the conversation screen has "Stop the coach").
- `speech/SpeechTranscriber.kt`: records first so you can replay yourself, then feeds the same audio to Android's on-device recogniser (`EXTRA_AUDIO_SOURCE`). A phoneme-level assessment engine can be added later as a separate component; `AssessmentData.phonemeAssessment` is the slot for it.
- Room entities match the PRD data model (Lesson, PracticeAttempt, Mistake, UserProgress, Settings) plus PracticeSession for practice minutes.

## Tests

- `./gradlew -p tools/core-check :core:test` runs the core unit tests on any JVM, no Android SDK needed (23 tests: comparison, grounding, parsing, fallback feedback, unlocks, recommendations, scheduling, planner, progress and backup).
- `app/src/androidTest` has Room/repository instrumentation tests (notebook merging, mastery, backup round trip). Run them with `./gradlew :app:connectedAndroidTest` on a phone or emulator.

## Phase 6: checks to do on your phone

1. Download the model on Wi-Fi and confirm the coach shows "Ready (GPU)" or "(CPU)".
2. Time a few feedback replies and conversation turns; if they're slow, try the GPU build in Settings › AI model options, or switch GPU off.
3. Watch memory, heat and battery over a 20-minute session.
4. Record 10–20 real attempts and check how well the transcripts match what you said.
5. Pick the British voice you like best and set a comfortable speed.
6. Turn on airplane mode and complete a beginner lesson end to end.
