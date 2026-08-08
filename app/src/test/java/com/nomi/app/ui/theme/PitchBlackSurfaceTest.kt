package com.nomi.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PitchBlackSurfaceTest {
    @Test
    fun `pure black canvas is detected as a pitch black theme`() {
        assertTrue(isPitchBlackCanvas(Color(0xFF000000)))
        assertTrue(isPitchBlackCanvas(Color(0xFF050505)))
    }

    @Test
    fun `ordinary dark canvases are not treated as pitch black`() {
        // Nomi's own dark canvas measures under 0.005 luminance, so only channel values
        // separate a genuine OLED black theme from an ordinary dark one.
        assertFalse(isPitchBlackCanvas(Color(0xFF130D0B)))
        assertFalse(isPitchBlackCanvas(Color(0xFF1C1B1F)))
        assertFalse(isPitchBlackCanvas(Color(0xFFFFF8F5)))
    }

    @Test
    fun `container tones are pulled toward a black canvas but stay distinguishable`() {
        val canvas = Color(0xFF000000)
        val greyContainer = Color(0xFF292929)

        val flattened = flattenTowardCanvas(canvas, greyContainer)

        assertTrue(flattened.luminance() < greyContainer.luminance())
        assertTrue(flattened.luminance() > canvas.luminance())
    }

    @Test
    fun `flattening keeps relative container ordering intact`() {
        val canvas = Color(0xFF000000)
        val low = flattenTowardCanvas(canvas, Color(0xFF1A1A1A))
        val high = flattenTowardCanvas(canvas, Color(0xFF303030))
        val highest = flattenTowardCanvas(canvas, Color(0xFF3B3B3B))

        assertTrue(low.luminance() < high.luminance())
        assertTrue(high.luminance() < highest.luminance())
    }

    @Test
    fun `a container already matching the canvas stays unchanged`() {
        val canvas = Color(0xFF000000)

        assertEquals(canvas, flattenTowardCanvas(canvas, canvas))
    }
}
