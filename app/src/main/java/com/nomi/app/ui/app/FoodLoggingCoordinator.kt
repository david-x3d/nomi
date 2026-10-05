package com.nomi.app.ui.app

import com.nomi.app.ai.model.AiProcessingStage
import com.nomi.app.ai.model.AnalyzedFoodItem
import com.nomi.app.ai.model.FoodAnalysis
import com.nomi.app.ai.model.MenuDish
import com.nomi.app.ai.model.ParsedFoodIntent
import com.nomi.app.ai.parsing.LocalFoodIntentParser
import com.nomi.app.data.preferences.AppPreferences
import com.nomi.app.data.preferences.ProviderPipeline
import com.nomi.app.data.remote.openfoodfacts.BarcodeProduct
import com.nomi.app.data.repository.NomiRepository
import com.nomi.app.domain.usecase.FoodAnalysisCacheKey
import com.nomi.app.domain.usecase.FoodEditRouter
import com.nomi.app.domain.usecase.RecentFoodAnalysisCache
import com.nomi.app.ui.localization.NomiLanguage
import com.nomi.app.ui.logging.FoodLoggingUiState
import com.nomi.app.ui.logging.ManualFoodDraft
import com.nomi.app.ui.today.AddFoodMethod
import com.nomi.app.ui.today.MealCategory
import com.nomi.app.ui.today.TodayFoodEntry
import com.nomi.app.ui.today.reeditableText
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Owns a food draft from input through capture, research, correction and persistence. */
internal class FoodLoggingCoordinator(
    private val repository: NomiRepository,
    private val foodCatalog: LocalFoodCatalog,
    private val providers: AiProviderAccess,
    private val debug: AiDebugRecorder,
    private val scope: CoroutineScope,
    private val preferences: StateFlow<AppPreferences>,
    private val destination: () -> LogDestination,
    private val defaultMealCategory: () -> MealCategory,
    private val currentLanguage: () -> NomiLanguage,
    private val inUserLanguage: (String) -> String,
    private val findBarcodeProduct: suspend (String) -> BarcodeProduct?,
    private val emitEvent: suspend (AppEvent) -> Unit,
) {
    val recentlySavedInputs = MutableStateFlow<Map<String, String>>(emptyMap())
    private val draft = LoggingDraft()
    val loggingState = draft.mutableLoggingState.asStateFlow()
    private val recentFoodAnalysisCache = RecentFoodAnalysisCache()
    private val requests = LoggingRequests(scope)
    private val saves = LoggingSaveController(scope)
    private val saver = LoggingSaver(
        cacheItem = { foodCatalog.cache(it) },
        cacheManual = { foodCatalog.cache(it) },
        writeRows = repository::saveLoggingRows,
    )

    val barcodeAmountState = draft.mutableBarcodeAmountState.asStateFlow()
    val lastLoggingText get() = draft.lastLoggingText
    private var pendingMenuDishes: List<MenuDish> = emptyList()
    private var pendingMenuLoggingText: String? = null
    /** The logged entry currently being rewritten as text on the page, if any. */
    val editedEntryId = draft.mutableEditedEntryId.asStateFlow()

    fun beginLogging(method: AddFoodMethod, initialText: String = "") {
        cancelAnalysis()
        draft.destination = destination()
        dismissPortionEdit()
        // A new entry is not a rewrite of the row that happened to be open. Leaving this set made
        // the next saved food - dictated, photographed or scanned - delete that row.
        draft.mutableEditedEntryId.value = null
        pendingMenuDishes = emptyList()
        pendingMenuLoggingText = null
        draft.mutableBarcodeAmountState.value = null
        val category = defaultMealCategory()
        draft.mutableLoggingState.value = when (method) {
            AddFoodMethod.TYPE, AddFoodMethod.VOICE -> FoodLoggingUiState.Input(initialText, category)
            else -> FoodLoggingUiState.Input("", category)
        }
        draft.lastLoggingText = initialText
    }

    fun updateLoggingText(value: String) {
        if (value != pendingMenuLoggingText) {
            pendingMenuDishes = emptyList()
            pendingMenuLoggingText = null
        }
        draft.lastLoggingText = value
        val current = draft.mutableLoggingState.value
        if (current is FoodLoggingUiState.Input) draft.mutableLoggingState.value = current.copy(text = value)
    }

    fun editLoggingText() {
        cancelAnalysis()
        dismissPortionEdit()
        val category = when (val current = draft.mutableLoggingState.value) {
            is FoodLoggingUiState.Input -> current.mealCategory
            is FoodLoggingUiState.Preview -> current.mealCategory
            is FoodLoggingUiState.Manual -> current.draft.mealCategory
            else -> defaultMealCategory()
        }
        draft.mutableLoggingState.value = FoodLoggingUiState.Input(draft.lastLoggingText, category)
    }

    fun dismissLoggingDraft() {
        cancelAnalysis()
        pendingMenuDishes = emptyList()
        pendingMenuLoggingText = null
        draft.mutableBarcodeAmountState.value = null
        draft.lastLoggingText = ""
        draft.destination = null
        dismissPortionEdit()
        draft.mutableEditedEntryId.value = null
        draft.mutableLoggingState.value = FoodLoggingUiState.Input("", defaultMealCategory())
    }

    /**
     * Reopens a logged entry as text on the page.
     *
     * The old entry is kept until the rewritten one is confirmed, so a failed or abandoned
     * re-research can never leave the day short of a meal. Rewriting the text always re-runs
     * research: keeping the previous calories under different words is exactly the mismatch
     * between text and numbers this app exists to prevent.
     */
    fun editEntryTextInline(entry: TodayFoodEntry) {
        if (entry.id <= 0) return
        cancelAnalysis()
        dismissPortionEdit()
        pendingMenuDishes = emptyList()
        pendingMenuLoggingText = null
        draft.mutableBarcodeAmountState.value = null
        draft.destination = destination()
        val text = entry.reeditableText()
        draft.lastLoggingText = text
        draft.mutableEditedEntryId.value = entry.id
        draft.mutableLoggingState.value = FoodLoggingUiState.Input(text, entry.mealCategory)
    }

    fun updateLoggingMealCategory(category: MealCategory) {
        draft.mutableLoggingState.value = when (val current = draft.mutableLoggingState.value) {
            is FoodLoggingUiState.Input -> current.copy(mealCategory = category)
            is FoodLoggingUiState.Preview -> current.copy(mealCategory = category)
            is FoodLoggingUiState.Manual -> current.copy(draft = current.draft.copy(mealCategory = category))
            else -> current
        }
    }

    fun showManualLogging(prefillName: String = draft.lastLoggingText) {
        cancelAnalysis()
        dismissPortionEdit()
        draft.mutableBarcodeAmountState.value = null
        val category = when (val current = draft.mutableLoggingState.value) {
            is FoodLoggingUiState.Input -> current.mealCategory
            is FoodLoggingUiState.Preview -> current.mealCategory
            else -> defaultMealCategory()
        }
        draft.mutableLoggingState.value = FoodLoggingUiState.Manual(
            ManualFoodDraft(name = prefillName, amount = "100", unit = "g", mealCategory = category),
        )
    }

    fun updateManualDraft(value: ManualFoodDraft) {
        draft.mutableLoggingState.value = FoodLoggingUiState.Manual(value)
    }

    fun updatePreviewItem(index: Int, item: AnalyzedFoodItem) {
        val current = draft.mutableLoggingState.value as? FoodLoggingUiState.Preview ?: return
        if (index !in current.analysis.items.indices) return
        val updated = current.analysis.items.toMutableList().apply { this[index] = item }
        draft.mutableLoggingState.value = current.copy(
            analysis = current.analysis.copy(items = updated),
        )
    }

    private val menu = MenuScanController(providers, scope, inUserLanguage) { dishes, text ->
        beginLogging(AddFoodMethod.TYPE, text)
        pendingMenuDishes = dishes
        pendingMenuLoggingText = text
        analyzeText()
    }
    val menuScanState = menu.menuScanState
    fun beginMenuScan() = menu.beginMenuScan()
    fun updateMenuSearch(query: String) = menu.updateMenuSearch(query)
    fun scanMenuPage(bytes: ByteArray, mediaType: String) = menu.scanMenuPage(bytes, mediaType)
    fun toggleMenuDish(dish: MenuDish) = menu.toggleMenuDish(dish)
    fun selectMenuDishes() = menu.selectMenuDishes()

    /** Removes one component from a detected meal before it is saved. */
    fun removePreviewItem(index: Int) {
        val current = draft.mutableLoggingState.value as? FoodLoggingUiState.Preview ?: return
        if (index !in current.analysis.items.indices) return
        val updated = current.analysis.items.toMutableList().apply { removeAt(index) }
        if (updated.isEmpty()) {
            // Keep the preview actionable; the user can still edit the original meal text or
            // dismiss the draft instead of reaching an empty meal that cannot be saved.
            return
        }
        draft.mutableLoggingState.value = current.copy(
            analysis = current.analysis.copy(items = updated),
        )
    }

    /**
     * Turns a sentence into foods and amounts: on the phone when it is plain enough, through the
     * interpretation model otherwise, and with spellings repaired against the user's own log.
     */
    suspend fun interpret(text: String): ParsedFoodIntent = foodCatalog.withKnownSpellings(
        LocalFoodIntentParser.parseOrNull(text)
            ?: providers.withProvider(ProviderPipeline.FOOD_INTERPRETATION) { it.parseFood(text) },
    )

    /** Binds the routing rules to this app's configured cheap classifier. */
    fun editRouter() = FoodEditRouter { context, correction ->
        providers.withProvider(ProviderPipeline.PORTION_CHANGE) { it.classifyEdit(context, correction) }
    }

    private val portions = PreviewPortionController(
        scope, debug, editRouter(), ::interpret,
        research = { intent ->
            val request = requests.current
            if (request == null) providers.researchNutrition(intent) else researchNutrition(intent, request)
        },
        preview = { draft.mutableLoggingState.value as? FoodLoggingUiState.Preview },
        updatePreview = { draft.mutableLoggingState.value = it },
        inUserLanguage = inUserLanguage,
    )
    val portionEditState = portions.portionEditState
    fun beginPortionEdit(index: Int) = portions.beginPortionEdit(index)
    fun updatePortionCorrection(correction: String) = portions.updatePortionCorrection(correction)
    fun dismissPortionEdit() = portions.dismissPortionEdit()
    fun interpretPortionCorrection() = portions.interpretPortionCorrection()
    fun researchEditedItem() = portions.researchEditedItem()
    fun applyPortionCorrection() = portions.applyPortionCorrection()

    private val resolver = FoodResearchResolver(repository, foodCatalog, recentFoodAnalysisCache, debug, ::interpret)

    fun analyzeText() {
        val current = draft.mutableLoggingState.value as? FoodLoggingUiState.Input ?: return
        val text = current.text.trim()
        if (text.isBlank()) return
        val menuDishes = pendingMenuDishes.takeIf { pendingMenuLoggingText == text && it.isNotEmpty() }
        draft.lastLoggingText = text
        val key = foodAnalysisCacheKey(text)
        val request = beginRequest()
        draft.mutableLoggingState.value = FoodLoggingUiState.Processing(
            AiProcessingStage.UNDERSTANDING_MEAL, originalText = text,
        )
        requests.launch(request) {
            try {
                val analysis = resolver.resolve(
                    text, menuDishes, key,
                    onResearch = {
                        if (requests.isCurrent(request)) {
                            draft.mutableLoggingState.value = FoodLoggingUiState.Processing(
                                AiProcessingStage.FINDING_NUTRITION, originalText = text,
                            )
                        }
                    },
                    research = { researchNutrition(it, request) },
                )
                if (requests.isCurrent(request)) {
                    saveTextAnalysisAutomatically(analysis, current.mealCategory, text, request.sourceUrls, request)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (requests.isCurrent(request)) {
                    val message = if (error is FoodInterpretationException) {
                        requireNotNull(error.cause).safeAiMessage()
                    } else researchFailureMessage(error, currentLanguage())
                    draft.mutableLoggingState.value = FoodLoggingUiState.Error(message, canRetry = true, originalText = text)
                }
            }
        }
    }

    /**
     * Typed and dictated meals become journal rows as soon as their researched nutrition is
     * ready. Photos and manual entries still use their dedicated correction steps.
     */
    private fun saveTextAnalysisAutomatically(
        analysis: FoodAnalysis,
        category: MealCategory,
        originalText: String,
        consultedUrls: List<String> = emptyList(),
        request: LoggingRequest? = requests.current,
    ) {
        val context = saveContext(originalText, request, consultedUrls)
        val revision = requests.revision
        recentlySavedInputs.update { it + (context.groupId to originalText) }
        saveDraft(
            revision = revision,
            originalText = originalText,
            write = { saver.saveAnalysis(analysis, category, context) },
            onFailure = { error ->
                if (revision == requests.revision) {
                    draft.mutableLoggingState.value = FoodLoggingUiState.Error(
                        error.safeAiMessage(), canRetry = true, originalText = originalText,
                    )
                }
            },
            afterSave = {
                scope.launch {
                    delay(1_600L)
                    recentlySavedInputs.update { it - context.groupId }
                }
            },
            cleanupFailure = { recentlySavedInputs.update { it - context.groupId } },
        )
    }

    private fun saveContext(
        text: String?,
        request: LoggingRequest? = requests.current,
        sources: List<String> = request?.sourceUrls.orEmpty(),
    ) = LoggingSaveContext(
        destination = request?.destination ?: draft.destination ?: destination(),
        replacedEntryId = request?.replacedEntryId ?: draft.mutableEditedEntryId.value,
        sourceUrls = sources.toList(),
        originalText = text,
    )

    private fun saveDraft(
        revision: Long,
        originalText: String,
        write: suspend () -> Unit,
        onFailure: suspend (Exception) -> Unit,
        afterSave: suspend () -> Unit = {},
        cleanupFailure: () -> Unit = {},
    ) {
        saves.save(
            revision = revision,
            write = write,
            onSuccess = {
                if (revision == requests.revision) {
                    requests.cancel()
                    draft.mutableEditedEntryId.value = null
                    draft.lastLoggingText = ""
                    draft.destination = null
                    dismissPortionEdit()
                    draft.mutableBarcodeAmountState.value = null
                    draft.mutableLoggingState.value = FoodLoggingUiState.Input("", defaultMealCategory())
                }
                afterSave()
                emitEvent(AppEvent.FoodSaved)
            },
            onFailure = { error ->
                cleanupFailure()
                onFailure(error)
            },
            onStarted = {
                draft.mutableLoggingState.value = FoodLoggingUiState.Processing(
                    AiProcessingStage.FINDING_NUTRITION, originalText = originalText,
                )
            },
        )
    }

    fun retryAnalysis() {
        editLoggingText()
        analyzeText()
    }

    private val capture = CaptureLoggingController(
        draft, requests, repository, foodCatalog, providers, debug, preferences,
        defaultMealCategory, currentLanguage, inUserLanguage, findBarcodeProduct,
        startCapture = {
            dismissPortionEdit()
            draft.destination = destination()
            draft.mutableEditedEntryId.value = null
            draft.mutableBarcodeAmountState.value = null
            beginRequest()
        },
        beginRequest = ::beginRequest,
        interpret = ::interpret,
        researchNutrition = ::researchNutrition,
        dismissLoggingDraft = ::dismissLoggingDraft,
    )
    fun analyzeNutritionLabel(bytes: ByteArray, mediaType: String) = capture.analyzeNutritionLabel(bytes, mediaType)
    fun analyzePhoto(bytes: ByteArray, mediaType: String) = capture.analyzePhoto(bytes, mediaType)
    fun updatePhotoDescription(description: String) = capture.updatePhotoDescription(description)
    fun updatePhotoPlace(place: String) = capture.updatePhotoPlace(place)
    fun confirmPhotoDescription() = capture.confirmPhotoDescription()
    fun lookupBarcode(barcode: String) = capture.lookupBarcode(barcode)
    fun updateBarcodeAmount(value: String) = capture.updateBarcodeAmount(value)
    fun updateBarcodeUnit(unit: String) = capture.updateBarcodeUnit(unit)
    fun confirmBarcodeAmount() = capture.confirmBarcodeAmount()
    fun cancelBarcodeAmount() = capture.cancelBarcodeAmount()

    fun confirmLogging() {
        val current = draft.mutableLoggingState.value
        val originalText = when (current) {
            is FoodLoggingUiState.Preview -> current.originalText
            is FoodLoggingUiState.Manual -> current.draft.name
            else -> return
        }
        val context = saveContext(originalText)
        val contextRevision = requests.revision
        saveDraft(
            revision = contextRevision,
            originalText = originalText,
            write = {
                when (current) {
                    is FoodLoggingUiState.Preview -> saver.saveAnalysis(current.analysis, current.mealCategory, context)
                    is FoodLoggingUiState.Manual -> saver.saveManual(current.draft, context)
                }
            },
            onFailure = { error ->
                if (requests.revision == contextRevision) draft.mutableLoggingState.value = current
                emitEvent(AppEvent.Message(error.safeAiMessage()))
            },
        )
    }

    private fun beginRequest() = requests.begin(
        draft.destination ?: destination(), draft.mutableEditedEntryId.value,
    )

    private fun cancelAnalysis() = requests.cancel()

    private suspend fun researchNutrition(intent: ParsedFoodIntent, request: LoggingRequest): FoodAnalysis =
        providers.researchNutrition(intent) { urls ->
            if (requests.recordSources(request, urls)) showResearchSources(urls)
        }

    private fun foodAnalysisCacheKey(text: String): FoodAnalysisCacheKey {
        val prefs = preferences.value
        return FoodAnalysisCacheKey.create(
            input = text,
            localeCountry = Locale.getDefault().country,
            interpretationProviderIdentity = prefs.foodInterpretationProvider.cacheIdentity(),
            researchProviderIdentity = prefs.foodResearchProvider.cacheIdentity() + "\u001e" +
                prefs.smartFallbackProvider.cacheIdentity(),
        )
    }

    private fun showResearchSources(sourceUrls: List<String>) {
        val current = draft.mutableLoggingState.value as? FoodLoggingUiState.Processing ?: return
        if (current.stage == AiProcessingStage.FINDING_NUTRITION) {
            draft.mutableLoggingState.value = current.copy(sourceUrls = sourceUrls.distinct().take(3))
        }
    }

    fun clearCache() = recentFoodAnalysisCache.clear()
}
