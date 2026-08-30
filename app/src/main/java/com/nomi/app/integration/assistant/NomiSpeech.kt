package com.nomi.app.integration.assistant

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class NomiSpeech(context: Context) {
    private val appContext = context.applicationContext
    private var ready = false
    private var pending: Pair<String, Locale>? = null
    private var tts: TextToSpeech? = TextToSpeech(appContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        val queued = pending
        pending = null
        if (ready && queued != null) speakNow(queued.first, queued.second)
    }

    fun speak(text: String, locale: Locale) {
        if (text.isBlank()) return
        if (!ready || tts == null) {
            pending = text to locale
            return
        }
        speakNow(text, locale)
    }

    private fun speakNow(text: String, locale: Locale) {
        val engine = tts ?: return
        engine.language = locale
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nomi-calories")
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
        pending = null
    }
}
