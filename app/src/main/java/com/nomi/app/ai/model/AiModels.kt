package com.nomi.app.ai.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class AiProviderKind {
    PERPLEXITY,
    OPEN_ROUTER,
    OPEN_AI,
    CUSTOM_OPEN_AI_COMPATIBLE,
}

@Serializable
data class AiProviderConfig(
    val kind: AiProviderKind,
    val endpoint: String,
    val model: String,
    val temperature: Double = 0.1,
    val timeoutMillis: Long = 45_000,
    val extraHeaders: Map<String, String> = emptyMap(),
)

/** Holds a credential without putting it in generated data-class toString/copy output. */
class AiRuntimeCredential private constructor(private val value: String) {
    fun revealForRequest(): String = value

    override fun toString(): String = "AiRuntimeCredential(••••••••)"

    companion object {
        fun from(value: String): AiRuntimeCredential {
            require(value.isNotBlank()) { "An API key is required" }
            return AiRuntimeCredential(value)
        }
    }
}

@Serializable
data class ParsedFoodIntent(
    val originalText: String,
    val language: String? = null,
    val mealReference: String? = null,
    val items: List<ParsedFoodItem>,
)

@Serializable
data class ParsedFoodItem(
    val name: String,
    val brand: String? = null,
    val quantity: Double? = null,
    val unit: String? = null,
    val gramsEquivalent: Double? = null,
    val preparation: String? = null,
    val assumptions: List<String> = emptyList(),
    /** Deterministic user/local quantity decision. Provider JSON is never trusted for this. */
    val quantityResolution: QuantityResolutionMetadata? = null,
)

@Serializable
data class FoodAnalysis(
    val items: List<AnalyzedFoodItem>,
    val overallConfidence: Double? = null,
)

@Serializable
data class AnalyzedFoodItem(
    val name: String,
    val brand: String? = null,
    val quantity: Double,
    val unit: String,
    val gramsEquivalent: Double? = null,
    val calories: Double,
    val proteinGrams: Double,
    val carbohydrateGrams: Double,
    val fatGrams: Double,
    val fiberGrams: Double? = null,
    val sourceName: String? = null,
    val sourceUrl: String? = null,
    /** Additional provider-cited pages used to cross-check this item's nutrition. */
    val supportingSourceUrls: List<String> = emptyList(),
    /** The amount whose nutrition is reported by the cited source, before app-side scaling. */
    val sourceServingQuantity: Double? = null,
    val sourceServingUnit: String? = null,
    val sourceServingGramsEquivalent: Double? = null,
    val sourceCountry: String? = null,
    val isEstimate: Boolean,
    /** Optional retail pack declared by the source; never used as the logged amount. */
    val sourcePackageQuantity: Double? = null,
    val sourcePackageUnit: String? = null,
    val confidence: Double? = null,
    val assumptions: List<String> = emptyList(),
    /** Set only by Nomi's deterministic normalizer. AI output cannot bypass save validation. */
    val servingValidation: ServingSizeValidation? = null,
    val requiresServingValidation: Boolean = false,
    /** Copied from the reconciled parsed intent, never accepted from provider output. */
    val quantityResolution: QuantityResolutionMetadata? = null,
)

@Serializable
data class ServingSizeValidation(
    val dimension: String,
    val sourceQuantity: Double,
    val sourceUnit: String,
    val sourceBaseAmount: Double,
    val loggedQuantity: Double,
    val loggedUnit: String,
    val loggedBaseAmount: Double,
    val scaleFactor: Double,
    val caloriesPer100: Double,
    val proteinGramsPer100: Double,
    val carbohydrateGramsPer100: Double,
    val fatGramsPer100: Double,
    val fiberGramsPer100: Double? = null,
)

@Serializable
enum class QuantityOrigin {
    USER_EXPLICIT,
    GERMAN_LOCAL_DEFAULT,
    SOURCE_OR_INFERRED,
}

@Serializable
enum class QuantitySemantic {
    DIRECT_AMOUNT,
    PACKAGE_PERCENT,
    PACKAGE_FRACTION,
    LOCAL_CAN_DEFAULT,
}

/**
 * Structured quantity metadata for precise arithmetic and humane presentation.
 *
 * [canonicalQuantity] is the amount nutrition must be calculated for. Package semantics are
 * retained separately so the UI can say `⅔ bag · ≈133 g`, never `0.7 bag`.
 */
@Serializable
data class QuantityResolutionMetadata(
    val origin: QuantityOrigin,
    val semantic: QuantitySemantic,
    val canonicalQuantity: Double,
    val canonicalUnit: String,
    /** Exact amount/unit as typed, retained when canonicalization changes the display unit. */
    val enteredQuantity: Double? = null,
    val enteredUnit: String? = null,
    val packageQuantity: Double? = null,
    val packageUnit: String? = null,
    val fractionNumerator: Int? = null,
    val fractionDenominator: Int? = null,
    val percentage: Double? = null,
    val isApproximate: Boolean = false,
    val sourcePackageQuantity: Double? = null,
    val sourcePackageUnit: String? = null,
    val sourcePackageConflict: Boolean = false,
)

@Serializable
data class VisionFoodResult(
    val items: List<VisionFoodItem>,
    val notes: List<String> = emptyList(),
)

@Serializable
data class VisionFoodItem(
    val name: String,
    val visibleIngredients: List<String> = emptyList(),
    val estimatedQuantity: Double? = null,
    val unit: String? = null,
    val estimatedGrams: Double? = null,
    val confidence: Double? = null,
)

@Serializable
data class PortionContext(
    val name: String,
    val currentQuantity: Double,
    val currentUnit: String,
    val currentGrams: Double? = null,
    val calories: Double,
    val proteinGrams: Double,
    val carbohydrateGrams: Double,
    val fatGrams: Double,
)

@Serializable
data class PortionAdjustment(
    val newQuantity: Double,
    val newUnit: String,
    val multiplier: Double,
    val newGrams: Double? = null,
    val interpretation: String,
    val requiresConfirmation: Boolean = false,
)

@Serializable
data class AiDebugSummary(
    val provider: AiProviderKind,
    val model: String,
    val requestDurationMillis: Long,
    val cacheHit: Boolean,
    val source: String,
    val validationStatus: String,
    val redactedResult: String,
)

enum class AiProcessingStage {
    UNDERSTANDING_MEAL,
    FINDING_NUTRITION,
    CHECKING_PORTIONS,
    PUTTING_IT_TOGETHER,
}

sealed interface AiCallResult<out T> {
    data class Success<T>(val value: T, val durationMillis: Long) : AiCallResult<T>
    data class Failure(val userMessage: String, val retryable: Boolean) : AiCallResult<Nothing>
}
