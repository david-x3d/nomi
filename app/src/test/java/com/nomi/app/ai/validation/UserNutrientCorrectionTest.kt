package com.nomi.app.ai.validation

import com.nomi.app.ai.model.AnalyzedFoodItem
import com.nomi.app.ai.model.FoodAnalysis
import com.nomi.app.ai.model.ParsedFoodIntent
import com.nomi.app.ai.model.ParsedFoodItem
import com.nomi.app.ai.model.PortionOperation
import com.nomi.app.ai.model.PortionEditInstruction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression cover for a verified defect: the "tap a food to fix a number" dialog wrote the
 * corrected nutrient values straight onto an already source-normalized item while leaving the
 * old [com.nomi.app.ai.model.ServingSizeValidation] attached.
 *
 * [ServingNutritionNormalizer.validateBeforeSave] re-derives every nutrient from that recorded
 * basis and compares it to the item, so any user edit made the invariant false and the whole
 * meal - not just the edited item - failed to save with "The serving amount could not be
 * validated."
 *
 * The correction therefore has to re-derive the per-100 basis rather than be waved through, and
 * the plausibility bounds have to survive that.
 */
class UserNutrientCorrectionTest {

    private fun researched(
        loggedQuantity: Double = 200.0,
        loggedUnit: String = "g",
        caloriesPer100: Double = 250.0,
        proteinPer100: Double = 10.0,
        carbsPer100: Double = 20.0,
        fatPer100: Double = 12.0,
    ): AnalyzedFoodItem {
        val source = AnalyzedFoodItem(
            name = "Test food",
            quantity = loggedQuantity,
            unit = loggedUnit,
            gramsEquivalent = loggedQuantity.takeIf { loggedUnit == "g" },
            calories = caloriesPer100,
            proteinGrams = proteinPer100,
            carbohydrateGrams = carbsPer100,
            fatGrams = fatPer100,
            sourceName = "Official manufacturer",
            sourceUrl = "https://manufacturer.example/de/product",
            sourceServingQuantity = 100.0,
            sourceServingUnit = "g",
            sourceCountry = "DE",
            isEstimate = false,
        )
        val intent = ParsedFoodIntent(
            originalText = "$loggedQuantity $loggedUnit test food",
            items = listOf(
                ParsedFoodItem(
                    name = "Test food",
                    quantity = loggedQuantity,
                    unit = loggedUnit,
                    gramsEquivalent = loggedQuantity.takeIf { loggedUnit == "g" },
                ),
            ),
        )
        return ServingNutritionNormalizer.normalize(
            intent = intent,
            unnormalized = FoodAnalysis(items = listOf(source)),
        ).items.single()
    }

    @Test
    fun `a researched item is genuinely validated before the correction`() {
        val item = researched()
        assertTrue("fixture is not a validated item", item.requiresServingValidation)
        assertNotNull(item.servingValidation)
        // Precondition for the defect: this passes on the untouched item.
        ServingNutritionNormalizer.validateBeforeSave(FoodAnalysis(listOf(item)))
    }

    @Test
    fun `correcting calories keeps the item saveable`() {
        val item = researched()
        val corrected = ServingNutritionNormalizer.applyUserNutrientCorrection(
            item = item,
            calories = 400.0,
            proteinGrams = item.proteinGrams,
            carbohydrateGrams = item.carbohydrateGrams,
            fatGrams = item.fatGrams,
        )
        // The whole point: the meal now saves.
        ServingNutritionNormalizer.validateBeforeSave(FoodAnalysis(listOf(corrected)))
        assertEquals(400.0, corrected.calories, 1e-9)
    }

    @Test
    fun `what the user typed is what gets persisted`() {
        val item = researched()
        val shown = listOf(400.0, 12.0, 24.0, 9.0)
        val corrected = ServingNutritionNormalizer.applyUserNutrientCorrection(
            item = item,
            calories = shown[0],
            proteinGrams = shown[1],
            carbohydrateGrams = shown[2],
            fatGrams = shown[3],
        )
        // Displayed values must equal persisted values: no silent re-derivation of what the user
        // asked for, which is the failure mode this whole fix is about.
        assertEquals(shown[0], corrected.calories, 1e-9)
        assertEquals(shown[1], corrected.proteinGrams, 1e-9)
        assertEquals(shown[2], corrected.carbohydrateGrams, 1e-9)
        assertEquals(shown[3], corrected.fatGrams, 1e-9)
    }

    @Test
    fun `the logged amount is untouched by a nutrient correction`() {
        val item = researched(loggedQuantity = 200.0, loggedUnit = "g")
        val corrected = ServingNutritionNormalizer.applyUserNutrientCorrection(
            item = item,
            calories = 400.0,
            proteinGrams = 10.0,
            carbohydrateGrams = 20.0,
            fatGrams = 12.0,
        )
        // The user corrected what the food contains, not how much they ate.
        assertEquals(200.0, corrected.quantity, 0.0)
        assertEquals("g", corrected.unit)
        assertEquals(item.gramsEquivalent ?: 0.0, corrected.gramsEquivalent ?: 0.0, 0.0)
    }

