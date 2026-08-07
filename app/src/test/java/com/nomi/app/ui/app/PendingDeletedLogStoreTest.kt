package com.nomi.app.ui.app

import com.nomi.app.data.local.entity.FoodLogEntity
import com.nomi.app.data.local.entity.NutritionSourceSnapshot
import com.nomi.app.data.local.entity.NutritionValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingDeletedLogStoreTest {
    @Test
    fun `take returns the exact deleted snapshot once`() {
        val store = PendingDeletedLogStore()
        val log = foodLog(id = 41, name = "Salami pizza")

        assertTrue(store.remember(log))
        assertEquals(log, store.peek(log.id))
        assertEquals(log, store.take(log.id))
        assertNull(store.take(log.id))
    }

    @Test
    fun `duplicate delete cannot replace the original undo snapshot`() {
        val store = PendingDeletedLogStore()
        val original = foodLog(id = 7, name = "Original")
        val replacement = foodLog(id = 7, name = "Replacement")

        assertTrue(store.remember(original))
        assertFalse(store.remember(replacement))
        assertEquals(original, store.take(original.id))
    }

    @Test
    fun `discard closes only the requested undo window`() {
        val store = PendingDeletedLogStore()
        val first = foodLog(id = 1, name = "First")
        val second = foodLog(id = 2, name = "Second")
        store.remember(first)
        store.remember(second)

        store.discard(first.id)

        assertNull(store.peek(first.id))
        assertEquals(second, store.peek(second.id))
    }

    private fun foodLog(id: Long, name: String) = FoodLogEntity(
        id = id,
        mealCategory = "lunch",
        displayNameSnapshot = name,
        amount = 250.0,
        unit = "g",
        grams = 250.0,
        nutritionSnapshot = NutritionValues(
            caloriesKcal = 400.0,
            proteinGrams = 20.0,
            carbohydrateGrams = 45.0,
            fatGrams = 12.0,
        ),
        sourceSnapshot = NutritionSourceSnapshot(displayName = "Test"),
        inputMethod = "text",
        localDate = "2026-08-08",
        loggedAtEpochMillis = 1_000L,
        zoneId = "Europe/Berlin",
        createdAtEpochMillis = 1_000L,
        updatedAtEpochMillis = 1_000L,
    )
}
