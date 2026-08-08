package com.nomi.app.ui.today

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tapping a logged line opens it for writing with the caret under the finger. The row and the
 * editable sentence word the same entry differently, so these cover the translation between
 * them: touching a word must land in that word, never a few characters off.
 */
class ReeditableCaretTest {
    @Test
    fun `tapping the food name lands in the name, past the amount`() {
        val entry = entry(name = "Reis", brand = null, amountText = "180 g")

        assertEquals("180 g Reis", entry.reeditableText())
        // Start of "Reis" on the row is start of "Reis" in the sentence.
        assertEquals(6, entry.reeditableCaretForDescription(0))
        assertEquals(8, entry.reeditableCaretForDescription(2))
        assertEquals(10, entry.reeditableCaretForDescription(4))
    }

    @Test
    fun `tapping the brand skips the separator the row adds`() {
        val entry = entry(name = "Latte", brand = "La Casita", amountText = "1 medium")

        assertEquals("1 medium Latte La Casita", entry.reeditableText())
        assertEquals("Latte · La Casita", entry.rowDescription())
        // Offset 8 on the row is the "L" of the brand; in the sentence that is offset 15.
        assertEquals(15, entry.reeditableCaretForDescription(8))
        assertEquals(24, entry.reeditableCaretForDescription(entry.rowDescription().length))
    }

    @Test
    fun `a tap inside the separator stays at the end of the name`() {
        val entry = entry(name = "Latte", brand = "La Casita", amountText = "1 medium")

        assertEquals(14, entry.reeditableCaretForDescription(5))
        assertEquals(15, entry.reeditableCaretForDescription(6))
    }

    @Test
    fun `an entry without an amount starts its sentence at the name`() {
        val entry = entry(name = "Espresso", brand = null, amountText = "")

        assertEquals("Espresso", entry.reeditableText())
        assertEquals(0, entry.reeditableCaretForDescription(0))
        assertEquals(4, entry.reeditableCaretForDescription(4))
    }

    @Test
    fun `the amount line can only place the caret inside the written amount`() {
        val entry = entry(name = "Reis", brand = null, amountText = "180 g")

        assertEquals(3, entry.reeditableCaretForAmount(3))
        // The line shows a formatted quantity that can be longer than what was typed.
        assertEquals(5, entry.reeditableCaretForAmount(40))
        assertEquals(0, entry.reeditableCaretForAmount(-1))
    }

    @Test
    fun `taps beyond the words are clamped into the sentence`() {
        val entry = entry(name = "Reis", brand = null, amountText = "180 g")

        assertEquals(10, entry.reeditableCaretForDescription(99))
        assertEquals(6, entry.reeditableCaretForDescription(-5))
    }

    private fun entry(name: String, brand: String?, amountText: String) = TodayFoodEntry(
        id = 1L,
        name = name,
        brand = brand,
        amountText = amountText,
        calories = 100.0,
        mealCategory = MealCategory.LUNCH,
        time = LocalTime.of(12, 0),
    )
}
