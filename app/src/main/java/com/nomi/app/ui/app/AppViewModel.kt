package com.nomi.app.ui.app


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nomi.app.ai.model.AiProcessingStage
import com.nomi.app.ai.model.AiProviderKind
import com.nomi.app.ai.model.AnalyzedFoodItem
import com.nomi.app.ai.model.FoodAnalysis
import com.nomi.app.ai.model.MenuDish
import com.nomi.app.ai.model.ParsedFoodIntent
import com.nomi.app.ai.model.ParsedFoodItem
import com.nomi.app.ai.parsing.LocalFoodIntentParser
import com.nomi.app.ai.validation.ServingNutritionNormalizer
import com.nomi.app.ai.validation.UserQuantityResolver
import com.nomi.app.data.local.entity.AiDebugEventEntity
import com.nomi.app.data.local.entity.FavoriteFoodEntity
import com.nomi.app.data.local.entity.FoodEntity
import com.nomi.app.data.local.entity.FoodLogEntity
import com.nomi.app.data.local.entity.NutritionPlanEntity
import com.nomi.app.data.local.entity.UserProfileEntity
import com.nomi.app.data.local.entity.WeightEntryEntity
import com.nomi.app.data.local.model.FavoriteFoodWithCatalog
import com.nomi.app.data.local.model.SavedMealWithItems
import com.nomi.app.data.preferences.AppPreferences
import com.nomi.app.data.preferences.CalorieEstimateBias
import com.nomi.app.data.preferences.GoalsCardStyle
import com.nomi.app.data.preferences.HeightUnitPreference
import com.nomi.app.data.preferences.MicronutrientPreferences
import com.nomi.app.data.preferences.ProviderPipeline
import com.nomi.app.data.preferences.WeightUnitPreference
import com.nomi.app.data.repository.AddSavedMealToLogRequest
import com.nomi.app.data.repository.SaveLoggedMealRequest
import com.nomi.app.data.repository.duplicatedLogs
import com.nomi.app.data.repository.mapping.toCompleteOnboardingRequest
import com.nomi.app.data.repository.mapping.toEntity
import com.nomi.app.data.repository.mapping.toPersistedDraft
import com.nomi.app.data.share.NomiShareImporter
import com.nomi.app.data.share.ShareEnvelopeV1
import com.nomi.app.di.AppContainer
import com.nomi.app.domain.StepCalorieEstimate
import com.nomi.app.domain.StepCalorieEstimator
import com.nomi.app.domain.model.NutritionPlan
import com.nomi.app.domain.model.OnboardingDraft
import com.nomi.app.domain.usecase.FoodAnalysisCacheKey
import com.nomi.app.domain.usecase.FoodEditRouter
import com.nomi.app.domain.usecase.NutritionRoute
import com.nomi.app.domain.usecase.PortionEditParser
import com.nomi.app.domain.usecase.RecentFoodAnalysisCache
import com.nomi.app.domain.usecase.toPortionContext
import com.nomi.app.ui.capture.BarcodeAmountSupport
import com.nomi.app.ui.capture.BarcodeAmountUiState
import com.nomi.app.ui.capture.MenuScanUiState
import com.nomi.app.ui.capture.menuDishKey
import com.nomi.app.ui.capture.mergeMenuDishes
import com.nomi.app.ui.history.HistoryDay
import com.nomi.app.ui.history.HistorySelection
import com.nomi.app.ui.history.HistorySelectionAction
import com.nomi.app.ui.history.HistoryUiState
import com.nomi.app.ui.history.logIdsForSelection
import com.nomi.app.ui.library.LibraryItem
import com.nomi.app.ui.library.LibraryItemKind
import com.nomi.app.ui.library.LibraryUiState
import com.nomi.app.ui.localization.NomiLanguage
import com.nomi.app.ui.localization.NomiTranslations
import com.nomi.app.ui.logging.FoodLoggingUiState
import com.nomi.app.ui.logging.ManualFoodDraft
import com.nomi.app.ui.logging.PortionEditUiState
import com.nomi.app.ui.logging.toPhotoMealDescription
import com.nomi.app.ui.logging.toPhotoParsedItem
import com.nomi.app.ui.profile.ProfileEdit
import com.nomi.app.ui.progress.NutritionPoint
import com.nomi.app.ui.progress.ProgressRange
import com.nomi.app.ui.progress.ProgressUiState
import com.nomi.app.ui.progress.WeightPoint
import com.nomi.app.ui.progress.loggingStreakDays
import com.nomi.app.ui.progress.longestLoggingStreakDays
import com.nomi.app.ui.settings.AiProviderEditorState
import com.nomi.app.ui.settings.AiProviderSetting
import com.nomi.app.ui.settings.SettingsUiState
import com.nomi.app.ui.settings.ThemeMode
import com.nomi.app.ui.settings.UnitSystem
import com.nomi.app.ui.today.AddFoodMethod
import com.nomi.app.ui.today.LoggedAmountEditError
import com.nomi.app.ui.today.LoggedAmountEditUiState
import com.nomi.app.ui.today.MealCategory
import com.nomi.app.ui.today.TodayFoodEntry
import com.nomi.app.ui.today.TodayUiState
import com.nomi.app.ui.today.reeditableText
import com.nomi.app.update.GitHubReleaseSource
import com.nomi.app.update.ReleaseVersion
import com.nomi.app.update.UpdateAvailability
import com.nomi.app.update.UpdateCheck
import com.nomi.app.update.UpdateReleaseSource
import com.nomi.app.update.installedVersion
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

