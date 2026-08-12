package com.nomi.app.data.local.entity

import androidx.room.ColumnInfo

/**
 * Nutrition values use kcal, grams, and milligrams consistently throughout storage.
 * A containing entity decides whether these values describe 100 g or a concrete portion.
 */
data class NutritionValues(
    @ColumnInfo(name = "calories_kcal") val caloriesKcal: Double = 0.0,
    @ColumnInfo(name = "protein_grams") val proteinGrams: Double = 0.0,
    @ColumnInfo(name = "carbohydrate_grams") val carbohydrateGrams: Double = 0.0,
    @ColumnInfo(name = "fat_grams") val fatGrams: Double = 0.0,
    @ColumnInfo(name = "fiber_grams") val fiberGrams: Double? = null,
    @ColumnInfo(name = "sugar_grams") val sugarGrams: Double? = null,
    @ColumnInfo(name = "saturated_fat_grams") val saturatedFatGrams: Double? = null,
    @ColumnInfo(name = "sodium_milligrams") val sodiumMilligrams: Double? = null,
)

/**
 * Scales every present nutrient by [factor], which is exactly how a portion correction is
 * applied: nutrition is linear in the eaten amount, so a changed amount never needs new
 * research. Absent optional nutrients stay absent rather than becoming zero.
 */
fun NutritionValues.scaledBy(factor: Double): NutritionValues {
    require(factor.isFinite() && factor > 0.0) { "Portion factor must be finite and positive" }
    return NutritionValues(
        caloriesKcal = caloriesKcal * factor,
        proteinGrams = proteinGrams * factor,
        carbohydrateGrams = carbohydrateGrams * factor,
        fatGrams = fatGrams * factor,
        fiberGrams = fiberGrams?.times(factor),
        sugarGrams = sugarGrams?.times(factor),
        saturatedFatGrams = saturatedFatGrams?.times(factor),
        sodiumMilligrams = sodiumMilligrams?.times(factor),
    )
}

/**
 * Point-in-time source metadata. This is embedded in historical rows so later cache edits
 * cannot rewrite where a logged value came from.
 */
data class NutritionSourceSnapshot(
    @ColumnInfo(name = "kind") val kind: String = "manual",
    @ColumnInfo(name = "provider_name") val providerName: String? = null,
    @ColumnInfo(name = "display_name") val displayName: String? = null,
    @ColumnInfo(name = "external_id") val externalId: String? = null,
    @ColumnInfo(name = "url") val url: String? = null,
    /**
     * Every page the research actually cited, newline separated and starting with [url].
     *
     * Stored as one column rather than a child table because these are display-only citations
     * that are written once with the row and are never queried, joined, or edited afterwards.
     */
    @ColumnInfo(name = "cited_urls") val citedUrls: String? = null,
    /** How sure the provider was, 0..1. Null for manual entries, which make no claim. */
    @ColumnInfo(name = "confidence") val confidence: Double? = null,
    @ColumnInfo(name = "retrieved_at_epoch_millis") val retrievedAtEpochMillis: Long? = null,
    @ColumnInfo(name = "verified_at_epoch_millis") val verifiedAtEpochMillis: Long? = null,
)

/** Splits [NutritionSourceSnapshot.citedUrls] back into the list the UI shows. */
fun NutritionSourceSnapshot.citedUrlList(): List<String> =
    citedUrls?.lineSequence()?.map(String::trim)?.filter(String::isNotEmpty)?.toList().orEmpty()

/** Joins cited URLs for storage. Blank entries are dropped so the column stays parseable. */
fun List<String>.toCitedUrlColumn(): String? =
    map(String::trim).filter(String::isNotEmpty).distinct()
        .takeIf { it.isNotEmpty() }
        ?.joinToString("\n")
