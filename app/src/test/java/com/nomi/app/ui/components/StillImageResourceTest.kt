package com.nomi.app.ui.components

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every raster drawable must be a still image.
 *
 * Compose's `painterResource` decodes a raster resource to a BitmapDrawable. An animated WebP
 * decodes to an AnimatedImageDrawable instead, and the cast fails at runtime - which took the
 * app down on its very first frame when animated fox loops were dropped into the drawables.
 * Compiling, unit tests and lint all passed; only launching the app found it.
 *
 * A WebP declares animation in its VP8X chunk, so it can be spotted by reading the header
 * without an Android device. This test is the thing that would have caught it.
 */
class StillImageResourceTest {
    @Test
    fun `no drawable is an animated WebP`() {
        val webpFiles = resourceDirectory().walkTopDown().filter { it.extension == "webp" }.toList()
        assertTrue("No WebP drawables were found to check", webpFiles.isNotEmpty())

        val animated = webpFiles.filter(::isAnimatedWebp).map(File::getName)
        assertTrue(
            "These drawables are animated WebP and would crash painterResource: $animated. " +
                "Flatten them to a single frame, or load them with a decoder that handles " +
                "animation.",
            animated.isEmpty(),
        )
    }

    /**
     * A WebP is animated when its extended header is present and carries the animation flag.
     * Layout: "RIFF" size "WEBP" then the first chunk; for an extended file that chunk is
     * "VP8X", whose first byte holds the feature flags and 0x02 is animation.
     */
    private fun isAnimatedWebp(file: File): Boolean {
        val header = file.readBytes().take(VP8X_FLAGS_OFFSET + 1)
        if (header.size <= VP8X_FLAGS_OFFSET) return false
        val riff = header.slice(0..3).toByteArray().decodeToString()
        val webp = header.slice(8..11).toByteArray().decodeToString()
        val chunk = header.slice(12..15).toByteArray().decodeToString()
        if (riff != "RIFF" || webp != "WEBP" || chunk != "VP8X") return false
        return header[VP8X_FLAGS_OFFSET].toInt() and ANIMATION_FLAG != 0
    }

    private fun resourceDirectory(): File = sequenceOf(
        File("src/main/res"),
        File("app/src/main/res"),
    ).firstOrNull(File::isDirectory) ?: error("Could not locate the res directory")

    private companion object {
        const val VP8X_FLAGS_OFFSET = 20
        const val ANIMATION_FLAG = 0x02
    }
}
