package com.nomi.app.data.repository

import com.nomi.app.data.local.entity.FoodLogEntity
import com.nomi.app.data.local.entity.NutritionSourceSnapshot
import com.nomi.app.data.local.entity.NutritionValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Coverage for the rules behind History's three per-day actions.
 *
 * `NomiRepository.copyDay`, `NomiRepository.copyMeal` and `NomiRepository.saveLoggedMeal` all
 * delegate their interesting half to the functions here, so what is left in the repository is only
 * validation and the Room round trip. Nothing in this file needs Android: every case is a plain
 * data transformation over `FoodLogEntity`, which is what made the rules liftable at all.
 */
class HistoryCopyRulesTest {

    @Test
    fun `copying a day rewrites the date, zone and every timestamp`() {
        val copied = copiedDayLogs(
            source = listOf(log(id = 1, at = 1_000L), log(id = 2, at = 2_000L)),
            targetLocalDate = "2026-09-27",
            targetStartEpochMillis = 50_000L,
            targetZoneId = "Europe/Berlin",
        )

        assertEquals(listOf("2026-09-27", "2026-09-27"), copied.map { it.localDate })
        assertEquals(listOf("Europe/Berlin", "Europe/Berlin"), copied.map { it.zoneId })
        // One millisecond apart, in source order, so the copied plate keeps its sequence.
        assertEquals(listOf(50_000L, 50_001L), copied.map { it.loggedAtEpochMillis })
        copied.forEach {
            assertEquals(50_000L, it.createdAtEpochMillis)
            assertEquals(50_000L, it.updatedAtEpochMillis)
        }
    }

    @Test
    fun `copying a day never reuses the source row id`() {
        val copied = copiedDayLogs(
            source = listOf(log(id = 7, at = 1L)),
            targetLocalDate = "2026-09-27",
            targetStartEpochMillis = 1L,
            targetZoneId = "UTC",
        )

        // id = 0 is Room's autoGenerate signal. Leaving the source id in place would overwrite
        // the original row instead of adding a second one.
        assertEquals(0L, copied.single().id)
    }

    @Test
    fun `copying a day gives every source group a fresh group id`() {
        val source = listOf(
            log(id = 1, at = 1L, group = "group-a"),
            log(id = 2, at = 2L, group = "group-a"),
            log(id = 3, at = 3L, group = "group-b"),
        )

        val copied = copiedDayLogs(
            source = source,
            targetLocalDate = "2026-09-27",
            targetStartEpochMillis = 1L,
            targetZoneId = "UTC",
        )

        val groups = copied.map { it.entryGroupId }
        // The two products of one meal stay a meal...
        assertEquals(groups[0], groups[1])
        // ...a different meal stays a different meal...
        assertNotEquals(groups[0], groups[2])
        // ...and none of them is welded onto a source group, which would make the copies show up
        // inside the original day's meals.
        assertTrue(source.mapNotNull { it.entryGroupId }.none { it in groups })
    }

    @Test
    fun `copying a day does not collide when several products have no group id`() {
        val copied = copiedDayLogs(
            source = listOf(log(id = 1, at = 1L), log(id = 2, at = 2L)),
            targetLocalDate = "2026-09-27",
            targetStartEpochMillis = 1L,
            targetZoneId = "UTC",
        )

        // entry_group_id is a plain shared TEXT column with no uniqueness guarantee, so two
        // ungrouped rows falling back to one constant would silently merge into a single meal.
        val groups = copied.map { it.entryGroupId }
        assertEquals(2, groups.distinct().size)
        assertTrue(groups.all { it != null })
    }

    @Test
    fun `copying a day preserves the meal category of every row`() {
        val copied = copiedDayLogs(
            source = listOf(
                log(id = 1, at = 1L, meal = "BREAKFAST"),
                log(id = 2, at = 2L, meal = "DINNER"),
            ),
            targetLocalDate = "2026-09-27",
            targetStartEpochMillis = 1L,
            targetZoneId = "UTC",
        )

        assertEquals(listOf("BREAKFAST", "DINNER"), copied.map { it.mealCategory })
    }

    @Test
    fun `copying an empty day inserts nothing`() {
        val copied = copiedDayLogs(
            source = emptyList(),
            targetLocalDate = "2026-09-27",
            targetStartEpochMillis = 1L,
            targetZoneId = "UTC",
        )

        assertTrue(copied.isEmpty())
    }

