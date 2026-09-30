package com.nomi.app.ui.onboarding

import com.nomi.app.domain.model.ActivityLevel
import com.nomi.app.domain.model.EnergySex
import com.nomi.app.domain.model.GoalType
import com.nomi.app.domain.model.OnboardingDraft
import com.nomi.app.domain.model.ProgressRate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * The notes onboarding shows while the user answers must agree with the plan it produces, and
 * must say nothing until they have something true to say.
 */
class OnboardingInsightsTest {

    private val today = LocalDate.of(2026, 9, 30)

    private val complete = OnboardingDraft(
        dateOfBirth = LocalDate.of(1990, 6, 15),
        energySex = EnergySex.FEMALE,
        heightCm = 170.0,
        currentWeightKg = 70.0,
        goalType = GoalType.LOSE,
        targetWeightKg = 65.0,
        activityLevel = ActivityLevel.ACTIVE,
        progressRate = ProgressRate.MODERATE,
    )

    @Test
    fun `maintenance matches the plan's equation, rounded to 10 kcal`() {
        // Mifflin-St Jeor: 10 x 70 + 6.25 x 170 - 5 x 36 - 161 = 1421.5, x 1.55 = 2203.3.
        assertEquals(2200, OnboardingInsights.maintenanceKcal(complete, today))
    }

    @Test
    fun `maintenance says nothing before activity is chosen or for a manual target`() {
        assertNull(OnboardingInsights.maintenanceKcal(complete.copy(activityLevel = null), today))
        assertNull(OnboardingInsights.maintenanceKcal(complete.copy(energySex = EnergySex.MANUAL), today))
    }

    @Test
    fun `distance is shown only for a target on the right side`() {
        assertEquals(5.0, OnboardingInsights.distanceToTargetKg(complete, 65.0)!!, 1e-9)
        assertNull(OnboardingInsights.distanceToTargetKg(complete, 75.0))
        assertNull(OnboardingInsights.distanceToTargetKg(complete.copy(goalType = GoalType.MAINTAIN), 65.0))
        assertNull(OnboardingInsights.distanceToTargetKg(complete, null))
    }

    @Test
    fun `arrival follows the plan's own pace`() {
        // 450 kcal/day below maintenance is 0.409 kg a week, so 5 kg takes 12.2 weeks = 86 days.
        assertEquals(LocalDate.of(2026, 12, 25), OnboardingInsights.estimatedArrival(complete, today))
    }

    @Test
    fun `arrival says nothing without a pace or for maintenance`() {
        assertNull(OnboardingInsights.estimatedArrival(complete.copy(progressRate = null), today))
        assertNull(OnboardingInsights.estimatedArrival(complete.copy(goalType = GoalType.MAINTAIN), today))
    }

    @Test
    fun `a date more than two years out is left unsaid`() {
        val farAway = complete.copy(targetWeightKg = 40.0, progressRate = ProgressRate.GENTLE)

        assertNull(OnboardingInsights.estimatedArrival(farAway, today))
    }
}
