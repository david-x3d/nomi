package com.nomi.app.ui.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.nomi.app.data.preferences.enabledMicronutrients
import com.nomi.app.ui.capture.BarcodeCaptureScreen
import com.nomi.app.ui.capture.MenuScanScreen
import com.nomi.app.ui.capture.PhotoCaptureScreen
import com.nomi.app.ui.capture.PhotoCaptureSubject
import com.nomi.app.ui.history.HistoryScreen
import com.nomi.app.ui.library.LibraryItemKind
import com.nomi.app.ui.library.LibraryScreen
import com.nomi.app.ui.profile.MicronutrientSettingsScreen
import com.nomi.app.ui.profile.NutritionPlanSettingsScreen
import com.nomi.app.ui.profile.ProfileSettingsScreen
import com.nomi.app.ui.settings.AiKeyEntryState
import com.nomi.app.ui.settings.AiKeyField
import com.nomi.app.ui.settings.AiSettingsScreen
import com.nomi.app.ui.today.AddFoodMethod

/**
 * The camera-backed ways of adding food: a plate, a nutrition label, a restaurant menu and a
 * barcode. Each one hands its result to the view model and returns to Today, where the entry
 * is confirmed in the same note as a typed meal.
 */
internal fun NavGraphBuilder.captureDestinations(
    navController: NavHostController,
    viewModel: AppViewModel,
    images: MealImageLoader,
) {
    fun typeInstead() {
        viewModel.beginLogging(AddFoodMethod.TYPE)
        navController.popBackStack(Routes.HOME, inclusive = false)
    }

    composable(Routes.PHOTO) {
        PhotoCaptureScreen(
            onBack = { navController.popBackStack() },
            onPhotoSelected = { uri, _ ->
                images.load(uri) { prepared ->
                    viewModel.analyzePhoto(prepared.bytes, prepared.mediaType)
                    // A photo returns to the page, the way voice does, so its result is
                    // confirmed in the same note as a typed meal instead of in the logging
                    // form with its meal-time picker.
                    navController.popBackStack(Routes.HOME, inclusive = false)
                }
            },
            onManualEntry = ::typeInstead,
        )
    }

    composable(Routes.LABEL) {
        PhotoCaptureScreen(
            subject = PhotoCaptureSubject.NUTRITION_LABEL,
            onBack = { navController.popBackStack() },
            onPhotoSelected = { uri, _ ->
                images.load(uri) { prepared ->
                    viewModel.analyzeNutritionLabel(prepared.bytes, prepared.mediaType)
                    // A label ends where a scanned barcode ends: in the amount sheet, because
                    // a printed table never says how much was eaten.
                    navController.popBackStack(Routes.HOME, inclusive = false)
                }
            },
            onManualEntry = ::typeInstead,
        )
    }

    composable(Routes.MENU_CAPTURE) {
        PhotoCaptureScreen(
            subject = PhotoCaptureSubject.MENU,
            onBack = { navController.popBackStack() },
            onPhotoSelected = { uri, _ ->
                images.load(uri) { prepared ->
                    viewModel.scanMenuPage(prepared.bytes, prepared.mediaType)
                    navController.navigate(Routes.MENU_RESULTS) {
                        popUpTo(Routes.MENU_CAPTURE) { inclusive = true }
                    }
                }
            },
            onManualEntry = ::typeInstead,
        )
    }

    composable(Routes.MENU_RESULTS) {
        val menuState by viewModel.menuScanState.collectAsStateWithLifecycle()
        MenuScanScreen(
            state = menuState,
            onBack = { navController.popBackStack() },
            onQueryChanged = viewModel::updateMenuSearch,
            onPhotoSelected = { uri, _ ->
                images.load(uri) { prepared ->
                    viewModel.scanMenuPage(prepared.bytes, prepared.mediaType)
                }
            },
            onToggleDish = viewModel::toggleMenuDish,
            onAddSelected = {
                viewModel.selectMenuDishes()
                navController.navigate(Routes.LOGGING) {
                    popUpTo(Routes.MENU_RESULTS) { inclusive = true }
                }
            },
        )
    }

    composable(Routes.BARCODE) {
        BarcodeCaptureScreen(
            onBack = { navController.popBackStack() },
            onBarcodeDetected = { barcode ->
                viewModel.lookupBarcode(barcode)
                navController.popBackStack(Routes.HOME, inclusive = false)
            },
            onManualEntry = ::typeInstead,
        )
    }
}

/**
 * The screens reached from Today, History and Settings that only read and edit stored data:
 * the library, history, one entry's details, and the settings sub-pages.
 */
