package com.nomi.app.domain.calculator

import com.nomi.app.data.local.entity.NutritionPlanEntity
import com.nomi.app.data.local.entity.UserProfileEntity
import com.nomi.app.data.local.entity.WeightEntryEntity
import com.nomi.app.data.local.model.DailyNutritionTotals
import com.nomi.app.data.preferences.AdaptiveTdeePreferences
import com.nomi.app.domain.EnergyCalculator
import com.nomi.app.domain.EnergySex
import com.nomi.app.domain.GoalType
import com.nomi.app.domain.MacroCalculator
import com.nomi.app.domain.MacroTargets
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

enum class AdaptiveTdeeConfidence {
    LOW,
    MEDIUM,
    HIGH,
}

sealed interface AdaptiveTdeeEstimate {
    /** Not enough weight or food history in the last 28 days to estimate expenditure reliably. */
    data class InsufficientData(
        val loggedDays: Int,
        val requiredLoggedDays: Int,
        val weighInDays: Int,
        val requiredWeighInDays: Int,
        val spanDays: Int,
        val requiredSpanDays: Int,
        val baselineMaintenanceKcal: Int?,
    ) : AdaptiveTdeeEstimate

    data class Ready(
        val estimatedTdeeKcal: Int,
        val baselineMaintenanceKcal: Int,
        val tdeeDeltaFromBaselineKcal: Int,
        val currentTargetKcal: Int,
        val suggestedTargetKcal: Int,
        val suggestedDeltaKcal: Int,
        val suggestedMacros: MacroTargets,
        val averageIntakeKcal: Int,
        val trendWeightStartKg: Double,
        val trendWeightEndKg: Double,
        val weeklyWeightChangeKg: Double,
        val windowDays: Int,
        val loggedDays: Int,
        val weighInDays: Int,
        val confidence: AdaptiveTdeeConfidence,
        val checkInAvailable: Boolean,
        val daysUntilNextCheckIn: Int,
        val lastCheckInDate: LocalDate?,
    ) : AdaptiveTdeeEstimate
}

/**
 * Fully local adaptive expenditure estimator and weekly calorie check-in engine.
 *
 * Uses an exponentially weighted moving average (EWMA) over weigh-ins in the last 28 days to damp
 * daily water/sodium noise, compares actual trend weight change against average logged intake, and
 * proposes gentle weekly calorie adjustments bounded by Nomi's safety floors.
 */
object AdaptiveTdeeCalculator {
    const val WINDOW_DAYS = 28L
    const val REQUIRED_LOGGED_DAYS = 7
    const val REQUIRED_WEIGH_IN_DAYS = 3
    const val REQUIRED_SPAN_DAYS = 10
    const val CHECK_IN_CADENCE_DAYS = 7L

    private const val EWMA_ALPHA = 0.22
    private const val MAX_WEEKLY_ADJUSTMENT_KCAL = 150
    private const val MIN_MEANINGFUL_DELTA_KCAL = 20
    private const val MIN_PLAUSIBLE_DAILY_INTAKE_KCAL = 400.0
    private const val MAX_TDEE_DRIFT_FRACTION = 0.35

    fun evaluate(
        today: LocalDate,
        profile: UserProfileEntity?,
        plan: NutritionPlanEntity?,
        weights: List<WeightEntryEntity>,
        nutrition: List<DailyNutritionTotals>,
        preferences: AdaptiveTdeePreferences,
    ): AdaptiveTdeeEstimate {
        val baselineMaintenance = plan?.maintenanceKcal?.roundToInt()
        val windowStart = today.minusDays(WINDOW_DAYS - 1)

        // Collapse multiple weigh-ins on the same day to the latest reading for that date.
        val dailyWeights = weights
            .mapNotNull { entry ->
                val date = runCatching { LocalDate.parse(entry.localDate) }.getOrNull() ?: return@mapNotNull null
                if (date.isBefore(windowStart) || date.isAfter(today)) return@mapNotNull null
                date to entry
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, list) -> list.maxBy { it.measuredAtEpochMillis }.weightKg }
            .toSortedMap()

