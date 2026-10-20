package com.britishcoach.app.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

/** A British English voice the phone can speak with. */
data class VoiceOption(
    val name: String,
    /** A readable label built from the engine's voice name. */
    val label: String,
    val offline: Boolean,
    val quality: Int,
)

sealed interface VoiceStatus {
    data object Initialising : VoiceStatus
    data class Ready(val voices: List<VoiceOption>, val selected: VoiceOption?) : VoiceStatus
    /** TTS works but has no en-GB voice installed. */
    data object NoBritishVoice : VoiceStatus
    data class Unavailable(val message: String) : VoiceStatus
}

/**
 * The coach's voice, using Android TextToSpeech. The PRD notes that Android does
 * not tell apps a voice's gender, so the app lists every British English voice
 * the phone has and lets the learner preview them and pick the one they like.
 */
class CoachVoice(private val context: Context) {

    private val _status = MutableStateFlow<VoiceStatus>(VoiceStatus.Initialising)
    val status: StateFlow<VoiceStatus> = _status.asStateFlow()

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    private val ready = CompletableDeferred<Boolean>()
    private val pending = mutableMapOf<String, (Boolean) -> Unit>()
    private var rate = 0.9f
    private var preferredName: String? = null

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { code ->
        if (code == TextToSpeech.SUCCESS) {
            refreshVoices()
            ready.complete(true)
        } else {
            _status.value = VoiceStatus.Unavailable("Text-to-speech isn't available on this phone. Install or enable a speech engine in Android settings.")
            ready.complete(false)
        }
    }

    private val cacheDir = File(context.cacheDir, "reference-audio").apply { mkdirs() }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { _speaking.value = true }
            override fun onDone(utteranceId: String?) = finish(utteranceId, true)
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finish(utteranceId, false)
            override fun onError(utteranceId: String?, errorCode: Int) = finish(utteranceId, false)
            override fun onStop(utteranceId: String?, interrupted: Boolean) = finish(utteranceId, false)
        })
    }

    private fun finish(id: String?, ok: Boolean) {
        _speaking.value = false
        val cb = synchronized(pending) { id?.let { pending.remove(it) } }
        cb?.invoke(ok)
    }

    fun refreshVoices() {
        val voices = runCatching { tts.voices }.getOrNull().orEmpty()
            .filter { it.locale.language == "en" && it.locale.country == "GB" }
            .filter { !it.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }
            .sortedWith(compareBy<Voice> { it.isNetworkConnectionRequired }.thenByDescending { it.quality }.thenBy { it.name })
            .map { VoiceOption(it.name, labelFor(it), !it.isNetworkConnectionRequired, it.quality) }
        if (voices.isEmpty()) {
            val gbOk = tts.isLanguageAvailable(Locale.UK) >= TextToSpeech.LANG_AVAILABLE
            if (gbOk) {
                tts.language = Locale.UK
                _status.value = VoiceStatus.Ready(emptyList(), null)
            } else {
                _status.value = VoiceStatus.NoBritishVoice
            }
            return
        }
        val selected = voices.firstOrNull { it.name == preferredName } ?: voices.first()
        applyVoice(selected.name)
        _status.value = VoiceStatus.Ready(voices, selected)
    }

    private fun labelFor(v: Voice): String {
        // Engine names look like "en-gb-x-gba-local". Keep the distinctive part.
        val core = v.name.removePrefix("en-gb-x-").removePrefix("en-GB-").removeSuffix("-local").removeSuffix("-network")
        val where = if (v.isNetworkConnectionRequired) "needs internet" else "offline"
        return "British voice ${core.uppercase(Locale.ROOT)} ($where)"
    }

    private fun applyVoice(name: String) {
        tts.voices?.firstOrNull { it.name == name }?.let { tts.voice = it }
    }

    fun configure(voiceName: String?, speechRate: Float) {
        preferredName = voiceName
        rate = speechRate.coerceIn(0.5f, 1.5f)
        tts.setSpeechRate(rate)
        val st = _status.value
        if (st is VoiceStatus.Ready && voiceName != null) {
            val v = st.voices.firstOrNull { it.name == voiceName } ?: return
            applyVoice(v.name)
            _status.value = st.copy(selected = v)
        }
    }

    /** Speaks [text] and suspends until it finishes. Returns false if it couldn't be spoken. */
    suspend fun speak(text: String, rateOverride: Float? = null): Boolean {
        if (!awaitReady()) return false
        stop()
        val id = UUID.randomUUID().toString()
        return suspendCancellableCoroutine { cont ->
            synchronized(pending) { pending[id] = { ok -> if (cont.isActive) cont.resume(ok) } }
            tts.setSpeechRate(rateOverride ?: rate)
            val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), id)
            if (result != TextToSpeech.SUCCESS) {
                synchronized(pending) { pending.remove(id) }
                if (cont.isActive) cont.resume(false)
            }
            cont.invokeOnCancellation { tts.stop() }
        }
    }

    fun stop() {
        if (tts.isSpeaking) tts.stop()
        _speaking.value = false
    }

    /**
     * Writes reference audio for [text] to a cached WAV file so it can be replayed
     * instantly and compared with the learner's recording. Returns null on failure.
     */
    suspend fun synthesizeToCache(text: String): File? {
        if (!awaitReady()) return null
        val key = sha1("${tts.voice?.name}|$rate|$text")
        val file = File(cacheDir, "$key.wav")
        if (file.exists() && file.length() > 0) return file
        val id = UUID.randomUUID().toString()
        val ok = withTimeoutOrNull(20_000) {
            suspendCancellableCoroutine { cont ->
                synchronized(pending) { pending[id] = { ok -> if (cont.isActive) cont.resume(ok) } }
                tts.setSpeechRate(rate)
                val r = tts.synthesizeToFile(text, Bundle(), file, id)
                if (r != TextToSpeech.SUCCESS) {
                    synchronized(pending) { pending.remove(id) }
                    if (cont.isActive) cont.resume(false)
                }
            }
        } ?: false
        return if (ok && file.exists() && file.length() > 0) file else { file.delete(); null }
    }

    fun clearCache() {
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    /** Opens the system screen for installing voice data, e.g. when no British voice is present. */
    fun installVoiceDataIntent(): Intent =
        Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private suspend fun awaitReady(): Boolean = ready.await()

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