    @Test
    fun `correcting every macro together stays saveable`() {
        val item = researched()
        val corrected = ServingNutritionNormalizer.applyUserNutrientCorrection(
            item = item,
            calories = 321.0,
            proteinGrams = 11.0,
            carbohydrateGrams = 22.0,
            fatGrams = 8.5,
        )
        ServingNutritionNormalizer.validateBeforeSave(FoodAnalysis(listOf(corrected)))
    }

    @Test
    fun `a corrected item is still marked as an estimate`() {
        val corrected = ServingNutritionNormalizer.applyUserNutrientCorrection(
            item = researched(),
            calories = 400.0,
            proteinGrams = 10.0,
            carbohydrateGrams = 20.0,
            fatGrams = 12.0,
        )
        // A value Nomi's source did not report is no longer a source reading.
        assertTrue(corrected.isEstimate)
    }

    @Test
    fun `an impossible calorie correction is still refused`() {
        val item = researched()
        // 5000 kcal for 200 g is 2500 kcal per 100 g, past the physical ceiling.
        assertThrows(AiValidationException::class.java) {
            ServingNutritionNormalizer.applyUserNutrientCorrection(
                item = item,
                calories = 5_000.0,
                proteinGrams = 10.0,
                carbohydrateGrams = 20.0,
                fatGrams = 12.0,
            )
        }
    }

    @Test
    fun `macros that cannot fit in 100 g are still refused`() {
        val item = researched()
        assertThrows(AiValidationException::class.java) {
            ServingNutritionNormalizer.applyUserNutrientCorrection(
                item = item,
                calories = 500.0,
                proteinGrams = 80.0,
                carbohydrateGrams = 80.0,
                fatGrams = 80.0,
            )
        }
    }

    @Test
    fun `a negative or non-finite correction is refused`() {
        val item = researched()
        listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { bad ->
            assertThrows(AiValidationException::class.java) {
                ServingNutritionNormalizer.applyUserNutrientCorrection(
                    item = item,
                    calories = bad,
                    proteinGrams = 10.0,
                    carbohydrateGrams = 20.0,
                    fatGrams = 12.0,
                )
            }
        }
    }

    @Test
    fun `a multi item meal with one corrected item saves as a whole`() {
        val first = researched(caloriesPer100 = 250.0, proteinPer100 = 10.0)
        val second = researched(caloriesPer100 = 100.0, proteinPer100 = 5.0, carbsPer100 = 12.0, fatPer100 = 3.0)
            .copy(name = "Second food")

        val correctedSecond = ServingNutritionNormalizer.applyUserNutrientCorrection(
            item = second,
            calories = 150.0,
            proteinGrams = 5.0,
            carbohydrateGrams = 12.0,
            fatGrams = 3.0,
        )
        val meal = FoodAnalysis(listOf(first, correctedSecond))
        // The defect made the ENTIRE meal unsaveable, not just the edited item.
        val saved = ServingNutritionNormalizer.validateBeforeSave(meal)
        assertEquals(2, saved.items.size)
        assertEquals(150.0, saved.items[1].calories, 1e-9)
    }

    @Test
    fun `an unvalidated item can also be corrected`() {
        // Manual, barcode and library entries arrive without a serving validation.
        val manual = AnalyzedFoodItem(
            name = "Manual food",
            quantity = 100.0,
            unit = "g",
            gramsEquivalent = 100.0,
            calories = 100.0,
            proteinGrams = 5.0,
            carbohydrateGrams = 10.0,
            fatGrams = 2.0,
            isEstimate = false,
        )
        val corrected = ServingNutritionNormalizer.applyUserNutrientCorrection(
            item = manual,
            calories = 250.0,
            proteinGrams = 6.0,
            carbohydrateGrams = 11.0,
            fatGrams = 3.0,
        )
        assertEquals(250.0, corrected.calories, 1e-9)
        assertTrue(corrected.isEstimate)
        ServingNutritionNormalizer.validateBeforeSave(FoodAnalysis(listOf(corrected)))
    }

    @Test
    fun `an absurd correction on an unvalidated item is still refused`() {
        val manual = AnalyzedFoodItem(
            name = "Manual food",
            quantity = 100.0,
            unit = "g",
            gramsEquivalent = 100.0,
            calories = 100.0,
            proteinGrams = 5.0,
            carbohydrateGrams = 10.0,
            fatGrams = 2.0,
            isEstimate = false,
        )
        assertThrows(AiValidationException::class.java) {
            ServingNutritionNormalizer.applyUserNutrientCorrection(
                item = manual,
                calories = 9_999.0,
                proteinGrams = 5.0,
                carbohydrateGrams = 10.0,
                fatGrams = 2.0,
            )
        }
    }
}
