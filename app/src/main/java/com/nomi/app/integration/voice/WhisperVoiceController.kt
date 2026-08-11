package com.nomi.app.integration.voice

import android.content.Context
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Dictation that runs entirely on the phone, presented through the same small surface as the
 * Android recognizer so the capture screen does not need to know which one it is talking to.
 *
 * The difference the user notices is that nothing is transcribed until they stop speaking:
 * whisper.cpp works on a finished recording rather than a live stream, so there is no partial
 * text on the way. In exchange the audio never leaves the device, and recognition does not
 * depend on Google's recognizer being installed or on there being a network at all - only the
 * one-time model download does.
 */
class WhisperVoiceController(
    context: Context,
    private val transcriber: WhisperTranscriber = WhisperTranscriber(context),
    private val recorder: WhisperRecorder = WhisperRecorder(),
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(VoiceRecognitionState())
    val state: StateFlow<VoiceRecognitionState> = mutableState.asStateFlow()

    /** Progress of the one-time model download, or null when nothing is downloading. */
    private val mutableDownloadProgress = MutableStateFlow<Float?>(null)
    val downloadProgress: StateFlow<Float?> = mutableDownloadProgress.asStateFlow()

    /** Current microphone loudness from 0 to 1, for showing that speech is being heard. */
    val level: StateFlow<Float> = recorder.level

    private var job: Job? = null

    /** Whisper needs no system recognizer, so it is available wherever the app runs. */
    fun isAvailable(): Boolean = true

    val isModelReady: Boolean get() = transcriber.isModelAvailable

    fun start(locale: Locale = Locale.getDefault()) {
        if (job?.isActive == true) return
        job = scope.launch {
            if (!transcriber.isModelAvailable) {
                mutableState.value = VoiceRecognitionState(
                    isListening = false,
                    partialText = "",
                )
                val downloaded = transcriber.ensureModel { progress ->
                    mutableDownloadProgress.value = progress ?: 0f
                }
                mutableDownloadProgress.value = null
                downloaded.onFailure { error ->
                    if (error is CancellationException) throw error
                    mutableState.value = VoiceRecognitionState(
                        errorMessage = error.message ?: "The speech model could not be downloaded.",
                    )
                    return@launch
                }
            }

            mutableState.value = VoiceRecognitionState(isListening = true)
            val samples = runCatching { recorder.record() }
                .getOrElse { error ->
                    // Being cancelled is not a failure worth reporting: "forget it" has to
                    // leave the bar the way it found it instead of explaining itself.
                    if (error is CancellationException) throw error
                    mutableState.value = VoiceRecognitionState(
                        errorMessage = error.message ?: "The microphone could not be opened.",
                    )
                    return@launch
                }

            // Recording has stopped; the model now works on what was captured.
            mutableState.value = VoiceRecognitionState(isListening = false, isTranscribing = true)
            transcriber.transcribe(samples, locale.language)
                .onSuccess { text ->
                    mutableState.value = if (text.isBlank()) {
                        VoiceRecognitionState(errorMessage = "Nothing was recognized. Try again.")
                    } else {
                        VoiceRecognitionState(finalText = text)
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    mutableState.value = VoiceRecognitionState(
                        errorMessage = error.message ?: "The recording could not be transcribed.",
                    )
                }
        }
    }

    fun stop() {
        recorder.stop()
    }

    /**
     * Clears a delivered result or a shown error, so the next dictation starts from silence.
     *
     * Without this the state would still hold the last sentence, and dictating the very same
     * words twice would look like nothing happened the second time.
     */
    fun acknowledge() {
        if (state.value.finalText != null || state.value.errorMessage != null) {
            mutableState.value = VoiceRecognitionState()
        }
    }

    /**
     * Throws the recording away instead of transcribing it. Stopping means "I'm done talking";
     * cancelling means "forget it", and the two must not produce the same entry.
     */
    fun cancel() {
        recorder.stop()
        job?.cancel()
        job = null
        mutableDownloadProgress.value = null
        mutableState.value = VoiceRecognitionState()
    }

    override fun close() {
        recorder.stop()
        scope.launch { transcriber.release() }
        job = null
        scope.cancel()
    }
}
