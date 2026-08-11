package com.nomi.app.ai.provider

import com.nomi.app.ai.model.FoodAnalysis
import com.nomi.app.ai.model.FoodEditClassification
import com.nomi.app.ai.model.MenuScanResult
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

/**
 * Answers a meal from model knowledge alone, with no search and no sourcing requirement.
 *
 * Separate from [NutritionResearchProvider] because it trades provenance for latency on purpose:
 * this is what puts a number on screen in a second or two, while research is still running. Its
 * results are always marked as estimates, and research replaces them when it arrives.
 */
fun interface NutritionEstimateProvider {
    suspend fun estimateNutrition(intent: ParsedFoodIntent): FoodAnalysis
}

fun interface VisionFoodProvider {
    suspend fun identifyFood(imageBytes: ByteArray, mediaType: String): VisionFoodResult
}

fun interface MenuVisionProvider {
    suspend fun scanMenu(imageBytes: ByteArray, mediaType: String): MenuScanResult
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

/**
 * Decides whether a correction is arithmetic or a different food.
 *
 * Deliberately a separate contract from [NutritionResearchProvider]: this is the cheap, fast
 * model whose entire job is to keep the expensive one from being called for something that
 * multiplication can answer.
 */
fun interface FoodEditClassificationProvider {
    suspend fun classifyEdit(
        current: PortionContext,
        userEdit: String,
    ): FoodEditClassification
}

data class AiProviderRegistry(
    val foodParser: FoodParsingProvider,
    val nutritionResearcher: NutritionResearchProvider,
    val visionFoodProvider: VisionFoodProvider,
    val menuVisionProvider: MenuVisionProvider,
    val nutritionLabelProvider: NutritionLabelProvider,
    val portionAdjustmentProvider: PortionAdjustmentProvider,
    val foodEditClassifier: FoodEditClassificationProvider,
)
