package com.nomi.app.integration.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.log10
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Captures microphone audio in exactly the form Whisper expects: 16 kHz, mono, and normalized
 * to floats in -1..1.
 *
 * The conversion happens here rather than in the transcriber because it is a property of the
 * recording, not of the model: any other source of 16 kHz mono audio can be handed to the
 * transcriber unchanged.
 */
class WhisperRecorder {
    private val recording = AtomicBoolean(false)

    @Volatile
    private var record: AudioRecord? = null

    private val mutableLevel = MutableStateFlow(0f)

    /**
     * How loud the microphone is right now, from 0 to 1, so the dictation bar can show that
     * something is being heard. It is published while recording and falls back to 0 once the
     * microphone closes.
     */
    val level: StateFlow<Float> = mutableLevel.asStateFlow()

    val isRecording: Boolean get() = recording.get()

    /**
     * Records until [stop] is called and returns the samples.
     *
     * Requires RECORD_AUDIO, which the caller must already have been granted; the annotation is
     * suppressed because the permission is checked at the UI layer where it can be requested.
     */
    @SuppressLint("MissingPermission")
    suspend fun record(): FloatArray = withContext(Dispatchers.IO) {
        check(recording.compareAndSet(false, true)) { "Already recording" }
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val bufferSize = maxOf(minBuffer, SAMPLE_RATE * BYTES_PER_SAMPLE)
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize,
        )
        record = audioRecord
        try {
            check(audioRecord.state == AudioRecord.STATE_INITIALIZED) {
                "The microphone could not be opened"
            }
            audioRecord.startRecording()
            val samples = ArrayList<Short>(SAMPLE_RATE * EXPECTED_SECONDS)
            // Read in short chunks rather than in whole buffers: a read only returns once it is
            // full, and a waveform that moved once per buffer would look like it had stopped
            // listening between words.
            val buffer = ShortArray(minOf(bufferSize / BYTES_PER_SAMPLE, SAMPLE_RATE / CHUNKS_PER_SECOND))
            while (recording.get()) {
                val read = audioRecord.read(buffer, 0, buffer.size)
                if (read > 0) {
                    for (index in 0 until read) samples.add(buffer[index])
                    mutableLevel.value = microphoneLevel(rootMeanSquare(buffer, read))
                }
                if (samples.size > SAMPLE_RATE * MAX_SECONDS) break
            }
            FloatArray(samples.size) { index ->
                (samples[index] / Short.MAX_VALUE.toFloat()).coerceIn(-1f, 1f)
            }
        } finally {
            recording.set(false)
            mutableLevel.value = 0f
            runCatching { audioRecord.stop() }
            audioRecord.release()
            record = null
        }
    }

    fun stop() {
        recording.set(false)
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val BYTES_PER_SAMPLE = 2
        const val EXPECTED_SECONDS = 8
        /** A meal description is a sentence, not a monologue; this bounds memory and latency. */
        const val MAX_SECONDS = 60
        /** How often the level is refreshed; 40 ms is faster than the eye follows a bar. */
        const val CHUNKS_PER_SECOND = 25
    }
}

/** Loudness of one block of 16-bit samples, in the 0..1 scale the sample floats use. */
internal fun rootMeanSquare(buffer: ShortArray, count: Int): Float {
    if (count <= 0) return 0f
    var sum = 0.0
    for (index in 0 until count) {
        val sample = buffer[index] / Short.MAX_VALUE.toDouble()
        sum += sample * sample
    }
    return sqrt(sum / count).toFloat()
}

/**
 * Turns a raw amplitude into the 0..1 height the waveform draws.
 *
 * Speech sits very low on a linear amplitude scale - a normal voice rarely passes a tenth of
 * full scale - so drawn linearly the bars would barely leave the floor. Mapping decibels
 * instead spreads a spoken sentence across the whole bar, which is what makes the waveform
 * look like it is following the voice rather than twitching.
 */
internal fun microphoneLevel(amplitude: Float): Float {
    if (amplitude <= 0f) return 0f
    val decibels = 20f * log10(amplitude)
    return ((decibels + QUIET_DECIBELS) / QUIET_DECIBELS).coerceIn(0f, 1f)
}

/** Everything this far below full scale counts as silence. */
private const val QUIET_DECIBELS = 50f
