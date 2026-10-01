package com.nomi.app.ui.today

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.nomi.app.ui.feedback.rememberNomiHaptics
import com.nomi.app.ui.format.quantityDisplay
import com.nomi.app.ui.localization.nomiFormat
import com.nomi.app.ui.localization.nomiLocale
import com.nomi.app.ui.localization.nomiString
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * The logged rows of the Today page: the line itself, its swipe-to-delete wrapper and the short
 * undo window a deleted row keeps its place for.
 */

/**
 * How long a deleted row stays on the page offering to come back.
 *
 * The row still occupies its place while it waits, so the page does not settle until it goes:
 * a long window reads as the list being stuck rather than as a generous offer. Two seconds
 * still catch an accidental swipe without holding the deleted line on screen for long.
 */
private const val UNDO_WINDOW_MILLIS = 2_000L

internal data class PendingDeletedFood(
    val entry: TodayFoodEntry,
    val undoRequested: Boolean = false,
    val removalObserved: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SwipeToDeleteFoodRow(
    entry: TodayFoodEntry,
    onOpenDetails: () -> Unit,
    onEditText: (Int) -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onFavorite: () -> Unit,
    onEditAmount: () -> Unit,
) {
    val deleteLabel = nomiFormat("Delete {0}", entry.name)
    val detailsLabel = nomiFormat("Nutrition for {0}", entry.name)
    val editLabel = nomiFormat("Rewrite {0}", entry.name)
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
                        text = nomiString("Delete"),
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
            NotesFoodRow(
                entry = entry,
                onOpenDetails = onOpenDetails,
                onEditText = onEditText,
                onDuplicate = onDuplicate,
                onFavorite = onFavorite,
                onEditAmount = onEditAmount,
                onDelete = onDelete,
            )
        }
    }
}

