package com.nomi.app.ui.today

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.nomi.app.ai.model.AnalyzedFoodItem
import com.nomi.app.ai.model.FoodAnalysis
import com.nomi.app.ui.components.AnimatedWebsiteIconStack
import com.nomi.app.ui.components.hairlineOnPitchBlack
import com.nomi.app.ui.profile.localizedName
import com.nomi.app.ui.components.NomiFox
import com.nomi.app.ui.components.NomiFoxMood
import com.nomi.app.ui.feedback.rememberNomiHaptics
import com.nomi.app.ui.format.quantityDisplay
import com.nomi.app.data.preferences.GoalsCardStyle
import com.nomi.app.ui.localization.nomiLocale
import com.nomi.app.ui.localization.nomiString
import com.nomi.app.ui.logging.FoodLoggingUiState
import com.nomi.app.ui.theme.LocalPitchBlackSurfaces
import com.nomi.app.ui.theme.NomiTheme
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A notes-first Today experience. The adaptive navigation suite remains owned by the caller;
 * this composable only draws the Today destination and its local sheets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NomiNotesTodayScreen(
    state: TodayUiState,
    loggingState: FoodLoggingUiState,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onToday: () -> Unit,
    onFoodClick: (Long) -> Unit,
    onDeleteFood: (Long) -> Unit = {},
    onUndoDeleteFood: (Long) -> Unit = {},
    onDiscardDeletedFood: (Long) -> Unit = {},
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
    onPhotoDescriptionChanged: (String) -> Unit = {},
    onPhotoPlaceChanged: (String) -> Unit = {},
    onConfirmPhotoDescription: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val haptics = rememberNomiHaptics()
    var showQuickAdd by rememberSaveable { mutableStateOf(false) }
    var showGoals by rememberSaveable { mutableStateOf(false) }
    var composerOpen by rememberSaveable { mutableStateOf(false) }
    // Where the line was touched, so the caret opens in that word instead of at the end.
    var caretInEditedEntry by rememberSaveable { mutableStateOf(0) }
    val loggingDescription = when (loggingState) {
        is FoodLoggingUiState.Input -> loggingState.text
        is FoodLoggingUiState.Processing -> loggingState.originalText
        is FoodLoggingUiState.PhotoReview -> loggingState.description
        is FoodLoggingUiState.Preview -> loggingState.originalText
        is FoodLoggingUiState.Error -> loggingState.originalText
        is FoodLoggingUiState.Manual -> loggingState.draft.name
    }
    val itemSpatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val itemFadeSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val contentSizeSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()
    val pendingDeletedFoods = remember { mutableStateMapOf<Long, PendingDeletedFood>() }

    val liveEntryIds = remember(state.entries) {
        state.entries.mapTo(mutableSetOf(), TodayFoodEntry::id)
    }
    LaunchedEffect(liveEntryIds) {
        pendingDeletedFoods.keys.toList().forEach { id ->
            val pending = pendingDeletedFoods[id] ?: return@forEach
            if (id !in liveEntryIds && !pending.removalObserved) {
                pendingDeletedFoods[id] = pending.copy(removalObserved = true)
            } else if (id in liveEntryIds && pending.undoRequested && pending.removalObserved) {
                pendingDeletedFoods.remove(id)
            }
        }
    }
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

    val pendingEntries = pendingDeletedFoods.values.map(PendingDeletedFood::entry)
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
                onPreviousDay = onPreviousDay,
                onNextDay = onNextDay,
                onToday = onToday,
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                contentAlignment = Alignment.Center,
            ) {
                NotesFloatingActionRow(
                    state = state,
                    canSend = (loggingState as? FoodLoggingUiState.Input)
                        ?.text?.isNotBlank() == true,
                    onGoals = { showGoals = true },
                    onVoice = { onQuickMethod(AddFoodMethod.VOICE) },
                    onMore = { showQuickAdd = true },
                    onWrite = { composerOpen = true },
                    onSend = { haptics.sent(); onAnalyze() },
                )
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
                        if (editedEntryId != null) onDismissDraft() else composerOpen = true
                    },
                ),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
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
                        val pending = pendingDeletedFoods[entry.id]
                        val editingThisEntry = entry.id == editedEntryId &&
                            loggingState is FoodLoggingUiState.Input
                        when {
                            // The row becomes the line you write on, so a rewrite happens
                            // where the entry already sits.
                            editingThisEntry -> InlineComposerCanvas(
                                text = loggingState.text,
                                autoFocus = true,
                                fillsPage = false,
                                initialCaret = caretInEditedEntry,
                                onTextChanged = onTextChanged,
                                onAnalyze = { haptics.sent(); onAnalyze() },
                            )
                            pending == null -> SwipeToDeleteFoodRow(
                                entry = entry,
                                onOpenDetails = { onFoodClick(entry.id) },
                                onEditText = { caret ->
                                    caretInEditedEntry = caret
                                    onEditEntryText(entry)
                                },
                                onDelete = {
                                    haptics.removed()
                                    pendingDeletedFoods[entry.id] = PendingDeletedFood(entry)
                                    onDeleteFood(entry.id)
                                },
                            )
                            pending.undoRequested -> RestoringFoodRow(entry)
                            else -> InlineDeletedFoodRow(
                                entry = entry,
                                onUndo = {
                                    haptics.confirmed()
                                    pendingDeletedFoods[entry.id] = pending.copy(undoRequested = true)
                                    onUndoDeleteFood(entry.id)
                                },
                                onTimeout = {
                                    if (pendingDeletedFoods[entry.id]?.undoRequested == false) {
                                        pendingDeletedFoods.remove(entry.id)
                                        onDiscardDeletedFood(entry.id)
                                    }
                                },
                            )
                        }
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
                            composerFocused = composerOpen,
                            onTextChanged = onTextChanged,
                            onAnalyze = { haptics.sent(); onAnalyze() },
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
                        )
                    }
                }
            }
        }
    }

    if (showQuickAdd) {
        QuickAddSheet(
            onDismiss = { showQuickAdd = false },
            onSelect = { method ->
                showQuickAdd = false
                onQuickMethod(method)
            },
        )
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
) {
    val locale = nomiLocale()
    val datePattern = nomiString("EEEE, MMMM d", "EEEE, d. MMMM")
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 0.dp,
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
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
                    ) {
                        NomiFox(mood = foxMood)
                        Text(
                            text = "Nomi",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    Surface(
                        onClick = onToday,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 1.dp,
                        modifier = Modifier
                            .sizeIn(minWidth = 72.dp, minHeight = 48.dp)
                            .animateContentSize(),
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
                                        nomiString("Today", "Heute")
                                    } else {
                                        nomiString("Go to today", "Zum heutigen Tag")
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
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = nomiString("Previous day", "Vorheriger Tag"),
                        )
                    }
                    AnimatedContent(
                        targetState = date,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        transitionSpec = {
                            (slideInHorizontally(animationSpec = spatialSpec) { width -> width / 5 } +
                                fadeIn(animationSpec = effectsSpec)).togetherWith(
                                slideOutHorizontally(animationSpec = spatialSpec) { width -> -width / 5 } +
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
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = nomiString("Next day", "Nächster Tag"),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotesEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 44.dp),
    ) {
        Text(
            text = nomiString("What did you eat?", "Was hast du gegessen?"),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.semantics { heading() },
        )
    }
}

/**
 * How long a deleted row stays on the page offering to come back.
 *
 * The row still occupies its place while it waits, so the page does not settle until it goes:
 * a long window reads as the list being stuck rather than as a generous offer. Three seconds
 * is enough to catch a swipe you did not mean, and short enough that deleting on purpose feels
 * finished. A deletion is recoverable anyway - the entry can simply be written again.
 */
private const val UNDO_WINDOW_MILLIS = 3_000L

private data class PendingDeletedFood(
    val entry: TodayFoodEntry,
    val undoRequested: Boolean = false,
    val removalObserved: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDeleteFoodRow(
    entry: TodayFoodEntry,
    onOpenDetails: () -> Unit,
    onEditText: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    val deleteLabel = nomiString("Delete ${entry.name}", "${entry.name} löschen")
    val editLabel = nomiString("Rewrite ${entry.name}", "${entry.name} umschreiben")
    val detailsLabel = nomiString("Nutrition for ${entry.name}", "Nährwerte von ${entry.name}")
    val haptics = rememberNomiHaptics()
    var deleteRequested by remember(entry.id) { mutableStateOf(false) }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart && !deleteRequested) {
                deleteRequested = true
                onDelete()
                true
            } else {
                false
            }
        },
        positionalThreshold = { distance -> distance * 0.32f },
    )
    val armed = dismissState.targetValue == SwipeToDismissBoxValue.EndToStart
    val revealScale by animateFloatAsState(
        targetValue = if (armed) 1f else 0.82f,
        animationSpec = tween(durationMillis = 180),
        label = "Delete reveal",
    )
    // The moment letting go would delete the row is felt, so the swipe has a point of no
    // return you can find without watching it.
    LaunchedEffect(armed) {
        if (armed) haptics.deleteArmed()
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 5.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Row(
                    modifier = Modifier.graphicsLayer {
                        scaleX = revealScale
                        scaleY = revealScale
                    },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = nomiString("Delete", "Löschen"),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        },
        modifier = Modifier.semantics {
            customActions = listOf(
                CustomAccessibilityAction(label = editLabel) {
                    onEditText(entry.reeditableText().length)
                    true
                },
                CustomAccessibilityAction(label = detailsLabel) {
                    onOpenDetails()
                    true
                },
                CustomAccessibilityAction(label = deleteLabel) {
                    if (!deleteRequested) {
                        deleteRequested = true
                        onDelete()
                    }
                    true
                },
            )
        },
    ) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest) {
            NotesFoodRow(entry = entry, onOpenDetails = onOpenDetails, onEditText = onEditText)
        }
    }
}

