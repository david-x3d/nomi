package com.nomi.app.ui.logging

import com.nomi.app.ai.model.AnalyzedFoodItem
import com.nomi.app.ai.model.FoodAnalysis
import com.nomi.app.ai.model.NutritionVerificationStatus
import com.nomi.app.ui.localization.NomiLanguage
import com.nomi.app.ui.localization.NomiTranslations

/**
 * Turns a researched multi-part order into the one line the person originally intended to log.
 * Nutrition is summed without another model request; only the visible label and serving wrapper
 * change. A single researched food is already concise and therefore stays untouched.
 */
internal fun FoodAnalysis.asSingleLoggedMeal(language: NomiLanguage): FoodAnalysis {
    if (items.size <= 1) return this

    val unitWord = NomiTranslations.translate("meal", language)
    val displayName = groupedMealTitle(items.map(AnalyzedFoodItem::name), language)

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
                calorieExplanation = items.mapNotNull { item ->
                    item.calorieExplanation?.trim()?.takeIf(String::isNotBlank)?.let { explanation ->
                        "${item.name}: $explanation"
                    }
                }.joinToString(" ").takeIf(String::isNotBlank),
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

/**
 * Names every researched part instead of inventing a generic menu label.
 *
 * The provider already returns corrected product names. This function only tidies whitespace,
 * gives the sentence its initial capital and joins the names naturally in the selected language.
 * Quantities remain on the individual stored items and are still visible in meal details.
 */
internal fun groupedMealTitle(names: List<String>, language: NomiLanguage): String {
    val cleaned = names.mapNotNull { raw ->
        raw.trim()
            .replace(Regex("\\s+"), " ")
            .takeIf(String::isNotBlank)
    }
    if (cleaned.isEmpty()) return NomiTranslations.translate("Meal", language)
    val sentenceStart = cleaned.first().replaceFirstChar { initial ->
        if (initial.isLowerCase()) initial.titlecase(language.locale) else initial.toString()
    }
    if (cleaned.size == 1) return sentenceStart

    val (withWord, andWord) = when (language) {
        NomiLanguage.ENGLISH -> "with" to "and"
        NomiLanguage.GERMAN -> "mit" to "und"
        NomiLanguage.SPANISH -> "con" to "y"
        NomiLanguage.FRENCH -> "avec" to "et"
        NomiLanguage.ITALIAN -> "con" to "e"
        NomiLanguage.DUTCH -> "met" to "en"
        NomiLanguage.PORTUGUESE -> "com" to "e"
        NomiLanguage.ALBANIAN -> "me" to "dhe"
        NomiLanguage.SWEDISH -> "med" to "och"
        NomiLanguage.TURKISH -> "ile" to "ve"
    }
    if (cleaned.size == 2) return "$sentenceStart $withWord ${cleaned[1]}"

    val middle = cleaned.subList(1, cleaned.lastIndex).joinToString(", ")
    return "$sentenceStart $withWord $middle $andWord ${cleaned.last()}"
}

private fun List<AnalyzedFoodItem>.sumOptional(
    selector: (AnalyzedFoodItem) -> Double?,
): Double? = mapNotNull(selector).takeIf(List<Double>::isNotEmpty)?.sum()
