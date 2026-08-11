package com.nomi.app.ui.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldValue
import androidx.compose.material3.adaptive.navigationsuite.rememberNavigationSuiteScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nomi.app.ai.model.AnalyzedFoodItem
import com.nomi.app.data.backup.BackupInspection
import com.nomi.app.data.preferences.AppPreferences
import com.nomi.app.data.preferences.CalorieEstimateBias
import com.nomi.app.data.preferences.GoalsCardStyle
import com.nomi.app.di.AppContainer
import com.nomi.app.integration.camera.MealImagePreprocessor
import com.nomi.app.integration.camera.deleteOwnedCameraCapture
import com.nomi.app.integration.health.HealthFeatures
import com.nomi.app.ui.capture.BarcodeCaptureScreen
import com.nomi.app.ui.capture.BarcodeAmountSheet
import com.nomi.app.ui.capture.PhotoCaptureScreen
import com.nomi.app.ui.capture.PhotoCaptureSubject
import com.nomi.app.ui.capture.MenuScanScreen
import com.nomi.app.ui.library.LibraryItemKind
import com.nomi.app.ui.library.LibraryScreen
import com.nomi.app.ui.logging.FoodLoggingScreen
import com.nomi.app.ui.logging.FoodLoggingUiState
import com.nomi.app.ui.logging.PortionEditSheet
import com.nomi.app.ui.localization.nomiString
import com.nomi.app.ui.onboarding.OnboardingRoute
import com.nomi.app.ui.profile.MicronutrientSettingsScreen
import com.nomi.app.ui.profile.NutritionPlanSettingsScreen
import com.nomi.app.ui.profile.ProfileSettingsScreen
import com.nomi.app.ui.progress.ProgressScreen
import com.nomi.app.ui.components.NomiDialog
import com.nomi.app.ui.settings.AiProviderEditorDialog
import com.nomi.app.ui.settings.AiProviderEditorState
import com.nomi.app.ui.settings.SettingsScreen
import com.nomi.app.ui.today.AddFoodMethod
import com.nomi.app.ui.today.LoggedAmountEditDialog
import com.nomi.app.ui.today.NomiNotesTodayScreen
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NomiRoot(
    container: AppContainer,
    viewModel: AppViewModel,
    modifier: Modifier = Modifier,
) {
    val startState by viewModel.startState.collectAsStateWithLifecycle()
    when (startState) {
        AppStartState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LoadingIndicator()
        }

        AppStartState.Onboarding -> OnboardingRoute(
            onComplete = viewModel::completeOnboarding,
            onDraftChanged = viewModel::persistOnboardingDraft,
            onMicronutrientsChanged = viewModel::saveMicronutrientPreferences,
            modifier = modifier,
        )

        AppStartState.Main -> NomiMain(container, viewModel, modifier)
    }
}

