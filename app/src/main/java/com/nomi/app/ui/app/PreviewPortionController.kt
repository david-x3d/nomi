package com.nomi.app.ui.app

import com.nomi.app.ai.model.AnalyzedFoodItem
import com.nomi.app.ai.model.FoodAnalysis
import com.nomi.app.ai.model.ParsedFoodIntent
import com.nomi.app.domain.usecase.FoodEditRouter
import com.nomi.app.domain.usecase.NutritionRoute
import com.nomi.app.domain.usecase.toPortionContext
import com.nomi.app.ui.logging.FoodLoggingUiState
import com.nomi.app.ui.logging.PortionEditUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Owns asynchronous corrections to a specific preview and rejects stale completions. */
internal class PreviewPortionController(
    private val scope: CoroutineScope,
    private val debug: AiDebugRecorder,
    private val router: FoodEditRouter,
    private val interpret: suspend (String) -> ParsedFoodIntent,
    private val research: suspend (ParsedFoodIntent) -> FoodAnalysis,
    private val preview: () -> FoodLoggingUiState.Preview?,
    private val updatePreview: (FoodLoggingUiState.Preview) -> Unit,
    private val inUserLanguage: (String) -> String,
) {
    private val mutablePortionEditState = MutableStateFlow<PortionEditUiState?>(null)
    val portionEditState = mutablePortionEditState.asStateFlow()
    private var portionEditIndex: Int? = null
    private var editRevision = 0L
    private var editJob: Job? = null
    fun beginPortionEdit(index: Int) {
        val preview = preview() ?: return
        val item = preview.analysis.items.getOrNull(index) ?: return
        editRevision += 1
        editJob?.cancel()
        portionEditIndex = index
        mutablePortionEditState.value = PortionEditUiState(current = item.toPortionContext())
    }

    fun updatePortionCorrection(correction: String) {
        editRevision += 1
        editJob?.cancel()
        mutablePortionEditState.value = mutablePortionEditState.value?.copy(
            correction = correction.take(500),
            isProcessing = false,
            proposed = null,
            scaledItem = null,
            needsResearch = false,
            researchReason = null,
            errorMessage = null,
        )
    }

    fun dismissPortionEdit() {
        editRevision += 1
        editJob?.cancel()
        portionEditIndex = null
        mutablePortionEditState.value = null
    }

    /**
     * Decides what a correction actually asks for, and answers it as cheaply as it can.
     *
     * Three tiers, in increasing cost. Most corrections are arithmetic phrased in English
     * ("half", "2x", "200 g"), and those never leave the device. Wording the local parser will
     * not guess at goes to the cheap classifier. Only a correction that genuinely changes the
     * food reaches the research model, which is the expensive one this whole path exists to
     * avoid calling.
     */
    fun interpretPortionCorrection() {
        val edit = mutablePortionEditState.value ?: return
        val index = portionEditIndex ?: return
        if (edit.correction.isBlank() || edit.isProcessing) return
        val item = currentPreviewItem(index) ?: return
        val previewSnapshot = preview()
        val revision = editRevision

        // Claim the edit before launching. A local result resolves before the next frame.
        mutablePortionEditState.value = edit.copy(
            isProcessing = true,
            proposed = null,
            scaledItem = null,
            needsResearch = false,
            errorMessage = null,
        )
        editJob = scope.launch {
            runCatching { router.route(item, edit.correction) }
                .onSuccess { decision ->
                    if (editRevision != revision || preview() !== previewSnapshot) return@onSuccess
                    when (decision) {
                        is FoodEditRouter.Decision.Scale -> {
                            debug.recordRoute(
                                route = NutritionRoute.PORTION_SCALE,
                                decision = decision.decidedBy,
                                detail = decision.classification?.reason?.takeIf(String::isNotBlank)
                                    ?: decision.result.description,
                                confidence = decision.classification?.confidence,
                            )
                            mutablePortionEditState.value = edit.copy(
                                isProcessing = false,
                                proposed = decision.result.toPortionAdjustment(),
                                scaledItem = decision.result.item,
                                needsResearch = false,
                                errorMessage = null,
                            )
                        }

                        is FoodEditRouter.Decision.Research -> {
                            mutablePortionEditState.value = edit.copy(
                                isProcessing = false,
                                needsResearch = true,
                                researchReason = decision.reason,
                            )
                        }
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    if (editRevision != revision || preview() !== previewSnapshot) return@onFailure
                    mutablePortionEditState.value = edit.copy(
                        isProcessing = false,
                        errorMessage = error.safeAiMessage(),
                    )
                }
        }
    }

    /**
     * Researches an edit that changed the food itself, carrying the original entry's context.
     *
     * The restaurant, product name, and logged amount are still true unless the edit says
     * otherwise, and throwing them away would make the second search worse than the first —
     * "actually it was tuna" alone loses the fact that it came from a particular chain.
     */
    fun researchEditedItem() {
        val index = portionEditIndex ?: return
        val edit = mutablePortionEditState.value ?: return
        if (edit.isProcessing) return
        val item = currentPreviewItem(index) ?: return
        val preview = preview() ?: return
        val revision = editRevision
        val correction = edit.correction.trim()
        if (correction.isBlank()) return

        mutablePortionEditState.value = edit.copy(isProcessing = true, errorMessage = null)
        editJob = scope.launch {
            runCatching {
                val request = buildString {
                    append(item.name)
                    item.brand?.takeIf(String::isNotBlank)?.let { append(" from ").append(it) }
                    append(", ").append(item.quantity.cleanNumber()).append(' ').append(item.unit)
                    append(". Correction: ").append(correction)
                }
                val parsed = interpret(request)
                // Known context survives the edit unless the correction replaced it.
                val intent = parsed.copy(
                    originalText = request,
                    items = parsed.items.map { parsedItem ->
                        parsedItem.copy(
                            brand = parsedItem.brand ?: item.brand,
                            quantity = parsedItem.quantity ?: item.quantity,
                            unit = parsedItem.unit ?: item.unit,
                        )
                    },
                )
                research(intent)
            }.onSuccess { analysis ->
                if (editRevision != revision || preview() !== preview) return@onSuccess
                debug.recordRoute(
                    route = NutritionRoute.CONTENT_RERESEARCH,
                    decision = NutritionRoute.Decision.CLASSIFIER,
                    detail = edit.researchReason ?: "The edit changed the food itself",
                )
                val replacement = analysis.items.firstOrNull()
                if (replacement == null) {
                    mutablePortionEditState.value = edit.copy(
                        isProcessing = false,
                        errorMessage = inUserLanguage("Nomi couldn't find nutrition for that change. Try again."),
                    )
                    return@onSuccess
                }
                val updated = preview.analysis.items.toMutableList().apply {
                    this[index] = replacement
                    // A correction naming several foods replaces the one row it started from
                    // and appends the rest, rather than silently dropping them.
                    addAll(index + 1, analysis.items.drop(1))
                }
                updatePreview(preview.copy(analysis = preview.analysis.copy(items = updated)))
                dismissPortionEdit()
            }.onFailure { error ->
                if (error is CancellationException) throw error
                if (editRevision != revision || preview() !== preview) return@onFailure
                mutablePortionEditState.value = edit.copy(
                    isProcessing = false,
                    errorMessage = error.safeAiMessage(),
                )
            }
        }
    }

    private fun currentPreviewItem(index: Int): AnalyzedFoodItem? =
        (preview())?.analysis?.items?.getOrNull(index)

    /**
     * Saves the result that was already computed deterministically when the change was read.
     *
     * Nothing is recalculated here: the preview the user approved and the row that gets stored
     * are the same value.
     */
    fun applyPortionCorrection() {
        val index = portionEditIndex ?: return
        val edit = mutablePortionEditState.value ?: return
        val updated = edit.scaledItem ?: return
        val current = preview() ?: return
        val items = current.analysis.items.toMutableList()
        if (index !in items.indices) return
        items[index] = updated
        updatePreview(current.copy(analysis = current.analysis.copy(items = items)))
        dismissPortionEdit()
    }

}
