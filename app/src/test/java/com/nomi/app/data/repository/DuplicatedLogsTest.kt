package com.nomi.app.data.repository

import com.nomi.app.data.local.entity.FoodLogEntity
import com.nomi.app.data.local.entity.NutritionSourceSnapshot
import com.nomi.app.data.local.entity.NutritionValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A duplicate is its own entry.
 *
 * Every typed entry carries a group id. Copying it onto the duplicate made Today show the two as
 * one combined meal, and deleting that row removed both.
 */
class DuplicatedLogsTest {

    @Test
    fun `a duplicated typed entry does not share the original's group`() {
        val original = log(id = 7, at = 1_000, group = "typed-entry")

        val copy = duplicatedLogs(original, listOf(original), loggedAtEpochMillis = 5_000, nowEpochMillis = 6_000)
            .single()

        assertEquals(0L, copy.id)
        assertNotEquals(original.entryGroupId, copy.entryGroupId)
        assertEquals(5_000L, copy.loggedAtEpochMillis)
        assertEquals(6_000L, copy.createdAtEpochMillis)
        assertEquals(original.nutritionSnapshot, copy.nutritionSnapshot)
        assertEquals(original.localDate, copy.localDate)
    }

    @Test
    fun `duplicating a meal row duplicates every product into one new group`() {
        val meal = listOf(
            log(id = 3, at = 1_002, group = "meal"),
            log(id = 1, at = 1_000, group = "meal"),
            log(id = 2, at = 1_001, group = "meal"),
        )

        // Today's meal row carries the id of the earliest product.
        val copies = duplicatedLogs(meal[1], meal, loggedAtEpochMillis = 9_000, nowEpochMillis = 9_000)

        assertEquals(listOf("food 1", "food 2", "food 3"), copies.map { it.displayNameSnapshot })
        assertEquals(listOf(9_000L, 9_001L, 9_002L), copies.map { it.loggedAtEpochMillis })
        assertEquals(1, copies.map { it.entryGroupId }.distinct().size)
        assertNotEquals("meal", copies.first().entryGroupId)
        assertTrue(copies.all { it.id == 0L })
    }

    @Test
    fun `duplicating one product out of a meal copies only that product`() {
        val meal = listOf(log(id = 1, at = 1_000, group = "meal"), log(id = 2, at = 1_001, group = "meal"))

        val copies = duplicatedLogs(meal[1], meal, loggedAtEpochMillis = 9_000, nowEpochMillis = 9_000)

        assertEquals(listOf("food 2"), copies.map { it.displayNameSnapshot })
        assertNotEquals("meal", copies.single().entryGroupId)
    }

    @Test
    fun `an ungrouped entry gains a group of its own`() {
        val original = log(id = 4, at = 1_000, group = null)

        val copy = duplicatedLogs(original, listOf(original), 2_000, 2_000, groupId = "fresh").single()

        assertEquals("fresh", copy.entryGroupId)
    }

    private fun log(id: Long, at: Long, group: String?) = FoodLogEntity(
        id = id,
        entryGroupId = group,
        mealCategory = "LUNCH",
        displayNameSnapshot = "food $id",
        amount = 100.0,
        unit = "g",
        grams = 100.0,
        nutritionSnapshot = NutritionValues(caloriesKcal = 100.0),
        sourceSnapshot = NutritionSourceSnapshot(),
        inputMethod = "ai",
        localDate = "2026-09-26",
        loggedAtEpochMillis = at,
        zoneId = "UTC",
        createdAtEpochMillis = at,
        updatedAtEpochMillis = at,
    )
}
