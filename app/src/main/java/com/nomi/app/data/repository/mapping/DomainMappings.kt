package com.nomi.app.data.repository.mapping

import com.nomi.app.data.local.entity.NutritionPlanEntity
import com.nomi.app.data.local.entity.NutritionValues
import com.nomi.app.data.local.entity.UserProfileEntity
import com.nomi.app.data.preferences.PersistedOnboardingDraft
import com.nomi.app.data.repository.CompleteOnboardingRequest
import com.nomi.app.domain.ActivityLevel
import com.nomi.app.domain.EnergyCalculation
import com.nomi.app.domain.EnergySex
import com.nomi.app.domain.GoalType
import com.nomi.app.domain.MacroTargets
import com.nomi.app.domain.Nutrition
import com.nomi.app.domain.NutritionPlan
import com.nomi.app.domain.OnboardingDraft
import com.nomi.app.domain.ProgressRate
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

fun Nutrition.toPersistenceValues(): NutritionValues = NutritionValues(
    caloriesKcal = caloriesKcal,
    proteinGrams = proteinGrams,
    carbohydrateGrams = carbsGrams,
    fatGrams = fatGrams,
    fiberGrams = fiberGrams,
    sugarGrams = sugarGrams,
    saturatedFatGrams = saturatedFatGrams,
    sodiumMilligrams = sodiumMilligrams,
)

fun NutritionValues.toDomainNutrition(): Nutrition = Nutrition(
    caloriesKcal = caloriesKcal,
    proteinGrams = proteinGrams,
    carbsGrams = carbohydrateGrams,
    fatGrams = fatGrams,
    fiberGrams = fiberGrams ?: 0.0,
    sugarGrams = sugarGrams ?: 0.0,
    saturatedFatGrams = saturatedFatGrams ?: 0.0,
    sodiumMilligrams = sodiumMilligrams ?: 0.0,
)

fun OnboardingDraft.toPersistedDraft(
    currentStep: Int,
    updatedAtEpochMillis: Long,
): PersistedOnboardingDraft = PersistedOnboardingDraft(
    currentStep = currentStep,
    dateOfBirth = dateOfBirth?.toString(),
    energyCalculationSex = energySex?.name,
    heightCm = heightCm,
    currentWeightKg = currentWeightKg,
    goalType = goalType?.name,
    targetWeightKg = targetWeightKg,
    activityLevel = activityLevel?.name,
    progressionRate = progressRate?.name,
    manualCalorieTargetKcal = customCalorieTarget?.toDouble(),
    updatedAtEpochMillis = updatedAtEpochMillis,
)

fun PersistedOnboardingDraft.toDomainDraft(): OnboardingDraft = OnboardingDraft(
    dateOfBirth = dateOfBirth?.let(LocalDate::parse),
    energySex = energyCalculationSex?.let { EnergySex.valueOf(it) },
    heightCm = heightCm,
    currentWeightKg = currentWeightKg,
    goalType = goalType?.let { GoalType.valueOf(it) },
    targetWeightKg = targetWeightKg,
    activityLevel = activityLevel?.let { ActivityLevel.valueOf(it) },
    progressRate = progressionRate?.let { ProgressRate.valueOf(it) },
    customCalorieTarget = manualCalorieTargetKcal?.roundToInt(),
)

fun NutritionPlan.toEntity(
    effectiveFrom: LocalDate,
    createdAtEpochMillis: Long,
    version: Int = 0,
    changeReason: String = "onboarding",
): NutritionPlanEntity = NutritionPlanEntity(
    version = version,
    effectiveFromLocalDate = effectiveFrom.toString(),
    calculationMethod = if (calculation.isManualTarget && calculation.bmrKcal == null) {
        "manual"
    } else {
        "mifflin_st_jeor"
    },
    activityMultiplier = calculation.activityMultiplier,
    bmrKcal = calculation.bmrKcal,
    maintenanceKcal = calculation.tdeeKcal,
    goalAdjustmentKcal = calculation.goalAdjustmentKcal,
    calorieTargetKcal = calculation.caloriesKcal.toDouble(),
    proteinTargetGrams = macroTargets.proteinGrams,
    carbohydrateTargetGrams = macroTargets.carbsGrams,
    fatTargetGrams = macroTargets.fatGrams,
    calorieTargetCustom = isCalorieCustomized,
    proteinTargetCustom = areMacrosCustomized,
    carbohydrateTargetCustom = areMacrosCustomized,
    fatTargetCustom = areMacrosCustomized,
    changeReason = changeReason,
    createdAtEpochMillis = createdAtEpochMillis,
)

