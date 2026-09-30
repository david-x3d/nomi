package com.nomi.app.ui.onboarding

import com.nomi.app.domain.AgeCalculator
import com.nomi.app.domain.calculator.EnergyCalculator
import com.nomi.app.domain.model.EnergySex
import com.nomi.app.domain.model.GoalType
import com.nomi.app.domain.model.OnboardingDraft
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * The small, true things onboarding can say back while the user is still answering.
 *
 * Every question in onboarding feeds the plan at the end, but until now nothing showed that: the
 * user typed six answers into silence and met the numbers only on the last screen. These are the
 * answers' first consequences, worked out with the same calculator the plan uses, so a note on
 * the activity step and the plan's own maintenance figure can never disagree.
 *
 * Each function returns null until every input it needs is known. A half-filled draft says
 * nothing rather than a number built on a guess.
 */
internal object OnboardingInsights {

    /** Estimated maintenance in kcal/day, rounded to 10 the way the plan rounds its target. */
    fun maintenanceKcal(draft: OnboardingDraft, today: LocalDate): Int? {
        val sex = draft.energySex?.takeIf { it != EnergySex.MANUAL } ?: return null
        val dateOfBirth = draft.dateOfBirth ?: return null
        val heightCm = draft.heightCm ?: return null
        val weightKg = draft.currentWeightKg ?: return null
        val activity = draft.activityLevel ?: return null
        val kcal = runCatching {
            val bmr = EnergyCalculator.mifflinStJeorBmr(
                weightKg = weightKg,
                heightCm = heightCm,
                ageYears = AgeCalculator.calculate(dateOfBirth, today),
                energySex = sex,
            )
            EnergyCalculator.maintenanceCalories(bmr, activity)
        }.getOrNull() ?: return null
        return ((kcal / ROUNDING_KCAL).roundToInt() * ROUNDING_KCAL).takeIf { it > 0 }
    }

    /**
     * How far the target is from today's weight, in kilograms.
     *
     * Only for a target on the right side of the current weight: a loss target above it is a
     * validation error the screen already reports, and a note agreeing with it would contradict
     * that message.
     */
    fun distanceToTargetKg(draft: OnboardingDraft, targetKg: Double?): Double? {
        val current = draft.currentWeightKg?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        val target = targetKg?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        val onTheRightSide = when (draft.goalType) {
            GoalType.LOSE -> target < current
            GoalType.GAIN -> target > current
            else -> false
        }
        return abs(current - target).takeIf { onTheRightSide }
    }

    /**
     * Roughly when the chosen pace reaches the target, from the plan the answers would produce.
     *
     * Using the full calculation rather than the pace's nominal rate matters: a pace the safety
     * limits slow down would otherwise promise a date the plan itself does not. Anything beyond
     * [MAX_WEEKS] is left unsaid, because a date years away reads as discouragement rather than
     * information at the moment someone is choosing how fast to go.
     */
    fun estimatedArrival(draft: OnboardingDraft, today: LocalDate): LocalDate? {
        if (draft.goalType == null || draft.goalType == GoalType.MAINTAIN) return null
        val weeks = runCatching { EnergyCalculator.calculate(draft, today) }.getOrNull()
            ?.estimatedWeeksToGoal
            ?.takeIf { it.isFinite() && it > 0.0 && it <= MAX_WEEKS }
            ?: return null
        return today.plusDays(ceil(weeks * DAYS_PER_WEEK).toLong())
    }

    private const val ROUNDING_KCAL = 10
    private const val DAYS_PER_WEEK = 7.0
    private const val MAX_WEEKS = 104.0
}
