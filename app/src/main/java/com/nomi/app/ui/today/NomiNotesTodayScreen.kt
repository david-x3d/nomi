package com.nomi.app.ui.today

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.nomi.app.ai.model.AnalyzedFoodItem
import com.nomi.app.ai.model.FoodAnalysis
import com.nomi.app.ui.capture.PhotoCaptureSubject
import com.nomi.app.ui.capture.rememberInlineDictation
import com.nomi.app.ui.components.NomiFox
import com.nomi.app.ui.components.NomiFoxMood
import com.nomi.app.ui.components.hairlineOnPitchBlack
import com.nomi.app.ui.feedback.nomiPress
import com.nomi.app.ui.feedback.rememberNomiHaptics
import com.nomi.app.ui.feedback.rememberNomiPressFeedback
import com.nomi.app.ui.localization.nomiLocale
import com.nomi.app.ui.localization.nomiString
import com.nomi.app.ui.logging.FoodLoggingUiState
import com.nomi.app.ui.theme.NomiTheme
import com.nomi.app.ui.theme.nomiFadeMotionSpec
import com.nomi.app.ui.theme.nomiLayoutMotionSpec
import com.nomi.app.ui.theme.nomiPageMotionSpec
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * A notes-first Today experience. The adaptive navigation suite remains owned by the caller;
 * this composable only draws the Today destination and its local sheets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NomiNotesTodayScreen(
    state: TodayUiState,
    loggingState: FoodLoggingUiState,
    pendingDeletions: Map<Long, PendingFoodDeletion> = emptyMap(),
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onToday: () -> Unit,
    onOpenHistory: () -> Unit = {},
    onFoodClick: (Long) -> Unit,
    onDeleteFood: (TodayFoodEntry) -> Unit = {},
    onDeleteFoodImmediately: (Long) -> Unit = {},
    onUndoDeleteFood: (Long) -> Unit = {},
    onDuplicateFood: (Long) -> Unit = {},
    onFavoriteFood: (Long) -> Unit = {},
    onEditFoodAmount: (TodayFoodEntry) -> Unit = {},
    editedEntryId: Long? = null,
    onEditEntryText: (TodayFoodEntry) -> Unit = {},
    onTextChanged: (String) -> Unit,
    onAnalyze: () -> Unit,
    onConfirm: () -> Unit,
    onRetry: () -> Unit,
    onEditText: () -> Unit,
    onEditPreview: () -> Unit,
    onDismissDraft: () -> Unit,
    onQuickMethod: (AddFoodMethod) -> Unit,
    onInlinePhotoSelected: (Uri, String, PhotoCaptureSubject) -> Unit = { _, _, _ -> },
    onInlineBarcodeDetected: (String) -> Unit = {},
    onVoiceTranscription: (String) -> Unit = {},
    onPhotoDescriptionChanged: (String) -> Unit = {},
    onPhotoPlaceChanged: (String) -> Unit = {},
    onConfirmPhotoDescription: () -> Unit = {},
    aiSetupNeeded: Boolean = false,
    onOpenAiSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val haptics = rememberNomiHaptics()
    // Dictation stays on this page: the row at the bottom becomes the microphone rather than
    // handing the page over to a screen whose only job is to listen.
    val dictation = rememberInlineDictation(onTranscription = onVoiceTranscription)
    var showGoals by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val composer = rememberTodayComposerState(listState)
    val capture = rememberTodayCaptureState(onInlinePhotoSelected, composer, listState)

    val loggingDescription = when (loggingState) {
        is FoodLoggingUiState.Input -> loggingState.text
        is FoodLoggingUiState.Processing -> loggingState.originalText
        is FoodLoggingUiState.PhotoReview -> loggingState.description
        is FoodLoggingUiState.Preview -> loggingState.originalText
        is FoodLoggingUiState.Error -> loggingState.originalText
        is FoodLoggingUiState.Manual -> loggingState.draft.name
    }
    val itemSpatialSpec = MaterialTheme.motionScheme.fastSpatialSpec<IntOffset>()
    val itemFadeSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val contentSizeSpec = MaterialTheme.motionScheme.fastSpatialSpec<IntSize>()
    val pendingDeletedFoods = pendingDeletions.filterValues { it.date == state.date }
    // The fox reads the app's state, never the user's day: there is no mood for eating too
    // much or too little, because a mascot with an opinion about a number is one you stop
    // opening the app to avoid.
    val foxMood = when {
        loggingState is FoodLoggingUiState.Error -> NomiFoxMood.CONCERNED
        loggingState is FoodLoggingUiState.Processing || state.isLoading -> NomiFoxMood.CURIOUS
        state.entries.isEmpty() -> NomiFoxMood.RESTING
        else -> NomiFoxMood.SETTLED
    }
    // A failed meal is worth one buzz, not one per recomposition.
    LaunchedEffect(loggingState is FoodLoggingUiState.Error) {
        if (loggingState is FoodLoggingUiState.Error) haptics.failed()
    }
    // Back means "forget what I just said" while the bar is listening, not "leave the day".
    BackHandler(enabled = dictation.isActive) { dictation.cancel() }
    val pendingEntries = pendingDeletedFoods.values.map(PendingFoodDeletion::entry)
    val displayedEntries = remember(state.entries, pendingEntries) {
        (state.entries + pendingEntries)
            .distinctBy(TodayFoodEntry::id)
            .sortedBy(TodayFoodEntry::time)
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        topBar = {
            NotesHeader(
                date = state.date,
                foxMood = foxMood,
                onPreviousDay = { haptics.selected(); onPreviousDay() },
                onNextDay = { haptics.selected(); onNextDay() },
                onToday = { haptics.selected(); onToday() },
                onOpenHistory = { haptics.selected(); onOpenHistory() },
            )
        },
        bottomBar = {
            AnimatedVisibility(
                visible = capture.subject == null && !capture.showBarcode,
                enter = fadeIn(nomiFadeMotionSpec()) + expandVertically(
                    animationSpec = nomiLayoutMotionSpec(),
                    expandFrom = Alignment.Bottom,
                ),
                exit = fadeOut(nomiFadeMotionSpec()) + shrinkVertically(
                    animationSpec = nomiLayoutMotionSpec(),
                    shrinkTowards = Alignment.Bottom,
                ),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    contentAlignment = Alignment.Center,
                ) {
                    NotesFloatingActionRow(
                        state = state.copy(entries = state.entries.filter { it.id !in pendingDeletedFoods }),
                        dictation = dictation,
                        onGoals = { haptics.selected(); showGoals = true },
                        onVoice = { haptics.selected(); dictation.start() },
                        onDictationDone = { haptics.sent(); dictation.stop() },
                        onCameraMethod = { method ->
                            haptics.selected()
                            when (method) {
                                AddFoodMethod.PHOTO -> capture.subject = PhotoCaptureSubject.MEAL
                                AddFoodMethod.MENU -> {
                                    onQuickMethod(method)
                                    capture.subject = PhotoCaptureSubject.MENU
                                }
                                AddFoodMethod.BARCODE -> capture.showBarcode = true
                                else -> onQuickMethod(method)
                            }
                        },
                        onChoosePhoto = {
                            haptics.selected()
                            capture.pickPhoto()
                        },
                        onLibraryMethod = { method ->
                            haptics.selected()
                            onQuickMethod(method)
                        },
                    )
                }
            }
        },
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                // The page itself is the writing surface: tapping the empty area below the
                // notes starts a new entry, the way tapping into a note does. While a logged
                // line is open for rewriting, that same tap puts it back instead, so leaving
                // a line alone is as easy as touching it was.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        if (editedEntryId != null) {
                            onDismissDraft()
                        } else {
                            haptics.selected()
                            composer.isOpen = true
                        }
                    },
                ),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 760.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
            ) {
                item(key = "loading") {
                    AnimatedVisibility(
                        visible = state.isLoading,
                        enter = expandVertically(
                            animationSpec = contentSizeSpec,
                            expandFrom = Alignment.Top,
                        ) + fadeIn(animationSpec = itemFadeSpec),
                        exit = shrinkVertically(
                            animationSpec = contentSizeSpec,
                            shrinkTowards = Alignment.Top,
                        ) + fadeOut(animationSpec = itemFadeSpec),
                    ) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        )
                    }
                }

                item(key = "inline-camera") {
                    AnimatedVisibility(
                        visible = capture.subject != null || capture.showBarcode,
                        enter = fadeIn(nomiFadeMotionSpec()) + expandVertically(
                            animationSpec = nomiLayoutMotionSpec(),
                            expandFrom = Alignment.Top,
                        ),
                        exit = fadeOut(nomiFadeMotionSpec()) + shrinkVertically(
                            animationSpec = nomiLayoutMotionSpec(),
                            shrinkTowards = Alignment.Top,
                        ),
                    ) {
                        InlineTodayCapture(capture, onQuickMethod, onInlinePhotoSelected, onInlineBarcodeDetected)
                    }
                }

                // Sits after the camera so the camera keeps its place as the second row, which
                // is where opening it scrolls to.
                item(key = "ai-setup") {
                    AnimatedVisibility(
                        // An error that already offers the way to the AI page says the same
                        // thing with the meal attached, so the note steps aside for it.
                        visible = aiSetupNeeded &&
                            (loggingState as? FoodLoggingUiState.Error)?.pointsToSettings != true,
                        enter = expandVertically(
                            animationSpec = contentSizeSpec,
                            expandFrom = Alignment.Top,
                        ) + fadeIn(animationSpec = itemFadeSpec),
                        exit = shrinkVertically(
                            animationSpec = contentSizeSpec,
                            shrinkTowards = Alignment.Top,
                        ) + fadeOut(animationSpec = itemFadeSpec),
                    ) {
                        AiSetupNote(onOpenAiSettings = onOpenAiSettings)
                    }
                }

                val entries = displayedEntries
                item(key = "empty") {
                    AnimatedVisibility(
                        visible = entries.isEmpty() && loggingState is FoodLoggingUiState.Input,
                        enter = expandVertically(
                            animationSpec = contentSizeSpec,
                            expandFrom = Alignment.Top,
                        ) + fadeIn(animationSpec = itemFadeSpec),
                        exit = shrinkVertically(
                            animationSpec = contentSizeSpec,
                            shrinkTowards = Alignment.Top,
                        ) + fadeOut(animationSpec = itemFadeSpec),
                    ) {
                        NotesEmptyState()
                    }
                }
                items(entries, key = TodayFoodEntry::id) { entry ->
                    Column(
                        modifier = Modifier.animateItem(
                            fadeInSpec = itemFadeSpec,
                            placementSpec = itemSpatialSpec,
                            fadeOutSpec = itemFadeSpec,
                        ),
                    ) {
                        TodayEntryRow(
                            entry, pendingDeletedFoods[entry.id], editedEntryId, loggingState, composer.caret,
                            onCaretChanged = { composer.caret = it },
                            onCloseComposer = composer::close,
                            onTextChanged, onAnalyze, onDismissDraft, onDeleteFoodImmediately,
                            onFoodClick, onEditEntryText, onDeleteFood, onDuplicateFood, onFavoriteFood,
                            onEditFoodAmount, onUndoDeleteFood,
                        )
                    }
                }

                item(key = "logging-state") {
                    Box(
                        modifier = Modifier.animateItem(
                            fadeInSpec = itemFadeSpec,
                            placementSpec = itemSpatialSpec,
                            fadeOutSpec = itemFadeSpec,
                        ),
                    ) {
                        InlineLoggingState(
                            state = loggingState,
                            rememberedDescription = loggingDescription,
                            // While a logged row is being rewritten it owns the caret, so the
                            // page must not offer a second empty line at the bottom.
                            suppressComposer = editedEntryId != null,
                            composerFocused = composer.isOpen,
                            onTextChanged = onTextChanged,
                            onAnalyze = { haptics.sent(); composer.close(); onAnalyze() },
                            onConfirm = { haptics.confirmed(); onConfirm() },
                            onRetry = onRetry,
                            onPhotoDescriptionChanged = onPhotoDescriptionChanged,
                            onPhotoPlaceChanged = onPhotoPlaceChanged,
                            onConfirmPhotoDescription = {
                                haptics.sent()
                                onConfirmPhotoDescription()
                            },
                            onEditText = onEditText,
                            onEditPreview = onEditPreview,
                            onDismissDraft = onDismissDraft,
                            onOpenAiSettings = onOpenAiSettings,
                        )
                    }
                }
            }
        }
    }

    if (showGoals) {
        GoalsSheet(state = state, onDismiss = { showGoals = false })
    }
}

