package com.nomi.app.integration.camera

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class PreparedMealImage(
    val bytes: ByteArray,
    val mediaType: String = "image/jpeg",
)

/**
 * Converts an untrusted picker/camera image into a bounded, metadata-free upload.
 *
 * The original compressed bytes are capped and wiped, dimensions are checked before decoding,
 * decoded pixels are sampled and bounded, EXIF orientation is applied, and a fresh JPEG is
 * produced. Re-encoding deliberately strips EXIF location, device, and timestamp metadata.
 */
object MealImagePreprocessor {
    const val MAX_SOURCE_BYTES: Int = 12 * 1024 * 1024
    const val MAX_SOURCE_DIMENSION: Int = 16_384
    const val MAX_SOURCE_PIXELS: Long = 80_000_000L
    const val MAX_OUTPUT_DIMENSION: Int = 2_048
    const val MAX_OUTPUT_PIXELS: Long = 4_000_000L
    const val MAX_OUTPUT_BYTES: Int = 6 * 1024 * 1024

    fun prepare(input: InputStream): PreparedMealImage {
        val source = input.readCapped(MAX_SOURCE_BYTES)
        var working: Bitmap? = null
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(source, 0, source.size, bounds)
            val width = bounds.outWidth
            val height = bounds.outHeight
            require(width > 0 && height > 0) { "Choose a supported image file." }
            require(width <= MAX_SOURCE_DIMENSION && height <= MAX_SOURCE_DIMENSION) {
                "That image has unsupported dimensions. Choose a smaller photo."
            }
            require(width.toLong() * height.toLong() <= MAX_SOURCE_PIXELS) {
                "That image contains too many pixels. Choose a smaller photo."
            }

            val sampleSize = calculateSampleSize(width, height)
            working = BitmapFactory.decodeByteArray(
                source,
                0,
                source.size,
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inScaled = false
                },
            ) ?: error("Nomi couldn't decode that image.")

            requireBoundedDecode(requireNotNull(working))
            working = requireNotNull(working).replaceWith(
                applyExifOrientation(requireNotNull(working), readOrientation(source)),
            )
            working = requireNotNull(working).replaceWith(
                scaleToOutputBounds(requireNotNull(working)),
            )
            working = requireNotNull(working).replaceWith(
                flattenTransparency(requireNotNull(working)),
            )

            val encoded = CappedByteArrayOutputStream(MAX_OUTPUT_BYTES)
            return try {
                check(requireNotNull(working).compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, encoded)) {
                    "Nomi couldn't prepare that image."
                }
                PreparedMealImage(encoded.toByteArray())
            } finally {
                encoded.wipe()
            }
        } finally {
            working?.recycle()
            source.fill(0)
        }
    }

    private fun calculateSampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (true) {
            val sampledWidth = ceilDivide(width, sample)
            val sampledHeight = ceilDivide(height, sample)
            val withinBounds = sampledWidth <= MAX_OUTPUT_DIMENSION &&
                sampledHeight <= MAX_OUTPUT_DIMENSION &&
                sampledWidth.toLong() * sampledHeight.toLong() <= MAX_OUTPUT_PIXELS
            if (withinBounds) return sample
            check(sample <= MAX_SOURCE_DIMENSION / 2) { "Image sampling could not be bounded." }
            sample *= 2
        }
    }

    private fun requireBoundedDecode(bitmap: Bitmap) {
        require(bitmap.width > 0 && bitmap.height > 0) { "The decoded image is empty." }
        require(bitmap.width <= MAX_OUTPUT_DIMENSION * 2 && bitmap.height <= MAX_OUTPUT_DIMENSION * 2) {
            "The decoded image is too large."
        }
        require(bitmap.width.toLong() * bitmap.height.toLong() <= MAX_OUTPUT_PIXELS * 4) {
            "The decoded image contains too many pixels."
        }
    }

    private fun readOrientation(source: ByteArray): Int = runCatching {
        ExifInterface(ByteArrayInputStream(source)).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    private fun applyExifOrientation(source: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return source
        }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun scaleToOutputBounds(source: Bitmap): Bitmap {
        val pixelScale = sqrt(MAX_OUTPUT_PIXELS.toDouble() / (source.width.toLong() * source.height))
        val scale = minOf(
            1.0,
            MAX_OUTPUT_DIMENSION.toDouble() / source.width,
            MAX_OUTPUT_DIMENSION.toDouble() / source.height,
            pixelScale,
        )
        if (scale >= 1.0) return source
        val width = (source.width * scale).roundToInt().coerceAtLeast(1)
        val height = (source.height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, width, height, true)
    }

    private fun flattenTransparency(source: Bitmap): Bitmap {
        if (!source.hasAlpha()) return source
        return Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888).also { target ->
            Canvas(target).apply {
                drawColor(Color.WHITE)
                drawBitmap(source, 0f, 0f, null)
            }
        }
    }

    private fun Bitmap.replaceWith(replacement: Bitmap): Bitmap {
        if (replacement !== this) recycle()
        return replacement
    }

    private fun ceilDivide(value: Int, divisor: Int): Int =
        ((value.toLong() + divisor - 1L) / divisor).toInt()

    private const val JPEG_QUALITY = 88
}

