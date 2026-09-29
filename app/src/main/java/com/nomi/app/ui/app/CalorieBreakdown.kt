package com.nomi.app.ui.app

import com.nomi.app.data.share.SHARED_INPUT_METHOD
import com.nomi.app.ui.localization.NomiLanguage
import com.nomi.app.ui.localization.NomiTranslations
import com.nomi.app.ui.today.TodayFoodEntry
import java.util.Locale
import kotlin.math.abs

/**
 * Where an entry's numbers came from, as far as the stored entry can tell.
 *
 * Worked out from what was saved with the log rather than stored as its own column, so every
 * entry already in a diary gets an answer without a migration.
 */
internal enum class NutritionOrigin {
    /** A web page's nutrition table that the research cited and Nomi scaled. */
    PUBLISHED_SOURCE,
    OPEN_FOOD_FACTS,
    LABEL_PHOTO,
    AI_ESTIMATE,
    FOOD_LIBRARY,
    MANUAL,
    SHARED,
    /** Saved values with nothing recorded about where they were read. */
    SAVED_VALUES,
}

internal enum class Macronutrient(val kcalPerGram: Double) {
    PROTEIN(4.0),
    CARBOHYDRATES(4.0),
    FAT(9.0),
}

/** One macronutrient's part of the total: its grams, the energy they carry, and its share. */
internal data class MacroEnergy(
    val macronutrient: Macronutrient,
    val grams: Double,
    val kcal: Double,
    /** 0..1 of the energy the three macronutrients carry together. */
    val share: Double,
)

/**
 * Energy per 100 g (or 100 ml), banded the way public-health guidance does: below 0.6 kcal/g is
 * very low, up to 1.5 low, up to 4 medium, anything above high.
 */
internal enum class EnergyDensityBand { VERY_LOW, LOW, MEDIUM, HIGH }

/** How the source's printed values became this portion: "250 kcal per 100 g, scaled to 150 g". */
internal data class SourceScaling(
    val basisQuantity: Double,
    val basisUnit: String,
    val basisKcal: Double,
    val portionQuantity: Double,
    val portionUnit: String,
)

internal data class CalorieBreakdown(
    /** Largest energy share first. Macronutrients with no grams are left out. */
    val macros: List<MacroEnergy>,
    val macroKcal: Double,
    val loggedKcal: Double,
    /**
     * Logged total minus the macronutrient sum, reported only when it is too large to be label
     * rounding: fibre, alcohol and sugar alcohols carry energy the three macros do not.
     */
    val unexplainedKcal: Double?,
    val dominant: Macronutrient?,
    val energyDensityPer100: Double?,
    /** "g" or "ml", the unit [energyDensityPer100] is per hundred of. */
    val densityUnit: String?,
    val densityBand: EnergyDensityBand?,
    val scaling: SourceScaling?,
)

internal fun TodayFoodEntry.calorieBreakdown(): CalorieBreakdown {
    val parts = listOf(
        Macronutrient.PROTEIN to proteinGrams,
        Macronutrient.CARBOHYDRATES to carbohydrateGrams,
        Macronutrient.FAT to fatGrams,
    ).map { (macro, grams) ->
        val safeGrams = grams.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
        macro to safeGrams
    }
    val macroKcal = parts.sumOf { (macro, grams) -> grams * macro.kcalPerGram }
    val macros = parts
        .filter { (_, grams) -> grams > 0.0 }
        .map { (macro, grams) ->
            val kcal = grams * macro.kcalPerGram
            MacroEnergy(macro, grams, kcal, if (macroKcal > 0.0) kcal / macroKcal else 0.0)
        }
        .sortedByDescending(MacroEnergy::kcal)

    val logged = calories.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
    val gap = logged - macroKcal
    val unexplained = gap.takeIf {
        macroKcal > 0.0 && abs(it) >= UNEXPLAINED_MIN_KCAL && abs(it) >= logged * UNEXPLAINED_SHARE
    }

    val (densityAmount, densityUnit) = portionForDensity()
    val density = densityAmount
        ?.takeIf { it > 0.0 && logged > 0.0 }
        ?.let { logged / it * 100.0 }
    return CalorieBreakdown(
        macros = macros,
        macroKcal = macroKcal,
        loggedKcal = logged,
        unexplainedKcal = unexplained,
        dominant = macros.firstOrNull()?.macronutrient,
        energyDensityPer100 = density,
        densityUnit = densityUnit.takeIf { density != null },
        densityBand = density?.let(::densityBandFor),
        scaling = sourceScaling(logged),
    )
}

