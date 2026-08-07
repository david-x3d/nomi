package com.nomi.app.domain.calculator

import com.nomi.app.domain.ActivityLevel
import com.nomi.app.domain.EnergySex
import com.nomi.app.domain.OnboardingDraft
import com.nomi.app.domain.NutritionPlan
import java.time.LocalDate
import com.nomi.app.domain.EnergyCalculator as DomainEnergyCalculator

/** Stable layered-package facade for UI/use-case callers. */
object EnergyCalculator {
    const val KCAL_PER_KILOGRAM = DomainEnergyCalculator.KCAL_PER_KILOGRAM

    fun calculate(draft: OnboardingDraft, today: LocalDate): NutritionPlan =
        DomainEnergyCalculator.calculate(draft, today)

    fun mifflinStJeorBmr(
        weightKg: Double,
        heightCm: Double,
        ageYears: Int,
        energySex: EnergySex,
    ): Double = DomainEnergyCalculator.mifflinStJeorBmr(
        weightKg = weightKg,
        heightCm = heightCm,
        ageYears = ageYears,
        energySex = energySex,
    )

    fun maintenanceCalories(bmrKcal: Double, activityLevel: ActivityLevel): Double =
        DomainEnergyCalculator.maintenanceCalories(bmrKcal, activityLevel)
}
