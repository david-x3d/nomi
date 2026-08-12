package com.nomi.app.ui.logging

import com.nomi.app.ai.model.AnalyzedFoodItem
import com.nomi.app.ai.model.FoodAnalysis
import com.nomi.app.ui.localization.NomiLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class MealSummaryTest {
    @Test
    fun `multi-part branded order becomes one menu without losing totals`() {
        val analysis = FoodAnalysis(
            items = listOf(
                item("Cheeseburger", "McDonald's", calories = 300.0, protein = 15.0, fiber = 2.0),
                item("Pommes", "McDonald's", calories = 340.0, protein = 4.0, fiber = 4.0),
                item("Cola", "McDonald's", calories = 140.0, protein = 0.0, fiber = null),
            ),
        )

        val summarized = analysis.asSingleLoggedMeal(NomiLanguage.GERMAN)

        assertEquals(1, summarized.items.size)
        with(summarized.items.single()) {
            assertEquals("McDonald's Menü", name)
            assertEquals(780.0, calories, 0.0)
            assertEquals(19.0, proteinGrams, 0.0)
            assertEquals(6.0, fiberGrams ?: 0.0, 0.0)
            assertEquals("Menü", unit)
            assertNull(gramsEquivalent)
        }
    }

    @Test
    fun `single food remains the researched food`() {
        val analysis = FoodAnalysis(items = listOf(item("Cheeseburger", "McDonald's", 300.0, 15.0)))

        assertSame(analysis, analysis.asSingleLoggedMeal(NomiLanguage.GERMAN))
    }

    private fun item(
        name: String,
        brand: String,
        calories: Double,
        protein: Double,
        fiber: Double? = null,
    ) = AnalyzedFoodItem(
        name = name,
        brand = brand,
        quantity = 1.0,
        unit = "portion",
        calories = calories,
        proteinGrams = protein,
        carbohydrateGrams = 10.0,
        fatGrams = 5.0,
        fiberGrams = fiber,
        isEstimate = false,
    )
}
