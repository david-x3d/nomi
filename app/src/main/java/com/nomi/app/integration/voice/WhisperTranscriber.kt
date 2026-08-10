package com.nomi.app.integration.voice

import android.content.Context
import com.whispercpp.whisper.WhisperContext
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Speech to text on the device, with no audio leaving the phone.
 *
 * That is the point of doing this locally rather than through an API: dictating a meal means
 * saying out loud what you eat, several times a day, and none of that needs to reach a server
 * to become a food entry.
 *
 * The loaded model is kept between dictations because loading it costs seconds, and released
 * when the app no longer needs it. whisper.cpp allows one call at a time, so the mutex here is
 * load-bearing rather than defensive.
 */
class WhisperTranscriber(
    context: Context,
    private val store: WhisperModelStore = WhisperModelStore(context),
) {
    private val lock = Mutex()
    private var loaded: WhisperContext? = null
    private var loadedFrom: File? = null

    val isModelAvailable: Boolean get() = store.isDownloaded()

    suspend fun ensureModel(onProgress: (Float?) -> Unit = {}): Result<File> =
        store.ensureDownloaded(onProgress)

    /**
     * Transcribes 16 kHz mono samples. Returns the recognized text with whisper's own timestamp
     * markers omitted, because what the food parser wants is the sentence.
     */
    suspend fun transcribe(samples: FloatArray, language: String? = null): Result<String> =
        withContext(Dispatchers.Default) {
            runCatching {
                require(samples.isNotEmpty()) { "Nothing was recorded" }
                val modelFile = store.modelFile
                check(modelFile.isFile) { "The speech model has not been downloaded yet" }
                lock.withLock {
                    val context = existingContext(modelFile)
                    context.transcribeData(samples, printTimestamp = false)
                }.trim()
            }
        }

    private suspend fun existingContext(modelFile: File): WhisperContext {
        loaded?.takeIf { loadedFrom == modelFile }?.let { return it }
        release()
        val context = WhisperContext.createContextFromFile(modelFile.absolutePath)
        loaded = context
        loadedFrom = modelFile
        return context
    }

    suspend fun release() {
        loaded?.release()
        loaded = null
        loadedFrom = null
    }
}
