package com.nomi.app.ui.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nomi.app.BuildConfig
import com.nomi.app.ai.model.AiProcessingStage
import com.nomi.app.ai.model.AiProviderConfig
import com.nomi.app.ai.model.AiProviderKind
import com.nomi.app.ai.model.AiRuntimeCredential
import com.nomi.app.ai.model.AnalyzedFoodItem
import com.nomi.app.ai.model.FoodAnalysis
import com.nomi.app.ai.model.MenuDish
import com.nomi.app.ai.model.NutritionLabelReading
import com.nomi.app.ai.model.ParsedFoodIntent
import com.nomi.app.ai.model.ParsedFoodItem
import com.nomi.app.ai.parsing.FoodNameCorrection
import com.nomi.app.ai.model.PortionAdjustment
import com.nomi.app.ai.parsing.LocalFoodIntentParser
import com.nomi.app.ai.validation.AiValidationException
import com.nomi.app.ai.validation.FoodDisplayName
import com.nomi.app.ai.validation.ServingNutritionNormalizer
import com.nomi.app.ai.validation.SourceIntegrityVerifier
import com.nomi.app.ai.validation.UserQuantityResolver
import com.nomi.app.data.local.entity.AiDebugEventEntity
import com.nomi.app.data.local.entity.FavoriteFoodEntity
import com.nomi.app.data.local.entity.FoodEntity
import com.nomi.app.data.local.entity.FoodLogEntity
import com.nomi.app.data.local.entity.NutritionPlanEntity
import com.nomi.app.data.local.entity.NutritionSourceSnapshot
import com.nomi.app.data.local.entity.NutritionValues
import com.nomi.app.data.local.entity.UserProfileEntity
import com.nomi.app.data.local.entity.WeightEntryEntity
import com.nomi.app.data.local.model.FavoriteFoodWithCatalog
import com.nomi.app.data.local.model.SavedMealWithItems
import com.nomi.app.data.preferences.AppPreferences
import com.nomi.app.data.preferences.HeightUnitPreference
import com.nomi.app.data.preferences.MicronutrientPreferences
import com.nomi.app.data.preferences.ProviderPipeline
import com.nomi.app.data.preferences.enabledMicronutrients
import com.nomi.app.data.preferences.resolvedTarget
import com.nomi.app.data.preferences.settingFor
import com.nomi.app.data.preferences.ProviderSelection
import com.nomi.app.data.preferences.ThemePreference
import com.nomi.app.data.preferences.WeightUnitPreference
import com.nomi.app.data.preferences.CalorieEstimateBias
import com.nomi.app.data.preferences.GoalsCardStyle
import com.nomi.app.data.preferences.withSupportedModel
import com.nomi.app.ai.provider.NutritionResearchProvider
import com.nomi.app.data.remote.ai.DEFAULT_GEMINI_NUTRITION_MODEL
import com.nomi.app.data.remote.ai.EXA_API_ENDPOINT
import com.nomi.app.data.remote.ai.ExaGeminiDebugTrace
import com.nomi.app.data.remote.ai.ExaGeminiNutritionProvider
import com.nomi.app.data.remote.ai.GEMINI_API_ENDPOINT
import com.nomi.app.data.remote.ai.OpenAiCompatibleProviders
import com.nomi.app.data.remote.ai.ProviderTemporarilyUnavailableException
import com.nomi.app.data.remote.openfoodfacts.BarcodeProduct
import com.nomi.app.data.repository.AddSavedMealToLogRequest
import com.nomi.app.data.repository.HEALTH_CONNECT_WEIGHT_SOURCE
import com.nomi.app.data.repository.SaveLoggedMealRequest
import com.nomi.app.data.repository.mapping.toCompleteOnboardingRequest
import com.nomi.app.data.repository.mapping.toPersistedDraft
import com.nomi.app.data.repository.mapping.toEntity
import com.nomi.app.data.security.SecretUnavailableException
import com.nomi.app.di.AppContainer
import com.nomi.app.domain.Micronutrient
import com.nomi.app.domain.model.NutritionPlan
import com.nomi.app.domain.model.OnboardingDraft
import com.nomi.app.domain.usecase.FoodAnalysisCacheKey
import com.nomi.app.domain.usecase.FoodEditRouter
import com.nomi.app.domain.usecase.NutritionRoute
import com.nomi.app.domain.usecase.PortionEditApplier
import com.nomi.app.domain.usecase.PortionEditParser
import com.nomi.app.domain.usecase.RecentFoodAnalysisCache
import com.nomi.app.domain.usecase.isTrustedForNutritionReuse
import com.nomi.app.ui.profile.ProfileEdit
import com.nomi.app.integration.health.HealthConnectPermissionStatus
import com.nomi.app.integration.health.HealthFeatures
import com.nomi.app.integration.health.NomiHealthFeatures
import com.nomi.app.integration.health.importableHealthWeights
import com.nomi.app.integration.health.resolveHealthConnectPermissionStatus
import com.nomi.app.ui.history.HistoryDay
import com.nomi.app.ui.history.HistoryUiState
import com.nomi.app.ui.capture.BarcodeAmountSupport
import com.nomi.app.ui.capture.BarcodeAmountUiState
import com.nomi.app.ui.capture.MenuScanUiState
import com.nomi.app.ui.capture.menuDishKey
import com.nomi.app.ui.capture.mergeMenuDishes
import com.nomi.app.ui.library.LibraryItem
import com.nomi.app.ui.library.LibraryItemKind
import com.nomi.app.ui.library.LibraryUiState
import com.nomi.app.ui.logging.FoodLoggingUiState
import com.nomi.app.ui.logging.ManualFoodDraft
import com.nomi.app.ui.logging.PortionEditUiState
import com.nomi.app.domain.usecase.toPortionContext
import com.nomi.app.ui.progress.NutritionPoint
import com.nomi.app.ui.progress.ProgressRange
import com.nomi.app.ui.progress.ProgressUiState
import com.nomi.app.ui.progress.WeightPoint
import com.nomi.app.ui.settings.AiProviderEditorState
import com.nomi.app.ui.settings.AiProviderSetting
import com.nomi.app.ui.settings.HealthConnectUiState
import com.nomi.app.ui.settings.NutritionTargetSetting
import com.nomi.app.ui.settings.SettingsUiState
import com.nomi.app.ui.settings.ThemeMode
import com.nomi.app.ui.settings.UnitSystem
import com.nomi.app.ui.today.AddFoodMethod
import com.nomi.app.ui.today.LoggedAmountEditError
import com.nomi.app.ui.today.LoggedAmountEditUiState
import com.nomi.app.ui.today.MacroProgress
import com.nomi.app.ui.today.MealCategory
import com.nomi.app.ui.today.MicronutrientProgress
import com.nomi.app.ui.today.TodayFoodEntry
import com.nomi.app.ui.today.reeditableText
import com.nomi.app.ui.today.TodayUiState
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString

import java.net.URI
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlin.math.roundToInt

sealed interface AppStartState {
    data object Loading : AppStartState
    data object Onboarding : AppStartState
    data object Main : AppStartState
}

sealed interface AppEvent {
    data class Message(val text: String) : AppEvent
    data object FoodSaved : AppEvent
    data object OnboardingSaved : AppEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val repository = container.repository
    private val zoneId: ZoneId = ZoneId.systemDefault()
    private val today: LocalDate get() = LocalDate.now(zoneId)

