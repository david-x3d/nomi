package com.nomi.app.ai.validation

import com.nomi.app.ai.model.AnalyzedFoodItem
import com.nomi.app.ai.model.FoodAnalysis
import com.nomi.app.ai.model.MenuDish
import com.nomi.app.ai.model.ParsedFoodIntent
import com.nomi.app.ai.model.ParsedFoodItem
import com.nomi.app.ai.model.QuantityOrigin
import org.junit.Assert.assertEquals
import org.junit.Test

class MenuQuantityResolutionTest {
    private val menuItems = listOf(
        MenuDish(
            name = "Suppe des verwirrten Kochs",
            description = "300 ml, Tagessuppe mit Brot",
        ),
        MenuDish(
            name = "Burger Doppelter Doedel",
            description = "1 St\u00fcck - 180 g Rind, Cheddar, Bacon",
        ),
        MenuDish(
            name = "Coca-Cola",
            description = "0.33 l Flasche",
        ),
        MenuDish(
            name = "Vanilleeis",
            description = "3 Kugeln / 165 g",
        ),
    )

    @Test
    fun `menu quantities override inferred values through serving normalization`() {
        val providerParsed = ParsedFoodIntent(
            originalText = menuItems.joinToString("; ") { it.description.orEmpty() },
            language = "de",
            items = menuItems.map { ParsedFoodItem(it.name, quantity = 999.0, unit = "ml") },
        )

        val menuResolved = UserQuantityResolver.applyMenuQuantities(menuItems, providerParsed)
        val reconciledAgain = UserQuantityResolver.reconcileIntent(menuResolved, "DE")

        assertAmount(reconciledAgain.items[0], 300.0, "ml")
        assertAmount(reconciledAgain.items[1], 1.0, "St\u00fcck")
        assertAmount(reconciledAgain.items[2], 330.0, "ml")
        assertAmount(reconciledAgain.items[3], 165.0, "g")
        assertEquals(3.0, reconciledAgain.items[3].quantityResolution!!.enteredQuantity!!, 0.0)
        assertEquals("Kugeln", reconciledAgain.items[3].quantityResolution!!.enteredUnit)

        val providerNutrition = FoodAnalysis(
            items = listOf(
                sourceItem("Suppe des verwirrten Kochs", 165.0, "g", 100.0, "ml"),
                sourceItem("Burger Doppelter Doedel", 300.0, "ml", 1.0, "St\u00fcck"),
                sourceItem("Coca-Cola", 180.0, "g", 100.0, "ml"),
                sourceItem("Vanilleeis", 330.0, "ml", 100.0, "g"),
            ),
        )
        val providerReconciled = UserQuantityResolver.reconcileAnalysis(
            reconciledAgain,
            providerNutrition,
        )
        val normalized = ServingNutritionNormalizer.normalize(reconciledAgain, providerReconciled)

        assertAmount(normalized.items[0], 300.0, "ml")
        assertAmount(normalized.items[1], 1.0, "St\u00fcck")
        assertAmount(normalized.items[2], 330.0, "ml")
        assertAmount(normalized.items[3], 165.0, "g")
        normalized.items.forEach {
            assertEquals(QuantityOrigin.MENU_EXPLICIT, it.quantityResolution!!.origin)
        }
    }

    private fun assertAmount(item: ParsedFoodItem, quantity: Double, unit: String) {
        assertEquals(quantity, item.quantity!!, 0.0)
        assertEquals(unit, item.unit)
        assertEquals(QuantityOrigin.MENU_EXPLICIT, item.quantityResolution!!.origin)
    }

    private fun assertAmount(item: AnalyzedFoodItem, quantity: Double, unit: String) {
        assertEquals(quantity, item.quantity, 0.0)
        assertEquals(unit, item.unit)
    }

    private fun sourceItem(
        name: String,
        quantity: Double,
        unit: String,
        sourceQuantity: Double,
        sourceUnit: String,
    ) = AnalyzedFoodItem(
        name = name,
        quantity = quantity,
        unit = unit,
        calories = 100.0,
        proteinGrams = 5.0,
        carbohydrateGrams = 10.0,
        fatGrams = 2.0,
        sourceServingQuantity = sourceQuantity,
        sourceServingUnit = sourceUnit,
        sourceName = "Test menu source",
        sourceUrl = "https://example.test/menu",
        isEstimate = true,
    )
}
