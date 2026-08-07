package com.nomi.app.domain

object NutritionScaler {
    fun scale(nutrition: Nutrition, multiplier: Double): Nutrition {
        require(multiplier.isFinite() && multiplier >= 0.0) {
            "Nutrition multiplier must be finite and non-negative."
        }
        return Nutrition(
            caloriesKcal = nutrition.caloriesKcal * multiplier,
            proteinGrams = nutrition.proteinGrams * multiplier,
            carbsGrams = nutrition.carbsGrams * multiplier,
            fatGrams = nutrition.fatGrams * multiplier,
            fiberGrams = nutrition.fiberGrams * multiplier,
        )
    }

    fun scaleFromQuantity(
        nutrition: Nutrition,
        originalQuantity: Double,
        newQuantity: Double,
    ): Nutrition {
        require(originalQuantity.isFinite() && originalQuantity > 0.0) {
            "Original quantity must be finite and positive."
        }
        require(newQuantity.isFinite() && newQuantity >= 0.0) {
            "New quantity must be finite and non-negative."
        }
        return scale(nutrition, newQuantity / originalQuantity)
    }

    fun fromPer100Grams(nutritionPer100Grams: Nutrition, grams: Double): Nutrition {
        require(grams.isFinite() && grams >= 0.0) { "Grams must be finite and non-negative." }
        return scale(nutritionPer100Grams, grams / 100.0)
    }

    fun sum(values: Iterable<Nutrition>): Nutrition = values.fold(Nutrition.ZERO) { total, next ->
        Nutrition(
            caloriesKcal = total.caloriesKcal + next.caloriesKcal,
            proteinGrams = total.proteinGrams + next.proteinGrams,
            carbsGrams = total.carbsGrams + next.carbsGrams,
            fatGrams = total.fatGrams + next.fatGrams,
            fiberGrams = total.fiberGrams + next.fiberGrams,
        )
    }
}
