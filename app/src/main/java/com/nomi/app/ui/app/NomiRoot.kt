package com.nomi.app.ui.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
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
import com.nomi.app.data.preferences.WeightUnitPreference
import com.nomi.app.data.preferences.CalorieEstimateBias
import com.nomi.app.data.preferences.GoalsCardStyle
import com.nomi.app.data.preferences.enabledMicronutrients
import com.nomi.app.di.AppContainer
import com.nomi.app.integration.camera.MealImagePreprocessor
import com.nomi.app.integration.camera.deleteOwnedCameraCapture
import com.nomi.app.integration.health.NomiHealthFeatures
import com.nomi.app.ui.capture.BarcodeAmountSheet
import com.nomi.app.ui.capture.BarcodeCaptureScreen
import com.nomi.app.ui.capture.MenuScanScreen
import com.nomi.app.ui.capture.PhotoCaptureScreen
import com.nomi.app.ui.capture.PhotoCaptureSubject
import com.nomi.app.ui.components.NomiDialog
import com.nomi.app.R
import com.nomi.app.ui.update.UpdateAvailableDialog
import com.nomi.app.update.UpdateAvailability
import com.nomi.app.ui.feedback.rememberNomiHaptics
import com.nomi.app.ui.history.HistoryScreen
import com.nomi.app.ui.library.LibraryItemKind
import com.nomi.app.ui.library.LibraryScreen
import com.nomi.app.ui.localization.NomiLanguage
import com.nomi.app.ui.localization.fillTemplate
import com.nomi.app.ui.localization.nomiString
import com.nomi.app.data.share.ShareReceiveFailure
import com.nomi.app.integration.nfc.NomiShareReader
import com.nomi.app.ui.share.LocalNomiShareCoordinator
import com.nomi.app.ui.share.NomiShareCoordinator
import com.nomi.app.ui.share.NomiShareEvent
import com.nomi.app.ui.share.ShareReceivedDialog
import com.nomi.app.ui.share.ShareReceivingDialog
import com.nomi.app.ui.share.ShareSendingDialog
import com.nomi.app.ui.logging.FoodLoggingScreen
import com.nomi.app.ui.logging.FoodLoggingUiState
import com.nomi.app.ui.logging.PortionEditSheet
import com.nomi.app.ui.onboarding.OnboardingRoute
import com.nomi.app.ui.profile.MicronutrientSettingsScreen
import com.nomi.app.ui.profile.NutritionPlanSettingsScreen
import com.nomi.app.ui.profile.ProfileSettingsScreen
import com.nomi.app.ui.progress.ProgressScreen
import com.nomi.app.ui.theme.nomiFadeMotionSpec
import com.nomi.app.ui.theme.nomiPageMotionSpec
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
    val resolver = context.contentResolver
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val barcodeAmountState by viewModel.barcodeAmountState.collectAsStateWithLifecycle()
    val loggedAmountEditState by viewModel.loggedAmountEditState.collectAsStateWithLifecycle()
    val selectedDocumentOpenError = nomiString("The selected document could not be opened")
    val backupExportedMessage = nomiString("Backup exported")
    val backupExportFailedMessage = nomiString("Nomi couldn't export the backup")
    val diaryExportedMessage = nomiString("Diary exported")
    val diaryExportFailedMessage = nomiString("Nomi couldn't export the diary")
    val invalidBackupMessage = nomiString("That isn't a valid Nomi backup")
    val notificationPermissionMessage = nomiString("Notification permission is needed for reminders")
    val backupRestoredMessage = nomiString("Backup restored")
    val backupRestoreFailedMessage = nomiString("Nomi couldn't restore that backup")

    var libraryKind by rememberSaveable { mutableStateOf(LibraryItemKind.RECENT) }
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
                    ?: error(selectedDocumentOpenError)
            }.onSuccess { showMessage(backupExportedMessage) }
                .onFailure { showMessage(it.message ?: backupExportFailedMessage) }
        }
    }
    val diaryExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                resolver.openOutputStream(uri, "wt")?.use { container.diaryExportService.exportTo(it) }
                    ?: error(selectedDocumentOpenError)
            }.onSuccess { showMessage(diaryExportedMessage) }
                .onFailure { showMessage(it.message ?: diaryExportFailedMessage) }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                resolver.openInputStream(uri)?.use { container.backupService.inspect(it) }
                    ?: error(selectedDocumentOpenError)
            }.onSuccess { backupInspection = it }
                .onFailure { showMessage(it.message ?: invalidBackupMessage) }
        }
    }

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

    // A finished share is reported as a message rather than left on screen, because the tap
    // screens have already been dismissed by the time the outcome is known. The wording is
    // resolved here, where the user's language is available, and the coordinator only names what
    // happened.
    val shareNoNfcMessage = nomiString("This phone has no NFC")
    val shareCannotEmulateMessage = nomiString("This phone can receive NFC shares but cannot send them")
    val shareNfcOffMessage = nomiString("Turn NFC on to share")
    val shareNothingSelectedMessage = nomiString("Tick at least one food to share")
    val shareNoTagMessage = nomiString("Nomi couldn't find a phone to read")
    val shareTransferFailedMessage = nomiString("The share was interrupted, try holding them closer")
    val shareCorruptedMessage = nomiString("That share arrived damaged, try again")
    val shareIncompatibleMessage = nomiString("That phone isn't a Nomi this version can read")
    val shareNotAShareMessage = nomiString("That wasn't a shared day")
    val shareOfferedMessage = nomiString("Held for the other phone to read")
    val shareAddedTemplate = nomiString("Added {0} shared foods to your day")

    fun shareMessageText(event: NomiShareEvent): String = when (event) {
        NomiShareEvent.Offered -> shareOfferedMessage
        is NomiShareEvent.Added -> fillTemplate(shareAddedTemplate, arrayOf<Any?>(event.foodCount))
        NomiShareEvent.NoNfcHardware -> shareNoNfcMessage
        NomiShareEvent.NfcTurnedOff -> shareNfcOffMessage
        NomiShareEvent.CannotEmulate -> shareCannotEmulateMessage
        NomiShareEvent.NothingSelected -> shareNothingSelectedMessage
        // The four ways a tap fails each get their own sentence. One "sharing failed" would send
        // the user off to guess, and the fixes are nothing alike: move the phones, hold still,
        // update the app, or the other phone simply had nothing to offer.
        is NomiShareEvent.Failed -> when (event.failure) {
            ShareReceiveFailure.NoTagFound -> shareNoTagMessage
            ShareReceiveFailure.TransferFailed -> shareTransferFailedMessage
            ShareReceiveFailure.Corrupted -> shareCorruptedMessage
            ShareReceiveFailure.Incompatible -> shareIncompatibleMessage
            ShareReceiveFailure.NotAShare -> shareNotAShareMessage
        }
    }

    LaunchedEffect(shareCoordinator) {
        shareCoordinator.events.collectLatest { event -> showMessage(shareMessageText(event)) }
    }

    // The three tap screens are dialogs rather than destinations because none of them is a place
    // the user can be: they are the phone waiting to be touched, and a tap that walks away from
    // them has to be able to come back to the day rather than to a blank screen.
    val offerState = shareCoordinator.offered
    if (offerState != null) {
        ShareSendingDialog(
            foodCount = offerState.foodCount,
            kcal = offerState.kcal,
            onCancel = shareCoordinator::collapse,
        )
    }
    if (shareCoordinator.isReceiving) {
        ShareReceivingDialog(
            receivedBytes = shareCoordinator.receivedBytes,
            expectedBytes = shareCoordinator.expectedBytes,
            onCancel = shareCoordinator::collapse,
        )
    }
    shareCoordinator.received?.let { envelope ->
        ShareReceivedDialog(
            envelope = envelope,
            onAdd = shareCoordinator::addReceivedToDiary,
            onDiscard = shareCoordinator::discardReceived,
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.onMainVisible()
        viewModel.events.collectLatest { event ->
            when (event) {
                AppEvent.FoodSaved -> {
                    haptics.confirmed()
                    navController.popBackStack(Routes.HOME, inclusive = false)
                }
                AppEvent.OnboardingSaved -> Unit
                is AppEvent.Message -> snackbarHostState.showSnackbar(event.text)
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
                menuAddingPage = false
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
                            AddFoodMethod.MENU -> {
                                viewModel.beginMenuScan()
                                menuAddingPage = false
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
                    onInlinePhotoSelected = { uri, _, subject ->
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
                            }.onFailure {
                                showMessage(it.message ?: "Nomi couldn't read that image")
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
                    onExportDiary = { diaryExportLauncher.launch("nomi-diary-${LocalDate.now()}.json") },
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
                                menuAddingPage = false
                            }.onFailure {
                                menuAddingPage = false
                                showMessage(it.message ?: "Nomi couldn't read that image")
                            }
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

    // The update check starts after the first frame has been produced, so the dialog can never be
    // part of what the user waits for at launch. The ViewModel makes the check itself
    // idempotent, so a recomposition or a rotation does not repeat the request.
    val updateAvailability by viewModel.update.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        withFrameNanos { }
        viewModel.checkForUpdate()
    }
    // In a debug build the dialog can be forced on by a build-time resource, so it can be
    // screenshotted without publishing a release first. `rememberForcedUpdateAvailability`
    // returns null in a release build, so the real check below is the only thing a shipped APK
    // can show.
    //
    // The forced value has to be dismissed through composition state, not through
    // `viewModel.dismissUpdate()`: that clears the ViewModel's `update` flow, which the forced
    // dialog never went through, so tapping "Later" would have done nothing and the debug build
    // would be stuck behind a dialog that cannot be closed.
    var forcedUpdateDismissed by rememberSaveable { mutableStateOf(false) }
    val forcedUpdate = rememberForcedUpdateAvailability()?.takeUnless { forcedUpdateDismissed }
    (forcedUpdate ?: updateAvailability as? UpdateAvailability.Available)?.let { available ->
        val dismiss = {
            forcedUpdateDismissed = true
            viewModel.dismissUpdate()
        }
        UpdateAvailableDialog(
            availability = available,
            onViewUpdate = {
                dismiss()
                context.openReleasePage(available.releaseUrl)
            },
            onDismiss = dismiss,
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
        val preferences by viewModel.preferences.collectAsStateWithLifecycle()
        WeightEntryDialog(
            metric = preferences.weightUnit == WeightUnitPreference.KILOGRAMS,
            onDismiss = { showWeightDialog = false },
            onSave = viewModel::addWeight,
        )
    }

    backupInspection?.let { inspection ->
        val summary = inspection.summary
        NomiDialog(
            onDismissRequest = { backupInspection = null },
            title = nomiString("Replace local Nomi data?"),
            icon = Icons.Default.SettingsBackupRestore,
            subtitle = nomiString("API keys stay on this device and are never imported."),
            confirmLabel = nomiString("Replace data"),
            destructive = true,
            onConfirm = {
                val confirmed = inspection
                backupInspection = null
                scope.launch {
                    runCatching { container.backupService.importValidated(confirmed) }
                        .onSuccess {
                            container.reminderScheduler.reconcileFrom(container.preferencesStore)
                            viewModel.refreshProviderAndHealthStatus()
                            showMessage(backupRestoredMessage)
                        }
                        .onFailure { showMessage(it.message ?: backupRestoreFailedMessage) }
                }
            },
            dismissLabel = nomiString("Cancel"),
        ) {
            // The counts are what the decision turns on, so they are a readable list rather
            // than a comma-separated sentence to parse under a destructive button.
            BackupSummaryLine(nomiString("Food logs"), summary.foodLogCount)
            BackupSummaryLine(nomiString("Foods"), summary.foodCount)
            BackupSummaryLine(nomiString("Saved meals"), summary.savedMealCount)
            BackupSummaryLine(nomiString("Weights"), summary.weightEntryCount)
            BackupSummaryLine(nomiString("Plans"), summary.nutritionPlanCount)
        }
    }
}

/**
 * Opens the exact release page in the user's browser.
 *
 * The repository homepage is deliberately never substituted: a user who tapped "View update"
 * wants the release, not a page they then have to navigate. `runCatching` because a device with
 * no browser at all should do nothing rather than crash.
 */
private fun Context.openReleasePage(url: String) {
    runCatching {
        startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
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
    onOpenHistory: () -> Unit,
    onFoodClick: (Long) -> Unit,
    onDeleteFood: (Long) -> Unit,
    onDeleteFoodImmediately: (Long) -> Unit,
    onUndoDeleteFood: (Long) -> Unit,
    onDiscardDeletedFood: (Long) -> Unit,
    onDuplicateFood: (Long) -> Unit,
    onFavoriteFood: (Long) -> Unit,
    onEditFoodAmount: (com.nomi.app.ui.today.TodayFoodEntry) -> Unit,
    onAddFood: (AddFoodMethod) -> Unit,
    onVoiceTranscription: (String) -> Unit,
    onInlinePhotoSelected: (android.net.Uri, String, PhotoCaptureSubject) -> Unit,
    onInlineBarcodeDetected: (String) -> Unit,
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
    onLanguage: (NomiLanguage) -> Unit,
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
    onExportDiary: () -> Unit,
    onImport: () -> Unit,
    onDeveloper: () -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf(MainDestination.TODAY) }
    val haptics = rememberNomiHaptics()
    val destinationStateHolder = rememberSaveableStateHolder()
    val destinationSpatialSpec = nomiPageMotionSpec<IntOffset>()
    val destinationEffectsSpec = nomiFadeMotionSpec<Float>()
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
                    onClick = {
                        if (selected != destination) haptics.selected() else haptics.tapped()
                        selected = destination
                    },
                    icon = {
                        val iconScale by animateFloatAsState(
                            targetValue = if (selected == destination) 1.08f else 1f,
                            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                            label = "Selected destination",
                        )
                        androidx.compose.material3.Icon(
                            imageVector = if (selected == destination) {
                                destination.selectedIcon
                            } else {
                                destination.unselectedIcon
                            },
                            // The adjacent NavigationSuite label already names this destination.
                            contentDescription = null,
                            modifier = Modifier.graphicsLayer {
                                scaleX = iconScale
                                scaleY = iconScale
                            },
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
                            direction * (width / 32)
                        }
                    ).togetherWith(
                    fadeOut(animationSpec = destinationEffectsSpec) +
                        slideOutHorizontally(animationSpec = destinationSpatialSpec) { width ->
                            -direction * (width / 40)
                        },
                ).using(
                    SizeTransform(
                        // Keep a sliding destination inside the content slot on rail layouts.
                        clip = true,
                        sizeAnimationSpec = { _, _ -> snap() },
                    ),
                )
            },
            contentKey = { it },
            contentAlignment = Alignment.Center,
            label = "Main destination",
        ) { destination ->
            destinationStateHolder.SaveableStateProvider(destination.name) {
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
                    onOpenHistory = onOpenHistory,
                    onFoodClick = onFoodClick,
                    onDeleteFood = onDeleteFood,
                    onDeleteFoodImmediately = onDeleteFoodImmediately,
                    onUndoDeleteFood = onUndoDeleteFood,
                    onDiscardDeletedFood = onDiscardDeletedFood,
                    onDuplicateFood = onDuplicateFood,
                    onFavoriteFood = onFavoriteFood,
                    onEditFoodAmount = onEditFoodAmount,
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
                    onInlinePhotoSelected = onInlinePhotoSelected,
                    onInlineBarcodeDetected = onInlineBarcodeDetected,
                    onVoiceTranscription = onVoiceTranscription,
                    onPhotoDescriptionChanged = viewModel::updatePhotoDescription,
                    onPhotoPlaceChanged = viewModel::updatePhotoPlace,
                    onConfirmPhotoDescription = viewModel::confirmPhotoDescription,
                )
            }
                    MainDestination.PROGRESS -> {
                val progressState by viewModel.progressState.collectAsStateWithLifecycle()
                val preferences by viewModel.preferences.collectAsStateWithLifecycle()
                ProgressScreen(
                    state = progressState,
                    metric = preferences.weightUnit == WeightUnitPreference.KILOGRAMS,
                    onRangeChanged = { haptics.toggled(); onProgressRange(it) },
                    onAddWeight = { haptics.selected(); onAddWeight() },
                )
            }
                    MainDestination.SETTINGS -> {
                val settingsState by viewModel.settingsState.collectAsStateWithLifecycle()
                SettingsScreen(
                    state = settingsState,
                    onThemeModeChanged = { haptics.toggled(); onTheme(it) },
                    onDynamicColorChanged = { haptics.toggled(); onDynamicColor(it) },
                    onLanguageChanged = { haptics.toggled(); onLanguage(it) },
                    onUnitSystemChanged = { haptics.toggled(); onUnits(it) },
                    onActivityTargetAdjustmentChanged = { haptics.toggled(); onActivityAdjustment(it) },
                    onProfile = { haptics.selected(); onProfile() },
                    onNutrition = { haptics.selected(); onNutrition() },
                    onMicronutrients = { haptics.selected(); onMicronutrients() },
                    onAiProvider = { haptics.selected(); onAiProvider(it) },
                    onAiRequestTimeoutDisabledChanged = { haptics.toggled(); onAiRequestTimeoutDisabled(it) },
                    onHealthConnect = { haptics.selected(); onHealth() },
                    onReminderChanged = { index, enabled -> haptics.toggled(); onReminder(index, enabled) },
                    onCalorieEstimateBiasChanged = { haptics.toggled(); onCalorieEstimateBias(it) },
                    onGoalsCardStyleChanged = { haptics.toggled(); onGoalsCardStyle(it) },
                    onReminderTimeChanged = { index, hour, minute ->
                        haptics.confirmed(); onReminderTime(index, hour, minute)
                    },
                    onExport = { haptics.selected(); onExport() },
                    onExportDiary = { haptics.selected(); onExportDiary() },
                    onImport = { haptics.selected(); onImport() },
                    onDeveloper = { haptics.selected(); onDeveloper() },
                )
                    }
                }
            }
        }
    }
}