internal fun NavGraphBuilder.detailDestinations(
    navController: NavHostController,
    viewModel: AppViewModel,
    libraryKind: () -> LibraryItemKind,
    providerSession: AiProviderEditorSession,
    onConnectHealth: () -> Unit,
) {
    composable(Routes.LIBRARY) {
        val libraryState by viewModel.libraryState.collectAsStateWithLifecycle()
        val pendingDeletions by viewModel.pendingLibraryDeletions.collectAsStateWithLifecycle()
        LibraryScreen(
            state = libraryState,
            initialKind = libraryKind(),
            onBack = { navController.popBackStack() },
            onAdd = viewModel::addLibraryItem,
            pendingDeletions = pendingDeletions,
            onDelete = viewModel::deleteLibraryItem,
            onUndoDelete = viewModel::undoLibraryDeletion,
        )
    }

    composable(Routes.HISTORY) {
        val historyState by viewModel.historyState.collectAsStateWithLifecycle()
        val historySelection by viewModel.historySelection.collectAsStateWithLifecycle()
        HistoryScreen(
            state = historyState,
            today = viewModel.currentDate,
            selection = historySelection,
            onQueryChanged = viewModel::setHistoryQuery,
            onDateSelected = viewModel::setHistoryDate,
            onFoodClick = { id -> navController.navigate(Routes.food(id)) },
            onCopyDay = { day -> viewModel.copyDayToToday(day.date) },
            onStartSelection = viewModel::startHistorySelection,
            onCancelSelection = viewModel::cancelHistorySelection,
            onToggleSelection = viewModel::toggleHistorySelection,
            onSaveMeal = { day, logIds, name ->
                viewModel.saveHistoryRowsAsMeal(day, logIds, name)
            },
            onAddToToday = { day, logIds ->
                viewModel.addHistoryRowsToToday(day, logIds)
            },
            onBack = { navController.popBackStack() },
        )
    }

    composable(
        route = Routes.FOOD,
        arguments = listOf(navArgument("id") { type = NavType.LongType }),
    ) { entry ->
        val preferences by viewModel.preferences.collectAsStateWithLifecycle()
        // Resolved straight from the database by id. The previous lookup scanned the
        // selected day's entries plus a 30-day history window, so opening a food older
        // than 30 days - which the Today day-pager lets a user reach - showed
        // "This entry is no longer available."
        val food by viewModel.foodDetail(entry.arguments?.getLong("id") ?: 0L)
            .collectAsStateWithLifecycle(initialValue = null)
        NomiFoodDetailScreen(
            entry = food,
            enabledMicronutrients = preferences.micronutrients.enabledMicronutrients(),
            onBack = { navController.popBackStack() },
            onDuplicate = viewModel::duplicateFoodLog,
            onFavorite = viewModel::favoriteFoodLog,
            onDelete = viewModel::deleteFoodLog,
            onEditAmount = viewModel::startLoggedAmountEdit,
        )
    }

    composable(Routes.PROFILE) {
        val profile by viewModel.profile.collectAsStateWithLifecycle()
        profile?.let { value ->
            ProfileSettingsScreen(
                profile = value,
                onBack = { navController.popBackStack() },
                onSave = { edit -> viewModel.saveProfile(edit); navController.popBackStack() },
            )
        } ?: LoadingPage()
    }

    composable(Routes.PLAN) {
        val plan by viewModel.currentPlan.collectAsStateWithLifecycle()
        plan?.let { value ->
            NutritionPlanSettingsScreen(
                plan = value,
                onBack = { navController.popBackStack() },
                onSave = { calories, protein, carbs, fat ->
                    viewModel.saveNutritionTargets(calories, protein, carbs, fat)
                    navController.popBackStack()
                },
            )
        } ?: LoadingPage()
    }

    composable(Routes.MICRONUTRIENTS) {
        val preferences by viewModel.preferences.collectAsStateWithLifecycle()
        MicronutrientSettingsScreen(
            preferences = preferences.micronutrients,
            onBack = { navController.popBackStack() },
            onSave = { updated ->
                viewModel.saveMicronutrientPreferences(updated)
                navController.popBackStack()
            },
        )
    }

    composable(Routes.HEALTH) {
        val settingsState by viewModel.settingsState.collectAsStateWithLifecycle()
        HealthConnectScreen(
            available = settingsState.healthConnectAvailable,
            connected = settingsState.healthConnectEnabled,
            health = settingsState.healthConnect,
            onBack = { navController.popBackStack() },
            onConnect = onConnectHealth,
            onSyncNow = viewModel::syncHealthConnect,
        )
    }

    composable(Routes.AI) {
        val settingsState by viewModel.settingsState.collectAsStateWithLifecycle()
        // The key being typed is held here and nowhere else: not in saved state, and not past
        // the moment it is stored.
        var keyEntry by remember { mutableStateOf(AiKeyEntryState()) }
        AiSettingsScreen(
            state = settingsState,
            keyEntry = keyEntry,
            onKeyChanged = { keyEntry = AiKeyEntryState(it, keyEntry.searchInput) },
            onSearchKeyChanged = { keyEntry = AiKeyEntryState(keyEntry.input, it) },
            onConnectKeys = {
                val typed = keyEntry
                keyEntry = AiKeyEntryState(typed.input, typed.searchInput, isChecking = true)
                viewModel.connectAiKeys(typed.input, typed.searchInput) { success, message, field ->
                    keyEntry = if (success) {
                        AiKeyEntryState(message = message)
                    } else {
                        AiKeyEntryState(
                            // A first key that passed before the second failed is stored
                            // already, so it is not left sitting in its field.
                            input = if (field == AiKeyField.SEARCH) "" else typed.input,
                            searchInput = typed.searchInput,
                            message = message,
                            failed = true,
                            failedField = field,
                        )
                    }
                }
            },
            onProvider = { index ->
                providerSession.open(index, viewModel.providerEditorState(index))
                navController.navigate(Routes.AI_PROVIDER)
            },
            onCalorieEstimateBiasChanged = viewModel::setCalorieEstimateBias,
            onAiRequestTimeoutDisabledChanged = viewModel::setAiRequestTimeoutDisabled,
            onExaFullPageTextChanged = viewModel::setExaFullPageText,
            onOpenRouterPreferredProviderChanged = viewModel::setOpenRouterPreferredProvider,
            onCompareModels = { navController.navigate(Routes.MODEL_COMPARE) },
            onDebug = { navController.navigate(Routes.DEVELOPER) },
            onBack = { navController.popBackStack() },
        )
    }

    composable(Routes.AI_PROVIDER) {
        AiProviderEditorPage(
            viewModel = viewModel,
            session = providerSession,
            onClose = { navController.popBackStack(Routes.AI, inclusive = false) },
        )
    }

    composable(Routes.MODEL_COMPARE) {
        val preferences by viewModel.preferences.collectAsStateWithLifecycle()
        val comparison by viewModel.modelComparison.collectAsStateWithLifecycle()
        val research = preferences.foodResearchProvider
        ModelComparisonScreen(
            state = comparison,
            fullPageText = preferences.exaFullPageText,
            researchModel = research.model.takeIf { research.usesExaSearch && !research.usesExaGemini },
            onInputChanged = viewModel::setModelComparisonInput,
            onToggleModel = viewModel::toggleComparedModel,
            onCustomModelChanged = viewModel::setCustomComparedModel,
            onAddCustomModel = viewModel::addCustomComparedModel,
            onCompare = viewModel::runModelComparison,
            onUseModel = viewModel::useResearchModel,
            onBack = { navController.popBackStack() },
        )
    }

    composable(Routes.DEVELOPER) {
        val preferences by viewModel.preferences.collectAsStateWithLifecycle()
        val debugEvents by viewModel.aiDebugEvents.collectAsStateWithLifecycle()
        DeveloperScreen(
            debugEnabled = preferences.aiDebugEnabled,
            events = debugEvents,
            onBack = { navController.popBackStack() },
            onDebugEnabledChanged = viewModel::setAiDebugEnabled,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoadingPage() = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    LoadingIndicator()
}

/** Every NavHost route. Today, Progress and Settings share [HOME]. */
internal object Routes {
    const val HOME = "home"
    const val LOGGING = "logging"
    const val PHOTO = "photo"
    const val MENU_CAPTURE = "menu_capture"
    const val MENU_RESULTS = "menu_results"
    const val LABEL = "label"
    const val BARCODE = "barcode"
    const val LIBRARY = "library"
    const val HISTORY = "history"
    const val FOOD = "food/{id}"
    const val PROFILE = "profile"
    const val PLAN = "plan"
    const val MICRONUTRIENTS = "micronutrients"
    const val HEALTH = "health"
    const val AI = "ai"
    const val AI_PROVIDER = "ai_provider"
    const val DEVELOPER = "developer"
    const val MODEL_COMPARE = "model_compare"
    fun food(id: Long) = "food/$id"
}
