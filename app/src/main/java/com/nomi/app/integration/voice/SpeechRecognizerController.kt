package com.nomi.app.integration.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VoiceRecognitionState(
    val isListening: Boolean = false,
    /** Set while the recording has been taken but the words are not back yet. */
    val isTranscribing: Boolean = false,
    val partialText: String = "",
    val finalText: String? = null,
    val errorMessage: String? = null,
)

class SpeechRecognizerController(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val mutableState = MutableStateFlow(VoiceRecognitionState())
    val state: StateFlow<VoiceRecognitionState> = mutableState.asStateFlow()

    private var recognizer: SpeechRecognizer? = null

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(appContext)

    fun start(locale: Locale = Locale.getDefault()) {
        if (!isAvailable()) {
            mutableState.value = VoiceRecognitionState(errorMessage = "Speech recognition isn't available on this device.")
            return
        }
        val speechRecognizer = recognizer ?: SpeechRecognizer.createSpeechRecognizer(appContext).also {
            it.setRecognitionListener(listener)
            recognizer = it
        }
        mutableState.value = VoiceRecognitionState(isListening = true)
        speechRecognizer.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Tell Nomi what you ate")
            },
        )
    }

    fun stop() {
        recognizer?.stopListening()
    }

    fun cancel() {
        recognizer?.cancel()
        mutableState.value = VoiceRecognitionState()
    }

    override fun close() {
        recognizer?.destroy()
        recognizer = null
        mutableState.value = VoiceRecognitionState()
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            mutableState.value = mutableState.value.copy(isListening = true, errorMessage = null)
        }

        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() {
            mutableState.value = mutableState.value.copy(isListening = false)
        }

        override fun onError(error: Int) {
            mutableState.value = mutableState.value.copy(
                isListening = false,
                errorMessage = error.toFriendlyMessage(),
            )
        }

        override fun onResults(results: Bundle?) {
            val text = results.bestMatch()
            mutableState.value = VoiceRecognitionState(finalText = text)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            mutableState.value = mutableState.value.copy(partialText = partialResults.bestMatch().orEmpty())
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }
}

private fun Bundle?.bestMatch(): String? = this
    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
    ?.firstOrNull()
    ?.trim()
    ?.takeIf(String::isNotBlank)

private fun Int.toFriendlyMessage(): String = when (this) {
    SpeechRecognizer.ERROR_AUDIO -> "Nomi couldn't access the microphone."
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is needed for voice logging."
    SpeechRecognizer.ERROR_NETWORK,
    SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
    -> "Speech recognition couldn't reach the network. You can type instead."

    SpeechRecognizer.ERROR_NO_MATCH -> "Nomi didn't catch that. Try again or type it."
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognition is busy. Try again in a moment."
    SpeechRecognizer.ERROR_SERVER,
    SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
    -> "Speech recognition is temporarily unavailable."

    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech was detected."
    else -> "Speech recognition stopped. Try again or type it."
}