    @Test
    fun `copying a meal puts every product in one new group`() {
        val copied = copiedMealLogs(
            source = listOf(
                log(id = 1, at = 1L, group = "source-group", meal = "DINNER"),
                log(id = 2, at = 2L, group = "source-group", meal = "DINNER"),
            ),
            targetLocalDate = "2026-09-27",
            targetMealCategory = "DINNER",
            targetStartEpochMillis = 90_000L,
            targetZoneId = "UTC",
        )

        val groups = copied.map { it.entryGroupId }
        // One new group for the whole copied meal, and not the source's: adopting "source-group"
        // would make deleting the copy take the original meal with it.
        assertEquals(1, groups.distinct().size)
        assertTrue(groups.all { it != null && it != "source-group" })
    }

    @Test
    fun `copying a meal can move it to a different category`() {
        val copied = copiedMealLogs(
            source = listOf(log(id = 1, at = 1L, meal = "LUNCH")),
            targetLocalDate = "2026-09-27",
            targetMealCategory = "DINNER",
            targetStartEpochMillis = 1L,
            targetZoneId = "UTC",
        )

        assertEquals("DINNER", copied.single().mealCategory)
    }

    @Test
    fun `copying a meal keeps the food itself untouched`() {
        val original = log(id = 1, at = 1L).copy(
            displayNameSnapshot = "Greek yoghurt",
            brandSnapshot = "Fage",
            amount = 200.0,
            unit = "g",
            grams = 200.0,
            nutritionSnapshot = NutritionValues(caloriesKcal = 130.0, proteinGrams = 18.0),
        )

        val copied = copiedMealLogs(
            source = listOf(original),
            targetLocalDate = "2026-09-27",
            targetMealCategory = "BREAKFAST",
            targetStartEpochMillis = 1L,
            targetZoneId = "UTC",
        ).single()

        assertEquals("Greek yoghurt", copied.displayNameSnapshot)
        assertEquals("Fage", copied.brandSnapshot)
        assertEquals(200.0, copied.amount, 0.0)
        assertEquals(130.0, copied.nutritionSnapshot.caloriesKcal, 0.0)
        assertEquals(18.0, copied.nutritionSnapshot.proteinGrams, 0.0)
    }

    @Test
    fun `copied rows are stamped with how they were produced`() {
        assertEquals(
            "copied_day",
            copiedDayLogs(listOf(log(1, 1L)), "2026-09-27", 1L, "UTC").single().inputMethod,
        )
        assertEquals(
            "copied_meal",
            copiedMealLogs(listOf(log(1, 1L)), "2026-09-27", "DINNER", 1L, "UTC").single().inputMethod,
        )
    }

    @Test
    fun `saving logs as a meal orders the products by when they were eaten`() {
        val (_, items) = savedMealFromLogs(
            name = "Tuesday dinner",
            normalizedName = "",
            notes = null,
            defaultMealCategory = null,
            createdAtEpochMillis = 5L,
            logs = listOf(
                log(id = 3, at = 3_000L, name = "rice"),
                log(id = 1, at = 1_000L, name = "chicken"),
                log(id = 2, at = 2_000L, name = "salad"),
            ),
        )

        assertEquals(listOf("chicken", "salad", "rice"), items.map { it.displayNameSnapshot })
        assertEquals(listOf(0, 1, 2), items.map { it.sortOrder })
    }

    @Test
    fun `logs logged in the same millisecond keep a stable order`() {
        val (_, items) = savedMealFromLogs(
            name = "Tie",
            normalizedName = "",
            notes = null,
            defaultMealCategory = null,
            createdAtEpochMillis = 1L,
            logs = listOf(log(id = 9, at = 7L, name = "second"), log(id = 4, at = 7L, name = "first")),
        )

        // Without the id tiebreak the order would depend on which row Room happened to return,
        // so a meal could reorder itself between two opens.
        assertEquals(listOf("first", "second"), items.map { it.displayNameSnapshot })
    }