/** Rehydrates the plan with its version-effective profile snapshot. */
fun NutritionPlanEntity.toDomainPlan(profile: UserProfileEntity): NutritionPlan {
    val effectiveDate = LocalDate.parse(effectiveFromLocalDate)
    val age = Period.between(LocalDate.parse(profile.dateOfBirth), effectiveDate).years
    val appliedAdjustment = goalAdjustmentKcal ?: 0.0
    val weeklyChange = appliedAdjustment * 7.0 / 7_700.0
    val weeksToGoal = profile.targetWeightKg?.let { target ->
        val distance = abs(target - profile.startingWeightKg)
        val weeklyMagnitude = abs(weeklyChange)
        if (distance == 0.0) 0.0 else if (weeklyMagnitude > 0.0) distance / weeklyMagnitude else null
    }
    return NutritionPlan(
        goalType = GoalType.valueOf(profile.goalType),
        currentWeightKg = profile.startingWeightKg,
        targetWeightKg = profile.targetWeightKg,
        calculation = EnergyCalculation(
            ageYears = age,
            bmrKcal = bmrKcal,
            activityMultiplier = activityMultiplier,
            tdeeKcal = maintenanceKcal,
            requestedGoalAdjustmentKcal = appliedAdjustment,
            goalAdjustmentKcal = appliedAdjustment,
            exactCaloriesKcal = calorieTargetKcal,
            caloriesKcal = calorieTargetKcal.roundToInt(),
            expectedWeeklyWeightChangeKg = weeklyChange,
            safetyLimitApplied = false,
            isManualTarget = calculationMethod == "manual" || calorieTargetCustom,
        ),
        macroTargets = MacroTargets(
            proteinGrams = proteinTargetGrams,
            carbsGrams = carbohydrateTargetGrams,
            fatGrams = fatTargetGrams,
        ),
        estimatedWeeksToGoal = weeksToGoal,
        isCalorieCustomized = calorieTargetCustom,
        areMacrosCustomized = proteinTargetCustom || carbohydrateTargetCustom || fatTargetCustom,
    )
}

/** Maps the calculation/UI hand-off into the repository's atomic onboarding command. */
fun OnboardingDraft.toCompleteOnboardingRequest(
    plan: NutritionPlan,
    completedAtEpochMillis: Long,
    localDate: LocalDate,
    zoneId: ZoneId,
): CompleteOnboardingRequest {
    val birthDate = requireNotNull(dateOfBirth) { "Date of birth is required" }
    val weight = requireNotNull(currentWeightKg) { "Current weight is required" }
    val goal = requireNotNull(goalType) { "Goal is required" }
    val activity = requireNotNull(activityLevel) { "Activity level is required" }
    val energySelection = requireNotNull(energySex) { "Energy calculation selection is required" }
    return CompleteOnboardingRequest(
        profile = UserProfileEntity(
            dateOfBirth = birthDate.toString(),
            energyCalculationSex = energySelection.name,
            heightCm = heightCm,
            startingWeightKg = weight,
            goalType = goal.name,
            targetWeightKg = targetWeightKg,
            activityLevel = activity.name,
            progressionRate = progressRate?.name,
            onboardingCompleted = true,
            createdAtEpochMillis = completedAtEpochMillis,
            updatedAtEpochMillis = completedAtEpochMillis,
        ),
        plan = plan.toEntity(
            effectiveFrom = localDate,
            createdAtEpochMillis = completedAtEpochMillis,
        ),
        localDate = localDate.toString(),
        completedAtEpochMillis = completedAtEpochMillis,
        zoneId = zoneId.id,
    )
}
