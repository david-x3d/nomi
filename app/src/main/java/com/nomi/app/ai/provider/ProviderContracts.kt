package com.nomi.app.ai.provider

import com.nomi.app.ai.model.FoodAnalysis
import com.nomi.app.ai.model.NutritionLabelReading
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

/** Reads a printed nutrition table. Separate from [VisionFoodProvider] because it estimates nothing. */
fun interface NutritionLabelProvider {
    suspend fun readNutritionLabel(imageBytes: ByteArray, mediaType: String): NutritionLabelReading
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
    val nutritionLabelProvider: NutritionLabelProvider,
    val portionAdjustmentProvider: PortionAdjustmentProvider,
)