        // Exclude today's incomplete day and trivially partial logs (<400 kcal) from intake average.
        val completeNutrition = nutrition
            .mapNotNull { day ->
                val date = runCatching { LocalDate.parse(day.localDate) }.getOrNull() ?: return@mapNotNull null
                if (date.isBefore(windowStart) || !date.isBefore(today)) return@mapNotNull null
                if (day.caloriesKcal < MIN_PLAUSIBLE_DAILY_INTAKE_KCAL) return@mapNotNull null
                date to day.caloriesKcal
            }
            .sortedBy { it.first }

        val spanDays = if (dailyWeights.size >= 2) {
            ChronoUnit.DAYS.between(dailyWeights.firstKey(), dailyWeights.lastKey()).toInt()
        } else {
            0
        }

        if (profile == null ||
            plan == null ||
            completeNutrition.size < REQUIRED_LOGGED_DAYS ||
            dailyWeights.size < REQUIRED_WEIGH_IN_DAYS ||
            spanDays < REQUIRED_SPAN_DAYS
        ) {
            return AdaptiveTdeeEstimate.InsufficientData(
                loggedDays = completeNutrition.size,
                requiredLoggedDays = REQUIRED_LOGGED_DAYS,
                weighInDays = dailyWeights.size,
                requiredWeighInDays = REQUIRED_WEIGH_IN_DAYS,
                spanDays = spanDays,
                requiredSpanDays = REQUIRED_SPAN_DAYS,
                baselineMaintenanceKcal = baselineMaintenance,
            )
        }

        // Compute time-aware EWMA trend weights at both endpoints so a single water-retention
        // spike on the first weigh-in cannot skew the start weight, and irregular gaps between
        // weigh-ins decay proportionally to elapsed days.
        val weightDates = dailyWeights.keys.toList()
        var forwardEwma = dailyWeights.getValue(weightDates.first())
        for (index in 1 until weightDates.size) {
            val deltaDays = ChronoUnit.DAYS.between(weightDates[index - 1], weightDates[index])
                .coerceAtLeast(1L)
                .toDouble()
            val alpha = 1.0 - (1.0 - EWMA_ALPHA).pow(deltaDays)
            val reading = dailyWeights.getValue(weightDates[index])
            forwardEwma = alpha * reading + (1.0 - alpha) * forwardEwma
        }
        val trendEndKg = forwardEwma

        var backwardEwma = dailyWeights.getValue(weightDates.last())
        for (index in weightDates.lastIndex - 1 downTo 0) {
            val deltaDays = ChronoUnit.DAYS.between(weightDates[index], weightDates[index + 1])
                .coerceAtLeast(1L)
                .toDouble()
            val alpha = 1.0 - (1.0 - EWMA_ALPHA).pow(deltaDays)
            val reading = dailyWeights.getValue(weightDates[index])
            backwardEwma = alpha * reading + (1.0 - alpha) * backwardEwma
        }
        val trendStartKg = backwardEwma

        // Prefer intake days aligned to the observed weight span so calories and weight change
        // describe the same period; fall back to the full 28-day window if the span has too few
        // logged days.
        val firstWeightDate = weightDates.first()
        val lastWeightDate = weightDates.last()
        val spanAlignedNutrition = completeNutrition.filter { (date, _) ->
            !date.isBefore(firstWeightDate) && !date.isAfter(lastWeightDate)
        }
        val intakeSample = if (spanAlignedNutrition.size >= REQUIRED_LOGGED_DAYS) {
            spanAlignedNutrition
        } else {
            completeNutrition
        }

        val averageIntake = intakeSample.map { it.second }.average()
        val weightDeltaKg = trendEndKg - trendStartKg
        val dailyEnergyImbalanceKcal = (weightDeltaKg * EnergyCalculator.KCAL_PER_KILOGRAM) / spanDays.toDouble()
        val rawEstimatedTdee = averageIntake - dailyEnergyImbalanceKcal

