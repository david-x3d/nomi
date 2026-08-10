package com.nomi.app.integration.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
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
            val buffer = ShortArray(bufferSize / BYTES_PER_SAMPLE)
            while (recording.get()) {
                val read = audioRecord.read(buffer, 0, buffer.size)
                if (read > 0) {
                    for (index in 0 until read) samples.add(buffer[index])
                }
                if (samples.size > SAMPLE_RATE * MAX_SECONDS) break
            }
            FloatArray(samples.size) { index ->
                (samples[index] / Short.MAX_VALUE.toFloat()).coerceIn(-1f, 1f)
            }
        } finally {
            recording.set(false)
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
    }
}
