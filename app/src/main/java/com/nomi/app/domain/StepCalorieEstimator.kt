package com.nomi.app.domain

import kotlin.math.pow

/**
 * A net-energy estimate for ordinary walking, separate from Health Connect's total activity.
 *
 * [activeCaloriesKcal] excludes resting energy. [distanceKilometers] is present only when the
 * profile contains a height; the energy estimate can still use the study's population shortcut
 * when a manual-calorie profile omitted height.
 */
data class StepCalorieEstimate(
    val steps: Long,
    val activeCaloriesKcal: Double,
    val distanceKilometers: Double?,
    val usesProfileHeight: Boolean,
)

/**
 * Estimates active walking energy from steps and the user's latest known body measurements.
 *
 * Weyand et al. measured net walking cost as `3.80 * height^-0.95 J/(kg*m)` and an economical
 * stride as roughly `0.76 * height`, or `0.38 * height` per individual step. Their practical
 * population approximation is used only when height is absent. Age and the profile's energy-
 * equation selection are intentionally not forced into the result: neither reliably identifies
 * pace, incline, gait or an independent walking-cost correction.
 *
 * Source: Weyand et al., Journal of Experimental Biology 213 (2010), 3972-3979,
 * https://doi.org/10.1242/jeb.048199
 */
object StepCalorieEstimator {
    fun estimateFromAvailableData(
        steps: Long?,
        latestWeightKg: Double?,
        startingWeightKg: Double?,
        heightCm: Double?,
    ): StepCalorieEstimate? {
        val presentSteps = steps ?: return null
        val presentWeight = latestWeightKg ?: startingWeightKg ?: return null
        return estimate(
            steps = presentSteps,
            weightKg = presentWeight,
            heightCm = heightCm,
        )
    }

    fun estimate(
        steps: Long,
        weightKg: Double,
        heightCm: Double?,
    ): StepCalorieEstimate {
        require(steps >= 0L) { "Steps cannot be negative." }
        require(weightKg.isFinite() && weightKg > 0.0) {
            "Weight must be a finite, positive value."
        }
        require(heightCm == null || heightCm.isFinite() && heightCm > 0.0) {
            "Height must be absent or a finite, positive value."
        }

        val heightMeters = heightCm?.div(CENTIMETERS_PER_METER)
        val distanceMeters = heightMeters?.let { height ->
            steps.toDouble() * STEP_LENGTH_IN_BODY_HEIGHTS * height
        }
        val activeCalories = if (heightMeters != null && distanceMeters != null) {
            val joulesPerKilogramMeter =
                NET_WALKING_COST_COEFFICIENT * heightMeters.pow(NET_WALKING_HEIGHT_EXPONENT)
            distanceMeters * weightKg * joulesPerKilogramMeter / JOULES_PER_KILOCALORIE
        } else {
            steps.toDouble() * weightKg * POPULATION_KCAL_PER_STEP_KILOGRAM
        }
        require(activeCalories.isFinite() && activeCalories >= 0.0) {
            "Inputs do not produce a finite walking-energy estimate."
        }

        return StepCalorieEstimate(
            steps = steps,
            activeCaloriesKcal = activeCalories,
            distanceKilometers = distanceMeters?.div(METERS_PER_KILOMETER),
            usesProfileHeight = heightMeters != null,
        )
    }

    private const val CENTIMETERS_PER_METER = 100.0
    private const val METERS_PER_KILOMETER = 1_000.0
    private const val JOULES_PER_KILOCALORIE = 4_184.0
    private const val STEP_LENGTH_IN_BODY_HEIGHTS = 0.38
    private const val NET_WALKING_COST_COEFFICIENT = 3.80
    private const val NET_WALKING_HEIGHT_EXPONENT = -0.95

    /** 0.94 cal/(kg*body-height) multiplied by 0.38 body-heights per step. */
    private const val POPULATION_KCAL_PER_STEP_KILOGRAM = 0.0003572
}
