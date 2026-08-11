package com.nomi.app.ui.logging

import com.nomi.app.ai.model.AnalyzedFoodItem
import com.nomi.app.ai.model.FoodAnalysis
import com.nomi.app.ai.model.NutritionVerificationStatus
import java.util.Locale

/**
 * Turns a researched multi-part order into the one line the person originally intended to log.
 * Nutrition is summed without another model request; only the visible label and serving wrapper
 * change. A single researched food is already concise and therefore stays untouched.
 */
internal fun FoodAnalysis.asSingleLoggedMeal(useGerman: Boolean): FoodAnalysis {
    if (items.size <= 1) return this

    val commonBrand = items.mapNotNull { it.brand?.trim()?.takeIf(String::isNotBlank) }
        .groupingBy { it.lowercase(Locale.ROOT) }
        .eachCount()
        .maxByOrNull(Map.Entry<String, Int>::value)
        ?.key
        ?.let { normalized -> items.firstNotNullOf { item ->
            item.brand?.trim()?.takeIf { it.lowercase(Locale.ROOT) == normalized }
        } }
    val firstName = items.first().name.trim().ifBlank { if (useGerman) "Mahlzeit" else "Meal" }
    val baseName = commonBrand ?: firstName
    val suffix = if (useGerman) "Menü" else "Meal"
    val displayName = if (
        baseName.endsWith("menu", ignoreCase = true) ||
        baseName.endsWith("meal", ignoreCase = true) ||
        baseName.endsWith("menü", ignoreCase = true)
    ) {
        baseName
    } else {
        "$baseName $suffix"
    }

    val verification = when {
        items.all { it.verificationStatus == NutritionVerificationStatus.VERIFIED } ->
            NutritionVerificationStatus.VERIFIED
        items.any { it.verificationStatus == NutritionVerificationStatus.ESTIMATED } ->
            NutritionVerificationStatus.ESTIMATED
        else -> NutritionVerificationStatus.UNKNOWN
    }
    return FoodAnalysis(
        items = listOf(
            AnalyzedFoodItem(
                name = displayName,
                quantity = 1.0,
                unit = if (useGerman) "Menü" else "meal",
                gramsEquivalent = items.map(AnalyzedFoodItem::gramsEquivalent)
                    .takeIf { grams -> grams.all { it != null } }
                    ?.sumOf { requireNotNull(it) },
                calories = items.sumOf(AnalyzedFoodItem::calories),
                proteinGrams = items.sumOf(AnalyzedFoodItem::proteinGrams),
                carbohydrateGrams = items.sumOf(AnalyzedFoodItem::carbohydrateGrams),
                fatGrams = items.sumOf(AnalyzedFoodItem::fatGrams),
                fiberGrams = items.sumOptional(AnalyzedFoodItem::fiberGrams),
                sugarGrams = items.sumOptional(AnalyzedFoodItem::sugarGrams),
                saturatedFatGrams = items.sumOptional(AnalyzedFoodItem::saturatedFatGrams),
                sodiumMilligrams = items.sumOptional(AnalyzedFoodItem::sodiumMilligrams),
                sourceName = items.mapNotNull(AnalyzedFoodItem::sourceName)
                    .distinct()
                    .joinToString(" + ")
                    .takeIf(String::isNotBlank),
                supportingSourceUrls = items.flatMap { item ->
                    listOfNotNull(item.sourceUrl) + item.supportingSourceUrls
                }.distinct(),
                verificationStatus = verification,
                isEstimate = items.any(AnalyzedFoodItem::isEstimate),
                uncertaintyPercent = items.mapNotNull(AnalyzedFoodItem::uncertaintyPercent).maxOrNull(),
                assumptions = items.flatMap(AnalyzedFoodItem::assumptions).distinct(),
            ),
        ),
        overallConfidence = overallConfidence,
    )
}

private fun List<AnalyzedFoodItem>.sumOptional(
    selector: (AnalyzedFoodItem) -> Double?,
): Double? = mapNotNull(selector).takeIf(List<Double>::isNotEmpty)?.sum()
