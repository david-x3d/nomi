package com.nomi.app.integration.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The dictation bar only says "you are being heard" if the bars actually follow a voice. These
 * cover the two steps between the microphone and a bar height: how loud a block of samples is,
 * and how that loudness becomes a height.
 */
class MicrophoneLevelTest {
    @Test
    fun `silence has no level`() {
        assertEquals(0f, rootMeanSquare(ShortArray(64), 64), 0f)
        assertEquals(0f, microphoneLevel(0f), 0f)
    }

    @Test
    fun `an empty read is not a division by zero`() {
        assertEquals(0f, rootMeanSquare(ShortArray(64), 0), 0f)
    }

    @Test
    fun `a full scale block reaches the top`() {
        val buffer = ShortArray(32) { Short.MAX_VALUE }

        assertEquals(1f, rootMeanSquare(buffer, buffer.size), 0.001f)
        assertEquals(1f, microphoneLevel(1f), 0.001f)
    }

    @Test
    fun `only the samples that were read count`() {
        val buffer = ShortArray(8)
        buffer[0] = Short.MAX_VALUE
        buffer[1] = Short.MAX_VALUE

        // Two loud samples out of two read is loud; out of eight it is a quarter of the energy.
        assertEquals(1f, rootMeanSquare(buffer, 2), 0.001f)
        assertEquals(0.5f, rootMeanSquare(buffer, 8), 0.001f)
    }

    @Test
    fun `speech is spread across the bar instead of hugging the floor`() {
        // A conversational voice sits around a tenth of full scale. Drawn linearly that would
        // be a bar with 10% of the height; the decibel mapping has to give it far more.
        val speech = microphoneLevel(0.1f)

        assertTrue("quiet speech was $speech", speech > 0.5f)
        assertTrue("quiet speech was $speech", speech < 1f)
    }

    @Test
    fun `room noise stays on the floor`() {
        assertEquals(0f, microphoneLevel(0.001f), 0f)
    }

    @Test
    fun `louder always means taller`() {
        val quiet = microphoneLevel(0.01f)
        val normal = microphoneLevel(0.1f)
        val loud = microphoneLevel(0.6f)

        assertTrue(quiet < normal)
        assertTrue(normal < loud)
        assertTrue(loud <= 1f)
    }
}
