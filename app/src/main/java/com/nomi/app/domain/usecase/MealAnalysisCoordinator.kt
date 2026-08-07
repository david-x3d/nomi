package com.nomi.app.domain.usecase

import com.nomi.app.ai.model.AiProcessingStage
import com.nomi.app.ai.model.FoodAnalysis
import com.nomi.app.ai.provider.FoodParsingProvider
import com.nomi.app.ai.provider.NutritionResearchProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

sealed interface MealAnalysisEvent {
    data class Processing(val stage: AiProcessingStage) : MealAnalysisEvent
    data class Ready(val analysis: FoodAnalysis) : MealAnalysisEvent
    data class Failed(val message: String, val retryable: Boolean) : MealAnalysisEvent
}

class MealAnalysisCoordinator(
    private val foodParser: FoodParsingProvider,
    private val nutritionResearcher: NutritionResearchProvider,
) {
    fun analyze(text: String): Flow<MealAnalysisEvent> = flow {
        try {
            emit(MealAnalysisEvent.Processing(AiProcessingStage.UNDERSTANDING_MEAL))
            val intent = foodParser.parseFood(text)
            emit(MealAnalysisEvent.Processing(AiProcessingStage.FINDING_NUTRITION))
            val nutrition = nutritionResearcher.researchNutrition(intent)
            emit(MealAnalysisEvent.Processing(AiProcessingStage.CHECKING_PORTIONS))
            emit(MealAnalysisEvent.Processing(AiProcessingStage.PUTTING_IT_TOGETHER))
            emit(MealAnalysisEvent.Ready(nutrition))
        } catch (error: Throwable) {
            val message = when {
                error.message?.contains("401") == true ->
                    "Your API key was rejected. Check it in Settings."

                error.message?.contains("timeout", ignoreCase = true) == true ->
                    "The nutrition search took too long. Try again."

                else -> "Nomi couldn't finish that meal. Try again or enter it manually."
            }
            emit(MealAnalysisEvent.Failed(message, retryable = true))
        }
    }
}