@Composable
internal fun InlineDeletedFoodRow(
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
                    text = nomiString("Undo"),
                    maxLines = 1,
                    softWrap = false,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
internal fun RestoringFoodRow(entry: TodayFoodEntry) {
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
                text = nomiFormat("Restoring {0}", entry.name),
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
    onDuplicate: () -> Unit,
    onFavorite: () -> Unit,
    onEditAmount: () -> Unit,
    onDelete: () -> Unit,
) {
    val haptics = rememberNomiHaptics()
    var showQuickActions by remember(entry.id) { mutableStateOf(false) }
    fun closeQuickActions() {
        showQuickActions = false
    }
    val finalDescription = entry.rowDescription()
    val originalDescription = entry.revealText?.trim()
        ?.takeIf { it.isNotBlank() && it != finalDescription }
    var showOriginal by remember(entry.id, originalDescription) {
        mutableStateOf(originalDescription != null)
    }
    val summarySweep = remember(entry.id) { Animatable(1.35f) }
    val calorieSweep = remember(entry.id) { Animatable(1.35f) }
    LaunchedEffect(originalDescription) {
        if (originalDescription == null) {
            showOriginal = false
            summarySweep.snapTo(1.35f)
            calorieSweep.snapTo(1.35f)
        } else {
            showOriginal = true
            summarySweep.snapTo(-0.35f)
            calorieSweep.snapTo(-0.35f)
            coroutineScope {
                launch {
                    calorieSweep.animateTo(
                        targetValue = 1.35f,
                        animationSpec = tween(durationMillis = 1_100, easing = LinearEasing),
                    )
                }
                launch {
                    delay(320)
                    // The warm sweep belongs to the AI's finished short label, never to the
                    // longer sentence while it is being replaced.
                    showOriginal = false
                    summarySweep.animateTo(
                        targetValue = 1.35f,
                        animationSpec = tween(durationMillis = 1_050, easing = LinearEasing),
                    )
                }
            }
        }
    }
    val description = if (showOriginal) originalDescription.orEmpty() else finalDescription
    val locale = nomiLocale()
    val isGrouped = entry.groupItems.size > 1
    val amountDisplay = entry.quantityDisplay(locale).withContext
    val detailsLabel = nomiFormat("Nutrition for {0}", entry.name)
    val writeLabel = nomiFormat("Rewrite {0}", entry.name)
    val primaryClickLabel = writeLabel
    val summaryProgress = summarySweep.value
    val summaryActive = originalDescription != null && !showOriginal && summaryProgress < 1.34f
    val calorieProgress = calorieSweep.value
    val calorieActive = originalDescription != null && calorieProgress < 1.34f
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Past the end of the words the line still opens for writing, with the caret at
                // the end, the way tapping the empty part of a note's line behaves.
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = primaryClickLabel,
                    onLongClickLabel = nomiString("Quick actions"),
                    onLongClick = {
                        haptics.held()
                        showQuickActions = true
                    },
                    onClick = {
                        onEditText(entry.reeditableText().length)
                    },
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
                    modifier = Modifier
                        .oneShotTextGradient(
                            active = summaryActive,
                            progress = summaryProgress,
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary,
                            ),
                        )
                        .pointerInput(entry.id, description) {
                            detectTapGestures(
                                onLongPress = {
                                    haptics.held()
                                    showQuickActions = true
                                },
                                onTap = { position ->
                                    if (isGrouped) {
                                        onEditText(entry.reeditableText().length)
                                    } else {
                                        val tapped = descriptionLayout
                                            ?.getOffsetForPosition(position) ?: description.length
                                        onEditText(entry.reeditableCaretForDescription(tapped))
                                    }
                                },
                            )
                        },
                    onTextLayout = { descriptionLayout = it },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (entry.amountText.isNotBlank() || entry.amount > 0.0) {
                    var amountLayout by remember(entry.id) {
                        mutableStateOf<TextLayoutResult?>(null)
                    }
                    Text(
                        text = amountDisplay,
                        modifier = Modifier.pointerInput(entry.id, amountDisplay) {
                            detectTapGestures(
                                onLongPress = {
                                    haptics.held()
                                    showQuickActions = true
                                },
                                onTap = { position ->
                                    if (isGrouped) {
                                        onOpenDetails()
                                    } else {
                                        val tapped = amountLayout
                                            ?.getOffsetForPosition(position) ?: 0
                                        onEditText(entry.reeditableCaretForAmount(tapped))
                                    }
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
                // The calories are the way into details now that the words belong to the keyboard.
                modifier = Modifier
                    .oneShotTextGradient(
                        active = calorieActive,
                        progress = calorieProgress,
                        colors = listOf(
                            MaterialTheme.colorScheme.secondary,
                            MaterialTheme.colorScheme.primary,
                        ),
                    )
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
            )
        }
        DropdownMenu(
            expanded = showQuickActions,
            onDismissRequest = { closeQuickActions() },
            // A context menu belongs to the touched row, not to the page's left edge. Anchoring
            // its popup at the row's trailing centre lets it float beside the content while the
            // position provider still keeps it safely inside narrow screens.
            modifier = Modifier.align(Alignment.CenterEnd),
            offset = DpOffset(x = (-12).dp, y = 0.dp),
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 14.dp,
        ) {
            DropdownMenuItem(
                text = { Text(nomiString("Duplicate")) },
                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                onClick = {
                    closeQuickActions()
                    haptics.confirmed()
                    onDuplicate()
                },
            )
            DropdownMenuItem(
                text = { Text(nomiString("Change amount")) },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                onClick = {
                    closeQuickActions()
                    haptics.selected()
                    onEditAmount()
                },
            )
            DropdownMenuItem(
                text = { Text(nomiString("Favorite")) },
                leadingIcon = { Icon(Icons.Default.FavoriteBorder, contentDescription = null) },
                onClick = {
                    closeQuickActions()
                    haptics.confirmed()
                    onFavorite()
                },
            )
            DropdownMenuItem(
                text = { Text(nomiString("Delete")) },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                onClick = {
                    closeQuickActions()
                    onDelete()
                },
            )
        }
    }
}

/** A soft halo and brighter core, both clipped to the glyphs so no coloured bar hits the row. */
private fun Modifier.oneShotTextGradient(
    active: Boolean,
    progress: Float,
    colors: List<Color>,
): Modifier {
    if (!active) return this
    return graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val center = size.width * progress
            val glowHalfBand = size.width * 0.48f
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent) +
                        colors.map { it.copy(alpha = 0.20f) } +
                        Color.Transparent,
                    startX = center - glowHalfBand,
                    endX = center + glowHalfBand,
                ),
                blendMode = BlendMode.SrcAtop,
            )
            val coreHalfBand = size.width * 0.30f
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        colors.first().copy(alpha = 0.88f),
                        Color.White.copy(alpha = 0.30f),
                        colors.last().copy(alpha = 0.88f),
                        Color.Transparent,
                    ),
                    startX = center - coreHalfBand,
                    endX = center + coreHalfBand,
                ),
                blendMode = BlendMode.SrcAtop,
            )
        }
}