@Composable
private fun NomiMain(
    container: AppContainer,
    viewModel: AppViewModel,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val resolver = context.contentResolver
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val barcodeAmountState by viewModel.barcodeAmountState.collectAsStateWithLifecycle()
    val loggedAmountEditState by viewModel.loggedAmountEditState.collectAsStateWithLifecycle()

    var libraryKind by remember { mutableStateOf(LibraryItemKind.RECENT) }
    var selectedProviderIndex by remember { mutableIntStateOf(-1) }
    var providerEditor by remember { mutableStateOf<AiProviderEditorState?>(null) }
    var editedItemIndex by remember { mutableStateOf<Int?>(null) }
    var showWeightDialog by remember { mutableStateOf(false) }
    var backupInspection by remember { mutableStateOf<BackupInspection?>(null) }
    var pendingReminderIndex by remember { mutableStateOf<Int?>(null) }
    var menuAddingPage by remember { mutableStateOf(false) }

    fun showMessage(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                resolver.openOutputStream(uri, "wt")?.use { container.backupService.exportTo(it) }
                    ?: error("The selected document could not be opened")
            }.onSuccess { showMessage("Backup exported") }
                .onFailure { showMessage(it.message ?: "Nomi couldn't export the backup") }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                resolver.openInputStream(uri)?.use { container.backupService.inspect(it) }
                    ?: error("The selected document could not be opened")
            }.onSuccess { backupInspection = it }
                .onFailure { showMessage(it.message ?: "That isn't a valid Nomi backup") }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        pendingReminderIndex?.let { index ->
            if (granted) viewModel.toggleReminder(index, true)
            else showMessage("Notification permission is needed for reminders")
        }
        pendingReminderIndex = null
    }

    val healthPermissions = remember {
        container.healthConnect.permissionsFor(
            HealthFeatures(readWeight = true, writeWeight = true, readSteps = true, readActiveCalories = true),
        )
    }
    val healthPermissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { viewModel.refreshProviderAndHealthStatus() }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                AppEvent.FoodSaved -> navController.popBackStack(Routes.HOME, inclusive = false)
                AppEvent.OnboardingSaved -> Unit
                is AppEvent.Message -> snackbarHostState.showSnackbar(event.text)
            }
        }
    }

    Box(modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            enterTransition = {
                fadeIn(animationSpec = tween(220)) +
                    slideInHorizontally(animationSpec = tween(320)) { width -> width / 10 }
            },
            exitTransition = {
                fadeOut(animationSpec = tween(150)) +
                    scaleOut(animationSpec = tween(220), targetScale = 0.985f)
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(220)) +
                    scaleIn(animationSpec = tween(260), initialScale = 0.985f)
            },
            popExitTransition = {
                fadeOut(animationSpec = tween(160)) +
                    slideOutHorizontally(animationSpec = tween(280)) { width -> width / 10 }
            },
        ) {
            composable(Routes.HOME) {
                MainNavigationSuite(
                    viewModel = viewModel,
                    onPreviousDay = viewModel::previousDay,
                    onNextDay = viewModel::nextDay,
                    onToday = viewModel::selectToday,
                    onFoodClick = { navController.navigate(Routes.food(it)) },
                    onDeleteFood = viewModel::deleteFoodLogForUndo,
                    onUndoDeleteFood = viewModel::undoDeletedFoodLog,
                    onDiscardDeletedFood = viewModel::discardDeletedFoodLog,
                    onVoiceTranscription = { text ->
                        viewModel.beginLogging(AddFoodMethod.VOICE, text)
                        viewModel.analyzeText()
                    },
                    onAddFood = { method ->
                        when (method) {
                            AddFoodMethod.TYPE -> {
                                viewModel.beginLogging(method)
                            }
                            // Dictation happens in the bar at the bottom of the Today page,
                            // so there is nothing to navigate to.
                            AddFoodMethod.VOICE -> Unit
                            AddFoodMethod.PHOTO -> navController.navigate(Routes.PHOTO)
                            AddFoodMethod.MENU -> {
                                viewModel.beginMenuScan()
                                menuAddingPage = false
                                navController.navigate(Routes.MENU_CAPTURE)
                            }
                            AddFoodMethod.LABEL -> navController.navigate(Routes.LABEL)
                            AddFoodMethod.BARCODE -> navController.navigate(Routes.BARCODE)
                            AddFoodMethod.RECENT,
                            AddFoodMethod.FAVORITES,
                            AddFoodMethod.SAVED_MEALS,
                            -> {
                                libraryKind = when (method) {
                                    AddFoodMethod.RECENT -> LibraryItemKind.RECENT
                                    AddFoodMethod.FAVORITES -> LibraryItemKind.FAVORITE
                                    else -> LibraryItemKind.SAVED_MEAL
                                }
                                navController.navigate(Routes.LIBRARY)
                            }
                        }
                    },
                    onLoggingTextChanged = viewModel::updateLoggingText,
                    onAnalyzeLogging = viewModel::analyzeText,
                    onConfirmLogging = viewModel::confirmLogging,
                    onRetryLogging = viewModel::retryAnalysis,
                    onEditLoggingText = viewModel::editLoggingText,
                    onDismissLoggingDraft = viewModel::dismissLoggingDraft,
                    onEditLoggingPreview = { navController.navigate(Routes.LOGGING) },
                    onProgressRange = viewModel::setProgressRange,
                    onAddWeight = { showWeightDialog = true },
                    onTheme = viewModel::setTheme,
                    onDynamicColor = viewModel::setDynamicColor,
                    onGermanTranslation = viewModel::setGermanTranslationEnabled,
                    onUnits = viewModel::setUnits,
                    onActivityAdjustment = viewModel::setActivityAdjustment,
                    onCalorieEstimateBias = viewModel::setCalorieEstimateBias,
                    onGoalsCardStyle = viewModel::setGoalsCardStyle,
                    onReminderTime = viewModel::setReminderTime,
                    onProfile = { navController.navigate(Routes.PROFILE) },
                    onNutrition = { navController.navigate(Routes.PLAN) },
                    onMicronutrients = { navController.navigate(Routes.MICRONUTRIENTS) },
                    onAiProvider = { index ->
                        selectedProviderIndex = index
                        providerEditor = viewModel.providerEditorState(index)
                    },
                    onAiRequestTimeoutDisabled = viewModel::setAiRequestTimeoutDisabled,
                    onHealth = { navController.navigate(Routes.HEALTH) },
                    onReminder = { index, enabled ->
                        val needsPermission = enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        if (needsPermission) {
                            pendingReminderIndex = index
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else viewModel.toggleReminder(index, enabled)
                    },
                    onExport = { exportLauncher.launch("nomi-backup-${LocalDate.now()}.json") },
                    onImport = { importLauncher.launch(arrayOf("application/json", "text/json", "text/plain")) },
                    onDeveloper = { navController.navigate(Routes.DEVELOPER) },
                )
            }

            composable(Routes.LOGGING) {
                val loggingState by viewModel.loggingState.collectAsStateWithLifecycle()
                FoodLoggingScreen(
                    state = loggingState,
                    onBack = { navController.popBackStack() },
                    onTextChanged = viewModel::updateLoggingText,
                    onMealCategoryChanged = viewModel::updateLoggingMealCategory,
                    onAnalyze = viewModel::analyzeText,
                    onRetry = viewModel::retryAnalysis,
                    onManual = { viewModel.showManualLogging() },
                    onManualDraftChanged = viewModel::updateManualDraft,
                    onEditItem = { editedItemIndex = it },
                    onChangePortion = viewModel::beginPortionEdit,
                    onRemoveItem = viewModel::removePreviewItem,
                    onConfirm = viewModel::confirmLogging,
                    onPhotoDescriptionChanged = viewModel::updatePhotoDescription,
                    onPhotoPlaceChanged = viewModel::updatePhotoPlace,
                    onConfirmPhotoDescription = viewModel::confirmPhotoDescription,
                )
            }

            composable(Routes.PHOTO) {
                PhotoCaptureScreen(
                    onBack = { navController.popBackStack() },
                    onPhotoSelected = { uri, _ ->
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    try {
                                        resolver.openInputStream(uri)?.use(MealImagePreprocessor::prepare)
                                            ?: error("The selected image could not be opened")
                                    } finally {
                                        deleteOwnedCameraCapture(context.applicationContext, uri)
                                    }
                                }
                            }.onSuccess { prepared ->
                                viewModel.analyzePhoto(prepared.bytes, prepared.mediaType)
                                // A photo returns to the page, the way voice does, so its
                                // result is confirmed in the same note as a typed meal
                                // instead of in the logging form with its meal-time picker.
                                navController.popBackStack(Routes.HOME, inclusive = false)
                            }.onFailure { showMessage(it.message ?: "Nomi couldn't read that image") }
                        }
                    },
                    onManualEntry = {
                        viewModel.beginLogging(AddFoodMethod.TYPE)
                        navController.popBackStack(Routes.HOME, inclusive = false)
                    },
                )
            }

            composable(Routes.LABEL) {
                PhotoCaptureScreen(
                    subject = PhotoCaptureSubject.NUTRITION_LABEL,
                    onBack = { navController.popBackStack() },
                    onPhotoSelected = { uri, _ ->
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    try {
                                        resolver.openInputStream(uri)?.use(MealImagePreprocessor::prepare)
                                            ?: error("The selected image could not be opened")
                                    } finally {
                                        deleteOwnedCameraCapture(context.applicationContext, uri)
                                    }
                                }
                            }.onSuccess { prepared ->
                                viewModel.analyzeNutritionLabel(prepared.bytes, prepared.mediaType)
                                // A label ends where a scanned barcode ends: in the amount
                                // sheet, because a printed table never says how much was eaten.
                                navController.popBackStack(Routes.HOME, inclusive = false)
                            }.onFailure { showMessage(it.message ?: "Nomi couldn't read that image") }
                        }
                    },
                    onManualEntry = {
                        viewModel.beginLogging(AddFoodMethod.TYPE)
                        navController.popBackStack(Routes.HOME, inclusive = false)
                    },
                )
            }

            composable(Routes.MENU_CAPTURE) {
                PhotoCaptureScreen(
                    subject = PhotoCaptureSubject.MENU,
                    onBack = { navController.popBackStack() },
                    onPhotoSelected = { uri, _ ->
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    try {
                                        resolver.openInputStream(uri)?.use(MealImagePreprocessor::prepare)
                                            ?: error("The selected image could not be opened")
                                    } finally {
                                        deleteOwnedCameraCapture(context.applicationContext, uri)
                                    }
                                }
                            }.onSuccess { prepared ->
                                viewModel.scanMenuPage(prepared.bytes, prepared.mediaType)
                                if (menuAddingPage) {
                                    navController.popBackStack()
                                } else {
                                    navController.navigate(Routes.MENU_RESULTS) {
                                        popUpTo(Routes.MENU_CAPTURE) { inclusive = true }
                                    }
                                }
                                menuAddingPage = false
                            }.onFailure { showMessage(it.message ?: "Nomi couldn't read that image") }
                        }
                    },
                    onManualEntry = {
                        viewModel.beginLogging(AddFoodMethod.TYPE)
                        navController.popBackStack(Routes.HOME, inclusive = false)
                    },
                )
            }

            composable(Routes.MENU_RESULTS) {
                val menuState by viewModel.menuScanState.collectAsStateWithLifecycle()
                MenuScanScreen(
                    state = menuState,
                    onBack = { navController.popBackStack() },
                    onQueryChanged = viewModel::updateMenuSearch,
                    onAddPage = {
                        menuAddingPage = true
                        navController.navigate(Routes.MENU_CAPTURE)
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
                    onManualEntry = {
                        viewModel.beginLogging(AddFoodMethod.TYPE)
                        navController.popBackStack(Routes.HOME, inclusive = false)
                    },
                )
            }

            composable(Routes.LIBRARY) {
                val libraryState by viewModel.libraryState.collectAsStateWithLifecycle()
                LibraryScreen(
                    state = libraryState,
                    initialKind = libraryKind,
                    onBack = { navController.popBackStack() },
                    onAdd = viewModel::addLibraryItem,
                )
            }

            composable(
                route = Routes.FOOD,
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                val todayState by viewModel.todayState.collectAsStateWithLifecycle()
                val historyState by viewModel.historyState.collectAsStateWithLifecycle()
                val id = entry.arguments?.getLong("id")
                val food = (todayState.entries + historyState.visibleDays.flatMap { it.entries })
                    .firstOrNull { it.id == id }
                NomiFoodDetailScreen(
                    entry = food,
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
                    onConnect = { healthPermissionLauncher.launch(healthPermissions) },
                    onSyncNow = viewModel::syncHealthConnect,
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

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
        )
    }

    loggedAmountEditState?.let { state ->
        LoggedAmountEditDialog(
            state = state,
            onAmountChanged = viewModel::updateLoggedAmountInput,
            onCorrectionChanged = viewModel::updateLoggedAmountCorrection,
            onInterpretCorrection = viewModel::interpretLoggedAmountCorrection,
            onConfirm = viewModel::applyLoggedAmountEdit,
            onDismiss = viewModel::dismissLoggedAmountEdit,
        )
    }

    barcodeAmountState?.let { state ->
        BarcodeAmountSheet(
            state = state,
            onAmountChanged = viewModel::updateBarcodeAmount,
            onUnitChanged = viewModel::updateBarcodeUnit,
            onCalculate = viewModel::confirmBarcodeAmount,
            onDismiss = viewModel::cancelBarcodeAmount,
        )
    }

    providerEditor?.let { editor ->
        AiProviderEditorDialog(
            state = editor,
            onStateChanged = { providerEditor = it },
            onTestConnection = {
                providerEditor = editor.copy(isTesting = true, testResult = null)
                viewModel.testProvider(selectedProviderIndex, editor) { result ->
                    providerEditor = providerEditor?.copy(isTesting = false, testResult = result)
                }
            },
            onSave = {
                providerEditor = editor.copy(isSaving = true, errorMessage = null, testResult = null)
                viewModel.saveProvider(selectedProviderIndex, editor) { success, message ->
                    if (success) {
                        providerEditor = null
                    } else {
                        providerEditor = providerEditor?.copy(
                            isSaving = false,
                            errorMessage = message,
                        )
                    }
                }
            },
            onRemoveStoredKey = {
                providerEditor = editor.copy(
                    isRemovingKey = true,
                    errorMessage = null,
                    testResult = null,
                )
                viewModel.removeProviderKey(selectedProviderIndex, editor) { success, message ->
                    val current = providerEditor
                    if (current != null) {
                        providerEditor = current.copy(
                            isRemovingKey = false,
                            hasStoredApiKey = if (success) false else current.hasStoredApiKey,
                            apiKeyInput = if (success) "" else current.apiKeyInput,
                            testResult = if (success) message else null,
                            errorMessage = if (success) null else message,
                        )
                    }
                }
            },
            onDismiss = { providerEditor = null },
        )
    }

    LoggingEditingOverlays(
        viewModel = viewModel,
        editedItemIndex = editedItemIndex,
        onEditFinished = { editedItemIndex = null },
    )


    if (showWeightDialog) {
        WeightEntryDialog(
            onDismiss = { showWeightDialog = false },
            onSave = viewModel::addWeight,
        )
    }

    backupInspection?.let { inspection ->
        val summary = inspection.summary
        NomiDialog(
            onDismissRequest = { backupInspection = null },
            title = "Replace local Nomi data?",
            icon = Icons.Default.SettingsBackupRestore,
            subtitle = "API keys stay on this device and are never imported.",
            confirmLabel = "Replace data",
            destructive = true,
            onConfirm = {
                val confirmed = inspection
                backupInspection = null
                scope.launch {
                    runCatching { container.backupService.importValidated(confirmed) }
                        .onSuccess {
                            container.reminderScheduler.reconcileFrom(container.preferencesStore)
                            viewModel.refreshProviderAndHealthStatus()
                            showMessage("Backup restored")
                        }
                        .onFailure { showMessage(it.message ?: "Nomi couldn't restore that backup") }
                }
            },
            dismissLabel = "Cancel",
        ) {
            // The counts are what the decision turns on, so they are a readable list rather
            // than a comma-separated sentence to parse under a destructive button.
            BackupSummaryLine("Food logs", summary.foodLogCount)
            BackupSummaryLine("Foods", summary.foodCount)
            BackupSummaryLine("Saved meals", summary.savedMealCount)
            BackupSummaryLine("Weights", summary.weightEntryCount)
            BackupSummaryLine("Plans", summary.nutritionPlanCount)
        }
    }
}