    @Test
    fun `a saved meal snapshots the portion so later edits cannot rewrite it`() {
        val source = log(id = 1, at = 1L, name = "oats", meal = "BREAKFAST").copy(
            nutritionSnapshot = NutritionValues(caloriesKcal = 200.0, proteinGrams = 8.0),
            sourceSnapshot = NutritionSourceSnapshot(kind = "open_food_facts", externalId = "1234"),
        )

        val (_, items) = savedMealFromLogs(
            name = "Breakfast",
            normalizedName = "",
            notes = null,
            defaultMealCategory = null,
            createdAtEpochMillis = 1L,
            logs = listOf(source),
        )

        val item = items.single()
        assertEquals(200.0, item.nutritionSnapshot.caloriesKcal, 0.0)
        assertEquals(8.0, item.nutritionSnapshot.proteinGrams, 0.0)
        assertEquals("open_food_facts", item.sourceSnapshot.kind)
        assertEquals("1234", item.sourceSnapshot.externalId)
    }

    @Test
    fun `a saved meal falls back to the first product's category when none is given`() {
        val (meal, _) = savedMealFromLogs(
            name = "Evening",
            normalizedName = "",
            notes = null,
            defaultMealCategory = null,
            createdAtEpochMillis = 1L,
            logs = listOf(log(id = 1, at = 1L, meal = "DINNER"), log(id = 2, at = 2L, meal = "SNACKS")),
        )

        assertEquals("DINNER", meal.defaultMealCategory)
    }

    @Test
    fun `a saved meal normalizes its own name when the caller leaves it blank`() {
        val (meal, _) = savedMealFromLogs(
            name = "  Tuesday   Dinner  ",
            normalizedName = "",
            notes = null,
            defaultMealCategory = "DINNER",
            createdAtEpochMillis = 1L,
            logs = listOf(log(id = 1, at = 1L)),
        )

        // This is the column the library search matches on, so "Tuesday  Dinner" and
        // "tuesday dinner" have to collapse to one row.
        assertEquals("tuesday dinner", meal.normalizedName)
        assertEquals("Tuesday   Dinner", meal.name)
    }

    @Test
    fun `a saved meal keeps the notes it was given`() {
        val (meal, _) = savedMealFromLogs(
            name = "Lunch",
            normalizedName = "lunch",
            notes = "the one from the cafe",
            defaultMealCategory = "LUNCH",
            createdAtEpochMillis = 1L,
            logs = listOf(log(id = 1, at = 1L)),
        )

        assertEquals("the one from the cafe", meal.notes)
    }

    @Test
    fun `saving nothing is refused rather than creating an empty meal`() {
        // saveLoggedMeal returns early on a blank name, and the History screen is what stops that
        // from looking like a dead chip. Both halves are asserted here.
        blankNameRejected()
        emptyLogsRejected()
    }

    private fun blankNameRejected() {
        val refused = runCatching {
            savedMealFromLogs("   ", "", null, null, 1L, listOf(log(1, 1L)))
        }
        assertTrue("a blank name must be refused", refused.isFailure)
    }

    private fun emptyLogsRejected() {
        val refused = runCatching {
            savedMealFromLogs("Dinner", "", null, null, 1L, emptyList())
        }
        assertTrue("a meal with no products must be refused", refused.isFailure)
    }

    @Test
    fun `normalizeMealName collapses case and repeated whitespace`() {
        assertEquals("tuesday dinner", normalizeMealName("  Tuesday   DINNER  "))
        // Whitespace-only input normalises to an empty key rather than to a name of spaces,
        // which is what the search column would otherwise index.
        assertEquals("", normalizeMealName("   "))
        assertEquals("", normalizeMealName("\t\n "))
    }

    private fun log(
        id: Long,
        at: Long,
        group: String? = null,
        meal: String = "LUNCH",
        name: String = "food $id",
    ) = FoodLogEntity(
        id = id,
        foodId = 100L + id,
        entryGroupId = group,
        mealCategory = meal,
        displayNameSnapshot = name,
        amount = 100.0,
        unit = "g",
        grams = 100.0,
        nutritionSnapshot = NutritionValues(caloriesKcal = 100.0),
        sourceSnapshot = NutritionSourceSnapshot(),
        inputMethod = "typed",
        localDate = "2026-09-26",
        loggedAtEpochMillis = at,
        zoneId = "UTC",
        createdAtEpochMillis = at,
        updatedAtEpochMillis = at,
    )
}