enum class LauncherShortcut { PHOTO, MENU }

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val repository = container.repository
    /**
     * Read on every use rather than captured once. Android keeps the process alive across a
     * time-zone change, so a captured zone left "today", the day rollover and every new log on
     * the zone the app happened to start in until the process died.
     */
    private val zoneId: ZoneId get() = ZoneId.systemDefault()
    private val today: LocalDate get() = LocalDate.now(zoneId)

    /** New rows land on the day being viewed, which is not always today. */
    private val logDestination: LogDestination get() = LogDestination(selectedDate.value, zoneId)

    private fun defaultMealCategory(): MealCategory = defaultMealCategory(zoneId)

    /**
     * The current date in the user's own zone.
     *
     * [today] is deliberately private because most screens are driven by [todayState] instead. The
     * History screen is the exception: it needs to know which visible day *is* today so it can
     * offer "copy to today" only where that would mean something.
     */
    val currentDate: LocalDate get() = today

    /**
     * The date every "up to today" query is anchored on.
     *
     * A flow built once from [today] would keep the date the view model was created on for as long
     * as the process lives, and Android keeps Nomi's process around for days. This is moved forward
     * by [refreshCurrentDay] at midnight and whenever the app comes back to the foreground.
     */
    private val currentDay = MutableStateFlow(today)

    val preferences: StateFlow<AppPreferences> = repository.preferences
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            AppPreferences(),
        )

    private val debug = AiDebugRecorder(container, preferences, viewModelScope)
    private val providers =
        AiProviderAccess(container, preferences, debug, viewModelScope, ::showResearchSources)
    private val foodCatalog = LocalFoodCatalog(repository) { recentFoodsSnapshot }

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

    private val mutableLauncherShortcut = MutableStateFlow<LauncherShortcut?>(null)
    val launcherShortcut = mutableLauncherShortcut.asStateFlow()

    /**
     * A newer published release, if one was found on the latest app start.
     *
     * Checked on each activity start without waiting for the network. Failures stay silent.
     * [dismissUpdate] clears the current prompt until the next start checks again.
     */
    private val mutableUpdate = MutableStateFlow<UpdateAvailability>(UpdateAvailability.UpToDate)
    val update: StateFlow<UpdateAvailability> = mutableUpdate.asStateFlow()
    private var updateCheckJob: Job? = null

    fun dismissUpdate() {
        mutableUpdate.value = UpdateAvailability.UpToDate
    }

    /** Rechecks on each start, coalescing requests while a check is running. */
    fun checkForUpdate(
        source: UpdateReleaseSource = GitHubReleaseSource(),
        installed: ReleaseVersion? = installedVersion,
    ) {
        if (updateCheckJob?.isActive == true) return
        updateCheckJob = viewModelScope.launch {
            val availability = runCatching {
                val current = installed ?: return@runCatching UpdateAvailability.UpToDate
                val release = source.latest() ?: return@runCatching UpdateAvailability.UpToDate
                when (
                    val decision = UpdateCheck.decide(
                        installed = current,
                        latest = release.version,
                        latestIsDraft = release.isDraft,
                        latestIsPreRelease = release.isPreRelease,
                    )
                ) {
                    is UpdateAvailability.UpToDate -> UpdateAvailability.UpToDate
                    is UpdateAvailability.Available -> decision.copy(
                        releaseUrl = release.releaseUrl,
                        summary = UpdateCheck.summarize(release.body),
                    )
                }
            }.getOrDefault(UpdateAvailability.UpToDate)
            mutableUpdate.value = availability
        }
    }

    private var pendingLauncherShortcut: LauncherShortcut? = null

    private val mutableOnboardingSaving = MutableStateFlow(false)
    val onboardingSaving = mutableOnboardingSaving.asStateFlow()

    private val selectedDate = MutableStateFlow(today)
    private var dayLogSnapshot: List<FoodLogEntity> = emptyList()
    /** Original wording kept briefly so a freshly saved row can visibly resolve into its label. */
    private val recentlySavedInputs = MutableStateFlow<Map<String, String>>(emptyMap())

    // Declared before the flows that read it: a property initialiser running earlier would see
    // null and take the whole view model down at construction.
    private val healthSync = HealthConnectSyncController(repository, { container.healthConnect }, viewModelScope)
    private val healthConnectUiState = healthSync.state

    /** Recalculates immediately when steps, the profile, or the latest logged weight changes. */
    private val stepCalorieEstimate: Flow<StepCalorieEstimate?> = combine(
        healthConnectUiState,
        repository.profile,
        repository.latestWeight,
    ) { health, profile, latestWeight ->
        if (profile == null || health.activityLocalDate != today.toString()) {
            null
        } else {
            runCatching {
                StepCalorieEstimator.estimateFromAvailableData(
                    steps = health.todaySteps,
                    latestWeightKg = latestWeight?.weightKg,
                    startingWeightKg = profile.startingWeightKg,
                    heightCm = profile.heightCm,
                )
            }.getOrNull()
        }
    }

    private val loggedTodayState: Flow<TodayUiState> = combine(
        selectedDate.flatMapLatest { repository.dayLogs(it.toString()) }
            .onEach { dayLogSnapshot = it },
        repository.currentPlan,
        selectedDate,
        repository.preferences,
        recentlySavedInputs,
    ) { logs, plan, date, prefs, freshInputs ->
        mapToday(
            date, logs, plan, prefs.micronutrients, prefs.goalsCardStyle, freshInputs,
            language = currentLanguage(),
            fallbackZone = zoneId,
        )
    }

    /**
     * The logged day joined with what Health Connect reported for today.
     *
     * The activity figures only belong to the current date. Health Connect is read for today
     * alone, so attaching them to a day the user paged back to would label yesterday's plate with
     * this morning's steps.
     */
    val todayState: StateFlow<TodayUiState> = combine(
        loggedTodayState,
        healthConnectUiState,
        stepCalorieEstimate,
    ) { state, health, stepEstimate ->
        if (state.date != today) {
            state
        } else {
            val activityBelongsToSelectedDay = health.activityLocalDate == state.date.toString()
            state.copy(
                activeCaloriesKcal = health.todayActiveCaloriesKcal
                    .takeIf { activityBelongsToSelectedDay },
                estimatedStepCaloriesKcal = stepEstimate?.activeCaloriesKcal,
                stepEstimateUsesProfileHeight = stepEstimate?.usesProfileHeight == true,
                steps = health.todaySteps.takeIf { activityBelongsToSelectedDay },
            )
        }
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
    ) { logs, query, date, plan -> mapHistory(logs, query, date, plan, currentLanguage(), zoneId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    /**
     * Which rows the user is currently picking out of History, if any.
     *
     * Held here rather than inside the screen so the picker survives a configuration change and
     * so nothing about browsing History depends on it: null is the normal state, and no selection
     * control is ever drawn for it.
     */
    private val _historySelection = MutableStateFlow<HistorySelection?>(null)
    val historySelection: StateFlow<HistorySelection?> = _historySelection.asStateFlow()

    fun startHistorySelection(day: HistoryDay, action: HistorySelectionAction) {
        // The picker is scoped to one whole day, so a search filter left over from browsing would
        // hide the very rows the user is being asked to choose between.
        historyQuery.value = ""
        _historySelection.value = HistorySelection(day = day.date, action = action)
    }

    fun cancelHistorySelection() {
        _historySelection.value = null
    }

    /** One tap toggles, so picking a single food takes a single tap on its row. */
    fun toggleHistorySelection(rowId: Long) {
        _historySelection.update { active -> active?.toggled(rowId) }
    }

    private val progressRange = MutableStateFlow(ProgressRange.THIRTY_DAYS)

    /**
     * Every date with something logged, from the whole log rather than the shown range.
     *
     * The streak is a fact about the user, not about the window the Progress page happens to be
     * looking at, so it must not change when the range does. One row per logged day, which for a
     * daily user is a few hundred short strings.
     */
    private val loggedDates: Flow<List<LocalDate>> =
        currentDay.flatMapLatest { repository.loggedDates(it.toString()) }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList(),
        )

    val progressState: StateFlow<ProgressUiState> = combine(progressRange, currentDay, ::Pair).flatMapLatest { (range, day) ->
        val totalDays = range.dayCount()
        val start = day.minusDays((totalDays - 1).toLong())
        combine(
            repository.weights(start.toString(), day.toString()),
            repository.nutritionHistory(start.toString(), day.toString()),
            repository.profile,
            loggedDates,
        ) { weights, nutrition, profile, dates ->
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
                rangeStart = start,
                streakDays = loggingStreakDays(dates, day),
                longestStreakDays = maxOf(
                    longestLoggingStreakDays(dates, day),
                    loggingStreakDays(dates, day),
                ),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUiState())

    val settingsState: StateFlow<SettingsUiState> = combine(
        repository.preferences,
        repository.currentPlan,
        providers.keyPresence,
        healthConnectUiState,
        stepCalorieEstimate,
    ) { prefs, plan, keys, health, stepEstimate ->
        mapSettings(prefs, plan, keys, health, stepEstimate, currentLanguage())
    }
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

    /**
     * Pages the running research actually opened, as the provider reports them.
     *
     * These are what the spinner shows as site icons. They used to exist only for that
     * animation and were dropped on save, so an entry the provider had clearly researched could
     * still end up claiming it had no sources. Written from the provider's callback thread and
     * read once the request it belongs to has won, hence volatile.
     */
    @Volatile
    private var consultedResearchUrls: List<String> = emptyList()

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
    private var loggedAmountEditEntry: TodayFoodEntry? = null
    /** The logged entry currently being rewritten as text on the page, if any. */
    private val mutableEditedEntryId = MutableStateFlow<Long?>(null)
    val editedEntryId = mutableEditedEntryId.asStateFlow()
    private var portionEditIndex: Int? = null
    private val pendingDeletedLogs = PendingDeletedLogStore()
    private val earlyUndoDeleteRequests = mutableSetOf<Long>()
    private val earlyDiscardDeleteRequests = mutableSetOf<Long>()


    init {
        // Handles the day changing while the app is on screen. The wait runs on uptime, which
        // stops in deep sleep, so MainActivity.onStart covers the app coming back the next morning.
        viewModelScope.launch {
            while (true) {
                delay(delayUntilNextDay(ZonedDateTime.now(zoneId), zoneId))
                refreshCurrentDay()
            }
        }
        // Provider keys are local and safe to inspect immediately. Health Connect reads, on the
        // other hand, must wait for MainActivity.onStart so they run while Nomi is foregrounded.
        providers.refreshKeyPresence()
        healthSync.observeFoodLog()
        viewModelScope.launch {
            runCatching { container.reminderScheduler.reconcileFrom(repository.appPreferencesStore) }
        }
    }

    fun onMainVisible() {
        val shortcut = pendingLauncherShortcut ?: return
        if (startState.value != AppStartState.Main) return
        pendingLauncherShortcut = null
        applyLauncherShortcut(shortcut)
    }

    fun openLauncherShortcut(shortcut: LauncherShortcut) {
        if (startState.value != AppStartState.Main) {
            pendingLauncherShortcut = shortcut
            return
        }
        applyLauncherShortcut(shortcut)
    }

    fun clearLauncherShortcut() {
        mutableLauncherShortcut.value = null
    }

    private fun applyLauncherShortcut(shortcut: LauncherShortcut) {
        if (shortcut == LauncherShortcut.MENU) beginMenuScan()
        mutableLauncherShortcut.value = shortcut
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
                mutableEvents.emit(AppEvent.Message(inUserLanguage("Nomi couldn't save your plan. Please try again.")))
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

    /**
     * Moves everything anchored on "today" to the real date if the day changed while Nomi was open
     * or in the background. Screens the user paged back to on purpose are left alone.
     */
    fun refreshCurrentDay() {
        val previous = currentDay.value
        val now = today
        if (previous == now) return
        currentDay.value = now
        selectedDate.update { followDayRollover(it, previous, now) }
        historyDate.update { followDayRollover(it, previous, now) }
        // Steps and active calories belong to one date, so yesterday's are hidden from now on;
        // read the new day's straight away instead of waiting for the next time the app starts.
        healthSync.request()
    }
    fun setHistoryQuery(value: String) { historyQuery.value = value }
    fun setHistoryDate(value: LocalDate) { historyDate.value = value.coerceAtMost(today) }
    fun setProgressRange(value: ProgressRange) { progressRange.value = value }

    fun beginLogging(method: AddFoodMethod, initialText: String = "") {
        cancelAnalysis()
        // A new entry is not a rewrite of the row that happened to be open. Leaving this set made
        // the next saved food - dictated, photographed or scanned - delete that row.
        mutableEditedEntryId.value = null
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
        mutableLoggingState.value = FoodLoggingUiState.Input(text, entry.mealCategory)
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
                providers.withProvider(ProviderPipeline.VISION) { it.scanMenu(bytes, mediaType) }
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
                    errorMessage = inUserLanguage("Nomi couldn't read that menu page. Add a clearer photo."),
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
                    mutablePortionEditState.value = edit.copy(
                        isProcessing = false,
                        errorMessage = error.safeAiMessage(),
                    )
                }
        }
    }

    /**
     * Turns a sentence into foods and amounts: on the phone when it is plain enough, through the
     * interpretation model otherwise, and with spellings repaired against the user's own log.
     */
    private suspend fun interpret(text: String): ParsedFoodIntent = foodCatalog.withKnownSpellings(
        LocalFoodIntentParser.parseOrNull(text)
            ?: providers.withProvider(ProviderPipeline.FOOD_INTERPRETATION) { it.parseFood(text) },
    )

    /** Binds the routing rules to this app's configured cheap classifier. */
    private fun editRouter() = FoodEditRouter { context, correction ->
        providers.withProvider(ProviderPipeline.PORTION_CHANGE) { it.classifyEdit(context, correction) }
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
                providers.researchNutrition(intent)
            }.onSuccess { analysis ->
                if (portionEditIndex != index) return@onSuccess
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
            debug.recordCachedNutritionTrace("5-minute exact-input cache", analysis)
            saveTextAnalysisAutomatically(analysis, current.mealCategory, text)
            return
        }

        analysisJob?.cancel()
        val requestId = ++analysisRequestId
        // A new lookup must not inherit the pages the previous one opened.
        consultedResearchUrls = emptyList()
        // Claim the input synchronously so repeated taps cannot launch duplicate provider calls.
        mutableLoggingState.value = FoodLoggingUiState.Processing(
            AiProcessingStage.UNDERSTANDING_MEAL,
            originalText = text,
        )
        val job = viewModelScope.launch {
            val intent = runCatching {
                interpret(text).let { parsed ->
                    menuDishes?.let { UserQuantityResolver.applyMenuQuantities(it, parsed) } ?: parsed
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

            if (menuDishes == null) {
                repository.cachedFoodResearch(cacheKey)?.let { cached ->
                    if (requestId == analysisRequestId) {
                        debug.recordRoute(
                            route = NutritionRoute.NEW_RESEARCH,
                            decision = NutritionRoute.Decision.LOCAL,
                            detail = "Validated 21-day food research cache hit",
                        )
                        debug.recordCachedNutritionTrace("21-day validated research cache", cached)
                        saveTextAnalysisAutomatically(cached, current.mealCategory, text)
                    }
                    return@launch
                }
            }

            foodCatalog.cachedAnalysis(intent)?.let { cached ->
                if (requestId == analysisRequestId) {
                    debug.recordCachedNutritionTrace("per-100-g local food cache", cached)
                    saveTextAnalysisAutomatically(cached, current.mealCategory, text)
                }
                return@launch
            }

            // Keep one owner for the whole lookup. A quick estimate used to be saveable before
            // research silently replaced its calories, making the day total change afterwards.
            mutableLoggingState.value = FoodLoggingUiState.Processing(
                AiProcessingStage.FINDING_NUTRITION,
                originalText = text,
            )
            runCatching { providers.researchNutrition(intent) }
                .onSuccess { analysis ->
                    if (requestId != analysisRequestId) return@onSuccess
                    recentFoodAnalysisCache.put(cacheKey, analysis)
                    if (menuDishes == null) {
                        repository.cacheFoodResearch(cacheKey, analysis)
                    }
                    // Persist trusted gram-based results as soon as research succeeds. This
                    // means a retry, app restart, or abandoned preview can reuse the nutrition
                    // without another Exa/Gemini request; estimates and size-only portions are
                    // intentionally skipped by cacheAnalyzedFood's provenance/weight checks.
                    analysis.items.forEach { item ->
                        runCatching { foodCatalog.cache(item) }
                    }
                    debug.recordRoute(
                        route = NutritionRoute.NEW_RESEARCH,
                        decision = NutritionRoute.Decision.DIRECT,
                        detail = "New food entry researched before preview",
                    )
                    saveTextAnalysisAutomatically(
                        analysis,
                        current.mealCategory,
                        text,
                        consultedUrls = consultedResearchUrls,
                    )
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    if (requestId != analysisRequestId) return@onFailure
                    mutableLoggingState.value = FoodLoggingUiState.Error(
                        researchFailureMessage(error, currentLanguage()),
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

    /**
     * Typed and dictated meals become journal rows as soon as their researched nutrition is
     * ready. Photos and manual entries still use their dedicated correction steps.
     */
    private fun saveTextAnalysisAutomatically(
        analysis: FoodAnalysis,
        category: MealCategory,
        originalText: String,
        consultedUrls: List<String> = emptyList(),
    ) {
        if (loggingSaveInProgress) return
        loggingSaveInProgress = true
        mutableLoggingState.value = FoodLoggingUiState.Processing(
            AiProcessingStage.FINDING_NUTRITION,
            originalText = originalText,
        )
        val revealGroupId = UUID.randomUUID().toString()
        recentlySavedInputs.update { current -> current + (revealGroupId to originalText) }
        viewModelScope.launch {
            try {
                runCatching {
                    val grouped = analysis.items.size > 1
                    // Keep every researched product as its own immutable log row. The Today
                    // page groups rows with the same entryGroupId into one menu summary, so the
                    // total stays compact without throwing away the per-product nutrition.
                    val validated = ServingNutritionNormalizer.validateBeforeSave(analysis)
                    val logs = validated.items.map { item ->
                        val foodId = if (grouped) null else foodCatalog.cache(item)
                        item.toLog(category, "ai", logDestination, consultedUrls, originalText).copy(
                            foodId = foodId,
                            entryGroupId = revealGroupId,
                        )
                    }
                    repository.addLogs(logs)
                }.onSuccess {
                    // The rewritten entry exists now, so the one it replaces can go.
                    mutableEditedEntryId.value?.let { replaced ->
                        mutableEditedEntryId.value = null
                        runCatching { repository.deleteLogsForUndo(replaced) }
                    }
                    lastLoggingText = ""
                    dismissPortionEdit()
                    mutableLoggingState.value = FoodLoggingUiState.Input("", defaultMealCategory())
                    mutableEvents.emit(AppEvent.FoodSaved)
                    // The UI has enough time to finish its longer shimmer, then this transient
                    // wording is discarded so old rows never replay the effect.
                    viewModelScope.launch {
                        delay(1_600L)
                        recentlySavedInputs.update { current -> current - revealGroupId }
                    }
                }.onFailure { error ->
                    recentlySavedInputs.update { current -> current - revealGroupId }
                    mutableLoggingState.value = FoodLoggingUiState.Error(
                        message = error.safeAiMessage(),
                        canRetry = true,
                        originalText = originalText,
                    )
                }
            } finally {
                loggingSaveInProgress = false
            }
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
        mutableEditedEntryId.value = null
        val requestId = ++barcodeLookupRequestId
        val category = defaultMealCategory()
        mutableBarcodeAmountState.value = null
        viewModelScope.launch {
            mutableLoggingState.value = FoodLoggingUiState.Processing(AiProcessingStage.FINDING_NUTRITION)
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
     * Recognizes a photo and stops there, handing the description back for review.
     *
     * Research is the expensive half in both money and seconds, so it does not start until the
     * user has agreed the photo was read correctly. Recognition mistakes are cheap to fix as
     * words and expensive to fix as nutrition.
     */
    fun analyzePhoto(bytes: ByteArray, mediaType: String) {
        analysisJob?.cancel()
        mutableEditedEntryId.value = null
        val requestId = ++analysisRequestId
        val job = viewModelScope.launch {
            val category = defaultMealCategory()
            runCatching {
                mutableLoggingState.value = FoodLoggingUiState.Processing(AiProcessingStage.UNDERSTANDING_MEAL)
                providers.withProvider(ProviderPipeline.VISION) { it.identifyFood(bytes, mediaType) }
            }.onSuccess { vision ->
                if (requestId != analysisRequestId) return@onSuccess
                val recognized = vision.items.map { it.toPhotoParsedItem() }
                val description = recognized.toPhotoMealDescription()
                debug.recordRoute(
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
                    notes = (vision.notes + vision.items.mapNotNull { item ->
                        item.weightEstimationBasis?.takeIf(String::isNotBlank)?.let { "${item.name}: $it" }
                    }).distinct(),
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
        // A new lookup must not inherit the pages the previous one opened.
        consultedResearchUrls = emptyList()
        val place = review.place.trim().takeIf(String::isNotBlank)
        lastLoggingText = description
        mutableLoggingState.value = FoodLoggingUiState.Processing(
            AiProcessingStage.UNDERSTANDING_MEAL,
            originalText = description,
        )
        val job = viewModelScope.launch {
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
                mutableLoggingState.value = FoodLoggingUiState.Processing(
                    AiProcessingStage.FINDING_NUTRITION,
                    originalText = description,
                    sourceUrls = listOfNotNull(preferences.value.foodResearchProvider.website()),
                )
                providers.researchNutrition(intent).let { analysis ->
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

    fun lookupBarcode(barcode: String) {
        cancelAnalysis()
        mutableEditedEntryId.value = null
        val requestId = ++barcodeLookupRequestId
        val category = defaultMealCategory()
        mutableBarcodeAmountState.value = null
        viewModelScope.launch {
            mutableLoggingState.value = FoodLoggingUiState.Processing(AiProcessingStage.FINDING_NUTRITION)
            runCatching {
                val cached = repository.foodByBarcode(barcode)
                var servingLabel: String? = null
                val analyzedItem = if (cached != null && !cached.isEstimated) {
                    cached.toAnalyzedItem("Local barcode cache")
                } else {
                    val product = container.openFoodFacts.findByBarcode(barcode)
                    servingLabel = product?.servingSize
                    product?.toAnalyzedItemOrNull()
                        ?: cached?.toAnalyzedItem("Local barcode estimate")
                        ?: run {
                            val label = product?.name?.takeIf { it.isNotBlank() }
                                ?: "Product with barcode $barcode"
                            val basisUnit = product?.nutritionBasisUnit ?: "g"
                            providers.researchNutrition(
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
            mutableBarcodeAmountState.value = current.copy(
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
                                item.toLog(current.mealCategory, "ai", logDestination, consultedResearchUrls)
                                    .copy(foodId = foodCatalog.cache(item))
                            }
                            repository.addLogs(logs)
                        }
                        is FoodLoggingUiState.Manual -> {
                            require(current.draft.isValid)
                            val log = current.draft.toLog(logDestination)
                            repository.addLog(log.copy(foodId = foodCatalog.cache(log)))
                        }
                        else -> error("Unsupported logging state")
                    }
                }.onSuccess {
                    // The rewritten entry exists now, so the one it replaces can go.
                    mutableEditedEntryId.value?.let { replaced ->
                        mutableEditedEntryId.value = null
                        runCatching { repository.deleteLogsForUndo(replaced) }
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
    /**
     * Resolves one logged entry for the detail screen straight from the database.
     *
     * Every logged food is its own immutable row, so an id is enough: a grouped meal's siblings
     * are found by the shared entry group. This replaces a scan of the selected day plus a
     * 30-day history window, which could not resolve anything older.
     */
    fun foodDetail(id: Long): Flow<TodayFoodEntry?> = flow {
        if (id <= 0L) return@flow
        val log = repository.foodLog(id) ?: return@flow
        val siblings = log.entryGroupId
            ?.let { repository.logsByEntryGroup(it) }
            .orEmpty()
            .ifEmpty { listOf(log) }
        emit(
            siblings.toGroupedTodayEntries(currentLanguage(), zoneId).firstOrNull { it.id == id }
                ?: log.toTodayEntry(zoneId),
        )
    }.flowOn(Dispatchers.IO)

    fun favoriteFoodLog(id: Long) {
        viewModelScope.launch {
            runCatching {
                val log = requireNotNull(repository.foodLog(id))
                val foodId = requireNotNull(log.foodId) { "This entry has no reusable food" }
                val now = System.currentTimeMillis()
                // Re-favouriting has to update the existing row, not collide with it: food_id is
                // UNIQUE, and inserting a second row for the same food failed and reported the
                // unrelated "save this food again" message, leaving the old portion in place.
                val existing = repository.favorites.first().firstOrNull { it.food.id == foodId }
                repository.favorite(
                    FavoriteFoodEntity(
                        id = existing?.favorite?.id ?: 0,
                        foodId = foodId,
                        typicalAmount = log.amount,
                        typicalUnit = log.unit,
                        typicalGrams = log.grams,
                        createdAtEpochMillis = existing?.favorite?.createdAtEpochMillis ?: now,
                        lastUsedAtEpochMillis = now,
                    ),
                )
            }.onFailure {
                mutableEvents.emit(AppEvent.Message(inUserLanguage("Nomi couldn't save that favorite.")))
            }
        }
    }

    /** Removes a food from favourites, which the favourite action previously had no way to do. */
    fun unfavoriteFoodLog(id: Long) {
        viewModelScope.launch {
            runCatching {
                val log = requireNotNull(repository.foodLog(id))
                val foodId = requireNotNull(log.foodId) { "This entry has no reusable food" }
                repository.unfavorite(foodId)
            }.onFailure {
                mutableEvents.emit(AppEvent.Message(inUserLanguage("Nomi couldn't remove that favorite.")))
            }
        }
    }

    /**
     * Deletes a logged entry and everything that was logged with it.
     *
     * Every product of a meal is its own row sharing an entry group, so the group is what the
     * user thinks of as one thing. Deleting a single row out of it used to be possible from the
     * detail screen: the remaining products kept the group id, the meal total on Today became
     * permanently wrong, and the survivors were then deletable only as orphans. Deleting by group
     * makes the detail screen and the Today row agree, with no schema change.
     */
    fun deleteFoodLog(id: Long) {
        viewModelScope.launch {
            runCatching {
                val log = repository.foodLog(id) ?: return@runCatching
                val groupId = log.entryGroupId
                val group = if (groupId.isNullOrBlank()) {
                    listOf(log)
                } else {
                    repository.logsByEntryGroup(groupId).ifEmpty { listOf(log) }
                }
                group.forEach { repository.deleteLog(it) }
            }.onFailure {
                mutableEvents.emit(AppEvent.Message(inUserLanguage("Nomi couldn't delete that food.")))
            }
        }
    }

    /**
     * Logs a day that arrived from another phone by holding the two together.
     *
     * The rows keep the date the day was eaten on rather than being dated today, because the point
     * of receiving somebody else's Monday is knowing it was their Monday. They are marked as
     * shared and estimated, so nothing later in the app treats them as this user's own numbers or
     * as anything the research verified.
     */
    fun importSharedDay(envelope: ShareEnvelopeV1) {
        viewModelScope.launch {
            // A sender whose clock or zone is ahead can name a date this phone has not reached;
            // Today cannot page forward, so those rows would be saved where nobody can see them.
            val eatenOn = runCatching { LocalDate.parse(envelope.day.date) }
                .getOrElse { today }
                .coerceAtMost(today)
            val logs = NomiShareImporter.logsFor(
                envelope = envelope,
                date = eatenOn,
                zone = zoneId,
                now = loggedAtFor(eatenOn, Instant.now(), zoneId),
            )
            if (logs.isEmpty()) {
                mutableEvents.emit(AppEvent.Message(inUserLanguage("That shared day had no food in it.")))
                return@launch
            }
            runCatching { repository.addLogs(logs) }
                .onSuccess {
                    mutableEvents.emit(
                        AppEvent.Message(
                            inUserLanguage(
                                "Added {0} shared foods to {1}.",
                                logs.size,
                                eatenOn.format(DateTimeFormatter.ofPattern("d MMMM", currentLanguage().locale)),
                            ),
                        ),
                    )
                }
                .onFailure {
                    mutableEvents.emit(
                        AppEvent.Message(inUserLanguage("Nomi couldn't add that shared day.")),
                    )
                }
        }
    }

    fun startLoggedAmountEdit(entry: TodayFoodEntry) {
        if (entry.id <= 0 || entry.amount <= 0.0) return
        loggedAmountEditEntry = entry
        mutableLoggedAmountEditState.value = LoggedAmountEditUiState(
            entryId = entry.id,
            name = entry.name,
            originalUnit = entry.unit,
            unit = entry.unit,
            originalAmount = entry.amount,
            originalCalories = entry.calories,
            amountText = formatLoggedAmountInput(entry.amount),
        )
    }

    fun updateLoggedAmountInput(text: String) {
        mutableLoggedAmountEditState.value = mutableLoggedAmountEditState.value?.copy(
            amountText = text.take(12),
            interpretation = null,
            error = null,
        )
    }

    fun updateLoggedAmountCorrection(text: String) {
        mutableLoggedAmountEditState.value = mutableLoggedAmountEditState.value?.copy(
            correctionText = text.take(300),
            interpretation = null,
            error = null,
        )
    }

    fun interpretLoggedAmountCorrection() {
        val edit = mutableLoggedAmountEditState.value ?: return
        val entry = loggedAmountEditEntry?.takeIf { it.id == edit.entryId } ?: run {
            mutableLoggedAmountEditState.value = edit.copy(error = LoggedAmountEditError.ENTRY_GONE)
            return
        }
        if (!edit.canInterpret) return
        mutableLoggedAmountEditState.value = edit.copy(
            isInterpreting = true,
            interpretation = null,
            error = null,
        )
        viewModelScope.launch {
            runCatching {
                editRouter().route(entry.toAmountEditItem(), edit.correctionText)
            }.onSuccess { decision ->
                val current = mutableLoggedAmountEditState.value
                    ?.takeIf { it.entryId == edit.entryId } ?: return@onSuccess
                when (decision) {
                    is FoodEditRouter.Decision.Scale -> {
                        val result = decision.result
                        debug.recordRoute(
                            route = NutritionRoute.PORTION_SCALE,
                            decision = decision.decidedBy,
                            detail = result.description,
                            confidence = decision.classification?.confidence,
                        )
                        mutableLoggedAmountEditState.value = current.copy(
                            unit = result.item.unit,
                            amountText = formatLoggedAmountInput(result.item.quantity),
                            interpretation = result.description,
                            isInterpreting = false,
                            error = null,
                        )
                    }
                    is FoodEditRouter.Decision.Research -> {
                        // Saved-amount corrections are never allowed to trigger food research.
                        mutableLoggedAmountEditState.value = current.copy(
                            isInterpreting = false,
                            error = LoggedAmountEditError.INVALID_CORRECTION,
                        )
                    }
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                mutableLoggedAmountEditState.value = mutableLoggedAmountEditState.value
                    ?.takeIf { it.entryId == edit.entryId }
                    ?.copy(
                        isInterpreting = false,
                        error = LoggedAmountEditError.INTERPRETATION_FAILED,
                    )
            }
        }
    }

    fun dismissLoggedAmountEdit() {
        loggedAmountEditEntry = null
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
                    newUnit = edit.unit,
                    updatedAtEpochMillis = System.currentTimeMillis(),
                )
            }.onSuccess { updated ->
                mutableLoggedAmountEditState.value = if (updated) {
                    loggedAmountEditEntry = null
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
                repository.deleteLogsForUndo(id)
                    .takeIf(List<FoodLogEntity>::isNotEmpty)
                    ?: error("That food is no longer available")
            }.onSuccess { snapshots ->
                if (earlyDiscardDeleteRequests.remove(id)) {
                    earlyUndoDeleteRequests.remove(id)
                    return@onSuccess
                }
                pendingDeletedLogs.remember(snapshots)
                if (earlyUndoDeleteRequests.remove(id)) restoreDeletedFoodLog(id)
            }.onFailure { error ->
                earlyUndoDeleteRequests.remove(id)
                earlyDiscardDeleteRequests.remove(id)
                mutableEvents.emit(
                    AppEvent.Message(error.message ?: inUserLanguage("Nomi couldn't delete that food.")),
                )
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
        val snapshots = pendingDeletedLogs.takeAll(id) ?: return
        viewModelScope.launch {
            runCatching {
                check(repository.restoreDeletedLogs(snapshots)) {
                    "The deleted food could not be restored"
                }
            }.onFailure { error ->
                pendingDeletedLogs.remember(snapshots)
                mutableEvents.emit(
                    AppEvent.Message(error.message ?: inUserLanguage("Nomi couldn't restore that food.")),
                )
            }
        }
    }
    /**
     * Saves the rows a History selection picked, and nothing else.
     *
     * [logIds] arrives already expanded from the visible rows, so a combined meal contributes all
     * of its products and a half-picked day contributes half a meal. The saved items keep the
     * portions that were logged, because [NomiRepository.saveLoggedMeal] snapshots the log rather
     * than re-deriving anything.
     */
    fun saveHistoryRowsAsMeal(day: HistoryDay, logIds: List<Long>, name: String) {
        if (name.isBlank() || logIds.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                val category = day.entries.firstOrNull { entry ->
                    logIds.any { it in entry.logIdsForSelection() }
                }?.mealCategory
                repository.saveLoggedMeal(
                    SaveLoggedMealRequest(
                        name = name.trim(),
                        normalizedName = name.trim().lowercase(Locale.ROOT),
                        logIds = logIds,
                        // A meal built from a lunch stays a lunch when it is logged again.
                        defaultMealCategory = category?.name,
                        createdAtEpochMillis = System.currentTimeMillis(),
                    ),
                )
            }.onSuccess {
                mutableEvents.emit(AppEvent.Message(inUserLanguage("Nomi saved that meal.")))
            }.onFailure {
                mutableEvents.emit(AppEvent.Message(inUserLanguage("Nomi couldn't save that meal")))
            }
        }
    }

    /**
     * Copies the rows a History selection picked onto today, and nothing else.
     *
     * The repository rewrites each picked row onto today, remapping the group ids so a copied meal
     * cannot weld itself onto the original, and leaving every portion, macro and meal category
     * exactly as it was logged.
     */
    fun addHistoryRowsToToday(day: HistoryDay, logIds: List<Long>) {
        if (logIds.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                repository.copyLogsToDate(
                    logIds = logIds,
                    targetLocalDate = today.toString(),
                    targetStartEpochMillis = System.currentTimeMillis(),
                    targetZoneId = zoneId.id,
                )
            }.onSuccess {
                mutableEvents.emit(AppEvent.Message(inUserLanguage("Nomi added those foods to today.")))
            }.onFailure {
                mutableEvents.emit(AppEvent.Message(inUserLanguage("Nomi couldn't add those foods to today.")))
            }
        }
    }


    fun duplicateFoodLog(id: Long) {
        viewModelScope.launch {
            runCatching {
                val source = requireNotNull(repository.foodLog(id))
                val group = source.entryGroupId
                    ?.let { repository.logsByEntryGroup(it) }
                    .orEmpty()
                    .ifEmpty { listOf(source) }
                val now = Instant.now()
                val date = runCatching { LocalDate.parse(source.localDate) }.getOrDefault(today)
                repository.addLogs(
                    duplicatedLogs(
                        source = source,
                        group = group,
                        loggedAtEpochMillis = loggedAtFor(date, now, zoneId),
                        nowEpochMillis = now.toEpochMilli(),
                    ),
                )
            }.onFailure {
                mutableEvents.emit(AppEvent.Message(inUserLanguage("That food is no longer available")))
            }
        }
    }

    /**
     * Copies a whole day onto today.
     *
     * The one-shot action, kept because a whole day is a real thing to eat again; picking out of it
     * is what "Add items to today" is for.
     */
    fun copyDayToToday(source: LocalDate) {
        viewModelScope.launch {
            runCatching {
                repository.copyDay(source.toString(), today.toString(), System.currentTimeMillis(), zoneId.id)
            }.onSuccess {
                mutableEvents.emit(AppEvent.Message(inUserLanguage("Nomi copied that day to today.")))
            }.onFailure {
                mutableEvents.emit(AppEvent.Message(inUserLanguage("Nomi couldn't copy that day.")))
            }
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
                mutableEvents.emit(AppEvent.Message(inUserLanguage("Enter a valid weight.")))
                return@launch
            }

            when (healthSync.mirrorNewWeight(localId, kilograms, now)) {
                WeightMirrorResult.SYNCED_BUT_UNMARKED -> mutableEvents.emit(
                    AppEvent.Message("Weight was saved in Nomi and Health Connect, but sync status couldn't be updated."),
                )
                WeightMirrorResult.FAILED -> mutableEvents.emit(
                    AppEvent.Message("Weight was saved in Nomi, but Health Connect sync failed."),
                )
                WeightMirrorResult.NOT_PERMITTED, WeightMirrorResult.SYNCED -> Unit
            }
        }
    }

    private val libraryDeletions = com.nomi.app.ui.library.LibraryDeletionController(
        scope = viewModelScope,
        delete = { item ->
            when (item.kind) {
                LibraryItemKind.FAVORITE -> repository.unfavorite(item.id)
                LibraryItemKind.SAVED_MEAL -> repository.deleteSavedMealById(item.id)
                LibraryItemKind.RECENT -> Unit
            }
        },
        onFailure = {
            mutableEvents.emit(AppEvent.Message(inUserLanguage("Nomi couldn't delete that food.")))
        },
    )
    val pendingLibraryDeletions = libraryDeletions.pending

    fun deleteLibraryItem(item: LibraryItem) = libraryDeletions.request(item)
    fun undoLibraryDeletion(item: LibraryItem) = libraryDeletions.undo(item)

    fun addLibraryItem(item: LibraryItem) {
        viewModelScope.launch {
            runCatching {
                when (item.kind) {
                    LibraryItemKind.RECENT -> recentFoodsSnapshot.first { it.id == item.id }
                        .let { repository.addLog(it.toLog(logDestination)) }
                    LibraryItemKind.FAVORITE -> favoriteSnapshot.first { it.food.id == item.id }
                        .let { repository.addLog(it.toLog(logDestination)) }
                    LibraryItemKind.SAVED_MEAL -> repository.addSavedMealToLog(
                        AddSavedMealToLogRequest(
                            savedMealId = item.id,
                            mealCategory = defaultMealCategory().name,
                            localDate = selectedDate.value.toString(),
                            startEpochMillis = loggedAtFor(selectedDate.value, Instant.now(), zoneId),
                            zoneId = zoneId.id,
                        ),
                    )
                }
            }.onSuccess {
                mutableEvents.emit(AppEvent.FoodSaved)
            }.onFailure {
                mutableEvents.emit(AppEvent.Message(inUserLanguage("Nomi couldn't add that item.")))
            }
        }
    }

    fun saveNutritionTargets(calories: Int, protein: Int, carbs: Int, fat: Int) {
        val plan = currentPlan.value ?: return
        if (calories !in 800..10_000 || protein !in 0..600 || carbs !in 0..900 || fat !in 0..300) {
            mutableEvents.tryEmit(AppEvent.Message(inUserLanguage("Check the nutrition target values.")))
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
            }.onFailure {
                mutableEvents.emit(AppEvent.Message(inUserLanguage("Nomi couldn't save those targets.")))
            }
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
            }.onFailure {
                mutableEvents.emit(AppEvent.Message(inUserLanguage("Nomi couldn't recalculate that profile.")))
            }
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

    fun setLanguage(language: NomiLanguage) {
        viewModelScope.launch {
            repository.appPreferencesStore.setLanguageTag(language.tag)
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
            runCatching { providers.save(pipelineAt(index), state) }
                .onSuccess {
                    recentFoodAnalysisCache.clear()
                    refreshProviderAndHealthStatus()
                    onResult(true, "Provider saved")
                }
                .onFailure { error -> onResult(false, error.safeProviderSettingsMessage()) }
        }
    }

    fun removeProviderKey(
        index: Int,
        state: AiProviderEditorState,
        onResult: (success: Boolean, message: String) -> Unit,
    ) {
        viewModelScope.launch {
            runCatching { providers.removeStoredKeys(pipelineAt(index), state) }
                .onSuccess { removed ->
                    recentFoodAnalysisCache.clear()
                    refreshProviderAndHealthStatus()
                    onResult(
                        true,
                        if (removed) "Stored API key removed" else "No stored API key was found",
                    )
                }
                .onFailure { error -> onResult(false, error.safeProviderSettingsMessage()) }
        }
    }

    fun testProvider(index: Int, state: AiProviderEditorState, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                providers.testConnection(pipelineAt(index), state)
                "Connection successful"
            }.getOrElse(Throwable::safeProviderConnectionMessage)
            onResult(result)
        }
    }

    /** The settings list is indexed in pipeline order; anything out of range edits research. */
    private fun pipelineAt(index: Int): ProviderPipeline =
        ProviderPipeline.entries.getOrElse(index) { ProviderPipeline.FOOD_RESEARCH }

    fun refreshProviderAndHealthStatus() {
        providers.refreshKeyPresence()
        healthSync.request()
    }

    fun syncHealthConnect() = healthSync.request(userInitiated = true)

    fun healthConnectPermissionsChanged() = healthSync.request()

    private fun cancelAnalysis() {
        analysisRequestId += 1
        analysisJob?.cancel()
        analysisJob = null
        // The pages belong to the lookup that opened them. A label photo or a cached barcode has
        // no pages of its own and used to be saved citing the previous meal's research.
        consultedResearchUrls = emptyList()
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
        // Kept whatever the stage is: the save needs the full list, while the spinner only wants
        // it while it is on screen.
        consultedResearchUrls = sourceUrls.distinct()
        val current = mutableLoggingState.value
        if (current !is FoodLoggingUiState.Processing ||
            current.stage != AiProcessingStage.FINDING_NUTRITION
        ) return
        mutableLoggingState.value = current.copy(
            sourceUrls = sourceUrls.distinct().take(3),
        )
    }

    /**
     * The composable [nomiString] needs a composition, but a few strings are written into saved
     * data from here. They read the same catalogue so a translated log does not sprout English
     * rows.
     */
    private fun inUserLanguage(english: String): String =
        NomiTranslations.translate(english, currentLanguage())

    private fun inUserLanguage(english: String, vararg arguments: Any?): String =
        NomiTranslations.format(english, currentLanguage(), *arguments)

    private fun currentLanguage(): NomiLanguage =
        NomiLanguage.fromTag(preferences.value.languageTag)
            ?: NomiLanguage.matching(Locale.getDefault())

}

/** Room for a described plate without room for a pasted document. */
private const val MAX_PHOTO_DESCRIPTION_CHARS = 1_000
private const val MAX_PHOTO_PLACE_CHARS = 120
private const val MAX_MENU_LOGGING_TEXT_CHARS = 1_500
