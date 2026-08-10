package com.nomi.app.domain.calculator

import com.nomi.app.ai.model.AnalyzedFoodItem
import com.nomi.app.data.preferences.CalorieEstimateBias
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CalorieBiasAdjusterTest {
    /** The user's own example: a meal plausibly 500-700 kcal, reported as 600. */
    private val uncertainty = 16.667

    @Test
    fun `each setting lands where the user expects inside the range`() {
        fun logged(bias: CalorieEstimateBias) =
            600.0 * CalorieBiasAdjuster.scaleFor(uncertainty, bias)

        assertEquals(500.0, logged(CalorieEstimateBias.STRONGLY_UNDERESTIMATE), 1.0)
        assertEquals(530.0, logged(CalorieEstimateBias.UNDERESTIMATE), 1.0)
        assertEquals(600.0, logged(CalorieEstimateBias.NONE), 0.0)
        assertEquals(670.0, logged(CalorieEstimateBias.OVERESTIMATE), 1.0)
        assertEquals(700.0, logged(CalorieEstimateBias.STRONGLY_OVERESTIMATE), 1.0)
    }

    @Test
    fun `macros move with the calories so the two still reconcile`() {
        val biased = CalorieBiasAdjuster.apply(
            estimate(uncertaintyPercent = uncertainty),
            CalorieEstimateBias.OVERESTIMATE,
        )

        assertEquals(670.0, biased.calories, 1.0)
        val macroCalories = biased.proteinGrams * 4 + biased.carbohydrateGrams * 4 +
            biased.fatGrams * 9
        assertEquals(biased.calories, macroCalories, 2.0)
    }

    @Test
    fun `a researched value is never biased`() {
        val verified = estimate(uncertaintyPercent = uncertainty).copy(isEstimate = false)

        assertSame(
            verified,
            CalorieBiasAdjuster.apply(verified, CalorieEstimateBias.STRONGLY_OVERESTIMATE),
        )
    }

    @Test
    fun `no bias leaves everything untouched`() {
        val item = estimate(uncertaintyPercent = uncertainty)

        assertSame(item, CalorieBiasAdjuster.apply(item, CalorieEstimateBias.NONE))
    }

    @Test
    fun `a provider that reports no range still responds to the setting`() {
        val withoutRange = estimate(uncertaintyPercent = null)

        val biased = CalorieBiasAdjuster.apply(withoutRange, CalorieEstimateBias.OVERESTIMATE)

        assertTrue(biased.calories > withoutRange.calories)
        assertEquals(600.0 * 1.105, biased.calories, 1.0)
    }

    @Test
    fun `an implausibly wide range cannot move the number arbitrarily far`() {
        val exaggerated = estimate(uncertaintyPercent = 95.0)

        val biased = CalorieBiasAdjuster.apply(exaggerated, CalorieEstimateBias.STRONGLY_OVERESTIMATE)

        // Capped at 40 %, so a model claiming near-total uncertainty cannot double the entry.
        assertEquals(840.0, biased.calories, 1.0)
    }

    private fun estimate(uncertaintyPercent: Double?) = AnalyzedFoodItem(
        name = "Homemade curry",
        quantity = 1.0,
        unit = "serving",
        gramsEquivalent = 450.0,
        calories = 600.0,
        proteinGrams = 30.0,
        carbohydrateGrams = 60.0,
        fatGrams = 26.7,
        isEstimate = true,
        uncertaintyPercent = uncertaintyPercent,
    )
}