@Composable
private fun NotesHeader(
    date: LocalDate,
    foxMood: NomiFoxMood,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onToday: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    val locale = nomiLocale()
    val datePattern = nomiString("EEEE, MMMM d")
    val spatialSpec = nomiPageMotionSpec<IntOffset>()
    val effectsSpec = nomiFadeMotionSpec<Float>()
    val todayPress = rememberNomiPressFeedback(pressedScale = 0.97f)
    val previousPress = rememberNomiPressFeedback(pressedScale = 0.90f)
    val nextPress = rememberNomiPressFeedback(pressedScale = 0.90f)
    val historyPress = rememberNomiPressFeedback(pressedScale = 0.90f)
    val foxHalo by animateColorAsState(
        targetValue = when (foxMood) {
            NomiFoxMood.RESTING -> MaterialTheme.colorScheme.secondaryContainer
            NomiFoxMood.CURIOUS -> MaterialTheme.colorScheme.tertiaryContainer
            NomiFoxMood.SETTLED -> MaterialTheme.colorScheme.primaryContainer
            NomiFoxMood.CONCERNED -> MaterialTheme.colorScheme.errorContainer
        },
        animationSpec = tween(durationMillis = 420),
        label = "Nomi mood halo",
    )
    val headerBrush = Brush.verticalGradient(
        colors = listOf(
            foxHalo.copy(alpha = 0.24f),
            MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
    )
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().background(headerBrush),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        modifier = Modifier.align(Alignment.CenterStart),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(foxHalo.copy(alpha = 0.58f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            NomiFox(mood = foxMood, size = 42.dp)
                        }
                        Text(
                            text = "Nomi",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    Surface(
                        onClick = onToday,
                        interactionSource = todayPress.interactionSource,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        tonalElevation = 1.dp,
                        border = hairlineOnPitchBlack(),
                        modifier = Modifier
                            .sizeIn(minWidth = 72.dp, minHeight = 48.dp)
                            .animateContentSize()
                            .nomiPress(todayPress),
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            AnimatedContent(
                                targetState = date == LocalDate.now(),
                                transitionSpec = {
                                    (fadeIn(animationSpec = effectsSpec) + scaleIn(
                                        animationSpec = effectsSpec,
                                        initialScale = 0.92f,
                                    )).togetherWith(
                                        fadeOut(animationSpec = effectsSpec) + scaleOut(
                                            animationSpec = effectsSpec,
                                            targetScale = 0.96f,
                                        ),
                                    )
                                },
                                label = "today action label",
                            ) { isToday ->
                                Text(
                                    text = if (isToday) {
                                        nomiString("Today")
                                    } else {
                                        nomiString("Go to today")
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IconButton(
                        onClick = onPreviousDay,
                        interactionSource = previousPress.interactionSource,
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .nomiPress(previousPress),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = nomiString("Previous day"),
                        )
                    }
                    AnimatedContent(
                        targetState = date,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        transitionSpec = {
                            val direction = if (targetState.isAfter(initialState)) 1 else -1
                            (slideInHorizontally(animationSpec = spatialSpec) { width ->
                                direction * (width / 10)
                            } +
                                fadeIn(animationSpec = effectsSpec)).togetherWith(
                                slideOutHorizontally(animationSpec = spatialSpec) { width ->
                                    -direction * (width / 12)
                                } +
                                    fadeOut(animationSpec = effectsSpec),
                            )
                        },
                        contentAlignment = Alignment.Center,
                        label = "selected day",
                    ) { displayedDate ->
                        Text(
                            text = displayedDate.format(
                                DateTimeFormatter.ofPattern(datePattern, locale),
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                    IconButton(
                        onClick = onNextDay,
                        interactionSource = nextPress.interactionSource,
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .nomiPress(nextPress),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = nomiString("Next day"),
                        )
                    }
                    // History is a destination with its own screen, not a fourth bottom-bar tab,
                    // so it hangs off the day pager: paging back through days is the gesture that
                    // makes someone want to see a whole month at once.
                    IconButton(
                        onClick = onOpenHistory,
                        interactionSource = historyPress.interactionSource,
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .nomiPress(historyPress),
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = nomiString("History"),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Says, before anything is typed, that nothing typed can be looked up yet.
 *
 * Without it the first thing a keyless install learns about keys is an error on its first meal.
 * It is a note on the page rather than a dialog over it: the day can still be read, and manual
 * entries and saved foods still work.
 */
@Composable
private fun AiSetupNote(onOpenAiSettings: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        border = hairlineOnPitchBlack(),
    ) {
        Row(
            modifier = Modifier.padding(start = 18.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Key, contentDescription = null)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = nomiString("Add your AI key"),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = nomiString("Nomi can't look up food until a key is saved."),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            // Filled, not tonal: a tonal button is the colour of the note it sits on.
            Button(onClick = onOpenAiSettings) {
                Text(nomiString("Set up"), maxLines = 1)
            }
        }
    }
}

@Composable
private fun NotesEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = nomiString("Tell Nomi what you ate"),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Text(
            text = nomiString("For example: 2 rolls with cheese"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f),
            textAlign = TextAlign.Center,
        )
    }
}

private fun sampleTodayState() = TodayUiState(
    date = LocalDate.now(),
    caloriesConsumed = 841.0,
    calorieTarget = 1_900.0,
    protein = MacroProgress(59.0, 120.0),
    carbohydrates = MacroProgress(51.0, 210.0),
    fat = MacroProgress(37.0, 60.0),
    entries = listOf(
        TodayFoodEntry(
            id = 1L,
            name = "Owyn protein shake",
            amountText = "1 bottle",
            calories = 180.0,
            proteinGrams = 32.0,
            carbohydrateGrams = 9.0,
            fatGrams = 4.0,
            mealCategory = MealCategory.BREAKFAST,
            time = LocalTime.of(8, 15),
        ),
        TodayFoodEntry(
            id = 2L,
            name = "Iced sweet potato latte with rice milk",
            brand = "La Casita Bakery",
            amountText = "1 medium",
            calories = 241.0,
            mealCategory = MealCategory.SNACKS,
            time = LocalTime.of(11, 30),
            isEstimated = true,
        ),
        TodayFoodEntry(
            id = 3L,
            name = "Shin ramen with two eggs",
            amountText = "1 bowl",
            calories = 620.0,
            proteinGrams = 19.0,
            carbohydrateGrams = 87.0,
            fatGrams = 22.0,
            mealCategory = MealCategory.DINNER,
            time = LocalTime.of(19, 0),
        ),
    ),
)

private fun sampleAnalysis() = FoodAnalysis(
    items = listOf(
        AnalyzedFoodItem(
            name = "Salami pizza",
            brand = "Domino’s",
            quantity = 1.0,
            unit = "pizza",
            calories = 785.0,
            proteinGrams = 32.0,
            carbohydrateGrams = 91.0,
            fatGrams = 31.0,
            isEstimate = true,
        ),
    ),
)

@Preview(name = "Nomi notes — light", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun NomiNotesLightPreview() {
    NomiTheme(darkTheme = false, dynamicColor = false) {
        NomiNotesTodayScreen(
            state = sampleTodayState(),
            loggingState = FoodLoggingUiState.Input("one salami pizza by Domino’s"),
            onPreviousDay = {},
            onNextDay = {},
            onToday = {},
            onFoodClick = {},
            onTextChanged = {},
            onAnalyze = {},
            onConfirm = {},
            onRetry = {},
            onEditText = {},
            onEditPreview = {},
            onDismissDraft = {},
            onQuickMethod = {},
        )
    }
}

@Preview(name = "Nomi notes — dark preview", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun NomiNotesDarkPreview() {
    NomiTheme(darkTheme = true, dynamicColor = false) {
        NomiNotesTodayScreen(
            state = sampleTodayState(),
            loggingState = FoodLoggingUiState.Preview(sampleAnalysis(), MealCategory.DINNER),
            onPreviousDay = {},
            onNextDay = {},
            onToday = {},
            onFoodClick = {},
            onTextChanged = {},
            onAnalyze = {},
            onConfirm = {},
            onRetry = {},
            onEditText = {},
            onEditPreview = {},
            onDismissDraft = {},
            onQuickMethod = {},
        )
    }
}
