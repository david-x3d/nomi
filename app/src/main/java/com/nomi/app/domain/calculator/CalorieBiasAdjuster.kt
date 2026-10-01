package com.nomi.app.domain.calculator

import com.nomi.app.ai.model.AnalyzedFoodItem
import com.nomi.app.ai.model.FoodAnalysis
import com.nomi.app.ai.model.ResearchNutritionBasis
import com.nomi.app.data.preferences.CalorieEstimateBias
import java.util.Locale

/**
 * Chooses where inside an estimate's plausible range the logged number lands.
 *
 * An estimated food is never one number, it is a range: "somewhere around 500 to 700 kcal". The
 * model reports how wide that range is, and this decides which end of it the user is tracked
 * against. Someone who would rather be surprised at the end of the day than fooled by it sets
 * the bias high; someone who wants the honest middle leaves it off.
 *
 * The arithmetic is deliberately here and not in a prompt. A model asked to "estimate a bit high"
 * drifts by an unknown amount that changes with every call; a factor applied in app code is the
 * same every time and can be explained to the user in one line.
 *
 * Nothing here touches a food whose nutrition came from a real source. A printed manufacturer
 * table is not uncertain, so there is no range to move within, and biasing it would replace a
 * fact with a preference.
 */
object CalorieBiasAdjuster {
    /**
     * How far toward the edge of the range each setting reaches. The outer settings go to the
     * full edge; the inner ones stop most of the way there, which keeps a visible difference
     * between "a bit high" and "as high as this food plausibly gets".
     */
    private fun CalorieEstimateBias.factor(): Double = when (this) {
        CalorieEstimateBias.STRONGLY_UNDERESTIMATE -> -1.0
        CalorieEstimateBias.UNDERESTIMATE -> -0.7
        CalorieEstimateBias.NONE -> 0.0
        CalorieEstimateBias.OVERESTIMATE -> 0.7
        CalorieEstimateBias.STRONGLY_OVERESTIMATE -> 1.0
    }

    /** Used when a provider reports no uncertainty of its own, so the setting still does something. */
    private const val DEFAULT_UNCERTAINTY_PERCENT = 15.0
    private const val MAX_UNCERTAINTY_PERCENT = 40.0

    fun apply(analysis: FoodAnalysis, bias: CalorieEstimateBias): FoodAnalysis {
        if (bias == CalorieEstimateBias.NONE) return analysis
        return analysis.copy(items = analysis.items.map { apply(it, bias) })
    }

    fun apply(item: AnalyzedFoodItem, bias: CalorieEstimateBias): AnalyzedFoodItem {
        if (bias == CalorieEstimateBias.NONE) return item
        // Only an estimate has a range to move within.
        if (!item.isEstimate) return item
        val scale = scaleFor(item.uncertaintyPercent, bias).coerceAtMost(physicalCeiling(item))
        if (scale == 1.0) return item
        return item.copy(
            calories = item.calories * scale,
            proteinGrams = item.proteinGrams * scale,
            carbohydrateGrams = item.carbohydrateGrams * scale,
            fatGrams = item.fatGrams * scale,
            fiberGrams = item.fiberGrams?.times(scale),
            sugarGrams = item.sugarGrams?.times(scale),
            saturatedFatGrams = item.saturatedFatGrams?.times(scale),
            sodiumMilligrams = item.sodiumMilligrams?.times(scale),
        )
    }

    /**
     * The largest factor that still leaves a food that can exist.
     *
     * The source serving weighs what it weighs, so its macros cannot be pushed past that weight
     * and its energy cannot be pushed past pure fat. Without this ceiling, "estimate high" turned
     * 100 g of oil into 110 g of fat per 100 g, the plausibility check refused it, and the user
     * could not log oil, butter or sugar at all while the setting was on.
     *
     * Never below 1.0: a value that is already impossible is the validator's to refuse, and a
     * downward bias has no ceiling to hit.
     */
    private fun physicalCeiling(item: AnalyzedFoodItem): Double {
        val capacityGrams = sourceCapacityGrams(item) ?: return Double.MAX_VALUE
        val macroGrams = item.proteinGrams + item.carbohydrateGrams + item.fatGrams
        val macroCeiling = if (macroGrams > 0.0) capacityGrams / macroGrams else Double.MAX_VALUE
        val energyCeiling = if (item.calories > 0.0) {
            capacityGrams * MAX_KCAL_PER_GRAM / item.calories
        } else {
            Double.MAX_VALUE
        }
        return minOf(macroCeiling, energyCeiling).coerceAtLeast(1.0)
    }

    /** Grams the source serving can hold; a volume is allowed the density of a heavy syrup. */
    private fun sourceCapacityGrams(item: AnalyzedFoodItem): Double? {
        when (item.nutritionBasis) {
            ResearchNutritionBasis.PER_100_G -> return 100.0
            ResearchNutritionBasis.PER_100_ML -> return 100.0 * MAX_GRAMS_PER_MILLILITER
            ResearchNutritionBasis.SOURCE_SERVING, null -> Unit
        }
        item.sourceServingGramsEquivalent?.takeIf { it.isFinite() && it > 0.0 }?.let { return it }
        val quantity = item.sourceServingQuantity?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        return when (item.sourceServingUnit?.trim()?.lowercase(Locale.ROOT)) {
            "g" -> quantity
            "kg" -> quantity * 1_000.0
            "ml" -> quantity * MAX_GRAMS_PER_MILLILITER
            "l" -> quantity * 1_000.0 * MAX_GRAMS_PER_MILLILITER
            else -> null
        }
    }

    /** Pure fat is 8.84 kcal per gram. */
    private const val MAX_KCAL_PER_GRAM = 9.0
    private const val MAX_GRAMS_PER_MILLILITER = 1.5

    /**
     * The factor every nutrient is multiplied by. Macros move with the calories rather than
     * being biased separately, so protein*4 + carbs*4 + fat*9 still reconciles with the energy
     * afterwards and the saved entry passes the same validation as an unbiased one.
     */
    fun scaleFor(uncertaintyPercent: Double?, bias: CalorieEstimateBias): Double {
        val uncertainty = uncertaintyPercent
            ?.takeIf { it.isFinite() && it > 0.0 }
            ?.coerceAtMost(MAX_UNCERTAINTY_PERCENT)
            ?: DEFAULT_UNCERTAINTY_PERCENT
        return 1.0 + bias.factor() * (uncertainty / 100.0)
    }
}
