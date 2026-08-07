package com.nomi.app.ui.today

import java.time.LocalDate
import java.time.LocalTime

enum class MealCategory(val displayName: String) {
    BREAKFAST("Breakfast"),
    LUNCH("Lunch"),
    DINNER("Dinner"),
    SNACKS("Snacks"),
}

enum class AddFoodMethod(val displayName: String) {
    TYPE("Type"),
    VOICE("Voice"),
    PHOTO("Photo"),
    BARCODE("Barcode"),
    RECENT("Recent"),
    FAVORITES("Favorites"),
    SAVED_MEALS("Saved meals"),
}

data class MacroProgress(
    val consumedGrams: Double,
    val targetGrams: Double,
) {
    val fraction: Float
        get() = if (targetGrams <= 0) 0f else (consumedGrams / targetGrams).toFloat().coerceIn(0f, 1f)
}

data class TodayFoodEntry(
    val id: Long,
    val name: String,
    val brand: String? = null,
    val amountText: String,
    val calories: Double,
    val proteinGrams: Double = 0.0,
    val carbohydrateGrams: Double = 0.0,
    val fatGrams: Double = 0.0,
    val mealCategory: MealCategory,
    val time: LocalTime,
    val isEstimated: Boolean = false,
    val thumbnailUri: String? = null,
    val foodId: Long? = null,
    val amount: Double = 0.0,
    val unit: String = "g",
    val grams: Double? = null,
    val sourceName: String? = null,
    val sourceUrl: String? = null,
)

data class TodayUiState(
    val date: LocalDate = LocalDate.now(),
    val caloriesConsumed: Double = 0.0,
    val calorieTarget: Double = 2_000.0,
    val protein: MacroProgress = MacroProgress(0.0, 130.0),
    val carbohydrates: MacroProgress = MacroProgress(0.0, 240.0),
    val fat: MacroProgress = MacroProgress(0.0, 65.0),
    val entries: List<TodayFoodEntry> = emptyList(),
    val isLoading: Boolean = false,
) {
    val caloriesDifference: Double get() = calorieTarget - caloriesConsumed
    val calorieFraction: Float
        get() = if (calorieTarget <= 0) 0f else (caloriesConsumed / calorieTarget).toFloat().coerceIn(0f, 1f)

    fun entriesFor(category: MealCategory): List<TodayFoodEntry> =
        entries.filter { it.mealCategory == category }.sortedBy { it.time }
}
