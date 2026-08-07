package com.nomi.app.ui.history

import com.nomi.app.ui.today.TodayFoodEntry
import java.time.LocalDate

data class HistoryDay(
    val date: LocalDate,
    val calories: Double,
    val calorieTarget: Double,
    val proteinGrams: Double,
    val carbohydrateGrams: Double,
    val fatGrams: Double,
    val entries: List<TodayFoodEntry>,
)

data class HistoryUiState(
    val query: String = "",
    val selectedDate: LocalDate = LocalDate.now(),
    val visibleDays: List<HistoryDay> = emptyList(),
    val isSearching: Boolean = false,
)
