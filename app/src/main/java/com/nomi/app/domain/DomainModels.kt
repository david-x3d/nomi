package com.nomi.app.domain

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

enum class EnergySex {
    MALE,
    FEMALE,
    MANUAL,
}

enum class GoalType {
    LOSE,
    MAINTAIN,
    GAIN,
}

enum class ActivityLevel(val multiplier: Double) {
    SEDENTARY(1.2),
    LIGHTLY_ACTIVE(1.375),
    ACTIVE(1.55),
    VERY_ACTIVE(1.725),
}

enum class ProgressRate {
    GENTLE,
    MODERATE,
    FASTER,
    CUSTOM,
}

/**
 * Mutable onboarding state expressed as an immutable value. All values use metric units internally.
 * A positive [customWeeklyChangeKg] is a magnitude; [GoalType] supplies the direction.
 */
data class OnboardingDraft(
    val dateOfBirth: LocalDate? = null,
    val energySex: EnergySex? = null,
    val heightCm: Double? = null,
    val currentWeightKg: Double? = null,
    val goalType: GoalType? = null,
    val targetWeightKg: Double? = null,
    val activityLevel: ActivityLevel? = null,
    val progressRate: ProgressRate? = null,
    val customCalorieTarget: Int? = null,
    val customWeeklyChangeKg: Double? = null,
) {
    /** Backwards-friendly descriptive alias for callers rendering the manual-energy branch. */
    val manualDailyEnergyKcal: Int?
        get() = customCalorieTarget
}

/**
 * Exact nutrition values. Rounding belongs at the display or persistence boundary.
 *
 * Sugar and saturated fat are components of carbohydrate and fat respectively, and sodium is
 * carried in milligrams because that is the unit every label prints it in. None of the three is
 * added to [macroCaloriesKcal]: their energy is already counted by the macro they belong to.
 */
data class Nutrition(
    val caloriesKcal: Double,
    val proteinGrams: Double,
    val carbsGrams: Double,
    val fatGrams: Double,
    val fiberGrams: Double = 0.0,
    val sugarGrams: Double = 0.0,
    val saturatedFatGrams: Double = 0.0,
    val sodiumMilligrams: Double = 0.0,
) {
    init {
        require(caloriesKcal.isFinite() && caloriesKcal >= 0.0) {
            "Calories must be a finite, non-negative value."
        }
        require(proteinGrams.isFinite() && proteinGrams >= 0.0) {
            "Protein must be a finite, non-negative value."
        }
        require(carbsGrams.isFinite() && carbsGrams >= 0.0) {
            "Carbohydrates must be a finite, non-negative value."
        }
        require(fatGrams.isFinite() && fatGrams >= 0.0) {
            "Fat must be a finite, non-negative value."
        }
        require(fiberGrams.isFinite() && fiberGrams >= 0.0) {
            "Fiber must be a finite, non-negative value."
        }
        require(sugarGrams.isFinite() && sugarGrams >= 0.0) {
            "Sugar must be a finite, non-negative value."
        }
        require(saturatedFatGrams.isFinite() && saturatedFatGrams >= 0.0) {
            "Saturated fat must be a finite, non-negative value."
        }
        require(sodiumMilligrams.isFinite() && sodiumMilligrams >= 0.0) {
            "Sodium must be a finite, non-negative value."
        }
    }

    val carbohydrateGrams: Double
        get() = carbsGrams

    val macroCaloriesKcal: Double
        get() = proteinGrams * PROTEIN_KCAL_PER_GRAM +
            carbsGrams * CARBOHYDRATE_KCAL_PER_GRAM +
            fatGrams * FAT_KCAL_PER_GRAM

    companion object {
        const val PROTEIN_KCAL_PER_GRAM = 4.0
        const val CARBOHYDRATE_KCAL_PER_GRAM = 4.0
        const val FAT_KCAL_PER_GRAM = 9.0

        val ZERO = Nutrition(
            caloriesKcal = 0.0,
            proteinGrams = 0.0,
            carbsGrams = 0.0,
            fatGrams = 0.0,
        )
    }
}

