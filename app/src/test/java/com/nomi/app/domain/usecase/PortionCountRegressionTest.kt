package com.nomi.app.domain.usecase

import com.nomi.app.ai.model.PortionOperation
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A two-digit count is an amount, not a fraction.
 *
 * "12 pieces" used to be read as "1 of 2 pieces" because the pattern for "3 of the 6 pieces" let
 * the two numbers touch, so correcting six nuggets to twelve halved them instead.
 */
class PortionCountRegressionTest {

    @Test
    fun `a two-digit count sets the quantity instead of scaling by its digits`() {
        listOf("12 pieces" to 12.0, "25 pieces" to 25.0, "36 slices" to 36.0, "15 Stück" to 15.0)
            .forEach { (typed, expected) ->
                val instruction = PortionEditParser.parseOrNull(typed)
                    ?: throw AssertionError("\"$typed\" should be read on device")
                assertEquals(typed, PortionOperation.SET_QUANTITY, instruction.operation)
                assertEquals(typed, expected, instruction.quantity!!, 0.0)
            }
    }

    @Test
    fun `a decimal count is not split at its last digit`() {
        val instruction = PortionEditParser.parseOrNull("2.25 pieces")!!

        assertEquals(PortionOperation.SET_QUANTITY, instruction.operation)
        assertEquals(2.25, instruction.quantity!!, 0.0)
    }

    @Test
    fun `a stated share of a count still scales`() {
        mapOf(
            "3 of the 6 pieces" to 0.5,
            "3/6 pieces" to 0.5,
            "2 out of 4 slices" to 0.5,
            "1,5 von 3 Stücken" to 0.5,
        ).forEach { (typed, expected) ->
            val instruction = PortionEditParser.parseOrNull(typed)
                ?: throw AssertionError("\"$typed\" should be read on device")
            assertEquals(typed, PortionOperation.SCALE, instruction.operation)
            assertEquals(typed, expected, instruction.factor!!, 1e-9)
        }
    }
}
