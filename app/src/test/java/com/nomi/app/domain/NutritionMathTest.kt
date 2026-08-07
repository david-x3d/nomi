package com.nomi.app.domain

import java.time.LocalDate
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionMathTest {
    @Test
    fun `macro recommendations exactly reconcile to calories for every goal`() {
        GoalType.entries.forEach { goal ->
            val macros = MacroCalculator.calculate(
                weightKg = 70.0,
                goalType = goal,
                calorieTargetKcal = 1_820.0,
            )

            assertEquals(1_820.0, macros.caloriesKcal, 1e-9)
            assertTrue(macros.proteinGrams > 0.0)
            assertTrue(macros.carbsGrams > 0.0)
            assertTrue(macros.fatGrams > 0.0)
            val displayedMacroCalories = macros.displayProteinGrams * 4 +
                macros.displayCarbsGrams * 4 + macros.displayFatGrams * 9
            assertTrue(abs(displayedMacroCalories - 1_820) <= 9)
        }
    }

    @Test
    fun `macro calculator remains non-negative for high weight and low calories`() {
        val macros = MacroCalculator.calculate(
            weightKg = 200.0,
            goalType = GoalType.LOSE,
            calorieTargetKcal = 800.0,
        )

        assertEquals(800.0, macros.caloriesKcal, 1e-9)
        assertTrue(macros.proteinGrams >= 0.0)
        assertTrue(macros.carbsGrams >= 0.0)
        assertTrue(macros.fatGrams >= 0.0)
    }

    @Test
    fun `weight and height conversions round trip without display loss`() {
        val pounds = UnitConverter.kilogramsToPounds(70.0)
        assertEquals(154.3235835294143, pounds, 1e-12)
        assertEquals(70.0, UnitConverter.poundsToKilograms(pounds), 1e-12)

        val imperialHeight = UnitConverter.centimetersToFeetAndInches(180.0)
        assertEquals(5, imperialHeight.feet)
        assertEquals(10.866141732283467, imperialHeight.inches, 1e-12)
        assertEquals(
            180.0,
            UnitConverter.feetAndInchesToCentimeters(imperialHeight.feet, imperialHeight.inches),
            1e-12,
        )
    }

    @Test
    fun `mass and volume converters use exact unit factors`() {
        assertEquals(
            453.59237,
            UnitConverter.convertMass(
                1.0,
                UnitConverter.MassUnit.POUND,
                UnitConverter.MassUnit.GRAM,
            ),
            1e-9,
        )
        assertEquals(
            14.78676478125,
            UnitConverter.convertVolume(
                1.0,
                UnitConverter.VolumeUnit.TABLESPOON,
                UnitConverter.VolumeUnit.MILLILITER,
            ),
            1e-12,
        )
    }

    @Test
    fun `negative and non-finite units are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            UnitConverter.kgToLb(-1.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            UnitConverter.cmToInches(Double.NaN)
        }
        assertThrows(IllegalArgumentException::class.java) {
            UnitConverter.feetAndInchesToCm(5, 12.0)
        }
    }

    @Test
    fun `nutrition scaling preserves exact proportional math`() {
        val twoSlices = Nutrition(
            caloriesKcal = 176.0,
            proteinGrams = 7.0,
            carbsGrams = 32.0,
            fatGrams = 2.0,
            fiberGrams = 4.0,
        )
        val threeSlices = NutritionScaler.scaleFromQuantity(twoSlices, 2.0, 3.0)

        assertEquals(264.0, threeSlices.caloriesKcal, 0.0)
        assertEquals(10.5, threeSlices.proteinGrams, 0.0)
        assertEquals(48.0, threeSlices.carbsGrams, 0.0)
        assertEquals(3.0, threeSlices.fatGrams, 0.0)
        assertEquals(6.0, threeSlices.fiberGrams, 0.0)
    }

    @Test
    fun `per-100g scaling and summation do not round internally`() {
        val per100 = Nutrition(123.4, 10.1, 11.2, 4.3, 2.5)
        val portion = NutritionScaler.fromPer100Grams(per100, 37.5)
        val total = NutritionScaler.sum(listOf(portion, portion))

        assertEquals(46.275, portion.caloriesKcal, 1e-12)
        assertEquals(92.55, total.caloriesKcal, 1e-12)
        assertEquals(7.575, total.proteinGrams, 1e-12)
        assertEquals(8.4, total.carbsGrams, 1e-12)
    }

    @Test
    fun `invalid nutrition and scaling values are rejected at domain boundary`() {
        assertThrows(IllegalArgumentException::class.java) {
            Nutrition(-1.0, 0.0, 0.0, 0.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NutritionScaler.scale(Nutrition.ZERO, -0.5)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NutritionScaler.scaleFromQuantity(Nutrition.ZERO, 0.0, 1.0)
        }
    }

    @Test
    fun `weight trend helper rejects movement in wrong direction`() {
        val weeklyChange = WeightTrendEstimator.expectedWeeklyChangeKg(-450.0)
        assertEquals(-450.0 * 7.0 / 7_700.0, weeklyChange, 1e-12)
        assertEquals(
            6.0 / -weeklyChange,
            WeightTrendEstimator.estimatedWeeksToTarget(70.0, 64.0, weeklyChange)!!,
            1e-9,
        )
        assertNull(WeightTrendEstimator.estimatedWeeksToTarget(70.0, 64.0, 0.2))

        val goalDate = WeightTrendEstimator.estimatedGoalDate(
            LocalDate.of(2025, 1, 1),
            70.0,
            64.0,
            weeklyChange,
        )
        assertTrue(goalDate!!.isAfter(LocalDate.of(2025, 1, 1)))
    }
}