/** Exact macro recommendation. Integer display values are derived without altering the calculation. */
data class MacroTargets(
    val proteinGrams: Double,
    val carbsGrams: Double,
    val fatGrams: Double,
) {
    init {
        require(proteinGrams.isFinite() && proteinGrams >= 0.0) {
            "Protein target must be a finite, non-negative value."
        }
        require(carbsGrams.isFinite() && carbsGrams >= 0.0) {
            "Carbohydrate target must be a finite, non-negative value."
        }
        require(fatGrams.isFinite() && fatGrams >= 0.0) {
            "Fat target must be a finite, non-negative value."
        }
    }

    val carbohydrateGrams: Double
        get() = carbsGrams

    val caloriesKcal: Double
        get() = proteinGrams * Nutrition.PROTEIN_KCAL_PER_GRAM +
            carbsGrams * Nutrition.CARBOHYDRATE_KCAL_PER_GRAM +
            fatGrams * Nutrition.FAT_KCAL_PER_GRAM

    val displayProteinGrams: Int
        get() = proteinGrams.roundToInt()

    val displayCarbsGrams: Int
        get() = carbsGrams.roundToInt()

    val displayFatGrams: Int
        get() = fatGrams.roundToInt()
}

/** The auditable energy math shown by the plan-reveal explanation. */
data class EnergyCalculation(
    val ageYears: Int,
    val bmrKcal: Double?,
    val activityMultiplier: Double?,
    val tdeeKcal: Double?,
    val requestedGoalAdjustmentKcal: Double,
    val goalAdjustmentKcal: Double,
    val exactCaloriesKcal: Double,
    val caloriesKcal: Int,
    val expectedWeeklyWeightChangeKg: Double,
    val safetyLimitApplied: Boolean,
    val isManualTarget: Boolean,
) {
    init {
        require(ageYears >= 0) { "Age cannot be negative." }
        require(bmrKcal == null || bmrKcal.isFinite() && bmrKcal > 0.0) {
            "BMR must be positive when present."
        }
        require(activityMultiplier == null || activityMultiplier.isFinite() && activityMultiplier > 0.0) {
            "Activity multiplier must be positive when present."
        }
        require(tdeeKcal == null || tdeeKcal.isFinite() && tdeeKcal > 0.0) {
            "TDEE must be positive when present."
        }
        require(requestedGoalAdjustmentKcal.isFinite()) { "Requested adjustment must be finite." }
        require(goalAdjustmentKcal.isFinite()) { "Applied adjustment must be finite." }
        require(exactCaloriesKcal.isFinite() && exactCaloriesKcal > 0.0) {
            "Exact calorie target must be positive."
        }
        require(caloriesKcal > 0) { "Displayed calorie target must be positive." }
        require(expectedWeeklyWeightChangeKg.isFinite()) { "Expected weekly change must be finite." }
    }

    val maintenanceKcal: Double?
        get() = tdeeKcal
}

/**
 * A complete calculated plan. Convenience properties intentionally expose display-ready integer targets,
 * while [calculation] and [macroTargets] retain exact values.
 */
