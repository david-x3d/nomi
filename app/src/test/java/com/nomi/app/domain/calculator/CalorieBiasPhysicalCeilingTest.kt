package com.nomi.app.domain.calculator

import com.nomi.app.ai.model.AnalyzedFoodItem
import com.nomi.app.ai.model.FoodAnalysis
import com.nomi.app.ai.model.ParsedFoodIntent
import com.nomi.app.ai.model.ParsedFoodItem
import com.nomi.app.ai.model.ResearchNutritionBasis
import com.nomi.app.ai.validation.ServingNutritionNormalizer
import com.nomi.app.data.preferences.CalorieEstimateBias
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Estimate high" must not produce a food that cannot exist.
 *
 * 100 g of oil is 100 g of fat. Scaling that by the bias gave 110 g of fat per 100 g, which the
 * normalizer refuses, so oil, butter and sugar could not be logged while the setting was on.
 */
class CalorieBiasPhysicalCeilingTest {

    @Test
    fun `a biased pure fat still passes the per-100 plausibility check`() {
        CalorieEstimateBias.entries.forEach { bias ->
            val biased = CalorieBiasAdjuster.apply(oilPer100g(), bias)

            assertTrue("$bias fat ${biased.fatGrams}", biased.fatGrams <= 100.0 + 1e-9)
            assertTrue("$bias kcal ${biased.calories}", biased.calories <= 900.0 + 1e-9)
            // Throws when the values are not possible per 100 g.
            ServingNutritionNormalizer.normalize(
                intent = ParsedFoodIntent(
                    originalText = "10 g olive oil",
                    items = listOf(
                        ParsedFoodItem(name = "Olive oil", quantity = 10.0, unit = "g", gramsEquivalent = 10.0),
                    ),
                ),
                unnormalized = FoodAnalysis(items = listOf(biased)),
            )
        }
    }

    @Test
    fun `an underestimate is never held back by the ceiling`() {
        val biased = CalorieBiasAdjuster.apply(oilPer100g(), CalorieEstimateBias.STRONGLY_UNDERESTIMATE)

        assertEquals(884.0 * 0.85, biased.calories, 1e-9)
    }

    @Test
    fun `a food with room to spare is biased in full`() {
        val curry = oilPer100g().copy(calories = 150.0, proteinGrams = 8.0, carbohydrateGrams = 12.0, fatGrams = 7.0)

        val biased = CalorieBiasAdjuster.apply(curry, CalorieEstimateBias.STRONGLY_OVERESTIMATE)

        assertEquals(150.0 * 1.15, biased.calories, 1e-9)
    }

    private fun oilPer100g() = AnalyzedFoodItem(
        name = "Olive oil",
        quantity = 10.0,
        unit = "g",
        gramsEquivalent = 10.0,
        calories = 884.0,
        proteinGrams = 0.0,
        carbohydrateGrams = 0.0,
        fatGrams = 100.0,
        sourceServingQuantity = 100.0,
        sourceServingUnit = "g",
        nutritionBasis = ResearchNutritionBasis.PER_100_G,
        isEstimate = true,
    )
}