/**
 * The update dialog a **debug** build has been told to force, or `null` in a release.
 *
 * The switch is a build-time resource rather than a flag, an intent extra or a preference, so
 * there is nothing in a shipped APK to flip: `nomi_debug_force_update_dialog` is a constant
 * `false` in `src/main/res/values`, and only the `debug` source set overrides it. That is why
 * this lives in the composition rather than in [com.nomi.app.ui.app.AppViewModel] - the ViewModel
 * deliberately holds no `Context`, and giving it one for a debug affordance would be a bad trade.
 */
@Composable
private fun rememberForcedUpdateAvailability(): UpdateAvailability.Available? {
    val context = LocalContext.current
    return remember(context) {
        if (!context.resources.getBoolean(R.bool.nomi_debug_force_update_dialog)) return@remember null
        val version = context.getString(R.string.nomi_debug_forced_update_version)
            .takeIf(String::isNotBlank) ?: return@remember null
        UpdateAvailability.Available(
            version = version,
            releaseUrl = "https://github.com/david-x3d/nomi/releases/latest",
            summary = "This text stands in for the release notes, which is the one part of the " +
                "dialog that cannot be faked - it comes from whatever GitHub returns. The dialog " +
                "itself, this summary's scroll area, and the two buttons are all real.",
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoadingPage() = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    LoadingIndicator()
}

private enum class MainDestination(
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    TODAY(Icons.Default.Today, Icons.Outlined.Today),
    PROGRESS(Icons.Default.Insights, Icons.Outlined.Insights),
    SETTINGS(Icons.Default.Settings, Icons.Outlined.Settings),
}

@Composable
private fun MainDestination.localizedLabel(): String = when (this) {
    MainDestination.TODAY -> nomiString("Today")
    MainDestination.PROGRESS -> nomiString("Progress")
    MainDestination.SETTINGS -> nomiString("Settings")
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
    const val HISTORY = "history"
    const val FOOD = "food/{id}"
    const val PROFILE = "profile"
    const val PLAN = "plan"
    const val MICRONUTRIENTS = "micronutrients"
    const val HEALTH = "health"
    const val DEVELOPER = "developer"
    fun food(id: Long) = "food/$id"
}
