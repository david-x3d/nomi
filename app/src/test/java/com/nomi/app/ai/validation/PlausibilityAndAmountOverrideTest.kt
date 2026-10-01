package com.nomi.app.ai.validation

import com.nomi.app.ai.model.AnalyzedFoodItem
import com.nomi.app.ai.model.FoodAnalysis
import com.nomi.app.ai.model.ParsedFoodIntent
import com.nomi.app.ai.model.ParsedFoodItem
import com.nomi.app.ai.model.ResearchNutritionBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PlausibilityAndAmountOverrideTest {

    /** US-style tables count fibre inside carbohydrate, so it must not be added a second time. */
    @Test
    fun `high-fibre foods from a US-style table are accepted`() {
        // protein, carbohydrate (fibre included), fat, fibre - per 100 g.
        mapOf(
            "chia seeds" to listOf(16.5, 42.1, 30.7, 34.4),
            "almonds" to listOf(21.2, 21.6, 49.9, 12.5),
            "flaxseed" to listOf(18.3, 28.9, 42.2, 27.3),
            "cocoa powder" to listOf(19.6, 57.9, 13.7, 37.0),
        ).forEach { (name, values) ->
            val (protein, carbs, fat, fibre) = values
            val normalized = normalize(per100(protein, carbs, fat).copy(name = name, fiberGrams = fibre))

            assertEquals(name, carbs * 0.3, normalized.carbohydrateGrams, 1e-9)
            assertEquals(name, fibre * 0.3, normalized.fiberGrams!!, 1e-9)
        }
    }

    @Test
    fun `macros that cannot fit into 100 g are still refused`() {
        assertThrows(AiValidationException::class.java) {
            normalize(per100(protein = 40.0, carbs = 50.0, fat = 30.0))
        }
        assertThrows(AiValidationException::class.java) {
            normalize(per100(protein = 5.0, carbs = 10.0, fat = 2.0).copy(fiberGrams = 140.0))
        }
    }

    @Test
    fun `a hand-typed amount keeps a researched item saveable`() {
        val researched = normalize(per100(protein = 10.0, carbs = 20.0, fat = 5.0))
        assertTrue(researched.requiresServingValidation)

        val edited = ServingNutritionNormalizer.applyUserAmountOverride(researched, 45.0, "g")

        assertEquals(45.0, edited.quantity, 0.0)
        assertEquals(45.0, edited.gramsEquivalent!!, 1e-9)
        assertEquals(researched.calories, edited.calories, 0.0)
        assertFalse(edited.requiresServingValidation)
        assertNull(edited.servingValidation)
        assertTrue(edited.isEstimate)
        // The exact call that used to throw "logged amount changed" and block the save.
        ServingNutritionNormalizer.validateBeforeSave(FoodAnalysis(listOf(edited)))
    }

    @Test
    fun `a new unit drops a weight it can no longer vouch for`() {
        val researched = normalize(per100(protein = 10.0, carbs = 20.0, fat = 5.0))

        val edited = ServingNutritionNormalizer.applyUserAmountOverride(researched, 1.0, " bowl ")

        assertEquals("bowl", edited.unit)
        assertNull(edited.gramsEquivalent)
        ServingNutritionNormalizer.validateBeforeSave(FoodAnalysis(listOf(edited)))
    }

    @Test
    fun `an unchanged amount is not a correction`() {
        val researched = normalize(per100(protein = 10.0, carbs = 20.0, fat = 5.0))

        assertSame(researched, ServingNutritionNormalizer.applyUserAmountOverride(researched, 30.0, "g"))
    }

    /** A 300 g plate's totals are not per-100 values. */
    @Test
    fun `an item whose amount was typed by hand can still have its nutrition corrected`() {
        val researched = normalize(per100(protein = 10.0, carbs = 20.0, fat = 5.0))
        val plate = ServingNutritionNormalizer.applyUserAmountOverride(researched, 300.0, "g")

        val corrected = ServingNutritionNormalizer.applyUserNutrientCorrection(
            item = plate,
            calories = 900.0,
            proteinGrams = 60.0,
            carbohydrateGrams = 70.0,
            fatGrams = 40.0,
        )

        assertEquals(900.0, corrected.calories, 0.0)
        ServingNutritionNormalizer.validateBeforeSave(FoodAnalysis(listOf(corrected)))
        // 170 g of macros in 100 g of food is still impossible.
        assertThrows(AiValidationException::class.java) {
            ServingNutritionNormalizer.applyUserNutrientCorrection(
                item = ServingNutritionNormalizer.applyUserAmountOverride(researched, 100.0, "g"),
                calories = 900.0,
                proteinGrams = 60.0,
                carbohydrateGrams = 70.0,
                fatGrams = 40.0,
            )
        }
    }

    private fun normalize(raw: AnalyzedFoodItem): AnalyzedFoodItem = ServingNutritionNormalizer.normalize(
        intent = ParsedFoodIntent(
            originalText = "30 g ${raw.name}",
            items = listOf(
                ParsedFoodItem(name = raw.name, quantity = 30.0, unit = "g", gramsEquivalent = 30.0),
            ),
        ),
        unnormalized = FoodAnalysis(items = listOf(raw)),
    ).items.single()

    private fun per100(protein: Double, carbs: Double, fat: Double) = AnalyzedFoodItem(
        name = "Test food",
        quantity = 30.0,
        unit = "g",
        gramsEquivalent = 30.0,
        calories = protein * 4 + carbs * 4 + fat * 9,
        proteinGrams = protein,
        carbohydrateGrams = carbs,
        fatGrams = fat,
        sourceName = "Food database",
        sourceUrl = "https://example.org/food",
        sourceServingQuantity = 100.0,
        sourceServingUnit = "g",
        nutritionBasis = ResearchNutritionBasis.PER_100_G,
        isEstimate = false,
    )
}
