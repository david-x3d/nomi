package com.nomi.app.ui.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nomi.app.integration.voice.WhisperVoiceController
import com.nomi.app.ui.localization.nomiLocale
import com.nomi.app.ui.localization.nomiString
import kotlinx.coroutines.delay

/**
 * Dictation that happens where the user already is, instead of on a page of its own.
 *
 * The page that shows this owns no recognizer of its own: it reads [isActive] to know whether
 * to draw the waveform, [level] to draw it, and calls [start], [stop] and [cancel] from the
 * buttons next to it.
 */
class InlineDictationState internal constructor(
    /** True from the moment the microphone is asked for until the words are back or dropped. */
    val isActive: Boolean,
    /** True while the recording is being turned into words, when nothing is heard any more. */
    val isTranscribing: Boolean,
    /** Progress of the one-time model download, or null when nothing is downloading. */
    val downloadProgress: Float?,
    /** Microphone loudness from 0 to 1. */
    val level: Float,
    /** A refusal or a failure worth saying out loud, shown in place of the waveform. */
    val message: String?,
    val start: () -> Unit,
    val stop: () -> Unit,
    val cancel: () -> Unit,
)

/**
 * Wires a [WhisperVoiceController], the microphone permission, and the delivery of the finished
 * sentence into one piece of state a page can draw.
 */
@Composable
fun rememberInlineDictation(
    onTranscription: (String) -> Unit,
    controllerFactory: (Context) -> WhisperVoiceController = ::WhisperVoiceController,
): InlineDictationState {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val controller = remember(appContext) { controllerFactory(appContext) }
    val recognition by controller.state.collectAsStateWithLifecycle()
    val downloadProgress by controller.downloadProgress.collectAsStateWithLifecycle()
    val level by controller.level.collectAsStateWithLifecycle()
    val currentOnTranscription by rememberUpdatedState(onTranscription)
    val currentSpeechLocale by rememberUpdatedState(nomiLocale())

    // Set the moment the button is pressed, so the bar changes under the finger instead of
    // after the model has finished loading and the microphone has opened.
    var requested by remember { mutableStateOf(false) }
    var deniedMessage by remember { mutableStateOf<String?>(null) }
    val permissionDenied = nomiString("Nomi needs the microphone to hear you.")

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            controller.start(currentSpeechLocale)
        } else {
            requested = false
            deniedMessage = permissionDenied
        }
    }

    LaunchedEffect(recognition.finalText) {
        val text = recognition.finalText?.trim()?.takeIf(String::isNotBlank)
        if (text != null) {
            requested = false
            currentOnTranscription(text)
        }
        controller.acknowledge()
    }

    // Failures say what happened and then get out of the way; the bar is not a place to leave a
    // sentence sitting.
    val failure = recognition.errorMessage ?: deniedMessage
    LaunchedEffect(failure) {
        if (failure != null) {
            requested = false
            delay(MESSAGE_MILLIS)
            deniedMessage = null
            controller.acknowledge()
        }
    }

    DisposableEffect(controller) {
        onDispose { controller.close() }
    }

    fun start() {
        deniedMessage = null
        requested = true
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            controller.start(currentSpeechLocale)
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun cancel() {
        requested = false
        deniedMessage = null
        controller.cancel()
    }

    val active = failure != null || requested || recognition.isListening ||
        recognition.isTranscribing || downloadProgress != null
    return InlineDictationState(
        isActive = active,
        isTranscribing = recognition.isTranscribing,
        downloadProgress = downloadProgress,
        level = level,
        message = failure,
        start = { start() },
        stop = { controller.stop() },
        cancel = { cancel() },
    )
}

/** Long enough to read a short sentence, short enough not to block the next dictation. */
private const val MESSAGE_MILLIS = 4_000L
