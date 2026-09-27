package com.nomi.app.ui.today

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayActivityCaloriesTest {
    @Test
    fun `Nomi estimate always wins over Health Connect including screenshot values`() {
        val state = TodayUiState(
            activeCaloriesKcal = 191.0,
            estimatedStepCaloriesKcal = 40.0,
            steps = 2_060,
            calorieTarget = 1_500.0,
        )
        assertEquals(40.0, state.effectiveBurnedCaloriesKcal!!, 0.0)
        assertEquals(40f / 1500f, state.burnedFraction, 0.00001f)
        assertTrue(state.burnedCaloriesAreEstimated)
        assertEquals(40.0, state.copy(activeCaloriesKcal = null).effectiveBurnedCaloriesKcal!!, 0.0)
    }

    @Test
    fun `missing estimate never falls back to Health Connect`() {
        val state = TodayUiState(activeCaloriesKcal = 191.0)
        assertEquals(null, state.effectiveBurnedCaloriesKcal)
        assertEquals(0f, state.burnedFraction, 0f)
        assertFalse(state.burnedCaloriesAreEstimated)
    }

    @Test
    fun `zero estimate remains zero even with Health Connect activity`() {
        val state = TodayUiState(activeCaloriesKcal = 191.0, estimatedStepCaloriesKcal = 0.0)
        assertEquals(0.0, state.effectiveBurnedCaloriesKcal!!, 0.0)
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

    @Test
    fun `the pill spells the estimate as a plain rounded number`() {
        assertEquals("250", estimatedStepCaloriesValue(248.1, Locale.US))
        assertEquals("1,250", estimatedStepCaloriesValue(1_248.1, Locale.US))
        assertEquals("< 5", estimatedStepCaloriesValue(3.2, Locale.US))
        assertEquals("0", estimatedStepCaloriesValue(0.0, Locale.US))
    }
}
