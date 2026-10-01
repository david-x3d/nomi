package com.nomi.app.ui.app

import com.nomi.app.ai.model.FoodAnalysis
import com.nomi.app.data.local.entity.AiDebugEventEntity
import com.nomi.app.data.preferences.AppPreferences
import com.nomi.app.data.preferences.ProviderPipeline
import com.nomi.app.data.preferences.providerSelection
import com.nomi.app.data.remote.ai.ExaGeminiDebugTrace
import com.nomi.app.data.remote.ai.NutritionScalingDebugTrace
import com.nomi.app.di.AppContainer
import com.nomi.app.domain.usecase.NutritionRoute
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString

/**
 * Writes the AI debug log shown on the developer screen.
 *
 * Every method is a no-op unless the developer switch is on, and every write is wrapped: a
 * bookkeeping failure must never be the reason a lookup or a correction does not go through.
 */
internal class AiDebugRecorder(
    private val container: AppContainer,
    private val preferences: StateFlow<AppPreferences>,
    private val scope: CoroutineScope,
) {
    private val repository get() = container.repository
    private val enabled: Boolean get() = preferences.value.aiDebugEnabled

    suspend fun recordExaGeminiTrace(trace: ExaGeminiDebugTrace) {
        if (!enabled) return
        runCatching {
            repository.recordAiDebugEvent(
                AiDebugEventEntity(
                    pipeline = ProviderPipeline.FOOD_RESEARCH.name,
                    providerId = trace.provider,
                    model = trace.model,
                    durationMillis = trace.totalLatencyMillis,
                    cacheHit = false,
                    sourceSummary = trace.returnedSources.joinToString(" | ") {
                        "${it.sourceId}: ${it.title} (${it.url})"
                    }.take(4_000),
                    parsedResultJson = container.exaGeminiClient.json.encodeToString(trace),
                    validationStatus = trace.status,
                    // The typed per-item reasons are what make one bad item in a meal
                    // diagnosable; the whole-request reason is the fallback for a pass that
                    // never got as far as resolving items.
                    failureCategory = trace.itemFailures.firstOrNull()?.reason
                        ?: trace.failureReason?.let { "EXA_GEMINI_REJECTED" },
                    safeMessage = (
                        trace.itemFailures.map { failure ->
                            "item ${failure.itemIndex + 1} ${failure.itemName}: " +
                                "${failure.reason} - ${failure.detail}"
                        } + listOfNotNull(trace.failureReason)
                        ).joinToString(" | ").takeIf(String::isNotBlank),
                    createdAtEpochMillis = System.currentTimeMillis(),
                ),
            )
        }
    }

    suspend fun recordResearchFallback(
        status: String,
        error: Throwable? = null,
        analysis: FoodAnalysis? = null,
    ) {
        if (!enabled) return
        val selection = preferences.value.smartFallbackProvider
        val sourceUrls = analysis?.items.orEmpty().flatMap { item ->
            listOfNotNull(item.sourceUrl) + item.supportingSourceUrls
        }.distinct()
        runCatching {
            repository.recordAiDebugEvent(
                AiDebugEventEntity(
                    pipeline = ProviderPipeline.FOOD_RESEARCH.name,
                    providerId = selection.providerId,
                    model = selection.model,
                    durationMillis = 0,
                    cacheHit = false,
                    sourceSummary = sourceUrls.joinToString(" | ").take(4_000),
                    validationStatus = status,
                    failureCategory = error?.nutritionFailureReason()?.name
                        ?: error?.javaClass?.simpleName,
                    safeMessage = error?.researchFailureDetail()
                        ?: error?.safeProviderFailureMessage()
                        ?: if (analysis != null) {
                            "The configured fallback provider returned validated nutrition."
                        } else {
                            "The primary research provider failed validation; using the configured fallback."
                        },
                    createdAtEpochMillis = System.currentTimeMillis(),
                ),
            )
        }
    }

    suspend fun recordNutritionScalingTrace(trace: NutritionScalingDebugTrace) {
        if (!enabled) return
        runCatching {
            repository.recordAiDebugEvent(
                AiDebugEventEntity(
                    pipeline = ProviderPipeline.FOOD_RESEARCH.name,
                    providerId = trace.provider,
                    model = trace.model,
                    durationMillis = 0,
                    cacheHit = false,
                    sourceSummary = trace.source,
                    parsedResultJson = container.openAiClient.json.encodeToString(trace),
                    validationStatus = "PORTION_NORMALIZED",
                    safeMessage = trace.items.joinToString(" | ") { item ->
                        "${item.requestedAmount}; ${item.researchBasis}; " +
                            "factor=${item.scalingFactor}; final=${item.finalPortionValues}"
                    }.take(4_000),
                    createdAtEpochMillis = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun recordCachedNutritionTrace(cacheKind: String, analysis: FoodAnalysis) {
        if (!enabled) return
        scope.launch {
            runCatching {
                val summaries = analysis.items.map { item ->
                    val basis = item.servingValidation
                    "${item.name}: requested=${item.quantity} ${item.unit}, " +
                        "researchBasis=${item.nutritionBasis}, " +
                        "per100={kcal=${basis?.caloriesPer100}, protein=${basis?.proteinGramsPer100}, " +
                        "carbs=${basis?.carbohydrateGramsPer100}, fat=${basis?.fatGramsPer100}, " +
                        "fiber=${basis?.fiberGramsPer100}, sugar=${basis?.sugarGramsPer100}, " +
                        "saturatedFat=${basis?.saturatedFatGramsPer100}, " +
                        "sodiumMg=${basis?.sodiumMilligramsPer100}}, factor=${basis?.scaleFactor}, " +
                        "final={kcal=${item.calories}, protein=${item.proteinGrams}, " +
                        "carbs=${item.carbohydrateGrams}, fat=${item.fatGrams}, " +
                        "fiber=${item.fiberGrams}, sugar=${item.sugarGrams}, " +
                        "saturatedFat=${item.saturatedFatGrams}, sodiumMg=${item.sodiumMilligrams}}"
                }
                repository.recordAiDebugEvent(
                    AiDebugEventEntity(
                        pipeline = ProviderPipeline.FOOD_RESEARCH.name,
                        providerId = "nomi-local",
                        model = cacheKind,
                        durationMillis = 0,
                        cacheHit = true,
                        sourceSummary = cacheKind,
                        parsedResultJson = summaries.joinToString("\n").take(16_000),
                        validationStatus = "PORTION_NORMALIZED",
                        safeMessage = summaries.joinToString(" | ").take(4_000),
                        createdAtEpochMillis = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    /**
     * Keeps the diagnostic trail in the existing debug log, where it is already gated behind the
     * developer switch. Failures here are swallowed on purpose: a bookkeeping problem must never
     * be the reason a user's correction does not apply.
     */
    fun recordRoute(
        route: NutritionRoute,
        decision: NutritionRoute.Decision,
        detail: String,
        confidence: Double? = null,
    ) {
        if (!enabled) return
        scope.launch {
            runCatching {
                val selection = preferences.value.providerSelection(
                    if (route == NutritionRoute.CONTENT_RERESEARCH) {
                        ProviderPipeline.FOOD_RESEARCH
                    } else {
                        ProviderPipeline.PORTION_CHANGE
                    },
                )
                repository.recordAiDebugEvent(
                    AiDebugEventEntity(
                        pipeline = route.name,
                        providerId = if (decision == NutritionRoute.Decision.LOCAL) {
                            "nomi-local"
                        } else {
                            selection.providerId
                        },
                        model = if (decision == NutritionRoute.Decision.LOCAL) {
                            "PortionEditParser"
                        } else {
                            selection.model
                        },
                        durationMillis = 0,
                        cacheHit = decision == NutritionRoute.Decision.LOCAL,
                        sourceSummary = decision.name,
                        validationStatus = "ROUTED",
                        safeMessage = confidence
                            ?.let { "$detail (confidence ${(it * 100).roundToInt()}%)" }
                            ?: detail,
                        createdAtEpochMillis = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }
}
