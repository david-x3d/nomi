package com.nomi.app.integration.voice

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Keeps the speech model on the device without putting it in the APK.
 *
 * The model is by far the largest part of on-device speech, several times the size of the rest
 * of Nomi. Shipping it in the download would cost every user that size whether or not they ever
 * dictate a meal, so it is fetched once, on the first dictation, and then belongs to the device.
 *
 * A partial download is written to a temporary file and only moved into place once it is
 * complete, so an interrupted download can never leave a truncated model that fails to load
 * with a confusing error later.
 */
class WhisperModelStore(private val context: Context) {
    /**
     * Base is the smallest model that handles German food wording reliably; tiny mishears
     * exactly the brand names and quantities that matter here. Quantized to keep the download
     * near 57 MB rather than 142 MB.
     */
    private val modelFileName = "ggml-base-q5_1.bin"
    private val modelUrl =
        "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin"

    val modelFile: File get() = File(context.filesDir, modelFileName)

    fun isDownloaded(): Boolean = modelFile.isFile && modelFile.length() > 0

    /**
     * Downloads the model if it is missing. [onProgress] reports 0..1 when the server states a
     * length, and stays null when it does not, because a fake progress bar is worse than none.
     */
    suspend fun ensureDownloaded(onProgress: (Float?) -> Unit = {}): Result<File> =
        withContext(Dispatchers.IO) {
            if (isDownloaded()) return@withContext Result.success(modelFile)
            runCatching {
                val connection = (URL(modelUrl).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = CONNECT_TIMEOUT_MILLIS
                    readTimeout = READ_TIMEOUT_MILLIS
                }
                try {
                    check(connection.responseCode in 200..299) {
                        "Model download failed: HTTP ${connection.responseCode}"
                    }
                    val total = connection.contentLengthLong.takeIf { it > 0 }
                    val partial = File(context.filesDir, "$modelFileName.part")
                    partial.delete()
                    connection.inputStream.use { input ->
                        partial.outputStream().use { output ->
                            val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
                            var copied = 0L
                            while (true) {
                                coroutineContext.ensureActive()
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                copied += read
                                onProgress(total?.let { (copied.toFloat() / it).coerceIn(0f, 1f) })
                            }
                        }
                    }
                    check(partial.renameTo(modelFile)) { "Could not store the speech model" }
                } finally {
                    connection.disconnect()
                }
                modelFile
            }
        }

    fun delete(): Boolean = modelFile.delete()

    private companion object {
        const val DOWNLOAD_BUFFER_BYTES = 64 * 1024
        const val CONNECT_TIMEOUT_MILLIS = 30_000
        const val READ_TIMEOUT_MILLIS = 60_000
    }
}