@Composable
private fun InlineDeletedFoodRow(
    entry: TodayFoodEntry,
    onUndo: () -> Unit,
    onTimeout: () -> Unit,
) {
    LaunchedEffect(entry.id) {
        delay(UNDO_WINDOW_MILLIS)
        onTimeout()
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = entry.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
            TextButton(onClick = onUndo) {
                Text(
                    text = nomiString("Undo", "Rückgängig"),
                    maxLines = 1,
                    softWrap = false,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun RestoringFoodRow(entry: TodayFoodEntry) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(
                text = nomiString("Restoring ${entry.name}", "${entry.name} wird wiederhergestellt"),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
            )
        }
    }
}

/**
 * A logged entry as a line of the page.
 *
 * The words behave like written text: touching them puts the caret where it was touched and
 * hands the line to the keyboard, so a wrong word is corrected where it stands. The calories
 * stay a separate target that opens the entry's details, and holding the line does the same,
 * because the numbers are a different subject from the sentence that produced them.
 */
@Composable
private fun NotesFoodRow(
    entry: TodayFoodEntry,
    onOpenDetails: () -> Unit,
    onEditText: (Int) -> Unit,
) {
    val description = entry.rowDescription()
    val locale = nomiLocale()
    val amountDisplay = entry.quantityDisplay(locale).withContext
    val detailsLabel = nomiString("Nutrition for ${entry.name}", "Nährwerte von ${entry.name}")
    val writeLabel = nomiString("Rewrite ${entry.name}", "${entry.name} umschreiben")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Past the end of the words the line still opens for writing, with the caret at
            // the end, the way tapping the empty part of a note's line behaves.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = writeLabel,
                onClick = { onEditText(entry.reeditableText().length) },
            )
            .heightIn(min = 64.dp)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            var descriptionLayout by remember(entry.id) {
                mutableStateOf<TextLayoutResult?>(null)
            }
            Text(
                text = description,
                modifier = Modifier.pointerInput(entry.id, description) {
                    detectTapGestures(
                        onLongPress = { onOpenDetails() },
                        onTap = { position ->
                            val tapped = descriptionLayout?.getOffsetForPosition(position)
                                ?: description.length
                            onEditText(entry.reeditableCaretForDescription(tapped))
                        },
                    )
                },
                onTextLayout = { descriptionLayout = it },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (entry.amountText.isNotBlank() || entry.amount > 0.0) {
                var amountLayout by remember(entry.id) { mutableStateOf<TextLayoutResult?>(null) }
                Text(
                    text = amountDisplay,
                    modifier = Modifier.pointerInput(entry.id, amountDisplay) {
                        detectTapGestures(
                            onLongPress = { onOpenDetails() },
                            onTap = { position ->
                                val tapped = amountLayout?.getOffsetForPosition(position) ?: 0
                                onEditText(entry.reeditableCaretForAmount(tapped))
                            },
                        )
                    },
                    onTextLayout = { amountLayout = it },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = "${entry.calories.roundToInt()} kcal",
            // The calories are the way into the entry's details now that the words belong to
            // the keyboard, so the figure carries a touch target rather than only its glyphs.
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = detailsLabel,
                    onClick = onOpenDetails,
                )
                .heightIn(min = 44.dp)
                .wrapContentHeight(Alignment.Top),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun InlineLoggingState(
    state: FoodLoggingUiState,
    rememberedDescription: String,
    suppressComposer: Boolean,
    composerFocused: Boolean,
    onTextChanged: (String) -> Unit,
    onAnalyze: () -> Unit,
    onConfirm: () -> Unit,
    onRetry: () -> Unit,
    onEditText: () -> Unit,
    onEditPreview: () -> Unit,
    onDismissDraft: () -> Unit,
    onPhotoDescriptionChanged: (String) -> Unit,
    onPhotoPlaceChanged: (String) -> Unit,
    onConfirmPhotoDescription: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        when (state) {
            is FoodLoggingUiState.Input -> if (!suppressComposer) {
                InlineComposerCanvas(
                    text = state.text,
                    autoFocus = composerFocused,
                    fillsPage = true,
                    onTextChanged = onTextChanged,
                    onAnalyze = onAnalyze,
                )
            }
            is FoodLoggingUiState.Processing -> ProcessingNote(
                description = rememberedDescription,
                sourceUrls = state.sourceUrls,
                onEditText = onEditText,
                onCancel = onDismissDraft,
            )

            is FoodLoggingUiState.PhotoReview -> PhotoReviewNote(
                state = state,
                onDescriptionChanged = onPhotoDescriptionChanged,
                onPlaceChanged = onPhotoPlaceChanged,
                onConfirm = onConfirmPhotoDescription,
                onCancel = onDismissDraft,
            )

            is FoodLoggingUiState.Preview -> PreviewNote(
                analysis = state.analysis,
                description = rememberedDescription,
                onAdd = onConfirm,
                onEditText = onEditText,
                onEditPreview = onEditPreview,
                onCancel = onDismissDraft,
            )

            is FoodLoggingUiState.Error -> ErrorNote(
                description = rememberedDescription,
                message = state.message,
                canRetry = state.canRetry,
                onRetry = onRetry,
                onEditText = onEditText,
                onCancel = onDismissDraft,
            )

            is FoodLoggingUiState.Manual -> ManualDraftNote(
                state = state,
                onAdd = onConfirm,
                onEdit = onEditPreview,
                onCancel = onDismissDraft,
            )
        }
    }
}

/**
 * The surface you write on.
 *
 * It is deliberately not a text field in appearance: same typography, padding, and alignment as
 * a logged row, so what you type reads as part of the page and stays in place when it turns
 * into real entries. As the page composer it claims the rest of the sheet and takes as many
 * lines as you want - Return breaks a line instead of submitting, so several foods can be
 * written out before anything is sent. Rewriting one existing row keeps the compact form with
 * its own send action, because there it sits between other entries.
 *
 * [initialCaret] is where the words were touched. It only seeds the caret; from then on the
 * caret belongs to the field, so typing in the middle of a sentence stays where it is.
 */
@Composable
private fun InlineComposerCanvas(
    text: String,
    autoFocus: Boolean,
    fillsPage: Boolean,
    onTextChanged: (String) -> Unit,
    onAnalyze: () -> Unit,
    initialCaret: Int? = null,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(autoFocus) {
        if (autoFocus) runCatching { focusRequester.requestFocus() }
    }
    var typed by remember {
        val caret = (initialCaret ?: text.length).coerceIn(0, text.length)
        mutableStateOf(TextFieldValue(text, TextRange(caret)))
    }
    // Text can also change from outside - a draft reopened for correction, a cleared page -
    // and then the field follows it with the caret at the end.
    val value = if (typed.text == text) typed else TextFieldValue(text, TextRange(text.length))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp),
        verticalAlignment = if (fillsPage) Alignment.Top else Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = { updated ->
                typed = updated
                if (updated.text != text) onTextChanged(updated.text)
            },
            modifier = Modifier
                .weight(1f)
                .then(if (fillsPage) Modifier.heightIn(min = 320.dp) else Modifier)
                .focusRequester(focusRequester),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = if (fillsPage) ImeAction.Default else ImeAction.Send,
            ),
            keyboardActions = KeyboardActions(
                onSend = { if (text.isNotBlank()) onAnalyze() },
            ),
            decorationBox = { innerTextField ->
                if (text.isEmpty()) {
                    Text(
                        text = nomiString(
                            "Tell Nomi what you ate",
                            "Sag Nomi, was du gegessen hast",
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
                innerTextField()
            },
        )
        // The page composer sends from the floating row, so nothing hovers over the sheet.
        AnimatedVisibility(visible = !fillsPage && text.isNotBlank()) {
            IconButton(
                onClick = onAnalyze,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = nomiString("Analyze meal", "Mahlzeit analysieren"),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun ProcessingNote(
    description: String,
    sourceUrls: List<String>,
    onEditText: () -> Unit,
    onCancel: () -> Unit,
) {
    val sourcesReady = sourceUrls.isNotEmpty()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.38f),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = description.ifBlank {
                            nomiString("Understanding your meal", "Deine Mahlzeit wird ausgewertet")
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    AnimatedContent(
                        targetState = sourcesReady,
                        label = "research source status",
                    ) { ready ->
                        Text(
                            text = if (ready) {
                                nomiString(
                                    "Checking sources and portions",
                                    "Quellen und Portionen werden abgeglichen",
                                )
                            } else {
                                nomiString(
                                    "Preparing a careful lookup",
                                    "Sorgfältige Recherche wird vorbereitet",
                                )
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
                AnimatedWebsiteIconStack(
                    sourceUrls = sourceUrls,
                    maxIcons = 3,
                )
            }
            Row(
                modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(onClick = onEditText) { Text(nomiString("Edit text", "Text bearbeiten")) }
                TextButton(onClick = onCancel) { Text(nomiString("Cancel", "Abbrechen")) }
            }
        }
    }
}

@Composable
private fun PreviewNote(
    analysis: FoodAnalysis,
    description: String,
    onAdd: () -> Unit,
    onEditText: () -> Unit,
    onEditPreview: () -> Unit,
    onCancel: () -> Unit,
) {
    val totalCalories = analysis.items.sumOf(AnalyzedFoodItem::calories)
    val totalProtein = analysis.items.sumOf(AnalyzedFoodItem::proteinGrams)
    val totalCarbohydrates = analysis.items.sumOf(AnalyzedFoodItem::carbohydrateGrams)
    val totalFat = analysis.items.sumOf(AnalyzedFoodItem::fatGrams)
    val locale = nomiLocale()
    val estimatedSuffix = nomiString(" · estimated", " · geschätzt")

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = description.ifBlank {
                            analysis.items.joinToString { it.name }
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    TextButton(
                        onClick = onEditText,
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp),
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(nomiString("Change wording", "Formulierung ändern"))
                    }
                }
                Text(
                    text = "${totalCalories.roundToInt()} kcal",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.End,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))

            analysis.items.forEach { item ->
                val quantityDisplay = item.quantityDisplay(locale)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = quantityDisplay.withContext +
                                if (item.isEstimate) estimatedSuffix else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        quantityDisplay.sourceConflictNote?.let { note ->
                            Text(
                                text = note,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    }
                    Text(
                        text = "${item.calories.roundToInt()} kcal",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.End,
                    )
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(18.dp),
            ) {
                Text(
                    text = nomiString(
                        "C ${totalCarbohydrates.roundToInt()} g  ·  P ${totalProtein.roundToInt()} g  ·  F ${totalFat.roundToInt()} g",
                        "K ${totalCarbohydrates.roundToInt()} g  ·  E ${totalProtein.roundToInt()} g  ·  F ${totalFat.roundToInt()} g",
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onCancel) { Text(nomiString("Cancel", "Abbrechen")) }
                    FilledTonalButton(onClick = onEditPreview) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(nomiString("Edit", "Bearbeiten"), maxLines = 1)
                    }
                }
                Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(nomiString("Add", "Hinzufügen"), maxLines = 1)
                }
            }
        }
    }
}

