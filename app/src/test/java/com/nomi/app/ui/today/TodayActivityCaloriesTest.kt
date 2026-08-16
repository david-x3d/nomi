package com.nomi.app.ui.today

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayActivityCaloriesTest {
    @Test
    fun `Health Connect total remains primary and step estimate is not added`() {
        val state = TodayUiState(
            activeCaloriesKcal = 420.0,
            estimatedStepCaloriesKcal = 250.0,
            calorieTarget = 2_000.0,
        )

        assertEquals(420.0, state.effectiveBurnedCaloriesKcal!!, 0.0)
        assertEquals(0.21f, state.burnedFraction, 0.0f)
        assertFalse(state.burnedCaloriesAreEstimated)
    }

    @Test
    fun `step estimate fills only a missing Health Connect value`() {
        val state = TodayUiState(
            activeCaloriesKcal = null,
            estimatedStepCaloriesKcal = 250.0,
            calorieTarget = 2_000.0,
        )

        assertEquals(250.0, state.effectiveBurnedCaloriesKcal!!, 0.0)
        assertEquals(0.125f, state.burnedFraction, 0.0f)
        assertTrue(state.burnedCaloriesAreEstimated)
    }

    @Test
    fun `walking estimate never changes food target or calories left`() {
        val state = TodayUiState(
            caloriesConsumed = 1_400.0,
            calorieTarget = 2_000.0,
            estimatedStepCaloriesKcal = 300.0,
        )

        assertEquals(600.0, state.caloriesDifference, 0.0)
        assertEquals(2_000.0, state.calorieTarget, 0.0)
    }

    @Test
    fun `step estimate stays visibly approximate and rounds to five kcal`() {
        assertEquals("≈ 250 kcal", estimatedStepCaloriesText(248.1, Locale.US))
        assertEquals("< 5 kcal", estimatedStepCaloriesText(3.2, Locale.US))
        assertEquals("0 kcal", estimatedStepCaloriesText(0.0, Locale.US))
    }
}
