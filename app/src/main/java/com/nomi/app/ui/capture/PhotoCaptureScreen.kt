package com.nomi.app.ui.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.nomi.app.ui.localization.nomiString
import com.nomi.app.integration.camera.CameraCaptureController
import kotlinx.coroutines.launch

/** Captures a camera image or chooses one through the system picker, returning its URI and MIME type. */
@Composable
fun PhotoCaptureScreen(
    onBack: () -> Unit,
    onPhotoSelected: (uri: Uri, mimeType: String) -> Unit,
    onManualEntry: () -> Unit,
    modifier: Modifier = Modifier,
    controllerFactory: (Context) -> CameraCaptureController = ::CameraCaptureController,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val appContext = context.applicationContext
    val controller = remember(appContext) { controllerFactory(appContext) }
    val previewView = remember(context) {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val cameraAvailable = remember(context) {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }
    var cameraPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var cameraPermissionDenied by remember { mutableStateOf(false) }
    var cameraReady by remember { mutableStateOf(false) }
    var isBinding by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val currentOnPhotoSelected by rememberUpdatedState(onPhotoSelected)
    val cameraStartError = nomiString("The camera couldn't be started.", "Die Kamera konnte nicht gestartet werden.")
    val photoSaveError = nomiString("Nomi couldn't save that photo. Try again.", "Nomi konnte das Foto nicht speichern. Versuch es erneut.")
    val cameraPreviewDescription = nomiString("Camera preview", "Kameravorschau")

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        cameraPermissionGranted = granted
        cameraPermissionDenied = !granted
    }
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        uri?.let { selected ->
            val mimeType = context.contentResolver.getType(selected) ?: "image/*"
            currentOnPhotoSelected(selected, mimeType)
        }
    }

    LaunchedEffect(cameraAvailable, cameraPermissionGranted, lifecycleOwner, previewView) {
        if (!cameraAvailable || !cameraPermissionGranted) {
            cameraReady = false
            return@LaunchedEffect
        }
        isBinding = true
        cameraError = null
        runCatching {
            controller.bind(lifecycleOwner = lifecycleOwner, previewView = previewView)
        }.onSuccess {
            cameraReady = true
        }.onFailure { failure ->
            cameraReady = false
            cameraError = failure.message ?: cameraStartError
        }
        isBinding = false
    }

    DisposableEffect(controller) {
        onDispose { controller.unbind() }
    }

    fun requestCameraOrCapture() {
        cameraPermissionDenied = false
        cameraError = null
        if (!cameraPermissionGranted) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            return
        }
        if (!cameraReady || isCapturing) return
        scope.launch {
            isCapturing = true
            runCatching { controller.capture() }
                .onSuccess { uri -> currentOnPhotoSelected(uri, "image/jpeg") }
                .onFailure { failure ->
                    cameraError = failure.message ?: photoSaveError
                }
            isCapturing = false
        }
    }

    fun openPhotoPicker() {
        photoPicker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    CaptureScaffold(
        title = nomiString("Photograph your meal", "Fotografiere deine Mahlzeit"),
        onBack = onBack,
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = nomiString("Keep the whole meal in frame", "Halte die ganze Mahlzeit im Bild"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = nomiString("Good light and a clear view of portions help Nomi make a better estimate.", "Gutes Licht und klar erkennbare Portionen helfen Nomi bei einer besseren Schätzung."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black)
                    .testTag("photo_camera_preview"),
                contentAlignment = Alignment.Center,
            ) {
                if (cameraPermissionGranted && cameraAvailable) {
                    AndroidView(
                        factory = { previewView },
                        modifier = Modifier
                            .fillMaxSize()
                            .semantics { contentDescription = cameraPreviewDescription },
                    )
                }
                when {
                    !cameraAvailable -> CameraPlaceholder(
                        icon = Icons.Outlined.BrokenImage,
                        title = nomiString("No camera found", "Keine Kamera gefunden"),
                        message = nomiString("Choose an existing photo or enter the meal manually.", "Wähle ein vorhandenes Foto aus oder gib die Mahlzeit manuell ein."),
                    )
                    !cameraPermissionGranted -> CameraPlaceholder(
                        icon = Icons.Outlined.CameraAlt,
                        title = nomiString("Camera access is off", "Kamerazugriff ist deaktiviert"),
                        message = nomiString("Allow access only when you're ready to take a photo.", "Erlaube den Zugriff, wenn du ein Foto aufnehmen möchtest."),
                        action = {
                            FilledTonalButton(
                                onClick = {
                                    cameraPermissionDenied = false
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                },
                            ) {
                                Text(nomiString("Allow camera", "Kamera erlauben"))
                            }
                        },
                    )
                    isBinding -> CircularProgressIndicator(
                        modifier = Modifier.testTag("photo_camera_loading"),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    )
                }
            }

            if (cameraPermissionDenied) {
                CaptureMessageCard(
                    title = nomiString("Camera permission not granted", "Kamerazugriff nicht erlaubt"),
                    message = nomiString("You can try again, choose a photo, or enter the meal manually.", "Du kannst es erneut versuchen, ein Foto auswählen oder die Mahlzeit manuell eingeben."),
                    icon = Icons.Outlined.CameraAlt,
                    isError = true,
                )
            }
            cameraError?.let { message ->
                CaptureMessageCard(
                    title = nomiString("Camera unavailable", "Kamera nicht verfügbar"),
                    message = nomiString("$message You can still choose an existing photo.", "$message Du kannst weiterhin ein vorhandenes Foto auswählen."),
                    icon = Icons.Outlined.BrokenImage,
                    isError = true,
                )
            }

            Button(
                onClick = ::requestCameraOrCapture,
                enabled = cameraAvailable && !isCapturing && (!cameraPermissionGranted || cameraReady),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("photo_take_picture"),
            ) {
                Icon(Icons.Outlined.AddAPhoto, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(
                    when {
                        !cameraPermissionGranted -> nomiString("Allow camera", "Kamera erlauben")
                        isCapturing -> nomiString("Saving photo…", "Foto wird gespeichert…")
                        !cameraReady -> nomiString("Preparing camera…", "Kamera wird vorbereitet…")
                        else -> nomiString("Take photo", "Foto aufnehmen")
                    },
                )
            }
            OutlinedButton(
                onClick = ::openPhotoPicker,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("photo_system_picker"),
            ) {
                Icon(Icons.Outlined.Image, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(nomiString("Choose a photo", "Foto auswählen"))
            }
            TextButton(
                onClick = onManualEntry,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("photo_manual_entry"),
            ) {
                Icon(Icons.Outlined.Keyboard, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(nomiString("Enter manually", "Manuell eingeben"))
            }
        }
    }
}

@Composable
private fun CameraPlaceholder(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = Modifier.padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = title,
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            color = Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        action?.invoke()
    }
}
