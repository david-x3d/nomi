package com.nomi.app.ui.onboarding

import com.nomi.app.domain.model.GoalType
import com.nomi.app.domain.model.NutritionPlan
import java.util.Locale

internal fun NutritionPlan.toEditorState(): PlanEditorState = PlanEditorState(
    calories = caloriesKcal.toString(),
    proteinGrams = proteinGrams.toString(),
    carbohydrateGrams = carbohydrateGrams.toString(),
    fatGrams = fatGrams.toString(),
    weeklyChangeKg = kotlin.math.abs(expectedWeeklyWeightChangeKg).formatForInput(),
)

internal fun NutritionPlan.withUiOverrides(
    calories: Int,
    proteinGrams: Int,
    carbohydrateGrams: Int,
    fatGrams: Int,
    weeklyChangeKg: Double,
): NutritionPlan {
    val weeklyMagnitudeUnchanged = goalType == GoalType.MAINTAIN ||
        kotlin.math.abs(kotlin.math.abs(expectedWeeklyWeightChangeKg) - weeklyChangeKg) < 0.0051
    val displayValuesUnchanged = calories == caloriesKcal &&
        proteinGrams == this.proteinGrams &&
        carbohydrateGrams == this.carbohydrateGrams &&
        fatGrams == this.fatGrams
    if (displayValuesUnchanged && weeklyMagnitudeUnchanged) return this

    val signedWeeklyChange = when (goalType) {
        GoalType.LOSE -> -kotlin.math.abs(weeklyChangeKg)
        GoalType.GAIN -> kotlin.math.abs(weeklyChangeKg)
        GoalType.MAINTAIN -> null
    }
    return withOverrides(
        caloriesKcal = calories,
        proteinGrams = proteinGrams,
        carbsGrams = carbohydrateGrams,
        fatGrams = fatGrams,
        weeklyChangeKg = signedWeeklyChange,
    )
}

private fun Double.formatForInput(): String = String.format(Locale.US, "%.2f", this)
    .trimEnd('0')
    .trimEnd('.')

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
internal operator fun IntRange.contains(value: Int?): Boolean = value != null && value in this

internal operator fun Double?.compareTo(other: Double?): Int = when {
    this == null && other == null -> 0
    this == null -> -1
    other == null -> 1
    else -> this.compareTo(other)
}