private fun densityBandFor(kcalPer100: Double): EnergyDensityBand = when {
    kcalPer100 < 60.0 -> EnergyDensityBand.VERY_LOW
    kcalPer100 < 150.0 -> EnergyDensityBand.LOW
    kcalPer100 <= 400.0 -> EnergyDensityBand.MEDIUM
    else -> EnergyDensityBand.HIGH
}

/** The eaten amount in grams or millilitres, when the entry knows it. A grouped meal does not. */
private fun TodayFoodEntry.portionForDensity(): Pair<Double?, String?> {
    if (groupItems.size > 1) return null to null
    grams?.takeIf { it.isFinite() && it > 0.0 }?.let { return it to "g" }
    val normalizedUnit = unit.trim().lowercase(Locale.ROOT)
    return when (normalizedUnit) {
        "g" -> amount.takeIf { it > 0.0 } to "g"
        "ml" -> amount.takeIf { it > 0.0 } to "ml"
        else -> null to null
    }
}

/**
 * Only offered when the portion can be put in the source's own unit, so the line is arithmetic
 * the reader can check rather than a conversion Nomi would have to explain.
 */
private fun TodayFoodEntry.sourceScaling(loggedKcal: Double): SourceScaling? {
    if (groupItems.size > 1) return null
    val basis = sourceServingQuantity?.takeIf { it.isFinite() && it > 0.0 } ?: return null
    val basisUnit = sourceServingUnit?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val normalizedBasis = basisUnit.lowercase(Locale.ROOT)
    val entryUnit = unit.trim().lowercase(Locale.ROOT)
    val portion = when {
        normalizedBasis == "g" -> grams?.takeIf { it > 0.0 } ?: amount.takeIf { entryUnit == "g" }
        normalizedBasis == entryUnit -> amount
        else -> null
    }?.takeIf { it.isFinite() && it > 0.0 } ?: return null
    val factor = portion / basis
    if (!factor.isFinite() || factor <= 0.0) return null
    return SourceScaling(
        basisQuantity = basis,
        basisUnit = basisUnit,
        basisKcal = loggedKcal / factor,
        portionQuantity = portion,
        portionUnit = basisUnit,
    )
}

internal fun TodayFoodEntry.nutritionOrigin(): NutritionOrigin {
    val source = sourceName?.trim().orEmpty()
    return when {
        inputMethod == SHARED_INPUT_METHOD -> NutritionOrigin.SHARED
        source == MANUAL_SOURCE || inputMethod == "manual" -> NutritionOrigin.MANUAL
        source.equals(OPEN_FOOD_FACTS_SOURCE, ignoreCase = true) -> NutritionOrigin.OPEN_FOOD_FACTS
        source in LIBRARY_SOURCES -> NutritionOrigin.FOOD_LIBRARY
        source in labelPhotoSourceNames -> NutritionOrigin.LABEL_PHOTO
        isEstimated -> NutritionOrigin.AI_ESTIMATE
        !sourceUrl.isNullOrBlank() || citedSourceUrls.isNotEmpty() -> NutritionOrigin.PUBLISHED_SOURCE
        else -> NutritionOrigin.SAVED_VALUES
    }
}

private const val MANUAL_SOURCE = "Manual entry"
private const val OPEN_FOOD_FACTS_SOURCE = "Open Food Facts"

/** Names Nomi writes for values it already had on the phone. */
private val LIBRARY_SOURCES = setOf(
    "Nomi food library",
    "Nomi favorite",
    "Local barcode cache",
    "Local barcode estimate",
    "Nomi local food cache",
)

/** A label photo's source name is written in the language chosen at the time, so any of them. */
private val labelPhotoSourceNames: Set<String> by lazy {
    NomiLanguage.entries.mapTo(mutableSetOf()) { language ->
        NomiTranslations.translate("Nutrition label photo", language)
    }
}

/** Ten kcal and five percent: below both, a gap is the label's own rounding. */
private const val UNEXPLAINED_MIN_KCAL = 10.0
private const val UNEXPLAINED_SHARE = 0.05
