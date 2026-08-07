package com.nomi.app.ui.onboarding

import androidx.lifecycle.ViewModel
import com.nomi.app.domain.calculator.EnergyCalculator
import com.nomi.app.domain.model.ActivityLevel
import com.nomi.app.domain.model.EnergySex
import com.nomi.app.domain.model.GoalType
import com.nomi.app.domain.model.OnboardingDraft
import com.nomi.app.domain.model.ProgressRate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.Period
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

class OnboardingViewModel(
    initialDraft: OnboardingDraft = OnboardingDraft(),
    private val todayProvider: () -> LocalDate = { LocalDate.now() },
) : ViewModel(), OnboardingActions {
    private val _uiState = MutableStateFlow(initialState(initialDraft))
    internal val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        _uiState.value = recalculate(_uiState.value)
    }

    override fun goBack() {
        update(recalculate = false) { state ->
            val journey = state.activeJourney()
            val index = journey.indexOf(state.currentStep)
            if (index <= 0) state else state.copy(
                currentStep = journey[index - 1],
                navigationDirection = NavigationDirection.BACKWARD,
                validationMessage = null,
            )
        }
    }

    override fun goNext() {
        val current = _uiState.value
        validationMessage(current)?.let { message ->
            _uiState.value = current.copy(validationMessage = message)
            return
        }

        val journey = current.activeJourney()
        val index = journey.indexOf(current.currentStep)
        if (index in 0 until journey.lastIndex) {
            val next = recalculate(current).copy(
                currentStep = journey[index + 1],
                navigationDirection = NavigationDirection.FORWARD,
                validationMessage = null,
            )
            _uiState.value = if (next.currentStep == OnboardingStep.PLAN && next.calculatedPlan == null) {
                next.copy(validationMessage = "We couldn't calculate a plan yet. Check your entries and try again.")
            } else {
                next
            }
        }
    }

    override fun setDateOfBirth(epochDay: Long) {
        update { state ->
            state.copy(
                draft = state.draft.copy(dateOfBirth = LocalDate.ofEpochDay(epochDay)),
                validationMessage = null,
            )
        }
    }

    override fun selectEnergySex(value: EnergySex) {
        update { state ->
            state.copy(
                draft = state.draft.copy(
                    energySex = value,
                    customCalorieTarget = if (value == EnergySex.MANUAL) {
                        state.manualCaloriesText.toIntOrNull()
                    } else {
                        null
                    },
                ),
                validationMessage = null,
            )
        }
    }

    override fun updateManualCalories(value: String) {
        val cleaned = value.filter(Char::isDigit).take(5)
        update { state ->
            state.copy(
                manualCaloriesText = cleaned,
                draft = state.draft.copy(
                    customCalorieTarget = cleaned.toIntOrNull().takeIf {
                        state.draft.energySex == EnergySex.MANUAL
                    },
                ),
                validationMessage = null,
            )
        }
    }

    override fun selectHeightSystem(value: MeasurementSystem) {
        update(recalculate = false) { state ->
            val heightCm = state.draft.heightCm
            state.copy(
                heightSystem = value,
                heightCentimetersText = heightCm?.let(::formatDecimal)
                    ?: state.heightCentimetersText,
                heightFeetText = heightCm?.let(::centimetersToFeet)?.first?.toString()
                    ?: state.heightFeetText,
                heightInchesText = heightCm?.let(::centimetersToFeet)?.second?.let(::formatDecimal)
                    ?: state.heightInchesText,
                validationMessage = null,
            )
        }
    }

    override fun updateHeightCentimeters(value: String) {
        val cleaned = sanitizeDecimal(value, maxIntegerDigits = 3)
        update { state ->
            val centimeters = cleaned.toDoubleOrNull()
            val imperial = centimeters?.let(::centimetersToFeet)
            state.copy(
                heightCentimetersText = cleaned,
                heightFeetText = imperial?.first?.toString() ?: state.heightFeetText,
                heightInchesText = imperial?.second?.let(::formatDecimal) ?: state.heightInchesText,
                draft = state.draft.copy(heightCm = centimeters),
                validationMessage = null,
            )
        }
    }

    override fun updateHeightFeet(value: String) {
        val cleaned = value.filter(Char::isDigit).take(1)
        updateHeightImperial(feet = cleaned)
    }

    override fun updateHeightInches(value: String) {
        val cleaned = sanitizeDecimal(value, maxIntegerDigits = 2)
        updateHeightImperial(inches = cleaned)
    }

    private fun updateHeightImperial(feet: String? = null, inches: String? = null) {
        update { state ->
            val newFeet = feet ?: state.heightFeetText
            val newInches = inches ?: state.heightInchesText
            val centimeters = imperialToCentimeters(
                feet = newFeet.toIntOrNull(),
                inches = newInches.toDoubleOrNull(),
            )
            state.copy(
                heightFeetText = newFeet,
                heightInchesText = newInches,
                heightCentimetersText = centimeters?.let(::formatDecimal)
                    ?: state.heightCentimetersText,
                draft = state.draft.copy(heightCm = centimeters),
                validationMessage = null,
            )
        }
    }

    override fun selectWeightSystem(value: MeasurementSystem) {
        update(recalculate = false) { state ->
            val currentKg = state.draft.currentWeightKg
            val targetKg = state.draft.targetWeightKg ?: state.rememberedTargetWeightKg
            state.copy(
                weightSystem = value,
                currentWeightKilogramsText = currentKg?.let(::formatDecimal)
                    ?: state.currentWeightKilogramsText,
                currentWeightPoundsText = currentKg?.let(::kilogramsToPounds)?.let(::formatDecimal)
                    ?: state.currentWeightPoundsText,
                targetWeightKilogramsText = targetKg?.let(::formatDecimal)
                    ?: state.targetWeightKilogramsText,
                targetWeightPoundsText = targetKg?.let(::kilogramsToPounds)?.let(::formatDecimal)
                    ?: state.targetWeightPoundsText,
                validationMessage = null,
            )
        }
    }

    override fun updateCurrentWeightKilograms(value: String) {
        val cleaned = sanitizeDecimal(value, maxIntegerDigits = 3)
        update { state ->
            val kilograms = cleaned.toDoubleOrNull()
            state.copy(
                currentWeightKilogramsText = cleaned,
                currentWeightPoundsText = kilograms?.let(::kilogramsToPounds)?.let(::formatDecimal)
                    ?: state.currentWeightPoundsText,
                draft = state.draft.copy(currentWeightKg = kilograms),
                validationMessage = null,
            )
        }
    }

    override fun updateCurrentWeightPounds(value: String) {
        val cleaned = sanitizeDecimal(value, maxIntegerDigits = 3)
        update { state ->
            val kilograms = cleaned.toDoubleOrNull()?.let(::poundsToKilograms)
            state.copy(
                currentWeightPoundsText = cleaned,
                currentWeightKilogramsText = kilograms?.let(::formatDecimal)
                    ?: state.currentWeightKilogramsText,
                draft = state.draft.copy(currentWeightKg = kilograms),
                validationMessage = null,
            )
        }
    }

    override fun selectGoal(value: GoalType) {
        update { state ->
            val target = state.draft.targetWeightKg ?: state.rememberedTargetWeightKg
            val clearConditionalValues = state.draft.goalType != value
            state.copy(
                draft = state.draft.copy(
                    goalType = value,
                    targetWeightKg = if (value == GoalType.MAINTAIN) null else target,
                    progressRate = if (value == GoalType.MAINTAIN || clearConditionalValues) null else state.draft.progressRate,
                    customWeeklyChangeKg = if (value == GoalType.MAINTAIN || clearConditionalValues) {
                        null
                    } else {
                        state.draft.customWeeklyChangeKg
                    },
                ),
                rememberedTargetWeightKg = target,
                validationMessage = null,
            )
        }
    }

    override fun updateTargetWeightKilograms(value: String) {
        val cleaned = sanitizeDecimal(value, maxIntegerDigits = 3)
        update { state ->
            val kilograms = cleaned.toDoubleOrNull()
            state.copy(
                targetWeightKilogramsText = cleaned,
                targetWeightPoundsText = kilograms?.let(::kilogramsToPounds)?.let(::formatDecimal)
                    ?: state.targetWeightPoundsText,
                rememberedTargetWeightKg = kilograms,
                draft = state.draft.copy(targetWeightKg = kilograms),
                validationMessage = null,
            )
        }
    }

    override fun updateTargetWeightPounds(value: String) {
        val cleaned = sanitizeDecimal(value, maxIntegerDigits = 3)
        update { state ->
            val kilograms = cleaned.toDoubleOrNull()?.let(::poundsToKilograms)
            state.copy(
                targetWeightPoundsText = cleaned,
                targetWeightKilogramsText = kilograms?.let(::formatDecimal)
                    ?: state.targetWeightKilogramsText,
                rememberedTargetWeightKg = kilograms,
                draft = state.draft.copy(targetWeightKg = kilograms),
                validationMessage = null,
            )
        }
    }

    override fun selectActivity(value: ActivityLevel) {
        update { state ->
            state.copy(
                draft = state.draft.copy(activityLevel = value),
                validationMessage = null,
            )
        }
    }

    override fun selectProgressRate(value: ProgressRate) {
        update { state ->
            state.copy(
                draft = state.draft.copy(
                    progressRate = value,
                    customWeeklyChangeKg = if (value == ProgressRate.CUSTOM) {
                        state.customWeeklyChangeText.toDoubleOrNull()
                    } else {
                        null
                    },
                ),
                validationMessage = null,
            )
        }
    }

    override fun updateCustomWeeklyChange(value: String) {
        val cleaned = sanitizeDecimal(value, maxIntegerDigits = 1)
        update { state ->
            state.copy(
                customWeeklyChangeText = cleaned,
                draft = state.draft.copy(
                    customWeeklyChangeKg = cleaned.toDoubleOrNull().takeIf {
                        state.draft.progressRate == ProgressRate.CUSTOM
                    },
                ),
                validationMessage = null,
            )
        }
    }

    override fun toggleCalculationBreakdown() {
        update(recalculate = false) { state ->
            state.copy(isCalculationExpanded = !state.isCalculationExpanded)
        }
    }

    override fun togglePlanEditor() {
        update(recalculate = false) { state ->
            state.copy(
                isPlanEditorExpanded = !state.isPlanEditorExpanded,
                planEditor = state.planEditor.copy(validationMessage = null),
            )
        }
    }

    override fun updatePlanCalories(value: String) = updatePlanEditor {
        copy(calories = value.filter(Char::isDigit).take(5), validationMessage = null)
    }

    override fun updatePlanProtein(value: String) = updatePlanEditor {
        copy(proteinGrams = value.filter(Char::isDigit).take(4), validationMessage = null)
    }

    override fun updatePlanCarbohydrates(value: String) = updatePlanEditor {
        copy(carbohydrateGrams = value.filter(Char::isDigit).take(4), validationMessage = null)
    }

    override fun updatePlanFat(value: String) = updatePlanEditor {
        copy(fatGrams = value.filter(Char::isDigit).take(4), validationMessage = null)
    }

    override fun updatePlanWeeklyChange(value: String) = updatePlanEditor {
        copy(weeklyChangeKg = sanitizeDecimal(value, maxIntegerDigits = 1), validationMessage = null)
    }

    override fun applyPlanEdits() {
        // NutritionPlan is copied here once its primitive overrides have passed UI validation.
        val state = _uiState.value
        val plan = state.calculatedPlan ?: return
        val calories = state.planEditor.calories.toIntOrNull()
        val protein = state.planEditor.proteinGrams.toIntOrNull()
        val carbohydrates = state.planEditor.carbohydrateGrams.toIntOrNull()
        val fat = state.planEditor.fatGrams.toIntOrNull()
        val weeklyChange = state.planEditor.weeklyChangeKg.toDoubleOrNull()
        val message = when {
            calories == null || calories !in 800..6000 -> "Enter a calorie target between 800 and 6,000 kcal."
            protein == null || protein !in 0..600 -> "Enter protein between 0 and 600 g."
            carbohydrates == null || carbohydrates !in 0..900 -> "Enter carbohydrates between 0 and 900 g."
            fat == null || fat !in 0..300 -> "Enter fat between 0 and 300 g."
            state.draft.goalType != GoalType.MAINTAIN &&
                (weeklyChange == null || weeklyChange !in 0.05..1.5) ->
                "Enter a weekly change between 0.05 and 1.5 kg."
            else -> null
        }
        if (message != null) {
            _uiState.value = state.copy(planEditor = state.planEditor.copy(validationMessage = message))
            return
        }

        _uiState.value = state.copy(
            finalPlan = plan.withUiOverrides(
                calories = calories!!,
                proteinGrams = protein!!,
                carbohydrateGrams = carbohydrates!!,
                fatGrams = fat!!,
                weeklyChangeKg = weeklyChange ?: 0.0,
            ),
            isPlanEditorExpanded = false,
            planEditor = state.planEditor.copy(validationMessage = null),
        )
    }

    private fun updatePlanEditor(transform: PlanEditorState.() -> PlanEditorState) {
        update(recalculate = false) { state -> state.copy(planEditor = state.planEditor.transform()) }
    }

    private fun update(
        recalculate: Boolean = true,
        transform: (OnboardingUiState) -> OnboardingUiState,
    ) {
        val changed = transform(_uiState.value)
        _uiState.value = if (recalculate) recalculate(changed) else changed
    }

    private fun recalculate(state: OnboardingUiState): OnboardingUiState {
        if (!isComplete(state.draft)) {
            return state.copy(calculatedPlan = null, finalPlan = null)
        }
        val result = runCatching {
            EnergyCalculator.calculate(draft = state.draft, today = todayProvider())
        }
        return result.fold(
            onSuccess = { plan ->
                state.copy(
                    calculatedPlan = plan,
                    finalPlan = plan,
                    planEditor = plan.toEditorState(),
                )
            },
            onFailure = {
                state.copy(calculatedPlan = null, finalPlan = null)
            },
        )
    }

    private fun validationMessage(state: OnboardingUiState): String? = when (state.currentStep) {
        OnboardingStep.WELCOME -> null
        OnboardingStep.DATE_OF_BIRTH -> when {
            state.draft.dateOfBirth == null -> "Choose your date of birth."
            state.draft.dateOfBirth.isAfter(todayProvider()) -> "Date of birth can't be in the future."
            Period.between(state.draft.dateOfBirth, todayProvider()).years !in 13..120 ->
                "Nomi supports ages 13 to 120."
            else -> null
        }
        OnboardingStep.ENERGY_SEX -> when {
            state.draft.energySex == null -> "Choose an energy calculation option."
            state.draft.energySex == EnergySex.MANUAL &&
                state.draft.customCalorieTarget !in 800..6000 ->
                "Enter a daily target between 800 and 6,000 kcal."
            else -> null
        }
        OnboardingStep.HEIGHT -> if (state.draft.heightCm?.let { it in 100.0..250.0 } == true) {
            null
        } else {
            "Enter a height between 100 and 250 cm."
        }
        OnboardingStep.WEIGHT -> if (state.draft.currentWeightKg?.let { it in 30.0..400.0 } == true) {
            null
        } else {
            "Enter a weight between 30 and 400 kg."
        }
        OnboardingStep.GOAL -> if (state.draft.goalType == null) "Choose a goal." else null
        OnboardingStep.TARGET_WEIGHT -> when {
            state.draft.targetWeightKg?.let { it in 30.0..400.0 } != true ->
                "Enter a target weight between 30 and 400 kg."
            state.draft.goalType == GoalType.LOSE &&
                state.draft.targetWeightKg >= state.draft.currentWeightKg ->
                "For weight loss, choose a target below your current weight."
            state.draft.goalType == GoalType.GAIN &&
                state.draft.targetWeightKg <= state.draft.currentWeightKg ->
                "For weight gain, choose a target above your current weight."
            else -> null
        }
        OnboardingStep.ACTIVITY -> if (state.draft.activityLevel == null) {
            "Choose the activity level that best matches a normal week."
        } else {
            null
        }
        OnboardingStep.PROGRESS_RATE -> when {
            state.draft.progressRate == null -> "Choose a progress rate."
            state.draft.progressRate == ProgressRate.CUSTOM &&
                state.draft.customWeeklyChangeKg?.let { it in 0.05..1.5 } != true ->
                "Enter a weekly change between 0.05 and 1.5 kg."
            else -> null
        }
        OnboardingStep.PLAN -> if (state.finalPlan == null) "Your plan isn't ready yet." else null
    }

    private fun isComplete(draft: OnboardingDraft): Boolean =
        draft.dateOfBirth != null &&
            draft.energySex != null &&
            (draft.energySex != EnergySex.MANUAL || draft.customCalorieTarget != null) &&
            draft.heightCm != null &&
            draft.currentWeightKg != null &&
            draft.goalType != null &&
            (draft.goalType == GoalType.MAINTAIN || draft.targetWeightKg != null) &&
            draft.activityLevel != null &&
            (draft.goalType == GoalType.MAINTAIN || draft.progressRate != null) &&
            (draft.progressRate != ProgressRate.CUSTOM || draft.customWeeklyChangeKg != null)

    private companion object {
        const val POUNDS_PER_KILOGRAM = 2.2046226218
        const val CENTIMETERS_PER_INCH = 2.54

        fun initialState(draft: OnboardingDraft): OnboardingUiState {
            val imperialHeight = draft.heightCm?.let(::centimetersToFeet)
            return OnboardingUiState(
                draft = draft,
                manualCaloriesText = draft.customCalorieTarget?.toString().orEmpty(),
                heightCentimetersText = draft.heightCm?.let(::formatDecimal).orEmpty(),
                heightFeetText = imperialHeight?.first?.toString().orEmpty(),
                heightInchesText = imperialHeight?.second?.let(::formatDecimal).orEmpty(),
                currentWeightKilogramsText = draft.currentWeightKg?.let(::formatDecimal).orEmpty(),
                currentWeightPoundsText = draft.currentWeightKg?.let(::kilogramsToPounds)
                    ?.let(::formatDecimal).orEmpty(),
                targetWeightKilogramsText = draft.targetWeightKg?.let(::formatDecimal).orEmpty(),
                targetWeightPoundsText = draft.targetWeightKg?.let(::kilogramsToPounds)
                    ?.let(::formatDecimal).orEmpty(),
                customWeeklyChangeText = draft.customWeeklyChangeKg?.let(::formatDecimal).orEmpty(),
                rememberedTargetWeightKg = draft.targetWeightKg,
            )
        }

        fun sanitizeDecimal(value: String, maxIntegerDigits: Int): String {
            val normalized = value.replace(',', '.')
            val output = StringBuilder()
            var hasDecimal = false
            var integerDigits = 0
            var fractionalDigits = 0
            normalized.forEach { character ->
                when {
                    character.isDigit() && !hasDecimal && integerDigits < maxIntegerDigits -> {
                        output.append(character)
                        integerDigits++
                    }
                    character == '.' && !hasDecimal -> {
                        if (output.isEmpty()) output.append('0')
                        output.append(character)
                        hasDecimal = true
                    }
                    character.isDigit() && hasDecimal && fractionalDigits < 2 -> {
                        output.append(character)
                        fractionalDigits++
                    }
                }
            }
            return output.toString()
        }

        fun centimetersToFeet(centimeters: Double): Pair<Int, Double> {
            val totalInches = centimeters / CENTIMETERS_PER_INCH
            var feet = floor(totalInches / 12.0).toInt()
            var inches = ((totalInches - feet * 12.0) * 10.0).roundToInt() / 10.0
            if (inches >= 12.0) {
                feet += 1
                inches = 0.0
            }
            return feet to inches
        }

        fun imperialToCentimeters(feet: Int?, inches: Double?): Double? {
            if (feet == null || inches == null || inches !in 0.0..<12.0) return null
            return (feet * 12.0 + inches) * CENTIMETERS_PER_INCH
        }

        fun kilogramsToPounds(kilograms: Double): Double = kilograms * POUNDS_PER_KILOGRAM

        fun poundsToKilograms(pounds: Double): Double = pounds / POUNDS_PER_KILOGRAM

        fun formatDecimal(value: Double): String = String.format(Locale.US, "%.1f", value)
            .removeSuffix(".0")
    }
}
