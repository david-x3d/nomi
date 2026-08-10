package com.nomi.app.ui.logging

import com.nomi.app.ai.model.AiProcessingStage
import com.nomi.app.ai.model.AnalyzedFoodItem
import com.nomi.app.ai.model.FoodAnalysis
import com.nomi.app.ai.model.ParsedFoodItem
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

    /**
     * What the photo was understood to be, before anything is looked up.
     *
     * Recognition and research are separate jobs, and this is the seam between them. The
     * description is shown as ordinary editable words because that is what it is: the sentence
     * the user would have typed. Correcting "tuna" to "chicken" here costs one word; correcting
     * it after a full nutrition search costs the whole search.
     *
     * [place] is the restaurant or shop the meal came from, if the user names one. It is passed
     * on as the brand of every item, which is what sends research to that chain's own published
     * numbers rather than to a generic recipe.
     */
    data class PhotoReview(
        val description: String,
        /** The vision model's own wording, kept to detect whether the user changed anything. */
        val recognizedDescription: String,
        val place: String = "",
        val recognizedItems: List<ParsedFoodItem> = emptyList(),
        val mealCategory: MealCategory = MealCategory.SNACKS,
        val notes: List<String> = emptyList(),
    ) : FoodLoggingUiState {
        val canContinue: Boolean
            get() = description.isNotBlank()

        val isEdited: Boolean
            get() = description.trim() != recognizedDescription.trim()
    }

    /**
     * [isProvisional] marks a preview built from the fast estimate while sourced research is
     * still running. The entry is complete and can be saved as it stands; if research finishes
     * first the values are replaced in place, and if the user saves first the saved row is
     * upgraded instead.
     */
    data class Preview(
        val analysis: FoodAnalysis,
        val mealCategory: MealCategory,
        val originalText: String = "",
        val isProvisional: Boolean = false,
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
    /**
     * The fully scaled result, computed in app code the moment the change was understood.
     *
     * Holding the finished item rather than recomputing it on apply means what the preview
     * shows and what gets saved are the same object, not two calculations that could disagree.
     */
    val scaledItem: AnalyzedFoodItem? = null,
    /** Set when the edit changed the food, so arithmetic cannot answer it. */
    val needsResearch: Boolean = false,
    val researchReason: String? = null,
    val isProcessing: Boolean = false,
    val errorMessage: String? = null,
)

