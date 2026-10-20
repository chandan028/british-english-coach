package com.britishcoach.app.audio

import android.media.AudioAttributes
import android.media.MediaPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** Plays back the learner's own recordings. One clip at a time. */
class AudioPlayer {
    private var player: MediaPlayer? = null
    private val _playing = MutableStateFlow<String?>(null)
    /** Path of the file currently playing, or null. */
    val playing: StateFlow<String?> = _playing.asStateFlow()

    fun play(file: File, onError: (String) -> Unit = {}) {
        stop()
        if (!file.exists()) {
            onError("That recording is no longer on the phone.")
            return
        }
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                setDataSource(file.absolutePath)
                setOnCompletionListener { stop() }
                setOnErrorListener { _, _, _ -> stop(); onError("Couldn't play the recording."); true }
                prepare()
                start()
            }
            _playing.value = file.absolutePath
        } catch (e: Exception) {
            stop()
            onError("Couldn't play the recording.")
        }
    }

    fun stop() {
        player?.runCatching { if (isPlaying) stop(); release() }
        player = null
        _playing.value = null
    }
}
