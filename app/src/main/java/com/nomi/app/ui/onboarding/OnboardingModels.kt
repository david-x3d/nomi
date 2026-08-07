package com.nomi.app.ui.onboarding

import com.nomi.app.domain.model.OnboardingDraft
import com.nomi.app.domain.model.NutritionPlan

internal enum class OnboardingStep {
    WELCOME,
    DATE_OF_BIRTH,
    ENERGY_SEX,
    HEIGHT,
    WEIGHT,
    GOAL,
    TARGET_WEIGHT,
    ACTIVITY,
    PROGRESS_RATE,
    PLAN,
}

enum class MeasurementSystem {
    METRIC,
    IMPERIAL,
}

internal enum class NavigationDirection {
    FORWARD,
    BACKWARD,
}

internal data class PlanEditorState(
    val calories: String = "",
    val proteinGrams: String = "",
    val carbohydrateGrams: String = "",
    val fatGrams: String = "",
    val weeklyChangeKg: String = "",
    val validationMessage: String? = null,
)

internal data class OnboardingUiState(
    val currentStep: OnboardingStep = OnboardingStep.WELCOME,
    val navigationDirection: NavigationDirection = NavigationDirection.FORWARD,
    val draft: OnboardingDraft = OnboardingDraft(),
    val heightSystem: MeasurementSystem = MeasurementSystem.METRIC,
    val weightSystem: MeasurementSystem = MeasurementSystem.METRIC,
    val manualCaloriesText: String = "",
    val heightCentimetersText: String = "",
    val heightFeetText: String = "",
    val heightInchesText: String = "",
    val currentWeightKilogramsText: String = "",
    val currentWeightPoundsText: String = "",
    val targetWeightKilogramsText: String = "",
    val targetWeightPoundsText: String = "",
    val customWeeklyChangeText: String = "",
    val rememberedTargetWeightKg: Double? = null,
    val calculatedPlan: NutritionPlan? = null,
    val finalPlan: NutritionPlan? = null,
    val planEditor: PlanEditorState = PlanEditorState(),
    val isCalculationExpanded: Boolean = false,
    val isPlanEditorExpanded: Boolean = false,
    val validationMessage: String? = null,
) {
    val progress: Float
        get() {
            val journey = activeJourney()
            return ((journey.indexOf(currentStep).coerceAtLeast(0) + 1).toFloat() / journey.size)
                .coerceIn(0f, 1f)
        }

    fun activeJourney(): List<OnboardingStep> = buildList {
        add(OnboardingStep.WELCOME)
        add(OnboardingStep.DATE_OF_BIRTH)
        add(OnboardingStep.ENERGY_SEX)
        add(OnboardingStep.HEIGHT)
        add(OnboardingStep.WEIGHT)
        add(OnboardingStep.GOAL)
        if (draft.goalType?.name != "MAINTAIN") add(OnboardingStep.TARGET_WEIGHT)
        add(OnboardingStep.ACTIVITY)
        if (draft.goalType?.name != "MAINTAIN") add(OnboardingStep.PROGRESS_RATE)
        add(OnboardingStep.PLAN)
    }
}

interface OnboardingActions {
    fun goBack()
    fun goNext()
    fun setDateOfBirth(epochDay: Long)
    fun selectEnergySex(value: com.nomi.app.domain.model.EnergySex)
    fun updateManualCalories(value: String)
    fun selectHeightSystem(value: MeasurementSystem)
    fun updateHeightCentimeters(value: String)
    fun updateHeightFeet(value: String)
    fun updateHeightInches(value: String)
    fun selectWeightSystem(value: MeasurementSystem)
    fun updateCurrentWeightKilograms(value: String)
    fun updateCurrentWeightPounds(value: String)
    fun selectGoal(value: com.nomi.app.domain.model.GoalType)
    fun updateTargetWeightKilograms(value: String)
    fun updateTargetWeightPounds(value: String)
    fun selectActivity(value: com.nomi.app.domain.model.ActivityLevel)
    fun selectProgressRate(value: com.nomi.app.domain.model.ProgressRate)
    fun updateCustomWeeklyChange(value: String)
    fun toggleCalculationBreakdown()
    fun togglePlanEditor()
    fun updatePlanCalories(value: String)
    fun updatePlanProtein(value: String)
    fun updatePlanCarbohydrates(value: String)
    fun updatePlanFat(value: String)
    fun updatePlanWeeklyChange(value: String)
    fun applyPlanEdits()
}
