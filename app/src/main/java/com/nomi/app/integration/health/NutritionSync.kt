package com.nomi.app.integration.health

import androidx.health.connect.client.records.MealType
import com.nomi.app.data.local.entity.FoodLogEntity
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

/** Prefix of every client record id Nomi writes, so its own nutrition records stay recognisable. */
private const val NUTRITION_CLIENT_RECORD_PREFIX = "nomi-food-"

/** A logged food entry lasts a moment; Health Connect refuses a record that ends when it starts. */
private const val NUTRITION_RECORD_SECONDS = 1L

/** Health Connect stores mass in grams, while Nomi keeps sodium in milligrams. */
private const val MILLIGRAMS_PER_GRAM = 1_000.0

/** The stable client record id for a food log row, which makes a rewrite an update. */
fun nutritionClientRecordId(logId: Long): String = "$NUTRITION_CLIENT_RECORD_PREFIX$logId"

/**
 * One logged portion as Health Connect will store it: absolute times, kcal, and grams.
 *
 * [version] is the log row's update timestamp. It travels into the record as the client record
 * version, so a later correction always wins over the copy already in Health Connect, and it is
 * what lets a sync skip entries that have not changed since the last one.
 */
data class HealthNutritionEntry(
    val logId: Long,
    /** ISO-8601 date of the day the portion belongs to, e.g. 2026-08-13. */
    val localDate: String,
    val version: Long,
    val name: String,
    val mealType: Int,
    val startTime: Instant,
    val endTime: Instant,
    val zoneOffset: ZoneOffset,
    val caloriesKcal: Double,
    val proteinGrams: Double,
    val carbohydrateGrams: Double,
    val fatGrams: Double,
    val fiberGrams: Double? = null,
    val sugarGrams: Double? = null,
    val saturatedFatGrams: Double? = null,
    val sodiumMilligrams: Double? = null,
) {
    val clientRecordId: String get() = nutritionClientRecordId(logId)
}

/**
 * The writes and deletes that would bring Health Connect back in step with the food log.
 *
 * [syncedVersions] is the bookkeeping to persist once the plan has been carried out: day ->
 * (log id -> written version). It is stored rather than derived because a deleted log leaves
 * nothing behind to compare against, and its Health Connect record still has to go.
 */
data class NutritionSyncPlan(
    val write: List<HealthNutritionEntry>,
    val deleteClientRecordIds: List<String>,
    val syncedVersions: Map<String, Map<String, Long>>,
) {
    val isEmpty: Boolean get() = write.isEmpty() && deleteClientRecordIds.isEmpty()
}

/**
 * Selects every day needed for a full-history repair.
 *
 * Current entry days make old food eligible for backfill. Ledger-only days are just as important:
 * they let the plan delete a Health Connect record when its Nomi food row was removed after a
 * previous sync. Sorting makes the resulting plan and persisted ledger independent of input order.
 */
fun nutritionSyncDatesForFullHistory(
    entries: Iterable<HealthNutritionEntry>,
    synced: Map<String, Map<String, Long>>,
): Set<String> = (
    entries.asSequence().map(HealthNutritionEntry::localDate) + synced.keys.asSequence()
).toSortedSet()

/** Durable record starts used to delete a removed food by owned-data time range on every provider. */
fun nutritionSyncStartTimes(
    entries: Iterable<HealthNutritionEntry>,
): Map<String, Map<String, Long>> = entries
    .sortedWith(compareBy({ it.localDate }, { it.logId }))
    .groupBy(HealthNutritionEntry::localDate)
    .mapValues { (_, dayEntries) ->
        dayEntries.associate { entry -> entry.logId.toString() to entry.startTime.toEpochMilli() }
    }

/**
 * Diffs the food log against what was last written to Health Connect.
 *
 * Only [windowDates] are considered in both directions. An ordinary background sync can pass a
 * small rolling window; an explicit full-history sync can use [nutritionSyncDatesForFullHistory]
 * to include old entries and ledger-only days. Days outside the selected dates are dropped from
 * the returned bookkeeping.
 *
 * [forceRewrite] re-emits every live entry in the selected dates even when its version is already
 * in the ledger. It is intended for an explicit repair/manual sync and defaults to false so normal
 * background syncs remain incremental. Rewrites keep [HealthNutritionEntry.clientRecordId], so
 * Health Connect updates the existing Nomi record instead of creating a duplicate.
 */
