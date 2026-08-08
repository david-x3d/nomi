package com.nomi.app.ui.today

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.nomi.app.R
import com.nomi.app.ai.model.AnalyzedFoodItem
import com.nomi.app.ai.model.FoodAnalysis
import com.nomi.app.ui.components.AnimatedWebsiteIconStack
import com.nomi.app.ui.format.quantityDisplay
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
    onTextChanged: (String) -> Unit,
    onAnalyze: () -> Unit,
    onConfirm: () -> Unit,
    onRetry: () -> Unit,
    onEditText: () -> Unit,
    onEditPreview: () -> Unit,
    onDismissDraft: () -> Unit,
    onQuickMethod: (AddFoodMethod) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showQuickAdd by rememberSaveable { mutableStateOf(false) }
    var showGoals by rememberSaveable { mutableStateOf(false) }
    var composerOpen by rememberSaveable { mutableStateOf(false) }
    val loggingDescription = when (loggingState) {
        is FoodLoggingUiState.Input -> loggingState.text
        is FoodLoggingUiState.Processing -> loggingState.originalText
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
                    onGoals = { showGoals = true },
                    onVoice = { onQuickMethod(AddFoodMethod.VOICE) },
                    onMore = { showQuickAdd = true },
                    onWrite = { composerOpen = true },
                )
            }
        },
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                // The page itself is the writing surface: tapping the empty area below the
                // notes starts a new entry, the way tapping into a note does.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { composerOpen = true },
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
                        when {
                            pending == null -> SwipeToDeleteFoodRow(
                                entry = entry,
                                onClick = { onFoodClick(entry.id) },
                                onDelete = {
                                    pendingDeletedFoods[entry.id] = PendingDeletedFood(entry)
                                    onDeleteFood(entry.id)
                                },
                            )
                            pending.undoRequested -> RestoringFoodRow(entry)
                            else -> InlineDeletedFoodRow(
                                entry = entry,
                                onUndo = {
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
                            composerFocused = composerOpen,
                            onTextChanged = onTextChanged,
                            onAnalyze = onAnalyze,
                            onConfirm = onConfirm,
                            onRetry = onRetry,
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
                        Image(
                            painter = painterResource(R.drawable.nomi_icon_foreground),
                            contentDescription = nomiString("Nomi fox logo", "Nomi-Fuchslogo"),
                            modifier = Modifier.size(44.dp),
                            contentScale = ContentScale.Fit,
                        )
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
                            contentDescription = nomiString("Next day", "NÃ¤chster Tag"),
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

private data class PendingDeletedFood(
    val entry: TodayFoodEntry,
    val undoRequested: Boolean = false,
    val removalObserved: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDeleteFoodRow(
    entry: TodayFoodEntry,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val deleteLabel = nomiString("Delete ${entry.name}", "${entry.name} lÃ¶schen")
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
    val revealScale by animateFloatAsState(
        targetValue = if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) 1f else 0.82f,
        animationSpec = tween(durationMillis = 180),
        label = "Delete reveal",
    )

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
                        text = nomiString("Delete", "LÃ¶schen"),
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
            NotesFoodRow(entry = entry, onClick = onClick)
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
        delay(6_000)
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
                    text = nomiString("Undo", "RÃ¼ckgÃ¤ngig"),
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

@Composable
private fun NotesFoodRow(entry: TodayFoodEntry, onClick: () -> Unit) {
    val description = buildString {
        append(entry.name)
        entry.brand?.takeIf(String::isNotBlank)?.let { append(" Â· ").append(it) }
    }
    val locale = nomiLocale()
    val amountDisplay = entry.quantityDisplay(locale).withContext
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (entry.amountText.isNotBlank() || entry.amount > 0.0) {
                Text(
                    text = amountDisplay,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = buildString {
                if (entry.isEstimated) append("â‰ˆ ")
                append(entry.calories.roundToInt()).append(" kcal")
            },
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
    composerFocused: Boolean,
    onTextChanged: (String) -> Unit,
    onAnalyze: () -> Unit,
    onConfirm: () -> Unit,
    onRetry: () -> Unit,
    onEditText: () -> Unit,
    onEditPreview: () -> Unit,
    onDismissDraft: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        when (state) {
            is FoodLoggingUiState.Input -> InlineComposerLine(
                text = state.text,
                autoFocus = composerFocused,
                onTextChanged = onTextChanged,
                onAnalyze = onAnalyze,
            )
            is FoodLoggingUiState.Processing -> ProcessingNote(
                description = rememberedDescription,
                sourceUrls = state.sourceUrls,
                onEditText = onEditText,
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
 * The line you write on. It is deliberately not a text field in appearance: same typography,
 * padding, and alignment as a logged row, so what you type reads as the next line of the page
 * and stays in place when it turns into a real entry. The calorie slot on the right holds the
 * send action while writing, which is where the calories themselves appear afterwards.
 */
@Composable
private fun InlineComposerLine(
    text: String,
    autoFocus: Boolean,
    onTextChanged: (String) -> Unit,
    onAnalyze: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(autoFocus) {
        if (autoFocus) runCatching { focusRequester.requestFocus() }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BasicTextField(
            value = text,
            onValueChange = onTextChanged,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
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
        AnimatedVisibility(visible = text.isNotBlank()) {
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
    val processingPulse = rememberInfiniteTransition(label = "research pulse")
    val pulseScale by processingPulse.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "research pulse scale",
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = description.ifBlank {
                    nomiString("Understanding your meal", "Deine Mahlzeit wird ausgewertet")
                },
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AnimatedWebsiteIconStack(
                            sourceUrls = sourceUrls,
                            maxIcons = 2,
                        )
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(18.dp)
                                .graphicsLayer { scaleX = pulseScale; scaleY = pulseScale },
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        )
                        Text(
                            text = nomiString("Thinkingâ€¦", "Wird analysiertâ€¦"),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TextButton(onClick = onEditText) { Text(nomiString("Edit text", "Text bearbeiten")) }
            TextButton(onClick = onCancel) { Text(nomiString("Cancel", "Abbrechen")) }
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
    val estimatedSuffix = nomiString(" Â· estimated", " Â· geschÃ¤tzt")

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
                        Text(nomiString("Change wording", "Formulierung Ã¤ndern"))
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
                        "C ${totalCarbohydrates.roundToInt()} g  Â·  P ${totalProtein.roundToInt()} g  Â·  F ${totalFat.roundToInt()} g",
                        "K ${totalCarbohydrates.roundToInt()} g  Â·  E ${totalProtein.roundToInt()} g  Â·  F ${totalFat.roundToInt()} g",
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
                    Text(nomiString("Add", "HinzufÃ¼gen"), maxLines = 1)
                }
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
                            nomiString("Couldnâ€™t understand this meal", "Diese Mahlzeit wurde nicht erkannt")
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
                    Text(nomiString("Add", "HinzufÃ¼gen"))
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
    onGoals: () -> Unit,
    onVoice: () -> Unit,
    onMore: () -> Unit,
    onWrite: () -> Unit,
) {
    val locale = nomiLocale()
    val difference = state.caloriesDifference.roundToInt()
    val calorieText = if (difference >= 0) {
        nomiString("${difference.formatted(locale)} left", "${difference.formatted(locale)} Ã¼brig")
    } else {
        nomiString("${abs(difference).formatted(locale)} over", "${abs(difference).formatted(locale)} darÃ¼ber")
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
            description = nomiString("More ways to add food", "Weitere MÃ¶glichkeiten zum HinzufÃ¼gen"),
            onClick = onMore,
        )
        NotesCircleAction(
            icon = Icons.Default.Keyboard,
            description = nomiString("Write what you ate", "Schreiben, was du gegessen hast"),
            onClick = onWrite,
        )
    }
}

@Composable
private fun NotesCircleAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = hairlineOnPitchBlack(),
    ) {
        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = description)
        }
    }
}

/**
 * On true-black themes the surface tones are flattened toward the canvas, so a hairline
 * outline carries the separation that tonal contrast normally provides.
 */
@Composable
private fun hairlineOnPitchBlack(): BorderStroke? = when {
    LocalPitchBlackSurfaces.current ->
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    else -> null
}


@Composable
private fun NutritionSummaryPill(state: TodayUiState, onClick: () -> Unit) {
    val difference = state.caloriesDifference.roundToInt()
    val locale = nomiLocale()
    val calorieText = if (difference >= 0) {
        nomiString("${difference.formatted(locale)} left", "${difference.formatted(locale)} Ã¼brig")
    } else {
        nomiString("${abs(difference).formatted(locale)} over", "${abs(difference).formatted(locale)} darÃ¼ber")
    }
    val openGoalsDescription = nomiString("Open nutrition goals", "ErnÃ¤hrungsziele Ã¶ffnen")
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = openGoalsDescription },
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp,
        border = hairlineOnPitchBlack(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SummaryMetric("$calorieText Â·", MaterialTheme.colorScheme.primary)
            SummaryMetric(
                nomiString(
                    "C ${state.carbohydrates.consumedGrams.roundToInt()}",
                    "K ${state.carbohydrates.consumedGrams.roundToInt()}",
                ),
                MaterialTheme.colorScheme.error,
            )
            SummaryMetric(
                nomiString(
                    "Â· P ${state.protein.consumedGrams.roundToInt()}",
                    "Â· E ${state.protein.consumedGrams.roundToInt()}",
                ),
                MaterialTheme.colorScheme.tertiary,
            )
            SummaryMetric("Â· F ${state.fat.consumedGrams.roundToInt()}", MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
private fun SummaryMetric(text: String, color: Color) {
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    AnimatedContent(
        targetState = text,
        modifier = Modifier.padding(horizontal = 3.dp),
        transitionSpec = {
            (slideInVertically(animationSpec = spatialSpec) { height -> height / 2 } +
                fadeIn(animationSpec = effectsSpec)).togetherWith(
                slideOutVertically(animationSpec = spatialSpec) { height -> -height / 2 } +
                    fadeOut(animationSpec = effectsSpec),
            )
        },
        label = "nutrition summary value",
    ) { displayedText ->
        Text(
            text = displayedText,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickAddSheet(onDismiss: () -> Unit, onSelect: (AddFoodMethod) -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        ) {
            Text(
                text = nomiString("Add food another way", "Essen anders hinzufÃ¼gen"),
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
                icon = Icons.Default.QrCodeScanner,
                title = nomiString("Barcode", "Barcode"),
                description = nomiString("Scan a packaged food", "Verpacktes Lebensmittel scannen"),
                onClick = { onSelect(AddFoodMethod.BARCODE) },
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
                description = nomiString("Choose one of your starred foods", "Ein favorisiertes Lebensmittel auswÃ¤hlen"),
                onClick = { onSelect(AddFoodMethod.FAVORITES) },
            )
            QuickAddRow(
                icon = Icons.Default.RestaurantMenu,
                title = nomiString("Saved meals", "Gespeicherte Mahlzeiten"),
                description = nomiString("Reuse a complete meal", "Eine vollstÃ¤ndige Mahlzeit erneut verwenden"),
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 72.dp)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalsSheet(state: TodayUiState, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = nomiString("Goals", "Ziele"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 1.dp,
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    GoalProgressRow(
                        label = nomiString("Calories", "Kalorien"),
                        consumed = state.caloriesConsumed,
                        target = state.calorieTarget,
                        unit = "",
                        color = MaterialTheme.colorScheme.primary,
                    )
                    GoalProgressRow(
                        label = nomiString("Carbs", "Kohlenhydrate"),
                        consumed = state.carbohydrates.consumedGrams,
                        target = state.carbohydrates.targetGrams,
                        unit = "g",
                        color = MaterialTheme.colorScheme.error,
                    )
                    GoalProgressRow(
                        label = nomiString("Protein", "EiweiÃŸ"),
                        consumed = state.protein.consumedGrams,
                        target = state.protein.targetGrams,
                        unit = "g",
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    GoalProgressRow(
                        label = nomiString("Fat", "Fett"),
                        consumed = state.fat.consumedGrams,
                        target = state.fat.targetGrams,
                        unit = "g",
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
            NutritionSummaryPill(state = state, onClick = onDismiss)
            Text(
                text = nomiString("Tap the summary to close", "Zum SchlieÃŸen auf die Ãœbersicht tippen"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            )
        }
    }
}

@Composable
private fun GoalProgressRow(
    label: String,
    consumed: Double,
    target: Double,
    unit: String,
    color: Color,
) {
    val fraction = if (target <= 0.0) 0f else (consumed / target).toFloat().coerceIn(0f, 1f)
    val animatedFraction = remember { Animatable(0f) }
    val progressSpec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    val locale = nomiLocale()
    LaunchedEffect(fraction) {
        animatedFraction.animateTo(
            targetValue = fraction,
            animationSpec = progressSpec,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(12.dp),
                shape = CircleShape,
                color = color,
            ) {}
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${consumed.roundToInt().formatted(locale)} / ${target.roundToInt().formatted(locale)}$unit",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End,
            )
        }
        LinearProgressIndicator(
            progress = { animatedFraction.value },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    }
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
            brand = "Dominoâ€™s",
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

@Preview(name = "Nomi notes â€” light", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun NomiNotesLightPreview() {
    NomiTheme(darkTheme = false, dynamicColor = false) {
        NomiNotesTodayScreen(
            state = sampleTodayState(),
            loggingState = FoodLoggingUiState.Input("one salami pizza by Dominoâ€™s"),
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

@Preview(name = "Nomi notes â€” dark preview", showBackground = true, widthDp = 412, heightDp = 915)
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
