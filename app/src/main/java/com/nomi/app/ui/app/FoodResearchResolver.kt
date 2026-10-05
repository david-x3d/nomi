package com.nomi.app.ui.app

import com.nomi.app.ai.model.FoodAnalysis
import com.nomi.app.ai.model.MenuDish
import com.nomi.app.ai.model.ParsedFoodIntent
import com.nomi.app.ai.validation.UserQuantityResolver
import com.nomi.app.data.repository.NomiRepository
import com.nomi.app.domain.usecase.FoodAnalysisCacheKey
import com.nomi.app.domain.usecase.NutritionRoute
import com.nomi.app.domain.usecase.RecentFoodAnalysisCache
import kotlinx.coroutines.CancellationException

internal class FoodInterpretationException(cause: Exception) : Exception(cause)

/** The cache order and research persistence live in one place, separate from UI transitions. */
internal class FoodResearchResolver(
    private val repository: NomiRepository,
    private val catalog: LocalFoodCatalog,
    private val recent: RecentFoodAnalysisCache,
    private val debug: AiDebugRecorder,
    private val interpret: suspend (String) -> ParsedFoodIntent,
) {
    suspend fun resolve(
        text: String,
        menuDishes: List<MenuDish>?,
        key: FoodAnalysisCacheKey,
        onResearch: () -> Unit,
        research: suspend (ParsedFoodIntent) -> FoodAnalysis,
    ): FoodAnalysis {
        if (menuDishes == null) {
            recent.get(key)?.let { return cached("5-minute exact-input cache", it) }
        }
        val intent = try {
            val parsed = interpret(text)
            menuDishes?.let { UserQuantityResolver.applyMenuQuantities(it, parsed) } ?: parsed
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            throw FoodInterpretationException(error)
        }
        if (menuDishes == null) {
            repository.cachedFoodResearch(key)?.let {
                debug.recordRoute(
                    route = NutritionRoute.NEW_RESEARCH,
                    decision = NutritionRoute.Decision.LOCAL,
                    detail = "Validated 21-day food research cache hit",
                )
                return cached("21-day validated research cache", it)
            }
        }
        catalog.cachedAnalysis(intent)?.let { return cached("per-100-g local food cache", it) }
        onResearch()
        val analysis = research(intent)
        recent.put(key, analysis)
        if (menuDishes == null) repository.cacheFoodResearch(key, analysis)
        analysis.items.forEach { item ->
            try {
                catalog.cache(item)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Catalog reuse is optional; the validated analysis can still be logged.
            }
        }
        debug.recordRoute(
            route = NutritionRoute.NEW_RESEARCH,
            decision = NutritionRoute.Decision.DIRECT,
            detail = "New food entry researched before saving",
        )
        return analysis
    }

    private fun cached(source: String, analysis: FoodAnalysis): FoodAnalysis {
        debug.recordCachedNutritionTrace(source, analysis)
        return analysis
    }
}
