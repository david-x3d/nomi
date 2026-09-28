package com.nomi.app.data.share

import kotlinx.serialization.Serializable

/**
 * What Nomi hands to another device when the user shares part of a day.
 *
 * Deliberately narrower than a backup: this is a reading of some of a day's foods, not something
 * a diary can be restored from, so it carries no preferences, no weights and no catalogue ids.
 * The format tag and version let a receiver see what it is looking at before it tries to parse
 * it, the same way the diary export does.
 */
@Serializable
data class ShareEnvelopeV1(
    val format: String = FORMAT,
    val schemaVersion: Int = SCHEMA_VERSION,
    val sharedAtEpochMillis: Long,
    val appVersionName: String,
    val day: ShareDayV1,
) {
    companion object {
        const val FORMAT: String = "nomi-share"
        const val SCHEMA_VERSION: Int = 1
    }
}

@Serializable
data class ShareDayV1(
    /** ISO-8601 date the foods were eaten, e.g. 2026-09-28. */
    val date: String,
    val foods: List<ShareFoodV1>,
    /** Null when the user shared the foods on their own without the day's running totals. */
    val totals: ShareTotalsV1? = null,
)

@Serializable
data class ShareFoodV1(
    val name: String,
    val brand: String? = null,
    val amount: Double,
    val unit: String,
    /** The meal this food was part of, as the day groups it. */
    val meal: String,
    val kcal: Double,
    val proteinGrams: Double,
    val carbohydrateGrams: Double,
    val fatGrams: Double,
)

/**
 * What the shared foods add up to.
 *
 * `foodCount` is in the payload rather than left to `foods.size` on purpose: the totals describe
 * exactly the foods that were shared, and a receiver that tidies or merges entries still needs to
 * know how many the sender meant.
 */
@Serializable
data class ShareTotalsV1(
    val foodCount: Int,
    val kcal: Double,
    val proteinGrams: Double,
    val carbohydrateGrams: Double,
    val fatGrams: Double,
)
