package com.nomi.app.ui.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.NoPhotography
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.lifecycle.LifecycleOwner
import com.nomi.app.ui.feedback.rememberNomiHaptics
import com.nomi.app.ui.localization.nomiString
import com.nomi.app.integration.barcode.BarcodeAnalyzer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** Scans the first supported retail barcode and returns its normalized raw value. */
@OptIn(ExperimentalGetImage::class)
@Composable
fun BarcodeCaptureScreen(
    onBack: () -> Unit,
    onBarcodeDetected: (String) -> Unit,
    onManualEntry: () -> Unit,
    modifier: Modifier = Modifier,
    inline: Boolean = false,
) {
    ViewfinderKeyboardDismissal()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
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
    var permissionDenied by remember { mutableStateOf(false) }
    var cameraReady by remember { mutableStateOf(false) }
    var isBinding by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var deliveredValue by remember { mutableStateOf<String?>(null) }
    var bindRequest by remember { mutableIntStateOf(0) }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var analysisUseCase by remember { mutableStateOf<ImageAnalysis?>(null) }
    val currentOnBarcodeDetected = rememberUpdatedState(onBarcodeDetected)
    // The code is read while the phone is still pointed at the package, so the confirmation
    // has to be felt rather than seen.
    val haptics = rememberNomiHaptics()
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val noUsableCameraError = nomiString("No usable camera was found.")
    val barcodeCameraStartError = nomiString("The barcode camera couldn't be started.")
    val cameraPreviewDescription = nomiString("Live barcode camera preview")
    val scanAreaDescription = nomiString("Barcode scan area")
    val analysisExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    val analyzer = remember {
        BarcodeAnalyzer(onBarcodeDetected = { rawValue ->
            mainExecutor.execute {
                val value = rawValue.trim()
                if (value.isNotBlank() && deliveredValue == null) {
                    deliveredValue = value
                    haptics.confirmed()
                    currentOnBarcodeDetected.value(value)
                }
            }
        })
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        cameraPermissionGranted = granted
        permissionDenied = !granted
    }

    LaunchedEffect(
        cameraAvailable,
        cameraPermissionGranted,
        lifecycleOwner,
        previewView,
        bindRequest,
    ) {
        if (!cameraAvailable || !cameraPermissionGranted) {
            cameraReady = false
            return@LaunchedEffect
        }
        isBinding = true
        cameraError = null
        runCatching {
            val cameraProvider = awaitCameraProvider(context)
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(analysisExecutor, analyzer) }
            val selector = when {
                cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ->
                    CameraSelector.DEFAULT_BACK_CAMERA
                cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ->
                    CameraSelector.DEFAULT_FRONT_CAMERA
                else -> error(noUsableCameraError)
            }

            analysisUseCase?.clearAnalyzer()
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
            provider = cameraProvider
            analysisUseCase = analysis
        }.onSuccess {
            cameraReady = true
        }.onFailure { failure ->
            cameraReady = false
            cameraError = failure.message ?: barcodeCameraStartError
        }
        isBinding = false
    }

    DisposableEffect(analyzer, analysisExecutor) {
        onDispose {
            analysisUseCase?.clearAnalyzer()
            provider?.unbindAll()
            analyzer.close()
            analysisExecutor.shutdown()
        }
    }

    if (inline) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = nomiString("Line up the product barcode"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 360.dp, max = 520.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.Black)
                    .testTag("inline_barcode_camera"),
                contentAlignment = Alignment.Center,
            ) {
                BarcodePreviewContent(
                    previewView = previewView,
                    cameraAvailable = cameraAvailable,
                    cameraPermissionGranted = cameraPermissionGranted,
                    cameraReady = cameraReady,
                    isBinding = isBinding,
                    deliveredValue = deliveredValue,
                    previewDescription = cameraPreviewDescription,
                    scanAreaDescription = scanAreaDescription,
                    onRequestPermission = {
                        permissionDenied = false
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                )
                FilledTonalIconButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.BottomStart).padding(20.dp),
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = nomiString("Cancel"))
                }
            }
            if (permissionDenied) {
                CaptureMessageCard(
                    title = nomiString("Camera permission not granted"),
                    message = nomiString("Try again when you're ready, or use manual entry."),
                    icon = Icons.Outlined.NoPhotography,
                    isError = true,
                )
            }
            cameraError?.let { message ->
                CaptureMessageCard(
                    title = nomiString("Scanner unavailable"),
                    message = message,
                    icon = Icons.Outlined.NoPhotography,
                    isError = true,
                )
                Button(
                    onClick = { bindRequest += 1 },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(nomiString("Try camera again"))
                }
            }
            OutlinedButton(onClick = onManualEntry, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Keyboard, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(nomiString("Enter manually"))
            }
        }
        return
    }

    CaptureScaffold(
        title = nomiString("Scan barcode"),
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
                text = nomiString("Line up the product barcode"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = nomiString("Nomi scans common EAN and UPC barcodes automatically. Nothing is captured until a code is visible."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black)
                    .testTag("barcode_camera_preview"),
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

                if (cameraReady && deliveredValue == null) {
                    Box(
                        modifier = Modifier
                            .size(width = 280.dp, height = 170.dp)
                            .border(
                                width = 3.dp,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(22.dp),
                            )
                            .semantics { contentDescription = scanAreaDescription }
                            .testTag("barcode_scan_area"),
                    )
                }

                when {
                    !cameraAvailable -> ScannerPlaceholder(
                        icon = Icons.Outlined.NoPhotography,
                        title = nomiString("No camera found"),
                        message = nomiString("Enter the barcode or food manually instead."),
                    )
                    !cameraPermissionGranted -> ScannerPlaceholder(
                        icon = Icons.Outlined.QrCodeScanner,
                        title = nomiString("Camera access is off"),
                        message = nomiString("Nomi asks only when you choose to scan."),
                        actionLabel = nomiString("Allow camera"),
                        onAction = {
                            permissionDenied = false
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                    )
                    isBinding -> CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.testTag("barcode_camera_loading"),
                    )
                    cameraReady && deliveredValue == null -> Column(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Outlined.CenterFocusStrong,
                            contentDescription = null,
                            tint = Color.White,
                        )
                        Text(
                            nomiString("Scanning…"),
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }

            if (permissionDenied) {
                CaptureMessageCard(
                    title = nomiString("Camera permission not granted"),
                    message = nomiString("Try again when you're ready, or use manual entry."),
                    icon = Icons.Outlined.NoPhotography,
                    isError = true,
                )
            }
            cameraError?.let { message ->
                CaptureMessageCard(
                    title = nomiString("Scanner unavailable"),
                    message = message,
                    icon = Icons.Outlined.NoPhotography,
                    isError = true,
                )
                Button(
                    onClick = { bindRequest += 1 },
                    modifier = Modifier.fillMaxWidth().testTag("barcode_retry_camera"),
                ) {
                    Text(nomiString("Try camera again"))
                }
            }
            deliveredValue?.let { value ->
                CaptureMessageCard(
                    title = nomiString("Barcode found"),
                    message = value,
                    icon = Icons.Outlined.QrCodeScanner,
                )
            }
            OutlinedButton(
                onClick = onManualEntry,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("barcode_manual_entry"),
            ) {
                Icon(Icons.Outlined.Keyboard, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(nomiString("Enter manually"))
            }
        }
    }
}

@Composable
private fun BarcodePreviewContent(
    previewView: PreviewView,
    cameraAvailable: Boolean,
    cameraPermissionGranted: Boolean,
    cameraReady: Boolean,
    isBinding: Boolean,
    deliveredValue: String?,
    previewDescription: String,
    scanAreaDescription: String,
    onRequestPermission: () -> Unit,
) {
    if (cameraPermissionGranted && cameraAvailable) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = previewDescription },
        )
    }
    if (cameraReady && deliveredValue == null) {
        Box(
            modifier = Modifier
                .size(width = 280.dp, height = 170.dp)
                .border(
                    width = 3.dp,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(22.dp),
                )
                .semantics { contentDescription = scanAreaDescription },
        )
    }
    when {
        !cameraAvailable -> ScannerPlaceholder(
            icon = Icons.Outlined.NoPhotography,
            title = nomiString("No camera found"),
            message = nomiString("Enter the barcode or food manually instead."),
        )
        !cameraPermissionGranted -> ScannerPlaceholder(
            icon = Icons.Outlined.QrCodeScanner,
            title = nomiString("Camera access is off"),
            message = nomiString("Nomi asks only when you choose to scan."),
            actionLabel = nomiString("Allow camera"),
            onAction = onRequestPermission,
        )
        isBinding -> CircularProgressIndicator(color = Color.White)
        cameraReady && deliveredValue == null -> Column(
            modifier = Modifier.fillMaxSize().padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
        ) {
            Icon(Icons.Outlined.CenterFocusStrong, contentDescription = null, tint = Color.White)
            Text(nomiString("Scanning…"), color = Color.White)
        }
    }
}

@Composable
private fun ScannerPlaceholder(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
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
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

private suspend fun awaitCameraProvider(context: Context): ProcessCameraProvider =
    suspendCancellableCoroutine { continuation ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            {
                runCatching(future::get)
                    .onSuccess { if (continuation.isActive) continuation.resume(it) }
                    .onFailure { if (continuation.isActive) continuation.resumeWithException(it) }
            },
            ContextCompat.getMainExecutor(context),
        )
    }
