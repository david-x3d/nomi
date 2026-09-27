package com.nomi.app.domain.usecase

import com.nomi.app.ai.model.PortionOperation
import com.nomi.app.ui.logging.ManualFoodDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A decimal comma is one character of typing, and it is the character a German, French, Italian,
 * Spanish, Dutch, Portuguese, Albanian, Swedish or Turkish keyboard produces for a decimal point.
 *
 * These are regression tests for two independently verified defects where one input surface
 * accepted `1.5` and rejected `1,5`:
 *  - [ManualFoodDraft] parsed its numeric fields with a bare [String.toDoubleOrNull], so a manual
 *    food entry could never be confirmed by a user typing a comma. The Save button stayed
 *    permanently disabled with no message at all.
 *  - [PortionEditParser]'s fraction-of-count pattern was the only one of its sibling quantity
 *    patterns that accepted a point and not a comma.
 *
 * The matrix below is the shared contract: every quantity surface accepts both separators.
 */
class DecimalCommaMatrixTest {

    private fun manualDraft(
        quantity: String,
        calories: String = "100",
        protein: String = "1",
        carbs: String = "1",
        fat: String = "1",
    ) = ManualFoodDraft(
        name = "Test food",
        amount = quantity,
        calories = calories,
        protein = protein,
        carbohydrates = carbs,
        fat = fat,
    )

    @Test
    fun `manual food entry accepts point and comma decimals alike`() {
        listOf("1.5", "1,5", "0.5", "0,5", "150", "150.0", "150,0").forEach { typed ->
            assertTrue("manual entry rejected \"$typed\"", manualDraft(typed).isValid)
        }
    }

    @Test
    fun `manual food entry accepts a comma in every numeric field at once`() {
        assertTrue(
            manualDraft(
                quantity = "1,5",
                calories = "250,5",
                protein = "12,25",
                carbs = "30,5",
                fat = "8,75",
            ).isValid,
        )
    }

    @Test
    fun `manual food entry still rejects nonsense`() {
        listOf("", "abc", "-1", "0", "1.2.3").forEach { typed ->
            assertFalse("manual entry accepted \"$typed\"", manualDraft(typed).isValid)
        }
    }

    @Test
    fun `fraction of a count parses identically from either separator`() {
        val point = PortionEditParser.parseOrNull("1.5 of 3 pieces")
        val comma = PortionEditParser.parseOrNull("1,5 of 3 pieces")
        assertNotNull("1.5 of 3 pieces did not parse", point)
        assertNotNull("1,5 of 3 pieces did not parse", comma)
        assertEquals(point, comma)
    }

    @Test
    fun `fraction of a count yields the eaten share of the total`() {
        val instruction = PortionEditParser.parseOrNull("1,5 of 3 pieces")
        assertEquals(PortionOperation.SCALE, instruction?.operation)
        assertEquals(0.5, instruction?.factor ?: 0.0, 1e-9)
    }

    @Test
    fun `fraction of a count in german wording yields the same share`() {
        // 1,5 of 3 and 1.5 of 3 are both half. "von" is the German "of".
        listOf("1,5 von 3 Stücken", "1.5 von 3 Stuck", "1,5 of 3 pieces").forEach { typed ->
            val instruction = PortionEditParser.parseOrNull(typed)
            assertNotNull("\"$typed\" did not parse", instruction)
            assertEquals(0.5, instruction?.factor ?: 0.0, 1e-9)
        }
        // And a different share stays a different share: 0,5 of 2 is a quarter.
        assertEquals(0.25, PortionEditParser.parseOrNull("0,5 of 2 pieces")?.factor ?: 0.0, 1e-9)
    }

    @Test
    fun `percentage accepts both separators and both spacings`() {
        listOf("50%", "50 %", "50,5%", "50.5 %", "50,5 %", "50,5 % der Packung").forEach { typed ->
            assertNotNull("percentage \"$typed\" did not parse", PortionEditParser.parseOrNull(typed))
        }
    }

    @Test
    fun `multiplier accepts point comma x and times forms`() {
        listOf("2x", "x2", "2×", "2 x", "2 times", "0,5x", "0.5x").forEach { typed ->
            val instruction = PortionEditParser.parseOrNull(typed)
            assertNotNull("multiplier \"$typed\" did not parse", instruction)
            assertEquals(PortionOperation.SCALE, instruction?.operation)
        }
    }

    @Test
    fun `half wording parses in english and german`() {
        listOf("half", "halve", "die Hälfte", "die Haelfte", "0,5", "0.5").forEach { typed ->
            val instruction = PortionEditParser.parseOrNull(typed)
            assertNotNull("\"$typed\" did not parse", instruction)
            assertEquals(0.5, instruction?.factor ?: 0.0, 1e-9)
        }
    }

    @Test
    fun `relative amount accepts a comma delta`() {
        // "1,5 g less" out of 100 g is a share, so the parser answers with a scale: 1 - 0.015.
        val comma = PortionEditParser.parseAgainstCurrentOrNull(
            correction = "1,5 g less",
            currentQuantity = 100.0,
            currentUnit = "g",
        )
        val point = PortionEditParser.parseAgainstCurrentOrNull(
            correction = "1.5 g less",
            currentQuantity = 100.0,
            currentUnit = "g",
        )
        assertNotNull("comma relative amount did not parse", comma)
        assertEquals(point, comma)
        assertEquals(PortionOperation.SCALE, comma?.operation)
        assertEquals(0.985, comma?.factor ?: 0.0, 1e-9)
    }

    @Test
    fun `an explicit amount with a comma parses`() {
        val instruction = PortionEditParser.parseOrNull("250,5 g")
        assertNotNull(instruction)
        assertEquals(250.5, instruction?.quantity ?: 0.0, 1e-9)
        assertEquals("g", instruction?.unit)
    }

    @Test
    fun `a non-quantity correction is still declined`() {
        // Guard against over-eager matching: the parser must keep deferring wording to the model.
        assertNull(PortionEditParser.parseOrNull("it was chicken, not tuna"))
        assertNull(PortionEditParser.parseOrNull("a bit less"))
    }
}