/** Deletes only CameraCaptureController's exact app-private file shape; picker URIs are untouched. */
fun deleteOwnedCameraCapture(context: Context, uri: Uri): Boolean {
    if (uri.scheme != ContentResolver.SCHEME_FILE) return false
    val rawPath = uri.path ?: return false
    return runCatching {
        val unresolved = File(rawPath)
        if (Files.isSymbolicLink(unresolved.toPath())) return@runCatching false
        val captureDirectory = File(context.applicationContext.cacheDir, CAMERA_CAPTURE_DIRECTORY)
            .canonicalFile
        val target = unresolved.canonicalFile
        val validName = CAMERA_CAPTURE_FILE.matches(target.name)
        if (!validName || target.parentFile != captureDirectory || !target.isFile) {
            false
        } else {
            Files.deleteIfExists(target.toPath())
        }
    }.getOrDefault(false)
}

internal const val CAMERA_CAPTURE_DIRECTORY = "selected-meal-images"
private val CAMERA_CAPTURE_FILE = Regex("meal-[0-9]+\\.jpg")
private const val MAX_CONSECUTIVE_EMPTY_READS = 16

private fun InputStream.readCapped(maxBytes: Int): ByteArray {
    val output = WipingByteArrayOutputStream(minOf(DEFAULT_BUFFER_SIZE, maxBytes))
    val chunk = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    var consecutiveEmptyReads = 0
    return try {
        while (true) {
            val read = read(chunk)
            if (read < 0) break
            if (read == 0) {
                consecutiveEmptyReads += 1
                check(consecutiveEmptyReads <= MAX_CONSECUTIVE_EMPTY_READS) {
                    "The image stream stopped responding."
                }
                continue
            }
            consecutiveEmptyReads = 0
            total += read
            require(total <= maxBytes) { "Choose an image smaller than 12 MB." }
            output.write(chunk, 0, read)
        }
        require(total > 0) { "The selected image is empty." }
        output.toByteArray()
    } finally {
        chunk.fill(0)
        output.wipe()
    }
}

private open class WipingByteArrayOutputStream(initialSize: Int) :
    ByteArrayOutputStream(initialSize) {
    fun wipe() {
        buf.fill(0)
        reset()
    }
}

private class CappedByteArrayOutputStream(
    private val maxBytes: Int,
) : WipingByteArrayOutputStream(minOf(DEFAULT_BUFFER_SIZE, maxBytes)) {
    override fun write(value: Int) {
        check(count < maxBytes) { "The prepared image is unexpectedly large." }
        super.write(value)
    }

    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        require(offset >= 0 && length >= 0 && offset <= bytes.size - length)
        check(count.toLong() + length <= maxBytes) { "The prepared image is unexpectedly large." }
        super.write(bytes, offset, length)
    }
}
