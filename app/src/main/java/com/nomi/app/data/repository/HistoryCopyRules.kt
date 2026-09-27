package com.nomi.app.data.repository

import com.nomi.app.data.local.entity.FoodLogEntity
import com.nomi.app.data.local.entity.SavedMealEntity
import com.nomi.app.data.local.entity.SavedMealItemEntity
import java.util.Locale
import java.util.UUID

/**
 * The rewrite rules behind "copy this day / this meal to today" and "save these logs as a meal",
 * lifted out of [NomiRepository] and [com.nomi.app.data.local.dao.FoodLogDao] so they can be
 * exercised on the JVM.
 *
 * The repository and the DAO both need these, and neither can be constructed in a plain unit test:
 * the repository holds a Room [androidx.room.RoomDatabase] and the DAO is a Room-generated
 * implementation. Keeping the rules here means the interesting part - group identity, timestamp
 * layout, and the snapshots that must survive a copy - is testable without Android.
 */

/**
 * One freshly minted group id stands in for every source group, so a copied meal stays a single
 * meal: its rows share an id and Today keeps showing them as one row, and deleting one deletes the
 * group (see the group-aware `deleteFoodLog`).
 */
internal fun copiedMealLogs(
    source: List<FoodLogEntity>,
    targetLocalDate: String,
    targetMealCategory: String,
    targetStartEpochMillis: Long,
    targetZoneId: String,
    groupId: String = UUID.randomUUID().toString(),
): List<FoodLogEntity> {
    if (source.isEmpty()) return emptyList()
    return source.mapIndexed { index, log ->
        log.copy(
            id = 0,
            entryGroupId = groupId,
            mealCategory = targetMealCategory,
            localDate = targetLocalDate,
            // Millisecond offsets keep the source ordering without letting two products claim the
            // same instant, which is what the log's own tie-breaking sort relies on.
            loggedAtEpochMillis = targetStartEpochMillis + index,
            zoneId = targetZoneId,
            inputMethod = COPIED_MEAL_INPUT_METHOD,
            createdAtEpochMillis = targetStartEpochMillis,
            updatedAtEpochMillis = targetStartEpochMillis,
        )
    }
}

/**
 * Copies a whole day, remapping each source group to its own new group id.
 *
 * The remap is the whole point: without it, copying a day whose rows still carry the *source*
 * group ids would weld the copies onto the originals, and the copied rows would appear inside the
 * source day's meal groups. Ungrouped rows are keyed by their own id so two of them never collide
 * on the shared `entry_group_id` text column.
 */
internal fun copiedDayLogs(
    source: List<FoodLogEntity>,
    targetLocalDate: String,
    targetStartEpochMillis: Long,
    targetZoneId: String,
    groupIds: Map<String, String> = emptyMap(),
): List<FoodLogEntity> {
    if (source.isEmpty()) return emptyList()
    val copiedGroupIds = LinkedHashMap(groupIds)
    return source.mapIndexed { index, log ->
        val sourceGroup = log.entryGroupId ?: "single:${log.id}"
        val targetGroup = copiedGroupIds.getOrPut(sourceGroup) { UUID.randomUUID().toString() }
        log.copy(
            id = 0,
            entryGroupId = targetGroup,
            localDate = targetLocalDate,
            loggedAtEpochMillis = targetStartEpochMillis + index,
            zoneId = targetZoneId,
            inputMethod = COPIED_DAY_INPUT_METHOD,
            createdAtEpochMillis = targetStartEpochMillis,
            updatedAtEpochMillis = targetStartEpochMillis,
        )
    }
}

internal const val COPIED_DAY_INPUT_METHOD = "copied_day"
internal const val COPIED_MEAL_INPUT_METHOD = "copied_meal"

/**
 * Builds the meal graph for [NomiRepository.saveLoggedMeal] from already-loaded logs.
 *
 * The logs are ordered by when they were eaten and then by id, so a meal built from a day always
 * lists its products the way they were eaten rather than in whatever order the ids came back.
 * Everything is snapshotted onto the item, so editing or deleting the original log later cannot
 * rewrite a saved meal the user already kept.
 */
internal fun savedMealFromLogs(
    name: String,
    normalizedName: String,
    notes: String?,
    defaultMealCategory: String?,
    createdAtEpochMillis: Long,
    logs: List<FoodLogEntity>,
): Pair<SavedMealEntity, List<SavedMealItemEntity>> {
    require(name.isNotBlank()) { "A saved meal needs a name" }
    require(logs.isNotEmpty()) { "Select at least one log" }
    val ordered = logs.sortedWith(compareBy<FoodLogEntity> { it.loggedAtEpochMillis }.thenBy { it.id })
    val meal = SavedMealEntity(
        name = name.trim(),
        normalizedName = normalizeMealName(normalizedName.ifBlank { name }),
        notes = notes,
        defaultMealCategory = defaultMealCategory ?: ordered.first().mealCategory,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = createdAtEpochMillis,
    )
    val items = ordered.mapIndexed { index, log ->
        SavedMealItemEntity(
            savedMealId = 0,
            foodId = log.foodId,
            foodServingId = log.foodServingId,
            sortOrder = index,
            displayNameSnapshot = log.displayNameSnapshot,
            brandSnapshot = log.brandSnapshot,
            amount = log.amount,
            unit = log.unit,
            grams = log.grams,
            resolvedVolumeMl = log.resolvedVolumeMl,
            resolutionSource = log.resolutionSource,
            nutritionSnapshot = log.nutritionSnapshot,
            sourceSnapshot = log.sourceSnapshot,
            isEstimated = log.isEstimated,
        )
    }
    return meal to items
}

internal fun normalizeMealName(value: String): String =
    value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