/**
 * A photo's reading, offered as words before anything is looked up.
 *
 * This is the cheap moment to disagree with the camera. Editing here costs one word; editing
 * after research means throwing away a web search and running another. The note deliberately
 * looks like the meal already written on the page, because that is what it is.
 */
@Composable
private fun PhotoReviewNote(
    state: FoodLoggingUiState.PhotoReview,
    onDescriptionChanged: (String) -> Unit,
    onPlaceChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = hairlineOnPitchBlack(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = nomiString("From your photo", "Aus deinem Foto"),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = state.description,
                onValueChange = onDescriptionChanged,
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 6,
                textStyle = MaterialTheme.typography.bodyLarge,
                placeholder = {
                    Text(nomiString("Describe what you ate", "Beschreibe, was du gegessen hast"))
                },
            )
            OutlinedTextField(
                value = state.place,
                onValueChange = onPlaceChanged,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = {
                    Text(nomiString("Restaurant (optional)", "Restaurant (optional)"))
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (state.canContinue) onConfirm() }),
            )
            Text(
                text = nomiString(
                    "Fix anything Nomi misread, then look up the nutrition.",
                    "Korrigiere, was Nomi falsch gelesen hat, und suche dann die Nährwerte.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onConfirm,
                enabled = state.canContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text(nomiString("Find nutrition", "Nährwerte suchen"))
            }
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text(nomiString("Discard", "Verwerfen"))
            }
        }
    }
}

