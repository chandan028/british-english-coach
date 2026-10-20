package com.britishcoach.app.speech

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi
import com.britishcoach.app.audio.AudioRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.Executors
import kotlin.coroutines.resume

data class Transcription(
    val text: String,
    /** Recogniser confidence for the transcript, 0..1, only if the engine reported one. */
    val confidence: Float?,
    val alternatives: List<String>,
)

sealed interface TranscriberStatus {
    data object Checking : TranscriberStatus
    data object Ready : TranscriberStatus
    /** The engine exists but the British English pack isn't on the phone yet. */
    data object LanguagePackMissing : TranscriberStatus
    data object DownloadingLanguagePack : TranscriberStatus
    data class Unavailable(val reason: String) : TranscriberStatus
}

sealed interface TranscriptionResult {
    data class Success(val transcription: Transcription) : TranscriptionResult
    data object NoSpeech : TranscriptionResult
    data class Error(val message: String) : TranscriptionResult
}

interface SpeechTranscriber {
    suspend fun status(): TranscriberStatus
    /** Asks the system to download the offline British English pack (one tap from Settings). */
    suspend fun downloadLanguagePack(): Boolean
    suspend fun transcribe(wav: File): TranscriptionResult
}

/**
 * Offline transcription with Android's on-device speech recogniser
 * (SpeechRecognizer.createOnDeviceSpeechRecognizer). The app records its own WAV
 * first, so the learner can replay it, then feeds that same audio to the
 * recogniser via EXTRA_AUDIO_SOURCE, which needs Android 13 or newer.
 *
 * Nothing leaves the phone: the on-device recogniser never uses the network.
 */
class OnDeviceSpeechTranscriber(private val context: Context) : SpeechTranscriber {

    private val language = "en-GB"

    override suspend fun status(): TranscriberStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return TranscriberStatus.Unavailable("Offline transcription needs Android 13 or newer. You can still listen, record and replay.")
        }
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            return TranscriberStatus.Unavailable("This phone has no on-device speech recogniser. Installing or updating the Google app usually adds one.")
        }
        return withContext(Dispatchers.Main) { checkSupport() }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun checkSupport(): TranscriberStatus {
        val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val result = withTimeoutOrNull(5_000) {
                suspendCancellableCoroutine { cont ->
                    recognizer.checkRecognitionSupport(
                        recognizerIntent(),
                        executor,
                        object : RecognitionSupportCallback {
                            override fun onSupportResult(support: RecognitionSupport) {
                                val installed = support.installedOnDeviceLanguages
                                val status = when {
                                    installed.any { it.equals(language, true) } -> TranscriberStatus.Ready
                                    support.pendingOnDeviceLanguages.any { it.equals(language, true) } -> TranscriberStatus.DownloadingLanguagePack
                                    support.supportedOnDeviceLanguages.any { it.equals(language, true) } -> TranscriberStatus.LanguagePackMissing
                                    // Some engines only list "en-US"; British English may still work through the generic pack.
                                    installed.any { it.startsWith("en", true) } -> TranscriberStatus.Ready
                                    else -> TranscriberStatus.LanguagePackMissing
                                }
                                if (cont.isActive) cont.resume(status)
                            }

                            override fun onError(error: Int) {
                                // Older engines don't implement the support check; assume they can try.
                                if (cont.isActive) cont.resume(TranscriberStatus.Ready)
                            }
                        },
                    )
                }
            }
            return result ?: TranscriberStatus.Ready
        } finally {
            recognizer.destroy()
            executor.shutdown()
        }
    }

    override suspend fun downloadLanguagePack(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        return withContext(Dispatchers.Main) {
            runCatching {
                val r = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                r.triggerModelDownload(recognizerIntent())
                r.destroy()
                true
            }.getOrDefault(false)
        }
    }

    override suspend fun transcribe(wav: File): TranscriptionResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return TranscriptionResult.Error("Offline transcription needs Android 13 or newer.")
        }
        val pcm = withContext(Dispatchers.IO) { toPaddedPcm(wav) } ?: return TranscriptionResult.Error("The recording couldn't be read.")
        try {
            return withContext(Dispatchers.Main) {
                withTimeoutOrNull(45_000) { runRecognizer(pcm) } ?: TranscriptionResult.Error("Transcription took too long. Please try a shorter recording.")
            }
        } finally {
            pcm.delete()
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun runRecognizer(pcm: File): TranscriptionResult {
        val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        val pfd = ParcelFileDescriptor.open(pcm, ParcelFileDescriptor.MODE_READ_ONLY)
        try {
            return suspendCancellableCoroutine { cont ->
                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onResults(results: Bundle) {
                        val texts = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                        val scores = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                        val best = texts.firstOrNull()?.trim().orEmpty()
                        val conf = scores?.firstOrNull()?.takeIf { it in 0f..1f }
                        val r = if (best.isEmpty()) TranscriptionResult.NoSpeech
                        else TranscriptionResult.Success(Transcription(best, conf, texts.drop(1)))
                        if (cont.isActive) cont.resume(r)
                    }

                    override fun onError(error: Int) {
                        val r = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> TranscriptionResult.NoSpeech
                            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                                TranscriptionResult.Error("The offline British English speech pack isn't installed. You can download it in Settings.")
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> TranscriptionResult.Error("The speech recogniser is busy. Please try again in a moment.")
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> TranscriptionResult.Error("The speech recogniser needs microphone permission.")
                            else -> TranscriptionResult.Error("The speech recogniser couldn't process that recording (code $error).")
                        }
                        if (cont.isActive) cont.resume(r)
                    }

                    override fun onReadyForSpeech(params: Bundle?) = Unit
                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() = Unit
                    override fun onPartialResults(partialResults: Bundle?) = Unit
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })
                val intent = recognizerIntent().apply {
                    putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pfd)
                    putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                    putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                    putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, AudioRecorder.SAMPLE_RATE)
                }
                recognizer.startListening(intent)
                cont.invokeOnCancellation { recognizer.cancel() }
            }
        } finally {
            recognizer.destroy()
            pfd.close()
        }
    }

    private fun recognizerIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
    }

    /** Strips the WAV header and adds a second of silence so the recogniser detects the end of speech. */
    private fun toPaddedPcm(wav: File): File? = runCatching {
        val out = File.createTempFile("asr", ".pcm", context.cacheDir)
        wav.inputStream().use { input ->
            out.outputStream().use { o ->
                input.skip(AudioRecorder.WAV_HEADER_BYTES.toLong())
                input.copyTo(o)
                o.write(ByteArray(AudioRecorder.SAMPLE_RATE * 2))
            }
        }
        out
    }.getOrNull()
}
