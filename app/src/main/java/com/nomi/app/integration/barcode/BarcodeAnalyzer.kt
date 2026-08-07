package com.nomi.app.integration.barcode

import android.os.SystemClock
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicBoolean

@androidx.annotation.OptIn(ExperimentalGetImage::class)
class BarcodeAnalyzer(
    private val onBarcodeDetected: (String) -> Unit,
    private val debounceMillis: Long = 2_000,
    options: BarcodeScannerOptions = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(
            Barcode.FORMAT_EAN_8,
            Barcode.FORMAT_EAN_13,
            Barcode.FORMAT_UPC_A,
            Barcode.FORMAT_UPC_E,
        )
        .build(),
) : ImageAnalysis.Analyzer, AutoCloseable {

    private val scanner: BarcodeScanner = BarcodeScanning.getClient(options)
    private val processing = AtomicBoolean(false)
    private var lastValue: String? = null
    private var lastDeliveredAt = 0L

    override fun analyze(imageProxy: ImageProxy) {
        if (!processing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            processing.set(false)
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val value = barcodes.firstNotNullOfOrNull { it.rawValue?.takeIf(String::isNotBlank) }
                if (value != null && shouldDeliver(value)) onBarcodeDetected(value)
            }
            .addOnCompleteListener {
                processing.set(false)
                imageProxy.close()
            }
    }

    private fun shouldDeliver(value: String): Boolean {
        val now = SystemClock.elapsedRealtime()
        val duplicate = value == lastValue && now - lastDeliveredAt < debounceMillis
        if (!duplicate) {
            lastValue = value
            lastDeliveredAt = now
        }
        return !duplicate
    }

    override fun close() {
        scanner.close()
    }
}
