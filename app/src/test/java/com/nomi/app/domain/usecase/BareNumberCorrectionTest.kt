package com.nomi.app.domain.usecase

import com.nomi.app.ai.model.PortionOperation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * "I only had 2" used to be read as "twice", so three logged eggs became six. A bare number of
 * one or more is a count, never a factor.
 */
class BareNumberCorrectionTest {

    @Test
    fun `a bare count against pieces sets the count`() {
        listOf("nur 2", "I only had 2", "2").forEach { typed ->
            val instruction = PortionEditParser.parseAgainstCurrentOrNull(typed, 3.0, "piece")
            assertEquals(typed, PortionOperation.SET_QUANTITY, instruction?.operation)
            assertEquals(typed, 2.0, instruction?.quantity ?: 0.0, 0.0)
            assertEquals(typed, "piece", instruction?.unit)
        }
    }

    @Test
    fun `a bare whole number is not a factor`() {
        assertNull(PortionEditParser.parseOrNull("2"))
        assertNull(PortionEditParser.parseOrNull("nur 2"))
        assertNull(PortionEditParser.parseOrNull("1,5"))
    }

    @Test
    fun `a bare number against grams is left to the model`() {
        assertNull(PortionEditParser.parseAgainstCurrentOrNull("2", 200.0, "g"))
    }

    @Test
    fun `a share below one is still a factor`() {
        assertEquals(0.5, PortionEditParser.parseOrNull("0,5")?.factor ?: 0.0, 1e-9)
    }

    @Test
    fun `halb halves`() {
        assertEquals(0.5, PortionEditParser.parseOrNull("halb")?.factor ?: 0.0, 1e-9)
    }
}
