package com.nomi.app.ui.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nomi.app.ui.localization.nomiString
import com.nomi.app.integration.voice.SpeechRecognizerController

/**
 * Captures one free-form meal description and returns the final transcription.
 * The microphone permission is requested only after the user starts listening.
 */
@Composable
fun VoiceCaptureScreen(
    onBack: () -> Unit,
    onTranscription: (String) -> Unit,
    onManualEntry: () -> Unit,
    modifier: Modifier = Modifier,
    controllerFactory: (Context) -> SpeechRecognizerController = ::SpeechRecognizerController,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val controller = remember(appContext) { controllerFactory(appContext) }
    val recognitionState by controller.state.collectAsStateWithLifecycle()
    val recognitionAvailable = remember(controller) { controller.isAvailable() }
    var microphoneGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var permissionDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        microphoneGranted = granted
        permissionDenied = !granted
        if (granted) controller.start()
    }

    val currentOnTranscription by rememberUpdatedState(onTranscription)
    LaunchedEffect(recognitionState.finalText) {
        recognitionState.finalText
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.let(currentOnTranscription)
    }

    DisposableEffect(controller) {
        onDispose { controller.close() }
    }

    fun startListening() {
        permissionDenied = false
        if (!recognitionAvailable) return
        if (microphoneGranted) {
            controller.start()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    CaptureScaffold(
        title = nomiString("Describe your meal", "Beschreibe deine Mahlzeit"),
        onBack = onBack,
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = if (recognitionState.isListening) nomiString("I'm listening", "Ich höre zu") else nomiString("Tell Nomi what you ate", "Sag Nomi, was du gegessen hast"),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = nomiString("Speak naturally—foods, amounts, brands, and preparation details all help.", "Sprich ganz natürlich – Lebensmittel, Mengen, Marken und Zubereitungsdetails helfen Nomi."),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(36.dp))

            LargeFloatingActionButton(
                onClick = {
                    if (recognitionState.isListening) controller.stop() else startListening()
                },
                modifier = Modifier
                    .size(112.dp)
                    .testTag("voice_capture_action"),
                shape = CircleShape,
                containerColor = if (recognitionState.isListening) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
                contentColor = if (recognitionState.isListening) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                },
            ) {
                Icon(
                    imageVector = if (recognitionState.isListening) Icons.Outlined.Stop else Icons.Outlined.Mic,
                    contentDescription = if (recognitionState.isListening) nomiString("Stop listening", "Zuhören beenden") else nomiString("Start listening", "Zuhören starten"),
                    modifier = Modifier.size(48.dp),
                )
            }
            Spacer(Modifier.height(28.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("voice_partial_transcription"),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = recognitionState.partialText.takeIf(String::isNotBlank)
                            ?: if (recognitionState.isListening) nomiString("Listening…", "Höre zu…") else nomiString("Your words will appear here", "Deine Worte erscheinen hier"),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }

            val errorMessage = when {
                !recognitionAvailable -> nomiString("Speech recognition isn't available on this device.", "Die Spracherkennung ist auf diesem Gerät nicht verfügbar.")
                permissionDenied -> nomiString("Microphone access wasn't granted. You can try again or type your meal instead.", "Der Mikrofonzugriff wurde nicht erlaubt. Du kannst es erneut versuchen oder deine Mahlzeit stattdessen eingeben.")
                else -> recognitionState.errorMessage
            }
            errorMessage?.let { message ->
                Spacer(Modifier.height(16.dp))
                CaptureMessageCard(
                    title = nomiString("Voice capture unavailable", "Spracheingabe nicht verfügbar"),
                    message = message,
                    icon = Icons.Outlined.MicOff,
                    isError = true,
                )
            }

            Spacer(Modifier.weight(1f))
            if (recognitionState.isListening) {
                TextButton(
                    onClick = controller::cancel,
                    modifier = Modifier.testTag("voice_cancel"),
                ) {
                    Text(nomiString("Cancel listening", "Zuhören abbrechen"))
                }
            } else if (permissionDenied && recognitionAvailable) {
                FilledTonalButton(
                    onClick = ::startListening,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Mic, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(nomiString("Try microphone again", "Mikrofon erneut versuchen"))
                }
                Spacer(Modifier.height(10.dp))
            }
            OutlinedButton(
                onClick = onManualEntry,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("voice_manual_entry"),
            ) {
                Icon(Icons.Outlined.Keyboard, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(nomiString("Type instead", "Stattdessen eingeben"))
            }
        }
    }
}
