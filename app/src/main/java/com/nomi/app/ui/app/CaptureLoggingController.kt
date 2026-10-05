package com.nomi.app.ui.app

import com.nomi.app.ai.model.AiProcessingStage
import com.nomi.app.ai.model.AnalyzedFoodItem
import com.nomi.app.ai.model.FoodAnalysis
import com.nomi.app.ai.model.ParsedFoodIntent
import com.nomi.app.ai.model.ParsedFoodItem
import com.nomi.app.ai.validation.ServingNutritionNormalizer
import com.nomi.app.data.preferences.AppPreferences
import com.nomi.app.data.preferences.ProviderPipeline
import com.nomi.app.data.remote.openfoodfacts.BarcodeProduct
import com.nomi.app.data.repository.NomiRepository
import com.nomi.app.domain.usecase.NutritionRoute
import com.nomi.app.ui.capture.BarcodeAmountSupport
import com.nomi.app.ui.capture.BarcodeAmountUiState
import com.nomi.app.ui.localization.NomiLanguage
import com.nomi.app.ui.logging.FoodLoggingUiState
import com.nomi.app.ui.logging.toPhotoMealDescription
import com.nomi.app.ui.logging.toPhotoParsedItem
import com.nomi.app.ui.today.MealCategory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Camera, label and barcode workflows share the logging request owner and draft. */
internal class CaptureLoggingController(
    private val draft: LoggingDraft,
    private val requests: LoggingRequests,
    private val repository: NomiRepository,
    private val foodCatalog: LocalFoodCatalog,
    private val providers: AiProviderAccess,
    private val debug: AiDebugRecorder,
    private val preferences: StateFlow<AppPreferences>,
    private val defaultMealCategory: () -> MealCategory,
    private val currentLanguage: () -> NomiLanguage,
    private val inUserLanguage: (String) -> String,
    private val findBarcodeProduct: suspend (String) -> BarcodeProduct?,
    private val startCapture: () -> LoggingRequest,
    private val beginRequest: () -> LoggingRequest,
    private val interpret: suspend (String) -> ParsedFoodIntent,
    private val researchNutrition: suspend (ParsedFoodIntent, LoggingRequest) -> FoodAnalysis,
    private val dismissLoggingDraft: () -> Unit,
) {
    /**
     * Reads a photographed nutrition table.
     *
     * This is the only logging path that never researches anything: the values are printed on
     * the package in the user's hand, so there is nothing to look up, cross-check, or estimate.
     * That also makes it the answer for the products the web knows badly - regional, store,
     * and foreign brands - where research is slowest and least certain.
     *
     * A label gives nutrition per 100 g/ml or per serving, never the amount eaten, so it ends
     * where a scanned barcode ends: in the amount sheet, which then scales it exactly as it
     * scales any other source serving.
     */
    fun analyzeNutritionLabel(bytes: ByteArray, mediaType: String) {
        val request = startCapture()
        val category = defaultMealCategory()
        draft.mutableBarcodeAmountState.value = null
        requests.launch(request) {
            draft.mutableLoggingState.value = FoodLoggingUiState.Processing(AiProcessingStage.FINDING_NUTRITION)
            runCatching {
                val reading = providers.withProvider(ProviderPipeline.VISION) {
                    it.readNutritionLabel(bytes, mediaType)
                }
                val sourceItem = reading.toAnalyzedItem(currentLanguage())
                foodCatalog.cache(sourceItem)
                BarcodeAmountUiState(
                    sourceItem = sourceItem,
                    amount = BarcodeAmountSupport.initialSuggestion(
                        reading.servingLabel,
                        sourceItem.unit,
                    ).amount,
                    unit = BarcodeAmountSupport.initialSuggestion(
                        reading.servingLabel,
                        sourceItem.unit,
                    ).unit,
                    compatibleUnits = BarcodeAmountSupport.compatibleUnits(sourceItem.unit),
                    mealCategory = category,
                    servingLabel = reading.servingLabel,
                )
            }.onSuccess { amountState ->
                if (!requests.isCurrent(request)) return@onSuccess
                draft.mutableLoggingState.value = FoodLoggingUiState.Input("", category)
                updateBarcodeAmountState(amountState)
            }.onFailure { error ->
                if (error is CancellationException) throw error
                if (!requests.isCurrent(request)) return@onFailure
                draft.mutableLoggingState.value = FoodLoggingUiState.Error(
                    error.safeAiMessage(),
                    canRetry = false,
                )
            }
        }.invokeOnCompletion { bytes.fill(0) }
    }

    /**
     * Recognizes a photo and stops there, handing the description back for review.
     *
     * Research is the expensive half in both money and seconds, so it does not start until the
     * user has agreed the photo was read correctly. Recognition mistakes are cheap to fix as
     * words and expensive to fix as nutrition.
     */
    fun analyzePhoto(bytes: ByteArray, mediaType: String) {
        val request = startCapture()
        requests.launch(request) {
            val category = defaultMealCategory()
            runCatching {
                draft.mutableLoggingState.value = FoodLoggingUiState.Processing(AiProcessingStage.UNDERSTANDING_MEAL)
                providers.withProvider(ProviderPipeline.VISION) { it.identifyFood(bytes, mediaType) }
            }.onSuccess { vision ->
                if (!requests.isCurrent(request)) return@onSuccess
                val recognized = vision.items.map { it.toPhotoParsedItem() }
                val description = recognized.toPhotoMealDescription()
                debug.recordRoute(
                    route = NutritionRoute.PHOTO_DESCRIPTION,
                    decision = NutritionRoute.Decision.DIRECT,
                    detail = "Photo described by the vision model; no nutrition looked up yet",
                )
                draft.lastLoggingText = description
                draft.mutableLoggingState.value = FoodLoggingUiState.PhotoReview(
                    description = description,
                    recognizedDescription = description,
                    recognizedItems = recognized,
                    mealCategory = category,
                    notes = (vision.notes + vision.items.mapNotNull { item ->
                        item.weightEstimationBasis?.takeIf(String::isNotBlank)?.let { "${item.name}: $it" }
                    }).distinct(),
                )
            }.onFailure { error ->
                if (error is CancellationException) throw error
                if (!requests.isCurrent(request)) return@onFailure
                draft.mutableLoggingState.value = FoodLoggingUiState.Error(error.safeAiMessage(), canRetry = false)
            }
        }
        .invokeOnCompletion { bytes.fill(0) }
    }

    fun updatePhotoDescription(description: String) {
        val current = draft.mutableLoggingState.value as? FoodLoggingUiState.PhotoReview ?: return
        draft.mutableLoggingState.value = current.copy(description = description.take(MAX_PHOTO_DESCRIPTION_CHARS))
    }

    fun updatePhotoPlace(place: String) {
        val current = draft.mutableLoggingState.value as? FoodLoggingUiState.PhotoReview ?: return
        draft.mutableLoggingState.value = current.copy(place = place.take(MAX_PHOTO_PLACE_CHARS))
    }

    /**
     * Researches the reviewed description.
     *
     * An untouched description still carries the vision model's portion and weight estimates, so
     * those are kept. An edited one no longer describes the same foods, so it re-enters through
     * the ordinary text path and is parsed like anything the user types.
     */
    fun confirmPhotoDescription() {
        val review = draft.mutableLoggingState.value as? FoodLoggingUiState.PhotoReview ?: return
        val description = review.description.trim()
        if (description.isBlank()) return

        val request = beginRequest()
        val place = review.place.trim().takeIf(String::isNotBlank)
        draft.lastLoggingText = description
        draft.mutableLoggingState.value = FoodLoggingUiState.Processing(
            AiProcessingStage.UNDERSTANDING_MEAL,
            originalText = description,
        )
        requests.launch(request) {
            runCatching {
                val items = if (review.isEdited || review.recognizedItems.isEmpty()) {
                    interpret(description).items
                } else {
                    review.recognizedItems
                }
                val intent = ParsedFoodIntent(
                    originalText = description,
                    // A named place is the brand of everything on the plate, which is what points
                    // research at that chain's published nutrition instead of a generic recipe.
                    items = items.map { item ->
                        if (place == null) item else item.copy(brand = item.brand ?: place)
                    },
                )
                draft.mutableLoggingState.value = FoodLoggingUiState.Processing(
                    AiProcessingStage.FINDING_NUTRITION,
                    originalText = description,
                    sourceUrls = listOfNotNull(preferences.value.foodResearchProvider.website()),
                )
                researchNutrition(intent, request).let { analysis ->
                    // Keep the visual portion caveat even when nutrition came from an exact table.
                    analysis.copy(items = analysis.items.mapIndexed { index, item ->
                        item.copy(assumptions = (item.assumptions +
                            intent.items.getOrNull(index)?.assumptions.orEmpty()).distinct())
                    })
                }.also {
                    debug.recordRoute(
                        route = NutritionRoute.NEW_RESEARCH,
                        decision = NutritionRoute.Decision.DIRECT,
                        detail = "Reviewed photo description researched on the web",
                    )
                }
            }.onSuccess { analysis ->
                if (!requests.isCurrent(request)) return@onSuccess
                // A photo lands on the page as the same preview a typed meal produces, so the
                // entry reads as if it had been written and "change wording" starts from something.
                val describedFoods = analysis.items.joinToString(", ", transform = AnalyzedFoodItem::name)
                draft.lastLoggingText = describedFoods
                draft.mutableLoggingState.value = FoodLoggingUiState.Preview(
                    analysis,
                    review.mealCategory,
                    originalText = describedFoods,
                )
            }.onFailure { error ->
                if (error is CancellationException) throw error
                if (!requests.isCurrent(request)) return@onFailure
                draft.mutableLoggingState.value = FoodLoggingUiState.Error(
                    error.safeAiMessage(),
                    canRetry = false,
                    originalText = description,
                )
            }
        }
    }

    fun lookupBarcode(barcode: String) {
        val request = startCapture()
        val category = defaultMealCategory()
        draft.mutableBarcodeAmountState.value = null
        requests.launch(request) {
            draft.mutableLoggingState.value = FoodLoggingUiState.Processing(AiProcessingStage.FINDING_NUTRITION)
            runCatching {
                val cached = repository.foodByBarcode(barcode)
                var servingLabel: String? = null
                val analyzedItem = if (cached != null && !cached.isEstimated) {
                    cached.toAnalyzedItem("Local barcode cache")
                } else {
                    val product = findBarcodeProduct(barcode)
                    servingLabel = product?.servingSize
                    product?.toAnalyzedItemOrNull()
                        ?: cached?.toAnalyzedItem("Local barcode estimate")
                        ?: run {
                            val label = product?.name?.takeIf { it.isNotBlank() }
                                ?: "Product with barcode $barcode"
                            val basisUnit = product?.nutritionBasisUnit ?: "g"
                            researchNutrition(
                                ParsedFoodIntent(
                                    originalText = "Barcode lookup",
                                    items = listOf(
                                        ParsedFoodItem(
                                            name = label,
                                            brand = product?.brand,
                                            quantity = 100.0,
                                            unit = basisUnit,
                                            gramsEquivalent = 100.0.takeIf { basisUnit == "g" },
                                        ),
                                    ),
                                ),
                                request,
                            ).items.single()
                        }
                }
                foodCatalog.cache(analyzedItem, barcode)
                val sourceItem = analyzedItem.asBarcodeSourceServing()
                val suggestion = BarcodeAmountSupport.initialSuggestion(servingLabel, sourceItem.unit)
                BarcodeAmountUiState(
                    barcode = barcode,
                    sourceItem = sourceItem,
                    amount = suggestion.amount,
                    unit = suggestion.unit,
                    compatibleUnits = BarcodeAmountSupport.compatibleUnits(sourceItem.unit),
                    mealCategory = category,
                    servingLabel = servingLabel,
                )
            }.onSuccess { amountState ->
                if (!requests.isCurrent(request)) return@onSuccess
                updateBarcodeAmountState(amountState)
            }.onFailure { error ->
                if (error is CancellationException) throw error
                if (!requests.isCurrent(request)) return@onFailure
                draft.mutableLoggingState.value = FoodLoggingUiState.Error(error.safeAiMessage(), canRetry = false)
            }
        }
    }

    fun updateBarcodeAmount(value: String) {
        val current = draft.mutableBarcodeAmountState.value ?: return
        updateBarcodeAmountState(
            current.copy(
                amount = BarcodeAmountSupport.sanitizeAmount(value),
                errorMessage = null,
            ),
        )
    }

    fun updateBarcodeUnit(unit: String) {
        val current = draft.mutableBarcodeAmountState.value ?: return
        if (unit !in current.compatibleUnits) return
        updateBarcodeAmountState(current.copy(unit = unit, errorMessage = null))
    }

    fun confirmBarcodeAmount() {
        val current = draft.mutableBarcodeAmountState.value ?: return
        val quantity = current.parsedAmount ?: run {
            draft.mutableBarcodeAmountState.value = current.copy(
                errorMessage = inUserLanguage("Enter an amount greater than zero"),
            )
            return
        }
        runCatching {
            ServingNutritionNormalizer.normalizeSourceServingTo(
                sourceServingItem = current.sourceItem,
                loggedQuantity = quantity,
                loggedUnit = current.unit,
                loggedGramsEquivalent = BarcodeAmountSupport.gramsEquivalent(quantity, current.unit),
            )
        }.onSuccess { item ->
            val description = BarcodeAmountSupport.description(current.amount, current.unit, item.name)
            draft.lastLoggingText = description
            draft.mutableBarcodeAmountState.value = null
            draft.mutableLoggingState.value = FoodLoggingUiState.Preview(
                analysis = FoodAnalysis(listOf(item), overallConfidence = item.confidence),
                mealCategory = current.mealCategory,
                originalText = description,
            )
        }.onFailure { error ->
            draft.mutableBarcodeAmountState.value = current.copy(errorMessage = error.safeAiMessage())
        }
    }

    fun cancelBarcodeAmount() {
        draft.mutableBarcodeAmountState.value = null
        dismissLoggingDraft()
    }

    private fun updateBarcodeAmountState(state: BarcodeAmountUiState) {
        draft.mutableBarcodeAmountState.value = state
        draft.lastLoggingText = BarcodeAmountSupport.description(state.amount, state.unit, state.sourceItem.name)
        draft.mutableLoggingState.value = FoodLoggingUiState.Input(draft.lastLoggingText, state.mealCategory)
    }

}

private const val MAX_PHOTO_DESCRIPTION_CHARS = 1_000
private const val MAX_PHOTO_PLACE_CHARS = 120
