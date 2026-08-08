package com.nomi.app.ai.parsing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Correcting a typo and swapping a product look identical from one character's distance. These
 * cover both directions: the slip must be fixed, and the neighbouring variant must never win.
 */
class FoodNameCorrectionTest {
    private val known = listOf(
        "Red Bull Juneberry",
        "Red Bull Watermelon",
        "Coca-Cola Zero",
        "Skyr Vanille",
        "Müsli",
        "Haferflocken",
        "Käse",
    )

    @Test
    fun `a mistyped product is corrected to the one you have logged`() {
        assertEquals("Red Bull Juneberry", FoodNameCorrection.correctedOrNull("red bull junebrry", known))
        assertEquals("Haferflocken", FoodNameCorrection.correctedOrNull("haferflcken", known))
    }

    @Test
    fun `a missing space is not a different food`() {
        assertEquals("Haferflocken", FoodNameCorrection.correctedOrNull("hafer flocken", known))
        assertEquals("Red Bull Juneberry", FoodNameCorrection.correctedOrNull("redbull juneberry", known))
    }

    @Test
    fun `German spellings without umlauts are not mistakes at all`() {
        assertEquals("Müsli", FoodNameCorrection.correctedOrNull("muesli", known))
        assertEquals("Käse", FoodNameCorrection.correctedOrNull("kaese", known))
        assertEquals("Käse", FoodNameCorrection.correctedOrNull("KÄSE", known))
    }

    @Test
    fun `punctuation and casing are ignored`() {
        assertEquals("Coca-Cola Zero", FoodNameCorrection.correctedOrNull("coca cola zero", known))
    }

    @Test
    fun `a neighbouring variant is never substituted`() {
        // Watermelon and Juneberry are both known; neither may stand in for the other.
        assertNull(FoodNameCorrection.correctedOrNull("red bull pineapple", known))
        assertNull(FoodNameCorrection.correctedOrNull("skyr erdbeere", known))
        assertNull(FoodNameCorrection.correctedOrNull("coca-cola light", known))
    }

    @Test
    fun `a short word must match exactly, because that is where the variant hides`() {
        // "Zero" is only one edit from "Hero", but it is the whole difference between products.
        assertNull(FoodNameCorrection.correctedOrNull("coca-cola hero", known))
    }

    @Test
    fun `a food you have never logged is left alone`() {
        assertNull(FoodNameCorrection.correctedOrNull("Bratwurst", known))
        assertNull(FoodNameCorrection.correctedOrNull("", known))
    }

    @Test
    fun `a name that only differs by an extra word is not a typo`() {
        assertNull(FoodNameCorrection.correctedOrNull("Red Bull", known))
        assertNull(FoodNameCorrection.correctedOrNull("Red Bull Juneberry Sugarfree", known))
    }

    @Test
    fun `an exact match wins immediately`() {
        assertEquals("Skyr Vanille", FoodNameCorrection.correctedOrNull("skyr vanille", known))
    }
}