@Composable
private fun ErrorNote(
    description: String,
    message: String,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onEditText: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = description.ifBlank {
                            nomiString("Couldn’t understand this meal", "Diese Mahlzeit wurde nicht erkannt")
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (canRetry) {
                    Button(
                        onClick = onRetry,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(
                            text = nomiString("Retry", "Erneut versuchen"),
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = nomiString("Cancel", "Abbrechen"),
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                    FilledTonalButton(
                        onClick = onEditText,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(
                            text = nomiString("Edit", "Bearbeiten"),
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ManualDraftNote(
    state: FoodLoggingUiState.Manual,
    onAdd: () -> Unit,
    onEdit: () -> Unit,
    onCancel: () -> Unit,
) {
    val draft = state.draft
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = draft.name.ifBlank { nomiString("Manual food", "Manueller Eintrag") },
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                draft.calories.toDoubleOrNull()?.let {
                    Text(
                        text = "${it.roundToInt()} kcal",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = onCancel) { Text(nomiString("Cancel", "Abbrechen")) }
                FilledTonalButton(onClick = onEdit) { Text(nomiString("Edit", "Bearbeiten")) }
                Button(onClick = onAdd, enabled = draft.isValid) {
                    Text(nomiString("Add", "Hinzufügen"))
                }
            }
        }
    }
}

/**
 * The resting state of the page: one floating row holding the remaining calories and the ways
 * to add food. Nothing else competes with the writing surface above it.
 */
@Composable
private fun NotesFloatingActionRow(
    state: TodayUiState,
    canSend: Boolean,
    onGoals: () -> Unit,
    onVoice: () -> Unit,
    onMore: () -> Unit,
    onWrite: () -> Unit,
    onSend: () -> Unit,
) {
    val locale = nomiLocale()
    val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    // The remaining calories settle into their new value instead of snapping when an entry
    // is added, removed, or rescaled.
    val animatedDifference by animateFloatAsState(
        targetValue = state.caloriesDifference.toFloat(),
        animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
        label = "calories left",
    )
    val difference = animatedDifference.roundToInt()
    val calorieText = if (difference >= 0) {
        nomiString("${difference.formatted(locale)} left", "${difference.formatted(locale)} übrig")
    } else {
        nomiString("${abs(difference).formatted(locale)} over", "${abs(difference).formatted(locale)} darüber")
    }
    Row(
        modifier = Modifier
            .widthIn(max = 760.dp)
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onGoals,
            modifier = Modifier.weight(1f),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = hairlineOnPitchBlack(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.LocalFireDepartment,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = calorieText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        NotesCircleAction(
            icon = Icons.Default.Mic,
            description = nomiString("Describe food by voice", "Essen per Sprache beschreiben"),
            onClick = onVoice,
        )
        NotesCircleAction(
            icon = Icons.Default.Add,
            description = nomiString("More ways to add food", "Weitere Möglichkeiten zum Hinzufügen"),
            onClick = onMore,
        )
        // Once there is something written, the same slot becomes the way to send it, and it
        // trades places rather than blinking.
        AnimatedContent(
            targetState = canSend,
            transitionSpec = {
                (fadeIn(animationSpec = effectsSpec) +
                    scaleIn(animationSpec = effectsSpec, initialScale = 0.72f))
                    .togetherWith(
                        fadeOut(animationSpec = effectsSpec) +
                            scaleOut(animationSpec = effectsSpec, targetScale = 0.72f),
                    )
            },
            label = "composer action",
        ) { sendable ->
            if (sendable) {
                NotesCircleAction(
                    icon = Icons.AutoMirrored.Filled.Send,
                    description = nomiString("Analyze meal", "Mahlzeit analysieren"),
                    onClick = onSend,
                    emphasized = true,
                )
            } else {
                NotesCircleAction(
                    icon = Icons.Default.Keyboard,
                    description = nomiString(
                        "Write what you ate",
                        "Schreiben, was du gegessen hast",
                    ),
                    onClick = onWrite,
                )
            }
        }
    }
}

@Composable
private fun NotesCircleAction(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    emphasized: Boolean = false,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (emphasized) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = if (emphasized) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        border = if (emphasized) null else hairlineOnPitchBlack(),
    ) {
        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = description)
        }
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickAddSheet(onDismiss: () -> Unit, onSelect: (AddFoodMethod) -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // Skipping the half-open stop removes the mid-flight settle that made the sheet look
        // like it hesitated on the way up.
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        ) {
            Text(
                text = nomiString("Add food another way", "Essen anders hinzufügen"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .semantics { heading() },
            )
            QuickAddRow(
                icon = Icons.Default.CameraAlt,
                title = nomiString("Photo", "Foto"),
                description = nomiString("Recognize a meal from a photo", "Mahlzeit auf einem Foto erkennen"),
                onClick = { onSelect(AddFoodMethod.PHOTO) },
            )
            QuickAddRow(
                icon = Icons.Default.RestaurantMenu,
                title = nomiString("Scan menu", "Speisekarte scannen"),
                description = nomiString(
                    "Photograph pages, search every dish, then log one",
                    "Seiten fotografieren, alle Gerichte durchsuchen und eines eintragen",
                ),
                onClick = { onSelect(AddFoodMethod.MENU) },
            )
            QuickAddRow(
                icon = Icons.Default.QrCodeScanner,
                title = nomiString("Barcode", "Barcode"),
                description = nomiString("Scan a packaged food", "Verpacktes Lebensmittel scannen"),
                onClick = { onSelect(AddFoodMethod.BARCODE) },
            )
            QuickAddRow(
                icon = Icons.Default.Article,
                title = nomiString("Nutrition label", "Nährwerttabelle"),
                description = nomiString(
                    "Read the printed values, no research",
                    "Gedruckte Werte ablesen, ohne Recherche",
                ),
                onClick = { onSelect(AddFoodMethod.LABEL) },
            )
            QuickAddRow(
                icon = Icons.Default.History,
                title = nomiString("Recent", "Zuletzt verwendet"),
                description = nomiString("Log something again", "Etwas erneut eintragen"),
                onClick = { onSelect(AddFoodMethod.RECENT) },
            )
            QuickAddRow(
                icon = Icons.Default.FavoriteBorder,
                title = nomiString("Favorites", "Favoriten"),
                description = nomiString("Choose one of your starred foods", "Ein favorisiertes Lebensmittel auswählen"),
                onClick = { onSelect(AddFoodMethod.FAVORITES) },
            )
            QuickAddRow(
                icon = Icons.Default.RestaurantMenu,
                title = nomiString("Saved meals", "Gespeicherte Mahlzeiten"),
                description = nomiString("Reuse a complete meal", "Eine vollständige Mahlzeit erneut verwenden"),
                onClick = { onSelect(AddFoodMethod.SAVED_MEALS) },
            )
        }
    }
}

@Composable
private fun QuickAddRow(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    // A real ListItem rather than a hand-built row, so it inherits Material's own heights,
    // spacing and text colours and stays right when the theme or the font scale changes.
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        },
        headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text(description) },
        trailingContent = {
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        },
    )
}

/**
 * The day's goals.
 *
 * Calories carry the headline because they are what the day is actually about; the macros sit
 * below as three equals. The old sheet gave all four the same weight and then repeated the
 * whole thing in a summary pill you had to tap to close - a workaround for an affordance a
 * bottom sheet already has. Swiping it down is the way out now, so nothing has to explain
 * itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalsSheet(state: TodayUiState, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // Skipping the half-open stop removes the mid-flight settle that made the sheet look
        // like it hesitated on the way up.
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = nomiString("Goals", "Ziele"),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .padding(bottom = 4.dp)
                    .semantics { heading() },
            )
            if (state.goalsCardStyle == GoalsCardStyle.RINGS) {
                // One compact card instead of three stacked ones: calories as a bar, every
                // other target as a ring, water underneath.
                GoalsRingCard(state)
            } else {
                CalorieGoalCard(state)
                MacroGoalCard(state)
                if (state.micronutrients.isNotEmpty()) {
                    MicronutrientGoalCard(state.micronutrients)
                }
            }
        }
    }
}

@Composable
private fun CalorieGoalCard(state: TodayUiState) {
    val locale = nomiLocale()
    val difference = state.caloriesDifference.roundToInt()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = hairlineOnPitchBlack(),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = abs(difference).formatted(locale),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    // Over the target is stated, never coloured as a warning. Nomi keeps the
                    // log and has no opinion about the number in it.
                    text = if (difference >= 0) {
                        nomiString("kcal left today", "kcal heute übrig")
                    } else {
                        nomiString("kcal over today", "kcal heute darüber")
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            GoalWave(fraction = state.calorieFraction, color = MaterialTheme.colorScheme.primary)
            Text(
                text = "${state.caloriesConsumed.roundToInt().formatted(locale)} / " +
                    "${state.calorieTarget.roundToInt().formatted(locale)} kcal",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MacroGoalCard(state: TodayUiState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = hairlineOnPitchBlack(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            MacroGoalRow(
                label = nomiString("Carbs", "Kohlenhydrate"),
                progress = state.carbohydrates,
                color = MaterialTheme.colorScheme.error,
            )
            MacroGoalRow(
                label = nomiString("Protein", "Eiweiß"),
                progress = state.protein,
                color = MaterialTheme.colorScheme.tertiary,
            )
            MacroGoalRow(
                label = nomiString("Fat", "Fett"),
                progress = state.fat,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

/**
 * The nutrients the user chose to follow, shown the same way the macros are so the day reads as
 * one picture. Fiber is a target to reach; sugar, saturated fat, and sodium are ceilings, and a
 * crossed ceiling is coloured rather than merely full.
 */
@Composable
private fun MicronutrientGoalCard(progress: List<MicronutrientProgress>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = hairlineOnPitchBlack(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            progress.forEach { entry ->
                MicronutrientGoalRow(entry)
            }
        }
    }
}

