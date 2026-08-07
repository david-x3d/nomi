package com.nomi.app.domain

import java.time.LocalDate
import kotlin.math.ceil

object WeightTrendEstimator {
    fun expectedWeeklyChangeKg(dailyEnergyDifferenceKcal: Double): Double {
        require(dailyEnergyDifferenceKcal.isFinite()) { "Energy difference must be finite." }
        return dailyEnergyDifferenceKcal * DAYS_PER_WEEK / EnergyCalculator.KCAL_PER_KILOGRAM
    }

    fun estimatedWeeksToTarget(
        currentWeightKg: Double,
        targetWeightKg: Double,
        weeklyChangeKg: Double,
    ): Double? {
        require(currentWeightKg.isFinite() && currentWeightKg > 0.0) {
            "Current weight must be positive."
        }
        require(targetWeightKg.isFinite() && targetWeightKg > 0.0) {
            "Target weight must be positive."
        }
        require(weeklyChangeKg.isFinite()) { "Weekly change must be finite." }

        val remainingChange = targetWeightKg - currentWeightKg
        if (remainingChange == 0.0) return 0.0
        if (weeklyChangeKg == 0.0 || remainingChange * weeklyChangeKg <= 0.0) return null
        return remainingChange / weeklyChangeKg
    }

    fun estimatedGoalDate(
        today: LocalDate,
        currentWeightKg: Double,
        targetWeightKg: Double,
        weeklyChangeKg: Double,
    ): LocalDate? {
        val weeks = estimatedWeeksToTarget(currentWeightKg, targetWeightKg, weeklyChangeKg)
            ?: return null
        return today.plusDays(ceil(weeks * DAYS_PER_WEEK).toLong())
    }

    private const val DAYS_PER_WEEK = 7.0
}
