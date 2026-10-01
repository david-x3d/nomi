package com.nomi.app.ui.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nomi.app.data.backup.BackupInspection
import com.nomi.app.data.preferences.WeightUnitPreference
import com.nomi.app.di.AppContainer
import com.nomi.app.integration.health.NomiHealthFeatures
import com.nomi.app.integration.nfc.NomiShareReader
import com.nomi.app.ui.capture.BarcodeAmountSheet
import com.nomi.app.ui.capture.PhotoCaptureSubject
import com.nomi.app.ui.feedback.rememberNomiHaptics
import com.nomi.app.ui.library.LibraryItemKind
import com.nomi.app.ui.localization.LocalNomiLanguage
import com.nomi.app.ui.localization.NomiTranslations
import com.nomi.app.ui.localization.nomiString
import com.nomi.app.ui.logging.FoodLoggingScreen
import com.nomi.app.ui.onboarding.OnboardingRoute
import com.nomi.app.ui.share.LocalNomiShareCoordinator
import com.nomi.app.ui.share.NomiShareCoordinator
import com.nomi.app.ui.theme.nomiFadeMotionSpec
import com.nomi.app.ui.theme.nomiPageMotionSpec
import com.nomi.app.ui.today.AddFoodMethod
import com.nomi.app.ui.today.LoggedAmountEditDialog
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NomiRoot(
    container: AppContainer,
    viewModel: AppViewModel,
    modifier: Modifier = Modifier,
) {
    val startState by viewModel.startState.collectAsStateWithLifecycle()
    // One share coordinator for the whole interface, above the start state, because a tap is one
    // continuous action rather than something a screen owns: the user can start receiving from one
    // row's menu and scroll the day out from under it, and the phone has to stay in the same role
    // while they do. The reader needs an activity and the diary is written by the ViewModel, so
    // both are handed in rather than reached for.
    val context = LocalContext.current
    val shareScope = rememberCoroutineScope()
    val shareCoordinator = remember(viewModel) {
        NomiShareCoordinator(
            reader = context.findActivity()?.let { NomiShareReader(it) },
            scope = shareScope,
            onAddToDiary = { viewModel.importSharedDay(it) },
        )
    }
    val shareLifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.DisposableEffect(shareCoordinator, shareLifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE &&
                (shareCoordinator.isSending || shareCoordinator.isReceiving)
            ) shareCoordinator.collapse()
        }
        shareLifecycle.addObserver(observer)
        onDispose {
            shareLifecycle.removeObserver(observer)
            shareCoordinator.collapse()
        }
    }
    CompositionLocalProvider(LocalNomiShareCoordinator provides shareCoordinator) {
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

            AppStartState.Main -> NomiMain(
                container = container,
                viewModel = viewModel,
                shareCoordinator = shareCoordinator,
                modifier = modifier,
            )
        }
    }
}

/**
 * The activity behind a context.
 *
 * NFC reading has to be started from an activity, and the one in scope during composition is
 * usually a wrapper rather than the activity itself, so the chain is unwrapped. Null when there is
 * no activity, which leaves sharing unavailable rather than crashing on a screen nobody is looking
 * at - there is no tap to be read from in that state anyway.
 */
internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun NomiMain(
    container: AppContainer,
    viewModel: AppViewModel,
    shareCoordinator: NomiShareCoordinator,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val haptics = rememberNomiHaptics()
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val barcodeAmountState by viewModel.barcodeAmountState.collectAsStateWithLifecycle()
    val loggedAmountEditState by viewModel.loggedAmountEditState.collectAsStateWithLifecycle()
    val notificationPermissionMessage = nomiString("Notification permission is needed for reminders")

    var libraryKind by rememberSaveable { mutableStateOf(LibraryItemKind.RECENT) }
    val providerSession = remember { AiProviderEditorSession() }
    var editedItemIndex by remember { mutableStateOf<Int?>(null) }
    var showWeightDialog by remember { mutableStateOf(false) }
    var backupInspection by remember { mutableStateOf<BackupInspection?>(null) }
    var pendingReminderIndex by remember { mutableStateOf<Int?>(null) }

    // Messages arrive already composed, often from outside any composition, so they are put
    // into the chosen language here, at the one place every snackbar passes through.
    val language by rememberUpdatedState(LocalNomiLanguage.current)
    fun showMessage(message: String) {
        scope.launch {
            snackbarHostState.showSnackbar(NomiTranslations.localizeMessage(message, language))
        }
    }

    val images = rememberMealImageLoader(::showMessage)
    val backup = rememberBackupActions(
        container = container,
        showMessage = ::showMessage,
        onInspected = { backupInspection = it },
        onRestored = viewModel::refreshProviderAndHealthStatus,
    )

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        pendingReminderIndex?.let { index ->
            if (granted) viewModel.toggleReminder(index, true)
            else showMessage(notificationPermissionMessage)
        }
        pendingReminderIndex = null
    }

    // Asks from the same feature definition the sync uses, including optional history access when
    // this provider supports it. Keeping one source of truth prevents a newly added category from
    // making the button appear dead while the connection remains incomplete.
    val healthConnectAvailability = container.healthConnect.availability
    val healthPermissions = remember(healthConnectAvailability) {
        container.healthConnect.permissionsFor(NomiHealthFeatures)
    }
    val healthPermissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { viewModel.healthConnectPermissionsChanged() }

    ShareTapOverlays(shareCoordinator, ::showMessage)

    LaunchedEffect(viewModel) {
        viewModel.onMainVisible()
        viewModel.events.collectLatest { event ->
            when (event) {
                AppEvent.FoodSaved -> {
                    haptics.confirmed()
                    navController.popBackStack(Routes.HOME, inclusive = false)
                }
                AppEvent.OnboardingSaved -> Unit
                is AppEvent.Message -> snackbarHostState.showSnackbar(
                    NomiTranslations.localizeMessage(event.text, language),
                )
            }
        }
    }

    val launcherShortcut by viewModel.launcherShortcut.collectAsStateWithLifecycle()
    LaunchedEffect(launcherShortcut) {
        when (launcherShortcut) {
            LauncherShortcut.PHOTO -> {
                navController.navigate(Routes.PHOTO)
                viewModel.clearLauncherShortcut()
            }
            LauncherShortcut.MENU -> {
                navController.navigate(Routes.MENU_CAPTURE)
                viewModel.clearLauncherShortcut()
            }
            null -> Unit
        }
    }

    Box(modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            enterTransition = {
                fadeIn(animationSpec = nomiFadeMotionSpec()) +
                    slideInHorizontally(animationSpec = nomiPageMotionSpec()) { width -> width / 16 }
            },
            exitTransition = {
                fadeOut(animationSpec = nomiFadeMotionSpec())
            },
            popEnterTransition = {
                fadeIn(animationSpec = nomiFadeMotionSpec())
            },
            popExitTransition = {
                fadeOut(animationSpec = nomiFadeMotionSpec()) +
                    slideOutHorizontally(animationSpec = nomiPageMotionSpec()) { width -> width / 16 }
            },
        ) {
            composable(Routes.HOME) {
                MainNavigationSuite(
                    viewModel = viewModel,
                    onPreviousDay = viewModel::previousDay,
                    onNextDay = viewModel::nextDay,
                    onToday = viewModel::selectToday,
                    onOpenHistory = { navController.navigate(Routes.HISTORY) },
                    onFoodClick = { navController.navigate(Routes.food(it)) },
                    onDeleteFood = viewModel::deleteFoodLogForUndo,
                    onDeleteFoodImmediately = viewModel::deleteFoodLog,
                    onUndoDeleteFood = viewModel::undoDeletedFoodLog,
                    onDiscardDeletedFood = viewModel::discardDeletedFoodLog,
                    onDuplicateFood = viewModel::duplicateFoodLog,
                    onFavoriteFood = viewModel::favoriteFoodLog,
                    onEditFoodAmount = viewModel::startLoggedAmountEdit,
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
                            AddFoodMethod.MENU -> viewModel.beginMenuScan()
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
                    onInlinePhotoSelected = { uri, _, subject ->
                        images.load(uri) { prepared ->
                            when (subject) {
                                PhotoCaptureSubject.MEAL ->
                                    viewModel.analyzePhoto(prepared.bytes, prepared.mediaType)
                                PhotoCaptureSubject.NUTRITION_LABEL ->
                                    viewModel.analyzeNutritionLabel(prepared.bytes, prepared.mediaType)
                                PhotoCaptureSubject.MENU -> {
                                    viewModel.scanMenuPage(prepared.bytes, prepared.mediaType)
                                    navController.navigate(Routes.MENU_RESULTS)
                                }
                            }
                        }
                    },
                    onInlineBarcodeDetected = viewModel::lookupBarcode,
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
                    onLanguage = viewModel::setLanguage,
                    onUnits = viewModel::setUnits,
                    onActivityAdjustment = viewModel::setActivityAdjustment,
                    onCalorieEstimateBias = viewModel::setCalorieEstimateBias,
                    onGoalsCardStyle = viewModel::setGoalsCardStyle,
                    onReminderTime = viewModel::setReminderTime,
                    onProfile = { navController.navigate(Routes.PROFILE) },
                    onNutrition = { navController.navigate(Routes.PLAN) },
                    onMicronutrients = { navController.navigate(Routes.MICRONUTRIENTS) },
                    onAiProvider = { index ->
                        providerSession.open(index, viewModel.providerEditorState(index))
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
                    onExport = backup.export,
                    onExportDiary = backup.exportDiary,
                    onImport = backup.import,
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

            captureDestinations(navController, viewModel, images)
            detailDestinations(
                navController = navController,
                viewModel = viewModel,
                libraryKind = { libraryKind },
                onConnectHealth = { healthPermissionLauncher.launch(healthPermissions) },
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
        )
    }

    UpdateDialogHost(viewModel)

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

    AiProviderEditorHost(viewModel, providerSession)

    LoggingEditingOverlays(
        viewModel = viewModel,
        editedItemIndex = editedItemIndex,
        onEditFinished = { editedItemIndex = null },
    )

    if (showWeightDialog) {
        val preferences by viewModel.preferences.collectAsStateWithLifecycle()
        WeightEntryDialog(
            metric = preferences.weightUnit == WeightUnitPreference.KILOGRAMS,
            onDismiss = { showWeightDialog = false },
            onSave = viewModel::addWeight,
        )
    }

    backupInspection?.let { inspection ->
        BackupRestoreDialog(
            inspection = inspection,
            onConfirm = {
                backupInspection = null
                backup.restore(inspection)
            },
            onDismiss = { backupInspection = null },
        )
    }
}
