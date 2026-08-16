package com.nomi.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StepCalorieEstimatorTest {
    @Test
    fun `ten thousand steps use current weight and height`() {
        val estimate = StepCalorieEstimator.estimate(
            steps = 10_000,
            weightKg = 70.0,
            heightCm = 170.0,
        )

        assertEquals(248.1, estimate.activeCaloriesKcal, 0.1)
        assertEquals(6.46, estimate.distanceKilometers!!, 0.001)
        assertTrue(estimate.usesProfileHeight)
    }

    @Test
    fun `energy scales linearly with steps and weight`() {
        val base = StepCalorieEstimator.estimate(steps = 5_000, weightKg = 70.0, heightCm = 170.0)
        val twiceSteps = StepCalorieEstimator.estimate(steps = 10_000, weightKg = 70.0, heightCm = 170.0)
        val twiceWeight = StepCalorieEstimator.estimate(steps = 5_000, weightKg = 140.0, heightCm = 170.0)

        assertEquals(base.activeCaloriesKcal * 2.0, twiceSteps.activeCaloriesKcal, 1e-9)
        assertEquals(base.activeCaloriesKcal * 2.0, twiceWeight.activeCaloriesKcal, 1e-9)
    }

    @Test
    fun `zero steps produce a real zero`() {
        val estimate = StepCalorieEstimator.estimate(steps = 0, weightKg = 80.0, heightCm = 180.0)

        assertEquals(0.0, estimate.activeCaloriesKcal, 0.0)
        assertEquals(0.0, estimate.distanceKilometers!!, 0.0)
    }

    @Test
    fun `manual profile without height uses the study population approximation`() {
        val estimate = StepCalorieEstimator.estimate(
            steps = 10_000,
            weightKg = 70.0,
            heightCm = null,
        )

        assertEquals(250.04, estimate.activeCaloriesKcal, 0.01)
        assertEquals(null, estimate.distanceKilometers)
        assertFalse(estimate.usesProfileHeight)
    }

    @Test
    fun `latest weight wins and starting weight is the fallback`() {
        val latest = StepCalorieEstimator.estimateFromAvailableData(
            steps = 10_000,
            latestWeightKg = 80.0,
            startingWeightKg = 70.0,
            heightCm = 180.0,
        )!!
        val starting = StepCalorieEstimator.estimateFromAvailableData(
            steps = 10_000,
            latestWeightKg = null,
            startingWeightKg = 70.0,
            heightCm = 180.0,
        )!!

        assertEquals(80.0 / 70.0, latest.activeCaloriesKcal / starting.activeCaloriesKcal, 1e-9)
    }

    @Test
    fun `missing steps or weight never invents an estimate`() {
        assertEquals(
            null,
            StepCalorieEstimator.estimateFromAvailableData(
                steps = null,
                latestWeightKg = 80.0,
                startingWeightKg = 70.0,
                heightCm = 180.0,
            ),
        )
        assertEquals(
            null,
            StepCalorieEstimator.estimateFromAvailableData(
                steps = 10_000,
                latestWeightKg = null,
                startingWeightKg = null,
                heightCm = 180.0,
            ),
        )
    }

    @Test
    fun `invalid sensor and profile inputs are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            StepCalorieEstimator.estimate(steps = -1, weightKg = 70.0, heightCm = 170.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            StepCalorieEstimator.estimate(steps = 1_000, weightKg = Double.NaN, heightCm = 170.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            StepCalorieEstimator.estimate(steps = 1_000, weightKg = 70.0, heightCm = 0.0)
        }
    }
}
