package com.nomi.app.data.remote.ai

import android.util.Base64
import com.nomi.app.ai.model.AiProviderConfig
import com.nomi.app.ai.model.AiRuntimeCredential
import com.nomi.app.ai.model.FoodAnalysis
import com.nomi.app.ai.model.ParsedFoodIntent
import com.nomi.app.ai.model.PortionAdjustment
import com.nomi.app.ai.model.PortionContext
import com.nomi.app.ai.model.VisionFoodResult
import com.nomi.app.ai.prompt.AiPrompts
import com.nomi.app.ai.provider.FoodParsingProvider
import com.nomi.app.ai.provider.NutritionResearchProvider
import com.nomi.app.ai.provider.PortionAdjustmentProvider
import com.nomi.app.ai.provider.VisionFoodProvider
import com.nomi.app.ai.validation.AiResponseValidator
import com.nomi.app.ai.validation.ServingNutritionNormalizer
import com.nomi.app.ai.validation.UserQuantityResolver
import java.util.Locale
import kotlinx.serialization.decodeFromString

class OpenAiCompatibleProviders(
    private val client: OpenAiCompatibleClient,
    private val parsingConfig: AiProviderConfig,
    private val parsingCredential: () -> AiRuntimeCredential,
    private val nutritionConfig: AiProviderConfig,
    private val nutritionCredential: () -> AiRuntimeCredential,
    private val portionConfig: AiProviderConfig,
    private val portionCredential: () -> AiRuntimeCredential,
    private val visionConfig: AiProviderConfig,
    private val visionCredential: () -> AiRuntimeCredential,
    private val localeCountryProvider: () -> String? = { Locale.getDefault().country },
) : FoodParsingProvider,
    NutritionResearchProvider,
    PortionAdjustmentProvider,
    VisionFoodProvider {

    override suspend fun parseFood(text: String): ParsedFoodIntent {
        require(text.isNotBlank()) { "Enter what you ate first" }
        val raw = client.completeJson(
            config = parsingConfig,
            credential = parsingCredential(),
            systemPrompt = "You are Nomi's structured multilingual food parser.",
            userPrompt = AiPrompts.parseFood(text),
        )
        val providerIntent: ParsedFoodIntent = client.json.decodeFromString(raw)
        AiResponseValidator.validate(providerIntent)
        return AiResponseValidator.validate(
            UserQuantityResolver.reconcileParsedIntent(text, providerIntent, localeCountryProvider()),
        )
    }

    override suspend fun researchNutrition(intent: ParsedFoodIntent): FoodAnalysis {
        val localeCountry = localeCountryProvider()
        val reconciledIntent = AiResponseValidator.validate(
            UserQuantityResolver.reconcileIntent(intent, localeCountry),
        )
        val raw = client.completeJson(
            config = nutritionConfig,
            credential = nutritionCredential(),
            systemPrompt = "You report cited source-serving nutrition as validated JSON only; Nomi performs serving arithmetic.",
            userPrompt = AiPrompts.researchNutrition(reconciledIntent, client.json, localeCountry),
        )
        val analysis: FoodAnalysis = client.json.decodeFromString(raw)
        val reconciledAnalysis = UserQuantityResolver.reconcileAnalysis(reconciledIntent, analysis)
        return ServingNutritionNormalizer.normalize(reconciledIntent, reconciledAnalysis)
    }

    override suspend fun interpretAdjustment(
        current: PortionContext,
        userCorrection: String,
    ): PortionAdjustment {
        require(userCorrection.isNotBlank()) { "Describe what should change" }
        val raw = client.completeJson(
            config = portionConfig,
            credential = portionCredential(),
            systemPrompt = "You interpret portion corrections and never do nutrition arithmetic.",
            userPrompt = AiPrompts.adjustPortion(current, userCorrection, client.json),
        )
        val adjustment: PortionAdjustment = client.json.decodeFromString(raw)
        return AiResponseValidator.validate(current, adjustment)
    }

    override suspend fun identifyFood(imageBytes: ByteArray, mediaType: String): VisionFoodResult {
        require(imageBytes.isNotEmpty()) { "The selected image is empty" }
        val raw = client.completeVisionJson(
            config = visionConfig,
            credential = visionCredential(),
            prompt = AiPrompts.identifyFoodFromPhoto(),
            base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP),
            mediaType = mediaType,
        )
        val visionResult: VisionFoodResult = client.json.decodeFromString(raw)
        return AiResponseValidator.validate(visionResult)
    }
}
