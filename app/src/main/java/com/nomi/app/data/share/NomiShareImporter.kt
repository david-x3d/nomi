package com.nomi.app.data.share

import com.nomi.app.data.local.entity.FoodLogEntity
import com.nomi.app.data.local.entity.NutritionSourceSnapshot
import com.nomi.app.data.local.entity.NutritionValues
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** How a day that arrived from another phone is marked once it is in the diary. */
internal const val SHARED_INPUT_METHOD = "shared"

/**
 * Turns a day that arrived over a tap into rows this app can log.
 *
 * Nothing is looked up in the catalogue on purpose. A shared food is somebody else's number for
 * their own portion, and quietly matching it to a Nomi food would be inventing a link the sender
 * never made. So the arriving rows stand on their own snapshots, exactly like a manual entry,
 * and the diary shows where they came from.
 */
object NomiShareImporter {

    /**
     * The rows for [envelope] to be logged on [date].
     *
     * Each food becomes its own row rather than one row for the day, so the receiving phone's
     * Today page can show, duplicate and delete them the way it would any other entry.
     */
    fun logsFor(
        envelope: ShareEnvelopeV1,
        date: LocalDate,
        zone: ZoneId,
        now: Long,
    ): List<FoodLogEntity> = envelope.day.foods.mapIndexed { index, food ->
        val loggedAt = now + index
        FoodLogEntity(
            mealCategory = food.meal.toMealCategoryName(),
            displayNameSnapshot = food.name,
            brandSnapshot = food.brand,
            amount = food.amount,
            unit = food.unit,
            // Grams are not part of a shared day, and guessing them from a unit would put a
            // number in the diary that nobody weighed.
            grams = null,
            nutritionSnapshot = NutritionValues(
                caloriesKcal = food.kcal,
                proteinGrams = food.proteinGrams,
                carbohydrateGrams = food.carbohydrateGrams,
                fatGrams = food.fatGrams,
            ),
            sourceSnapshot = NutritionSourceSnapshot(
                kind = "shared",
                displayName = envelope.appVersionName,
            ),
            isEstimated = true,
            inputMethod = SHARED_INPUT_METHOD,
            localDate = date.toString(),
            loggedAtEpochMillis = loggedAt,
            zoneId = zone.id,
            createdAtEpochMillis = loggedAt,
            updatedAtEpochMillis = loggedAt,
        )
    }

    /** One instant for the whole day, so the rows keep the order they were shared in. */
    fun now(): Long = Instant.now().toEpochMilli()

    /**
     * A meal name from the other phone, matched to the four this app groups by.
     *
     * Anything unrecognised becomes a snack rather than being dropped: a day whose breakfast was
     * filed as lunch is a small inaccuracy, and a day that is missing a food is a lie.
     */
    private fun String.toMealCategoryName(): String = when (lowercase()) {
        "breakfast" -> "BREAKFAST"
        "lunch" -> "LUNCH"
        "dinner" -> "DINNER"
        else -> "SNACKS"
    }
}
