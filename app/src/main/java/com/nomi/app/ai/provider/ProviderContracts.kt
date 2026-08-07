package com.nomi.app.ai.provider

import com.nomi.app.ai.model.FoodAnalysis
import com.nomi.app.ai.model.ParsedFoodIntent
import com.nomi.app.ai.model.PortionAdjustment
import com.nomi.app.ai.model.PortionContext
import com.nomi.app.ai.model.VisionFoodResult

fun interface FoodParsingProvider {
    suspend fun parseFood(text: String): ParsedFoodIntent
}

fun interface NutritionResearchProvider {
    suspend fun researchNutrition(intent: ParsedFoodIntent): FoodAnalysis
}

fun interface VisionFoodProvider {
    suspend fun identifyFood(imageBytes: ByteArray, mediaType: String): VisionFoodResult
}

fun interface PortionAdjustmentProvider {
    suspend fun interpretAdjustment(
        current: PortionContext,
        userCorrection: String,
    ): PortionAdjustment
}

data class AiProviderRegistry(
    val foodParser: FoodParsingProvider,
    val nutritionResearcher: NutritionResearchProvider,
    val visionFoodProvider: VisionFoodProvider,
    val portionAdjustmentProvider: PortionAdjustmentProvider,
)