data class NutritionPlan(
    val goalType: GoalType,
    val currentWeightKg: Double,
    val targetWeightKg: Double?,
    val calculation: EnergyCalculation,
    val macroTargets: MacroTargets,
    val estimatedWeeksToGoal: Double?,
    val isCalorieCustomized: Boolean = false,
    val areMacrosCustomized: Boolean = false,
) {
    init {
        require(currentWeightKg.isFinite() && currentWeightKg > 0.0) {
            "Current weight must be positive."
        }
        require(targetWeightKg == null || targetWeightKg.isFinite() && targetWeightKg > 0.0) {
            "Target weight must be positive when present."
        }
        require(estimatedWeeksToGoal == null || estimatedWeeksToGoal.isFinite() && estimatedWeeksToGoal >= 0.0) {
            "Estimated weeks to goal cannot be negative."
        }
    }

    val ageYears: Int
        get() = calculation.ageYears

    val bmrKcal: Double?
        get() = calculation.bmrKcal

    val activityMultiplier: Double?
        get() = calculation.activityMultiplier

    val tdeeKcal: Double?
        get() = calculation.tdeeKcal

    val maintenanceKcal: Double?
        get() = calculation.tdeeKcal

    val requestedGoalAdjustmentKcal: Double
        get() = calculation.requestedGoalAdjustmentKcal

    val goalAdjustmentKcal: Double
        get() = calculation.goalAdjustmentKcal

    val exactCaloriesKcal: Double
        get() = calculation.exactCaloriesKcal

    val caloriesKcal: Int
        get() = calculation.caloriesKcal

    val expectedWeeklyWeightChangeKg: Double
        get() = calculation.expectedWeeklyWeightChangeKg

    val safetyLimitApplied: Boolean
        get() = calculation.safetyLimitApplied

    val isManualTarget: Boolean
        get() = calculation.isManualTarget

    val proteinGrams: Int
        get() = macroTargets.displayProteinGrams

    val carbsGrams: Int
        get() = macroTargets.displayCarbsGrams

    val carbohydrateGrams: Int
        get() = carbsGrams

    val fatGrams: Int
        get() = macroTargets.displayFatGrams

    /**
     * Applies explicit user overrides without mutating the original recommendation. If [weeklyChangeKg]
     * is omitted and maintenance is known, the trend is recomputed from the overridden calorie target.
     */
    fun withOverrides(
        caloriesKcal: Int = this.caloriesKcal,
        proteinGrams: Int = this.proteinGrams,
        carbsGrams: Int = this.carbsGrams,
        fatGrams: Int = this.fatGrams,
        weeklyChangeKg: Double? = null,
    ): NutritionPlan {
        require(caloriesKcal > 0) { "Calorie override must be positive." }
        require(proteinGrams >= 0 && carbsGrams >= 0 && fatGrams >= 0) {
            "Macro overrides cannot be negative."
        }
        require(weeklyChangeKg == null || weeklyChangeKg.isFinite()) {
            "Weekly change override must be finite."
        }

        val overriddenAdjustment = calculation.tdeeKcal?.let { caloriesKcal - it } ?: 0.0
        val overriddenWeeklyChange = weeklyChangeKg
            ?: calculation.tdeeKcal?.let { overriddenAdjustment * 7.0 / EnergyCalculator.KCAL_PER_KILOGRAM }
            ?: calculation.expectedWeeklyWeightChangeKg
        val overriddenWeeks = targetWeightKg?.let { target ->
            val remaining = abs(target - currentWeightKg)
            val weeklyMagnitude = abs(overriddenWeeklyChange)
            if (remaining == 0.0) 0.0 else if (weeklyMagnitude > 0.0) remaining / weeklyMagnitude else null
        }

        return copy(
            calculation = calculation.copy(
                requestedGoalAdjustmentKcal = overriddenAdjustment,
                goalAdjustmentKcal = overriddenAdjustment,
                exactCaloriesKcal = caloriesKcal.toDouble(),
                caloriesKcal = caloriesKcal,
                expectedWeeklyWeightChangeKg = overriddenWeeklyChange,
                safetyLimitApplied = false,
                isManualTarget = true,
            ),
            macroTargets = MacroTargets(
                proteinGrams = proteinGrams.toDouble(),
                carbsGrams = carbsGrams.toDouble(),
                fatGrams = fatGrams.toDouble(),
            ),
            estimatedWeeksToGoal = overriddenWeeks,
            isCalorieCustomized = caloriesKcal != this.caloriesKcal || isCalorieCustomized,
            areMacrosCustomized = proteinGrams != this.proteinGrams ||
                carbsGrams != this.carbsGrams ||
                fatGrams != this.fatGrams ||
                areMacrosCustomized,
        )
    }
}