    val preferences: StateFlow<AppPreferences> = repository.preferences
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            AppPreferences(),
        )

    val startState: StateFlow<AppStartState> = repository.profile
        .map { profile ->
            if (profile?.onboardingCompleted == true) AppStartState.Main
            else AppStartState.Onboarding
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppStartState.Loading)

    val profile: StateFlow<UserProfileEntity?> = repository.profile.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null,
    )

    val currentPlan: StateFlow<NutritionPlanEntity?> = repository.currentPlan.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null,
    )
    val latestWeight: StateFlow<WeightEntryEntity?> = repository.latestWeight.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null,
    )


    private val mutableEvents = MutableSharedFlow<AppEvent>(extraBufferCapacity = 8)
    val events = mutableEvents.asSharedFlow()

    private val mutableOnboardingSaving = MutableStateFlow(false)
    val onboardingSaving = mutableOnboardingSaving.asStateFlow()

    private val selectedDate = MutableStateFlow(today)
    private var dayLogSnapshot: List<FoodLogEntity> = emptyList()

    val todayState: StateFlow<TodayUiState> = combine(
        selectedDate.flatMapLatest { repository.dayLogs(it.toString()) }
            .onEach { dayLogSnapshot = it },
        repository.currentPlan,
        selectedDate,
        repository.preferences,
    ) { logs, plan, date, prefs ->
        mapToday(date, logs, plan, prefs.micronutrients, prefs.goalsCardStyle)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState(isLoading = true))
    val aiDebugEvents: StateFlow<List<AiDebugEventEntity>> = repository.aiDebugEvents().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )


    private val historyQuery = MutableStateFlow("")
    private val historyDate = MutableStateFlow(today)
    private val historyLogs: Flow<List<FoodLogEntity>> = historyDate.flatMapLatest { endDate ->
        repository.history(endDate.minusDays(29).toString(), endDate.toString())
    }
    val historyState: StateFlow<HistoryUiState> = combine(
        historyLogs,
        historyQuery,
        historyDate,
        repository.currentPlan,
    ) { logs, query, date, plan -> mapHistory(logs, query, date, plan) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    private val progressRange = MutableStateFlow(ProgressRange.THIRTY_DAYS)
    val progressState: StateFlow<ProgressUiState> = progressRange.flatMapLatest { range ->
        val totalDays = range.dayCount()
        val start = today.minusDays((totalDays - 1).toLong())
        combine(
            repository.weights(start.toString(), today.toString()),
            repository.nutritionHistory(start.toString(), today.toString()),
            repository.profile,
        ) { weights, nutrition, profile ->
            ProgressUiState(
                range = range,
                weights = weights.map { WeightPoint(LocalDate.parse(it.localDate), it.weightKg) },
                nutrition = nutrition.map {
                    NutritionPoint(
                        date = LocalDate.parse(it.localDate),
                        calories = it.caloriesKcal,
                        protein = it.proteinGrams,
                        carbohydrates = it.carbohydrateGrams,
                        fat = it.fatGrams,
                    )
                },
                startingWeightKg = profile?.startingWeightKg,
                targetWeightKg = profile?.targetWeightKg,
                loggingDays = nutrition.size,
                totalDays = totalDays,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUiState())

    private data class ProviderKeyPresence(
        val primary: Boolean,
        val search: Boolean,
    ) {
        val complete: Boolean get() = primary && search
    }

    private val keyPresence = MutableStateFlow<Map<ProviderPipeline, ProviderKeyPresence>>(emptyMap())
    private val healthConnectUiState = MutableStateFlow(HealthConnectUiState())
    private val healthSyncMutex = Mutex()
    val settingsState: StateFlow<SettingsUiState> = combine(
        repository.preferences,
        repository.currentPlan,
        keyPresence,
        healthConnectUiState,
    ) { prefs, plan, keys, health -> mapSettings(prefs, plan, keys, health) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    private var recentFoodsSnapshot: List<FoodEntity> = emptyList()
    private var favoriteSnapshot: List<FavoriteFoodWithCatalog> = emptyList()
    private var savedMealSnapshot: List<SavedMealWithItems> = emptyList()
    val libraryState: StateFlow<LibraryUiState> = combine(
        repository.recentFoods(),
        repository.favorites,
        repository.savedMeals,
    ) { recent, favorites, meals ->
        recentFoodsSnapshot = recent
        favoriteSnapshot = favorites
        savedMealSnapshot = meals
        LibraryUiState(
            recent = recent.map { food -> food.toLibraryItem(LibraryItemKind.RECENT) },
            favorites = favorites.map { favorite ->
                favorite.food.toLibraryItem(
                    kind = LibraryItemKind.FAVORITE,
                    amountText = "${favorite.favorite.typicalAmount.cleanNumber()} ${favorite.favorite.typicalUnit}",
                )
            },
            savedMeals = meals.map { saved ->
                LibraryItem(
                    id = saved.meal.id,
                    kind = LibraryItemKind.SAVED_MEAL,
                    title = saved.meal.name,
                    subtitle = "${saved.items.size} item${if (saved.items.size == 1) "" else "s"}",
                    calories = saved.items.sumOf { it.nutritionSnapshot.caloriesKcal },
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    private val mutableLoggingState = MutableStateFlow<FoodLoggingUiState>(FoodLoggingUiState.Input())
    val loggingState = mutableLoggingState.asStateFlow()
    private val recentFoodAnalysisCache = RecentFoodAnalysisCache()
    private var analysisJob: Job? = null
    private var analysisRequestId = 0L
    private var loggingSaveInProgress = false

    private val mutableBarcodeAmountState = MutableStateFlow<BarcodeAmountUiState?>(null)
    val barcodeAmountState = mutableBarcodeAmountState.asStateFlow()
    private var lastLoggingText = ""
    private var barcodeLookupRequestId = 0L
    private val mutableMenuScanState = MutableStateFlow(MenuScanUiState())
    val menuScanState = mutableMenuScanState.asStateFlow()
    private var menuScanRequestId = 0L
    private var pendingMenuDishes: List<MenuDish> = emptyList()
    private var pendingMenuLoggingText: String? = null
    private val mutablePortionEditState = MutableStateFlow<PortionEditUiState?>(null)
    val portionEditState = mutablePortionEditState.asStateFlow()
    private val mutableLoggedAmountEditState = MutableStateFlow<LoggedAmountEditUiState?>(null)
    val loggedAmountEditState = mutableLoggedAmountEditState.asStateFlow()
    /** The logged entry currently being rewritten as text on the page, if any. */
    private val mutableEditedEntryId = MutableStateFlow<Long?>(null)
    val editedEntryId = mutableEditedEntryId.asStateFlow()
    private var portionEditIndex: Int? = null
    private val pendingDeletedLogs = PendingDeletedLogStore()
    private val earlyUndoDeleteRequests = mutableSetOf<Long>()
    private val earlyDiscardDeleteRequests = mutableSetOf<Long>()


    init {
        refreshProviderAndHealthStatus()
        viewModelScope.launch {
            runCatching { container.reminderScheduler.reconcileFrom(repository.appPreferencesStore) }
        }
    }

    fun completeOnboarding(draft: OnboardingDraft, plan: NutritionPlan) {
        if (mutableOnboardingSaving.value) return
        viewModelScope.launch {
            mutableOnboardingSaving.value = true
            runCatching {
                val now = System.currentTimeMillis()
                repository.completeOnboarding(
                    draft.toCompleteOnboardingRequest(plan, now, today, zoneId),
                )
            }.onSuccess {
                mutableEvents.emit(AppEvent.OnboardingSaved)
            }.onFailure {
                mutableEvents.emit(AppEvent.Message("Nomi couldn't save your plan. Please try again."))
            }
            mutableOnboardingSaving.value = false
        }
    }

    fun previousDay() { selectedDate.value = selectedDate.value.minusDays(1) }
    fun nextDay() { if (selectedDate.value < today) selectedDate.value = selectedDate.value.plusDays(1) }
    fun persistOnboardingDraft(draft: OnboardingDraft) {
        viewModelScope.launch {
            repository.appPreferencesStore.setOnboardingDraft(
                draft.toPersistedDraft(currentStep = 0, updatedAtEpochMillis = System.currentTimeMillis()),
            )
        }
    }
    fun selectToday() { selectedDate.value = today }
    fun setHistoryQuery(value: String) { historyQuery.value = value }
    fun setHistoryDate(value: LocalDate) { historyDate.value = value.coerceAtMost(today) }
    fun setProgressRange(value: ProgressRange) { progressRange.value = value }

    fun beginLogging(method: AddFoodMethod, initialText: String = "") {
        cancelAnalysis()
        pendingMenuDishes = emptyList()
        pendingMenuLoggingText = null
        barcodeLookupRequestId += 1
        mutableBarcodeAmountState.value = null
        val category = defaultMealCategory()
        mutableLoggingState.value = when (method) {
            AddFoodMethod.TYPE, AddFoodMethod.VOICE -> FoodLoggingUiState.Input(initialText, category)
            else -> FoodLoggingUiState.Input("", category)
        }
        lastLoggingText = initialText
    }

    fun updateLoggingText(value: String) {
        if (value != pendingMenuLoggingText) {
            pendingMenuDishes = emptyList()
            pendingMenuLoggingText = null
        }
        lastLoggingText = value
        val current = mutableLoggingState.value
        if (current is FoodLoggingUiState.Input) mutableLoggingState.value = current.copy(text = value)
    }

    fun editLoggingText() {
        cancelAnalysis()
        val category = when (val current = mutableLoggingState.value) {
            is FoodLoggingUiState.Input -> current.mealCategory
            is FoodLoggingUiState.Preview -> current.mealCategory
            is FoodLoggingUiState.Manual -> current.draft.mealCategory
            else -> defaultMealCategory()
        }
        mutableLoggingState.value = FoodLoggingUiState.Input(lastLoggingText, category)
    }

    fun dismissLoggingDraft() {
        cancelAnalysis()
        pendingMenuDishes = emptyList()
        pendingMenuLoggingText = null
        barcodeLookupRequestId += 1
        mutableBarcodeAmountState.value = null
        lastLoggingText = ""
        dismissPortionEdit()
        mutableEditedEntryId.value = null
        mutableLoggingState.value = FoodLoggingUiState.Input("", defaultMealCategory())
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
        val text = entry.reeditableText()
        lastLoggingText = text
        mutableEditedEntryId.value = entry.id
        mutableLoggingState.value = FoodLoggingUiState.Input(text, defaultMealCategory())
    }

    fun updateLoggingMealCategory(category: MealCategory) {
        mutableLoggingState.value = when (val current = mutableLoggingState.value) {
            is FoodLoggingUiState.Input -> current.copy(mealCategory = category)
            is FoodLoggingUiState.Preview -> current.copy(mealCategory = category)
            is FoodLoggingUiState.Manual -> current.copy(draft = current.draft.copy(mealCategory = category))
            else -> current
        }
    }

    fun showManualLogging(prefillName: String = lastLoggingText) {
        val category = when (val current = mutableLoggingState.value) {
            is FoodLoggingUiState.Input -> current.mealCategory
            is FoodLoggingUiState.Preview -> current.mealCategory
            else -> defaultMealCategory()
        }
        mutableLoggingState.value = FoodLoggingUiState.Manual(
            ManualFoodDraft(name = prefillName, amount = "100", unit = "g", mealCategory = category),
        )
    }

    fun updateManualDraft(value: ManualFoodDraft) {
        mutableLoggingState.value = FoodLoggingUiState.Manual(value)
    }

    fun updatePreviewItem(index: Int, item: AnalyzedFoodItem) {
        val current = mutableLoggingState.value as? FoodLoggingUiState.Preview ?: return
        if (index !in current.analysis.items.indices) return
        val updated = current.analysis.items.toMutableList().apply { this[index] = item }
        mutableLoggingState.value = current.copy(
            analysis = current.analysis.copy(items = updated),
        )
    }

    fun beginMenuScan() {
        menuScanRequestId += 1
        mutableMenuScanState.value = MenuScanUiState()
    }

    fun updateMenuSearch(query: String) {
        mutableMenuScanState.value = mutableMenuScanState.value.copy(query = query.take(200))
    }

    fun scanMenuPage(bytes: ByteArray, mediaType: String) {
        if (bytes.isEmpty()) return
        val requestId = ++menuScanRequestId
        val before = mutableMenuScanState.value
        mutableMenuScanState.value = before.copy(isProcessing = true, errorMessage = null)
        viewModelScope.launch {
            runCatching {
                withConfiguredProvider(ProviderPipeline.VISION) { config, key ->
                    providerFor(config, key).scanMenu(bytes, mediaType)
                }
            }.onSuccess { result ->
                if (requestId != menuScanRequestId) return@onSuccess
                val current = mutableMenuScanState.value
                mutableMenuScanState.value = current.copy(
                    restaurantName = current.restaurantName
                        ?: result.restaurantName?.trim()?.takeIf(String::isNotBlank),
                    items = mergeMenuDishes(current.items, result.items),
                    pageCount = current.pageCount + 1,
                    isProcessing = false,
                    errorMessage = null,
                    notes = (current.notes + result.notes).distinct().takeLast(20),
                )
            }.onFailure { error ->
                if (error is CancellationException) throw error
                if (requestId != menuScanRequestId) return@onFailure
                mutableMenuScanState.value = mutableMenuScanState.value.copy(
                    isProcessing = false,
                    errorMessage = inUserLanguage(
                        english = "Nomi couldn't read that menu page. Add a clearer photo.",
                        german = "Nomi konnte diese Speisekartenseite nicht lesen. F\u00fcge ein deutlicheres Foto hinzu.",
                    ),
                )
            }
        }.invokeOnCompletion { bytes.fill(0) }
    }

    fun toggleMenuDish(dish: MenuDish) {
        val key = menuDishKey(dish)
        val selected = mutableMenuScanState.value.selectedDishKeys
        mutableMenuScanState.value = mutableMenuScanState.value.copy(
            selectedDishKeys = if (key in selected) selected - key else selected + key,
        )
    }

    fun selectMenuDishes() {
        val state = mutableMenuScanState.value
        val dishes = state.items.filter { menuDishKey(it) in state.selectedDishKeys }
        if (dishes.isEmpty()) return
        val restaurant = state.restaurantName
        val text = buildString {
            restaurant?.takeIf(String::isNotBlank)?.let { append("At ").append(it.trim()).append(": ") }
            dishes.forEachIndexed { index, dish ->
                if (index > 0) append("; ")
                append("1 serving ").append(dish.name.trim())
                dish.number?.takeIf(String::isNotBlank)?.let {
                    append(" (menu number ").append(it.trim()).append(')')
                }
                dish.description?.takeIf(String::isNotBlank)?.let {
                    append(". Menu description: ").append(it.trim())
                }
                dish.quantityText?.takeIf(String::isNotBlank)?.let {
                    append(". Printed serving: ").append(it.trim())
                }
            }
        }.take(MAX_MENU_LOGGING_TEXT_CHARS)
        beginLogging(AddFoodMethod.TYPE, text)
        pendingMenuDishes = dishes
        pendingMenuLoggingText = text
        analyzeText()
    }

    /** Removes one component from a detected meal before it is saved. */
    fun removePreviewItem(index: Int) {
        val current = mutableLoggingState.value as? FoodLoggingUiState.Preview ?: return
        if (index !in current.analysis.items.indices) return
        val updated = current.analysis.items.toMutableList().apply { removeAt(index) }
        if (updated.isEmpty()) {
            // Keep the preview actionable; the user can still edit the original meal text or
            // dismiss the draft instead of reaching an empty meal that cannot be saved.
            return
        }
        mutableLoggingState.value = current.copy(
            analysis = current.analysis.copy(items = updated),
        )
    }

    fun beginPortionEdit(index: Int) {
        val preview = mutableLoggingState.value as? FoodLoggingUiState.Preview ?: return
        val item = preview.analysis.items.getOrNull(index) ?: return
        portionEditIndex = index
        mutablePortionEditState.value = PortionEditUiState(current = item.toPortionContext())
    }

    fun updatePortionCorrection(correction: String) {
        mutablePortionEditState.value = mutablePortionEditState.value?.copy(
            correction = correction.take(500),
            proposed = null,
            scaledItem = null,
            needsResearch = false,
            researchReason = null,
            errorMessage = null,
        )
    }

    fun dismissPortionEdit() {
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

        // Shown only while a model is actually being consulted. A locally parsed edit resolves
        // within this call and never flashes a spinner.
        val willAskModel = PortionEditParser.parseOrNull(edit.correction) == null
        if (willAskModel) {
            mutablePortionEditState.value = edit.copy(
                isProcessing = true,
                proposed = null,
                scaledItem = null,
                needsResearch = false,
                errorMessage = null,
            )
        }
        viewModelScope.launch {
            runCatching { editRouter().route(item, edit.correction) }
                .onSuccess { decision ->
                    if (portionEditIndex != index) return@onSuccess
                    when (decision) {
                        is FoodEditRouter.Decision.Scale -> {
                            recordRoute(
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
                    mutablePortionEditState.value = edit.copy(
                        isProcessing = false,
                        errorMessage = error.safeAiMessage(),
                    )
                }
        }
    }

    /** Binds the routing rules to this app's configured cheap classifier. */
    private fun editRouter() = FoodEditRouter { context, correction ->
        withConfiguredProvider(ProviderPipeline.PORTION_CHANGE) { config, key ->
            providerFor(config, key).classifyEdit(context, correction)
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
        val preview = mutableLoggingState.value as? FoodLoggingUiState.Preview ?: return
        val correction = edit.correction.trim()
        if (correction.isBlank()) return

        mutablePortionEditState.value = edit.copy(isProcessing = true, errorMessage = null)
        viewModelScope.launch {
            runCatching {
                val request = buildString {
                    append(item.name)
                    item.brand?.takeIf(String::isNotBlank)?.let { append(" from ").append(it) }
                    append(", ").append(item.quantity.cleanNumber()).append(' ').append(item.unit)
                    append(". Correction: ").append(correction)
                }
                val parsed = (
                    LocalFoodIntentParser.parseOrNull(request)
                        ?: withConfiguredProvider(ProviderPipeline.FOOD_INTERPRETATION) { config, key ->
                            providerFor(config, key).parseFood(request)
                        }
                    ).withKnownSpellings()
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
                researchNutrition(intent)
            }.onSuccess { analysis ->
                if (portionEditIndex != index) return@onSuccess
                recordRoute(
                    route = NutritionRoute.CONTENT_RERESEARCH,
                    decision = NutritionRoute.Decision.CLASSIFIER,
                    detail = edit.researchReason ?: "The edit changed the food itself",
                )
                val replacement = analysis.items.firstOrNull()
                if (replacement == null) {
                    mutablePortionEditState.value = edit.copy(
                        isProcessing = false,
                        errorMessage = inUserLanguage(
                            english = "Nomi couldn't find nutrition for that change. Try again.",
                            german = "Nomi hat für diese Änderung keine Nährwerte gefunden. Versuch es erneut.",
                        ),
                    )
                    return@onSuccess
                }
                val updated = preview.analysis.items.toMutableList().apply {
                    this[index] = replacement
                    // A correction naming several foods replaces the one row it started from
                    // and appends the rest, rather than silently dropping them.
                    addAll(index + 1, analysis.items.drop(1))
                }
                mutableLoggingState.value = preview.copy(
                    analysis = preview.analysis.copy(items = updated),
                )
                dismissPortionEdit()
            }.onFailure { error ->
                if (error is CancellationException) throw error
                mutablePortionEditState.value = edit.copy(
                    isProcessing = false,
                    errorMessage = error.safeAiMessage(),
                )
            }
        }
    }

    private fun currentPreviewItem(index: Int): AnalyzedFoodItem? =
        (mutableLoggingState.value as? FoodLoggingUiState.Preview)?.analysis?.items?.getOrNull(index)

    /**
     * Keeps the diagnostic trail in the existing debug log, where it is already gated behind the
     * developer switch. Failures here are swallowed on purpose: a bookkeeping problem must never
     * be the reason a user's correction does not apply.
     */
    private fun recordRoute(
        route: NutritionRoute,
        decision: NutritionRoute.Decision,
        detail: String,
        confidence: Double? = null,
    ) {
        if (!preferences.value.aiDebugEnabled) return
        viewModelScope.launch {
            runCatching {
                val selection = preferences.value.selectionFor(
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

    /** Presents a deterministic result in the shape the preview sheet already renders. */
    private fun PortionEditApplier.Result.toPortionAdjustment(): PortionAdjustment = PortionAdjustment(
        newQuantity = item.quantity,
        newUnit = item.unit,
        multiplier = factor,
        newGrams = item.gramsEquivalent,
        interpretation = description,
        requiresConfirmation = false,
    )

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
        updatePreviewItem(index, updated)
        dismissPortionEdit()
    }

    fun analyzeText() {
        val current = mutableLoggingState.value as? FoodLoggingUiState.Input ?: return
        val text = current.text.trim()
        if (text.isBlank()) return
        val menuDishes = pendingMenuDishes.takeIf { pendingMenuLoggingText == text && it.isNotEmpty() }
        lastLoggingText = text
        val cacheKey = foodAnalysisCacheKey(text)
        recentFoodAnalysisCache.get(cacheKey)?.takeIf { menuDishes == null }?.let { analysis ->
            mutableLoggingState.value = FoodLoggingUiState.Preview(
                analysis = analysis,
                mealCategory = current.mealCategory,
                originalText = text,
            )
            return
        }

        analysisJob?.cancel()
        val requestId = ++analysisRequestId
        // Claim the input synchronously so repeated taps cannot launch duplicate provider calls.
        mutableLoggingState.value = FoodLoggingUiState.Processing(
            AiProcessingStage.UNDERSTANDING_MEAL,
            originalText = text,
        )
        val job = viewModelScope.launch {
            val intent = runCatching {
                (
                    LocalFoodIntentParser.parseOrNull(text)
                        ?: withConfiguredProvider(ProviderPipeline.FOOD_INTERPRETATION) { config, key ->
                            providerFor(config, key).parseFood(text)
                        }
                    ).withKnownSpellings().let { parsed ->
                        menuDishes?.let { UserQuantityResolver.applyMenuQuantities(it, parsed) }
                            ?: parsed
                    }
            }.getOrElse { error ->
                if (error is CancellationException) throw error
                if (requestId == analysisRequestId) {
                    mutableLoggingState.value = FoodLoggingUiState.Error(
                        error.safeAiMessage(),
                        canRetry = true,
                        originalText = text,
                    )
                }
                return@launch
            }

            cachedNutritionAnalysis(intent)?.let { cached ->
                if (requestId == analysisRequestId) {
                    mutableLoggingState.value = FoodLoggingUiState.Preview(
                        cached,
                        current.mealCategory,
                        originalText = text,
                    )
                }
                return@launch
            }

            // Keep one owner for the whole lookup. A quick estimate used to be saveable before
            // research silently replaced its calories, making the day total change afterwards.
            mutableLoggingState.value = FoodLoggingUiState.Processing(
                AiProcessingStage.FINDING_NUTRITION,
                originalText = text,
            )
            runCatching { researchNutrition(intent) }
                .onSuccess { analysis ->
                    if (requestId != analysisRequestId) return@onSuccess
                    recentFoodAnalysisCache.put(cacheKey, analysis)
                    // Persist trusted gram-based results as soon as research succeeds. This
                    // means a retry, app restart, or abandoned preview can reuse the nutrition
                    // without another Exa/Gemini request; estimates and size-only portions are
                    // intentionally skipped by cacheAnalyzedFood's provenance/weight checks.
                    analysis.items.forEach { item ->
                        runCatching { cacheAnalyzedFood(item) }
                    }
                    recordRoute(
                        route = NutritionRoute.NEW_RESEARCH,
                        decision = NutritionRoute.Decision.DIRECT,
                        detail = "New food entry researched before preview",
                    )
                    mutableLoggingState.value = FoodLoggingUiState.Preview(
                        analysis = analysis,
                        mealCategory = current.mealCategory,
                        originalText = text,
                    )
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    if (requestId != analysisRequestId) return@onFailure
                    mutableLoggingState.value = FoodLoggingUiState.Error(
                        researchFailureMessage(error),
                        canRetry = true,
                        originalText = text,
                    )
                }
        }
        analysisJob = job
        job.invokeOnCompletion {
            if (analysisJob === job) analysisJob = null
        }
    }
    fun retryAnalysis() {
        editLoggingText()
        analyzeText()
    }

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
        cancelAnalysis()
        val requestId = ++barcodeLookupRequestId
        val category = defaultMealCategory()
        mutableBarcodeAmountState.value = null
        viewModelScope.launch {
            mutableLoggingState.value = FoodLoggingUiState.Processing(AiProcessingStage.FINDING_NUTRITION)
            runCatching {
                val reading = withConfiguredProvider(ProviderPipeline.VISION) { config, key ->
                    providerFor(config, key).readNutritionLabel(bytes, mediaType)
                }
                val sourceItem = reading.toAnalyzedItem()
                cacheAnalyzedFood(sourceItem)
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
                if (requestId != barcodeLookupRequestId) return@onSuccess
                mutableLoggingState.value = FoodLoggingUiState.Input("", category)
                updateBarcodeAmountState(amountState)
            }.onFailure { error ->
                if (error is CancellationException) throw error
                if (requestId != barcodeLookupRequestId) return@onFailure
                mutableLoggingState.value = FoodLoggingUiState.Error(
                    error.safeAiMessage(),
                    canRetry = false,
                )
            }
        }
    }

    /**
     * A printed table is a source serving like any other, so it enters the pipeline the same
     * way an Open Food Facts product does. It is never an estimate: these numbers were read,
     * not guessed.
     */
    private fun NutritionLabelReading.toAnalyzedItem(): AnalyzedFoodItem {
        val unit = basisUnit.trim().ifBlank { "g" }
        val grams = basisQuantity.takeIf { unit.equals("g", ignoreCase = true) }
        return AnalyzedFoodItem(
            name = FoodDisplayName.clean(
                productName?.takeIf(String::isNotBlank) ?: inUserLanguage(
                    english = "Photographed label",
                    german = "Fotografiertes Etikett",
                ),
            ),
            brand = brand?.takeIf(String::isNotBlank)?.take(200),
            quantity = basisQuantity,
            unit = unit,
            gramsEquivalent = grams,
            calories = calories,
            proteinGrams = proteinGrams,
            carbohydrateGrams = carbohydrateGrams,
            fatGrams = fatGrams,
            fiberGrams = fiberGrams,
            sugarGrams = sugarGrams,
            saturatedFatGrams = saturatedFatGrams,
            sodiumMilligrams = sodiumMilligrams,
            sourceName = inUserLanguage(
                english = "Nutrition label photo",
                german = "Foto der Nährwerttabelle",
            ),
            sourceProductName = productName?.takeIf(String::isNotBlank),
            sourceServingQuantity = basisQuantity,
            sourceServingUnit = unit,
            sourceServingGramsEquivalent = grams,
            sourcePackageQuantity = packageQuantity,
            sourcePackageUnit = packageUnit?.takeIf(String::isNotBlank),
            confidence = confidence,
            assumptions = notes,
            isEstimate = false,
        )
    }

    /**
     * Recognizes a photo and stops there, handing the description back for review.
     *
     * Research is the expensive half in both money and seconds, so it does not start until the
     * user has agreed the photo was read correctly. Recognition mistakes are cheap to fix as
     * words and expensive to fix as nutrition.
     */
    fun analyzePhoto(bytes: ByteArray, mediaType: String) {
        analysisJob?.cancel()
        val requestId = ++analysisRequestId
        val job = viewModelScope.launch {
            val category = defaultMealCategory()
            runCatching {
                mutableLoggingState.value = FoodLoggingUiState.Processing(AiProcessingStage.UNDERSTANDING_MEAL)
                withConfiguredProvider(ProviderPipeline.VISION) { config, key ->
                    providerFor(config, key).identifyFood(bytes, mediaType)
                }
            }.onSuccess { vision ->
                if (requestId != analysisRequestId) return@onSuccess
                val recognized = vision.items.map { item ->
                    ParsedFoodItem(
                        name = item.name,
                        quantity = item.estimatedQuantity,
                        unit = item.unit,
                        gramsEquivalent = item.estimatedGrams,
                        assumptions = item.visibleIngredients,
                    )
                }
                val description = recognized.toMealDescription()
                recordRoute(
                    route = NutritionRoute.PHOTO_DESCRIPTION,
                    decision = NutritionRoute.Decision.DIRECT,
                    detail = "Photo described by the vision model; no nutrition looked up yet",
                )
                lastLoggingText = description
                mutableLoggingState.value = FoodLoggingUiState.PhotoReview(
                    description = description,
                    recognizedDescription = description,
                    recognizedItems = recognized,
                    mealCategory = category,
                    notes = vision.notes,
                )
            }.onFailure { error ->
                if (error is CancellationException) throw error
                if (requestId != analysisRequestId) return@onFailure
                mutableLoggingState.value = FoodLoggingUiState.Error(error.safeAiMessage(), canRetry = false)
            }
        }
        analysisJob = job
        job.invokeOnCompletion {
            bytes.fill(0)
            if (analysisJob === job) analysisJob = null
        }
    }

    fun updatePhotoDescription(description: String) {
        val current = mutableLoggingState.value as? FoodLoggingUiState.PhotoReview ?: return
        mutableLoggingState.value = current.copy(description = description.take(MAX_PHOTO_DESCRIPTION_CHARS))
    }

    fun updatePhotoPlace(place: String) {
        val current = mutableLoggingState.value as? FoodLoggingUiState.PhotoReview ?: return
        mutableLoggingState.value = current.copy(place = place.take(MAX_PHOTO_PLACE_CHARS))
    }

    /**
     * Researches the reviewed description.
     *
     * An untouched description still carries the vision model's portion and weight estimates, so
     * those are kept. An edited one no longer describes the same foods, so it re-enters through
     * the ordinary text path and is parsed like anything the user types.
     */
    fun confirmPhotoDescription() {
        val review = mutableLoggingState.value as? FoodLoggingUiState.PhotoReview ?: return
        val description = review.description.trim()
        if (description.isBlank()) return

        analysisJob?.cancel()
        val requestId = ++analysisRequestId
        val place = review.place.trim().takeIf(String::isNotBlank)
        lastLoggingText = description
        mutableLoggingState.value = FoodLoggingUiState.Processing(
            AiProcessingStage.UNDERSTANDING_MEAL,
            originalText = description,
        )
        val job = viewModelScope.launch {
            runCatching {
                val items = if (review.isEdited || review.recognizedItems.isEmpty()) {
                    (
                        LocalFoodIntentParser.parseOrNull(description)
                            ?: withConfiguredProvider(ProviderPipeline.FOOD_INTERPRETATION) { config, key ->
                                providerFor(config, key).parseFood(description)
                            }
                        ).withKnownSpellings().items
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
                mutableLoggingState.value = FoodLoggingUiState.Processing(
                    AiProcessingStage.FINDING_NUTRITION,
                    originalText = description,
                    sourceUrls = listOfNotNull(currentResearchProviderWebsite()),
                )
                researchNutrition(intent).also {
                    recordRoute(
                        route = NutritionRoute.NEW_RESEARCH,
                        decision = NutritionRoute.Decision.DIRECT,
                        detail = "Reviewed photo description researched on the web",
                    )
                }
            }.onSuccess { analysis ->
                if (requestId != analysisRequestId) return@onSuccess
                // A photo lands on the page as the same preview a typed meal produces, so the
                // entry reads as if it had been written and "change wording" starts from something.
                val describedFoods = analysis.items.joinToString(", ", transform = AnalyzedFoodItem::name)
                lastLoggingText = describedFoods
                mutableLoggingState.value = FoodLoggingUiState.Preview(
                    analysis,
                    review.mealCategory,
                    originalText = describedFoods,
                )
            }.onFailure { error ->
                if (error is CancellationException) throw error
                if (requestId != analysisRequestId) return@onFailure
                mutableLoggingState.value = FoodLoggingUiState.Error(
                    error.safeAiMessage(),
                    canRetry = false,
                    originalText = description,
                )
            }
        }
        analysisJob = job
        job.invokeOnCompletion {
            if (analysisJob === job) analysisJob = null
        }
    }

    /**
     * Writes recognized foods the way a person would have typed them, so the review field holds
     * an ordinary sentence rather than a report. An item without a usable amount contributes
     * only its name; inventing "1 piece" would put a quantity in the user's mouth.
     */
    private fun List<ParsedFoodItem>.toMealDescription(): String = joinToString(", ") { item ->
        val quantity = item.quantity?.takeIf { it.isFinite() && it > 0.0 }
        val unit = item.unit?.trim()?.takeIf(String::isNotBlank)
        when {
            quantity != null && unit != null -> "${quantity.cleanNumber()} $unit ${item.name}"
            quantity != null -> "${quantity.cleanNumber()} ${item.name}"
            else -> item.name
        }
    }
    fun lookupBarcode(barcode: String) {
        cancelAnalysis()
        val requestId = ++barcodeLookupRequestId
        val category = defaultMealCategory()
        mutableBarcodeAmountState.value = null
        viewModelScope.launch {
            mutableLoggingState.value = FoodLoggingUiState.Processing(AiProcessingStage.FINDING_NUTRITION)
            runCatching {
                val cached = repository.foodByBarcode(barcode)
                var servingLabel: String? = null
                val analyzedItem = if (cached != null) {
                    cached.toAnalyzedItem("Local barcode cache")
                } else {
                    val product = container.openFoodFacts.findByBarcode(barcode)
                    servingLabel = product?.servingSize
                    product?.toAnalyzedItemOrNull() ?: run {
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
                        ).items.single()
                    }
                }
                cacheAnalyzedFood(analyzedItem, barcode)
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
                if (requestId != barcodeLookupRequestId) return@onSuccess
                updateBarcodeAmountState(amountState)
            }.onFailure { error ->
                if (requestId != barcodeLookupRequestId) return@onFailure
                mutableLoggingState.value = FoodLoggingUiState.Error(error.safeAiMessage(), canRetry = false)
            }
        }
    }

    fun updateBarcodeAmount(value: String) {
        val current = mutableBarcodeAmountState.value ?: return
        updateBarcodeAmountState(
            current.copy(
                amount = BarcodeAmountSupport.sanitizeAmount(value),
                errorMessage = null,
            ),
        )
    }

    fun updateBarcodeUnit(unit: String) {
        val current = mutableBarcodeAmountState.value ?: return
        if (unit !in current.compatibleUnits) return
        updateBarcodeAmountState(current.copy(unit = unit, errorMessage = null))
    }

    fun confirmBarcodeAmount() {
        val current = mutableBarcodeAmountState.value ?: return
        val quantity = current.parsedAmount ?: run {
            mutableBarcodeAmountState.value = current.copy(errorMessage = "Enter an amount greater than zero")
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
            lastLoggingText = description
            mutableBarcodeAmountState.value = null
            mutableLoggingState.value = FoodLoggingUiState.Preview(
                analysis = FoodAnalysis(listOf(item), overallConfidence = item.confidence),
                mealCategory = current.mealCategory,
                originalText = description,
            )
        }.onFailure { error ->
            mutableBarcodeAmountState.value = current.copy(errorMessage = error.safeAiMessage())
        }
    }

    fun cancelBarcodeAmount() {
        mutableBarcodeAmountState.value = null
        dismissLoggingDraft()
    }

    private fun updateBarcodeAmountState(state: BarcodeAmountUiState) {
        mutableBarcodeAmountState.value = state
        lastLoggingText = BarcodeAmountSupport.description(state.amount, state.unit, state.sourceItem.name)
        mutableLoggingState.value = FoodLoggingUiState.Input(lastLoggingText, state.mealCategory)
    }

    fun confirmLogging() {
        if (loggingSaveInProgress) return
        val current = mutableLoggingState.value
        if (current !is FoodLoggingUiState.Preview && current !is FoodLoggingUiState.Manual) return
        loggingSaveInProgress = true
        viewModelScope.launch {
            try {
                runCatching {
                    when (current) {
                        is FoodLoggingUiState.Preview -> {
                            val validated = ServingNutritionNormalizer.validateBeforeSave(current.analysis)
                            val logs = validated.items.map { item ->
                                item.toLog(current.mealCategory, "ai").copy(foodId = cacheAnalyzedFood(item))
                            }
                            repository.addLogs(logs)
                        }
                        is FoodLoggingUiState.Manual -> {
                            require(current.draft.isValid)
                            val log = current.draft.toLog()
                            repository.addLog(log.copy(foodId = cacheLogFood(log)))
                        }
                        else -> error("Unsupported logging state")
                    }
                }.onSuccess {
                    // The rewritten entry exists now, so the one it replaces can go.
                    mutableEditedEntryId.value?.let { replaced ->
                        mutableEditedEntryId.value = null
                        runCatching { repository.deleteLog(replaced) }
                    }
                    lastLoggingText = ""
                    dismissPortionEdit()
                    mutableBarcodeAmountState.value = null
                    mutableLoggingState.value = FoodLoggingUiState.Input("", defaultMealCategory())
                    mutableEvents.emit(AppEvent.FoodSaved)
                }.onFailure { error ->
                    mutableEvents.emit(AppEvent.Message(error.safeAiMessage()))
                }
            } finally {
                loggingSaveInProgress = false
            }
        }
    }
    fun favoriteFoodLog(id: Long) {
        viewModelScope.launch {
            runCatching {
                val log = requireNotNull(repository.foodLog(id))
                val foodId = requireNotNull(log.foodId) { "This entry has no reusable food" }
                val now = System.currentTimeMillis()
                repository.favorite(
                    FavoriteFoodEntity(
                        foodId = foodId,
                        typicalAmount = log.amount,
                        typicalUnit = log.unit,
                        typicalGrams = log.grams,
                        createdAtEpochMillis = now,
                        lastUsedAtEpochMillis = now,
                    ),
                )
            }.onFailure {
                mutableEvents.emit(AppEvent.Message("Save this food again before favoriting it"))
            }
        }
    }

    fun deleteFoodLog(id: Long) {
        viewModelScope.launch {
            runCatching { repository.deleteLog(id) }
                .onFailure { mutableEvents.emit(AppEvent.Message("Nomi couldn't delete that food.")) }
        }
    }

    fun startLoggedAmountEdit(entry: TodayFoodEntry) {
        if (entry.id <= 0 || entry.amount <= 0.0) return
        mutableLoggedAmountEditState.value = LoggedAmountEditUiState(
            entryId = entry.id,
            name = entry.name,
            unit = entry.unit,
            originalAmount = entry.amount,
            originalCalories = entry.calories,
            amountText = formatLoggedAmountInput(entry.amount),
        )
    }

    fun updateLoggedAmountInput(text: String) {
        mutableLoggedAmountEditState.value = mutableLoggedAmountEditState.value?.copy(
            amountText = text.take(12),
            error = null,
        )
    }

    fun dismissLoggedAmountEdit() {
        mutableLoggedAmountEditState.value = null
    }

    fun applyLoggedAmountEdit() {
        val edit = mutableLoggedAmountEditState.value ?: return
        if (edit.isSaving) return
        val amount = edit.parsedAmount ?: run {
            mutableLoggedAmountEditState.value =
                edit.copy(error = LoggedAmountEditError.INVALID_AMOUNT)
            return
        }
        mutableLoggedAmountEditState.value = edit.copy(isSaving = true, error = null)
        viewModelScope.launch {
            runCatching {
                repository.updateLoggedAmount(
                    id = edit.entryId,
                    newAmount = amount,
                    updatedAtEpochMillis = System.currentTimeMillis(),
                )
            }.onSuccess { updated ->
                mutableLoggedAmountEditState.value = if (updated) {
                    null
                } else {
                    mutableLoggedAmountEditState.value?.copy(
                        isSaving = false,
                        error = LoggedAmountEditError.ENTRY_GONE,
                    )
                }
            }.onFailure {
                mutableLoggedAmountEditState.value = mutableLoggedAmountEditState.value?.copy(
                    isSaving = false,
                    error = LoggedAmountEditError.SAVE_FAILED,
                )
            }
        }
    }

    /** Starts the Today-row delete while retaining an exact database snapshot for inline Undo. */
    fun deleteFoodLogForUndo(id: Long) {
        if (id <= 0 || pendingDeletedLogs.peek(id) != null) return
        viewModelScope.launch {
            runCatching {
                repository.deleteLogForUndo(id)
                    ?: error("That food is no longer available")
            }.onSuccess { snapshot ->
                if (earlyDiscardDeleteRequests.remove(id)) {
                    earlyUndoDeleteRequests.remove(id)
                    return@onSuccess
                }
                pendingDeletedLogs.remember(snapshot)
                if (earlyUndoDeleteRequests.remove(id)) restoreDeletedFoodLog(id)
            }.onFailure { error ->
                earlyUndoDeleteRequests.remove(id)
                earlyDiscardDeleteRequests.remove(id)
                mutableEvents.emit(AppEvent.Message(error.message ?: "Nomi couldn't delete that food."))
            }
        }
    }

    /** Handles both normal Undo and the tiny race where Undo is tapped before Room returns. */
    fun undoDeletedFoodLog(id: Long) {
        if (pendingDeletedLogs.peek(id) == null) {
            if (id > 0 && id !in earlyDiscardDeleteRequests) earlyUndoDeleteRequests += id
            return
        }
        restoreDeletedFoodLog(id)
    }

    /** Closes the short Undo window without showing a transient confirmation banner. */
    fun discardDeletedFoodLog(id: Long) {
        earlyUndoDeleteRequests.remove(id)
        if (pendingDeletedLogs.peek(id) == null) earlyDiscardDeleteRequests += id
        else pendingDeletedLogs.discard(id)
    }

    private fun restoreDeletedFoodLog(id: Long) {
        val snapshot = pendingDeletedLogs.take(id) ?: return
        viewModelScope.launch {
            runCatching {
                check(repository.restoreDeletedLog(snapshot)) { "The deleted food could not be restored" }
            }.onFailure { error ->
                pendingDeletedLogs.remember(snapshot)
                mutableEvents.emit(AppEvent.Message(error.message ?: "Nomi couldn't restore that food."))
            }
        }
    }
    fun saveHistoryDayAsMeal(day: HistoryDay, name: String) {
        if (name.isBlank() || day.entries.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                repository.saveLoggedMeal(
                    SaveLoggedMealRequest(
                        name = name.trim(),
                        normalizedName = name.trim().lowercase(Locale.ROOT),
                        logIds = day.entries.map { it.id },
                        defaultMealCategory = day.entries.first().mealCategory.name,
                        createdAtEpochMillis = System.currentTimeMillis(),
                    ),
                )
            }.onFailure {
                mutableEvents.emit(AppEvent.Message("Nomi couldn't save that meal"))
            }
        }
    }


    fun duplicateFoodLog(id: Long) {
        viewModelScope.launch {
            runCatching {
                val source = requireNotNull(repository.foodLog(id))
                val now = System.currentTimeMillis()
                repository.addLog(source.copy(id = 0, loggedAtEpochMillis = now, createdAtEpochMillis = now, updatedAtEpochMillis = now))
            }.onFailure { mutableEvents.emit(AppEvent.Message("That food is no longer available")) }
        }
    }

    fun copyDayToToday(source: LocalDate) {
        viewModelScope.launch {
            runCatching {
                repository.copyDay(source.toString(), today.toString(), System.currentTimeMillis(), zoneId.id)
            }.onFailure { mutableEvents.emit(AppEvent.Message("Nomi couldn't copy that day.")) }
        }
    }

    fun addWeight(kilograms: Double, note: String?) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val localId = runCatching {
                repository.addWeight(
                    WeightEntryEntity(
                        weightKg = kilograms,
                        localDate = today.toString(),
                        measuredAtEpochMillis = now,
                        zoneId = zoneId.id,
                        note = note?.trim()?.takeIf(String::isNotBlank),
                        createdAtEpochMillis = now,
                        updatedAtEpochMillis = now,
                    ),
                )
            }.getOrElse {
                mutableEvents.emit(AppEvent.Message("Enter a valid weight."))
                return@launch
            }

            val canWriteWeight = runCatching {
                container.healthConnect.hasPermissions(HealthFeatures(writeWeight = true))
            }.getOrDefault(false)
            if (!canWriteWeight) return@launch

            runCatching {
                container.healthConnect.writeWeight(
                    kilograms = kilograms,
                    time = Instant.ofEpochMilli(now),
                    clientRecordId = "nomi-weight-${UUID.randomUUID()}",
                    zoneId = zoneId,
                )
            }.onSuccess { healthConnectId ->
                runCatching {
                    repository.markWeightHealthConnectSynced(
                        id = localId,
                        externalId = healthConnectId,
                        updatedAtEpochMillis = System.currentTimeMillis(),
                    )
                }.onFailure {
                    mutableEvents.emit(
                        AppEvent.Message("Weight was saved in Nomi and Health Connect, but sync status couldn't be updated."),
                    )
                }
            }.onFailure {
                mutableEvents.emit(
                    AppEvent.Message("Weight was saved in Nomi, but Health Connect sync failed."),
                )
            }
        }
    }

    fun addLibraryItem(item: LibraryItem) {
        viewModelScope.launch {
            runCatching {
                when (item.kind) {
                    LibraryItemKind.RECENT -> recentFoodsSnapshot.first { it.id == item.id }
                        .let { repository.addLog(it.toLog()) }
                    LibraryItemKind.FAVORITE -> favoriteSnapshot.first { it.food.id == item.id }
                        .let { repository.addLog(it.toLog()) }
                    LibraryItemKind.SAVED_MEAL -> repository.addSavedMealToLog(
                        AddSavedMealToLogRequest(
                            savedMealId = item.id,
                            mealCategory = defaultMealCategory().name,
                            localDate = selectedDate.value.toString(),
                            startEpochMillis = System.currentTimeMillis(),
                            zoneId = zoneId.id,
                        ),
                    )
                }
            }.onSuccess {
                mutableEvents.emit(AppEvent.FoodSaved)
            }.onFailure { mutableEvents.emit(AppEvent.Message("Nomi couldn't add that item.")) }
        }
    }

    fun saveNutritionTargets(calories: Int, protein: Int, carbs: Int, fat: Int) {
        val plan = currentPlan.value ?: return
        if (calories !in 800..10_000 || protein !in 0..600 || carbs !in 0..900 || fat !in 0..300) {
            mutableEvents.tryEmit(AppEvent.Message("Check the nutrition target values."))
            return
        }
        viewModelScope.launch {
            runCatching {
                repository.saveNewPlan(
                    plan.copy(
                        id = 0,
                        version = 0,
                        effectiveFromLocalDate = today.toString(),
                        calorieTargetKcal = calories.toDouble(),
                        proteinTargetGrams = protein.toDouble(),
                        carbohydrateTargetGrams = carbs.toDouble(),
                        fatTargetGrams = fat.toDouble(),
                        calorieTargetCustom = true,
                        proteinTargetCustom = true,
                        carbohydrateTargetCustom = true,
                        fatTargetCustom = true,
                        changeReason = "targets_edited",
                        createdAtEpochMillis = System.currentTimeMillis(),
                    ),
                )
            }.onFailure { mutableEvents.emit(AppEvent.Message("Nomi couldn't save those targets.")) }
        }
    }

    fun saveProfile(edit: ProfileEdit) {
        val existingProfile = profile.value ?: return
        val existingPlan = currentPlan.value ?: return
        val calculationWeight = latestWeight.value?.weightKg ?: existingProfile.startingWeightKg
        viewModelScope.launch {
            runCatching {
                val goal = com.nomi.app.domain.GoalType.valueOf(edit.goalType)
                val energySex = com.nomi.app.domain.EnergySex.valueOf(edit.energyCalculationSex)
                val updatedProfile = existingProfile.copy(
                    dateOfBirth = edit.dateOfBirth,
                    energyCalculationSex = energySex.name,
                    heightCm = edit.heightCm,
                    goalType = goal.name,
                    targetWeightKg = edit.targetWeightKg.takeIf { goal != com.nomi.app.domain.GoalType.MAINTAIN },
                    activityLevel = edit.activityLevel,
                    progressionRate = edit.progressionRate.takeIf { goal != com.nomi.app.domain.GoalType.MAINTAIN },
                    updatedAtEpochMillis = System.currentTimeMillis(),
                )
                val nextPlan = if (energySex == com.nomi.app.domain.EnergySex.MANUAL) {
                    existingPlan.copy(
                        id = 0,
                        version = 0,
                        effectiveFromLocalDate = today.toString(),
                        changeReason = "profile_edited_keep_custom",
                        createdAtEpochMillis = System.currentTimeMillis(),
                    )
                } else {
                    val draft = OnboardingDraft(
                        dateOfBirth = LocalDate.parse(edit.dateOfBirth),
                        energySex = energySex,
                        heightCm = edit.heightCm,
                        currentWeightKg = calculationWeight,
                        goalType = goal,
                        targetWeightKg = updatedProfile.targetWeightKg,
                        activityLevel = com.nomi.app.domain.ActivityLevel.valueOf(edit.activityLevel),
                        progressRate = edit.progressionRate?.let(com.nomi.app.domain.ProgressRate::valueOf),
                    )
                    val recommendation = com.nomi.app.domain.EnergyCalculator.calculate(draft, today)
                    val effective = if (edit.keepCustomTargets && (
                            existingPlan.calorieTargetCustom || existingPlan.proteinTargetCustom ||
                                existingPlan.carbohydrateTargetCustom || existingPlan.fatTargetCustom
                        )
                    ) {
                        recommendation.withOverrides(
                            caloriesKcal = existingPlan.calorieTargetKcal.roundToInt(),
                            proteinGrams = existingPlan.proteinTargetGrams.roundToInt(),
                            carbsGrams = existingPlan.carbohydrateTargetGrams.roundToInt(),
                            fatGrams = existingPlan.fatTargetGrams.roundToInt(),
                        )
                    } else recommendation
                    effective.toEntity(today, System.currentTimeMillis(), changeReason = "profile_edited")
                }
                check(repository.updateProfile(updatedProfile)) { "Profile update failed" }
                repository.saveNewPlan(nextPlan)
            }.onFailure { mutableEvents.emit(AppEvent.Message("Nomi couldn't recalculate that profile.")) }
        }
    }
    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch {
            repository.appPreferencesStore.setAppearance(mode.toPreference(), preferences.value.dynamicColorEnabled)
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            repository.appPreferencesStore.setAppearance(preferences.value.theme, enabled)
        }
    }

    fun setGermanTranslationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.appPreferencesStore.setGermanTranslationEnabled(enabled)
        }
    }

    fun setUnits(system: UnitSystem) {
        viewModelScope.launch {
            repository.appPreferencesStore.setUnits(
                weight = if (system == UnitSystem.METRIC) WeightUnitPreference.KILOGRAMS else WeightUnitPreference.POUNDS,
                height = if (system == UnitSystem.METRIC) HeightUnitPreference.CENTIMETERS else HeightUnitPreference.FEET_AND_INCHES,
            )
        }
    }

    fun setActivityAdjustment(enabled: Boolean) {
        viewModelScope.launch { repository.appPreferencesStore.setAdjustTargetFromActivity(enabled) }
    }

    fun setGoalsCardStyle(style: GoalsCardStyle) {
        viewModelScope.launch { repository.appPreferencesStore.setGoalsCardStyle(style) }
    }

    fun setCalorieEstimateBias(bias: CalorieEstimateBias) {
        viewModelScope.launch {
            repository.appPreferencesStore.setCalorieEstimateBias(bias)
            // Cached analyses were biased under the previous setting.
            recentFoodAnalysisCache.clear()
        }
    }

    fun toggleReminder(index: Int, enabled: Boolean) {
        viewModelScope.launch {
            val current = preferences.value.reminders
            val updated = when (index) {
                0 -> current.copy(breakfast = current.breakfast.copy(enabled = enabled))
                1 -> current.copy(lunch = current.lunch.copy(enabled = enabled))
                2 -> current.copy(dinner = current.dinner.copy(enabled = enabled))
                3 -> current.copy(dailySummary = current.dailySummary.copy(enabled = enabled))
                4 -> current.copy(weight = current.weight.copy(enabled = enabled))
                else -> return@launch
            }
            repository.appPreferencesStore.setReminders(updated)
            container.reminderScheduler.reconcile(updated)
        }
    }

    /**
     * Moves one reminder to a new time of day and reschedules it.
     *
     * A reminder that is off keeps its new time so turning it on later fires when the user
     * expects, rather than at whatever default it shipped with.
     */
    fun setReminderTime(index: Int, hour: Int, minute: Int) {
        viewModelScope.launch {
            val localTime = "%02d:%02d".format(hour, minute)
            val current = preferences.value.reminders
            val updated = when (index) {
                0 -> current.copy(breakfast = current.breakfast.copy(localTime = localTime))
                1 -> current.copy(lunch = current.lunch.copy(localTime = localTime))
                2 -> current.copy(dinner = current.dinner.copy(localTime = localTime))
                3 -> current.copy(dailySummary = current.dailySummary.copy(localTime = localTime))
                4 -> current.copy(weight = current.weight.copy(localTime = localTime))
                else -> return@launch
            }
            repository.appPreferencesStore.setReminders(updated)
            container.reminderScheduler.reconcile(updated)
        }
    }

    fun saveMicronutrientPreferences(micronutrients: MicronutrientPreferences) {
        viewModelScope.launch { repository.appPreferencesStore.setMicronutrients(micronutrients) }
    }

    fun setAiDebugEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.appPreferencesStore.setAiDebugEnabled(enabled) }
    }

    fun setAiRequestTimeoutDisabled(disabled: Boolean) {
        viewModelScope.launch {
            repository.appPreferencesStore.setAiRequestTimeoutDisabled(disabled)
        }
    }

    fun providerEditorState(index: Int): AiProviderEditorState {
        val settings = settingsState.value.aiProviders.getOrNull(index)
            ?: settingsState.value.aiProviders.firstOrNull()
            ?: AiProviderSetting("Food research", AiProviderKind.PERPLEXITY, "sonar", "https://api.perplexity.ai", false)
        return AiProviderEditorState(
            purpose = settings.purpose,
            provider = settings.provider,
            model = settings.model,
            endpoint = settings.endpoint,
            hasStoredApiKey = settings.hasPrimaryApiKey,
            hasStoredSearchApiKey = settings.hasSearchApiKey,
        )
    }

    fun saveProvider(
        index: Int,
        state: AiProviderEditorState,
        onResult: (success: Boolean, message: String) -> Unit,
    ) {
        viewModelScope.launch {
            runCatching {
                val pipeline = ProviderPipeline.entries.getOrElse(index) { ProviderPipeline.FOOD_RESEARCH }
                val draft = state.toProviderSelection(pipeline)
                val config = draft.toRuntimeConfig()
                val selection = draft.copy(endpoint = config.endpoint)
                state.apiKeyInput.normalizedApiKeyCharsOrNull()?.let { chars ->
                    try {
                        container.secretStore.put(secretId(selection), chars)
                    } finally {
                        chars.fill('\u0000')
                    }
                }
                if (config.kind == AiProviderKind.EXA_GEMINI) {
                    state.searchApiKeyInput.normalizedApiKeyCharsOrNull()?.let { chars ->
                        try {
                            container.secretStore.put(exaSecretId(), chars)
                        } finally {
                            chars.fill('\u0000')
                        }
                    }
                }
                repository.appPreferencesStore.setProvider(pipeline, selection)
            }.onSuccess {
                recentFoodAnalysisCache.clear()
                refreshProviderAndHealthStatus()
                onResult(true, "Provider saved")
            }.onFailure { error ->
                onResult(false, error.safeProviderSettingsMessage())
            }
        }
    }

    fun removeProviderKey(
        index: Int,
        state: AiProviderEditorState,
        onResult: (success: Boolean, message: String) -> Unit,
    ) {
        viewModelScope.launch {
            runCatching {
                val pipeline = ProviderPipeline.entries.getOrElse(index) { ProviderPipeline.FOOD_RESEARCH }
                val selection = state.toProviderSelection(pipeline)
                val primaryRemoved = container.secretStore.delete(secretId(selection))
                val searchRemoved = if (selection.providerId.equals("exa-gemini", true)) {
                    container.secretStore.delete(exaSecretId())
                } else false
                primaryRemoved || searchRemoved
            }.onSuccess { removed ->
                recentFoodAnalysisCache.clear()
                refreshProviderAndHealthStatus()
                onResult(
                    true,
                    if (removed) "Stored API key removed" else "No stored API key was found",
                )
            }.onFailure { error ->
                onResult(false, error.safeProviderSettingsMessage())
            }
        }
    }

    fun testProvider(index: Int, state: AiProviderEditorState, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                val pipeline = ProviderPipeline.entries.getOrElse(index) { ProviderPipeline.FOOD_RESEARCH }
                val draft = state.toProviderSelection(pipeline)
                val config = draft.toRuntimeConfig()
                val selection = draft.copy(endpoint = config.endpoint)
                suspend fun testWith(
                    targetConfig: AiProviderConfig,
                    credential: AiRuntimeCredential,
                ) {
                    if (pipeline.requiresWebResearch()) {
                        providerFor(targetConfig, credential).researchNutrition(
                            providerConnectionTestIntent(),
                        )
                    } else {
                        val content = container.openAiClient.completeJson(
                            config = targetConfig,
                            credential = credential,
                            systemPrompt = "Return one JSON object and nothing else.",
                            userPrompt = "Reply with {\"ok\":true} to confirm this connection.",
                        )
                        require(content.isNotBlank()) { "The provider returned an empty response." }
                    }
                }
                suspend fun <T> withDraftOrStoredCredential(
                    input: String,
                    storedId: String,
                    missingMessage: String,
                    block: suspend (AiRuntimeCredential) -> T,
                ): T {
                    val entered = input.normalizedApiKeyCharsOrNull()
                    if (entered != null) {
                        return try {
                            block(AiRuntimeCredential.from(entered.concatToString()))
                        } finally {
                            entered.fill('\u0000')
                        }
                    }
                    return container.secretStore.useSecret(storedId) { chars ->
                        block(AiRuntimeCredential.from(chars.concatToString()))
                    } ?: error(missingMessage)
                }

                if (config.kind == AiProviderKind.EXA_GEMINI) {
                    withDraftOrStoredCredential(
                        input = state.apiKeyInput,
                        storedId = secretId(selection),
                        missingMessage = "Enter a Google Gemini API key before testing this provider.",
                    ) { geminiCredential ->
                        withDraftOrStoredCredential(
                            input = state.searchApiKeyInput,
                            storedId = exaSecretId(),
                            missingMessage = "Enter an Exa API key before testing this provider.",
                        ) { exaCredential ->
                            exaGeminiProvider(config, geminiCredential, exaCredential)
                                .researchNutrition(providerConnectionTestIntent())
                        }
                    }
                    return@runCatching "Connection successful"
                }

                val enteredKey = state.apiKeyInput.normalizedApiKeyCharsOrNull()
                if (enteredKey != null) {
                    try {
                        testWith(config, AiRuntimeCredential.from(enteredKey.concatToString()))
                    } finally {
                        enteredKey.fill('\u0000')
                    }
                } else if (pipeline == ProviderPipeline.SMART_FALLBACK) {
                    withSmartFallbackCredential(
                        prefs = loadedPreferences(),
                        selection = selection,
                        block = ::testWith,
                    )
                } else {
                    container.secretStore.useSecret(secretId(selection)) { chars ->
                        testWith(config, AiRuntimeCredential.from(chars.concatToString()))
                    } ?: error("Enter an API key before testing this provider.")
                }
                "Connection successful"
            }.getOrElse(Throwable::safeProviderConnectionMessage)
            onResult(result)
        }
    }

    fun refreshProviderAndHealthStatus() {
        viewModelScope.launch {
            val prefs = loadedPreferences()
            keyPresence.value = ProviderPipeline.entries.associateWith { pipeline ->
                val selection = prefs.selectionFor(pipeline)
                val primary = runCatching {
                    container.secretStore.contains(secretId(selection))
                }.getOrDefault(false)
                val search = if (selection.providerId.equals("exa-gemini", true)) {
                    runCatching { container.secretStore.contains(exaSecretId()) }.getOrDefault(false)
                } else true
                ProviderKeyPresence(primary = primary, search = search)
            }
        }
        viewModelScope.launch { refreshHealthConnectAndSync() }
    }

    fun syncHealthConnect() {
        viewModelScope.launch { refreshHealthConnectAndSync(userInitiated = true) }
    }

    private suspend fun refreshHealthConnectAndSync(userInitiated: Boolean = false) {
        if (!healthSyncMutex.tryLock()) return
        try {
            val healthConnect = container.healthConnect
            val availability = healthConnect.availability
            val requiredPermissions = healthConnect.permissionsFor(NomiHealthFeatures)
            val grantedPermissions = runCatching { healthConnect.grantedPermissions() }
                .getOrElse { error ->
                    if (error is CancellationException) throw error
                    emptySet()
                }
            val status = resolveHealthConnectPermissionStatus(
                availability = availability,
                requiredPermissions = requiredPermissions,
                grantedPermissions = grantedPermissions,
            )
            if (status != HealthConnectPermissionStatus.CONNECTED) {
                healthConnectUiState.value = HealthConnectUiState(
                    status = status,
                    message = when (status) {
                        HealthConnectPermissionStatus.UPDATE_REQUIRED ->
                            "Update Health Connect to enable syncing."
                        HealthConnectPermissionStatus.PARTIAL ->
                            "Grant all requested categories to finish connecting."
                        else -> null
                    },
                )
                return
            }

            val previous = healthConnectUiState.value
            healthConnectUiState.value = previous.copy(
                status = HealthConnectPermissionStatus.CONNECTED,
                isSyncing = true,
                message = if (userInitiated) "Syncing Health Connect..." else previous.message,
            )

            val now = Instant.now()
            val syncEpochMillis = now.toEpochMilli()
            val failures = mutableListOf<String>()
            var importedWeightCount = 0
            var weightsSynced = false
            var activitySynced = false
            var todaySteps: Long? = null
            var todayActiveCaloriesKcal: Double? = null

            runCatching {
                val weights = healthConnect.readWeights(
                    start = now.minus(30, ChronoUnit.DAYS),
                    end = now,
                )
                val entries = importableHealthWeights(
                    weights = weights,
                    ownPackageName = healthConnect.applicationPackageName,
                ).map { weight ->
                    val measuredDate = weight.zoneOffset
                        ?.let { offset -> weight.time.atOffset(offset).toLocalDate() }
                        ?: weight.time.atZone(zoneId).toLocalDate()
                    WeightEntryEntity(
                        weightKg = weight.kilograms,
                        localDate = measuredDate.toString(),
                        measuredAtEpochMillis = weight.time.toEpochMilli(),
                        zoneId = weight.zoneOffset?.id ?: zoneId.id,
                        source = HEALTH_CONNECT_WEIGHT_SOURCE,
                        externalId = weight.id,
                        createdAtEpochMillis = syncEpochMillis,
                        updatedAtEpochMillis = syncEpochMillis,
                    )
                }
                repository.importHealthConnectWeights(entries)
            }.onSuccess { imported ->
                weightsSynced = true
                importedWeightCount = imported
            }.onFailure { error ->
                if (error is CancellationException) throw error
                failures += "weight"
            }

            runCatching {
                healthConnect.readActivity(
                    start = today.atStartOfDay(zoneId).toInstant(),
                    end = now,
                )
            }.onSuccess { activity ->
                activitySynced = true
                todaySteps = activity.steps
                todayActiveCaloriesKcal = activity.activeCaloriesKcal
            }.onFailure { error ->
                if (error is CancellationException) throw error
                failures += "activity"
            }

            healthConnectUiState.value = HealthConnectUiState(
                status = HealthConnectPermissionStatus.CONNECTED,
                isSyncing = false,
                todaySteps = if (activitySynced) todaySteps else null,
                todayActiveCaloriesKcal = if (activitySynced) todayActiveCaloriesKcal else null,
                lastSyncEpochMillis = if (weightsSynced && activitySynced) {
                    syncEpochMillis
                } else {
                    previous.lastSyncEpochMillis
                },
                importedWeightCount = importedWeightCount,
                message = when {
                    failures.isNotEmpty() -> "Some Health Connect data couldn't be synced. Try again."
                    importedWeightCount == 1 -> "Health Connect synced. Imported 1 new weight."
                    importedWeightCount > 1 -> "Health Connect synced. Imported $importedWeightCount new weights."
                    else -> "Health Connect is up to date."
                },
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            healthConnectUiState.value = healthConnectUiState.value.copy(
                isSyncing = false,
                message = "Health Connect couldn't be synced. Try again.",
            )
        } finally {
            healthSyncMutex.unlock()
        }
    }

    private fun cancelAnalysis() {
        analysisRequestId += 1
        analysisJob?.cancel()
        analysisJob = null
    }

    /**
     * Repairs a mistyped food against the ones already in the log.
     *
     * It runs before the cache lookup on purpose: a corrected spelling can hit the local
     * catalog exactly, so a typo in something eaten every week costs no provider request at
     * all. The correction is strict about variants - it will fix "junebrry" but never trade one
     * edition for another - and it leaves anything it is not sure about untouched for research.
     */
    private fun ParsedFoodIntent.withKnownSpellings(): ParsedFoodIntent {
        val known = recentFoodsSnapshot.map(FoodEntity::canonicalName)
        if (known.isEmpty()) return this
        return copy(
            items = items.map { item ->
                val corrected = FoodNameCorrection.correctedOrNull(item.name, known)
                if (corrected == null) item else item.copy(name = corrected)
            },
        )
    }

    /** Reuses an exact local catalog match before paying for another provider request. */
    private suspend fun cachedNutritionAnalysis(intent: ParsedFoodIntent): FoodAnalysis? {
        val reconciled = UserQuantityResolver.reconcileIntent(intent, Locale.getDefault().country)
        if (reconciled.items.isEmpty()) return null

        val analyzed = mutableListOf<AnalyzedFoodItem>()
        for (requested in reconciled.items) {
            val quantity = requested.quantity ?: return null
            val unit = requested.unit?.takeIf(String::isNotBlank) ?: return null
            val normalizedName = requested.name.trim()
                .lowercase(Locale.ROOT)
                .replace(Regex("\\s+"), " ")
            val normalizedBrand = requested.brand?.trim()?.lowercase(Locale.ROOT)
            val food = recentFoodsSnapshot.firstOrNull {
                it.normalizedName == normalizedName &&
                    it.brand?.trim()?.lowercase(Locale.ROOT) == normalizedBrand
            } ?: repository.foodByIdentity(normalizedName, normalizedBrand) ?: return null
            if (!food.isTrustedForNutritionReuse()) return null
            val scaled = runCatching {
                ServingNutritionNormalizer.normalizeSourceServingTo(
                    sourceServingItem = food.toAnalyzedItem("Nomi local food cache"),
                    loggedQuantity = quantity,
                    loggedUnit = unit,
                    loggedGramsEquivalent = requested.gramsEquivalent,
                ).copy(quantityResolution = requested.quantityResolution)
            }.getOrNull() ?: return null
            analyzed += scaled
        }
        return FoodAnalysis(items = analyzed, overallConfidence = 1.0)
    }
    private suspend fun researchNutrition(intent: ParsedFoodIntent): FoodAnalysis =
        runWithSmartFallback(
            primary = {
                withConfiguredResearchProvider { provider ->
                    provider.researchNutrition(intent)
                }
            },
            fallback = {
                withConfiguredSmartFallback { config, key ->
                    providerFor(config, key).researchNutrition(intent)
                }
            },
            onFallback = { error ->
                recordResearchFallback(status = "FALLBACK_STARTED", error = error)
            },
            onFallbackSuccess = { analysis ->
                recordResearchFallback(status = "FALLBACK_VALIDATED", analysis = analysis)
            },
        ).withCleanDisplayNames()

    /**
     * Every researched item passes through here on its way to the page, so the name that is
     * previewed is the same one that is saved and later reopened for rewriting. The prompts
     * ask the model for a clean short name; this only removes what a provider left behind.
     */
    private fun FoodAnalysis.withCleanDisplayNames(): FoodAnalysis =
        copy(items = items.map { it.copy(name = FoodDisplayName.clean(it.name)) })

    private fun currentResearchProviderWebsite(): String? {
        val selection = preferences.value.foodResearchProvider
        return when (selection.providerId.lowercase(Locale.ROOT)) {
            "perplexity" -> "https://www.perplexity.ai"
            "openrouter" -> "https://openrouter.ai"
            "openai" -> "https://openai.com"
            "exa-gemini" -> "https://exa.ai"
            "codex-easy" -> "https://codex-easy.ai"
            else -> selection.endpoint
        }
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

    private suspend fun loadedPreferences(): AppPreferences = repository.preferences.first()

    private suspend fun <T> withConfiguredProvider(
        pipeline: ProviderPipeline,
        block: suspend (AiProviderConfig, AiRuntimeCredential) -> T,
    ): T {
        val prefs = loadedPreferences()
        val selection = prefs.selectionFor(pipeline)
        require(selection.providerId.isNotBlank()) { "Configure this AI provider in Settings first." }
        return container.secretStore.useSecret(secretId(selection)) { chars ->
            val credential = AiRuntimeCredential.from(chars.concatToString())
            block(selection.toRuntimeConfig(prefs.aiRequestTimeoutDisabled), credential)
        } ?: error("Add the ${selection.providerId.displayProviderName()} API key in Settings first.")
    }

    private suspend fun <T> withConfiguredResearchProvider(
        block: suspend (NutritionResearchProvider) -> T,
    ): T {
        val prefs = loadedPreferences()
        val selection = prefs.foodResearchProvider
        if (!selection.providerId.equals("exa-gemini", true)) {
            return withConfiguredProvider(ProviderPipeline.FOOD_RESEARCH) { config, key ->
                block(providerFor(config, key))
            }
        }
        val config = selection.toRuntimeConfig(prefs.aiRequestTimeoutDisabled)
        return container.secretStore.useSecret(secretId(selection)) { geminiChars ->
            val geminiCredential = AiRuntimeCredential.from(geminiChars.concatToString())
            container.secretStore.useSecret(exaSecretId()) { exaChars ->
                val exaCredential = AiRuntimeCredential.from(exaChars.concatToString())
                block(exaGeminiProvider(config, geminiCredential, exaCredential))
            } ?: error("Add the Exa API key in Settings first.")
        } ?: error("Add the Google Gemini API key in Settings first.")
    }
    private suspend fun <T : Any> withConfiguredSmartFallback(
        block: suspend (AiProviderConfig, AiRuntimeCredential) -> T,
    ): T {
        val prefs = loadedPreferences()
        return withSmartFallbackCredential(prefs, prefs.smartFallbackProvider, block)
    }

    private suspend fun <T : Any> withSmartFallbackCredential(
        prefs: AppPreferences,
        selection: ProviderSelection,
        block: suspend (AiProviderConfig, AiRuntimeCredential) -> T,
    ): T {
        require(selection.providerId.isNotBlank()) {
            "Configure Fallback in Settings first."
        }
        val config = selection.toRuntimeConfig(prefs.aiRequestTimeoutDisabled)
        suspend fun use(secret: String): T? = container.secretStore.useSecret(secret) { chars ->
            block(config, AiRuntimeCredential.from(chars.concatToString()))
        }
        smartFallbackCredentialIds(selection, prefs.foodResearchProvider).forEach { secret ->
            use(secret)?.let { return it }
        }
        error(
            "Configure Fallback in Settings with an API key, or select the same provider " +
                "as Food research to reuse its key.",
        )
    }

    private fun exaGeminiProvider(
        config: AiProviderConfig,
        geminiCredential: AiRuntimeCredential,
        exaCredential: AiRuntimeCredential,
    ) = ExaGeminiNutritionProvider(
        exaSearch = container.exaGeminiClient,
        geminiExtractor = container.exaGeminiClient,
        exaCredential = { exaCredential },
        geminiConfig = config,
        geminiCredential = { geminiCredential },
        searchProgressSink = ::showResearchSources,
        debugSink = ::recordExaGeminiTrace,
    )

    private fun showResearchSources(sourceUrls: List<String>) {
        val current = mutableLoggingState.value
        if (current !is FoodLoggingUiState.Processing ||
            current.stage != AiProcessingStage.FINDING_NUTRITION
        ) return
        mutableLoggingState.value = current.copy(
            sourceUrls = sourceUrls.distinct().take(3),
        )
    }

    private suspend fun recordExaGeminiTrace(trace: ExaGeminiDebugTrace) {
        if (!preferences.value.aiDebugEnabled) return
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
                    failureCategory = trace.failureReason?.let { "EXA_GEMINI_REJECTED" },
                    safeMessage = trace.failureReason,
                    createdAtEpochMillis = System.currentTimeMillis(),
                ),
            )
        }
    }
    private suspend fun recordResearchFallback(
        status: String,
        error: Throwable? = null,
        analysis: FoodAnalysis? = null,
    ) {
        if (!preferences.value.aiDebugEnabled) return
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
                    failureCategory = error?.javaClass?.simpleName,
                    safeMessage = error?.safeProviderFailureMessage()
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
    private fun providerFor(config: AiProviderConfig, credential: AiRuntimeCredential) =
        OpenAiCompatibleProviders(
            client = container.openAiClient,
            parsingConfig = config,
            parsingCredential = { credential },
            nutritionConfig = config,
            nutritionCredential = { credential },
            portionConfig = config,
            portionCredential = { credential },
            visionConfig = config,
            visionCredential = { credential },
            calorieBiasProvider = { preferences.value.calorieEstimateBias },
        )

    private fun mapToday(
        date: LocalDate,
        logs: List<FoodLogEntity>,
        plan: NutritionPlanEntity?,
        micronutrients: MicronutrientPreferences,
        goalsCardStyle: GoalsCardStyle,
    ): TodayUiState {
        val totals = logs.fold(NutritionValues()) { total, log -> total + log.nutritionSnapshot }
        return TodayUiState(
            date = date,
            caloriesConsumed = totals.caloriesKcal,
            calorieTarget = plan?.calorieTargetKcal ?: 2_000.0,
            protein = MacroProgress(totals.proteinGrams, plan?.proteinTargetGrams ?: 130.0),
            carbohydrates = MacroProgress(totals.carbohydrateGrams, plan?.carbohydrateTargetGrams ?: 240.0),
            fat = MacroProgress(totals.fatGrams, plan?.fatTargetGrams ?: 65.0),
            micronutrients = micronutrients.toProgress(logs, totals),
            entries = logs.map { it.toTodayEntry() },
            goalsCardStyle = goalsCardStyle,
        )
    }

    /**
     * Builds the day's micronutrient rows for the nutrients the user chose to track.
     *
     * A row is marked partial when only some of the day's foods reported the nutrient, because
     * a total assembled from half the plate is a floor rather than an answer, and the card says
     * so instead of presenting it as complete.
     */
    private fun MicronutrientPreferences.toProgress(
        logs: List<FoodLogEntity>,
        totals: NutritionValues,
    ): List<MicronutrientProgress> = enabledMicronutrients().map { nutrient ->
        val reportingLogs = logs.count { nutrient.amountIn(it.nutritionSnapshot) != null }
        MicronutrientProgress(
            nutrient = nutrient,
            consumed = nutrient.amountIn(totals),
            target = settingFor(nutrient).resolvedTarget(nutrient),
            isPartial = reportingLogs in 1 until logs.size,
        )
    }

    /** Reads one optional nutrient out of a stored snapshot, keeping "not reported" as null. */
    private fun Micronutrient.amountIn(values: NutritionValues): Double? = when (this) {
        Micronutrient.FIBER -> values.fiberGrams
        Micronutrient.SUGAR -> values.sugarGrams
        Micronutrient.SATURATED_FAT -> values.saturatedFatGrams
        Micronutrient.SODIUM -> values.sodiumMilligrams
    }

    private fun mapHistory(
        logs: List<FoodLogEntity>,
        query: String,
        selected: LocalDate,
        plan: NutritionPlanEntity?,
    ): HistoryUiState {
        val filtered = query.trim().lowercase(Locale.ROOT).let { normalized ->
            if (normalized.isBlank()) logs else logs.filter {
                it.displayNameSnapshot.lowercase(Locale.ROOT).contains(normalized) ||
                    it.brandSnapshot?.lowercase(Locale.ROOT)?.contains(normalized) == true
            }
        }
        val days = filtered.groupBy { LocalDate.parse(it.localDate) }
            .toSortedMap(compareByDescending { it })
            .map { (date, entries) ->
                val nutrition = entries.fold(NutritionValues()) { total, log -> total + log.nutritionSnapshot }
                HistoryDay(
                    date = date,
                    calories = nutrition.caloriesKcal,
                    calorieTarget = plan?.calorieTargetKcal ?: 2_000.0,
                    proteinGrams = nutrition.proteinGrams,
                    carbohydrateGrams = nutrition.carbohydrateGrams,
                    fatGrams = nutrition.fatGrams,
                    entries = entries.map { it.toTodayEntry() },
                )
            }
        return HistoryUiState(query, selected, days, isSearching = false)
    }

    private fun mapSettings(
        prefs: AppPreferences,
        plan: NutritionPlanEntity?,
        keys: Map<ProviderPipeline, ProviderKeyPresence>,
        health: HealthConnectUiState,
    ): SettingsUiState {
        val providers = ProviderPipeline.entries.map { pipeline ->
            val selected = prefs.selectionFor(pipeline)
            AiProviderSetting(
                purpose = pipeline.displayName(),
                provider = selected.providerId.toProviderKind(),
                model = selected.model,
                endpoint = runCatching { selected.toRuntimeConfig().endpoint }
                    .getOrElse { selected.endpoint.orEmpty() },
                hasApiKey = keys[pipeline]?.complete == true,
                hasPrimaryApiKey = keys[pipeline]?.primary == true,
                hasSearchApiKey = keys[pipeline]?.search == true &&
                    selected.providerId.equals("exa-gemini", true),
            )
        }
        val reminders = prefs.reminders
        return SettingsUiState(
            themeMode = prefs.theme.toThemeMode(),
            dynamicColor = prefs.dynamicColorEnabled,
            germanTranslationEnabled = prefs.germanTranslationEnabled,
            unitSystem = if (prefs.weightUnit == WeightUnitPreference.KILOGRAMS) UnitSystem.METRIC else UnitSystem.IMPERIAL,
            activityTargetAdjustment = prefs.adjustTargetFromActivity,
            calorieEstimateBias = prefs.calorieEstimateBias,
            goalsCardStyle = prefs.goalsCardStyle,
            healthConnectAvailable = health.status != HealthConnectPermissionStatus.UNAVAILABLE,
            healthConnectEnabled = health.status == HealthConnectPermissionStatus.CONNECTED,
            healthConnect = health,
            nutritionTargets = NutritionTargetSetting(
                calories = plan?.calorieTargetKcal?.roundToInt() ?: 2_000,
                proteinGrams = plan?.proteinTargetGrams?.roundToInt() ?: 130,
                carbohydrateGrams = plan?.carbohydrateTargetGrams?.roundToInt() ?: 240,
                fatGrams = plan?.fatTargetGrams?.roundToInt() ?: 65,
                isCustom = plan?.let {
                    it.calorieTargetCustom || it.proteinTargetCustom ||
                        it.carbohydrateTargetCustom || it.fatTargetCustom
                } ?: false,
            ),
            trackedMicronutrients = prefs.micronutrients.enabledMicronutrients(),
            aiProviders = providers,
            aiRequestTimeoutDisabled = prefs.aiRequestTimeoutDisabled,
            reminders = listOf(
                com.nomi.app.ui.settings.ReminderSetting("Breakfast", reminders.breakfast.enabled, reminders.breakfast.localTime),
                com.nomi.app.ui.settings.ReminderSetting("Lunch", reminders.lunch.enabled, reminders.lunch.localTime),
                com.nomi.app.ui.settings.ReminderSetting("Dinner", reminders.dinner.enabled, reminders.dinner.localTime),
                com.nomi.app.ui.settings.ReminderSetting("Daily summary", reminders.dailySummary.enabled, reminders.dailySummary.localTime),
                com.nomi.app.ui.settings.ReminderSetting("Weight", reminders.weight.enabled, reminders.weight.localTime),
            ),
            appVersion = BuildConfig.VERSION_NAME,
        )
    }

    private fun FoodLogEntity.toTodayEntry(): TodayFoodEntry = TodayFoodEntry(
        id = id,
        name = displayNameSnapshot,
        brand = brandSnapshot,
        amountText = "${amount.cleanNumber()} $unit",
        calories = nutritionSnapshot.caloriesKcal,
        proteinGrams = nutritionSnapshot.proteinGrams,
        carbohydrateGrams = nutritionSnapshot.carbohydrateGrams,
        fatGrams = nutritionSnapshot.fatGrams,
        mealCategory = mealCategory.toMealCategory(),
        time = Instant.ofEpochMilli(loggedAtEpochMillis).atZone(runCatching { ZoneId.of(zoneId) }.getOrDefault(this@AppViewModel.zoneId)).toLocalTime(),
        isEstimated = isEstimated,
        foodId = foodId,
        amount = amount,
        unit = unit,
        grams = grams,
        sourceName = sourceSnapshot.displayName,
        sourceUrl = sourceSnapshot.url,
    )

    private fun AnalyzedFoodItem.toLog(category: MealCategory, inputMethod: String): FoodLogEntity {
        val now = System.currentTimeMillis()
        val enteredServingUnit = quantityResolution?.enteredUnit
            ?.takeIf { it.isSpoonLoggingUnit() || it.isHouseholdCountLoggingUnit() }
        val enteredServingQuantity = quantityResolution?.enteredQuantity
            ?.takeIf { enteredServingUnit != null && it.isFinite() && it > 0.0 }
        return FoodLogEntity(
            mealCategory = category.name,
            displayNameSnapshot = name.trim(),
            brandSnapshot = brand,
            amount = enteredServingQuantity ?: quantity,
            unit = enteredServingUnit?.takeIf { enteredServingQuantity != null } ?: unit,
            grams = gramsEquivalent,
            nutritionSnapshot = NutritionValues(
                caloriesKcal = calories,
                proteinGrams = proteinGrams,
                carbohydrateGrams = carbohydrateGrams,
                fatGrams = fatGrams,
                fiberGrams = fiberGrams,
                sugarGrams = sugarGrams,
                saturatedFatGrams = saturatedFatGrams,
                sodiumMilligrams = sodiumMilligrams,
            ),
            sourceSnapshot = NutritionSourceSnapshot(
                kind = if (isEstimate) "ai_estimate" else "database",
                providerName = sourceName,
                displayName = sourceName,
                url = sourceUrl,
                retrievedAtEpochMillis = now,
            ),
            isEstimated = isEstimate,
            inputMethod = inputMethod,
            localDate = selectedDate.value.toString(),
            loggedAtEpochMillis = now,
            zoneId = zoneId.id,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )
    }

    private fun ManualFoodDraft.toLog(): FoodLogEntity = AnalyzedFoodItem(
        name = name.trim(),
        quantity = requireNotNull(amount.toDoubleOrNull()),
        unit = unit.trim(),
        calories = requireNotNull(calories.toDoubleOrNull()),
        proteinGrams = requireNotNull(protein.toDoubleOrNull()),
        carbohydrateGrams = requireNotNull(carbohydrates.toDoubleOrNull()),
        fatGrams = requireNotNull(fat.toDoubleOrNull()),
        isEstimate = false,
        sourceName = "Manual entry",
    ).toLog(mealCategory, "manual")

    private fun FoodEntity.toLog(): FoodLogEntity = AnalyzedFoodItem(
        name = canonicalName,
        brand = brand,
        quantity = 100.0,
        unit = "g",
        gramsEquivalent = 100.0,
        calories = nutritionPer100g.caloriesKcal,
        proteinGrams = nutritionPer100g.proteinGrams,
        carbohydrateGrams = nutritionPer100g.carbohydrateGrams,
        fatGrams = nutritionPer100g.fatGrams,
        fiberGrams = nutritionPer100g.fiberGrams,
        sugarGrams = nutritionPer100g.sugarGrams,
        saturatedFatGrams = nutritionPer100g.saturatedFatGrams,
        sodiumMilligrams = nutritionPer100g.sodiumMilligrams,
        sourceName ="Nomi food library",
        isEstimate = isEstimated,
    ).toLog(defaultMealCategory(), "recent").copy(foodId = id)

    private fun FavoriteFoodWithCatalog.toLog(): FoodLogEntity {
        val grams = favorite.typicalGrams ?: favorite.typicalAmount.takeIf { favorite.typicalUnit.equals("g", true) } ?: 100.0
        val factor = grams / 100.0
        val values = food.nutritionPer100g
        return AnalyzedFoodItem(
            name = food.canonicalName,
            brand = food.brand,
            quantity = favorite.typicalAmount,
            unit = favorite.typicalUnit,
            gramsEquivalent = grams,
            calories = values.caloriesKcal * factor,
            proteinGrams = values.proteinGrams * factor,
            carbohydrateGrams = values.carbohydrateGrams * factor,
            fatGrams = values.fatGrams * factor,
            fiberGrams = values.fiberGrams?.times(factor),
            sugarGrams = values.sugarGrams?.times(factor),
            saturatedFatGrams = values.saturatedFatGrams?.times(factor),
            sodiumMilligrams = values.sodiumMilligrams?.times(factor),
            sourceName = "Nomi favorite",
            isEstimate = food.isEstimated,
        ).toLog(defaultMealCategory(), "favorite").copy(foodId = food.id)
    }

    private fun FoodEntity.toLibraryItem(kind: LibraryItemKind, amountText: String = "100 g") = LibraryItem(
        id = id,
        kind = kind,
        title = canonicalName,
        subtitle = listOfNotNull(brand, amountText).joinToString(" · "),
        calories = nutritionPer100g.caloriesKcal,
    )

    private fun BarcodeProduct.toAnalyzedItemOrNull(): AnalyzedFoodItem? {
        val calories = caloriesPer100g?.takeIf { it.isFinite() && it in 0.0..1_500.0 } ?: return null
        val protein = proteinPer100g?.takeIf { it.isFinite() && it in 0.0..100.0 } ?: return null
        val carbohydrates = carbohydratesPer100g?.takeIf { it.isFinite() && it in 0.0..100.0 } ?: return null
        val fat = fatPer100g?.takeIf { it.isFinite() && it in 0.0..100.0 } ?: return null
        val basisUnit = nutritionBasisUnit.takeIf { it == "ml" } ?: "g"
        return SourceIntegrityVerifier.resolveItem(
            AnalyzedFoodItem(
                name = name.take(300),
                brand = brand?.take(200),
                quantity = 100.0,
                unit = basisUnit,
                gramsEquivalent = 100.0.takeIf { basisUnit == "g" },
                calories = calories,
                proteinGrams = protein,
                carbohydrateGrams = carbohydrates,
                fatGrams = fat,
                fiberGrams = fiberPer100g?.takeIf { it.isFinite() && it in 0.0..100.0 },
                sugarGrams = sugarPer100g?.takeIf { it.isFinite() && it in 0.0..100.0 },
                saturatedFatGrams = saturatedFatPer100g?.takeIf { it.isFinite() && it in 0.0..100.0 },
                // 100 g of pure salt carries 40,000 mg of sodium, so that bounds a per-100 value.
                sodiumMilligrams = sodiumMilligramsPer100g
                    ?.takeIf { it.isFinite() && it in 0.0..40_000.0 },
                sourceName = sourceName,
                sourceUrl = sourceUrl,
                sourceProductName = name.take(300),
                sourceServingQuantity = 100.0,
                sourceServingUnit = basisUnit,
                sourceServingGramsEquivalent = 100.0.takeIf { basisUnit == "g" },
                isEstimate = false,
            ),
        )
    }

    private fun FoodEntity.toAnalyzedItem(source: String) = AnalyzedFoodItem(
        name = canonicalName,
        brand = brand,
        quantity = 100.0,
        unit = "g",
        gramsEquivalent = 100.0,
        calories = nutritionPer100g.caloriesKcal,
        proteinGrams = nutritionPer100g.proteinGrams,
        carbohydrateGrams = nutritionPer100g.carbohydrateGrams,
        fatGrams = nutritionPer100g.fatGrams,
        fiberGrams = nutritionPer100g.fiberGrams,
        sugarGrams = nutritionPer100g.sugarGrams,
        saturatedFatGrams = nutritionPer100g.saturatedFatGrams,
        sodiumMilligrams = nutritionPer100g.sodiumMilligrams,
        sourceName =source,
        sourceServingQuantity = 100.0,
        sourceServingUnit = "g",
        sourceServingGramsEquivalent = 100.0,
        isEstimate = isEstimated,
    )

    private fun AnalyzedFoodItem.asBarcodeSourceServing(): AnalyzedFoodItem = copy(
        sourceServingQuantity = quantity,
        sourceServingUnit = unit,
        sourceServingGramsEquivalent = gramsEquivalent,
        servingValidation = null,
        requiresServingValidation = false,
    )

    private suspend fun cacheLogFood(log: FoodLogEntity): Long? = cacheAnalyzedFood(
        AnalyzedFoodItem(
            name = log.displayNameSnapshot,
            brand = log.brandSnapshot,
            quantity = log.amount,
            unit = log.unit,
            gramsEquivalent = log.grams ?: log.amount.takeIf { log.unit.equals("g", true) },
            calories = log.nutritionSnapshot.caloriesKcal,
            proteinGrams = log.nutritionSnapshot.proteinGrams,
            carbohydrateGrams = log.nutritionSnapshot.carbohydrateGrams,
            fatGrams = log.nutritionSnapshot.fatGrams,
            fiberGrams = log.nutritionSnapshot.fiberGrams,
            sugarGrams = log.nutritionSnapshot.sugarGrams,
            saturatedFatGrams = log.nutritionSnapshot.saturatedFatGrams,
            sodiumMilligrams = log.nutritionSnapshot.sodiumMilligrams,
            sourceName = log.sourceSnapshot.displayName,
            sourceUrl = log.sourceSnapshot.url,
            isEstimate = log.isEstimated,
        ),
    )

    private suspend fun cacheAnalyzedFood(item: AnalyzedFoodItem, barcode: String? = null): Long? {
        val grams = item.gramsEquivalent ?: item.quantity.takeIf { item.unit.equals("g", true) } ?: return null
        if (!grams.isFinite() || grams <= 0.0) return null
        val normalized = item.name.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
        val normalizedBrand = item.brand?.trim()?.lowercase(Locale.ROOT)
        val existing = barcode?.let { repository.foodByBarcode(it) }
            ?: recentFoodsSnapshot.firstOrNull {
                it.normalizedName == normalized &&
                    it.brand?.trim()?.lowercase(Locale.ROOT) == normalizedBrand
            }
            ?: repository.foodByIdentity(normalized, normalizedBrand)
        if (existing != null) return existing.id
        val factor = 100.0 / grams
        val now = System.currentTimeMillis()
        return repository.addFood(
            FoodEntity(
                canonicalName = item.name.trim().take(300),
                normalizedName = normalized.take(300),
                brand = item.brand?.trim()?.take(200),
                barcode = barcode,
                nutritionPer100g = NutritionValues(
                    caloriesKcal = item.calories * factor,
                    proteinGrams = item.proteinGrams * factor,
                    carbohydrateGrams = item.carbohydrateGrams * factor,
                    fatGrams = item.fatGrams * factor,
                    fiberGrams = item.fiberGrams?.times(factor),
                    sugarGrams = item.sugarGrams?.times(factor),
                    saturatedFatGrams = item.saturatedFatGrams?.times(factor),
                    sodiumMilligrams = item.sodiumMilligrams?.times(factor),
                ),
                isUserCreated = item.sourceName == "Manual entry",
                isEstimated = item.isEstimate,
                lastVerifiedAtEpochMillis = now.takeUnless { item.isEstimate },
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            ),
        )
    }

    /**
     * The composable [nomiString] needs a composition, but a few strings are written into saved
     * data from here. They follow the same switch so a German log does not sprout English rows.
     */
    private fun inUserLanguage(english: String, german: String): String =
        if (preferences.value.germanTranslationEnabled) german else english

    /** Research validation is actionable to developers, but raw source-ID language is not UI. */
    private fun researchFailureMessage(error: Throwable): String {
        val technicalEvidenceFailure = error is AiValidationException && listOf(
            "Exa source",
            "nutrition evidence",
            "support Gemini",
            "not compatible",
        ).any { marker -> error.message?.contains(marker, ignoreCase = true) == true }
        if (!technicalEvidenceFailure) return error.safeAiMessage()
        return inUserLanguage(
            english = "Nomi couldn't verify nutrition for every product. Try again or edit the entry.",
            german = "Nomi konnte nicht für alle Produkte passende Nährwerte belegen. " +
                "Versuche es erneut oder bearbeite die Eingabe.",
        )
    }

    private fun defaultMealCategory(): MealCategory = when (LocalTime.now(zoneId).hour) {
        in 4..10 -> MealCategory.BREAKFAST
        in 11..15 -> MealCategory.LUNCH
        in 16..21 -> MealCategory.DINNER
        else -> MealCategory.SNACKS
    }

    private fun ProgressRange.dayCount(): Int = when (this) {
        ProgressRange.SEVEN_DAYS -> 7
        ProgressRange.THIRTY_DAYS -> 30
        ProgressRange.THREE_MONTHS -> 90
        ProgressRange.SIX_MONTHS -> 180
        ProgressRange.ONE_YEAR -> 365
        ProgressRange.ALL -> 3_650
    }
}

/** Room for a described plate without room for a pasted document. */
private const val MAX_PHOTO_DESCRIPTION_CHARS = 1_000
private const val MAX_PHOTO_PLACE_CHARS = 120
private const val MAX_MENU_LOGGING_TEXT_CHARS = 1_500

private operator fun NutritionValues.plus(other: NutritionValues) = NutritionValues(
    caloriesKcal = caloriesKcal + other.caloriesKcal,
    proteinGrams = proteinGrams + other.proteinGrams,
    carbohydrateGrams = carbohydrateGrams + other.carbohydrateGrams,
    fatGrams = fatGrams + other.fatGrams,
    fiberGrams = fiberGrams.plusOptional(other.fiberGrams),
    sugarGrams = sugarGrams.plusOptional(other.sugarGrams),
    saturatedFatGrams = saturatedFatGrams.plusOptional(other.saturatedFatGrams),
    sodiumMilligrams = sodiumMilligrams.plusOptional(other.sodiumMilligrams),
)

/**
 * Sums what is known and stays null while nothing is. A day whose foods never reported sugar
 * has no sugar total, which is a different statement from a day that genuinely contained none -
 * and the difference is what stops Today from showing a confident 0 g it cannot support.
 */
private fun Double?.plusOptional(other: Double?): Double? =
    if (this == null && other == null) null else (this ?: 0.0) + (other ?: 0.0)

private fun String.toMealCategory(): MealCategory = runCatching {
    MealCategory.valueOf(trim().uppercase(Locale.ROOT))
}.getOrDefault(MealCategory.SNACKS)

private fun Double.cleanNumber(): String = if (this == toLong().toDouble()) toLong().toString()
else String.format(Locale.US, "%.1f", this)

private fun AppPreferences.selectionFor(pipeline: ProviderPipeline): ProviderSelection = when (pipeline) {
    ProviderPipeline.FOOD_RESEARCH -> foodResearchProvider
    ProviderPipeline.FOOD_INTERPRETATION -> foodInterpretationProvider
    ProviderPipeline.PORTION_CHANGE -> portionChangeProvider
    ProviderPipeline.VISION -> visionProvider
    ProviderPipeline.SMART_FALLBACK -> smartFallbackProvider
}

private fun AiProviderEditorState.toProviderSelection(
    pipeline: ProviderPipeline = ProviderPipeline.FOOD_INTERPRETATION,
): ProviderSelection = ProviderSelection(
    providerId = provider.toProviderId(),
    model = model.trim(),
    endpoint = endpoint.asHttpsEndpoint(),
).withSupportedModel(pipeline)


private fun String.asHttpsEndpoint(): String = trim().let { endpoint ->
    if ("://" in endpoint) endpoint else "https://$endpoint"
}
private fun ProviderSelection.resolvedEndpoint(): String {
    val resolved = when (providerId.toProviderKind()) {
        AiProviderKind.PERPLEXITY -> "https://api.perplexity.ai"
        AiProviderKind.OPEN_ROUTER -> "https://openrouter.ai/api/v1"
        AiProviderKind.OPEN_AI -> "https://api.openai.com/v1"
        AiProviderKind.EXA_GEMINI -> GEMINI_API_ENDPOINT
        // Codex Easy publishes both a bare host and a /v1 base; Nomi appends OpenAI request
        // paths, so the versioned base is the one that resolves to /v1/chat/completions.
        AiProviderKind.CODEX_EASY -> "https://codex-easy.ai/v1"
        AiProviderKind.CUSTOM_OPEN_AI_COMPATIBLE -> endpoint?.trim()?.takeIf(String::isNotBlank)
            ?: error("Enter a provider endpoint in Settings.")
    }.trimEnd('/')
    val uri = runCatching { URI(resolved) }.getOrNull()
    require(uri?.scheme.equals("https", ignoreCase = true) && !uri?.host.isNullOrBlank()) {
        "AI endpoints must use a valid HTTPS URL."
    }
    return resolved
}
/**
 * [timeoutDisabled] comes from the user's "Never time out" setting: research that runs long is
 * then waited out instead of being cut off.
 */
private fun ProviderSelection.toRuntimeConfig(timeoutDisabled: Boolean = false): AiProviderConfig {
    val kind = providerId.toProviderKind()
    require(model.isNotBlank()) { "Choose a model in Settings." }
    val defaults = AiProviderConfig(kind, resolvedEndpoint(), model.trim())
    return if (timeoutDisabled) defaults.copy(timeoutMillis = null) else defaults
}

private fun ProviderSelection.cacheIdentity(): String = listOf(
    providerId.trim().lowercase(Locale.ROOT),
    model.trim(),
    runCatching { resolvedEndpoint() }.getOrElse { endpoint.orEmpty().trim() },
    advancedParametersJson.orEmpty().trim(),
).joinToString(separator = "\u001f")

private fun String.toProviderKind(): AiProviderKind = when (lowercase(Locale.ROOT)) {
    "perplexity" -> AiProviderKind.PERPLEXITY
    "openrouter" -> AiProviderKind.OPEN_ROUTER
    "openai" -> AiProviderKind.OPEN_AI
    "exa-gemini" -> AiProviderKind.EXA_GEMINI
    "codex-easy" -> AiProviderKind.CODEX_EASY
    else -> AiProviderKind.CUSTOM_OPEN_AI_COMPATIBLE
}
private fun AiProviderKind.toProviderId(): String = when (this) {
    AiProviderKind.PERPLEXITY -> "perplexity"
    AiProviderKind.OPEN_ROUTER -> "openrouter"
    AiProviderKind.OPEN_AI -> "openai"
    AiProviderKind.EXA_GEMINI -> "exa-gemini"
    AiProviderKind.CODEX_EASY -> "codex-easy"
    AiProviderKind.CUSTOM_OPEN_AI_COMPATIBLE -> "custom"
}
private fun String.displayProviderName(): String = when (lowercase(Locale.ROOT)) {
    "perplexity" -> "Perplexity"
    "openrouter" -> "OpenRouter"
    "openai" -> "OpenAI"
    "exa-gemini" -> "Exa + Gemini"
    "codex-easy" -> "Codex Easy"
    else -> "custom provider"
}
private fun ProviderPipeline.displayName(): String = when (this) {
    ProviderPipeline.FOOD_RESEARCH -> "Food research"
    ProviderPipeline.FOOD_INTERPRETATION -> "Food interpretation"
    ProviderPipeline.PORTION_CHANGE -> "Portion changes"
    ProviderPipeline.VISION -> "Photo recognition"
    ProviderPipeline.SMART_FALLBACK -> "Fallback"
}

internal fun ProviderPipeline.requiresWebResearch(): Boolean =
    this == ProviderPipeline.FOOD_RESEARCH || this == ProviderPipeline.SMART_FALLBACK

private fun ProviderSelection.sharesCredentialWith(other: ProviderSelection): Boolean =
    providerId.equals(other.providerId, ignoreCase = true) &&
        runCatching { resolvedEndpoint() }.getOrNull()
            ?.equals(runCatching { other.resolvedEndpoint() }.getOrNull(), ignoreCase = true) == true

internal fun smartFallbackCredentialIds(
    selection: ProviderSelection,
    primary: ProviderSelection,
): List<String> = buildList {
    add(secretId(selection))
    // Only a fallback on the same provider account may reuse the research key.
    if (selection.sharesCredentialWith(primary)) add(secretId(primary))
}.distinct()

internal suspend fun <T> runWithSmartFallback(
    primary: suspend () -> T,
    fallback: suspend () -> T,
    onFallback: suspend (Throwable) -> Unit = {},
    onFallbackSuccess: suspend (T) -> Unit = {},
): T = try {
    primary()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (primaryError: Throwable) {
    onFallback(primaryError)
    try {
        fallback().also { onFallbackSuccess(it) }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (fallbackError: Throwable) {
        // The configured primary provider's error is the actionable one; a misconfigured
        // fallback must not mask it.
        primaryError.addSuppressed(fallbackError)
        throw primaryError
    }
}

/** Prefills the amount field without a trailing ".0" on whole amounts. */
internal fun formatLoggedAmountInput(amount: Double): String =
    if (amount == amount.toLong().toDouble()) amount.toLong().toString() else amount.toString()

private fun ThemePreference.toThemeMode(): ThemeMode = when (this) {
    ThemePreference.SYSTEM -> ThemeMode.SYSTEM
    ThemePreference.LIGHT -> ThemeMode.LIGHT
    ThemePreference.DARK -> ThemeMode.DARK
}

private fun ThemeMode.toPreference(): ThemePreference = when (this) {
    ThemeMode.SYSTEM -> ThemePreference.SYSTEM
    ThemeMode.LIGHT -> ThemePreference.LIGHT
    ThemeMode.DARK -> ThemePreference.DARK
}

/**
 * Keys are scoped to the provider account, not to the pipeline that happens to use it. All five
 * pipelines run on the same OpenRouter key by default, so entering it once in any of them
 * configures the rest; a second provider still gets its own separate secret.
 */
private fun secretId(selection: ProviderSelection): String = providerSecretId(
    providerId = selection.providerId,
    endpoint = selection.resolvedEndpoint(),
)

private fun exaSecretId(): String = providerSecretId("exa", EXA_API_ENDPOINT)

private fun providerSecretId(providerId: String, endpoint: String): String {
    val material = "${providerId.lowercase(Locale.ROOT)}|${endpoint.lowercase(Locale.ROOT)}"
    val digest = MessageDigest.getInstance("SHA-256").digest(material.toByteArray(Charsets.UTF_8))
    val token = digest.take(16).joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    return "provider:$token"
}

internal fun String.normalizedApiKeyCharsOrNull(): CharArray? =
    trim().takeIf(String::isNotEmpty)?.toCharArray()

private fun providerConnectionTestIntent(): ParsedFoodIntent = ParsedFoodIntent(
    originalText = "100 g apple",
    items = listOf(
        ParsedFoodItem(
            name = "apple",
            quantity = 100.0,
            unit = "g",
            gramsEquivalent = 100.0,
        ),
    ),
)

private fun String.isSpoonLoggingUnit(): Boolean = trim()
    .lowercase(Locale.ROOT)
    .replace('ö', 'o')
    .replace("oe", "o") in setOf(
    "el", "essloffel", "tbsp", "tbs", "tablespoon", "tablespoons",
    "tl", "teeloffel", "tsp", "teaspoon", "teaspoons",
    "loffel", "spoon", "spoons",
)

private fun String.isHouseholdCountLoggingUnit(): Boolean = trim()
    .lowercase(Locale.ROOT)
    .replace('\u00fc', 'u')
    .replace("ue", "u") in setOf(
    "piece", "pieces", "pc", "pcs", "stuck", "stucke",
    "kugel", "kugeln", "scoop", "scoops",
)
private fun Throwable.safeProviderSettingsMessage(): String = when {
    causeChain().any { it is SecretUnavailableException } ->
        "Nomi couldn't access secure API-key storage. Re-enter the key and try again."
    message?.contains("Configure", ignoreCase = true) == true -> message.orEmpty()
    message?.contains("endpoint", ignoreCase = true) == true ->
        "Enter a valid HTTPS API endpoint."
    message?.contains("model", ignoreCase = true) == true ->
        "Enter a model name."
    else -> "Nomi couldn't update this provider. Try again."
}

internal fun Throwable.safeAiMessage(): String {
    if (this is AiValidationException && message?.contains("not compatible", ignoreCase = true) == true) {
        return "Nomi couldn't match that source serving to your amount. Try g, ml, EL, or TL."
    }
    if (this is AiValidationException) {
        return message ?: "The serving amount could not be validated."
    }
    if (message?.contains("API key", ignoreCase = true) == true ||
        message?.contains("Configure", ignoreCase = true) == true
    ) {
        return message.orEmpty()
    }
    return safeProviderFailureMessage()
        ?: "Nomi couldn't finish that analysis. Try again or enter the food manually."
}

internal fun Throwable.safeProviderConnectionMessage(): String =
    safeProviderFailureMessage()
        ?: message?.takeIf {
            it.contains("API key", ignoreCase = true) ||
                it.contains("Configure", ignoreCase = true)
        }
        ?: "Connection failed. Check the API key, HTTPS endpoint, model, and network connection."

private fun Throwable.safeProviderFailureMessage(): String? {
    val causes = causeChain()
    if (causes.any { it is SecretUnavailableException }) {
        return "Nomi couldn't read the stored API key. Remove it in Settings and enter it again."
    }
    causes.filterIsInstance<ProviderTemporarilyUnavailableException>().firstOrNull()?.let { error ->
        return "${error.providerName} is temporarily unavailable (HTTP ${error.statusCode}) " +
            "after automatic retries. Try again shortly."
    }
    val responseError = causes.filterIsInstance<ResponseException>().firstOrNull()
    if (responseError != null) {
        val status = responseError.response.status.value
        return when (status) {
            401 -> "The provider rejected that API key. Check it in Settings."
            402 -> "The provider account is out of credit. Top it up or switch the provider in Settings."
            403 -> "The provider denied access. Check the API key and model access in Settings."
            404 -> "The provider endpoint or model variant was not found. If the model ends " +
                "in :free, OpenRouter may not currently offer a free endpoint for it. Check " +
                "the exact model ID in Settings."
            408 -> "The provider took too long. Try again."
            429 -> "The provider rate limit was reached. Wait a moment and try again."
            in 400..499 ->
                "The provider rejected the request (HTTP $status). The selected model may not " +
                    "support live web search. For OpenAI pick a search model such as " +
                    "gpt-4o-search-preview, or use Perplexity/OpenRouter for Food research."
            else -> "The provider is temporarily unavailable (HTTP $status). Try again."
        }
    }
    if (causes.any {
            it is HttpRequestTimeoutException || it is ConnectTimeoutException ||
                it is SocketTimeoutException
        }
    ) {
        return "The provider took too long. Try again."
    }
    // A base URL missing its version segment still answers 200, but with the provider's own
    // web page. That arrives as a content type Ktor cannot read as a completion, and blaming
    // the model would send someone looking in the wrong place.
    if (causes.any { it is NoTransformationFoundException } ||
        causeMessageContains("No transformation found")
    ) {
        return "That endpoint answered with a web page instead of an API response. Check the " +
            "base URL in Settings — an OpenAI-compatible endpoint usually ends in /v1."
    }
    if (causes.any { it is SerializationException } ||
        causeMessageContains("JSON", "serialize", "deserialize", "structured content")
    ) {
        return "The provider returned a response Nomi couldn't read. Check the selected model in Settings."
    }
    if (causes.any {
            it is UnknownHostException || it is ConnectException || it is IOException
        }
    ) {
        return "Nomi couldn't reach the provider. Check the internet connection and endpoint."
    }
    return when {
        causeMessageContains("401") -> "The provider rejected that API key. Check it in Settings."
        causeMessageContains("403") ->
            "The provider denied access. Check the API key and model access in Settings."
        causeMessageContains("404") ->
            "The provider endpoint or model was not found. Check Settings."
        causeMessageContains("429", "rate limit") ->
            "The provider rate limit was reached. Wait a moment and try again."
        causeMessageContains("timeout", "timed out") -> "The provider took too long. Try again."
        else -> null
    }
}

private fun Throwable.causeChain(): List<Throwable> =
    generateSequence(this) { it.cause }.take(8).toList()

private fun Throwable.causeMessageContains(vararg values: String): Boolean =
    causeChain().any { error ->
        values.any { value -> error.message?.contains(value, ignoreCase = true) == true }
    }
