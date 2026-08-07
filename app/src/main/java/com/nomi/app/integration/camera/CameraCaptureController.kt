package com.nomi.app.integration.camera

import android.content.Context
import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.time.Instant
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class CameraCaptureController(private val context: Context) {
    private var provider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null

    suspend fun bind(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
    ) {
        val cameraProvider = awaitProvider()
        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }
        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            capture,
        )
        provider = cameraProvider
        imageCapture = capture
    }

    suspend fun capture(): Uri = suspendCancellableCoroutine { continuation ->
        val capture = imageCapture
        if (capture == null) {
            continuation.resumeWithException(IllegalStateException("Camera is not ready"))
            return@suspendCancellableCoroutine
        }
        val directory = File(context.cacheDir, CAMERA_CAPTURE_DIRECTORY).apply { mkdirs() }
        val file = File(directory, "meal-${Instant.now().toEpochMilli()}.jpg")
        val output = ImageCapture.OutputFileOptions.Builder(file).build()
        capture.takePicture(
            output,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val uri = Uri.fromFile(file)
                    if (continuation.isActive) {
                        continuation.resume(uri)
                    } else {
                        deleteOwnedCameraCapture(context, uri)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    deleteOwnedCameraCapture(context, Uri.fromFile(file))
                    if (continuation.isActive) continuation.resumeWithException(exception)
                }
            },
        )
    }

    fun unbind() {
        provider?.unbindAll()
        provider = null
        imageCapture = null
    }

    private suspend fun awaitProvider(): ProcessCameraProvider = suspendCancellableCoroutine { continuation ->
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
}
