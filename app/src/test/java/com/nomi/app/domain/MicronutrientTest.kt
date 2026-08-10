package com.nomi.app.domain

import com.nomi.app.data.preferences.MicronutrientPreferences
import com.nomi.app.data.preferences.MicronutrientSetting
import com.nomi.app.data.preferences.enabledMicronutrients
import com.nomi.app.data.preferences.resolvedTarget
import com.nomi.app.data.preferences.settingFor
import com.nomi.app.data.preferences.with
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MicronutrientTest {
    /**
     * The stored defaults are literals so old preferences keep deserializing to a stable value.
     * This is the pin that keeps that duplication honest.
     */
    @Test
    fun `stored defaults match each nutrient's reference intake`() {
        val defaults = MicronutrientPreferences()
        Micronutrient.entries.forEach { nutrient ->
            assertEquals(
                "Default target for $nutrient drifted from its reference intake",
                nutrient.referenceDailyAmount,
                defaults.settingFor(nutrient).dailyTarget,
                0.0,
            )
        }
    }

    @Test
    fun `nothing is tracked until the user turns something on`() {
        assertTrue(MicronutrientPreferences().enabledMicronutrients().isEmpty())
        assertFalse(MicronutrientPreferences().anyEnabled)
    }

    @Test
    fun `enabled nutrients are listed in presentation order`() {
        val preferences = MicronutrientPreferences()
            .with(Micronutrient.SODIUM, MicronutrientSetting(enabled = true, dailyTarget = 1_500.0))
            .with(Micronutrient.FIBER, MicronutrientSetting(enabled = true, dailyTarget = 35.0))

        assertEquals(
            listOf(Micronutrient.FIBER, Micronutrient.SODIUM),
            preferences.enabledMicronutrients(),
        )
    }

    @Test
    fun `an unusable stored target falls back to the reference intake`() {
        Micronutrient.entries.forEach { nutrient ->
            val broken = listOf(0.0, -5.0, Double.NaN, nutrient.maximumTarget * 2)
            broken.forEach { value ->
                assertEquals(
                    "Target $value for $nutrient should have fallen back",
                    nutrient.referenceDailyAmount,
                    MicronutrientSetting(enabled = true, dailyTarget = value).resolvedTarget(nutrient),
                    0.0,
                )
            }
        }
    }

    @Test
    fun `a usable stored target is kept exactly`() {
        assertEquals(
            35.0,
            MicronutrientSetting(enabled = true, dailyTarget = 35.0).resolvedTarget(Micronutrient.FIBER),
            0.0,
        )
    }

    @Test
    fun `sodium is the only nutrient stored in milligrams`() {
        assertEquals(MicronutrientUnit.MILLIGRAMS, Micronutrient.SODIUM.storageUnit)
        listOf(Micronutrient.FIBER, Micronutrient.SUGAR, Micronutrient.SATURATED_FAT).forEach {
            assertEquals(MicronutrientUnit.GRAMS, it.storageUnit)
        }
    }

    @Test
    fun `only fiber is a target to reach rather than a ceiling`() {
        assertFalse(Micronutrient.FIBER.isLimit)
        assertTrue(Micronutrient.SUGAR.isLimit)
        assertTrue(Micronutrient.SATURATED_FAT.isLimit)
        assertTrue(Micronutrient.SODIUM.isLimit)
    }

    @Test
    fun `scaling a portion scales every micronutrient with it`() {
        val meal = Nutrition(
            caloriesKcal = 600.0,
            proteinGrams = 24.0,
            carbsGrams = 70.0,
            fatGrams = 26.0,
            fiberGrams = 8.0,
            sugarGrams = 12.0,
            saturatedFatGrams = 9.0,
            sodiumMilligrams = 1_400.0,
        )

        val half = NutritionScaler.scale(meal, 0.5)

        assertEquals(4.0, half.fiberGrams, 1e-12)
        assertEquals(6.0, half.sugarGrams, 1e-12)
        assertEquals(4.5, half.saturatedFatGrams, 1e-12)
        assertEquals(700.0, half.sodiumMilligrams, 1e-12)
    }

    @Test
    fun `summing a day adds every micronutrient`() {
        val first = Nutrition(
            caloriesKcal = 100.0,
            proteinGrams = 1.0,
            carbsGrams = 20.0,
            fatGrams = 1.0,
            sugarGrams = 18.0,
            sodiumMilligrams = 40.0,
        )
        val second = Nutrition(
            caloriesKcal = 250.0,
            proteinGrams = 9.0,
            carbsGrams = 30.0,
            fatGrams = 10.0,
            fiberGrams = 3.0,
            sugarGrams = 4.0,
            saturatedFatGrams = 5.0,
            sodiumMilligrams = 900.0,
        )

        val total = NutritionScaler.sum(listOf(first, second))

        assertEquals(3.0, total.fiberGrams, 1e-12)
        assertEquals(22.0, total.sugarGrams, 1e-12)
        assertEquals(5.0, total.saturatedFatGrams, 1e-12)
        assertEquals(940.0, total.sodiumMilligrams, 1e-12)
    }

    /**
     * The failure this guards against is the reason the portion split exists at all: a halved
     * portion that comes back with numbers that are not half.
     */
    @Test
    fun `a halved portion whose micronutrients did not halve is rejected`() {
        val original = Nutrition(
            caloriesKcal = 600.0,
            proteinGrams = 24.0,
            carbsGrams = 70.0,
            fatGrams = 26.0,
            sugarGrams = 12.0,
        )

        val result = PortionChangeValidator.validate(
            currentNutrition = original,
            originalQuantity = 1.0,
            newQuantity = 0.5,
            originalUnit = "serving",
            proposedNutrition = NutritionScaler.scale(original, 0.5).copy(sugarGrams = 11.0),
        )

        assertFalse(result.isValid)
        assertTrue(PortionValidationIssue.INCONSISTENT_NUTRITION in result.issues)
    }

    @Test
    fun `a correctly halved portion passes`() {
        val original = Nutrition(
            caloriesKcal = 600.0,
            proteinGrams = 24.0,
            carbsGrams = 70.0,
            fatGrams = 26.0,
            sugarGrams = 12.0,
            sodiumMilligrams = 1_400.0,
        )

        val result = PortionChangeValidator.validate(
            currentNutrition = original,
            originalQuantity = 1.0,
            newQuantity = 0.5,
            originalUnit = "serving",
            proposedNutrition = NutritionScaler.scale(original, 0.5),
        )

        assertTrue(result.issues.toString(), result.isValid)
        assertEquals(6.0, result.expectedNutrition!!.sugarGrams, 1e-12)
        assertEquals(700.0, result.expectedNutrition!!.sodiumMilligrams, 1e-12)
    }
}