fun planNutritionSync(
    entries: List<HealthNutritionEntry>,
    windowDates: Set<String>,
    synced: Map<String, Map<String, Long>>,
    forceRewrite: Boolean = false,
): NutritionSyncPlan {
    val current = entries.filter { it.localDate in windowDates }
        .sortedWith(compareBy({ it.localDate }, { it.logId }))
        .groupBy(HealthNutritionEntry::localDate)

    val write = current.values.flatten().filter { entry ->
        forceRewrite || synced[entry.localDate]?.get(entry.logId.toString()) != entry.version
    }

    val deleteClientRecordIds = synced.filterKeys { it in windowDates }
        .toSortedMap()
        .flatMap { (localDate, versions) ->
            val live = current[localDate].orEmpty().mapTo(mutableSetOf()) { it.logId.toString() }
            versions.keys.filterNot { it in live }.sorted()
                .map { logId -> "$NUTRITION_CLIENT_RECORD_PREFIX$logId" }
        }

    val syncedVersions = current.mapValues { (_, dayEntries) ->
        dayEntries.associate { entry -> entry.logId.toString() to entry.version }
    }

    return NutritionSyncPlan(
        write = write,
        deleteClientRecordIds = deleteClientRecordIds,
        syncedVersions = syncedVersions,
    )
}

/**
 * Turns a stored log row into the record Health Connect will hold.
 *
 * The nutrition snapshot is already scaled to the eaten portion, so the values move across
 * unchanged. [zoneId] is only a fallback: an entry logged in another time zone keeps the offset
 * it was logged with, which is what makes a travel day read correctly in both apps.
 */
fun FoodLogEntity.toHealthNutritionEntry(zoneId: ZoneId): HealthNutritionEntry {
    val start = Instant.ofEpochMilli(loggedAtEpochMillis)
    val offset = runCatching { ZoneId.of(this.zoneId) }.getOrDefault(zoneId).rules.getOffset(start)
    return HealthNutritionEntry(
        logId = id,
        localDate = localDate,
        version = updatedAtEpochMillis,
        name = brandSnapshot?.takeIf(String::isNotBlank)
            ?.let { brand -> "$displayNameSnapshot ($brand)" }
            ?: displayNameSnapshot,
        mealType = healthMealType(mealCategory),
        startTime = start,
        endTime = start.plusSeconds(NUTRITION_RECORD_SECONDS),
        zoneOffset = offset,
        caloriesKcal = nutritionSnapshot.caloriesKcal.forHealthConnect(),
        proteinGrams = nutritionSnapshot.proteinGrams.forHealthConnect(),
        carbohydrateGrams = nutritionSnapshot.carbohydrateGrams.forHealthConnect(),
        fatGrams = nutritionSnapshot.fatGrams.forHealthConnect(),
        fiberGrams = nutritionSnapshot.fiberGrams?.forHealthConnect(),
        sugarGrams = nutritionSnapshot.sugarGrams?.forHealthConnect(),
        saturatedFatGrams = nutritionSnapshot.saturatedFatGrams?.forHealthConnect(),
        sodiumMilligrams = nutritionSnapshot.sodiumMilligrams?.forHealthConnect(),
    )
}

/** Grams of sodium, which is the unit Health Connect records it in. */
fun HealthNutritionEntry.sodiumGrams(): Double? = sodiumMilligrams?.div(MILLIGRAMS_PER_GRAM)

/** Maps Nomi's stored meal category onto the four meal types Health Connect knows. */
internal fun healthMealType(mealCategory: String): Int =
    when (mealCategory.trim().uppercase(Locale.ROOT)) {
        "BREAKFAST" -> MealType.MEAL_TYPE_BREAKFAST
        "LUNCH" -> MealType.MEAL_TYPE_LUNCH
        "DINNER" -> MealType.MEAL_TYPE_DINNER
        "SNACK", "SNACKS" -> MealType.MEAL_TYPE_SNACK
        else -> MealType.MEAL_TYPE_UNKNOWN
    }

/**
 * Health Connect rejects a negative or non-finite amount and would fail the whole batch with it,
 * so a nutrient that somehow arrived broken is written as zero instead of losing the meal.
 */
private fun Double.forHealthConnect(): Double = if (isFinite() && this > 0.0) this else 0.0
