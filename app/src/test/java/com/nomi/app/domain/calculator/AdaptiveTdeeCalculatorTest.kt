package com.nomi.app.domain.calculator

import com.nomi.app.data.local.entity.NutritionPlanEntity
import com.nomi.app.data.local.entity.UserProfileEntity
import com.nomi.app.data.local.entity.WeightEntryEntity
import com.nomi.app.data.local.model.DailyNutritionTotals
import com.nomi.app.data.preferences.AdaptiveTdeePreferences
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveTdeeCalculatorTest {
    private val today = LocalDate.of(2026, 11, 19)

    private val profile = UserProfileEntity(
        dateOfBirth = "1995-05-01",
        energyCalculationSex = "MALE",
        heightCm = 180.0,
        startingWeightKg = 80.0,
        goalType = "MAINTAIN",
        activityLevel = "MODERATE",
        onboardingCompleted = true,
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 1L,
    )

    private val plan = NutritionPlanEntity(
        version = 1,
        effectiveFromLocalDate = "2026-10-01",
        calculationMethod = "MIFFLIN_ST_JEOR",
        maintenanceKcal = 2_400.0,
        goalAdjustmentKcal = 0.0,
        calorieTargetKcal = 2_400.0,
        proteinTargetGrams = 150.0,
        carbohydrateTargetGrams = 250.0,
        fatTargetGrams = 80.0,
        createdAtEpochMillis = 1L,
    )

    private fun weight(daysAgo: Long, kg: Double): WeightEntryEntity = WeightEntryEntity(
        weightKg = kg,
        localDate = today.minusDays(daysAgo).toString(),
        measuredAtEpochMillis = 1_700_000_000_000L - daysAgo * 86_400_000L,
        zoneId = "Europe/Berlin",
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 1L,
    )

    private fun intake(daysAgo: Long, kcal: Double): DailyNutritionTotals = DailyNutritionTotals(
        localDate = today.minusDays(daysAgo).toString(),
        caloriesKcal = kcal,
        proteinGrams = 140.0,
        carbohydrateGrams = 240.0,
        fatGrams = 70.0,
    )

    @Test
    fun firstWeighInSpikeIsDampenedByBidirectionalTrend() {
        // Stable 80.0 kg every day except a +1.5 kg water-retention spike on the very first day.
        val weights = (20L downTo 1L).map { daysAgo ->
            weight(daysAgo, if (daysAgo == 20L) 81.5 else 80.0)
        }
        val nutrition = (20L downTo 1L).map { intake(it, 2_400.0) }

        val estimate = AdaptiveTdeeCalculator.evaluate(
            today = today,
            profile = profile,
            plan = plan,
            weights = weights,
            nutrition = nutrition,
            preferences = AdaptiveTdeePreferences(),
        )

        assertTrue(estimate is AdaptiveTdeeEstimate.Ready)
        val ready = estimate as AdaptiveTdeeEstimate.Ready
        // Start weight should be smoothed near 80.33 kg (not raw 81.5 kg), keeping TDEE close to 2400.
        assertTrue("Expected smoothed start near 80.3, was ${ready.trendWeightStartKg}", ready.trendWeightStartKg < 80.6)
        assertTrue("Expected TDEE within 200 kcal of 2400, was ${ready.estimatedTdeeKcal}", ready.estimatedTdeeKcal in 2_300..2_600)
    }

    @Test
    fun intakeAveragePrefersDaysAlignedWithWeightSpan() {
        // User ate 3000 kcal in days 27..15 (no weigh-ins), then 2000 kcal in days 14..1 with
        // stable 80.0 kg weigh-ins during days 14..1.
        val weights = (14L downTo 1L).map { weight(it, 80.0) }
        val nutrition = (27L downTo 15L).map { intake(it, 3_000.0) } +
            (14L downTo 1L).map { intake(it, 2_000.0) }

        val estimate = AdaptiveTdeeCalculator.evaluate(
            today = today,
            profile = profile,
            plan = plan,
            weights = weights,
            nutrition = nutrition,
            preferences = AdaptiveTdeePreferences(),
        )

        assertTrue(estimate is AdaptiveTdeeEstimate.Ready)
        val ready = estimate as AdaptiveTdeeEstimate.Ready
        assertEquals(2_000, ready.averageIntakeKcal)
        assertEquals(2_000, ready.estimatedTdeeKcal)
    }

    @Test
    fun insufficientDataReturnsCounts() {
        val estimate = AdaptiveTdeeCalculator.evaluate(
            today = today,
            profile = profile,
            plan = plan,
            weights = listOf(weight(5, 80.0), weight(1, 79.8)),
            nutrition = listOf(intake(3, 2_200.0)),
            preferences = AdaptiveTdeePreferences(),
        )

        assertTrue(estimate is AdaptiveTdeeEstimate.InsufficientData)
        val insufficient = estimate as AdaptiveTdeeEstimate.InsufficientData
        assertEquals(1, insufficient.loggedDays)
        assertEquals(2, insufficient.weighInDays)
        assertEquals(4, insufficient.spanDays)
    }
}
