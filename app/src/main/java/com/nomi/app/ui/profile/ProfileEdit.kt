package com.nomi.app.ui.profile

import com.nomi.app.domain.ActivityLevel
import com.nomi.app.domain.EnergySex
import com.nomi.app.domain.GoalType
import com.nomi.app.domain.ProgressRate
import java.time.LocalDate
import java.time.Period

/** Parsed, persistence-ready profile values emitted by [ProfileSettingsScreen]. */
data class ProfileEdit(
    val dateOfBirth: String,
    val energyCalculationSex: String,
    val heightCm: Double?,
    val goalType: String,
    val targetWeightKg: Double?,
    val activityLevel: String,
    val progressionRate: String?,
    val keepCustomTargets: Boolean,
)

internal data class ProfileEditValidation(
    val dateOfBirthError: String? = null,
    val energySexError: String? = null,
    val heightError: String? = null,
    val goalError: String? = null,
    val targetWeightError: String? = null,
    val activityError: String? = null,
    val progressionRateError: String? = null,
) {
    val isValid: Boolean
        get() = listOf(
            dateOfBirthError,
            energySexError,
            heightError,
            goalError,
            targetWeightError,
            activityError,
            progressionRateError,
        ).all { it == null }

    val firstError: String?
        get() = listOfNotNull(
            dateOfBirthError,
            energySexError,
            heightError,
            goalError,
            targetWeightError,
            activityError,
            progressionRateError,
        ).firstOrNull()
}

internal fun ProfileEdit.validate(
    currentWeightKg: Double,
    today: LocalDate = LocalDate.now(),
): ProfileEditValidation {
    val birthDate = runCatching { LocalDate.parse(dateOfBirth.trim()) }.getOrNull()
    val energySex = enumValueOrNull<EnergySex>(energyCalculationSex)
    val goal = enumValueOrNull<GoalType>(goalType)
    val activity = enumValueOrNull<ActivityLevel>(activityLevel)
    val rate = progressionRate?.let { enumValueOrNull<ProgressRate>(it) }

    val dateError = when {
        birthDate == null -> "Use a valid date in YYYY-MM-DD format."
        birthDate.isAfter(today) -> "Date of birth can't be in the future."
        Period.between(birthDate, today).years !in 13..120 -> "Nomi supports ages 13 to 120."
        else -> null
    }
    val energyError = when {
        energySex == null -> "Choose an energy calculation option."
        energySex == EnergySex.MANUAL && !keepCustomTargets ->
            "Keep custom targets or choose an equation so Nomi can calculate the new plan."
        else -> null
    }
    val heightValidationError = when {
        heightCm == null && energySex == EnergySex.MANUAL -> null
        heightCm == null -> "Enter your height."
        !heightCm.isFinite() || heightCm !in 100.0..250.0 -> "Height must be between 100 and 250 cm."
        else -> null
    }
    val targetError = when {
        goal == null || goal == GoalType.MAINTAIN -> null
        targetWeightKg == null -> "Enter a target weight."
        !targetWeightKg.isFinite() || targetWeightKg !in 30.0..400.0 ->
            "Target weight must be between 30 and 400 kg."
        goal == GoalType.LOSE && targetWeightKg >= currentWeightKg ->
            "For weight loss, the target must be below your current weight."
        goal == GoalType.GAIN && targetWeightKg <= currentWeightKg ->
            "For weight gain, the target must be above your current weight."
        else -> null
    }
    val rateError = when {
        goal == null || goal == GoalType.MAINTAIN -> null
        rate == null -> "Choose a progression rate."
        rate == ProgressRate.CUSTOM -> "Choose a preset rate when recalculating from profile settings."
        goal == GoalType.GAIN && rate == ProgressRate.FASTER ->
            "Faster is available for loss plans only."
        else -> null
    }

    return ProfileEditValidation(
        dateOfBirthError = dateError,
        energySexError = energyError,
        heightError = heightValidationError,
        goalError = if (goal == null) "Choose a goal." else null,
        targetWeightError = targetError,
        activityError = if (activity == null) "Choose an activity level." else null,
        progressionRateError = rateError,
    )
}

private inline fun <reified T : Enum<T>> enumValueOrNull(value: String): T? =
    enumValues<T>().firstOrNull { it.name == value.trim().uppercase() }
