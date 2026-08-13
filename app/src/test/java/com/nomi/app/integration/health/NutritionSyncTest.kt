package com.nomi.app.integration.health

import androidx.health.connect.client.records.MealType
import com.nomi.app.data.local.entity.FoodLogEntity
import com.nomi.app.data.local.entity.NutritionSourceSnapshot
import com.nomi.app.data.local.entity.NutritionValues
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionSyncTest {
    private val window = setOf("2026-08-12", "2026-08-13")

    @Test
    fun plan_writesEveryEntryWhenNothingHasBeenSyncedYet() {
        val breakfast = entry(logId = 1, localDate = "2026-08-13", version = 10)
        val lunch = entry(logId = 2, localDate = "2026-08-13", version = 20)

        val plan = planNutritionSync(listOf(lunch, breakfast), window, emptyMap())

        assertEquals(listOf(breakfast, lunch), plan.write)
        assertEquals(emptyList<String>(), plan.deleteClientRecordIds)
        assertEquals(
            mapOf("2026-08-13" to mapOf("1" to 10L, "2" to 20L)),
            plan.syncedVersions,
        )
    }

    @Test
    fun plan_skipsUnchangedEntriesAndRewritesCorrectedOnes() {
        val unchanged = entry(logId = 1, localDate = "2026-08-13", version = 10)
        val corrected = entry(logId = 2, localDate = "2026-08-13", version = 99)
        val synced = mapOf("2026-08-13" to mapOf("1" to 10L, "2" to 20L))

        val plan = planNutritionSync(listOf(unchanged, corrected), window, synced)

        assertEquals(listOf(corrected), plan.write)
        assertEquals(emptyList<String>(), plan.deleteClientRecordIds)
    }

    @Test
    fun plan_deletesRecordsForFoodThatIsNoLongerLogged() {
        val remaining = entry(logId = 1, localDate = "2026-08-13", version = 10)
        val synced = mapOf("2026-08-13" to mapOf("1" to 10L, "2" to 20L, "3" to 30L))

        val plan = planNutritionSync(listOf(remaining), window, synced)

        assertEquals(emptyList<HealthNutritionEntry>(), plan.write)
        assertEquals(listOf("nomi-food-2", "nomi-food-3"), plan.deleteClientRecordIds)
        assertEquals(mapOf("2026-08-13" to mapOf("1" to 10L)), plan.syncedVersions)
    }

    @Test
    fun plan_clearsADayWhoseEntriesWereAllDeleted() {
        val synced = mapOf("2026-08-13" to mapOf("1" to 10L))

        val plan = planNutritionSync(emptyList(), window, synced)

        assertEquals(listOf("nomi-food-1"), plan.deleteClientRecordIds)
        assertEquals(emptyMap<String, Map<String, Long>>(), plan.syncedVersions)
    }

    /**
     * A day that slides out of the window keeps its Health Connect records - they still describe
     * food the user ate - but is forgotten locally so the bookkeeping cannot grow without bound.
     */
    @Test
    fun plan_forgetsDaysOutsideTheWindowWithoutDeletingThem() {
        val today = entry(logId = 5, localDate = "2026-08-13", version = 50)
        val synced = mapOf(
            "2026-07-01" to mapOf("1" to 10L),
            "2026-08-13" to mapOf("5" to 50L),
        )

        val plan = planNutritionSync(listOf(today), window, synced)

        assertTrue(plan.isEmpty)
        assertEquals(mapOf("2026-08-13" to mapOf("5" to 50L)), plan.syncedVersions)
    }

    @Test
    fun plan_ignoresEntriesOutsideTheWindow() {
        val old = entry(logId = 1, localDate = "2026-07-01", version = 10)

        val plan = planNutritionSync(listOf(old), window, emptyMap())

        assertTrue(plan.isEmpty)
        assertEquals(emptyMap<String, Map<String, Long>>(), plan.syncedVersions)
    }

    @Test
    fun mapping_carriesMacrosMealAndBrandIntoTheRecord() {
        val mapped = log(
            id = 7,
            mealCategory = "BREAKFAST",
            nutrition = NutritionValues(
                caloriesKcal = 240.0,
                proteinGrams = 12.5,
                carbohydrateGrams = 30.0,
                fatGrams = 8.0,
                fiberGrams = 3.0,
                sodiumMilligrams = 400.0,
            ),
        ).toHealthNutritionEntry(ZoneId.of("Europe/Berlin"))

        assertEquals("nomi-food-7", mapped.clientRecordId)
        assertEquals("Skyr (Arla)", mapped.name)
        assertEquals(MealType.MEAL_TYPE_BREAKFAST, mapped.mealType)
        assertEquals(240.0, mapped.caloriesKcal, 0.0)
        assertEquals(12.5, mapped.proteinGrams, 0.0)
        assertEquals(30.0, mapped.carbohydrateGrams, 0.0)
        assertEquals(8.0, mapped.fatGrams, 0.0)
        assertEquals(3.0, mapped.fiberGrams!!, 0.0)
        assertEquals(0.4, mapped.sodiumGrams()!!, 1e-9)
        assertNull(mapped.sugarGrams)
        assertTrue(mapped.startTime.isBefore(mapped.endTime))
    }

    /** The entry keeps the zone it was logged in, so a travel day reads the same in both apps. */
    @Test
    fun mapping_keepsTheZoneTheEntryWasLoggedIn() {
        val mapped = log(id = 1, zone = "Pacific/Auckland")
            .toHealthNutritionEntry(ZoneId.of("Europe/Berlin"))

        assertEquals(ZoneOffset.ofHours(12), mapped.zoneOffset)
    }

    @Test
    fun mapping_replacesBrokenNutrientsWithZeroInsteadOfLosingTheMeal() {
        val mapped = log(
            id = 1,
            nutrition = NutritionValues(
                caloriesKcal = Double.NaN,
                proteinGrams = -4.0,
                carbohydrateGrams = 10.0,
                fatGrams = 2.0,
            ),
        ).toHealthNutritionEntry(ZoneId.of("Europe/Berlin"))

        assertEquals(0.0, mapped.caloriesKcal, 0.0)
        assertEquals(0.0, mapped.proteinGrams, 0.0)
        assertEquals(10.0, mapped.carbohydrateGrams, 0.0)
    }

    @Test
    fun mealType_mapsNomisCategoriesAndFallsBackToUnknown() {
        assertEquals(MealType.MEAL_TYPE_BREAKFAST, healthMealType("BREAKFAST"))
        assertEquals(MealType.MEAL_TYPE_LUNCH, healthMealType("lunch"))
        assertEquals(MealType.MEAL_TYPE_DINNER, healthMealType(" Dinner "))
        assertEquals(MealType.MEAL_TYPE_SNACK, healthMealType("SNACKS"))
        assertEquals(MealType.MEAL_TYPE_UNKNOWN, healthMealType("second breakfast"))
    }

    private fun entry(logId: Long, localDate: String, version: Long) = HealthNutritionEntry(
        logId = logId,
        localDate = localDate,
        version = version,
        name = "Skyr",
        mealType = MealType.MEAL_TYPE_BREAKFAST,
        startTime = Instant.parse("2026-08-13T06:30:00Z"),
        endTime = Instant.parse("2026-08-13T06:30:01Z"),
        zoneOffset = ZoneOffset.UTC,
        caloriesKcal = 120.0,
        proteinGrams = 11.0,
        carbohydrateGrams = 4.0,
        fatGrams = 0.5,
    )

    private fun log(
        id: Long,
        mealCategory: String = "LUNCH",
        zone: String = "Europe/Berlin",
        nutrition: NutritionValues = NutritionValues(
            caloriesKcal = 100.0,
            proteinGrams = 5.0,
            carbohydrateGrams = 10.0,
            fatGrams = 2.0,
        ),
    ) = FoodLogEntity(
        id = id,
        mealCategory = mealCategory,
        displayNameSnapshot = "Skyr",
        brandSnapshot = "Arla",
        amount = 1.0,
        unit = "cup",
        nutritionSnapshot = nutrition,
        sourceSnapshot = NutritionSourceSnapshot(),
        inputMethod = "typed",
        localDate = "2026-08-13",
        loggedAtEpochMillis = Instant.parse("2026-08-13T06:30:00Z").toEpochMilli(),
        zoneId = zone,
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 2L,
    )
}
