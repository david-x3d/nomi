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
     * This is what decides how many "copy" chips a day shows. A day with breakfast and dinner gets
     * two; a day with only one meal gets one; an empty day gets none, so the row is never
     * rendered with a chip that could not do anything.
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
