package com.nomi.app.ui.app

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldValue
import androidx.compose.material3.adaptive.navigationsuite.rememberNavigationSuiteScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nomi.app.data.preferences.GoalsCardStyle
import com.nomi.app.data.preferences.WeightUnitPreference
import com.nomi.app.ui.capture.PhotoCaptureSubject
import com.nomi.app.ui.feedback.rememberNomiHaptics
import com.nomi.app.ui.localization.NomiLanguage
import com.nomi.app.ui.localization.nomiString
import com.nomi.app.ui.progress.ProgressScreen
import com.nomi.app.ui.settings.SettingsScreen
import com.nomi.app.ui.theme.nomiFadeMotionSpec
import com.nomi.app.ui.theme.nomiPageMotionSpec
import com.nomi.app.ui.today.AddFoodMethod
import com.nomi.app.ui.today.NomiNotesTodayScreen

/*
 * The three top-level destinations - Today, Progress and Settings - and the bar or rail that
 * switches between them. Everything reached from one of them is a NavHost destination instead.
 */

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MainNavigationSuite(
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
    onGoalsCardStyle: (GoalsCardStyle) -> Unit,
    onReminderTime: (index: Int, hour: Int, minute: Int) -> Unit,
    onProfile: () -> Unit,
    onNutrition: () -> Unit,
    onMicronutrients: () -> Unit,
    onAiSettings: () -> Unit,
    onHealth: () -> Unit,
    onReminder: (Int, Boolean) -> Unit,
    onExport: () -> Unit,
    onExportDiary: () -> Unit,
    onImport: () -> Unit,
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
                        val aiSetupNeeded by viewModel.aiSetupNeeded.collectAsStateWithLifecycle()
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
                            aiSetupNeeded = aiSetupNeeded,
                            onOpenAiSettings = { haptics.selected(); onAiSettings() },
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
                            onAi = { haptics.selected(); onAiSettings() },
                            onHealthConnect = { haptics.selected(); onHealth() },
                            onReminderChanged = { index, enabled -> haptics.toggled(); onReminder(index, enabled) },
                            onGoalsCardStyleChanged = { haptics.toggled(); onGoalsCardStyle(it) },
                            onReminderTimeChanged = { index, hour, minute ->
                                haptics.confirmed(); onReminderTime(index, hour, minute)
                            },
                            onExport = { haptics.selected(); onExport() },
                            onExportDiary = { haptics.selected(); onExportDiary() },
                            onImport = { haptics.selected(); onImport() },
                        )
                    }
                }
            }
        }
    }
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
