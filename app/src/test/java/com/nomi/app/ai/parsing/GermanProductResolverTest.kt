package com.nomi.app.ai.parsing

import com.nomi.app.ai.model.ParsedFoodIntent
import com.nomi.app.ai.model.ParsedFoodItem
import com.nomi.app.ai.validation.UserQuantityResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GermanProductResolverTest {
    @Test
    fun `plain Duplo is resolved after the local single food parser`() {
        val locallyParsed = requireNotNull(LocalFoodIntentParser.parseOrNull("Duplo"))

        val resolved = UserQuantityResolver.reconcileIntent(locallyParsed, "DE").items.single()

        assertEquals("Duplo", resolved.name)
        assertEquals("Ferrero", resolved.brand)
        assertTrue(resolved.assumptions.single().contains("exact German product"))
    }

    @Test
    fun `spoken German aliases resolve to searchable product identities`() {
        val cases = listOf(
            Triple("Kinderriegel", "Kinder Riegel", "Ferrero"),
            Triple("Milch Schnitte", "Kinder Milch-Schnitte", "Ferrero"),
            Triple("Knopper", "Knoppers", "Storck"),
            Triple("Goldbaeren", "HARIBO Goldbären", "HARIBO"),
            Triple("Prinzenrolle", "Prinzen Rolle", "DeBeukelaer"),
        )

        cases.forEach { (input, expectedName, expectedBrand) ->
            val resolved = resolve(input, ParsedFoodItem(input))
            assertEquals(input, expectedName, resolved.name)
            assertEquals(input, expectedBrand, resolved.brand)
        }
    }

    @Test
    fun `explicit product variant and quantity are preserved`() {
        val resolved = resolve(
            "2 Duplo White Riegel",
            ParsedFoodItem(name = "Duplo White", quantity = 2.0, unit = "pieces"),
        )

        assertEquals("Duplo White", resolved.name)
        assertEquals("Ferrero", resolved.brand)
        assertEquals(2.0, resolved.quantity!!, 0.0)
        assertEquals("pieces", resolved.unit)
    }

    @Test
    fun `whole meal text cannot relabel a different item`() {
        val intent = ParsedFoodIntent(
            originalText = "Duplo und Banane",
            items = listOf(ParsedFoodItem("Duplo"), ParsedFoodItem("Banane")),
        )

        val resolved = UserQuantityResolver.reconcileIntent(intent, "DE")

        assertEquals("Ferrero", resolved.items[0].brand)
        assertEquals("Banane", resolved.items[1].name)
        assertNull(resolved.items[1].brand)
    }

    @Test
    fun `German catalog is not forced onto another market`() {
        val original = ParsedFoodItem("Duplo")

        val resolved = GermanProductResolver.enrich("Duplo", original, 1, "US")

        assertEquals(original, resolved)
    }

    private fun resolve(text: String, item: ParsedFoodItem): ParsedFoodItem =
        UserQuantityResolver.reconcileParsedIntent(
            userText = text,
            parsed = ParsedFoodIntent(originalText = text, items = listOf(item)),
            localeCountry = "DE",
        ).items.single()
}
