package com.nomi.app.ui.logging

import com.nomi.app.ai.model.AnalyzedFoodItem
import com.nomi.app.ai.model.FoodAnalysis
import com.nomi.app.ai.model.NutritionVerificationStatus
import com.nomi.app.ui.localization.NomiLanguage
import com.nomi.app.ui.localization.NomiTranslations
import java.util.Locale

/**
 * Turns a researched multi-part order into the one line the person originally intended to log.
 * Nutrition is summed without another model request; only the visible label and serving wrapper
 * change. A single researched food is already concise and therefore stays untouched.
 */
internal fun FoodAnalysis.asSingleLoggedMeal(language: NomiLanguage): FoodAnalysis {
    if (items.size <= 1) return this

    val commonBrand = items.mapNotNull { it.brand?.trim()?.takeIf(String::isNotBlank) }
        .groupingBy { it.lowercase(Locale.ROOT) }
        .eachCount()
        .maxByOrNull(Map.Entry<String, Int>::value)
        ?.key
        ?.let { normalized -> items.firstNotNullOf { item ->
            item.brand?.trim()?.takeIf { it.lowercase(Locale.ROOT) == normalized }
        } }
    // The logged unit and the name suffix are the same word, so "1 Menü" reads as the thing the
    // row is called. Only its capitalization differs, which each language decides for itself.
    val unitWord = NomiTranslations.translate("meal", language)
    val suffix = unitWord.replaceFirstChar { it.uppercase(language.locale) }
    val firstName = items.first().name.trim()
        .ifBlank { NomiTranslations.translate("Meal", language) }
    val baseName = commonBrand ?: firstName
    // "McDonald's Menu" must not become "McDonald's Menu Meal". English and German are checked
    // alongside the active language because brand names carry those words in every market.
    val alreadyNamedAsMeal = listOf(suffix, "menu", "meal", "menü")
        .any { word -> baseName.endsWith(word, ignoreCase = true) }
    val displayName = if (alreadyNamedAsMeal) baseName else "$baseName $suffix"

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
                unit = unitWord,
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
                // The first researched page leads so the detail view still has a primary source
                // to show; the rest stay behind it in the citation list.
                sourceUrl = items.firstNotNullOfOrNull(AnalyzedFoodItem::sourceUrl),
                supportingSourceUrls = items.flatMap { item ->
                    listOfNotNull(item.sourceUrl) + item.supportingSourceUrls
                }.distinct(),
                // A combined meal is only as trustworthy as its least certain part.
                confidence = items.mapNotNull(AnalyzedFoodItem::confidence).minOrNull(),
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