@Composable
private fun MicronutrientGoalRow(progress: MicronutrientProgress) {
    val locale = nomiLocale()
    val suffix = progress.nutrient.storageUnit.suffix
    val color = when {
        progress.isOverLimit -> MaterialTheme.colorScheme.error
        progress.nutrient.isLimit -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.tertiary
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = progress.nutrient.localizedName(),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = progress.consumed?.let { amount ->
                    "${amount.roundToInt().formatted(locale)} / " +
                        "${progress.target.roundToInt().formatted(locale)} $suffix"
                } ?: nomiString("No data yet", "Noch keine Daten"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        GoalWave(fraction = progress.fraction, color = color)
        if (progress.isPartial) {
            Text(
                text = nomiString(
                    "Some of today's foods didn't publish this value, so the real total is higher.",
                    "Für einige Lebensmittel von heute wurde kein Wert veröffentlicht, die tatsächliche Summe liegt also höher.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MacroGoalRow(label: String, progress: MacroProgress, color: Color) {
    val locale = nomiLocale()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${progress.consumedGrams.roundToInt().formatted(locale)} / " +
                    "${progress.targetGrams.roundToInt().formatted(locale)} g",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        GoalWave(fraction = progress.fraction, color = color)
    }
}

/**
 * Material 3's wavy indicator, filling from empty every time the sheet opens rather than
 * appearing already full, so the day is something you watch arrive at its number.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GoalWave(fraction: Float, color: Color) {
    val filled = remember { Animatable(0f) }
    val progressSpec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(fraction) {
        filled.animateTo(targetValue = fraction, animationSpec = progressSpec)
    }
    LinearWavyProgressIndicator(
        progress = { filled.value },
        modifier = Modifier.fillMaxWidth(),
        color = color,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    )
}

private fun cleanAmount(value: Double, locale: Locale): String =
    if (value % 1.0 == 0.0) value.roundToInt().toString() else String.format(locale, "%.1f", value)

private fun Int.formatted(locale: Locale): String = String.format(locale, "%,d", this)

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