        // Clamp against baseline to prevent extreme swings from incomplete logging.
        val baseline = (plan.maintenanceKcal ?: plan.calorieTargetKcal).coerceAtLeast(1_000.0)
        val minPlausibleTdee = baseline * (1.0 - MAX_TDEE_DRIFT_FRACTION)
        val maxPlausibleTdee = baseline * (1.0 + MAX_TDEE_DRIFT_FRACTION)
        val clampedTdee = rawEstimatedTdee.coerceIn(minPlausibleTdee, maxPlausibleTdee)
        val estimatedTdeeKcal = roundToTen(clampedTdee)
        val baselineMaintenanceKcal = roundToTen(baseline)

        val currentTargetKcal = plan.calorieTargetKcal.roundToInt()
        val desiredTarget = clampedTdee + (plan.goalAdjustmentKcal ?: 0.0)
        val sex = EnergySex.entries.firstOrNull { it.name == profile.energyCalculationSex }
            ?: EnergySex.FEMALE
        val goalType = GoalType.entries.firstOrNull { it.name == profile.goalType }
            ?: GoalType.MAINTAIN
        val safetyFloor = when (sex) {
            EnergySex.MALE -> 1_500.0
            EnergySex.FEMALE, EnergySex.MANUAL -> 1_200.0
        }
        val boundedDesired = desiredTarget.coerceIn(safetyFloor, 10_000.0)
        val unclampedDelta = boundedDesired - currentTargetKcal
        val clampedDelta = unclampedDelta.coerceIn(
            -MAX_WEEKLY_ADJUSTMENT_KCAL.toDouble(),
            MAX_WEEKLY_ADJUSTMENT_KCAL.toDouble(),
        )
        val suggestedTargetKcal = roundToTen((currentTargetKcal + clampedDelta).coerceAtLeast(safetyFloor))
        val suggestedDeltaKcal = suggestedTargetKcal - currentTargetKcal

        val latestWeightKg = dailyWeights.getValue(weightDates.last())
        val suggestedMacros = MacroCalculator.calculate(
            weightKg = latestWeightKg,
            goalType = goalType,
            calorieTargetKcal = suggestedTargetKcal,
        )

        val lastCheckInDate = preferences.lastCheckInDate
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val lastDismissedDate = preferences.lastDismissedDate
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val latestActionDate = listOfNotNull(lastCheckInDate, lastDismissedDate).maxOrNull()
        val daysSinceAction = latestActionDate?.let { ChronoUnit.DAYS.between(it, today) }
        val daysUntilNext = if (daysSinceAction == null) {
            0
        } else {
            (CHECK_IN_CADENCE_DAYS - daysSinceAction).coerceAtLeast(0L).toInt()
        }

        val confidence = when {
            completeNutrition.size >= 18 && dailyWeights.size >= 10 && spanDays >= 18 ->
                AdaptiveTdeeConfidence.HIGH
            completeNutrition.size >= 12 && dailyWeights.size >= 6 && spanDays >= 14 ->
                AdaptiveTdeeConfidence.MEDIUM
            else -> AdaptiveTdeeConfidence.LOW
        }

        val checkInAvailable = preferences.enabled &&
            daysUntilNext == 0 &&
            abs(suggestedDeltaKcal) >= MIN_MEANINGFUL_DELTA_KCAL

        return AdaptiveTdeeEstimate.Ready(
            estimatedTdeeKcal = estimatedTdeeKcal,
            baselineMaintenanceKcal = baselineMaintenanceKcal,
            tdeeDeltaFromBaselineKcal = estimatedTdeeKcal - baselineMaintenanceKcal,
            currentTargetKcal = currentTargetKcal,
            suggestedTargetKcal = suggestedTargetKcal,
            suggestedDeltaKcal = suggestedDeltaKcal,
            suggestedMacros = suggestedMacros,
            averageIntakeKcal = averageIntake.roundToInt(),
            trendWeightStartKg = trendStartKg,
            trendWeightEndKg = trendEndKg,
            weeklyWeightChangeKg = (weightDeltaKg / spanDays.toDouble()) * 7.0,
            windowDays = spanDays,
            loggedDays = completeNutrition.size,
            weighInDays = dailyWeights.size,
            confidence = confidence,
            checkInAvailable = checkInAvailable,
            daysUntilNextCheckIn = daysUntilNext,
            lastCheckInDate = lastCheckInDate,
        )
    }

    private fun roundToTen(value: Double): Int = ((value / 10.0).roundToInt() * 10)
}
