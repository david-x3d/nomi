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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
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
import com.nomi.app.integration.camera.CameraCaptureController
import com.nomi.app.ui.localization.nomiFormat
import com.nomi.app.ui.localization.nomiString
import kotlinx.coroutines.launch

/**
 * What the camera is pointed at. The two subjects need opposite advice: a meal wants the whole
 * plate and forgiving light, a nutrition table wants to fill the frame and be sharp, because
 * nothing downstream will second-guess a misread digit.
 */
enum class PhotoCaptureSubject { MEAL, MENU, NUTRITION_LABEL }

/** Captures a camera image or chooses one through the system picker, returning its URI and MIME type. */
@Composable
fun PhotoCaptureScreen(
    onBack: () -> Unit,
    onPhotoSelected: (uri: Uri, mimeType: String) -> Unit,
    onManualEntry: () -> Unit,
    modifier: Modifier = Modifier,
    subject: PhotoCaptureSubject = PhotoCaptureSubject.MEAL,
    inline: Boolean = false,
    controllerFactory: (Context) -> CameraCaptureController = ::CameraCaptureController,
) {
    ViewfinderKeyboardDismissal()
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
    val cameraStartError = nomiString("The camera couldn't be started.")
    val photoSaveError = nomiString("Nomi couldn't save that photo. Try again.")
    val cameraPreviewDescription = nomiString("Camera preview")

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

    val screenTitle = when (subject) {
        PhotoCaptureSubject.MEAL ->
            nomiString("Photograph your meal")
        PhotoCaptureSubject.MENU ->
            nomiString("Photograph the menu")
        PhotoCaptureSubject.NUTRITION_LABEL ->
            nomiString("Photograph the label")
    }
    val guidanceHeadline = when (subject) {
        PhotoCaptureSubject.MEAL ->
            nomiString("Keep the whole meal in frame")
        PhotoCaptureSubject.MENU ->
            nomiString("Keep one complete page in frame")
        PhotoCaptureSubject.NUTRITION_LABEL ->
            nomiString("Fill the frame with the table")
    }
    val guidanceDetail = when (subject) {
        PhotoCaptureSubject.MEAL -> nomiString("Good light and a clear view of portions help Nomi make a better estimate.")
        PhotoCaptureSubject.MENU -> nomiString("Straight on, sharp, and close enough to read every name, description, number and price.")
        // Nothing is researched or estimated here, so the only thing that decides whether the
        // numbers are right is whether they can be read.
        PhotoCaptureSubject.NUTRITION_LABEL -> nomiString("Straight on and in focus. Nomi reads the printed values and researches nothing.")
    }

    if (inline) {
        InlineCameraSurface(
            previewView = previewView,
            cameraAvailable = cameraAvailable,
            cameraPermissionGranted = cameraPermissionGranted,
            cameraReady = cameraReady,
            isBinding = isBinding,
            isCapturing = isCapturing,
            cameraPermissionDenied = cameraPermissionDenied,
            cameraError = cameraError,
            previewDescription = cameraPreviewDescription,
            subject = subject,
            onClose = onBack,
            onCapture = ::requestCameraOrCapture,
            onChoosePhoto = ::openPhotoPicker,
            onRequestPermission = {
                cameraPermissionDenied = false
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            },
            modifier = modifier,
        )
        return
    }

    CaptureScaffold(
        title = screenTitle,
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
                text = guidanceHeadline,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = guidanceDetail,
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
                        title = nomiString("No camera found"),
                        message = nomiString("Choose an existing photo or enter the meal manually."),
                    )
                    !cameraPermissionGranted -> CameraPlaceholder(
                        icon = Icons.Outlined.CameraAlt,
                        title = nomiString("Camera access is off"),
                        message = nomiString("Allow access only when you're ready to take a photo."),
                        action = {
                            FilledTonalButton(
                                onClick = {
                                    cameraPermissionDenied = false
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                },
                            ) {
                                Text(nomiString("Allow camera"))
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
                    title = nomiString("Camera permission not granted"),
                    message = nomiString("You can try again, choose a photo, or enter the meal manually."),
                    icon = Icons.Outlined.CameraAlt,
                    isError = true,
                )
            }
            cameraError?.let { message ->
                CaptureMessageCard(
                    title = nomiString("Camera unavailable"),
                    message = nomiFormat("{0} You can still choose an existing photo.", message),
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
                        !cameraPermissionGranted -> nomiString("Allow camera")
                        isCapturing -> nomiString("Saving photo…")
                        !cameraReady -> nomiString("Preparing camera…")
                        else -> nomiString("Take photo")
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
                Text(nomiString("Choose a photo"))
            }
            TextButton(
                onClick = onManualEntry,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("photo_manual_entry"),
            ) {
                Icon(Icons.Outlined.Keyboard, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(nomiString("Enter manually"))
            }
        }
    }
}

/**
 * The camera lives inside Today rather than taking the user to a temporary destination. Controls
 * float over the preview so opening it only adds one compact surface to the current page.
 */
@Composable
private fun InlineCameraSurface(
    previewView: PreviewView,
    cameraAvailable: Boolean,
    cameraPermissionGranted: Boolean,
    cameraReady: Boolean,
    isBinding: Boolean,
    isCapturing: Boolean,
    cameraPermissionDenied: Boolean,
    cameraError: String?,
    previewDescription: String,
    subject: PhotoCaptureSubject,
    onClose: () -> Unit,
    onCapture: () -> Unit,
    onChoosePhoto: () -> Unit,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val subjectLabel = when (subject) {
        PhotoCaptureSubject.MEAL -> nomiString("Photo")
        PhotoCaptureSubject.MENU -> nomiString("Scan menu")
        PhotoCaptureSubject.NUTRITION_LABEL -> nomiString("Nutrition label")
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 360.dp, max = 520.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color.Black)
            .testTag("inline_photo_camera"),
        contentAlignment = Alignment.Center,
    ) {
        if (cameraPermissionGranted && cameraAvailable) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = previewDescription },
            )
        }

        when {
            !cameraAvailable -> CameraPlaceholder(
                icon = Icons.Outlined.BrokenImage,
                title = nomiString("No camera found"),
                message = nomiString("Choose an existing photo or enter the meal manually."),
            )
            !cameraPermissionGranted -> CameraPlaceholder(
                icon = Icons.Outlined.CameraAlt,
                title = nomiString("Camera access is off"),
                message = if (cameraPermissionDenied) {
                    nomiString("You can try again, choose a photo, or enter the meal manually.")
                } else {
                    nomiString("Allow access only when you're ready to take a photo.")
                },
                action = {
                    FilledTonalButton(onClick = onRequestPermission) {
                        Text(nomiString("Allow camera"))
                    }
                },
            )
            isBinding -> CircularProgressIndicator(color = Color.White)
            cameraError != null -> CameraPlaceholder(
                icon = Icons.Outlined.BrokenImage,
                title = nomiString("Camera unavailable"),
                message = cameraError,
            )
        }

        FilledTonalButton(
            onClick = {},
            enabled = false,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 14.dp),
        ) {
            Text(subjectLabel)
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(onClick = onClose) {
                Icon(Icons.Outlined.Close, contentDescription = nomiString("Cancel"))
            }
            Button(
                onClick = onCapture,
                enabled = cameraAvailable && !isCapturing && (!cameraPermissionGranted || cameraReady),
                modifier = Modifier.size(72.dp),
                shape = RoundedCornerShape(36.dp),
            ) {
                if (isCapturing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(Icons.Outlined.AddAPhoto, contentDescription = nomiString("Take photo"))
                }
            }
            FilledTonalIconButton(onClick = onChoosePhoto) {
                Icon(Icons.Outlined.Image, contentDescription = nomiString("Choose a photo"))
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
