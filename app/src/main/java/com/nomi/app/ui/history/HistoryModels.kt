package com.nomi.app.ui.history

import com.nomi.app.ui.today.MealCategory
import com.nomi.app.ui.today.TodayFoodEntry
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

data class HistoryDay(
    val date: LocalDate,
    val calories: Double,
    val calorieTarget: Double,
    val proteinGrams: Double,
    val carbohydrateGrams: Double,
    val fatGrams: Double,
    val entries: List<TodayFoodEntry>,
) {
    /**
     * The meal categories actually eaten that day, in the day's own order rather than in enum
     * declaration order.
     *
     * De-duplicated, so two breakfasts are one breakfast, and empty for a day with nothing on it.
     * v2.4.0 used this to decide how many per-meal copy chips a day showed; those are gone, and
     * what remains is the day's own shape - which is what the copy and selection actions are
     * resolved against.
     */
    val mealCategories: List<MealCategory> = entries.asSequence()
        .map(TodayFoodEntry::mealCategory)
        .distinct()
        .sortedBy(MealCategory::ordinal)
        .toList()

    /** True when [date] is the day being copied into, so copying it would only duplicate it. */
    fun isSameDayAs(other: LocalDate): Boolean = date == other
}

data class HistoryUiState(
    val query: String = "",
    val selectedDate: LocalDate = LocalDate.now(),
    val visibleDays: List<HistoryDay> = emptyList(),
    val isSearching: Boolean = false,
)

/**
 * What History is currently asking the user to pick, and why.
 *
 * The two actions differ only in what happens to the selection, so they share one mode: one picker,
 * one set of selected ids, one confirm button whose label is derived from the mode and the count.
 */
enum class HistorySelectionAction {
    SAVE_MEAL,
    ADD_TO_TODAY,
}

/**
 * A temporary selection inside the History browser.
 *
 * History is still a browser while this is active: the same rows are on screen, only their tap
 * meaning changes. Selection is scoped to one day because that is the unit both actions operate on
 * - "save *that* day" and "add *that* day's foods" - and a selection that silently reached across
 * days would let a user assemble a meal out of two unrelated days without being able to see that
 * is what they did.
 *
 * A null selection of this type is the browsing state, so the picker needs no separate boolean and
 * cannot get out of step with itself.
 */
data class HistorySelection(
    val day: LocalDate,
    val action: HistorySelectionAction,
    val selectedRowIds: Set<Long> = emptySet(),
) {
    val count: Int get() = selectedRowIds.size

    val isEmpty: Boolean get() = selectedRowIds.isEmpty()

    fun isSelected(rowId: Long): Boolean = rowId in selectedRowIds

    /** One tap toggles, so a single wanted food takes a single tap. */
    fun toggled(rowId: Long): HistorySelection = if (rowId in selectedRowIds) {
        copy(selectedRowIds = selectedRowIds - rowId)
    } else {
        copy(selectedRowIds = selectedRowIds + rowId)
    }
}

/**
 * The log rows a visible History row stands for.
 *
 * A meal logged as one combined entry is shown as a single row, and picking that row picks the
 * whole meal - the products were eaten together, so saving or copying half of them would invent a
 * meal nobody ate. Everything else is the row's own log.
 */
fun TodayFoodEntry.logIdsForSelection(): List<Long> =
    groupItems.ifEmpty { listOf(this) }.map(TodayFoodEntry::id)

/**
 * Expands the picked rows into the log ids both actions hand to the repository.
 *
 * Only rows the user actually tapped contribute, which is what makes "save only what I selected"
 * and "add only what I selected" true rather than aspirational.
 */
fun HistoryDay.logIdsFor(rowIds: Set<Long>): List<Long> =
    entries.asSequence()
        .filter { it.id in rowIds }
        .flatMap { it.logIdsForSelection().asSequence() }
        .distinct()
        .toList()

/** The catalogue key for the confirm button, which counts what is selected. */
fun HistorySelectionAction.confirmLabelKey(count: Int): String = when (this) {
    HistorySelectionAction.SAVE_MEAL ->
        if (count == 1) "Save {0} item" else "Save {0} items"
    HistorySelectionAction.ADD_TO_TODAY ->
        if (count == 1) "Add {0} item to today" else "Add {0} items to today"
}

/** The label the confirm button shows before anything is picked. */
fun HistorySelectionAction.emptyConfirmLabelKey(): String = "Select foods"

/** The one line that explains what tapping a row will do while the picker is open. */
fun HistorySelectionAction.hintKey(): String = when (this) {
    HistorySelectionAction.SAVE_MEAL -> "Tap the foods you want to save."
    HistorySelectionAction.ADD_TO_TODAY -> "Tap the foods you want to add to today."
}

/** The bar title, so the mode is named by the action that opened it. */
fun HistorySelectionAction.titleKey(): String = when (this) {
    HistorySelectionAction.SAVE_MEAL -> "Save meal"
    HistorySelectionAction.ADD_TO_TODAY -> "Add items to today"
}

/**
 * The name a history day gets in the food library when the user saves it without typing one.
 *
 * Derived from the date so the suggestion is already meaningful and, more importantly, non-blank:
 * `saveHistoryDayAsMeal` returns early on a blank name, so a blank suggestion would make the chip
 * look broken.
 *
 * [prefix] is passed in rather than hard-coded because this runs outside composition and cannot
 * reach the catalogue itself; the caller supplies the already-localised word.
 */
fun defaultSavedMealName(
    date: LocalDate,
    locale: Locale = Locale.getDefault(),
    prefix: String = "Meal",
): String {
    val formatted = date.format(
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale),
    )
    return "$prefix · $formatted"
}
