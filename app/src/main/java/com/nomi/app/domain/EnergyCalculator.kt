package com.nomi.app.domain

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

object EnergyCalculator {
    const val KCAL_PER_KILOGRAM = 7_700.0

    fun calculate(draft: OnboardingDraft, today: LocalDate): NutritionPlan {
        val dateOfBirth = requireNotNull(draft.dateOfBirth) { "Date of birth is required." }
        val ageYears = AgeCalculator.calculate(dateOfBirth, today)
        require(ageYears <= MAX_CALCULATION_AGE_YEARS) {
            "Age must be $MAX_CALCULATION_AGE_YEARS years or less for this estimate."
        }

        val energySex = requireNotNull(draft.energySex) { "Energy calculation selection is required." }
        val weightKg = requirePositive(draft.currentWeightKg, "Current weight")
        val goalType = requireNotNull(draft.goalType) { "Goal is required." }
        validateTargetWeight(goalType, weightKg, draft.targetWeightKg)

        val calculation = if (energySex == EnergySex.MANUAL) {
            manualCalculation(draft, ageYears, goalType)
        } else {
            automaticCalculation(draft, ageYears, energySex, weightKg, goalType)
        }

        val macros = MacroCalculator.calculate(
            weightKg = weightKg,
            goalType = goalType,
            calorieTargetKcal = calculation.caloriesKcal,
        )
        val estimatedWeeks = draft.targetWeightKg?.let { target ->
            WeightTrendEstimator.estimatedWeeksToTarget(
                currentWeightKg = weightKg,
                targetWeightKg = target,
                weeklyChangeKg = calculation.expectedWeeklyWeightChangeKg,
            )
        }

        return NutritionPlan(
            goalType = goalType,
            currentWeightKg = weightKg,
            targetWeightKg = draft.targetWeightKg,
            calculation = calculation,
            macroTargets = macros,
            estimatedWeeksToGoal = estimatedWeeks,
            isCalorieCustomized = draft.customCalorieTarget != null,
        )
    }

    /** Exact Mifflin-St Jeor resting-energy estimate. */
    fun mifflinStJeorBmr(
        weightKg: Double,
        heightCm: Double,
        ageYears: Int,
        energySex: EnergySex,
    ): Double {
        require(weightKg.isFinite() && weightKg > 0.0) { "Weight must be positive." }
        require(heightCm.isFinite() && heightCm > 0.0) { "Height must be positive." }
        require(ageYears >= 0) { "Age cannot be negative." }
        require(energySex != EnergySex.MANUAL) { "Manual energy selection has no BMR constant." }

        val sexConstant = when (energySex) {
            EnergySex.MALE -> 5.0
            EnergySex.FEMALE -> -161.0
            EnergySex.MANUAL -> error("Handled above")
        }
        val result = 10.0 * weightKg + 6.25 * heightCm - 5.0 * ageYears + sexConstant
        require(result > 0.0) { "Inputs do not produce a valid BMR estimate." }
        return result
    }

    fun maintenanceCalories(bmrKcal: Double, activityLevel: ActivityLevel): Double {
        require(bmrKcal.isFinite() && bmrKcal > 0.0) { "BMR must be positive." }
        return bmrKcal * activityLevel.multiplier
    }

    private fun automaticCalculation(
        draft: OnboardingDraft,
        ageYears: Int,
        energySex: EnergySex,
        weightKg: Double,
        goalType: GoalType,
    ): EnergyCalculation {
        val heightCm = requirePositive(draft.heightCm, "Height")
        val activityLevel = requireNotNull(draft.activityLevel) { "Activity level is required." }
        val bmrKcal = mifflinStJeorBmr(weightKg, heightCm, ageYears, energySex)
        val tdeeKcal = maintenanceCalories(bmrKcal, activityLevel)
        val requestedAdjustment = requestedAdjustment(draft, goalType)

        val automaticAdjustment = when (goalType) {
            GoalType.LOSE -> requestedAdjustment.coerceAtLeast(
                -min(MAX_DAILY_DEFICIT_KCAL, tdeeKcal * MAX_DEFICIT_FRACTION),
            )
            GoalType.MAINTAIN -> 0.0
            GoalType.GAIN -> requestedAdjustment.coerceAtMost(
                min(MAX_DAILY_SURPLUS_KCAL, tdeeKcal * MAX_SURPLUS_FRACTION),
            )
        }
        val sexFloor = when (energySex) {
            EnergySex.MALE -> MALE_AUTOMATIC_FLOOR_KCAL
            EnergySex.FEMALE -> FEMALE_AUTOMATIC_FLOOR_KCAL
            EnergySex.MANUAL -> error("Manual calculation uses a separate path.")
        }
        val minimumAutomaticTarget = when (goalType) {
            GoalType.LOSE -> min(sexFloor, tdeeKcal)
            GoalType.MAINTAIN,
            GoalType.GAIN,
            -> tdeeKcal
        }
        val boundedAutomaticTarget = (tdeeKcal + automaticAdjustment)
            .coerceAtLeast(minimumAutomaticTarget)

        val customTarget = draft.customCalorieTarget
        if (customTarget != null) requireManualTarget(customTarget)
        val exactTarget = customTarget?.toDouble() ?: boundedAutomaticTarget
        val appliedAdjustment = exactTarget - tdeeKcal
        val isSafetyLimited = customTarget == null &&
            abs(appliedAdjustment - requestedAdjustment) > CALCULATION_EPSILON
        val displayedTarget = customTarget ?: roundForDisplay(exactTarget)

        return EnergyCalculation(
            ageYears = ageYears,
            bmrKcal = bmrKcal,
            activityMultiplier = activityLevel.multiplier,
            tdeeKcal = tdeeKcal,
            requestedGoalAdjustmentKcal = requestedAdjustment,
            goalAdjustmentKcal = appliedAdjustment,
            exactCaloriesKcal = exactTarget,
            caloriesKcal = displayedTarget,
            expectedWeeklyWeightChangeKg = WeightTrendEstimator.expectedWeeklyChangeKg(
                appliedAdjustment,
            ),
            safetyLimitApplied = isSafetyLimited,
            isManualTarget = customTarget != null,
        )
    }

