package com.nomi.app.ui.app

import com.nomi.app.ai.model.FoodAnalysis
import com.nomi.app.data.preferences.DEFAULT_EXA_OPENROUTER_MODEL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** At most this many models answer one comparison, so a curious tap cannot run up a bill. */
internal const val MAX_COMPARED_MODELS = 4

/**
 * The cheap OpenRouter readers worth comparing, plus Gemini 3.8 Flash as the yardstick: it is
 * the model Exa + Gemini uses, and at $0.75 / $3.75 per million tokens it is under the ceiling.
 */
internal val SUGGESTED_COMPARISON_MODELS = listOf(
    DEFAULT_EXA_OPENROUTER_MODEL,
    "openai/gpt-6-luna",
    "qwen/qwen3.8-flash",
    "google/gemini-3.8-flash",
)

/** One model's finished run: its analysis or the error it ended with, and how long it took. */
internal data class ModelRun(
    val model: String,
    val result: Result<FoodAnalysis>,
    val durationMillis: Long,
)

/**
 * Researches with every model at once and reports each as it finishes.
 *
 * One model failing is an answer about that model, not a reason to stop the others, so each run
 * catches its own error. Only cancellation - a new comparison replacing this one - ends them all.
 */
internal suspend fun compareModels(
    models: List<String>,
    research: suspend (model: String) -> FoodAnalysis,
    onRun: suspend (ModelRun) -> Unit,
    clock: () -> Long = System::currentTimeMillis,
): List<ModelRun> = coroutineScope {
    models.distinct().take(MAX_COMPARED_MODELS).map { model ->
        async {
            val startedAt = clock()
            val result = try {
                Result.success(research(model))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Result.failure(error)
            }
            ModelRun(model, result, clock() - startedAt).also { onRun(it) }
        }
    }.awaitAll()
}

internal enum class ModelComparisonStatus { RUNNING, DONE, FAILED }

/** What one model's card shows. */
internal data class ModelComparisonCard(
    val model: String,
    val status: ModelComparisonStatus,
    val analysis: FoodAnalysis? = null,
    val errorMessage: String? = null,
    val durationMillis: Long? = null,
)

internal data class ModelComparisonUiState(
    val input: String = "",
    val models: List<String> = SUGGESTED_COMPARISON_MODELS,
    val selected: Set<String> = SUGGESTED_COMPARISON_MODELS.take(3).toSet(),
    val customModelInput: String = "",
    val isRunning: Boolean = false,
    /** Set when the meal could not be read or a key is missing, so no model ran at all. */
    val errorMessage: String? = null,
    val cards: List<ModelComparisonCard> = emptyList(),
) {
    val canRun: Boolean
        get() = input.isNotBlank() && selected.isNotEmpty() && !isRunning

    fun canSelectMore(): Boolean = selected.size < MAX_COMPARED_MODELS

    fun toggled(model: String): ModelComparisonUiState = when {
        model in selected -> copy(selected = selected - model)
        canSelectMore() -> copy(selected = selected + model)
        else -> this
    }

    /** Adds a typed model ID to the list and selects it while there is room. */
    fun withCustomModel(): ModelComparisonUiState {
        val model = customModelInput.trim()
        if (model.isEmpty()) return this
        val listed = if (model in models) models else models + model
        val picked = if (model in selected || !canSelectMore()) selected else selected + model
        return copy(models = listed, selected = picked, customModelInput = "")
    }

    /** The selected models in list order, which is also the order of the cards. */
    fun selectedInOrder(): List<String> = models.filter { it in selected }.take(MAX_COMPARED_MODELS)
}
