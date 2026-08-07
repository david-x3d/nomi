package com.nomi.app.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EnergyCalculatorTest {
    private val today = LocalDate.of(2025, 6, 15)

    @Test
    fun `age calculation changes on birthday instead of storing a fixed age`() {
        val birthday = LocalDate.of(1990, 6, 15)

        assertEquals(34, AgeCalculator.calculate(birthday, today.minusDays(1)))
        assertEquals(35, AgeCalculator.calculate(birthday, today))
        assertEquals(35, AgeCalculator.calculate(birthday, today.plusDays(1)))
    }

    @Test
    fun `leap-day birthday is handled by LocalDate calendar rules`() {
        val birthday = LocalDate.of(2000, 2, 29)

        assertEquals(20, AgeCalculator.calculate(birthday, LocalDate.of(2021, 2, 27)))
        assertEquals(21, AgeCalculator.calculate(birthday, LocalDate.of(2021, 2, 28)))
    }

    @Test
    fun `future date of birth is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            AgeCalculator.calculate(today.plusDays(1), today)
        }
    }

    @Test
    fun `Mifflin St Jeor uses separate male and female constants`() {
        val male = EnergyCalculator.mifflinStJeorBmr(
            weightKg = 80.0,
            heightCm = 180.0,
            ageYears = 35,
            energySex = EnergySex.MALE,
        )
        val female = EnergyCalculator.mifflinStJeorBmr(
            weightKg = 80.0,
            heightCm = 180.0,
            ageYears = 35,
            energySex = EnergySex.FEMALE,
        )

        assertEquals(1_755.0, male, 1e-9)
        assertEquals(1_589.0, female, 1e-9)
        assertEquals(166.0, male - female, 1e-9)
    }

    @Test
    fun `TDEE is exact BMR times realistic activity multiplier`() {
        assertEquals(
            2_720.25,
            EnergyCalculator.maintenanceCalories(1_755.0, ActivityLevel.ACTIVE),
            1e-9,
        )
        assertEquals(1.2, ActivityLevel.SEDENTARY.multiplier, 0.0)
        assertEquals(1.375, ActivityLevel.LIGHTLY_ACTIVE.multiplier, 0.0)
        assertEquals(1.55, ActivityLevel.ACTIVE.multiplier, 0.0)
        assertEquals(1.725, ActivityLevel.VERY_ACTIVE.multiplier, 0.0)
    }

    @Test
    fun `maintain plan uses TDEE with no goal adjustment`() {
        val plan = EnergyCalculator.calculate(baseDraft(goalType = GoalType.MAINTAIN), today)

        assertEquals(35, plan.ageYears)
        assertEquals(1_755.0, plan.bmrKcal!!, 1e-9)
        assertEquals(2_106.0, plan.tdeeKcal!!, 1e-9)
        assertEquals(0.0, plan.goalAdjustmentKcal, 1e-9)
        assertEquals(2_106.0, plan.exactCaloriesKcal, 1e-9)
        assertEquals(2_110, plan.caloriesKcal)
        assertEquals(0.0, plan.expectedWeeklyWeightChangeKg, 1e-9)
        assertNull(plan.targetWeightKg)
        assertNull(plan.estimatedWeeksToGoal)
        assertFalse(plan.safetyLimitApplied)
    }

    @Test
    fun `moderate loss plan applies deficit and estimates weekly trend`() {
        val plan = EnergyCalculator.calculate(
            baseDraft(
                goalType = GoalType.LOSE,
                targetWeightKg = 70.0,
                progressRate = ProgressRate.MODERATE,
            ),
            today,
        )

        assertEquals(-450.0, plan.requestedGoalAdjustmentKcal, 1e-9)
        assertEquals(-450.0, plan.goalAdjustmentKcal, 1e-9)
        assertEquals(1_656.0, plan.exactCaloriesKcal, 1e-9)
        assertEquals(1_660, plan.caloriesKcal)
        assertEquals(-450.0 * 7.0 / 7_700.0, plan.expectedWeeklyWeightChangeKg, 1e-12)
        assertEquals(10.0 / (450.0 * 7.0 / 7_700.0), plan.estimatedWeeksToGoal!!, 1e-9)
        assertFalse(plan.safetyLimitApplied)
    }

    @Test
    fun `moderate gain plan applies conservative surplus and estimates trajectory`() {
        val plan = EnergyCalculator.calculate(
            baseDraft(
                goalType = GoalType.GAIN,
                targetWeightKg = 90.0,
                progressRate = ProgressRate.MODERATE,
            ),
            today,
        )

        assertEquals(250.0, plan.goalAdjustmentKcal, 1e-9)
        assertEquals(2_356.0, plan.exactCaloriesKcal, 1e-9)
        assertEquals(2_360, plan.caloriesKcal)
        assertEquals(250.0 * 7.0 / 7_700.0, plan.expectedWeeklyWeightChangeKg, 1e-12)
        assertTrue(plan.estimatedWeeksToGoal!! > 0.0)
        assertFalse(plan.safetyLimitApplied)
    }

    @Test
    fun `automatic loss target is bounded by deficit percentage and calorie floor`() {
        val plan = EnergyCalculator.calculate(
            baseDraft(
                heightCm = 170.0,
                currentWeightKg = 70.0,
                goalType = GoalType.LOSE,
                targetWeightKg = 60.0,
                progressRate = ProgressRate.FASTER,
            ),
            today,
        )

        assertTrue(plan.safetyLimitApplied)
        assertTrue(plan.exactCaloriesKcal >= 1_500.0)
        assertTrue(plan.goalAdjustmentKcal >= -(plan.tdeeKcal!! * 0.25) - 1e-9)
        assertTrue(plan.expectedWeeklyWeightChangeKg < 0.0)
    }

    @Test
    fun `extreme custom rate is conservatively bounded and retains requested math`() {
        val plan = EnergyCalculator.calculate(
            baseDraft(
                goalType = GoalType.LOSE,
                targetWeightKg = 70.0,
                progressRate = ProgressRate.CUSTOM,
                customWeeklyChangeKg = 3.0,
            ),
            today,
        )

        assertEquals(-3.0 * 7_700.0 / 7.0, plan.requestedGoalAdjustmentKcal, 1e-9)
        assertTrue(plan.safetyLimitApplied)
        assertTrue(plan.expectedWeeklyWeightChangeKg > -3.0)
        assertTrue(plan.expectedWeeklyWeightChangeKg < 0.0)
    }

    @Test
    fun `manual energy selection preserves exact custom target without fabricated BMR`() {
        val plan = EnergyCalculator.calculate(
            baseDraft(
                energySex = EnergySex.MANUAL,
                heightCm = null,
                activityLevel = null,
                goalType = GoalType.MAINTAIN,
                customCalorieTarget = 2_003,
            ),
            today,
        )

        assertNull(plan.bmrKcal)
        assertNull(plan.tdeeKcal)
        assertEquals(2_003.0, plan.exactCaloriesKcal, 0.0)
        assertEquals(2_003, plan.caloriesKcal)
        assertTrue(plan.isManualTarget)
        assertTrue(plan.isCalorieCustomized)
        assertEquals(2_003.0, plan.macroTargets.caloriesKcal, 1e-9)
    }

    @Test
    fun `explicit target override keeps automatic calculation context`() {
        val plan = EnergyCalculator.calculate(
            baseDraft(
                goalType = GoalType.MAINTAIN,
                customCalorieTarget = 1_903,
            ),
            today,
        )

        assertEquals(1_755.0, plan.bmrKcal!!, 1e-9)
        assertEquals(2_106.0, plan.tdeeKcal!!, 1e-9)
        assertEquals(-203.0, plan.goalAdjustmentKcal, 1e-9)
        assertEquals(1_903, plan.caloriesKcal)
        assertTrue(plan.isManualTarget)
        assertTrue(plan.isCalorieCustomized)
    }

    @Test
    fun `custom plan overrides are immutable and update calculation values`() {
        val original = EnergyCalculator.calculate(baseDraft(goalType = GoalType.MAINTAIN), today)
        val adjusted = original.withOverrides(
            caloriesKcal = 2_000,
            proteinGrams = 140,
            carbsGrams = 210,
            fatGrams = 67,
        )

        assertEquals(2_110, original.caloriesKcal)
        assertEquals(2_000, adjusted.caloriesKcal)
        assertEquals(140, adjusted.proteinGrams)
        assertEquals(210, adjusted.carbsGrams)
        assertEquals(67, adjusted.fatGrams)
        assertEquals(-106.0, adjusted.goalAdjustmentKcal, 1e-9)
        assertTrue(adjusted.isCalorieCustomized)
        assertTrue(adjusted.areMacrosCustomized)
    }

    @Test
    fun `invalid or incomplete drafts fail with domain errors`() {
        assertThrows(IllegalArgumentException::class.java) {
            EnergyCalculator.calculate(
                baseDraft(
                    goalType = GoalType.LOSE,
                    targetWeightKg = 90.0,
                    progressRate = ProgressRate.MODERATE,
                ),
                today,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            EnergyCalculator.calculate(
                baseDraft(
                    goalType = GoalType.GAIN,
                    targetWeightKg = 90.0,
                    progressRate = null,
                ),
                today,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            EnergyCalculator.calculate(
                baseDraft(
                    energySex = EnergySex.MANUAL,
                    goalType = GoalType.MAINTAIN,
                    customCalorieTarget = 799,
                ),
                today,
            )
        }
    }

    private fun baseDraft(
        dateOfBirth: LocalDate? = LocalDate.of(1990, 6, 15),
        energySex: EnergySex? = EnergySex.MALE,
        heightCm: Double? = 180.0,
        currentWeightKg: Double? = 80.0,
        goalType: GoalType? = GoalType.MAINTAIN,
        targetWeightKg: Double? = null,
        activityLevel: ActivityLevel? = ActivityLevel.SEDENTARY,
        progressRate: ProgressRate? = null,
        customCalorieTarget: Int? = null,
        customWeeklyChangeKg: Double? = null,
    ) = OnboardingDraft(
        dateOfBirth = dateOfBirth,
        energySex = energySex,
        heightCm = heightCm,
        currentWeightKg = currentWeightKg,
        goalType = goalType,
        targetWeightKg = targetWeightKg,
        activityLevel = activityLevel,
        progressRate = progressRate,
        customCalorieTarget = customCalorieTarget,
        customWeeklyChangeKg = customWeeklyChangeKg,
    )
}
