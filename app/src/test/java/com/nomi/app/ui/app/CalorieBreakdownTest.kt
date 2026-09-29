package com.nomi.app.ui.app

import com.nomi.app.data.share.SHARED_INPUT_METHOD
import com.nomi.app.ui.localization.NomiLanguage
import com.nomi.app.ui.localization.NomiTranslations
import com.nomi.app.ui.today.MealCategory
import com.nomi.app.ui.today.TodayFoodEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime

/**
 * "Where do the calories come from?" is computed on the phone from the entry itself, so every
 * figure on it has to be arithmetic the reader could redo.
 */
class CalorieBreakdownTest {

    @Test
    fun `each macronutrient carries its grams times its energy factor`() {
        // 10 g protein, 20 g carbs, 10 g fat: 40 + 80 + 90 = 210 kcal.
        val breakdown = entry(calories = 210.0, protein = 10.0, carbs = 20.0, fat = 10.0)
            .calorieBreakdown()

        assertEquals(210.0, breakdown.macroKcal, 0.001)
        assertEquals(
            listOf(Macronutrient.FAT, Macronutrient.CARBOHYDRATES, Macronutrient.PROTEIN),
            breakdown.macros.map { it.macronutrient },
        )
        assertEquals(90.0 / 210.0, breakdown.macros.first().share, 0.0001)
        assertEquals(1.0, breakdown.macros.sumOf { it.share }, 0.0001)
        assertEquals(Macronutrient.FAT, breakdown.dominant)
        assertNull("a total that matches its macros has nothing to explain", breakdown.unexplainedKcal)
    }

    @Test
    fun `a macronutrient with no grams is left out rather than shown as zero percent`() {
        val breakdown = entry(calories = 40.0, protein = 0.0, carbs = 10.0, fat = 0.0).calorieBreakdown()

        assertEquals(listOf(Macronutrient.CARBOHYDRATES), breakdown.macros.map { it.macronutrient })
    }

    @Test
    fun `a gap larger than label rounding is reported with its sign`() {
        // Macros say 210 kcal; a label with alcohol or fibre lists 260.
        val higher = entry(calories = 260.0, protein = 10.0, carbs = 20.0, fat = 10.0).calorieBreakdown()
        assertEquals(50.0, higher.unexplainedKcal!!, 0.001)

        val lower = entry(calories = 180.0, protein = 10.0, carbs = 20.0, fat = 10.0).calorieBreakdown()
        assertEquals(-30.0, lower.unexplainedKcal!!, 0.001)
    }

    @Test
    fun `a gap within label rounding is not reported`() {
        // 6 kcal is under the 10 kcal floor even though it is over 5 % of a small total.
        val breakdown = entry(calories = 46.0, protein = 0.0, carbs = 10.0, fat = 0.0).calorieBreakdown()
        assertNull(breakdown.unexplainedKcal)
    }

    @Test
    fun `energy density uses the eaten grams and is banded`() {
        val cheese = entry(calories = 120.0, fat = 10.0, protein = 7.0, grams = 30.0).calorieBreakdown()
        assertEquals(400.0, cheese.energyDensityPer100!!, 0.001)
        assertEquals("g", cheese.densityUnit)
        assertEquals(EnergyDensityBand.MEDIUM, cheese.densityBand)

        val butter = entry(calories = 75.0, fat = 8.3, grams = 10.0).calorieBreakdown()
        assertEquals(EnergyDensityBand.HIGH, butter.densityBand)

        val cucumber = entry(calories = 24.0, carbs = 4.0, grams = 200.0).calorieBreakdown()
        assertEquals(EnergyDensityBand.VERY_LOW, cucumber.densityBand)
    }

    @Test
    fun `a drink logged in millilitres is dense per 100 ml`() {
        val juice = entry(calories = 110.0, carbs = 26.0, amount = 250.0, unit = "ml").calorieBreakdown()
        assertEquals(44.0, juice.energyDensityPer100!!, 0.001)
        assertEquals("ml", juice.densityUnit)
    }

