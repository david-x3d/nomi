package com.nomi.app.ui.components

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The waveform is a moving trail, not a set of independently wobbling bars: what was heard a
 * moment ago has to walk left while the newest sound arrives on the right.
 */
class DictationWaveformTest {
    @Test
    fun `the newest level lands on the right and the rest shifts left`() {
        val trail = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f)

        assertArrayEquals(floatArrayOf(0.2f, 0.3f, 0.4f, 0.9f), advancedTrail(trail, 0.9f), 0.0001f)
    }

    @Test
    fun `the oldest bar leaves after as many steps as there are bars`() {
        var trail = floatArrayOf(1f, 0f, 0f)
        repeat(3) { trail = advancedTrail(trail, 0f) }

        assertArrayEquals(floatArrayOf(0f, 0f, 0f), trail, 0.0001f)
    }

    @Test
    fun `a level outside the scale cannot draw past the pill`() {
        assertEquals(1f, advancedTrail(FloatArray(3), 4f).last(), 0.0001f)
        assertEquals(0f, advancedTrail(FloatArray(3), -2f).last(), 0.0001f)
    }

    @Test
    fun `the trail keeps its length`() {
        val trail = FloatArray(34)

        assertEquals(34, advancedTrail(trail, 0.5f).size)
    }
}
