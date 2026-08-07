package com.nomi.app.ui.logging

import com.nomi.app.ai.model.AiProcessingStage
import com.nomi.app.ai.model.AnalyzedFoodItem
import com.nomi.app.ai.model.FoodAnalysis
import com.nomi.app.ai.model.PortionAdjustment
import com.nomi.app.ai.model.PortionContext
import com.nomi.app.ui.today.MealCategory

sealed interface FoodLoggingUiState {
    data class Input(
        val text: String = "",
        val mealCategory: MealCategory = MealCategory.SNACKS,
    ) : FoodLoggingUiState

    data class Processing(
        val stage: AiProcessingStage,
        val originalText: String = "",
        val sourceUrls: List<String> = emptyList(),
    ) : FoodLoggingUiState

    data class Preview(
        val analysis: FoodAnalysis,
        val mealCategory: MealCategory,
        val originalText: String = "",
    ) : FoodLoggingUiState

    data class Error(
        val message: String,
        val canRetry: Boolean = true,
        val originalText: String = "",
    ) : FoodLoggingUiState
    data class Manual(val draft: ManualFoodDraft = ManualFoodDraft()) : FoodLoggingUiState
}

data class ManualFoodDraft(
    val name: String = "",
    val amount: String = "",
    val unit: String = "g",
    val calories: String = "",
    val protein: String = "",
    val carbohydrates: String = "",
    val fat: String = "",
    val mealCategory: MealCategory = MealCategory.SNACKS,
) {
    val isValid: Boolean
        get() = name.isNotBlank() && amount.toDoubleOrNull()?.let { it > 0 } == true &&
            calories.toDoubleOrNull()?.let { it >= 0 } == true &&
            protein.toDoubleOrNull()?.let { it >= 0 } == true &&
            carbohydrates.toDoubleOrNull()?.let { it >= 0 } == true &&
            fat.toDoubleOrNull()?.let { it >= 0 } == true
}

data class PortionEditUiState(
    val current: PortionContext,
    val correction: String = "",
    val proposed: PortionAdjustment? = null,
    val isProcessing: Boolean = false,
    val errorMessage: String? = null,
)

fun AnalyzedFoodItem.toPortionContext(): PortionContext = PortionContext(
    name = name,
    currentQuantity = quantity,
    currentUnit = unit,
    currentGrams = gramsEquivalent,
    calories = calories,
    proteinGrams = proteinGrams,
    carbohydrateGrams = carbohydrateGrams,
    fatGrams = fatGrams,
)