/** One counted category from a validated backup envelope. */
@Composable
private fun BackupSummaryLine(label: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(count.toString(), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun LoggingEditingOverlays(
    viewModel: AppViewModel,
    editedItemIndex: Int?,
    onEditFinished: () -> Unit,
) {
    val loggingState by viewModel.loggingState.collectAsStateWithLifecycle()
    val portionEditState by viewModel.portionEditState.collectAsStateWithLifecycle()

    editedItemIndex?.let { index ->
        val preview = loggingState as? FoodLoggingUiState.Preview
        preview?.analysis?.items?.getOrNull(index)?.let { item: AnalyzedFoodItem ->
            AnalyzedItemEditDialog(
                item = item,
                onDismiss = onEditFinished,
                onSave = {
                    viewModel.updatePreviewItem(index, it)
                    onEditFinished()
                },
            )
        }
    }
    portionEditState?.let { state ->
        PortionEditSheet(
            state = state,
            onCorrectionChanged = viewModel::updatePortionCorrection,
            onInterpret = viewModel::interpretPortionCorrection,
            onApply = viewModel::applyPortionCorrection,
            onDismiss = viewModel::dismissPortionEdit,
            onResearch = viewModel::researchEditedItem,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MainNavigationSuite(
    viewModel: AppViewModel,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onToday: () -> Unit,
    onFoodClick: (Long) -> Unit,
    onDeleteFood: (Long) -> Unit,
    onUndoDeleteFood: (Long) -> Unit,
    onDiscardDeletedFood: (Long) -> Unit,
    onAddFood: (AddFoodMethod) -> Unit,
    onVoiceTranscription: (String) -> Unit,
    onLoggingTextChanged: (String) -> Unit,
    onAnalyzeLogging: () -> Unit,
    onConfirmLogging: () -> Unit,
    onRetryLogging: () -> Unit,
    onEditLoggingText: () -> Unit,
    onDismissLoggingDraft: () -> Unit,
    onEditLoggingPreview: () -> Unit,
    onProgressRange: (com.nomi.app.ui.progress.ProgressRange) -> Unit,
    onAddWeight: () -> Unit,
    onTheme: (com.nomi.app.ui.settings.ThemeMode) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onGermanTranslation: (Boolean) -> Unit,
    onUnits: (com.nomi.app.ui.settings.UnitSystem) -> Unit,
    onActivityAdjustment: (Boolean) -> Unit,
    onCalorieEstimateBias: (CalorieEstimateBias) -> Unit,
    onGoalsCardStyle: (GoalsCardStyle) -> Unit,
    onReminderTime: (index: Int, hour: Int, minute: Int) -> Unit,
    onProfile: () -> Unit,
    onNutrition: () -> Unit,
    onMicronutrients: () -> Unit,
    onAiProvider: (Int) -> Unit,
    onAiRequestTimeoutDisabled: (Boolean) -> Unit,
    onHealth: () -> Unit,
    onReminder: (Int, Boolean) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onDeveloper: () -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf(MainDestination.TODAY) }
    val destinationSpatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val destinationEffectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    // Keeping the navigation suite measured under the IME makes child imePadding count both
    // heights and leaves a navigation-bar-sized gap above the keyboard.
    val isImeVisible = WindowInsets.isImeVisible
    val navigationSuiteState = rememberNavigationSuiteScaffoldState(
        initialValue = if (isImeVisible) {
            NavigationSuiteScaffoldValue.Hidden
        } else {
            NavigationSuiteScaffoldValue.Visible
        },
    )

    LaunchedEffect(isImeVisible) {
        navigationSuiteState.snapTo(
            if (isImeVisible) {
                NavigationSuiteScaffoldValue.Hidden
            } else {
                NavigationSuiteScaffoldValue.Visible
            },
        )
    }

    BackHandler(enabled = selected != MainDestination.TODAY) { selected = MainDestination.TODAY }
    NavigationSuiteScaffold(
        state = navigationSuiteState,
        navigationSuiteItems = {
            MainDestination.entries.forEach { destination ->
                item(
                    selected = selected == destination,
                    onClick = { selected = destination },
                    icon = {
                        androidx.compose.material3.Icon(
                            destination.icon,
                            contentDescription = destination.localizedLabel(),
                        )
                    },
                    label = { Text(destination.localizedLabel()) },
                )
            }
        },
    ) {
        AnimatedContent(
            targetState = selected,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (
                    fadeIn(animationSpec = destinationEffectsSpec) +
                        slideInHorizontally(animationSpec = destinationSpatialSpec) { width ->
                            direction * (width / 24)
                        }
                    ).togetherWith(
                    fadeOut(animationSpec = destinationEffectsSpec) +
                        slideOutHorizontally(animationSpec = destinationSpatialSpec) { width ->
                            -direction * (width / 32)
                        },
                )
            },
            contentKey = { it },
            contentAlignment = Alignment.Center,
            label = "Main destination",
        ) { destination ->
        when (destination) {
            MainDestination.TODAY -> {
                val todayState by viewModel.todayState.collectAsStateWithLifecycle()
                val loggingState by viewModel.loggingState.collectAsStateWithLifecycle()
                val editedEntryId by viewModel.editedEntryId.collectAsStateWithLifecycle()
                NomiNotesTodayScreen(
                    state = todayState,
                    loggingState = loggingState,
                    onPreviousDay = onPreviousDay,
                    onNextDay = onNextDay,
                    onToday = onToday,
                    onFoodClick = onFoodClick,
                    onDeleteFood = onDeleteFood,
                    onUndoDeleteFood = onUndoDeleteFood,
                    onDiscardDeletedFood = onDiscardDeletedFood,
                    editedEntryId = editedEntryId,
                    onEditEntryText = viewModel::editEntryTextInline,
                    onTextChanged = onLoggingTextChanged,
                    onAnalyze = onAnalyzeLogging,
                    onConfirm = onConfirmLogging,
                    onRetry = onRetryLogging,
                    onEditText = onEditLoggingText,
                    onEditPreview = onEditLoggingPreview,
                    onDismissDraft = onDismissLoggingDraft,
                    onQuickMethod = onAddFood,
                    onVoiceTranscription = onVoiceTranscription,
                    onPhotoDescriptionChanged = viewModel::updatePhotoDescription,
                    onPhotoPlaceChanged = viewModel::updatePhotoPlace,
                    onConfirmPhotoDescription = viewModel::confirmPhotoDescription,
                )
            }
            MainDestination.PROGRESS -> {
                val progressState by viewModel.progressState.collectAsStateWithLifecycle()
                ProgressScreen(
                    state = progressState,
                    onRangeChanged = onProgressRange,
                    onAddWeight = onAddWeight,
                )
            }
            MainDestination.SETTINGS -> {
                val settingsState by viewModel.settingsState.collectAsStateWithLifecycle()
                SettingsScreen(
                    state = settingsState,
                    onThemeModeChanged = onTheme,
                    onDynamicColorChanged = onDynamicColor,
                    onGermanTranslationChanged = onGermanTranslation,
                    onUnitSystemChanged = onUnits,
                    onActivityTargetAdjustmentChanged = onActivityAdjustment,
                    onProfile = onProfile,
                    onNutrition = onNutrition,
                    onMicronutrients = onMicronutrients,
                    onAiProvider = onAiProvider,
                    onAiRequestTimeoutDisabledChanged = onAiRequestTimeoutDisabled,
                    onHealthConnect = onHealth,
                    onReminderChanged = onReminder,
                    onCalorieEstimateBiasChanged = onCalorieEstimateBias,
                    onGoalsCardStyleChanged = onGoalsCardStyle,
                    onReminderTimeChanged = onReminderTime,
                    onExport = onExport,
                    onImport = onImport,
                    onDeveloper = onDeveloper,
                )
            }
        }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoadingPage() = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    LoadingIndicator()
}

private enum class MainDestination(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    TODAY(Icons.Default.Today),
    PROGRESS(Icons.Default.Insights),
    SETTINGS(Icons.Default.Settings),
}

@Composable
private fun MainDestination.localizedLabel(): String = when (this) {
    MainDestination.TODAY -> nomiString("Today", "Heute")
    MainDestination.PROGRESS -> nomiString("Progress", "Fortschritt")
    MainDestination.SETTINGS -> nomiString("Settings", "Einstellungen")
}

private object Routes {
    const val HOME = "home"
    const val LOGGING = "logging"
    const val PHOTO = "photo"
    const val MENU_CAPTURE = "menu_capture"
    const val MENU_RESULTS = "menu_results"
    const val LABEL = "label"
    const val BARCODE = "barcode"
    const val LIBRARY = "library"
    const val FOOD = "food/{id}"
    const val PROFILE = "profile"
    const val PLAN = "plan"
    const val MICRONUTRIENTS = "micronutrients"
    const val HEALTH = "health"
    const val DEVELOPER = "developer"
    fun food(id: Long) = "food/$id"
}
