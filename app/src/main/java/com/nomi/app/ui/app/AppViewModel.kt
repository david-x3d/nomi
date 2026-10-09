package com.nomi.app.ui.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nomi.app.ai.model.AiProviderKind
import com.nomi.app.ai.model.AnalyzedFoodItem
import com.nomi.app.ai.model.MenuDish
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
import com.nomi.app.data.preferences.ProviderSelection
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
import com.nomi.app.domain.usecase.FoodEditRouter
import com.nomi.app.domain.usecase.NutritionRoute
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
import com.nomi.app.ui.logging.ManualFoodDraft
import com.nomi.app.ui.profile.ProfileEdit
import com.nomi.app.ui.progress.NutritionPoint
import com.nomi.app.ui.progress.ProgressRange
import com.nomi.app.ui.progress.ProgressUiState
import com.nomi.app.ui.progress.WeightPoint
import com.nomi.app.ui.progress.loggingStreakDays
import com.nomi.app.ui.progress.longestLoggingStreakDays
import com.nomi.app.ui.settings.AiKeyField
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
import com.nomi.app.ui.today.withActivityTargetAdjustment
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
        AiProviderAccess(container, preferences, debug, viewModelScope)
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

    private val mutableSharedContent = MutableStateFlow<SharedContent?>(null)
    val sharedContent = mutableSharedContent.asStateFlow()

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
    private var pendingSharedContent: SharedContent? = null

    private val mutableOnboardingSaving = MutableStateFlow(false)
    val onboardingSaving = mutableOnboardingSaving.asStateFlow()

    private val selectedDate = MutableStateFlow(today)
    private var dayLogSnapshot: List<FoodLogEntity> = emptyList()
    /** Original wording kept briefly so a freshly saved row can visibly resolve into its label. */
    private val logging = FoodLoggingCoordinator(
        repository, foodCatalog, providers, debug, viewModelScope, preferences,
        destination = { logDestination },
        defaultMealCategory = ::defaultMealCategory,
        currentLanguage = ::currentLanguage,
        inUserLanguage = { inUserLanguage(it) },
        findBarcodeProduct = container.openFoodFacts::findByBarcode,
        emitEvent = mutableEvents::emit,
    )
    private val recentlySavedInputs = logging.recentlySavedInputs

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
        repository.preferences,
    ) { state, health, stepEstimate, prefs ->
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
            ).withActivityTargetAdjustment(prefs.adjustTargetFromActivity)
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

    /** True once the key store has been read and typing a meal would fail for want of a key. */
    val aiSetupNeeded: StateFlow<Boolean> = providers.keyPresence
        .map { keys -> keys.needsAiSetup() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

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

    val loggingState = logging.loggingState
    val barcodeAmountState = logging.barcodeAmountState
    val menuScanState = logging.menuScanState
    val portionEditState = logging.portionEditState
    val editedEntryId = logging.editedEntryId

    private val mutableLoggedAmountEditState = MutableStateFlow<LoggedAmountEditUiState?>(null)
    val loggedAmountEditState = mutableLoggedAmountEditState.asStateFlow()
    private var loggedAmountEditEntry: TodayFoodEntry? = null
    private val foodDeletions = FoodDeletionController(
        viewModelScope, repository::deleteLogsForUndo, repository::restoreDeletedLogs,
        onFailure = { error -> mutableEvents.emit(AppEvent.Message(error.safeAiMessage())) },
    )
    val pendingFoodDeletions = foodDeletions.pending

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
        viewModelScope.launch {
            // An install already past onboarding when this build first runs was set up on the
            // old provider defaults. That goes on record before the keys are looked up, because
            // which keys are looked for depends on it. The profile is the truth here; the
            // onboarding flag in preferences is only a startup hint and can be missing.
            runCatching {
                if (repository.profile.first()?.onboardingCompleted == true) {
                    repository.appPreferencesStore.keepLegacyProviderDefaults()
                }
            }
            providers.refreshKeyPresence()
        }
        healthSync.observeFoodLog()
        viewModelScope.launch {
            runCatching { container.reminderScheduler.reconcileFrom(repository.appPreferencesStore) }
        }
    }

    fun onMainVisible() {
        if (startState.value != AppStartState.Main) return
        pendingLauncherShortcut?.let { shortcut ->
            pendingLauncherShortcut = null
            applyLauncherShortcut(shortcut)
        }
        pendingSharedContent?.let { content ->
            pendingSharedContent = null
            mutableSharedContent.value = content
        }
    }

    /**
     * Holds a share until the main screen exists. A share can arrive during onboarding or before
     * the profile has loaded, and handing it to a page that is not there would drop it.
     */
    fun openSharedContent(content: SharedContent) {
        if (startState.value != AppStartState.Main) {
            pendingSharedContent = content
            return
        }
        mutableSharedContent.value = content
    }

    fun clearSharedContent() {
        mutableSharedContent.value = null
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

    fun beginLogging(method: AddFoodMethod, initialText: String = "") = logging.beginLogging(method, initialText)
    fun updateLoggingText(value: String) = logging.updateLoggingText(value)
    fun editLoggingText() = logging.editLoggingText()
    fun dismissLoggingDraft() = logging.dismissLoggingDraft()
    fun editEntryTextInline(entry: TodayFoodEntry) = logging.editEntryTextInline(entry)
    fun updateLoggingMealCategory(category: MealCategory) = logging.updateLoggingMealCategory(category)
    fun showManualLogging(prefillName: String = logging.lastLoggingText) = logging.showManualLogging(prefillName)
    fun updateManualDraft(value: ManualFoodDraft) = logging.updateManualDraft(value)
    fun updatePreviewItem(index: Int, item: AnalyzedFoodItem) = logging.updatePreviewItem(index, item)
    fun beginMenuScan() = logging.beginMenuScan()
    fun updateMenuSearch(query: String) = logging.updateMenuSearch(query)
    fun scanMenuPage(bytes: ByteArray, mediaType: String) = logging.scanMenuPage(bytes, mediaType)
    fun toggleMenuDish(dish: MenuDish) = logging.toggleMenuDish(dish)
    fun selectMenuDishes() = logging.selectMenuDishes()
    fun removePreviewItem(index: Int) = logging.removePreviewItem(index)
    fun beginPortionEdit(index: Int) = logging.beginPortionEdit(index)
    fun updatePortionCorrection(correction: String) = logging.updatePortionCorrection(correction)
    fun dismissPortionEdit() = logging.dismissPortionEdit()
    fun interpretPortionCorrection() = logging.interpretPortionCorrection()
    fun researchEditedItem() = logging.researchEditedItem()
    fun applyPortionCorrection() = logging.applyPortionCorrection()
    fun analyzeText() = logging.analyzeText()
    fun retryAnalysis() = logging.retryAnalysis()
    fun analyzeNutritionLabel(bytes: ByteArray, mediaType: String) = logging.analyzeNutritionLabel(bytes, mediaType)
    fun analyzePhoto(bytes: ByteArray, mediaType: String) = logging.analyzePhoto(bytes, mediaType)
    fun updatePhotoDescription(description: String) = logging.updatePhotoDescription(description)
    fun updatePhotoPlace(place: String) = logging.updatePhotoPlace(place)
    fun confirmPhotoDescription() = logging.confirmPhotoDescription()
    fun lookupBarcode(barcode: String) = logging.lookupBarcode(barcode)
    fun updateBarcodeAmount(value: String) = logging.updateBarcodeAmount(value)
    fun updateBarcodeUnit(unit: String) = logging.updateBarcodeUnit(unit)
    fun confirmBarcodeAmount() = logging.confirmBarcodeAmount()
    fun cancelBarcodeAmount() = logging.cancelBarcodeAmount()
    fun confirmLogging() = logging.confirmLogging()

    private suspend fun interpret(text: String) = logging.interpret(text)
    private fun editRouter() = logging.editRouter()

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

    fun deleteFoodLogForUndo(entry: TodayFoodEntry) = foodDeletions.request(entry, selectedDate.value)
    fun undoDeletedFoodLog(id: Long) = foodDeletions.undo(id)

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
            logging.clearCache()
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

    fun setExaFullPageText(enabled: Boolean) {
        viewModelScope.launch { repository.appPreferencesStore.setExaFullPageText(enabled) }
    }

    fun setOpenRouterPreferredProvider(slug: String) {
        viewModelScope.launch { repository.appPreferencesStore.setOpenRouterPreferredProvider(slug) }
    }

    private val mutableModelComparison = MutableStateFlow(ModelComparisonUiState())
    internal val modelComparison: StateFlow<ModelComparisonUiState> = mutableModelComparison.asStateFlow()
    private var modelComparisonJob: Job? = null

    fun setModelComparisonInput(text: String) {
        mutableModelComparison.update { it.copy(input = text) }
    }

    fun toggleComparedModel(model: String) {
        mutableModelComparison.update { it.toggled(model) }
    }

    fun setCustomComparedModel(text: String) {
        mutableModelComparison.update { it.copy(customModelInput = text) }
    }

    fun addCustomComparedModel() {
        mutableModelComparison.update { it.withCustomModel() }
    }

    /**
     * Reads the meal once, then has every selected OpenRouter model research it through Exa.
     * A comparison started while one is running replaces it.
     */
    fun runModelComparison() {
        val start = mutableModelComparison.value
        val text = start.input.trim()
        val models = start.selectedInOrder()
        if (text.isEmpty() || models.isEmpty()) return
        modelComparisonJob?.cancel()
        mutableModelComparison.update { state ->
            state.copy(
                isRunning = true,
                errorMessage = null,
                cards = models.map { ModelComparisonCard(it, ModelComparisonStatus.RUNNING) },
            )
        }
        modelComparisonJob = viewModelScope.launch {
            try {
                val intent = interpret(text)
                providers.compareOpenRouterModels(intent, models) { run ->
                    val card = run.result.fold(
                        onSuccess = { analysis ->
                            ModelComparisonCard(
                                model = run.model,
                                status = ModelComparisonStatus.DONE,
                                analysis = analysis,
                                durationMillis = run.durationMillis,
                            )
                        },
                        onFailure = { error ->
                            ModelComparisonCard(
                                model = run.model,
                                status = ModelComparisonStatus.FAILED,
                                errorMessage = researchFailureMessage(error, currentLanguage()),
                                durationMillis = run.durationMillis,
                            )
                        },
                    )
                    mutableModelComparison.update { state ->
                        state.copy(cards = state.cards.map { if (it.model == run.model) card else it })
                    }
                }
                mutableModelComparison.update { it.copy(isRunning = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                mutableModelComparison.update {
                    it.copy(
                        isRunning = false,
                        cards = emptyList(),
                        errorMessage = researchFailureMessage(error, currentLanguage()),
                    )
                }
            }
        }
    }

    /** Makes [model] the food research reader, through Exa + OpenRouter. */
    fun useResearchModel(model: String) {
        viewModelScope.launch {
            repository.appPreferencesStore.setProvider(
                ProviderPipeline.FOOD_RESEARCH,
                ProviderSelection(providerId = "exa-openrouter", model = model),
            )
            providers.refreshKeyPresence()
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
                    logging.clearCache()
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
                    logging.clearCache()
                    refreshProviderAndHealthStatus()
                    onResult(
                        true,
                        if (removed) "Stored API key removed" else "No stored API key was found",
                    )
                }
                .onFailure { error -> onResult(false, error.safeProviderSettingsMessage()) }
        }
    }

    /**
     * Checks the keys from the one-step key form and stores each one its provider accepts.
     * Onboarding and the AI page both end here. [onResult] names the field a failure belongs to
     * when that is known, so the form can mark it.
     */
    fun connectAiKeys(
        key: String,
        searchKey: String,
        onResult: (success: Boolean, message: String, failedField: AiKeyField?) -> Unit,
    ) {
        viewModelScope.launch {
            runCatching { providers.connectKeys(key, searchKey) }
                .onSuccess {
                    logging.clearCache()
                    providers.refreshKeyPresence()
                    onResult(true, "Connection successful", null)
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    // A first key may have been stored before the second one failed.
                    providers.refreshKeyPresence()
                    val check = error as? KeyCheckException
                    onResult(
                        false,
                        (check?.cause ?: error).safeProviderConnectionMessage(),
                        check?.field,
                    )
                }
        }
    }

    /** Looks up whether the provider a draft was just switched to already has its keys stored. */
    fun storedProviderKeys(
        index: Int,
        state: AiProviderEditorState,
        onResult: (primary: Boolean, search: Boolean) -> Unit,
    ) {
        viewModelScope.launch {
            val presence = providers.storedKeyPresence(pipelineAt(index), state)
            onResult(presence.primary, presence.search)
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
