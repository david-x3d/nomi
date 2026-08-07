package com.nomi.app.domain

import kotlin.math.max
import kotlin.math.min

object MacroCalculator {
    /**
     * Produces exact gram targets whose macro calories equal [calorieTargetKcal]. Protein is weight based,
     * fat is energy/weight based, and carbohydrates receive the exact remainder.
     */
    fun calculate(
        weightKg: Double,
        goalType: GoalType,
        calorieTargetKcal: Double,
    ): MacroTargets {
        require(weightKg.isFinite() && weightKg > 0.0) { "Weight must be positive." }
        require(calorieTargetKcal.isFinite() && calorieTargetKcal > 0.0) {
            "Calorie target must be positive."
        }

        val proteinPerKg = when (goalType) {
            GoalType.LOSE -> 1.8
            GoalType.MAINTAIN -> 1.6
            GoalType.GAIN -> 1.8
        }
        val desiredProteinGrams = weightKg * proteinPerKg
        val maximumProteinGrams = calorieTargetKcal * MAX_PROTEIN_ENERGY_FRACTION /
            Nutrition.PROTEIN_KCAL_PER_GRAM
        val proteinGrams = min(desiredProteinGrams, maximumProteinGrams)

        val desiredFatGrams = max(
            weightKg * MIN_FAT_GRAMS_PER_KG,
            calorieTargetKcal * FAT_ENERGY_FRACTION / Nutrition.FAT_KCAL_PER_GRAM,
        )
        val caloriesAfterProtein = calorieTargetKcal -
            proteinGrams * Nutrition.PROTEIN_KCAL_PER_GRAM
        val maximumFatGrams = max(
            0.0,
            (caloriesAfterProtein - calorieTargetKcal * MIN_CARBOHYDRATE_ENERGY_FRACTION) /
                Nutrition.FAT_KCAL_PER_GRAM,
        )
        val fatGrams = min(desiredFatGrams, maximumFatGrams)

        val carbohydrateCalories = calorieTargetKcal -
            proteinGrams * Nutrition.PROTEIN_KCAL_PER_GRAM -
            fatGrams * Nutrition.FAT_KCAL_PER_GRAM
        val carbsGrams = max(0.0, carbohydrateCalories / Nutrition.CARBOHYDRATE_KCAL_PER_GRAM)

        return MacroTargets(
            proteinGrams = proteinGrams,
            carbsGrams = carbsGrams,
            fatGrams = fatGrams,
        )
    }

    fun calculate(weightKg: Double, goalType: GoalType, calorieTargetKcal: Int): MacroTargets =
        calculate(weightKg, goalType, calorieTargetKcal.toDouble())

    private const val MAX_PROTEIN_ENERGY_FRACTION = 0.35
    private const val FAT_ENERGY_FRACTION = 0.27
    private const val MIN_CARBOHYDRATE_ENERGY_FRACTION = 0.25
    private const val MIN_FAT_GRAMS_PER_KG = 0.6
}
