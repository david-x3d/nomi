package com.nomi.app.ai.validation

import com.nomi.app.ai.model.ParsedFoodIntent
import com.nomi.app.ai.model.ParsedFoodItem
import com.nomi.app.ai.model.QuantitySemantic
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A fat percentage in front of an amount is part of the product name.
 *
 * "Milch 1,5% 250 ml" used to be read as 1.5 % of a 250 ml package and logged 3.75 ml.
 */
class FatPercentageRegressionTest {

    @Test
    fun `a fat percentage followed by an amount logs the whole amount`() {
        listOf(
            Triple("Milch 1,5% 250 ml", 250.0, "ml"),
            Triple("Joghurt 3,5 % 150 g", 150.0, "g"),
            Triple("Hackfleisch 20% 300g", 300.0, "g"),
            Triple("Skyr 0,2% 150 g", 150.0, "g"),
        ).forEach { (text, quantity, unit) ->
            val item = resolve(text)

            assertEquals(text, quantity, item.quantity!!, 0.0)
            assertEquals(text, unit, item.unit)
            assertEquals(text, QuantitySemantic.DIRECT_AMOUNT, item.quantityResolution!!.semantic)
        }
    }

    @Test
    fun `a share of a package still needs only a linking word`() {
        listOf(
            "50% of a 200 g bag of chips",
            "50 % von 200 g Chips",
            "50% einer 200 g Packung Chips",
        ).forEach { text ->
            val item = resolve(text)

            assertEquals(text, 100.0, item.quantity!!, 0.0)
            assertEquals(text, QuantitySemantic.PACKAGE_PERCENT, item.quantityResolution!!.semantic)
        }
    }

    private fun resolve(text: String): ParsedFoodItem = UserQuantityResolver.reconcileParsedIntent(
        userText = text,
        parsed = ParsedFoodIntent(
            originalText = text,
            language = "de",
            items = listOf(ParsedFoodItem(name = "Testprodukt", quantity = 999.0, unit = "ml")),
        ),
        localeCountry = "DE",
    ).items.single()
}
