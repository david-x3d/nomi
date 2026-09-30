package com.nomi.app.ai.validation

import com.nomi.app.ai.model.ParsedFoodItem
import com.nomi.app.ai.parsing.LocalFoodIntentParser
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Units written out in full, or in US customary, used to miss the amount pattern entirely and
 * fall through to the count pattern: "500 milliliters orange juice" was logged as 500 pieces of
 * "milliliters orange juice" without the model ever seeing it.
 */
class SpelledOutUnitRegressionTest {

    private fun item(text: String): ParsedFoodItem {
        val parsed = requireNotNull(LocalFoodIntentParser.parseOrNull(text)) { text }
        return UserQuantityResolver.reconcileParsedIntent(text, parsed).items.single()
    }

    @Test
    fun `spelled out metric volumes are volumes`() {
        listOf(
            "500 milliliters orange juice" to 500.0,
            "250 millilitres milk" to 250.0,
            "2 liters milk" to 2_000.0,
            "2 litres water" to 2_000.0,
        ).forEach { (text, ml) ->
            val item = item(text)
            assertEquals(text, "ml", item.unit)
            assertEquals(text, ml, item.quantity!!, 1e-9)
            assertEquals(text, ml, item.resolvedVolumeMl!!, 1e-9)
        }
    }

    @Test
    fun `the food name no longer carries the unit`() {
        assertEquals("orange juice", item("500 milliliters orange juice").name)
        assertEquals("chicken", item("1 lb chicken").name)
    }

    @Test
    fun `pounds and ounces are mass`() {
        assertEquals(453.59237, item("1 lb chicken").gramsEquivalent!!, 1e-9)
        assertEquals(907.18474, item("2 lbs beef").gramsEquivalent!!, 1e-9)
        assertEquals(226.796185, item("8 ounces steak").gramsEquivalent!!, 1e-9)
    }

    @Test
    fun `fluid ounces written without a space still resolve`() {
        assertEquals(12 * 29.5735295625, item("12 floz cola").resolvedVolumeMl!!, 1e-9)
    }

    @Test
    fun `German counts four to six are counts`() {
        listOf("vier Eier" to 4.0, "fünf Eier" to 5.0, "sechs Eier" to 6.0).forEach { (text, count) ->
            val item = item(text)
            assertEquals(text, "piece", item.unit)
            assertEquals(text, count, item.quantity!!, 0.0)
        }
    }
}
