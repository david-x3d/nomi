package com.nomi.app.ui.app

import com.nomi.app.ai.model.FoodAnalysis
import kotlinx.coroutines.CancellationException

/** Provider implementations never start hidden requests after a failed research attempt. */
internal suspend fun runNutritionResearchPolicy(
    primary: suspend () -> FoodAnalysis,
    fallback: suspend () -> FoodAnalysis,
    estimate: suspend () -> FoodAnalysis,
    onFallback: suspend (Throwable) -> Unit = {},
    onFallbackSuccess: suspend (FoodAnalysis) -> Unit = {},
): FoodAnalysis {
    val researchError = try {
        return runWithSmartFallback(primary, fallback, onFallback, onFallbackSuccess)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        error
    }
    return try {
        estimate()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (estimateError: Exception) {
        researchError.addSuppressed(estimateError)
        throw researchError
    }
}