    private fun manualCalculation(
        draft: OnboardingDraft,
        ageYears: Int,
        goalType: GoalType,
    ): EnergyCalculation {
        val manualTarget = requireNotNull(draft.customCalorieTarget) {
            "A custom calorie target is required when calories are set manually."
        }
        requireManualTarget(manualTarget)

        val weeklyMagnitude = draft.customWeeklyChangeKg?.also {
            require(it.isFinite() && it >= 0.0) {
                "Custom weekly change must be a finite, non-negative magnitude."
            }
        }
        val weeklyChange = when (goalType) {
            GoalType.LOSE -> -(weeklyMagnitude ?: 0.0)
            GoalType.MAINTAIN -> 0.0
            GoalType.GAIN -> weeklyMagnitude ?: 0.0
        }

        return EnergyCalculation(
            ageYears = ageYears,
            bmrKcal = null,
            activityMultiplier = null,
            tdeeKcal = null,
            requestedGoalAdjustmentKcal = 0.0,
            goalAdjustmentKcal = 0.0,
            exactCaloriesKcal = manualTarget.toDouble(),
            caloriesKcal = manualTarget,
            expectedWeeklyWeightChangeKg = weeklyChange,
            safetyLimitApplied = false,
            isManualTarget = true,
        )
    }

    private fun requestedAdjustment(draft: OnboardingDraft, goalType: GoalType): Double {
        if (goalType == GoalType.MAINTAIN) return 0.0
        val progressRate = requireNotNull(draft.progressRate) {
            "Progress rate is required for a weight-change goal."
        }
        val magnitude = when (progressRate) {
            ProgressRate.GENTLE -> if (goalType == GoalType.LOSE) 250.0 else 150.0
            ProgressRate.MODERATE -> if (goalType == GoalType.LOSE) 450.0 else 250.0
            ProgressRate.FASTER -> if (goalType == GoalType.LOSE) 650.0 else 350.0
            ProgressRate.CUSTOM -> {
                val weeklyChange = requireNotNull(draft.customWeeklyChangeKg) {
                    "Custom weekly change is required for a custom progress rate."
                }
                require(weeklyChange.isFinite() && weeklyChange > 0.0) {
                    "Custom weekly change must be a finite, positive magnitude."
                }
                weeklyChange * KCAL_PER_KILOGRAM / DAYS_PER_WEEK
            }
        }
        return if (goalType == GoalType.LOSE) -magnitude else magnitude
    }

    private fun validateTargetWeight(
        goalType: GoalType,
        currentWeightKg: Double,
        targetWeightKg: Double?,
    ) {
        when (goalType) {
            GoalType.LOSE -> {
                val target = requirePositive(targetWeightKg, "Target weight")
                require(target < currentWeightKg) {
                    "A loss target must be below current weight."
                }
            }
            GoalType.MAINTAIN -> Unit
            GoalType.GAIN -> {
                val target = requirePositive(targetWeightKg, "Target weight")
                require(target > currentWeightKg) {
                    "A gain target must be above current weight."
                }
            }
        }
    }

    private fun requirePositive(value: Double?, name: String): Double {
        val present = requireNotNull(value) { "$name is required." }
        require(present.isFinite() && present > 0.0) { "$name must be a finite, positive value." }
        return present
    }

    private fun requireManualTarget(target: Int) {
        require(target in MIN_MANUAL_TARGET_KCAL..MAX_MANUAL_TARGET_KCAL) {
            "Manual calorie target must be between $MIN_MANUAL_TARGET_KCAL and $MAX_MANUAL_TARGET_KCAL kcal."
        }
    }

    private fun roundForDisplay(value: Double): Int =
        (value / DISPLAY_ROUNDING_INCREMENT).roundToInt() * DISPLAY_ROUNDING_INCREMENT

    private const val DAYS_PER_WEEK = 7.0
    private const val MAX_CALCULATION_AGE_YEARS = 120
    private const val MAX_DEFICIT_FRACTION = 0.25
    private const val MAX_SURPLUS_FRACTION = 0.20
    private const val MAX_DAILY_DEFICIT_KCAL = 750.0
    private const val MAX_DAILY_SURPLUS_KCAL = 500.0
    private const val MALE_AUTOMATIC_FLOOR_KCAL = 1_500.0
    private const val FEMALE_AUTOMATIC_FLOOR_KCAL = 1_200.0
    private const val MIN_MANUAL_TARGET_KCAL = 800
    private const val MAX_MANUAL_TARGET_KCAL = 10_000
    private const val DISPLAY_ROUNDING_INCREMENT = 10
    private const val CALCULATION_EPSILON = 1e-9
}
