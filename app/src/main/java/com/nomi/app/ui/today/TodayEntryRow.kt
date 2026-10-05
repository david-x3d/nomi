package com.nomi.app.ui.today

import androidx.compose.runtime.Composable
import com.nomi.app.ui.feedback.rememberNomiHaptics
import com.nomi.app.ui.logging.FoodLoggingUiState

/** Rendering one row does not own the deletion operation or its undo window. */
@Composable
internal fun TodayEntryRow(
    entry: TodayFoodEntry,
    deletion: PendingFoodDeletion?,
    editedEntryId: Long?,
    loggingState: FoodLoggingUiState,
    caret: Int,
    onCaretChanged: (Int) -> Unit,
    onCloseComposer: () -> Unit,
    onTextChanged: (String) -> Unit,
    onAnalyze: () -> Unit,
    onDismissDraft: () -> Unit,
    onDeleteFoodImmediately: (Long) -> Unit,
    onFoodClick: (Long) -> Unit,
    onEditEntryText: (TodayFoodEntry) -> Unit,
    onDeleteFood: (TodayFoodEntry) -> Unit,
    onDuplicateFood: (Long) -> Unit,
    onFavoriteFood: (Long) -> Unit,
    onEditFoodAmount: (TodayFoodEntry) -> Unit,
    onUndoDeleteFood: (Long) -> Unit,
) {
    val haptics = rememberNomiHaptics()
    val pending = deletion
    val editingThisEntry = entry.id == editedEntryId &&
        loggingState is FoodLoggingUiState.Input
    when {
        // The row becomes the line you write on, so a rewrite happens
        // where the entry already sits.
        editingThisEntry -> InlineComposerCanvas(
            text = loggingState.text,
            autoFocus = true,
            fillsPage = false,
            initialCaret = caret,
            onTextChanged = onTextChanged,
            onAnalyze = { haptics.sent(); onCloseComposer(); onAnalyze() },
            onEmptied = {
                haptics.removed()
                onDismissDraft()
                onDeleteFoodImmediately(entry.id)
            },
        )
        pending == null -> SwipeToDeleteFoodRow(
            entry = entry,
            onOpenDetails = {
                haptics.selected()
                onFoodClick(entry.id)
            },
            onEditText = { caret ->
                haptics.selected()
                onCaretChanged(caret)
                onEditEntryText(entry)
            },
            onDelete = {
                haptics.removed()
                onDeleteFood(entry)
            },
            onDuplicate = { onDuplicateFood(entry.id) },
            onFavorite = { onFavoriteFood(entry.id) },
            onEditAmount = { onEditFoodAmount(entry) },
        )
        pending.isRestoring -> RestoringFoodRow(entry)
        else -> InlineDeletedFoodRow(
            entry = entry,
            onUndo = {
                haptics.confirmed()
                onUndoDeleteFood(entry.id)
            },

        )
    }
}
