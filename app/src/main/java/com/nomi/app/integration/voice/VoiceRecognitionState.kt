package com.nomi.app.integration.voice

/** What a dictation is doing right now, as the capture bar draws it. */
data class VoiceRecognitionState(
    val isListening: Boolean = false,
    /** Set while the recording has been taken but the words are not back yet. */
    val isTranscribing: Boolean = false,
    val partialText: String = "",
    val finalText: String? = null,
    val errorMessage: String? = null,
)
