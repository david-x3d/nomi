package com.nomi.app.ui.logging

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualFoodDraftTest {
    private val complete = ManualFoodDraft(
        name = "Soup", amount = "1,5", unit = "bowl",
        calories = "210", protein = "8", carbohydrates = "30", fat = "6",
    )

    @Test
    fun `a complete draft can be saved`() {
        assertTrue(complete.isValid)
    }

    /** A cleared unit was saved as an empty string, which every later backup then refused. */
    @Test
    fun `a draft without a unit cannot be saved`() {
        assertFalse(complete.copy(unit = "").isValid)
        assertFalse(complete.copy(unit = "   ").isValid)
    }
}
