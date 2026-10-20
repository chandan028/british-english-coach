package com.britishcoach.app.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.abs
import kotlin.math.sqrt

sealed interface RecordingState {
    data object Idle : RecordingState
    /** [level] is a 0..1 input loudness for the on-screen meter. */
    data class Recording(val startedAt: Long, val elapsedMs: Long, val level: Float) : RecordingState
    data class Finished(val file: File, val durationMs: Long, val tooQuiet: Boolean) : RecordingState
    data class Failed(val message: String) : RecordingState
}

/**
 * Records 16 kHz mono 16-bit PCM, the format offline speech recognisers expect,
 * and writes it as a WAV file so it can be replayed with MediaPlayer.
 */
class AudioRecorder(private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) {

    private val _state = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    private var job: Job? = null
    @Volatile private var stopRequested = false

    val isRecording: Boolean get() = _state.value is RecordingState.Recording

    /** Caller must hold RECORD_AUDIO; the screen requests it before showing the record button. */
    @SuppressLint("MissingPermission")
    fun start(output: File, maxDurationMs: Long = 60_000) {
        if (job?.isActive == true) return
        stopRequested = false
        job = scope.launch {
            val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (minBuf <= 0) {
                _state.value = RecordingState.Failed("This phone's microphone doesn't support the recording format.")
                return@launch
            }
            val bufferSize = maxOf(minBuf, SAMPLE_RATE / 5 * 2) // at least 200 ms
            val recorder = try {
                AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
            } catch (e: Exception) {
                _state.value = RecordingState.Failed("Couldn't open the microphone. Is another app using it?")
                return@launch
            }
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                _state.value = RecordingState.Failed("Couldn't open the microphone. Check the microphone permission in Settings.")
                return@launch
            }

            output.parentFile?.mkdirs()
            val started = System.currentTimeMillis()
            var totalBytes = 0L
            var peak = 0
            try {
                RandomAccessFile(output, "rw").use { raf ->
                    raf.setLength(0)
                    raf.write(ByteArray(WAV_HEADER_BYTES)) // placeholder, filled in at the end
                    val buf = ShortArray(bufferSize / 2)
                    val bytes = ByteArray(buf.size * 2)
                    recorder.startRecording()
                    _state.value = RecordingState.Recording(started, 0, 0f)
                    while (isActive && !stopRequested) {
                        val n = recorder.read(buf, 0, buf.size)
                        if (n < 0) throw IllegalStateException("Microphone read failed ($n)")
                        if (n == 0) continue
                        var sum = 0.0
                        for (i in 0 until n) {
                            val s = buf[i].toInt()
                            bytes[i * 2] = (s and 0xff).toByte()
                            bytes[i * 2 + 1] = (s shr 8 and 0xff).toByte()
                            sum += s.toDouble() * s
                            if (abs(s) > peak) peak = abs(s)
                        }
                        raf.write(bytes, 0, n * 2)
                        totalBytes += n * 2
                        val rms = sqrt(sum / n) / Short.MAX_VALUE
                        val elapsed = System.currentTimeMillis() - started
                        _state.value = RecordingState.Recording(started, elapsed, (rms * 4).toFloat().coerceIn(0f, 1f))
                        if (elapsed >= maxDurationMs) break
                    }
                    recorder.stop()
                    raf.seek(0)
                    raf.write(wavHeader(totalBytes))
                }
                val durationMs = totalBytes * 1000 / (SAMPLE_RATE * 2)
                _state.value = RecordingState.Finished(output, durationMs, tooQuiet = peak < QUIET_PEAK)
            } catch (e: Exception) {
                output.delete()
                _state.value = RecordingState.Failed("Recording stopped unexpectedly. Please try again.")
            } finally {
                recorder.release()
            }
        }
    }

    fun stop() {
        stopRequested = true
    }

    /** Stops without keeping the result, e.g. when leaving the screen. */
    fun cancel() {
        job?.cancel()
        _state.value = RecordingState.Idle
    }

    fun reset() {
        if (!isRecording) _state.value = RecordingState.Idle
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        const val WAV_HEADER_BYTES = 44
        private const val QUIET_PEAK = 800

        fun wavHeader(dataBytes: Long): ByteArray {
            val channels = 1
            val bits = 16
            val byteRate = SAMPLE_RATE * channels * bits / 8
            fun le32(v: Long) = byteArrayOf((v and 0xff).toByte(), (v shr 8 and 0xff).toByte(), (v shr 16 and 0xff).toByte(), (v shr 24 and 0xff).toByte())
            fun le16(v: Int) = byteArrayOf((v and 0xff).toByte(), (v shr 8 and 0xff).toByte())
            return "RIFF".toByteArray() + le32(36 + dataBytes) + "WAVE".toByteArray() +
                "fmt ".toByteArray() + le32(16) + le16(1) + le16(channels) + le32(SAMPLE_RATE.toLong()) +
                le32(byteRate.toLong()) + le16(channels * bits / 8) + le16(bits) +
                "data".toByteArray() + le32(dataBytes)
        }
    }
}