    @Test
    fun `a portion with no known weight has no density`() {
        val slice = entry(calories = 285.0, carbs = 36.0, amount = 1.0, unit = "slice").calorieBreakdown()
        assertNull(slice.energyDensityPer100)
        assertNull(slice.densityBand)
    }

    @Test
    fun `the calculation line shows the source basis scaled to the portion`() {
        // 150 g of something the page lists per 100 g, logged at 375 kcal: 250 kcal per 100 g.
        val scaling = entry(
            calories = 375.0,
            carbs = 60.0,
            grams = 150.0,
            sourceServingQuantity = 100.0,
            sourceServingUnit = "g",
        ).calorieBreakdown().scaling

        assertNotNull(scaling)
        assertEquals(250.0, scaling!!.basisKcal, 0.001)
        assertEquals(100.0, scaling.basisQuantity, 0.0)
        assertEquals(150.0, scaling.portionQuantity, 0.0)
    }

    @Test
    fun `no calculation line is offered when the units cannot be compared`() {
        val scaling = entry(
            calories = 90.0,
            carbs = 20.0,
            amount = 1.0,
            unit = "piece",
            sourceServingQuantity = 100.0,
            sourceServingUnit = "ml",
        ).calorieBreakdown().scaling

        assertNull(scaling)
    }

    @Test
    fun `origin follows what was stored with the log`() {
        assertEquals(NutritionOrigin.SHARED, entry(inputMethod = SHARED_INPUT_METHOD, estimated = true).nutritionOrigin())
        assertEquals(NutritionOrigin.MANUAL, entry(sourceName = "Manual entry").nutritionOrigin())
        assertEquals(NutritionOrigin.OPEN_FOOD_FACTS, entry(sourceName = "Open Food Facts").nutritionOrigin())
        assertEquals(NutritionOrigin.FOOD_LIBRARY, entry(sourceName = "Nomi favorite").nutritionOrigin())
        assertEquals(
            NutritionOrigin.PUBLISHED_SOURCE,
            entry(sourceName = "Skyr Natur", sourceUrl = "https://www.arla.de/skyr").nutritionOrigin(),
        )
        assertEquals(NutritionOrigin.AI_ESTIMATE, entry(sourceName = "Estimate", estimated = true).nutritionOrigin())
        assertEquals(NutritionOrigin.SAVED_VALUES, entry().nutritionOrigin())
    }

    @Test
    fun `a label photo is recognised in whichever language it was logged in`() {
        val german = NomiTranslations.translate("Nutrition label photo", NomiLanguage.GERMAN)
        assertEquals(NutritionOrigin.LABEL_PHOTO, entry(sourceName = german).nutritionOrigin())
        assertEquals(NutritionOrigin.LABEL_PHOTO, entry(sourceName = "Nutrition label photo").nutritionOrigin())
    }

    private fun entry(
        calories: Double = 100.0,
        protein: Double = 0.0,
        carbs: Double = 0.0,
        fat: Double = 0.0,
        amount: Double = 100.0,
        unit: String = "g",
        grams: Double? = null,
        sourceName: String? = null,
        sourceUrl: String? = null,
        sourceServingQuantity: Double? = null,
        sourceServingUnit: String? = null,
        estimated: Boolean = false,
        inputMethod: String? = null,
    ) = TodayFoodEntry(
        id = 1,
        name = "Food",
        amountText = "$amount $unit",
        calories = calories,
        proteinGrams = protein,
        carbohydrateGrams = carbs,
        fatGrams = fat,
        mealCategory = MealCategory.LUNCH,
        time = LocalTime.NOON,
        isEstimated = estimated,
        amount = amount,
        unit = unit,
        grams = grams,
        sourceName = sourceName,
        sourceUrl = sourceUrl,
        sourceServingQuantity = sourceServingQuantity,
        sourceServingUnit = sourceServingUnit,
        inputMethod = inputMethod,
    )
}
